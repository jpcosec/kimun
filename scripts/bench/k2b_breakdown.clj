;; K2b-0 measurement: bytes of the Revision mmaps and of the per-document edges of a store.
;; Usage:  bb scripts/bench/k2b_breakdown.clj <store-sdir>   (the .kimun directory; read-only)
;;
;; Reads objects/ and heads.edn only (loose objects; no pack, no kimun code). Prints:
;;  1. bytes of every stored object by class (all history), as scripts/store_size.clj does;
;;  2. the mmaps of the Revisions (:trees :edges :incidence) and the per-tree heads mmap:
;;     bytes of the nodes reachable from the HEAD revision (live) and from ALL revisions (history);
;;  3. edge objects by (type, kind of target): the three per-document edges and the rest,
;;     with the bytes of the :evidence part and the count of active edges at the head.
(require '[babashka.fs :as fs] '[clojure.edn :as edn] '[clojure.string :as str])

(def sdir (or (first *command-line-args*) (do (println "usage: k2b_breakdown.clj <.kimun dir>") (System/exit 2))))

(def objs ; id -> {:bytes n :o parsed}
  (into {} (for [f (fs/glob (fs/path sdir "objects") "**") :when (fs/regular-file? f)]
             [(str (fs/file-name f)) {:bytes (fs/size f)
                                      :o (try (edn/read-string (slurp (str f))) (catch Exception _ ::unreadable))}])))
(defn o [id] (:o (objs id)))
(defn b [id] (:bytes (objs id) 0))
(defn node? [x] (and (map? x) (#{:leaf :branch} (:mmap x))))
(def heads (edn/read-string (slurp (str (fs/path sdir "heads.edn")))))
(defn revision? [x] (and (map? x) (:tx x) (:incidence x)))

(defn walk
  "Ids of the mmap nodes under `root`, plus the ids named by leaf values ({:set root} nested sets)."
  [root]
  (loop [todo [root] seen #{} sets #{}]
    (if (empty? todo)
      {:nodes seen :sets sets}
      (let [id (peek todo) more (pop todo)]
        (if (or (seen id) (nil? (o id)))
          (recur more seen sets)
          (let [x (o id)]
            (if (= :branch (:mmap x))
              (recur (into more (vals (:children x))) (conj seen id) sets)
              (let [nested (keep (fn [[_ v]] (when (and (map? v) (:set v)) (:set v))) (:entries x))]
                (recur (into more nested) (conj seen id) (into sets nested))))))))))

(defn kbytes [ids] (reduce + 0 (map b ids)))

(defn revs-from [head]
  (loop [todo [head] seen #{}]
    (if (empty? todo) seen
        (let [r (peek todo)]
          (if (or (seen r) (not (revision? (o r)))) (recur (pop todo) seen)
              (recur (into (pop todo) (:parents (o r))) (conj seen r)))))))

(def all-revs (revs-from (:head heads)))
(defn mmap-class [k revs]
  (let [w (map #(walk (get (o %) k)) revs)]
    {:nodes (count (distinct (mapcat :nodes w))) :bytes (kbytes (distinct (mapcat :nodes w)))
     :hub-set-nodes (count (distinct (mapcat :sets w)))}))

(println "store" sdir "objects" (count objs) "revisions" (count all-revs))
(println "\n== 1. bytes by class of object (all history) ==")
(defn klass [x]
  (cond (node? x) (str "mmap/" (name (:mmap x)))
        (and (map? x) (:route/version x)) "route"
        (revision? x) "revision"
        (and (map? x) (:tree x) (:root x)) "descriptor"
        (and (map? x) (:class x) (:kind x)) (str "sign/" (name (:class x)) "/" (name (:kind x)))
        (and (map? x) (:type x) (:from x)) (str "edge/" (name (:type x)))
        (and (map? x) (:node x) (contains? x :children)) "tree-object"
        (and (map? x) (contains? x :ops)) "plan"
        :else "other"))
(let [t (reduce (fn [a [_ {:keys [bytes o]}]] (update a (klass o) (fnil (fn [[n s]] [(inc n) (+ s bytes)]) [0 0]))) {} objs)]
  (doseq [[k [n s]] (sort-by (comp - second val) t)] (println (format "%-26s %7d %12d" k n s)))
  (println (format "%-26s %7d %12d" "TOTAL objects" (count objs) (reduce + (map b (keys objs))))))
(println "log.edn bytes" (fs/size (fs/path sdir "log.edn")))

(println "\n== 2. Revision mmaps: LIVE (head) vs HISTORY (all revisions) ==")
(println (format "%-12s %-24s %-24s" "map" "head: nodes bytes" "history: nodes bytes"))
(doseq [k [:trees :edges :incidence]]
  (let [h (mmap-class k [(:head heads)]) a (mmap-class k all-revs)]
    (println (format "%-12s %6d %10d  (+%d hub-set nodes) %6d %10d  (+%d hub-set nodes)" (name k)
                     (:nodes h) (:bytes h) (:hub-set-nodes h) (:nodes a) (:bytes a) (:hub-set-nodes a)))))
(let [h (walk (:heads-root heads))]
  (println (format "%-12s %6d %10d  (heads.edn :heads-root, live only)" "heads" (count (:nodes h)) (kbytes (:nodes h)))))
(let [hr (o (:head heads))
      inc-entries (mapcat #(:entries (o %)) (:nodes (walk (:incidence hr))))
      hubs (filter (fn [[_ v]] (map? v)) inc-entries)]
  (println "head: incidence entries" (count inc-entries) "of which hubs" (count hubs)
           "(hub = more than 16 edges; a node that many documents point at, e.g. the class descriptor)")
  (println "head: active edges" (count (mapcat #(:entries (o %)) (:nodes (walk (:edges hr)))))
           "trees" (count (mapcat #(:entries (o %)) (:nodes (walk (:trees hr)))))))

(println "\n== 3. edge objects by (type, target kind), all stored ==")
(defn target-kind [e]
  (let [t (o (:to e))]
    (cond (nil? t) "target-absent"
          (= :external (:kind t)) "external(file)"
          (and (map? t) (= "kimun/model" (get-in t [:content :format]))) "model-descriptor"
          (and (map? t) (str/includes? (str (get-in t [:content :format])) "model")) "model-descriptor"
          (and (map? t) (:class t) (:kind t)) (str "sign/" (name (:kind t)))
          (and (map? t) (:tree t)) "class-descriptor"
          :else "other")))
(def active-ids (set (mapcat #(map first (:entries (o %))) (:nodes (walk (:edges (o (:head heads))))))))
(let [edges (filter (fn [[_ {:keys [o]}]] (and (map? o) (:type o) (:from o))) objs)
      rows (reduce (fn [a [id {:keys [bytes o]}]]
                     (update a [(:type o) (target-kind o)]
                             (fn [m] (-> (or m {:n 0 :bytes 0 :ev 0 :active 0 :active-bytes 0})
                                         (update :n inc) (update :bytes + bytes)
                                         (update :ev + (count (pr-str (:evidence o))))
                                         (cond-> (active-ids id) (-> (update :active inc) (update :active-bytes + bytes)))))))
                   {} edges)]
  (println (format "%-34s %6s %10s %12s %7s %12s" "type -> target" "n" "bytes" "evidence-B" "active" "active-bytes"))
  (doseq [[k m] (sort-by (comp - :bytes val) rows)]
    (println (format "%-34s %6d %10d %12d %7d %12d" (str/join " -> " (map #(if (keyword? %) (name %) %) k))
                     (:n m) (:bytes m) (:ev m) (:active m) (:active-bytes m)))))
(println "\nnote: ids and bytes are file sizes of loose objects (apparent bytes, no block rounding).")
