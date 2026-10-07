(ns kimun.parity.edges-test
  "v1 `api/test_api_edges.py` store-level promises that kimun holds by other
   means than v1's shards: a write touches only the document it changes, a
   hand edit reaches the derived graph, and a wiped derived index costs
   nothing because the graph is a view of `(store, head)`."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run (vec argv) env cwd)))
(defn- file [p n] (str (fs/path p n)))

(def table
  (pr-str {:model/name "Table" :model/version 1 :base [] :semantics {"type" ["restaurant" "table"]}
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "capacity" :type :int :required true :description "Seats."}]
           :template "# ⸢rev•title⸥\n\nCapacity: ⸢rev•capacity⸥\n"}))

(defn- world []
  (let [p (str (ktu/temp-dir "kimun-parity-edges-"))]
    (run p "stores" "init" "--name" "p")
    (spit (file p "table.edn") table)
    (is (true? (:ok (run p "models" "define" "-f" "table.edn"))))
    (is (true? (:ok (run p "docs" "create" "Table" "t12" "--set" "title=Table 12" "--set" "capacity=6"))))
    (is (true? (:ok (run p "docs" "create" "Table" "t14" "--set" "title=Table 14" "--set" "capacity=8"))))
    p))

(defn- fingerprint [p doc]
  (get-in (run p "edges" "node" (str "Table:" doc)) [:data :semantics "fingerprint"]))
(defn- tree [p doc] (get-in (run p "docs" "show" doc) [:data :tree]))
(defn- content [p doc] (get-in (run p "docs" "show" doc) [:data :content-hash]))
(defn- node-ids [p] (set (map :id (:nodes (:data (run p "edges" "nodes"))))))

;; v1: api/test_api_edges.py::test_editing_one_document_rewrites_only_its_shard
(deftest editing-one-document-changes-only-that-document
  (let [p (world)
        [f12 f14 t12 t14 c12 c14] [(fingerprint p "t12") (fingerprint p "t14") (tree p "t12") (tree p "t14") (content p "t12") (content p "t14")]]
    (is (every? some? [f12 f14]))
    (spit (file p "t14.md") "# Table 14\n\nCapacity: 10\n")
    (is (true? (:ok (run p "docs" "update" "t14"))))
    (is (= f12 (fingerprint p "t12")) "the untouched document's node is identical")
    (is (= [t12 c12] [(tree p "t12") (content p "t12")]))
    (is (not= c14 (content p "t14")))
    (is (not= f14 (fingerprint p "t14")) "the edited document's node moved")
    (is (= t14 (tree p "t14")) "identity is nominal: the document keeps its tree")
    (is (= 10 (get-in (run p "docs" "show" "t14") [:data :payload "capacity"])))
    (is (true? (get-in (run p "stores" "check") [:data :documents :valid])))))

;; v1: api/test_api_edges.py::test_a_hand_edit_reaches_the_index_through_stores_update
;; (kimun: a hand-edited file is a decision, so `stores update` skips it and names `docs update`;
;;  once that runs the graph carries the new content)
(deftest a-hand-edit-reaches-the-graph-through-the-named-command
  (let [p (world)
        before (fingerprint p "t12")
        other (fingerprint p "t14")]
    (spit (file p "t12.md") "# Table 12\n\nCapacity: 7\n")
    (let [u (:data (run p "stores" "update"))]
      (is (= [{:document "t12" :category :edited :action "kimun docs update t12"}] (:skipped u))))
    (is (true? (:ok (run p "docs" "update" "t12"))))
    (is (not= before (fingerprint p "t12")))
    (is (= other (fingerprint p "t14")))
    (is (= [] (get-in (run p "stores" "check") [:warnings])))))

;; v1: api/test_api_edges.py::test_a_wiped_index_reads_as_stale_and_rebuild_brings_it_back
(deftest a-wiped-derived-index-changes-nothing-and-rebuild-brings-it-back
  (let [p (world)
        nodes (node-ids p)]
    (is (true? (:ok (run p "rebuild-indexes"))))
    (let [idx (file p ".kimun/index")]
      (is (fs/exists? idx) "the derived index is written under .kimun/index")
      (fs/delete-tree idx)
      (is (= nodes (node-ids p)) "the graph is derived from (store, head), not from the index")
      (is (true? (get-in (run p "stores" "check") [:data :documents :valid])))
      (is (true? (:ok (run p "rebuild-indexes"))))
      (is (fs/exists? idx))
      (is (= nodes (node-ids p))))
    (testing "the log and objects were never touched"
      (is (true? (:ok (run p "stores" "verify")))))))
