(ns sldb.host.fs-store
  "Files-only backend for sldb.kernel.store with Babashka/JVM file semantics
   (docs/v2/02 §8.1). Node parity is a later task."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [sldb.kernel.store :as store]
            #?@(:clj [[clojure.java.io :as io]]))
  #?(:clj (:import [java.nio.file Files Paths StandardCopyOption StandardOpenOption]
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
                    (into-array StandardOpenOption [StandardOpenOption/CREATE StandardOpenOption/APPEND StandardOpenOption/WRITE])))))

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
  (append-tx! [_ tx]
    #?(:clj (append-line! (path dir "log.edn") (pr-str tx))))
  (read-log [_]
    #?(:clj (->> (or (read-text (path dir "log.edn")) "")
                 str/split-lines
                 (remove str/blank?)
                 (map-indexed (fn [i line]
                                (try (let [tx (edn/read-string line)]
                                       (when-not (and (map? tx) (:id tx) (:plan tx) (:revision tx))
                                         (throw (ex-info "not a Transaction" {})))
                                       tx)
                                     (catch Exception e
                                       (throw (ex-info (str "store: corrupt log line " (inc i))
                                                       {:type :store/corrupt-log :line (inc i) :cause (ex-message e)}))))))
                 vec)))
  (read-heads [_]
    #?(:clj (some-> (read-text (path dir "heads.edn")) edn/read-string)))
  (cas-heads! [_ expected new]
    #?(:clj (let [p (path dir "heads.edn")
                  current (some-> (read-text p) edn/read-string)
                  current (or current {:head nil :heads {}})]
              (if (= current expected)
                (do (write-atomic! p (pr-str new)) true)
                false)))))

(defn backend
  "A files backend rooted at `dir`."
  [dir] (->FsBackend dir))
