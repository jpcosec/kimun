#!/usr/bin/env bb
;; Store size by class of object, log and total (docs/v2/14 §10, docs/v2/10 §9).
;;
;;   bb scripts/store_size.clj [STORE-DIR]      (default .kimun)
;;   bb store-size [STORE-DIR]
;;
;; Reads objects/ one file at a time (streaming: counters only, never a whole
;; tree or a list of parsed objects), classifies each object by its shape and
;; prints the byte table the format doc quotes. `--json` prints only the
;; machine-readable summary.
(ns store-size
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(defn- klass
  "The class of a stored object, by shape. A tree descriptor carries :tree/:root
   (format 4 adds :class/:content-hash to it): it is checked before the node
   shapes so it is not counted as a sign node."
  [o]
  (cond
    (and (map? o) (:mmap o)) (str "mmap/" (name (:mmap o)))
    ;; the route map: one flat object (route schema 1) or tree nodes (schema 2, docs/v2/10 §11.2)
    (and (map? o) (:route/version o) (:entries o)) "route/map"
    (and (map? o) (:route/version o) (:children o)) "route/node"
    (and (map? o) (:tree o) (:root o)) "descriptor"
    (and (map? o) (:class o) (:kind o)) (str "sign/" (name (:class o)) "/" (name (:kind o)))
    (and (map? o) (:type o) (:from o)) (str "edge/" (name (:type o)))
    (and (map? o) (:tx o) (:trees o)) "revision"
    (and (map? o) (:node o) (contains? o :children)) "tree-object"
    (and (map? o) (contains? o :ops) (contains? o :plan/version)) "plan"
    :else "other/unknown"))

(defn- accumulate!
  "Counts one object: `acc` is an atom of {class [n bytes]}."
  [acc f]
  (let [n (fs/size f)
        k (try (klass (edn/read-string (slurp (str f)))) (catch Exception _ "unreadable"))]
    (swap! acc update k (fnil (fn [[c b]] [(inc c) (+ b n)]) [0 0]))))

(defn- disk-bytes
  "Bytes on disk of `dir` (`du -s`), or nil when du is unavailable."
  [dir]
  (let [r (process/shell {:out :string :err :string :continue true} "du" "-s" "--block-size=1" (str dir))]
    (when (zero? (:exit r)) (parse-long (first (str/split (str/trim (:out r)) #"\s+"))))))

(defn- log-bytes [dir]
  (let [f (fs/path dir "log.edn")] (if (fs/regular-file? f) (fs/size f) 0)))

(defn- cache-summary
  "The derived cache of `dir` (`<store>/cache/`, docs/v2/14 §18.6): count and
   total bytes, measured apart from the store."
  [dir]
  (let [d (fs/path dir "cache")]
    (if (fs/directory? d)
      (let [fs* (filter fs/regular-file? (fs/glob d "**"))]
        {:count (count fs*) :bytes (reduce + 0 (map #(fs/size %) fs*))})
      {:count 0 :bytes 0})))

(defn measure
  "`{:classes {class {:count n :bytes b}} :objects :log :apparent :disk}` of the
   store at `dir` (throws when it is not a store)."
  [dir]
  (let [objs (fs/path dir "objects")]
    (when-not (fs/directory? objs) (throw (ex-info (str "not a kimun store: " dir) {:path (str dir)})))
    (let [acc (atom {})]
      (doseq [f (fs/glob objs "**") :when (fs/regular-file? f)]
        (accumulate! acc f))
      (let [classes (into (sorted-map) (map (fn [[k [n b]]] [k {:count n :bytes b}])) @acc)
            objects (reduce + 0 (map :bytes (vals classes)))
            log (log-bytes dir)]
        {:classes classes :objects {:count (reduce + 0 (map :count (vals classes))) :bytes objects}
         :log {:bytes log}
         :apparent (+ objects log)
         :cache (cache-summary dir)
         :disk (disk-bytes dir)}))))

(defn- human [b] (if (nil? b) "—" (format "%.1f KB" (/ (double b) 1024))))

(defn- print-table [{:keys [classes objects log apparent disk cache]}]
  (println (format "%-28s %8s %12s %10s" "class" "count" "bytes" "avg"))
  (doseq [[k {:keys [count bytes]}] (sort-by (comp - :bytes val) classes)]
    (println (format "%-28s %8d %12d %10.0f" k count bytes (double (/ bytes (max 1 count))))))
  (println (format "%-28s %8d %12d" "TOTAL objects" (:count objects) (:bytes objects)))
  (println (format "%-28s %8s %12d" "log.edn" "" (:bytes log)))
  (println (format "%-28s %8s %12d  (%s)" "TOTAL apparent" "" apparent (human apparent)))
  (println (format "%-28s %8d %12d  (%s)" "cache (derived)" (:count cache) (:bytes cache) (human (:bytes cache))))
  (println (format "%-28s %8s %12s  (%s)" "on disk" "" (str disk) (human disk))))

(let [args *command-line-args*
      json? (some #{"--json"} args)
      dir (or (first (remove #(str/starts-with? % "--") args)) ".kimun")
      m (measure dir)]
  (when json? (println (json/generate-string m)))
  (when-not json?
    (println (str "store " dir))
    (print-table m)))
