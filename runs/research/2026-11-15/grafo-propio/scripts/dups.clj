(require '[clojure.edn :as edn] '[clojure.string :as str] '[clojure.tools.reader :as r] '[clojure.tools.reader.reader-types :as rt])
(def g (edn/read-string (slurp "/tmp/kimun-planes/grafo-propio/grafo.edn")))
(def defs
  (for [n (:nodes g)
        :let [rdr (rt/indexing-push-back-reader (slurp (str "/home/jp/proyectos/kimun/" (:file n))))
              forms (loop [fs []] (let [f (r/read {:read-cond :allow :features #{:clj} :eof ::eof} rdr)] (if (= f ::eof) fs (recur (conj fs f)))))
              forms (mapcat (fn flat [x] (if (and (seq? x) (= 'do (first x))) (mapcat flat (rest x)) [x])) forms)]
        f forms :when (and (seq? f) ('#{defn defn- def} (first f)) (symbol? (second f)))]
    {:ns (:ns n) :name (str (second f)) :line (:line (meta f)) :body (pr-str (drop 2 (remove string? f))) :private (or (= 'defn- (first f)) (:private (meta (second f))))}))
(println "total defs" (count defs) "public" (count (remove :private defs)) "private" (count (filter :private defs)))
(doseq [[nm xs] (sort-by key (group-by :name defs)) :when (and (> (count (distinct (map :ns xs))) 1) (not (#{"spec" "commands" "dispatch" "usage"} nm)))]
  (let [same (= 1 (count (distinct (map :body xs))))]
    (when (or same (> (count xs) 1))
      (println (if same "IDENTICO" "mismo-nombre") nm (str/join ", " (map #(str (str/replace (:ns %) "kimun." "") ":" (:line %)) xs))))))
