(ns kimun.host.fs-store
  "Files-only backend for kimun.kernel.store with Babashka/JVM file semantics
   (docs/v2/02 §8.1). Node parity is a later task.

   `commit-tx!` runs under an exclusive `FileChannel` lock on <dir>/lock (it
   excludes other processes; a JVM monitor excludes the threads of this one,
   which the file lock alone rejects with OverlappingFileLockException). The
   lock is released by closing the channel. A lock held by another process
   makes the commit fail at once with :store/busy (nothing written but the
   write-once objects), or wait for it when the backend was made with
   {:wait? true}. Order inside the lock: compare the
   heads, repair the log tail (the pure rule is kimun.kernel.store/log-tail),
   append the line, replace heads.edn by temp file + atomic move. Readers take
   no lock: heads.edn is replaced atomically and a line being appended is an
   uncommitted tail to them.

   Object cache (the kernel's optional `ObjectCache` port): each backend
   instance keeps id -> parsed object in an atom, unbounded. Objects are
   immutable, so nothing is invalidated; `read-object` itself is never cached
   (it always returns the bytes on disk, which is what the hash checks hash).
   Unbounded, by measurement on an 800-document store (37,906 objects,
   76.6 MB of object text): `docs list`, `docs show` and `fields set` each
   read 17.4 thousand objects once (7.2 MB, 9% of the bytes), and `stores
   check` / `stores verify` walk every reachable object, which is the whole
   store, so a bound could only add re-reads; the lazy :objects map of the
   opened store memoises the same values anyway, so evicting here would free
   nothing.

   Packs (K4, docs/v2/10 §11.4): a stored object smaller than a filesystem
   block still occupies a whole one, and most objects are that small.
   `pack!` moves the small loose objects into `packs/<name>.pack` and
   `unpack!` moves them back; neither is ever part of a commit. A pack is the
   STORE (the only copy of what it holds); its `.idx` is DERIVED from it.

     <name>.pack   `KMNPACK1`, then one record per object in id order:
                   length (4 bytes, big-endian) + the UTF-8 content. The id is
                   not written: it is the hash of the content, so the pack
                   alone says what it holds.
     <name>.idx    one entry per record in pack order: id (32 binary bytes) +
                   offset of the content (8) + length (4). Read once per
                   backend instance; when it is absent or does not describe
                   the pack record by record it is rebuilt from the pack.

   `read-object` and `read-stored-object` prefer the loose file and fall back
   to the packs; `object-ids` is the loose listing plus every packed id;
   `write-object!` writes nothing for an id a pack already holds. The
   write-once and H(content) == id invariants are untouched: a packed object
   is the same bytes under the same id."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [kimun.host.store-cache :as cache]
            [kimun.kernel.err :as err]
            [kimun.kernel.store :as store]
            #?@(:clj [[clojure.java.io :as io]]))
  #?(:clj (:import [java.nio.file Files Paths StandardCopyOption StandardOpenOption]
                   [java.nio.channels FileChannel]
                   [java.nio ByteBuffer]
                   [java.nio.charset StandardCharsets]
                   [java.security MessageDigest])))

