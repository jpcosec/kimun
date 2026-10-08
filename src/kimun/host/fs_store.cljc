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
   nothing."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [kimun.kernel.store :as store]
            #?@(:clj [[clojure.java.io :as io]]))
  #?(:clj (:import [java.nio.file Files Paths StandardCopyOption StandardOpenOption]
                   [java.nio.channels FileChannel]
                   [java.nio ByteBuffer]
                   [java.nio.charset StandardCharsets])))

#?(:clj
   (do
     (defn- path [& parts] (Paths/get (first parts) (into-array String (rest parts))))
     (defn- exists? [p] (Files/exists p (make-array java.nio.file.LinkOption 0)))
     (defn- read-text [p] (when (exists? p) (String. (Files/readAllBytes p) StandardCharsets/UTF_8)))
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
         (.truncate ch (long size))))))

(defrecord FsBackend [dir cache heads-memo wait?]
  store/ObjectCache
  (cached-object [_ id] (get @cache id))
  (cache-object! [_ id obj] (swap! cache assoc id obj) obj)
  store/Backend
  (read-descriptor [_]
    #?(:clj (some-> (read-text (path dir "store.edn")) edn/read-string)))
  (write-descriptor! [_ d]
    #?(:clj (do (Files/createDirectories (path dir "objects") (make-array java.nio.file.attribute.FileAttribute 0))
                (write-atomic! (path dir "store.edn") (pr-str d)))))
  (read-object [_ id]
    #?(:clj (read-text (path dir "objects" id))))
  (write-object! [_ id content]
    #?(:clj (let [p (path dir "objects" id)]
              (when-not (exists? p) (write-atomic! p content)))))
  (object-ids [_]
    #?(:clj (let [d (path dir "objects")]
              (if (exists? d)
                (with-open [s (Files/list d)] (mapv #(str (.getFileName %)) (iterator-seq (.iterator s))))
                []))))
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
                      true))))))))

(defn backend
  "A files backend rooted at `dir`, with its own empty object cache and heads
   memo (the parsed heads.edn of its last commit, reused by the next commit's
   comparison only when the file text is byte-identical): every call returns an
   independent instance (no state shared between instances, none global).
   `opts` {:wait? true}: a commit that finds the lock held by another process
   waits for it; by default it fails at once with :store/busy (v1's
   `--wait` / fail-fast contract)."
  ([dir] (backend dir {}))
  ([dir {:keys [wait?]}] (->FsBackend dir (atom {}) (atom nil) (boolean wait?))))

(defn cached-ids
  "Ids of the objects this backend instance has parsed so far (diagnostics and tests)."
  [b] (set (keys @(:cache b))))
