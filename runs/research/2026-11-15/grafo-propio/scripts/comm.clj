(require '[clojure.edn :as edn] '[clojure.string :as str] '[clojure.pprint :as pp])
(def g (edn/read-string (slurp "/tmp/kimun-planes/grafo-propio/grafo.edn")))
(def hubs #{"kimun.kernel.err" "kimun.kernel.ports" "kimun.cli.out" "kimun.cli.store"})
(defn dr [n] (let [p (str/split (str/replace n "kimun." "") #"\.")] (str/join "." (if (> (count p) 1) (butlast p) p))))
(defn run [excl]
  (let [ns (sort (remove excl (map :ns (:nodes g))))
        es (for [[a b] (:edges g) :when (and (not (excl a)) (not (excl b)))] [a b])
        m (count es)
        w (reduce (fn [acc [a b]] (-> acc (update-in [a b] (fnil inc 0)) (update-in [b a] (fnil inc 0)))) {} es)
        k (into {} (for [n ns] [n (reduce + (vals (w n)))]))
        comm (atom (into {} (map (juxt identity identity) ns)))
        tot (atom k)]
    (loop [it 0]
      (let [moved (atom 0)]
        (doseq [n ns]
          (let [c0 (@comm n) kn (k n)
                _ (swap! tot update c0 - kn)
                links (reduce (fn [a [nb wt]] (update a (@comm nb) (fnil + 0) wt)) {} (w n))
                gain (fn [c] (- (get links c 0) (/ (* (@tot c 0) kn) (* 2.0 m))))
                best (apply max-key (fn [c] (+ (gain c) (if (= c c0) 1e-9 0))) (conj (keys links) c0))
                best (if (> (gain best) (+ (gain c0) 1e-12)) best c0)]
            (swap! tot update best (fnil + 0) kn)
            (when (not= best c0) (swap! moved inc) (swap! comm assoc n best))))
        (when (and (pos? @moved) (< it 30)) (recur (inc it)))))
    (let [Q (let [cs (set (vals @comm))]
              (reduce + (for [c cs] (- (/ (count (filter (fn [[a b]] (= c (@comm a) (@comm b))) es)) (double m)) (Math/pow (/ (reduce + (for [n ns :when (= c (@comm n))] (k n))) (* 2.0 m)) 2)))))]
      {:Q Q :groups (sort-by (comp - count) (vals (group-by @comm ns)))})))
(let [{:keys [Q groups]} (run hubs)]
  (println "modularity" Q "groups" (count groups) "(hubs excluded:" hubs ")")
  (doseq [gp groups]
    (println (count gp) (frequencies (map dr gp)))
    (println "   " (str/join " " (map #(str/replace % "kimun." "") gp)))))
(let [dm (frequencies (map (comp dr :ns) (:nodes g)))
      same (count (filter (fn [[a b]] (= (dr a) (dr b))) (:edges g)))]
  (println "edges within same dir" same "/" (count (:edges g))))
