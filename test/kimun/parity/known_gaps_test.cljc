(ns kimun.parity.known-gaps-test
  "Known parity gaps: promises sldb v1 held that kimun does not hold today, found
   while closing the parity books (docs/v2/tests/parity-gaps.edn lists each with
   the smallest fix seen). Every deftest asserts kimun's CURRENT behaviour under a
   `;; GAP:` comment, so the suite stays green and the gap stays visible: the day
   a gap is fixed its deftest turns red and must be rewritten as the v1 promise
   and moved next to the ports of its v1 file."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run (vec argv) env cwd)))

;; ---------------------------------------------------------------- edges and graph across linked stores; field-add of an optional field
(defn- file [p n] (str (fs/path p n)))

(defn- table-store [doc]
  (let [p (str (ktu/temp-dir "kimun-gap-"))]
    (run p "stores" "init" "--name" "p")
    (spit (file p "table.edn")
          (pr-str {:model/name "Table" :model/version 1 :base []
                   :fields [{:name "title" :type :string :required true :description "Title."}]
                   :template "# ⸢rev•title⸥\n"}))
    (is (true? (:ok (run p "models" "define" "-f" "table.edn"))))
    (is (true? (:ok (run p "docs" "create" "Table" doc "--set" (str "title=" doc)))))
    p))

(defn- linked-world []
  (let [p (table-store "t12") q (table-store "t99")]
    (is (true? (:ok (run p "stores" "link" "annex" q))))
    p))

;; v1: api/test_api_edges.py::test_linked_store_documents_come_back_qualified
;; GAP: v1 promised the edge index of a store with links carries the linked store's documents
;; qualified by the link name (`annex:Table:t99`, `semantics.store == "annex"`, `has_document`
;; edge from the model); kimun's `edges` view holds only the local store's documents.
(deftest linked-store-documents-are-not-in-the-edge-view
  (let [p (linked-world)
        ids (set (map :id (:nodes (:data (run p "edges" "nodes")))))]
    (is (contains? ids "sldb://document/Table:t12"))
    (is (not-any? #(re-find #"annex|t99" %) ids))
    (is (nil? (:data (run p "edges" "node" "annex:Table:t99")))
        "the qualified node does not exist")))

;; v1: api/test_graph_source_parity.py::test_snapshot_save_keeps_the_legacy_route_for_linked_stores_gap_G5
;; GAP: v1 promised `snapshot_save` of a store with links composes the linked store's documents
;; (ids `sldb://document/bee:...`) unless include_linked=False; kimun's `graph export` has no
;; federation and exports only the local store.
(deftest graph-export-does-not-federate-linked-stores
  (let [p (linked-world)
        ids (set (map #(get % "id") (get-in (run p "graph" "export") [:data "nodes"])))]
    (is (contains? ids "sldb://document/Table:t12"))
    (is (not-any? #(re-find #"annex|t99" %) ids))))
