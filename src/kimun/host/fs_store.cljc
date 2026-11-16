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
            [kimun.kernel.store :as store])
  #?(:clj (:import [java.nio.file Files Paths StandardCopyOption StandardOpenOption]
                   [java.nio.channels FileChannel]
                   [java.nio ByteBuffer]
                   [java.nio.charset StandardCharsets])))

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

     (defn- in-dir
       "`name` inside directory `d` (a Path or a String) as a Path."
       [d name]
       (.resolve (Paths/get (str d) (make-array String 0)) (str name)))

     (defn- pack-file [home nm] (in-dir (:packs home) (str nm ".pack")))
     (defn- idx-file [home nm] (in-dir (:packs home) (str nm ".idx")))
     (defn- pack-names [home] (file-names (:packs home) ".pack"))

     (defn- store-home
       "The pack home of the STORE at `dir` (a String): `{:packs <Path of
        packs/> :loose <Path of objects/>}`. The store's packs are the stable
        store (docs/v2/10 §11.4)."
       [dir]
       {:packs (path dir "packs") :loose (path dir "objects")})

     (defn- cache-home
       "The pack home of the derived CACHE at `dir`: `{:packs <Path of
        cache/packs> :loose <Path of cache/objects>}` (K4b). Derived, deletable
        with `cache/`, never part of the store."
       [dir]
       {:packs (path dir "cache" "packs")
        :loose (Paths/get (cache/objects-dir dir) (make-array String 0))})

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
                      :digest (cache/sha256-hex (entries->idx es)) :footer-digest footer-digest})
                   (let [lb (read-at ch pos 4)
                         len (when lb (.getInt (ByteBuffer/wrap lb)))]
                     (when (or (nil? len) (neg? len) (> (long len) (- footer-start pos 4)))
                       (bad-pack p "record length beyond the file"))
                     (let [content (read-at ch (+ pos 4) len)]
                       (when (nil? content) (bad-pack p "truncated record"))
                       (let [id (cache/sha256-hex content)]
                         (when (and prev (not (pos? (compare id prev))))
                           (bad-pack p "records are not in strict id order"))
                         (recur (+ pos 4 len) id
                                (conj! entries [id (+ pos 4) len]))))))))))))

     (defn- write-idx!
       "Writes the canonical index of pack `nm` by temp file + atomic move."
       [home nm ^bytes arr]
       (let [target (idx-file home nm)
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
       [home nm pf ^bytes foot]
       (let [claimed (.getLong (ByteBuffer/wrap foot 8 8))
             ip (idx-file home nm)
             fits? (and (<= 0 claimed)
                        (<= claimed (quot (- pf (+ (count pack-magic) footer-bytes)) 4)))
             isz (when (and fits? (exists? ip)) (Files/size ip))
             arr (when (and isz (= (long isz) (* idx-entry claimed)))
                   (Files/readAllBytes ip))]
         (when (and arr (= (bytes->hex foot 16 32) (cache/sha256-hex arr))) arr)))

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
       [home nm]
       (let [p (pack-file home nm)
             pf (Files/size p)]
         (when (< pf (+ (count pack-magic) footer-bytes))
           (bad-pack p "shorter than the magic and the footer"))
         (with-open [ch (FileChannel/open p (into-array StandardOpenOption [StandardOpenOption/READ]))]
           (let [foot (read-at ch (- pf footer-bytes) footer-bytes)
                 fmagic (String. ^bytes (java.util.Arrays/copyOfRange ^bytes foot 0 8) StandardCharsets/US_ASCII)]
             (when-not (= idx-magic fmagic) (bad-pack p "no index footer"))
             (let [claimed (.getLong (ByteBuffer/wrap foot 8 8))
                   footer-digest (bytes->hex foot 16 32)
                   arr (matching-idx home nm pf foot)]
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

     (defn- packs-stamp-from
       "A cheap witness of a packs directory: its last-modified time, or nil when
        there is none. The directory is listed only when this changes (another
        process packed or unpacked)."
       [home]
       (when (exists? (:packs home))
         (.toMillis (Files/getLastModifiedTime (:packs home) (make-array java.nio.file.LinkOption 0)))))

     (def ^:private stamp-settle-ms
       "How long after its last change the last-modified time of the packs
        directory is not trusted to tell a later change apart: the timestamp
        of a filesystem may be as coarse as a second, and two packs written
        within one tick leave the same time."
       2000)

     (defn- packs-of
       "The loaded packs of directory `packs-dir`, `[{:name :bytes :count
        :start} …]` in name order, memoised in `packs-atom`/`stamp-atom` (the
        state belongs to the backend instance). The directory is listed again
        when its last-modified time changed, and also whenever that time is
        less than `stamp-settle-ms` old: an equal timestamp only proves nothing
        changed once the clock has moved past its granularity. `load-one` is
        `load-pack` for the store and `load-cache-pack` for the cache; it
        answers nil for a pack this instance must ignore."
       [home packs-atom stamp-atom load-one]
       (let [stamp (packs-stamp-from home)
             loaded @packs-atom
             settled? (or (nil? stamp) (> (- (System/currentTimeMillis) stamp) stamp-settle-ms))]
         (if (and settled? (= stamp @stamp-atom) (or (some? stamp) (empty? loaded)))
           loaded
           (let [names (pack-names home)
                 by-name (into {} (map (juxt :name identity)) loaded)
                 fresh (vec (keep #(or (by-name %) (load-one home %)) names))]
             (reset! packs-atom fresh)
             (reset! stamp-atom (packs-stamp-from home))
             fresh))))

     (defn- store-packs
       "The loaded packs of the STORE of `b` (the stable store, K4)."
       [b]
       (packs-of (store-home (:dir b)) (:packs b) (:packs-stamp b)
                 (fn [home nm] (load-pack home nm))))

     (defn- load-cache-pack
       "`{:name :bytes :count :start}` of a CACHE pack when its `.idx` is the
        index its footer names, else nil and the pack's name is remembered in
        `(:cache-bad b)`: a cache pack without a matching index, or with records
        that do not derive its footer, is ignored **in memory** for this
        backend instance (C3: a reader never scans the pack for the index, never
        writes and never fails because of the cache)."
       [b home nm]
       (let [res (err/rescue-any (fn [_] nil)
                                 (let [p (pack-file home nm)
                                       pf (Files/size p)]
                                   (when (>= pf (+ (count pack-magic) footer-bytes))
                                     (with-open [ch (FileChannel/open p (into-array StandardOpenOption [StandardOpenOption/READ]))]
                                       (let [foot (read-at ch (- pf footer-bytes) footer-bytes)
                                             fmagic (String. ^bytes (java.util.Arrays/copyOfRange ^bytes foot 0 8) StandardCharsets/US_ASCII)]
                                         (when (= idx-magic fmagic)
                                           (when-let [arr (matching-idx home nm pf foot)]
                                             {:name nm :bytes arr
                                              :count (.getLong (ByteBuffer/wrap foot 8 8))
                                              :start (- pf footer-bytes)})))))))]
         (if res
           res
           (do (when-not (some #{nm} @(:cache-bad b)) (swap! (:cache-bad b) conj nm))
               nil))))

     (defn- cache-packs
       "The loaded packs of the CACHE of `b` (K4b): derived, deletable, never
        part of the store."
       [b]
       (packs-of (cache-home (:dir b)) (:cache-packs b) (:cache-stamp b)
                 (fn [home nm] (load-cache-pack b home nm))))

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

     (defn- pack-bytes-of
       "Bytes [off, off+len) of the loaded `pack` of `packs-dir` (never past its
        body), or nil; nil when the file vanished (another process unpacked)."
       [home pack off len]
       (when (and (<= 0 off) (<= 0 len) (<= (+ off len) (:start pack)))
         (try
           (with-open [ch (FileChannel/open (pack-file home (:name pack))
                                            (into-array StandardOpenOption [StandardOpenOption/READ]))]
             (read-at ch off len))
           (catch java.nio.file.NoSuchFileException _ nil))))

     (defn- read-packed
       "UTF-8 content of object `id` from the STORE packs of `b`, or nil. The
        pack is opened, read and closed here: a backend holds no descriptor, and
        a command that opens the store once per beat must not leak one per pack.
        The content is not rehashed (`verify` does that)."
       [b ^String id]
       (let [packs (store-packs b)]
         (when-let [[arr off len] (packed-entry packs id)]
           (let [pack (some #(when (identical? arr (:bytes %)) %) packs)
                 bs (pack-bytes-of (store-home (:dir b)) pack off len)]
             (if bs
               (String. ^bytes bs StandardCharsets/UTF_8)
               ;; another process unpacked between the lookup and the read: the
               ;; object is loose again, so forget the packs and read it there
               (do (close-packs! b)
                   (read-text (path (:dir b) "objects" id))))))))

     (defn- loose-ids
       "Ids of the loose object files under `loose-dir` (temporary files excluded)."
       [home]
       (filterv #(not (str/includes? % ".tmp-")) (file-names (:loose home) "")))

     (defn- read-cached-verified
       "Text of the derived-cache object `id` of `b`, or nil. The loose cache
        file is served only when its bytes hash to `id` (C1); else the CACHE
        packs are searched and a record is served only when its bytes hash to
        `id` too (K4b: bytes corrupted with a valid `.idx` are a cache miss, not
        an object). A damaged copy is never repaired, renamed or deleted here."
       [b ^String id]
       (let [dir (:dir b)
             f (in-dir (:loose (cache-home dir)) id)
             loose (when (exists? f)
                     (when-let [bs (err/rescue-any (fn [_] nil) (Files/readAllBytes f))]
                       (when (= id (cache/sha256-hex bs))
                         (String. ^bytes bs StandardCharsets/UTF_8))))]
         (or loose
             (let [packs (cache-packs b)]
               (when-let [[arr off len] (packed-entry packs id)]
                 (let [pack (some #(when (identical? arr (:bytes %)) %) packs)]
                   (when-let [bs (pack-bytes-of (cache-home (:dir b)) pack off len)]
                     (when (= id (cache/sha256-hex bs))
                       (String. ^bytes bs StandardCharsets/UTF_8)))))))))

     (defn- write-bytes-atomic!
       "Writes `arr` to `p` by temp file in the same directory, then atomic move."
       [p ^bytes arr]
       (let [tmp (.resolveSibling p (str (.getFileName p) ".tmp-" (System/nanoTime)))]
         (Files/write tmp arr (into-array StandardOpenOption [StandardOpenOption/CREATE StandardOpenOption/TRUNCATE_EXISTING StandardOpenOption/WRITE]))
         (Files/move tmp p (into-array java.nio.file.CopyOption [StandardCopyOption/ATOMIC_MOVE StandardCopyOption/REPLACE_EXISTING]))))

     (defn- pack-candidates
       "Loose objects under `loose-dir` of fewer than `max-bytes` file bytes, as
        `[[id size] …]` sorted by id."
       [home max-bytes]
       (->> (loose-ids home)
            (keep (fn [id]
                    (let [f (in-dir (:loose home) id)]
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
       "Writes the `.idx` of every pack of `packs-dir` whose index is missing or
        does not match its footer, under the caller's lock (`stores pack`). A
        pack whose records do not derive the index its footer names is left
        alone (readers ignore it for the cache; the store's readers raise). The
        single writer of an index in the whole backend. Returns how many indexes
        it wrote."
       [home]
       (reduce (fn [n nm]
                 (let [p (pack-file home nm)
                       pf (Files/size p)
                       foot (when (>= pf (+ (count pack-magic) footer-bytes))
                              (with-open [ch (FileChannel/open p (into-array StandardOpenOption [StandardOpenOption/READ]))]
                                (read-at ch (- pf footer-bytes) footer-bytes)))
                       fmagic (when foot (String. ^bytes (java.util.Arrays/copyOfRange ^bytes foot 0 8) StandardCharsets/US_ASCII))]
                   (if-not (and foot (= idx-magic fmagic))
                     n
                     (let [footer-digest (bytes->hex foot 16 32)]
                       (if (matching-idx home nm pf foot)
                         n
                         (let [scan (err/rescue-any (fn [_] nil) (scan-pack p))]
                           (if (and scan (= footer-digest (:digest scan)))
                             (do (write-idx! home nm (entries->idx (:entries scan))) (inc n))
                             n)))))))
               0 (pack-names home)))

     (defn- pack-new!
       "Writes the loose objects `cands` (`[[id size] …]`, in this order) of
        `loose-dir` into a new pack of `packs-dir`, reading, hashing and writing
        ONE object at a time (never all of them in memory), with its footer and
        the index derived by the single writer. An object whose bytes do not hash
        to its id goes to `:damaged` and is not written; an id in `skip` goes to
        `:skipped` (the cache already holds it in a pack) and is not written
        either. Returns `{:pack :packed :bytes :packed-ids :damaged :skipped}`."
       [home nm cands skip]
       (Files/createDirectories (:packs home) (make-array java.nio.file.attribute.FileAttribute 0))
       ;; nobody else packs while the lock is held: only an interrupted run can
       ;; have left these temporaries
       (when (exists? (:packs home))
         (with-open [ls (Files/list (:packs home))]
           (doseq [f (iterator-seq (.iterator ls))
                   :let [n (str (.getFileName f))]
                   :when (or (str/includes? n ".pack.tmp-") (str/includes? n ".idx.tmp-"))]
             (err/rescue-any (fn [_] nil) (Files/deleteIfExists f)))))
       (let [target (pack-file home nm)
             tmp (in-dir (:packs home) (str nm ".pack.tmp-" (System/nanoTime)))
             bytes (volatile! 0)
             damaged (volatile! [])
             skipped (volatile! [])
             magic-len (long (count pack-magic))
             es (with-open [ch (FileChannel/open tmp (into-array StandardOpenOption [StandardOpenOption/CREATE StandardOpenOption/TRUNCATE_EXISTING StandardOpenOption/WRITE]))]
                  (write-fully! ch (ByteBuffer/wrap (.getBytes pack-magic StandardCharsets/US_ASCII)))
                  (let [es (loop [pos magic-len cands cands acc []]
                             (if-let [[id _] (first cands)]
                               ;; the hash decides first: a loose copy that does not
                               ;; hash to its name is damaged even when a pack holds
                               ;; a sound copy of that id
                               (let [content (read-bytes (in-dir (:loose home) id))]
                                 (cond
                                   (not= id (cache/sha256-hex content))
                                   (do (vswap! damaged conj id) (recur pos (rest cands) acc))
                                   (contains? skip id)
                                   (do (vswap! skipped conj id) (recur pos (rest cands) acc))
                                   :else
                                   (do (write-fully! ch (doto (ByteBuffer/allocate 4) (.putInt (alength ^bytes content)) (.flip)))
                                       (write-fully! ch (ByteBuffer/wrap content))
                                       (vswap! bytes + (alength ^bytes content))
                                       (recur (+ pos 4 (alength ^bytes content)) (rest cands)
                                              (conj acc [id (+ pos 4) (alength ^bytes content)])))))
                               acc))]
                    (write-fully! ch (ByteBuffer/wrap (footer (count es) (cache/sha256-hex (entries->idx es)))))
                    (.force ch true)
                    es))]
         (if (empty? es)
           (do (Files/deleteIfExists tmp)
               {:pack nil :packed 0 :bytes 0 :packed-ids [] :damaged @damaged :skipped @skipped})
           (do
             (Files/move tmp target (into-array java.nio.file.CopyOption [StandardCopyOption/ATOMIC_MOVE StandardCopyOption/REPLACE_EXISTING]))
             (let [scan (err/rescue-any (fn [e] e) (scan-pack target))
                   expected (mapv first es)]
               (if (or (instance? Throwable scan)
                       (not= expected (mapv first (:entries scan)))
                       (not= (cache/sha256-hex (entries->idx (:entries scan))) (:footer-digest scan)))
                 (do (Files/deleteIfExists target)
                     (Files/deleteIfExists (idx-file home nm))
                     (err/raise :store/corrupt-pack
                                (str "store: the pack written in " (:packs home) " did not read back as its objects; nothing was removed")
                                {:packs-dir (str (:packs home)) :pack nm :expected (count expected)}))
                 (do (write-idx! home nm (entries->idx (:entries scan)))
                     ;; the pack and its index are written: make the directory
                     ;; entry durable before removing the only other copy
                     (err/rescue-any
                      (fn [_] nil)
                      (with-open [dch (FileChannel/open (:packs home) (into-array StandardOpenOption [StandardOpenOption/READ]))]
                        (.force dch true)))
                     {:pack nm :packed (count expected) :bytes @bytes
                      :packed-ids expected :damaged @damaged :skipped @skipped})))))))

     (defn pack!
       "Moves the loose objects of `b` smaller than `:max-bytes` (default 4096)
        bytes into a new STORE pack (docs/v2/10 §11.4). Explicit: never part of
        a commit. Under the store's exclusive lock: writes `packs/<name>.pack`
        record by record, one object in memory at a time, writes the footer that
        names the canonical index, derives its `.idx`, reads the final pack back
        and checks that its records derive exactly the expected index, and only
        then removes the loose files. A loose file whose content does not hash
        to its name is left alone and reported in `:skipped-damaged`. Data:
        `{:scope store :pack :packed :bytes :kept-loose :skipped-damaged
        :indexes-repaired}`. It also writes the `.idx` of an existing pack whose
        index is missing or stale (readers rebuild in memory and never write),
        so it does that even when there is nothing to pack: then `:pack` is nil
        and only the indexes may change."
       ([b] (pack! b {}))
       ([b {:keys [max-bytes] :or {max-bytes default-max-bytes}}]
        (with-store-lock (:dir b) true
          (fn []
            (let [kdir (:dir b)
                  home (store-home kdir)
                  repaired (repair-indexes! home)
                  cands (pack-candidates home max-bytes)
                  nm (str (System/currentTimeMillis) "-" (System/nanoTime))
                  r (if (seq cands) (pack-new! home nm cands #{}) nil)]
              (doseq [id (:packed-ids r)] (Files/deleteIfExists (in-dir (:loose home) id)))
              (when (seq (:packed-ids r)) (close-packs! b))
              {:scope "store"
               :pack (:pack r) :packed (:packed r 0) :bytes (:bytes r 0)
               :kept-loose (count (loose-ids home))
               :skipped-damaged (:damaged r []) :indexes-repaired repaired})))))

     (defn- forget-index-cache
       "Drops what `b` remembered of the cache packs (after `pack-cache!`)."
       [b]
       (reset! (:cache-packs b) [])
       (reset! (:cache-stamp b) nil)
       (reset! (:cache-bad b) []))

     (defn pack-cache!
       "Moves the small loose objects of the derived cache of `b` into a CACHE
        pack (docs/v2/10 §11.5): derived, deletable with `cache/`, never part of
        the store. Under the store's exclusive lock: drops the cache packs this
        backend cannot load (`:dropped-packs`), rewrites the `.idx` of the sound
        ones that lack it (`:indexes-repaired`), deletes a loose cache object
        whose bytes do not hash to its name (`:dropped-damaged`), deletes a
        loose object a cache pack already holds (`:deduplicated`) and packs the
        rest in streaming order, one object in memory at a time. Data:
        `{:scope cache :pack :packed :bytes :kept-loose :dropped-damaged
        :deduplicated :dropped-packs :indexes-repaired :packs}`."
       ([b] (pack-cache! b {}))
       ([b {:keys [max-bytes] :or {max-bytes default-max-bytes}}]
        (with-store-lock (:dir b) true
          (fn []
            (let [kdir (:dir b)
                  home (cache-home kdir)
                  ;; a sound pack whose `.idx` is missing is not "doomed": repair
                  ;; the indexes first, then load and classify what still fails
                  repaired (repair-indexes! home)
                  _ (forget-index-cache b)
                  loaded (cache-packs b)
                  good (set (map :name loaded))
                  doomed (remove good (pack-names home))
                  dropped-packs (count doomed)
                  _ (doseq [nm doomed]
                      (Files/deleteIfExists (pack-file home nm))
                      (Files/deleteIfExists (idx-file home nm)))
                  _ (forget-index-cache b)
                  packed-ids (set (mapcat idx-ids (map :bytes (cache-packs b))))
                  cands (pack-candidates home max-bytes)
                  nm (str (System/currentTimeMillis) "-" (System/nanoTime))
                  r (if (seq cands) (pack-new! home nm cands packed-ids) nil)
                  damaged (:damaged r [])
                  dedup (:skipped r [])]
              (doseq [id (concat damaged dedup (:packed-ids r))]
                (Files/deleteIfExists (in-dir (:loose home) id)))
              (forget-index-cache b)
              {:scope "cache"
               :pack (:pack r) :packed (:packed r 0) :bytes (:bytes r 0)
                :kept-loose (count (loose-ids home))
               :dropped-damaged (count damaged)
               :deduplicated (count dedup)
               :dropped-packs dropped-packs
               :indexes-repaired repaired
                :packs (count (pack-names home))})))))

     (defn cache-bad-packs
       "Names of the cache packs this backend instance ignored (missing or
        mismatched `.idx`, or records that do not derive the footer). Read-only;
        `stores check` reports them as `cache/corrupt-pack`."
       [b]
       ;; loading the cache packs is what discovers an ignored one: force it so
       ;; the report does not depend on whether this instance read the cache yet
       (cache-packs b)
       (vec @(:cache-bad b)))

     (defn unpack!
       "Writes back every object of the STORE packs of `b` and only then removes
        the pack and its index (docs/v2/10 §11.4). Read with `scan-pack` only: it
        ignores the `.idx` and does not require the footer digest to match, being
        the recovery path. A loose file already there is kept only when its bytes
        hash to its id; a damaged one is replaced from the pack (atomically). No
        `unpack` exists for the cache: it is recovered by deleting `cache/`.
        Under the store's exclusive lock. Data: `{:packs :unpacked}`."
       [b]
       (with-store-lock (:dir b) true
         (fn []
           (let [kdir (:dir b)
                 home (store-home kdir)]
             (loop [remaining (pack-names home)
                    packs 0
                    unpacked 0]
               (if-let [nm (first remaining)]
                 (let [entries (:entries (scan-pack (pack-file home nm)))
                       written (with-open [ch (FileChannel/open (pack-file home nm) (into-array StandardOpenOption [StandardOpenOption/READ]))]
                                 (reduce (fn [n [id off len]]
                                           (let [p (in-dir (:loose home) id)
                                                 res (when (exists? p) (read-bytes p))
                                                 sound? (and res (= (long len) (alength ^bytes res))
                                                             (= id (cache/sha256-hex res)))]
                                             (if sound?
                                               n
                                               (do (write-bytes-atomic! p (read-at ch off len))
                                                   (inc n)))))
                                         0 entries))]
                   (doseq [[id _ len] entries]
                     (let [p (in-dir (:loose home) id)]
                       (when (or (not (exists? p))
                                 (not= (long len) (Files/size p))
                                 (not= id (cache/sha256-hex (read-bytes p))))
                         (err/raise :store/corrupt-pack
                                    (str "store: " id " was not written back from pack " nm "; the pack was kept")
                                    {:path kdir :pack nm :id id}))))
                   (Files/deleteIfExists (pack-file home nm))
                   (Files/deleteIfExists (idx-file home nm))
                   (recur (rest remaining) (inc packs) (+ unpacked written)))
                 (do (close-packs! b)
                     {:packs packs :unpacked unpacked})))))))

     (defn pack-stats
       "`{:packs n :packed n :pack-bytes n :loose n}` for the STORE packs of `b`:
        how many packs, how many objects they hold altogether, their size in
        bytes, and how many loose objects remain (docs/v2/10 §11.4). The cache
        packs are not counted here. Read-only."
       [b]
       (let [kdir (:dir b)
             home (store-home kdir)
             names (pack-names home)
             counts (mapv (fn [nm]
                            (let [p (pack-file home nm)
                                  ch (FileChannel/open p (into-array StandardOpenOption [StandardOpenOption/READ]))]
                              (try (let [size (.size ch)
                                         foot (read-at ch (- size footer-bytes) footer-bytes)]
                                     (.getLong (ByteBuffer/wrap foot) 8))
                                   (finally (.close ch)))))
                          names)]
         {:packs (count names)
          :packed (reduce + 0 counts)
          :pack-bytes (reduce + 0 (map #(Files/size (pack-file home %)) names))
          :loose (count (loose-ids home))}))

     (defn stored-size
       "Bytes of object `id` as the STORE holds it: its loose file, else its
        length in a store pack, else nil. The cache is not consulted."
       [b ^String id]
       (let [p (in-dir (:loose (store-home (:dir b))) id)]
         (if (exists? p)
           (Files/size p)
           (when-let [[_ _ len] (packed-entry (store-packs b) id)] (long len)))))))

(defrecord FsBackend [dir cache heads-memo wait? packs packs-stamp cache-packs cache-stamp cache-bad]
  store/ObjectCache
  (cached-object [_ id] (get @cache id))
  (cache-object! [_ id obj] (swap! cache assoc id obj) obj)
  store/PlanCache
  (read-plan [_ tx-id] (cache/read-plan dir tx-id))
  (write-plan! [_ tx-id plan] (cache/write-plan! dir tx-id plan))
  store/Backend
  (read-descriptor [_]
    #?(:clj (some-> (read-text (path dir "store.edn")) edn/read-string)))
  (write-descriptor! [_ d]
    #?(:clj (do (Files/createDirectories (path dir "objects") (make-array java.nio.file.attribute.FileAttribute 0))
                (write-atomic! (path dir "store.edn") (pr-str d)))))
  (read-object [this id]
    ;; the cache of derived objects (tree objects, block/text/opaque nodes of a
    ;; by-reference document) is read as a fallback: derived, deletable and
    ;; recomputable from the file (docs/v2/14 §18.6, §19). A cache hit is
    ;; verified: bytes that do not hash to `id` are a cache miss (C1), never an
    ;; object — and the reading path does not repair, rename or delete them.
    #?(:clj (or (read-text (path dir "objects" id))
                (read-packed this id)
                (read-cached-verified this id)
                ;; a resolved plan is not a CAS object: the in-memory object
                ;; view still exposes it so journal/history read it as before
                (some-> (cache/read-plan dir id) pr-str))))
  (write-object! [this id content]
    ;; write-once across the loose files and the packs: packing removes the
    ;; loose file, it does not make the object writable again
    #?(:clj (let [p (path dir "objects" id)]
              (when-not (or (exists? p) (packed-entry (store-packs this) id))
                (write-atomic! p content)))))
  (object-ids [this]
    ;; loose and packed; an id present both ways (a pack interrupted before it
    ;; removed the loose files) is one object
    #?(:clj (let [loose (loose-ids (store-home dir))
                  packed (mapcat (comp idx-ids :bytes) (store-packs this))]
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
  ([dir {:keys [wait?]}] (->FsBackend dir (atom {}) (atom nil) (boolean wait?) (atom []) (atom nil) (atom []) (atom nil) (atom []))))

(defn cached-ids
  "Ids of the objects this backend instance has parsed so far (diagnostics and tests)."
  [b] (set (keys @(:cache b))))
