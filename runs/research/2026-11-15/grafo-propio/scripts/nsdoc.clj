(require '[clojure.edn :as edn] '[clojure.string :as str] '[clojure.tools.reader :as r] '[clojure.tools.reader.reader-types :as rt])
(def g (edn/read-string (slurp "/tmp/kimun-planes/grafo-propio/grafo.edn")))
(doseq [n (:nodes g)]
  (let [rdr (rt/indexing-push-back-reader (slurp (str "/home/jp/proyectos/kimun/" (:file n))))
        f (r/read {:read-cond :allow :features #{:clj}} rdr)
        d (nth f 2 nil)]
    (println (:lines n) (str/replace (:ns n) "kimun." "") "|" (some-> d (str/replace #"\s+" " ") (as-> s (subs s 0 (min 130 (count s))))))))
