(ns kimun.graph.export-test
  "The semantic export read as a graph snapshot, for both contract versions."
  (:require [clojure.test :refer [deftest is]]
            [kimun.graph.export :as sexport]))

(deftest export-snapshot-reads-both-contract-versions
  (let [v2 {"contract" {"name" "sldb_kgdb_semantic_export" "version" 2 "generated_at" "t"}
            "producer" {"name" "kimun" "version" "0" "command" []}
            "store" {"root" "." "store_path" ".kimun" "store_hash" "h"}
            "models" [{"name" "Spec" "semantics" ["type.knowledge.spec"] "base_models" [] "model_hash" "m"}]
            "documents" [{"id" "Spec:s1" "name" "s1" "model" "Spec" "path" "s1.md" "semantic_tags" ["type.knowledge.spec"]}]
            "sections" []
            "semantic_dag" {"nodes" [{"id" "type" "parents" []} {"id" "type.knowledge.spec" "parents" ["type"]}] "equivalences" {}}}
        snap (sexport/export->snapshot v2)
        ids (set (map #(get-in % ["identity" "node_id"]) (get snap "nodes")))]
    (is (= 2 (get-in snap ["metadata" "source_contract" "version"])))
    (is (every? ids ["sldb://store" "sldb://model/Spec" "sldb://document/Spec:s1" "sldb://semantic_tag/type.knowledge.spec"]))
    (is (= ids (set (map #(get-in % ["identity" "node_id"]) (get (sexport/export->snapshot (assoc-in v2 ["contract" "version"] 1)) "nodes")))))
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) (sexport/export->snapshot (assoc-in v2 ["contract" "version"] 3))))))
