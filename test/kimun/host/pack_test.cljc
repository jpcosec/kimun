(ns kimun.host.pack-test
  "Packs of small CAS objects (K4, first cut; docs/v2/10 §11.4). A pack is the
   store (the only copy of what it holds) and its `.idx` is derived; packing and
   unpacking are explicit, hold the store lock, and never change an id, a
   revision or a head. These tests pin the round trip, the read and verify paths,
   the derived index, damaged records and loose files, and the guards the pack
   format needs against a corrupted length or a lying index."
  (:require [babashka.fs :as bfs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.host.fs-store :as fs]
            [kimun.kernel.node :as node]
            [kimun.kernel.store :as store]
            [kimun.test-util :as ktu])
  (:import (java.nio.file Files OpenOption StandardOpenOption)
           (java.security MessageDigest)))

(def h host/host)
(def TS "2026-08-29T12:00:00.000Z")

(defn- tid [i] (format "01ARZ3NDEKTSV4RRFFQ69G%04d" i))
(defn- path-of [i] (str "d" (mod i 3) "/f" i ".md"))

(defn- register-ops
  "The ops registering document `i` with sensed value `v` and its route."
  [i v]
  (let [root (node/make h :sign :block {:format :markdown :type :document :attrs {:n i}})
        ext (node/make h :sign :external
                       {:locator {:kind :file :path (path-of i)} :sample ""
                        :fingerprint "sha-256:1111111111111111111111111111111111111111111111111111111111111111"})]
    [{:op :add-node :node root}
     {:op :register-tree
      :tree {:kind :document :name (str "d" i) :class "C" :content-hash (str "content-" v) :cached? true
             :root (:id root)}
      :id (tid i) :root (:id root) :merkle-root (str "projection-" v)
      :route {:path (path-of i) :fingerprint (str "sha-256:" v) :class "C"
              :projection-hash (str "projection-" v) :content-hash (str "content-" v)}}
     {:op :add-node :node ext}
     {:op :add-edge :edge {:type :reference :from (:id root) :to (:id ext)
                           :evidence {:ref-hash (:id ext) :actor "jp"}}}]))

(defn- plan [base ops]
  {:plan/version 1 :base base :actor "jp" :engines {} :timestamp TS :ops (vec ops)})

