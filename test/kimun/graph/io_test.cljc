(ns kimun.graph.io-test
  "Ports of v1 tests/store/test_graph_io.py (3 tests): the semantic export
   ingest and the node-link round trip, pure over maps and JSON text."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [kimun.graph.io :as gio]))

(def payload
  "The v1 contract fixture, parsed."
  (gio/read-json (slurp (io/resource "kimun/graph/sldb_kgdb_semantic_export.v1.json"))))

(def snapshot (gio/semantic-export->snapshot payload))
(def doc-id "sldb://document/TaskDoc:001-define-kgdb-semantic-export-payload")

(deftest ingest-builds-structural-nodes
  (testing "v1 test_ingest_sldb_builds_the_structural_nodes"
    (let [by-id (into {} (map (juxt #(get-in % ["identity" "node_id"]) identity)) (get snapshot "nodes"))
          d (by-id doc-id)]
      (is (= "sldb_store" (get-in by-id ["sldb://store" "identity" "node_type"])))
      (is (= "sldb_document" (get-in d ["identity" "node_type"])))
      (is (= "hash-doc-index-001" (get-in d ["semantics" "hash_c"])))
      (is (= "hash-store-001" (get-in d ["source" "store" "hash_a"])))
      (is (some #(and (= "tagged_as" (get % "relation_type")) (= "sldb://semantic_tag/domain.contract" (get % "target_id")))
                (get d "edges")))
      (is (= "1.0" (get snapshot "version"))))
    (is (= :graph/bad-export
           (try (gio/semantic-export->snapshot (assoc-in payload ["contract" "version"] 2))
                (catch clojure.lang.ExceptionInfo e (:type (ex-data e))))))))

(defn- ids [g] (sort (keys (:nodes g))))
(defn- targets [g] (into #{} (map :to) (:edges g)))

(deftest save-and-load-round-trip
  (testing "v1 test_save_and_load_round_trip (through JSON text, no file)"
    (let [g (gio/semantic-export->graph payload)
          text (gio/write-json (gio/to-node-link g))
          back (gio/from-node-link (gio/read-json text))]
      (is (= (ids g) (ids back)))
      (is (= (targets g) (targets back)))
      (is (= (count (:edges g)) (count (:edges back))))
      (is (= true (get (gio/to-node-link g) "directed")))
      (is (= "hash-doc-index-001" (get-in back [:nodes doc-id :semantics "hash_c"]))))))

(deftest load-graph-reads-native-snapshot
  (testing "v1 test_load_graph_reads_a_native_snapshot"
    (let [reloaded (gio/from-node-link (gio/read-json (gio/write-json snapshot)))]
      (is (= (count (get snapshot "nodes")) (count (:nodes reloaded))))
      (is (= (count (:nodes (gio/snapshot->graph snapshot))) (count (:nodes reloaded)))))))