#?(:clj
   (do
     (defn- path [& parts] (Paths/get (first parts) (into-array String (rest parts))))
     (defn- exists? [p] (Files/exists p (make-array java.nio.file.LinkOption 0)))
     (defn- read-text
       "The text of `p`, or nil when it is absent. A file that disappears between
        the check and the read (another process packed it) is nil too, so the
        caller can fall back to the packs instead of failing the command."
       [p]
       (try (when (exists? p) (String. (Files/readAllBytes p) StandardCharsets/UTF_8))
            (catch java.nio.file.NoSuchFileException _ nil)))
     (defn- write-atomic!
       "Write to a temp file in the same directory, then rename over the target."
       [p ^String content]
       (let [tmp (.resolveSibling p (str (.getFileName p) ".tmp-" (System/nanoTime)))]
         (Files/write tmp (.getBytes content StandardCharsets/UTF_8) (into-array StandardOpenOption [StandardOpenOption/CREATE StandardOpenOption/TRUNCATE_EXISTING StandardOpenOption/WRITE]))
         (Files/move tmp p (into-array java.nio.file.CopyOption [StandardCopyOption/ATOMIC_MOVE StandardCopyOption/REPLACE_EXISTING]))))
     (defn- append-line! [p ^String line]
       (Files/write p (.getBytes (str line "\n") StandardCharsets/UTF_8)
                    (into-array StandardOpenOption [StandardOpenOption/CREATE StandardOpenOption/APPEND StandardOpenOption/WRITE])))
     (defn- parse-tx
       "The Transaction of a log line; throws when it is not one."
       [line]
       (let [tx (edn/read-string line)]
         (when-not (store/transaction? tx) (throw (ex-info "not a Transaction" {})))
         tx))
     (defonce ^:private jvm-lock (Object.))
     (defn- with-store-lock
       "Runs `f` holding the store's exclusive lock (other processes: file lock; this JVM: monitor).
        When another process holds the file lock: with `wait?` blocks until it is released,
        otherwise raises :store/busy at once, having written nothing."
       [dir wait? f]
       (locking jvm-lock
         (let [ch (FileChannel/open (path dir "lock") (into-array StandardOpenOption [StandardOpenOption/CREATE StandardOpenOption/WRITE]))]
           (try (if wait?
                  (.lock ch)
                  (when-not (.tryLock ch)
                    (throw (ex-info (str "store: another process is writing " dir "; retry, or pass --wait to wait for it")
                                    {:type :store/busy :path (str dir)}))))
                (f)
                (finally (.close ch))))))
     (defn- tail-lines
       "The last two non-blank lines of file `p` as
        [{:start byte-offset :terminated? bool :entry e}], reading only the end
        of the file. :entry is the Transaction, or {:torn-line 0} for a line
        that does not read as one."
       [p]
       (if-not (exists? p)
         []
         (with-open [ch (FileChannel/open p (into-array StandardOpenOption [StandardOpenOption/READ]))]
           (let [size (.size ch)]
             (loop [chunk 65536]
               (let [n (int (min size chunk))
                     base (- size n)
                     buf (ByteBuffer/allocate n)
                     _ (loop [] (when (and (.hasRemaining buf) (pos? (.read ch buf (+ base (.position buf))))) (recur)))
                     arr (.array buf)
                     ;; newline offsets through a one-byte-per-char view (no interpreted byte loop)
                     view (String. arr StandardCharsets/ISO_8859_1)
                     nls (loop [from 0 acc []]
                           (let [i (.indexOf view "\n" (int from))]
                             (if (neg? i) acc (recur (inc i) (conj acc i)))))]
                 (if (and (< n size) (< (count nls) 3))
                   (recur (* 4 chunk))
                   (let [;; segment starts: the chunk start only when it is the file start
                         starts (cond->> (map inc nls) (= n size) (cons 0))
                         ends (concat (if (= n size) nls (rest nls)) [n])
                         segs (for [[s e] (map vector starts ends)
                                    :let [text (String. arr (int s) (int (- e s)) StandardCharsets/UTF_8)]
                                    :when (not (str/blank? text))]
                                {:start (+ base s)
                                 :terminated? (< e n)
                                 :entry (try (parse-tx text) (catch Exception _ {:torn-line 0}))})]
                     (vec (take-last 2 segs))))))))))
     (defn- truncate! [p size]
       (with-open [ch (FileChannel/open p (into-array StandardOpenOption [StandardOpenOption/WRITE]))]
         (.truncate ch (long size))))

     ;; -------------------------------------------------------------- packs (K4)

     (def ^:private pack-magic "KMNPACK1")
     (def ^:private idx-entry "Bytes of one `.idx` entry: id (32) + offset (8) + length (4)." 44)
     (def ^:private hex-format
       "The JDK hex codec: the interpreted loops this replaced cost ~52 us per
       64-character id, the difference between a packed and a loose `docs list`."
       (java.util.HexFormat/of))

     (defn- hex->bytes!
       "Writes the 32 bytes of the 64-character hex id `hex` into `arr` at `off`."
       [^String hex ^bytes arr off]
       (System/arraycopy (.parseHex hex-format hex) 0 arr (int off) 32))

     (defn- hex->bytes
       "The 32 bytes of the 64-character hex id `s`, or nil when it is not one
        (another kind of id, or a character that is not hex)."
       ^bytes [^String s]
       (when (and (string? s) (= 64 (count s)))
         (try (.parseHex hex-format s) (catch Exception _ nil))))

     (defn- bytes->hex
       "The lower-case hex of bytes [off, off+len) of `arr`."
       [^bytes arr off len]
       (.formatHex hex-format arr (int off) (int (+ off len))))

     (defn- sha256-hex
       "The id a content has: lower-case hex SHA-256 of its bytes (the store's
        only hash algorithm)."
       [^bytes content]
       (bytes->hex (.digest (MessageDigest/getInstance "SHA-256") content) 0 32))

     (defn- file-names
       "Sorted names of the regular files of directory `d` that end with
        `suffix`, without it; [] when `d` does not exist."
       [d suffix]
       (if (exists? d)
         (with-open [s (Files/list d)]
           (->> (iterator-seq (.iterator s))
                (map #(str (.getFileName %)))
                (filter #(str/ends-with? % suffix))
                (map #(subs % 0 (- (count %) (count suffix))))
                sort vec))
         []))

     (defn- pack-file [dir nm] (path dir "packs" (str nm ".pack")))
     (defn- idx-file [dir nm] (path dir "packs" (str nm ".idx")))
     (defn- pack-names [dir] (file-names (path dir "packs") ".pack"))

     (defn- read-at
       "Bytes [off, off+len) of channel `ch`, or nil when the file is shorter."
       [^FileChannel ch off len]
       (let [buf (ByteBuffer/allocate (int len))]
         (loop [pos (long off)]
           (when (.hasRemaining buf)
             (let [n (.read ch buf pos)] (when (pos? n) (recur (+ pos n))))))
         (when-not (.hasRemaining buf) (.array buf))))

     (defn- write-fully!
       "Writes all of `buf` to `ch`: FileChannel.write may take fewer bytes than
        it was given."
       [^FileChannel ch ^ByteBuffer buf]
       (while (.hasRemaining buf)
         (.write ch buf)))

     (defn- bad-pack
       "Raises :store/corrupt-pack saying what is wrong with pack `p`."
       [p why]
       (err/raise :store/corrupt-pack (str "store: pack " (.getFileName p) ": " why)
                  {:pack (str (.getFileName p)) :why why}))

     (def ^:private idx-magic "KMNPIDX1")
     (def ^:private footer-bytes
       "Magic (8) + record count (8) + SHA-256 of the canonical index (32)."
       48)

     (defn- entries->idx
       "The canonical index bytes of `entries` ([[id offset length] …], ids in
        pack order): id (32 binary) + offset (8) + length (4) per record."
       ^bytes [entries]
       (let [buf (ByteBuffer/allocate (* idx-entry (count entries)))
             arr (.array buf)]
         (doseq [[id off len] entries]
           (hex->bytes! id arr (.position buf))
           (.position buf (+ (.position buf) 32))
           (.putLong buf (long off))
           (.putInt buf (int len)))
         arr))

     (defn- cmp-id
       "Compares the 32 id bytes of entry `i` of `arr` with `key` (32 bytes) as
        unsigned bytes: negative, zero or positive. A plain loop on purpose (see
        the measurement below): it stops at the first differing byte, which for
        hashes is almost always the first."
       [^bytes arr i ^bytes key]
       ;; measured in bb: this loop is 0.41 us/comparison, `ByteBuffer.mismatch`
        ;; 1.78 us (two wraps per step) and `Arrays/compareUnsigned` is not
        ;; callable from the native image (no reflective registration)
       (let [from (int (* i idx-entry))]
         (loop [k 0]
           (if (= k 32)
             0
             (let [d (- (bit-and 0xff (aget arr (+ from k)))
                        (bit-and 0xff (aget key k)))]
               (if (zero? d) (recur (inc k)) d))))))

     (defn- index-lookup
       "Binary search of `key` (32 id bytes) in the canonical index `arr`:
        `[offset length]` or nil. The ids are in strict order, so this is O(log n)."
       [^bytes arr key]
       (let [n (quot (alength arr) idx-entry)]
         (loop [lo 0 hi (dec n)]
           (when (<= lo hi)
             (let [mid (quot (+ lo hi) 2)
                   c (cmp-id arr mid key)]
               (cond (zero? c) [(.getLong (ByteBuffer/wrap arr) (+ (* (long mid) idx-entry) 32))
                                (.getInt (ByteBuffer/wrap arr) (+ (* (long mid) idx-entry) 40))]
                     (neg? c) (recur (inc mid) hi)
                     :else (recur lo (dec mid))))))))

     (defn- idx-ids
       "The ids (hex) of the canonical index bytes `arr`, in pack order."
       [^bytes arr]
       (mapv (fn [i] (bytes->hex arr (* (long i) idx-entry) 32))
             (range (quot (alength arr) idx-entry))))

     (defn- scan-pack
       "Reads the pack at `p` from the pack alone: `{:entries [[id offset length] …]
        :count n :start <offset of the footer> :digest <sha-256 hex of the
        canonical index> :footer-digest <what the footer claims>}`. Each id is
        recomputed as the hash of its content, a length is checked against the
        bytes left before allocating, the ids must be in strict order and the
        records must end exactly where the footer starts. A file that is not a
        pack, a record cut short and a bad footer are :store/corrupt-pack. The
        footer digest is not compared here: `load-pack` does (and `unpack!`
        ignores it on purpose, being the recovery path)."
       [p]
       (with-open [ch (FileChannel/open p (into-array StandardOpenOption [StandardOpenOption/READ]))]
         (let [size (.size ch)]
           (when (< size (+ (count pack-magic) footer-bytes))
             (bad-pack p "shorter than the magic and the footer"))
           (let [head (read-at ch 0 (count pack-magic))]
             (when-not (and head (= pack-magic (String. ^bytes head StandardCharsets/US_ASCII)))
               (bad-pack p "not a pack")))
           (let [footer-start (- size footer-bytes)
                 foot (read-at ch footer-start footer-bytes)
                 fmagic (String. ^bytes (java.util.Arrays/copyOfRange ^bytes foot 0 8) StandardCharsets/US_ASCII)]
             (when-not (= idx-magic fmagic) (bad-pack p "no index footer"))
             (let [count-buf (ByteBuffer/wrap foot 8 8)
                   claimed (.getLong count-buf)
                   footer-digest (bytes->hex foot 16 32)]
               (loop [pos (long (count pack-magic)) prev nil entries (transient [])]
                 (if (= pos footer-start)
                   (let [es (persistent! entries)]
                     (when (not= claimed (count es)) (bad-pack p "the footer's record count does not match"))
                     {:entries es :count (count es) :start footer-start
                      :digest (sha256-hex (entries->idx es)) :footer-digest footer-digest})
                   (let [lb (read-at ch pos 4)
                         len (when lb (.getInt (ByteBuffer/wrap lb)))]
                     (when (or (nil? len) (neg? len) (> (long len) (- footer-start pos 4)))
                       (bad-pack p "record length beyond the file"))
                     (let [content (read-at ch (+ pos 4) len)]
                       (when (nil? content) (bad-pack p "truncated record"))
                       (let [id (sha256-hex content)]
                         (when (and prev (not (pos? (compare id prev))))
                           (bad-pack p "records are not in strict id order"))
                         (recur (+ pos 4 len) id
                                (conj! entries [id (+ pos 4) len]))))))))))))

     (defn- write-idx!
       "Writes the canonical index of pack `nm` by temp file + atomic move."
       [dir nm ^bytes arr]
       (let [target (idx-file dir nm)
             tmp (.resolveSibling target (str nm ".idx.tmp-" (System/nanoTime)))]
         (Files/write tmp arr (into-array StandardOpenOption [StandardOpenOption/CREATE StandardOpenOption/TRUNCATE_EXISTING StandardOpenOption/WRITE]))
         (Files/move tmp target (into-array java.nio.file.CopyOption [StandardCopyOption/ATOMIC_MOVE StandardCopyOption/REPLACE_EXISTING]))))

     (defn- footer
       "The 48-byte footer for an index of `count` records whose SHA-256 is `digest`."
       ^bytes [count digest]
       (let [arr (byte-array footer-bytes)
             sb (ByteBuffer/wrap arr)]
         (System/arraycopy (.getBytes idx-magic StandardCharsets/US_ASCII) 0 arr 0 8)
         (.putLong sb 8 (long count))
         (System/arraycopy (hex->bytes digest) 0 arr 16 32)
         arr))

     (defn- matching-idx
       "The bytes of the `.idx` of pack `nm` of `dir` when that file is the index
        the footer `foot` of the pack (of `pf` bytes) names, else nil. The ONE
        place that decides whether an index file is read, for readers and for
        the repair alike: the footer's record count must fit the body of the
        pack (a record is at least its 4 length bytes) and the file must be
        exactly count × 44 bytes before a byte of it is read, so a huge or
        sparse file and a forged count never reach memory; then its SHA-256
        must be the footer's."
       [dir nm pf ^bytes foot]
       (let [claimed (.getLong (ByteBuffer/wrap foot 8 8))
             ip (idx-file dir nm)
             fits? (and (<= 0 claimed)
                        (<= claimed (quot (- pf (+ (count pack-magic) footer-bytes)) 4)))
             isz (when (and fits? (exists? ip)) (Files/size ip))
             arr (when (and isz (= (long isz) (* idx-entry claimed)))
                   (Files/readAllBytes ip))]
         (when (and arr (= (bytes->hex foot 16 32) (sha256-hex arr))) arr)))

     (defn- load-pack
       "`{:name nm :bytes <canonical index bytes> :count n :start footer-start}` of
        pack `nm` of `dir`. The `.idx` is read only when the footer's record
        count fits the pack and the file's size is exactly count × 44 (so a huge
        or sparse file is never read) and its SHA-256 matches; else the pack is
        scanned once and the index is rebuilt **in memory only**: a reading
        command never writes (`stores sense` and `stores heartbeat` promise to
        write nothing). A scan whose index does not derive the footer is
        :store/corrupt-pack (recovery: `stores unpack`). `stores pack` repairs a
        missing or stale `.idx` on disk. No file is kept open: a backend carries
        no descriptor."
       [dir nm]
       (let [p (pack-file dir nm)
             pf (Files/size p)]
         (when (< pf (+ (count pack-magic) footer-bytes))
           (bad-pack p "shorter than the magic and the footer"))
         (with-open [ch (FileChannel/open p (into-array StandardOpenOption [StandardOpenOption/READ]))]
           (let [foot (read-at ch (- pf footer-bytes) footer-bytes)
                 fmagic (String. ^bytes (java.util.Arrays/copyOfRange ^bytes foot 0 8) StandardCharsets/US_ASCII)]
             (when-not (= idx-magic fmagic) (bad-pack p "no index footer"))
             (let [claimed (.getLong (ByteBuffer/wrap foot 8 8))
                   footer-digest (bytes->hex foot 16 32)
                   arr (matching-idx dir nm pf foot)]
               (if arr
                 {:name nm :bytes arr :count claimed :start (- pf footer-bytes)}
                 (let [scan (scan-pack p)
                       arr (entries->idx (:entries scan))]
                   (when-not (= footer-digest (:digest scan))
                     (bad-pack p "the records do not derive the index its footer names"))
                   {:name nm :bytes arr :count (:count scan) :start (:start scan)})))))))

     (defn- close-packs!
       "Forgets the loaded packs of `b`: state only, no file is held open."
       [b]
       (reset! (:packs b) [])
       (reset! (:packs-stamp b) nil))

     (defn- packs-stamp
       "A cheap witness of the packs directory of `dir`: its last-modified time,
        or nil when there is none. The directory is listed only when this
        changes (another process packed or unpacked)."
       [dir]
       (let [d (path dir "packs")]
         (when (exists? d)
           (.toMillis (Files/getLastModifiedTime d (make-array java.nio.file.LinkOption 0))))))

     (def ^:private stamp-settle-ms
       "How long after its last change the last-modified time of the packs
        directory is not trusted to tell a later change apart: the timestamp
        of a filesystem may be as coarse as a second, and two packs written
        within one tick leave the same time."
       2000)

     (defn- packs-of
       "The loaded packs of backend `b`, `[{:name :bytes :count :start} …]`
        in name order. The directory is listed again when its last-modified
        time changed, and also whenever that time is less than
        `stamp-settle-ms` old: an equal timestamp only proves nothing changed
        once the clock has moved past its granularity. So a long-lived reader
        lists the directory per read for two seconds after a pack or an
        unpack, and with one `stat` per read after that."
       [b]
       (let [dir (:dir b)
             stamp (packs-stamp dir)
             loaded @(:packs b)
             settled? (or (nil? stamp) (> (- (System/currentTimeMillis) stamp) stamp-settle-ms))]
         (if (and settled? (= stamp @(:packs-stamp b)) (or (some? stamp) (empty? loaded)))
           loaded
           (let [names (pack-names dir)
                 by-name (into {} (map (juxt :name identity)) loaded)
                 fresh (mapv #(or (by-name %) (load-pack dir %)) names)]
             (reset! (:packs b) fresh)
             (reset! (:packs-stamp b) (packs-stamp dir))
             fresh))))

     (defn- packed-entry
       "[pack offset length] of object `id` in the loaded packs, or nil: one
        binary search per pack, no content is hashed. Only a 64-character
        lowercase hex is a CAS id; anything else (a tree id, for instance) is
        not looked for."
       [packs ^String id]
       (let [key (when (and (string? id) (= 64 (count id))) (hex->bytes id))]
         (when key
           (some (fn [{:keys [bytes]}]
                   (when-let [[off len] (index-lookup bytes key)] [bytes off len]))
                 packs))))

     (defn- read-packed
       "UTF-8 content of object `id` from the packs of `b`, or nil. The pack is
        opened, read and closed here: a backend holds no descriptor, and a
        command that opens the store once per beat must not leak one per pack.
        The range is checked against the pack's body (before its footer) before
        it is allocated; the content is not rehashed (`verify` does that)."
       [b ^String id]
       (let [packs (packs-of b)]
         (when-let [[arr off len] (packed-entry packs id)]
           (let [pack (some #(when (identical? arr (:bytes %)) %) packs)
                 p (pack-file (:dir b) (:name pack))]
             ;; `read-at` answers nil when the file is shorter than the range
             (when (and (<= 0 off) (<= 0 len) (<= (+ off len) (:start pack)))
               (try
                 (with-open [ch (FileChannel/open p (into-array StandardOpenOption [StandardOpenOption/READ]))]
                   (some-> (read-at ch off len)
                           (String. StandardCharsets/UTF_8)))
                 ;; another process unpacked between the lookup and the read: the
                 ;; object is loose again, so forget the packs and read it there
                 (catch java.nio.file.NoSuchFileException _
                   (close-packs! b)
                   (read-text (path (:dir b) "objects" id)))))))))

     (defn- loose-ids
       "Ids of the loose object files under objects/ (temporary files excluded)."
       [dir]
       (filterv #(not (str/includes? % ".tmp-")) (file-names (path dir "objects") "")))

     (defn- write-bytes-atomic!
       "Writes `arr` to `p` by temp file in the same directory, then atomic move."
       [p ^bytes arr]
       (let [tmp (.resolveSibling p (str (.getFileName p) ".tmp-" (System/nanoTime)))]
         (Files/write tmp arr (into-array StandardOpenOption [StandardOpenOption/CREATE StandardOpenOption/TRUNCATE_EXISTING StandardOpenOption/WRITE]))
         (Files/move tmp p (into-array java.nio.file.CopyOption [StandardCopyOption/ATOMIC_MOVE StandardCopyOption/REPLACE_EXISTING]))))

     (defn- pack-candidates
       "Loose objects under objects/ of fewer than `max-bytes` file bytes, as
        `[[id size] …]` sorted by id."
       [dir max-bytes]
       (->> (loose-ids dir)
            (keep (fn [id]
                    (let [f (path dir "objects" id)]
                      (when (exists? f)
                        (let [size (Files/size f)]
                          (when (< size max-bytes) [id size]))))))
            (sort-by first)))

     (defn- read-bytes [p] (Files/readAllBytes p))

     (def ^:private default-max-bytes
       "A loose object this size or larger is not packed: a filesystem block is
        usually 4 KB, so a smaller file costs a whole one."
       4096)

     (defn- repair-indexes!
       "Writes the `.idx` of every pack of `dir` whose index is missing or does
        not match its footer, under the caller's lock (`pack!`). A pack whose
        records do not derive the index its footer names is left alone: readers
        report it as :store/corrupt-pack. Returns how many indexes it wrote."
       [dir]
       (reduce (fn [n nm]
                 (let [p (pack-file dir nm)
                       pf (Files/size p)
                       foot (when (>= pf (+ (count pack-magic) footer-bytes))
                              (with-open [ch (FileChannel/open p (into-array StandardOpenOption [StandardOpenOption/READ]))]
                                (read-at ch (- pf footer-bytes) footer-bytes)))
                       fmagic (when foot (String. ^bytes (java.util.Arrays/copyOfRange ^bytes foot 0 8) StandardCharsets/US_ASCII))]
                   (if-not (and foot (= idx-magic fmagic))
                     n
                     (let [footer-digest (bytes->hex foot 16 32)]
                       (if (matching-idx dir nm pf foot)
                         n
                         (let [scan (err/rescue-any (fn [_] nil) (scan-pack p))]
                           (if (and scan (= footer-digest (:digest scan)))
                             (do (write-idx! dir nm (entries->idx (:entries scan))) (inc n))
                             n)))))))
               0 (pack-names dir)))

     (defn pack!
       "Moves the loose objects of `b` smaller than `:max-bytes` (default 4096)
        bytes into a new pack (docs/v2/10 §11.4). Explicit: never part of a
        commit. Under the store's exclusive lock: writes `packs/<name>.pack`
        record by record, one object in memory at a time, keeping only the
        index entries (not the contents), writes the footer that names the
        canonical index, derives its `.idx`, reads the final pack back and
        checks that its records derive exactly the expected index, and only
        then removes the loose files. A loose file whose content does not hash
        to its name is left alone and reported in `:skipped-damaged`. Data:
        `{:pack :packed :bytes :kept-loose :skipped-damaged :indexes-repaired}`.
        It also writes the `.idx` of an existing pack whose index is missing or
        stale (readers rebuild in memory and never write), so it does that even
        when there is nothing to pack: then `:pack` is nil and only the indexes
        may change."
       ([b] (pack! b {}))
       ([b {:keys [max-bytes] :or {max-bytes default-max-bytes}}]
        (with-store-lock (:dir b) true
          (fn []
            (let [kdir (:dir b)
                  repaired (repair-indexes! kdir)
                  cands (pack-candidates kdir max-bytes)]
              (if (empty? cands)
                {:pack nil :packed 0 :bytes 0 :kept-loose (count (loose-ids kdir))
                 :skipped-damaged [] :indexes-repaired repaired}
                (let [packs-dir (path kdir "packs")
                      _ (Files/createDirectories packs-dir (make-array java.nio.file.attribute.FileAttribute 0))
                      ;; nobody else packs while the lock is held: only an
                      ;; interrupted run can have left these
                      _ (with-open [ls (Files/list packs-dir)]
                          (doseq [f (iterator-seq (.iterator ls))
                                  :let [n (str (.getFileName f))]
                                  :when (or (str/includes? n ".pack.tmp-") (str/includes? n ".idx.tmp-"))]
                            (err/rescue-any (fn [_] nil) (Files/deleteIfExists f))))
                      nm (str (System/currentTimeMillis) "-" (System/nanoTime))
                      target (pack-file kdir nm)
                      tmp (.resolveSibling target (str nm ".pack.tmp-" (System/nanoTime)))
                      damaged (volatile! [])
                      bytes (volatile! 0)
                      magic-len (long (count pack-magic))
                      es (with-open [ch (FileChannel/open tmp (into-array StandardOpenOption [StandardOpenOption/CREATE StandardOpenOption/TRUNCATE_EXISTING StandardOpenOption/WRITE]))]
                           (write-fully! ch (ByteBuffer/wrap (.getBytes pack-magic StandardCharsets/US_ASCII)))
                           (let [es (loop [pos magic-len cands cands acc []]
                                      (if-let [[id _] (first cands)]
                                        (let [content (read-bytes (path kdir "objects" id))]
                                          (if (= id (sha256-hex content))
                                            (do (write-fully! ch (doto (ByteBuffer/allocate 4) (.putInt (alength ^bytes content)) (.flip)))
                                                (write-fully! ch (ByteBuffer/wrap content))
                                                (vswap! bytes + (alength ^bytes content))
                                                (recur (+ pos 4 (alength ^bytes content)) (rest cands)
                                                       (conj acc [id (+ pos 4) (alength ^bytes content)])))
                                            (do (vswap! damaged conj id) (recur pos (rest cands) acc))))
                                        acc))]
                             (write-fully! ch (ByteBuffer/wrap (footer (count es) (sha256-hex (entries->idx es)))))
                             (.force ch true)
                             es))]
                  (if (empty? es)
                    (do (Files/deleteIfExists tmp)
                        {:pack nil :packed 0 :bytes 0 :kept-loose (count (loose-ids kdir))
                         :skipped-damaged @damaged :indexes-repaired repaired})
                    (do
                      (Files/move tmp target (into-array java.nio.file.CopyOption [StandardCopyOption/ATOMIC_MOVE StandardCopyOption/REPLACE_EXISTING]))
                      (let [scan (err/rescue-any (fn [e] e) (scan-pack target))
                            expected (mapv first es)]
                        (if (or (instance? Throwable scan)
                                (not= expected (mapv first (:entries scan)))
                                (not= (sha256-hex (entries->idx (:entries scan))) (:footer-digest scan)))
                          (do (Files/deleteIfExists target)
                              (Files/deleteIfExists (idx-file kdir nm))
                              (err/raise :store/corrupt-pack
                                         (str "store: the pack written from " kdir "/objects did not read back as its objects; nothing was removed")
                                         {:path kdir :pack nm :expected (count expected)}))
                          (do (write-idx! kdir nm (entries->idx (:entries scan)))
                              ;; the pack and its index are written: make the
                              ;; directory entry durable before removing the only
                              ;; other copy (best effort: some platforms refuse)
                              (err/rescue-any
                               (fn [_] nil)
                               (with-open [dch (FileChannel/open packs-dir
                                                                 (into-array StandardOpenOption [StandardOpenOption/READ]))]
                                 (.force dch true)))
                              (doseq [id expected] (Files/deleteIfExists (path kdir "objects" id)))
                              (close-packs! b)
                              {:pack nm :packed (count expected) :bytes @bytes
                               :kept-loose (count (loose-ids kdir)) :skipped-damaged @damaged
                               :indexes-repaired repaired}))))))))))))

     (defn unpack!
       "Writes back every packed object of `b` and only then removes the pack and
        its index (docs/v2/10 §11.4). Read with `scan-pack` only: it ignores the
        `.idx` and does not require the footer digest to match, being the
        recovery path. A loose file already there is kept only when its bytes
        hash to its id; a damaged one is replaced from the pack (atomically),
        because the pack is the copy being deleted. Under the store's exclusive
        lock. Data: `{:packs :unpacked}` (`:unpacked` counts the objects written
        back, replaced ones included)."
       [b]
       (with-store-lock (:dir b) true
         (fn []
           (let [kdir (:dir b)]
             (loop [remaining (pack-names kdir)
                    packs 0
                    unpacked 0]
               (if-let [nm (first remaining)]
                 (let [entries (:entries (scan-pack (pack-file kdir nm)))
                       written (with-open [ch (FileChannel/open (pack-file kdir nm) (into-array StandardOpenOption [StandardOpenOption/READ]))]
                                 (reduce (fn [n [id off len]]
                                           (let [p (path kdir "objects" id)
                                                 res (when (exists? p) (read-bytes p))
                                                 sound? (and res (= (long len) (alength ^bytes res))
                                                             (= id (sha256-hex res)))]
                                             (if sound?
                                               n
                                               (do (write-bytes-atomic! p (read-at ch off len))
                                                   (inc n)))))
                                         0 entries))]
                   (doseq [[id _ len] entries]
                     (let [p (path kdir "objects" id)]
                       (when (or (not (exists? p))
                                 (not= (long len) (Files/size p))
                                 (not= id (sha256-hex (read-bytes p))))
                         (err/raise :store/corrupt-pack
                                    (str "store: " id " was not written back from pack " nm "; the pack was kept")
                                    {:path kdir :pack nm :id id}))))
                   (Files/deleteIfExists (pack-file kdir nm))
                   (Files/deleteIfExists (idx-file kdir nm))
                   (recur (rest remaining) (inc packs) (+ unpacked written)))
                 (do (close-packs! b)
                     {:packs packs :unpacked unpacked})))))))

     (defn pack-stats
       "`{:packs n :packed n :pack-bytes n :loose n}` for `b`: how many packs,
        how many objects they hold altogether, their size in bytes, and how many
        loose objects remain (docs/v2/10 §11.4). Read-only."
       [b]
       (let [kdir (:dir b)
             names (pack-names kdir)
             counts (mapv (fn [nm]
                            (let [p (pack-file kdir nm)
                                  ch (FileChannel/open p (into-array StandardOpenOption [StandardOpenOption/READ]))]
                              (try (let [size (.size ch)
                                         foot (read-at ch (- size footer-bytes) footer-bytes)]
                                     (.getLong (ByteBuffer/wrap foot) 8))
                                   (finally (.close ch)))))
                          names)]
         {:packs (count names)
          :packed (reduce + 0 counts)
          :pack-bytes (reduce + 0 (map #(Files/size (pack-file kdir %)) names))
          :loose (count (loose-ids kdir))}))

     (defn stored-size
       "Bytes of object `id` as the store holds it: its loose file, else its
        length in a pack, else nil."
       [b ^String id]
       (let [p (path (:dir b) "objects" id)]
         (if (exists? p)
           (Files/size p)
           (when-let [[_ _ len] (packed-entry (packs-of b) id)] (long len)))))))

(defrecord FsBackend [dir cache heads-memo wait? packs packs-stamp]
  store/ObjectCache
  (cached-object [_ id] (get @cache id))
  (cache-object! [_ id obj] (swap! cache assoc id obj) obj)
  store/PlanCache
  (read-plan [_ tx-id] (cache/read-plan dir tx-id))
  (write-plan! [_ tx-id plan] (cache/write-plan! dir tx-id plan))
  store/RouteCache
  (read-routes [_ root head] (cache/read-routes dir root head))
  (write-routes! [_ root head routes] (cache/write-routes! dir root head routes))
  store/Backend
  (read-descriptor [_]
    #?(:clj (some-> (read-text (path dir "store.edn")) edn/read-string)))
  (write-descriptor! [_ d]
    #?(:clj (do (Files/createDirectories (path dir "objects") (make-array java.nio.file.attribute.FileAttribute 0))
                (write-atomic! (path dir "store.edn") (pr-str d)))))
  (read-object [this id]
    ;; the cache of derived objects (tree objects, block/text/opaque nodes of a
    ;; by-reference document) is read as a fallback: derived, deletable and
    ;; recomputable from the file (docs/v2/14 §18.6, §19)
    #?(:clj (or (read-text (path dir "objects" id))
                (read-packed this id)
                (cache/read-object dir id)
                ;; a resolved plan is not a CAS object: the in-memory object
                ;; view still exposes it so journal/history read it as before
                (some-> (cache/read-plan dir id) pr-str))))
  (write-object! [this id content]
    ;; write-once across the loose files and the packs: packing removes the
    ;; loose file, it does not make the object writable again
    #?(:clj (let [p (path dir "objects" id)]
              (when-not (or (exists? p) (packed-entry (packs-of this) id))
                (write-atomic! p content)))))
  (object-ids [this]
    ;; loose and packed; an id present both ways (a pack interrupted before it
    ;; removed the loose files) is one object
    #?(:clj (let [loose (loose-ids dir)
                  packed (mapcat (comp idx-ids :bytes) (packs-of this))]
              (if (seq packed) (vec (distinct (concat loose packed))) loose))))
  (read-log [_]
    #?(:clj (let [text (or (read-text (path dir "log.edn")) "")
                  lines (vec (remove str/blank? (str/split-lines text)))
                  torn? (and (seq lines) (not (str/ends-with? text "\n")))]
              (vec (map-indexed (fn [i line]
                                  (try (parse-tx line)
                                       (catch Exception e
                                         ;; a final line cut short is the kernel's to judge (store/log-tail)
                                         (if (and torn? (= i (dec (count lines))))
                                           {:torn-line (inc i)}
                                           (throw (ex-info (str "store: corrupt log line " (inc i))
                                                           {:type :store/corrupt-log :line (inc i) :cause (ex-message e)}))))))
                                lines)))))
  (read-heads [_]
    #?(:clj (when-let [text (read-text (path dir "heads.edn"))]
              (try (edn/read-string text)
                   (catch Exception e
                     (throw (ex-info "store: heads.edn is unreadable" {:type :store/corrupt-heads :cause (ex-message e)})))))))
  (commit-tx! [_ expected tx new]
    #?(:clj (with-store-lock dir wait?
              (fn []
                (let [hp (path dir "heads.edn")
                      lp (path dir "log.edn")
                      text (read-text hp)
                      ;; the heads this instance last wrote or parsed, keyed by the exact file text:
                      ;; parsing heads.edn costs ~3 ms per 1,000 trees, comparing the text ~0.05 ms
                      memo @heads-memo
                      current (cond (nil? text) store/no-heads
                                    (= text (:text memo)) (:value memo)
                                    :else (edn/read-string text))]
                  (if (not= current expected)
                    false
                    (let [tail (tail-lines lp)]
                      (case (store/log-tail current (mapv :entry tail))
                        ;; a complete last line that lacks its newline (hand-edited log) gets it
                        :clean (when (and (seq tail) (not (:terminated? (peek tail)))) (append-line! lp ""))
                        :uncommitted (truncate! lp (:start (peek tail)))
                        :inconsistent (throw (ex-info "store: log.edn does not end at the recorded heads; run a full verification"
                                                      {:type :store/error :head (:head current)})))
                      (append-line! lp (pr-str tx))
                      (let [new-text (pr-str new)]
                        (write-atomic! hp new-text)
                        (reset! heads-memo {:text new-text :value new}))
                      true)))))))
  store/HeadsSwap
  (swap-heads! [_ expected new]
    #?(:clj (with-store-lock dir wait?
              (fn []
                (let [hp (path dir "heads.edn")
                      text (read-text hp)
                      current (if (nil? text) store/no-heads (edn/read-string text))]
                  (if (not= current expected)
                    false
                    (let [new-text (pr-str new)]
                      (write-atomic! hp new-text)
                      (reset! heads-memo {:text new-text :value new})
                      true)))))))
  store/StoredObjects
  (read-stored-object [this id]
    ;; verify reads here: a cached copy must never stand in for a stored object;
    ;; a pack is the store, so a packed object is read (and rehashed) from it
    #?(:clj (or (read-text (path dir "objects" id))
                (read-packed this id)))))

(defn backend
  "A files backend rooted at `dir`, with its own empty object cache and heads
   memo (the parsed heads.edn of its last commit, reused by the next commit's
   comparison only when the file text is byte-identical): every call returns an
   independent instance (no state shared between instances, none global).
   `opts` {:wait? true}: a commit that finds the lock held by another process
   waits for it; by default it fails at once with :store/busy (v1's
   `--wait` / fail-fast contract)."
  ([dir] (backend dir {}))
  ([dir {:keys [wait?]}] (->FsBackend dir (atom {}) (atom nil) (boolean wait?) (atom []) (atom nil))))

(defn cached-ids
  "Ids of the objects this backend instance has parsed so far (diagnostics and tests)."
  [b] (set (keys @(:cache b))))
