(require '[clojure.edn :as edn] '[clojure.string :as str])
(def g (edn/read-string (slurp "/tmp/kimun-planes/grafo-propio/grafo.edn")))
(defn s [x] (str/replace x "kimun." ""))
(doseq [t *command-line-args*]
  (println t "<-" (map s (sort (for [[a b] (:edges g) :when (= b (str "kimun." t))] a))))
  (println t "->" (map s (sort (for [[a b] (:edges g) :when (= a (str "kimun." t))] b)))))
