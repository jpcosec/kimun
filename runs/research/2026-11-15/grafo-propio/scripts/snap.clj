(require '[clojure.edn :as edn] '[cheshire.core :as j])
(def g (edn/read-string (slurp "/tmp/kimun-planes/grafo-propio/grafo.edn")))
(spit "/tmp/kimun-planes/grafo-propio/snapshot.json"
 (j/generate-string {"directed" true "multigraph" false "graph" {}
  "nodes" (for [n (:nodes g)] {"id" (str "sldb://ns/" (:ns n)) "type" (:ring n) "status" "unknown" "semantics" {} "facets" {}})
  "links" (for [[a b] (:edges g)] {"source" (str "sldb://ns/" a) "target" (str "sldb://ns/" b) "relation" "requires" "metadata" {}})} {:pretty true}))
