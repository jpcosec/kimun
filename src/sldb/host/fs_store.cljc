(ns sldb.host.fs-store
  "Files-only backend for sldb.kernel.store with Babashka/JVM file semantics
   (docs/v2/02 §8.1). Node parity is a later task.

   `commit-tx!` runs under an exclusive `FileChannel` lock on <dir>/lock (it
   excludes other processes; a JVM monitor excludes the threads of this one,
   which the file lock alone rejects with OverlappingFileLockException). The
   lock is released by closing the channel. Order inside the lock: compare the
   heads, repair the log tail (the pure rule is sldb.kernel.store/log-tail),
   append the line, replace heads.edn by temp file + atomic move. Readers take
   no lock: heads.edn is replaced atomically and a line being appended is an
   uncommitted tail to them."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [sldb.kernel.store :as store]
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
       "Runs `f` holding the store's exclusive lock (other processes: file lock; this JVM: monitor)."
       [dir f]
       (locking jvm-lock
         (let [ch (FileChannel/open (path dir "lock") (into-array StandardOpenOption [StandardOpenOption/CREATE StandardOpenOption/WRITE]))]
           (try (.lock ch) (f) (finally (.close ch))))))
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

(defrecord FsBackend [dir]
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
    #?(:clj (some-> (read-text (path dir "heads.edn")) edn/read-string)))
  (commit-tx! [_ expected tx new]
    #?(:clj (with-store-lock dir
              (fn []
                (let [hp (path dir "heads.edn")
                      lp (path dir "log.edn")
                      current (or (some-> (read-text hp) edn/read-string) store/no-heads)]
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
                      (write-atomic! hp (pr-str new))
                      true))))))))

(defn backend
  "A files backend rooted at `dir`."
  [dir] (->FsBackend dir))