(defn- fresh
  "A format-4 store directory with `n` documents and `edits` later edits of the
   first (the second pack test needs enough records to be a pack)."
  ([] (fresh 3 2))
  ([docs edits]
   (let [dir (str (ktu/temp-dir "kimun-pack-"))
         b (fs/backend dir)]
     (store/init! b h {"jp" :all})
     (let [s (store/open (fs/backend dir) h)
           _ (store/commit! (fs/backend dir) s (plan (:head s) (mapcat #(register-ops % 0) (range docs))))]
       (doseq [k (range 1 (inc edits))]
         (let [b (fs/backend dir)
               s (store/open b h)]
           (store/commit! b s (plan (:head s) (register-ops 0 k)))))
       dir))))

;; ---------------------------------------------------------------- helpers

(defn- bytes [p] (Files/readAllBytes (bfs/path p)))
(defn- write-bytes! [p ^bytes arr] (Files/write (bfs/path p) arr (into-array OpenOption [])))
(defn- hex [^bytes arr] (apply str (map #(format "%02x" (bit-and 0xff %)) arr)))
(defn- sha [^bytes arr] (hex (.digest (MessageDigest/getInstance "SHA-256") arr)))
(defn- hex->bytes [^String s]
  (let [arr (byte-array 32)]
    (dotimes [i 32]
      (aset-byte arr i (unchecked-byte (Integer/parseInt (subs s (* 2 i) (+ 2 (* 2 i))) 16))))
    arr))
(defn- hex? [s] (and (string? s) (boolean (re-matches #"[0-9a-f]{64}" s))))

(defn- loose
  "`{id content}` of the loose objects of `dir`."
  [dir]
  (into (sorted-map)
        (map (fn [f] [(str (bfs/file-name f)) (slurp (str f))]))
        (filter bfs/regular-file? (bfs/glob dir "objects/*"))))

(defn- pack-names [dir]
  (sort (map #(str/replace (str (bfs/file-name %)) #"\.pack$" "") (bfs/glob dir "packs/*.pack"))))

(defn- idx-of [dir nm] (str (bfs/path dir "packs" (str nm ".idx"))))
(defn- pack-of [dir nm] (str (bfs/path dir "packs" (str nm ".pack"))))

(def ^:private foot 48)

(defn- records
  "`[{:id :offset :len}]` of a pack, checked against the bytes left before its
   footer."
  [p]
  (let [arr (bytes p)
        magic 8
        foot-start (- (alength arr) foot)]
    (loop [pos magic acc []]
      (if (= pos foot-start)
        acc
        (let [len (java.nio.ByteBuffer/wrap arr pos 4)
              n (.getInt len)]
          (when (or (neg? n) (> (long n) (- foot-start pos 4)))
            (throw (ex-info "record length beyond the file" {:type :store/corrupt-pack})))
          (recur (+ pos 4 n)
                 (conj acc {:id (sha (java.util.Arrays/copyOfRange arr (+ pos 4) (+ pos 4 n)))
                            :offset (+ pos 4) :len n})))))))

(defn- idx-entries
  "`[[id offset length]]` of a `.idx` file."
  [p]
  (let [arr (bytes p)]
    (mapv (fn [i]
            (let [base (* i 44)]
              [(hex (java.util.Arrays/copyOfRange arr base (+ base 32)))
               (.getLong (java.nio.ByteBuffer/wrap arr) (+ base 32))
               (.getInt (java.nio.ByteBuffer/wrap arr) (+ base 40))]))
          (range (quot (alength arr) 44)))))

(defn- write-idx-entries! [p entries]
  (let [arr (byte-array (* 44 (count entries)))]
    (doseq [[i [id off len]] (map-indexed vector entries)]
      (let [base (* i 44)]
        (System/arraycopy (hex->bytes id) 0 arr base 32)
        (.putLong (java.nio.ByteBuffer/wrap arr) (+ base 32) (long off))
        (.putInt (java.nio.ByteBuffer/wrap arr) (+ base 40) (int len))))
    (write-bytes! p arr)))

(defn- damage-record!
  "Flips the last content byte of object `id` inside pack `p` (same length)."
  [p id]
  (let [arr (bytes p)
        {:keys [offset len]} (some #(when (= id (:id %)) %) (records p))]
    (aset-byte arr (dec (+ offset len)) (unchecked-byte (bit-xor 1 (aget arr (dec (+ offset len))))))
    (write-bytes! p arr)))

(defn- verify [dir]
  (let [b (fs/backend dir)] (store/verify b (store/open b h))))

(defn- verify-full [dir]
  (let [b (fs/backend dir)] (store/verify-full b h)))

(defn- opened [dir]
  (let [b (fs/backend dir)] [b (store/open b h)]))

(defn- file-snapshot [dir]
  (into (sorted-map)
        (map (fn [f] [(str (bfs/relativize dir f)) (sha (bytes f))]))
        (filter bfs/regular-file? (bfs/glob dir "**" {:hidden true}))))

;; ---------------------------------------------------------------- round trip

(deftest a-pack-and-unpack-store-round-trips-exactly
  (let [dir (fresh)
        before (loose dir)
        b (fs/backend dir)
        r (fs/pack! b)]
    (is (pos? (:packed r)))
    (is (> (:packed r) 1) "a pack holds several records")
    (is (= (* (:packed r) 0) (:kept-loose r)) "every small object was packed")
    (is (= [] (:skipped-damaged r)))
    (is (= 1 (count (pack-names dir))))
    (is (empty? (loose dir)) "packing removed the loose copies")
    (is (= (:packed r) (count (records (pack-of dir (first (pack-names dir)))))))
    (let [u (fs/unpack! (fs/backend dir))]
      (is (= 1 (:packs u)))
      (is (= (:packed r) (:unpacked u)))
      (is (= [] (pack-names dir)) "unpacking removed the pack")
      (is (= before (loose dir)) "objects/ is byte-identical, names and contents")
      (is (:ok? (verify dir)))
      (is (:ok? (verify-full dir))))))

(deftest a-packed-store-opens-reads-and-verifies-as-before
  (let [dir (fresh)
        [b s] (opened dir)
        ids (set (store/object-ids b))
        texts (into {} (map (fn [id] [id (store/read-object b id)]) ids))
        heads (:heads s)
        head (:head s)]
    (fs/pack! b)
    (let [[b2 s2] (opened dir)]
      (is (= head (:head s2)))
      (is (= heads (:heads s2)))
      (is (= ids (set (store/object-ids b2))) "the same id set, packed or loose")
      (is (= texts (into {} (map (fn [id] [id (store/read-object b2 id)]) ids))))
      (is (= (store/reachable s) (store/reachable s2)))
      (is (= (store/route-coverage s) (store/route-coverage s2)))
      (is (:ok? (verify dir)))
      (is (:ok? (verify-full dir))))))

(deftest a-commit-after-packing-leaves-packs-alone-and-a-second-pack-appears
  (let [dir (fresh)
        b (fs/backend dir)
        r1 (fs/pack! b)
        before (set (loose dir))
        s (store/open (fs/backend dir) h)
        _ (store/commit! (fs/backend dir) s (plan (:head s) (register-ops 1 7)))
        loose-now (loose dir)
        b2 (fs/backend dir)
        r2 (fs/pack! b2)]
    (is (seq loose-now) "the commit wrote new loose objects")
    (is (empty? (filter (set (keys loose-now)) (map :id (records (pack-of dir (first (pack-names dir)))))))
        "no packed id was written loose again")
    (is (pos? (:packed r2)))
    (is (= 2 (count (pack-names dir))) "a second run adds a second pack")
    (is (= [] (:skipped-damaged r2)))
    (is (:ok? (verify dir)))
    (is (:ok? (verify-full dir))))
  )

;; ---------------------------------------------------------------- thresholds

(deftest max-bytes-keeps-large-objects-loose
  (let [dir (fresh)
        b (fs/backend dir)
        biggest (apply max (map (comp count slurp str) (filter bfs/regular-file? (bfs/glob dir "objects/*"))))
        r (fs/pack! b {:max-bytes biggest})]
    (is (pos? (:packed r)))
    (is (pos? (:kept-loose r)))
    (is (some #(= biggest (count (slurp (str %)))) (filter bfs/regular-file? (bfs/glob dir "objects/*")))
        "an object of exactly the threshold stays loose")
    (is (:ok? (verify dir))))
  (let [dir (fresh)
        b (fs/backend dir)
        r (fs/pack! b {:max-bytes 1})]
    (is (zero? (:packed r)))
    (is (nil? (:pack r)))
    (is (= [] (pack-names dir)) "nothing to pack writes no packs/")
    (is (:ok? (verify dir)))))

;; ---------------------------------------------------------------- damage

(deftest a-damaged-record-reads-as-it-is-and-verify-reports-it
  (let [dir (fresh)
        _ (fs/pack! (fs/backend dir))
        nm (first (pack-names dir))
        head (:head (store/open (fs/backend dir) h))
        other (first (filter #(not= head %) (map :id (records (pack-of dir nm)))))]
    (testing "an ordinary record changed in place is read and reported by verify"
      (damage-record! (pack-of dir nm) other)
      (is (some? (store/read-object (fs/backend dir) other)) "a pack is read as it is, like a loose file")
      (let [v (verify dir)]
        (is (false? (:ok? v)))
        (is (some #(and (= other (:id %)) (= :hash-mismatch (:reason %))) (:bad v))))
      (is (pos? (count (filter #(some? (store/read-object (fs/backend dir) (:id %)))
                              (remove (fn [r] (= other (:id r))) (records (pack-of dir nm))))))
          "the other records still read"))
    (testing "the same damage on the head revision makes open refuse"
      (let [dir (fresh)
            _ (fs/pack! (fs/backend dir))
            nm (first (pack-names dir))
            head (:head (store/open (fs/backend dir) h))]
        (damage-record! (pack-of dir nm) head)
        (is (thrown-with-msg? Exception #"head" (store/open (fs/backend dir) h)))))))

(defn- counting-scan-pack
  "Calls `f` with every `scan-pack` counted: `[result scans]`."
  [f]
  (let [calls (atom 0)
        v (ns-resolve 'kimun.host.fs-store 'scan-pack)
        orig (deref v)]
    [(with-redefs-fn {v (fn [p] (swap! calls inc) (orig p))} f) @calls]))

(defn- idx-state
  "Bytes and last-modified time of the `.idx` of pack `nm`, or `:absent`."
  [dir nm]
  (let [p (idx-of dir nm)]
    (if (bfs/exists? p)
      [(seq (bytes p)) (str (bfs/last-modified-time p))]
      :absent)))

(deftest a-derived-index-is-rebuilt-in-memory-and-never-written-by-reading
  (let [dir (fresh)
        _ (fs/pack! (fs/backend dir))
        nm (first (pack-names dir))
        rs (records (pack-of dir nm))
        texts (fn []
                (let [b (fs/backend dir)]
                  (into {} (map (fn [r] [(:id r) (store/read-object b (:id r))]) rs))))
        original (texts)
        canonical (idx-state dir nm)]
    (testing "an index that matches the footer is used without scanning the pack"
      (let [[_ scans] (counting-scan-pack #(texts))]
        (is (zero? scans)) (is (= original (texts))))
      (is (= canonical (idx-state dir nm)) "and it is not rewritten"))
    (testing "a missing index is rebuilt once in memory; the file stays absent"
      (bfs/delete (idx-of dir nm))
      (let [[_ scans] (counting-scan-pack #(texts))]
        (is (= 1 scans)) (is (= original (texts))))
      (is (= :absent (idx-state dir nm)) "a reader never writes"))
    (testing "an index of garbage is detected at load, rebuilt in memory, file untouched"
      (write-bytes! (idx-of dir nm) (byte-array 88))
      (let [before (idx-state dir nm)
            [_ scans] (counting-scan-pack #(texts))]
        (is (= 1 scans)) (is (= original (texts)))
        (is (= before (idx-state dir nm)) "bytes and mtime unchanged"))
      (testing "and reads unrelated commands (verify, unreachable) leave it alone too"
        (let [before (idx-state dir nm)]
          (is (:ok? (verify dir)))
          (let [[b s] (opened dir)]
            (is (map? (store/unreachable b s))))
          (is (= before (idx-state dir nm))))))
    (testing "an index with the ids exchanged is detected at load and rebuilt once, file untouched"
      (bfs/delete (idx-of dir nm))
      (is (:ok? (verify dir)))
      (fs/pack! (fs/backend dir))
      (let [es (idx-entries (idx-of dir nm))
            [a b] (->> (group-by #(nth % 2) es) (filter (fn [[_ v]] (>= (count v) 2))) first second)
            swapped (mapv (fn [[id off len]]
                            (cond (= id (first a)) [(first b) off len]
                                  (= id (first b)) [(first a) off len]
                                  :else [id off len]))
                          es)]
        (write-idx-entries! (idx-of dir nm) swapped)
        (let [before (idx-state dir nm)
              [_ scans] (counting-scan-pack #(texts))]
          (is (= 1 scans)) (is (= original (texts)))
          (is (= before (idx-state dir nm))))))
    (is (:ok? (verify dir)))))

(deftest pack-repairs-a-missing-or-stale-index-even-with-nothing-to-pack
  (let [dir (fresh)
        _ (fs/pack! (fs/backend dir))
        nm (first (pack-names dir))
        canonical (first (idx-state dir nm))]
    (testing "a missing index"
      (bfs/delete (idx-of dir nm))
      (let [r (fs/pack! (fs/backend dir))]
        (is (= 1 (:indexes-repaired r)))
        (is (zero? (:packed r)) "nothing was loose")
        (is (= canonical (first (idx-state dir nm))) "the index is the canonical one"))
      (testing "and a second run has nothing to repair"
        (is (zero? (:indexes-repaired (fs/pack! (fs/backend dir)))))))
    (testing "a stale index"
      (write-bytes! (idx-of dir nm) (byte-array 88))
      (let [r (fs/pack! (fs/backend dir))]
        (is (= 1 (:indexes-repaired r)))
        (is (= canonical (first (idx-state dir nm))))))
    (testing "a corrupted pack is left alone and reported by readers"
      (let [arr (bytes (pack-of dir nm))]
        (aset-byte arr (+ (- (alength arr) foot) 20) (unchecked-byte 0))
        (write-bytes! (pack-of dir nm) arr))
      (is (zero? (:indexes-repaired (fs/pack! (fs/backend dir)))))
      (is (thrown? Exception (store/read-object (fs/backend dir) (first (map :id (records (pack-of dir nm))))))))))

(deftest a-damaged-footer-is-corrupt-pack-and-unpack-recovers
  (let [dir (fresh)
        _ (fs/pack! (fs/backend dir))
        nm (first (pack-names dir))
        objects-before (set (map :id (records (pack-of dir nm))))
        arr (bytes (pack-of dir nm))
        _ (aset-byte arr (+ (- (alength arr) foot) 20) (unchecked-byte 0))
        _ (write-bytes! (pack-of dir nm) arr)]
    (is (thrown-with-msg? Exception #"corrupt|footer|index" (store/read-object (fs/backend dir) (first objects-before))))
    (let [u (fs/unpack! (fs/backend dir))]
      (is (pos? (:unpacked u)))
      (is (= objects-before (set (keys (loose dir)))))
      (is (:ok? (verify dir)))
      (is (:ok? (verify-full dir))))))

(deftest a-truncated-pack-is-corrupt-pack-and-never-reads-short
  (let [dir (fresh)
        _ (fs/pack! (fs/backend dir))
        nm (first (pack-names dir))
        arr (bytes (pack-of dir nm))]
    (write-bytes! (pack-of dir nm) (java.util.Arrays/copyOfRange arr 0 (dec (alength arr))))
    (is (thrown-with-msg? Exception #"corrupt|pack|record" (store/read-object (fs/backend dir) (first (map :id (records (pack-of dir nm)))))))
    (is (thrown? Exception (verify dir)))))

;; ---------------------------------------------------------------- loose files

(deftest a-loose-file-that-does-not-hash-to-its-name-is-left-alone
  (let [dir (fresh)
        [b _] (opened dir)
        id (first (filter #(and (hex? %) (< (count (slurp (str (bfs/path dir "objects" %)))) 4096))
                          (store/object-ids b)))
        p (bfs/path dir "objects" id)
        text (slurp (str p))
        _ (write-bytes! p (.getBytes (str (subs text 0 (dec (count text))) "X") "UTF-8"))
        r (fs/pack! (fs/backend dir))
        v (verify dir)]
    (is (contains? (set (:skipped-damaged r)) id))
    (is (bfs/exists? p) "it stays loose")
    (is (false? (:ok? v)))
    (is (some #(and (= id (:id %)) (= :hash-mismatch (:reason %))) (:bad v))
        "verify still reports it as before packing")))

(deftest a-loose-copy-beside-a-packed-one-is-one-object
  (let [dir (fresh)
        b (fs/backend dir)
        _ (fs/pack! b)
        [b2 _] (opened dir)
        ids (set (store/object-ids b2))
        id (first (sort ids))
        text (store/read-object b2 id)]
    (write-bytes! (bfs/path dir "objects" id) (.getBytes text "UTF-8"))
    (let [[b3 _] (opened dir)]
      (is (= ids (set (store/object-ids b3))) "counted once")
      (is (= (count ids) (count (store/object-ids b3))))
      (is (= text (store/read-object b3 id)))
      (is (:ok? (verify dir))))))

(deftest another-backend-sees-a-pack-written-after-it-read
  (let [dir (fresh)
        [a _] (opened dir)
        ids (sort (store/object-ids a))
        _ (doseq [id (take 2 ids)] (store/read-object a id))
        _ (fs/pack! (fs/backend dir))
        last-id (last ids)]
    (is (some? (store/read-object a last-id)) "the instance that had not read it still does")))

;; ---------------------------------------------------------------- sizes

(deftest a-second-pack-in-the-same-timestamp-tick-is-still-seen
  ;; the directory time may not tell two changes apart (coarse filesystem
  ;; timestamps): a reader that loaded the first pack must find an object that
  ;; went into a second one written while the directory time read the same
  (let [dir (fresh)
        _ (fs/pack! (fs/backend dir))
        [a _] (opened dir)
        packs-dir (bfs/path dir "packs")
        _ (store/read-object a (first (sort (store/object-ids a))))   ; `a` has loaded the first pack
        seen (Files/getLastModifiedTime packs-dir (into-array java.nio.file.LinkOption []))
        s (store/open (fs/backend dir) h)
        r (store/commit! (fs/backend dir) s (plan (:head s) (register-ops 1 7)))
        new-id (:head (:store r))
        _ (fs/pack! (fs/backend dir))
        _ (Files/setLastModifiedTime packs-dir seen)]   ; as if both packs fell in one tick
    (is (= 2 (count (pack-names dir))))
    (is (not (bfs/exists? (bfs/path dir "objects" new-id))) "the new revision is only in the second pack")
    (is (some? (store/read-object a new-id)) "and the reader that predates it still reads it")))

(deftest sizes-and-stats-follow-the-pack
  (let [dir (fresh)
        b (fs/backend dir)
        ids (sort (store/object-ids b))
        sizes (into {} (map (fn [id] [id (fs/stored-size b id)]) ids))
        s0 (fs/pack-stats b)
        r (fs/pack! b)
        s1 (fs/pack-stats b)]
    (is (= (count ids) (:loose s0)))
    (is (zero? (:packs s0)))
    (is (= 1 (:packs s1)))
    (is (= (:packed r) (:packed s1)))
    (is (pos? (:pack-bytes s1)))
    (is (= 0 (:loose s1)))
    (is (= sizes (into {} (map (fn [id] [id (fs/stored-size b id)]) ids)))
        "the same sizes, from the loose file or from the pack")))

;; ---------------------------------------------------------------- K5 and layout

(deftest unreachable-is-the-same-before-and-after-packing
  (let [dir (fresh)
        inv (fn []
              (let [[b s] (opened dir)
                    r (store/unreachable b s)]
                (into {} (map (fn [k] [k (get r k)]) [:stored :reached :unreachable]))
                (assoc (select-keys r [:stored :reached :unreachable])
                       :ids (sort (map :id (concat (:candidates r) (:damaged r)))))))
        before (inv)]
    (fs/pack! (fs/backend dir))
    (is (= before (inv)))))

(deftest packing-touches-nothing-but-objects-and-packs
  (let [dir (fresh)
        keep-fn (fn [[k _]] (not (or (str/starts-with? k "objects/")
                                     (str/starts-with? k "packs/"))))
        before (into (sorted-map) (filter keep-fn (file-snapshot dir)))]
    (fs/pack! (fs/backend dir))
    (is (= before (into (sorted-map) (filter keep-fn (file-snapshot dir)))) "log, heads and store.edn byte-identical")
    (fs/unpack! (fs/backend dir))
    (is (= before (into (sorted-map) (filter keep-fn (file-snapshot dir)))))))

;; ---------------------------------------------------------------- hostile lengths

(defn- pack-with-lengths
  "A pack whose first record claims `len-bytes` as its length, with a well-formed
   footer (one record, zero digest) so the length check is what refuses it."
  [dir ^bytes len-bytes ^bytes content]
  (let [p (str (bfs/path dir "packs" "hostile.pack"))
        arr (byte-array (+ 8 4 (alength content) foot))]
    (System/arraycopy (.getBytes "KMNPACK1" "US-ASCII") 0 arr 0 8)
    (System/arraycopy len-bytes 0 arr 8 4)
    (System/arraycopy content 0 arr 12 (alength content))
    (System/arraycopy (.getBytes "KMNPIDX1" "US-ASCII") 0 arr (- (alength arr) foot) 8)
    (bfs/create-dirs (bfs/path dir "packs"))
    (write-bytes! p arr)
    p))

(deftest a-record-length-is-checked-before-anything-is-allocated
  (let [dir (fresh)
        huge (byte-array [-1 -1 -1 -1])            ; 0xffffffff: negative as an int
        big (byte-array (map unchecked-byte [0x7f 0xff 0xff 0xff]))  ; 2 GiB - 1
        content (.getBytes "{}" "UTF-8")]
    (testing "a negative length"
      (pack-with-lengths dir huge content)
      (is (thrown-with-msg? Exception #"length" (store/read-stored-object (fs/backend dir) "aa")))
      (bfs/delete (bfs/path dir "packs" "hostile.pack")))
    (testing "a length beyond the file (2 GiB asked, a few bytes there)"
      (pack-with-lengths dir big content)
      (is (thrown-with-msg? Exception #"length" (store/read-stored-object (fs/backend dir) "aa")))
      (bfs/delete (bfs/path dir "packs" "hostile.pack")))
    (testing "a length just past what is left"
      (pack-with-lengths dir (byte-array [0 0 0 9]) content)
      (is (thrown-with-msg? Exception #"length" (store/read-stored-object (fs/backend dir) "aa"))))))

;; ---------------------------------------------------------------- a lying index

(deftest a-valid-shaped-index-with-wrong-ids-still-reads-the-right-bytes
  (let [dir (fresh)
        _ (fs/pack! (fs/backend dir))
        nm (first (pack-names dir))
        rs (records (pack-of dir nm))
        text (into {} (map (fn [r] [(:id r) (store/read-object (fs/backend dir) (:id r))]) rs))
        pairs (->> (group-by :len rs) (filter (fn [[_ v]] (>= (count v) 2))) first second)
        [a b] (take 2 pairs)]
    (testing "two ids of equal-length records exchanged"
      (let [es (idx-entries (idx-of dir nm))
            swapped (mapv (fn [[id off len]]
                            (cond (= id (:id a)) [(:id b) off len]
                                  (= id (:id b)) [(:id a) off len]
                                  :else [id off len]))
                          es)]
        (write-idx-entries! (idx-of dir nm) swapped)
        (is (= text (into {} (map (fn [r] [(:id r) (store/read-object (fs/backend dir) (:id r))])) rs))
            "each id reads its own content")
        (is (:ok? (verify dir)))
        (is (= swapped (idx-entries (idx-of dir nm))) "a reader leaves the file as it was")
        (is (= 1 (:indexes-repaired (fs/pack! (fs/backend dir)))))
        (is (= (mapv :id rs) (mapv first (idx-entries (idx-of dir nm)))) "stores pack rewrote it canonical")))
    (testing "a real id replaced by an arbitrary one"
      (let [es (idx-entries (idx-of dir nm))
            bogus (apply str (repeat 64 "0"))
            lying (mapv (fn [[id off len]] [(if (= id (:id a)) bogus id) off len]) es)]
        (write-idx-entries! (idx-of dir nm) lying)
        (is (= text (into {} (map (fn [r] [(:id r) (store/read-object (fs/backend dir) (:id r))])) rs)))
        (is (:ok? (verify dir)))
        (is (= lying (idx-entries (idx-of dir nm))) "a reader leaves the file as it was")
        (is (= 1 (:indexes-repaired (fs/pack! (fs/backend dir)))))
        (is (= (mapv :id rs) (mapv first (idx-entries (idx-of dir nm)))))))
    (testing "no read ever answers with content whose hash is not the id asked"
      (doseq [r rs]
        (let [t (store/read-object (fs/backend dir) (:id r))]
          (when t (is (= (:id r) (sha (.getBytes ^String t "UTF-8"))))))))))

;; ---------------------------------------------------------------- unpack guards

(deftest unpack-replaces-a-damaged-loose-duplicate-of-the-same-length
  (let [dir (fresh)
        _ (fs/pack! (fs/backend dir))
        nm (first (pack-names dir))
        r (first (filter #(< (:len %) 4096) (records (pack-of dir nm))))
        p (bfs/path dir "objects" (:id r))
        text (store/read-object (fs/backend dir) (:id r))]
    (is (some? text))
    (write-bytes! p (.getBytes (apply str (repeat (count text) "x")) "UTF-8"))
    (is (= (count text) (count (slurp (str p)))) "the loose duplicate has the same length")
    (let [u (fs/unpack! (fs/backend dir))]
      (is (pos? (:unpacked u)))
      (is (= text (slurp (str p))) "the pack bytes replaced it")
      (is (:ok? (verify dir)))
      (is (:ok? (verify-full dir))))))

(deftest no-descriptor-is-leaked-per-open-and-read
  ;; R1: `load-pack` must not cache a channel. A command that opens the store
  ;; once per beat (stores heartbeat, serve) would otherwise leak one descriptor
  ;; per pack and per beat.
  (let [dir (fresh)
        _ (fs/pack! (fs/backend dir))
        fds (fn [] (count (bfs/list-dir "/proc/self/fd")))
        id (first (sort (map :id (records (pack-of dir (first (pack-names dir)))))))
        before (do (dotimes [_ 5] (let [b (fs/backend dir)] (store/open b h) (store/read-object b id)))
                   (fds))]
    (dotimes [_ 200]
      (let [b (fs/backend dir)]
        (store/open b h)
        (store/read-object b id)))
    (let [after (fds)]
      (is (<= after (+ before 8)) (str "fds before " before ", after " after)))))

(deftest a-huge-index-is-never-read
  ;; R2: the `.idx` is read only when the footer's count and the file size agree.
  (let [dir (fresh)
        _ (fs/pack! (fs/backend dir))
        nm (first (pack-names dir))
        ip (idx-of dir nm)
        rs (mapv :id (records (pack-of dir nm)))
        texts (fn [] (into {} (map (fn [id] [id (store/read-object (fs/backend dir) id)]) rs)))]
    (bfs/delete ip)
    (with-open [raf (java.io.RandomAccessFile. (str ip) "rw")]
      (.setLength raf (* 1024 1024 1024)))
    (is (= 1073741824 (bfs/size ip)) "a 1 GiB sparse file")
    (is (= (texts) (texts)) "everything still reads")
    (let [r (fs/pack! (fs/backend dir))]
      (is (= 1 (:indexes-repaired r)) "a reader never writes; `stores pack` repairs it"))
    (is (= (* 44 (count rs)) (bfs/size ip)) "and the index was rebuilt canonical")
    (is (:ok? (verify dir)))))

(deftest an-interrupted-pack-leaves-no-temporaries
  ;; R6: a `pack!` sweeps the temporaries an interrupted run left, under the lock.
  (let [dir (fresh)
        packs (bfs/path dir "packs")]
    (bfs/create-dirs packs)
    (spit (str (bfs/path packs "old.pack.tmp-1")) "x")
    (spit (str (bfs/path packs "old.idx.tmp-2")) "x")
    (fs/pack! (fs/backend dir))
    (is (empty? (filter #(re-find #"\.tmp-" (str %)) (bfs/glob dir "packs/*"))))))

(deftest pack-refuses-nothing-to-pack-and-reports-it
  (let [dir (fresh)
        _ (fs/pack! (fs/backend dir))
        r (fs/pack! (fs/backend dir))]
    (is (zero? (:packed r)))
    (is (nil? (:pack r)))
    (is (= 0 (:kept-loose r)))
    (is (= [] (:skipped-damaged r)))))
