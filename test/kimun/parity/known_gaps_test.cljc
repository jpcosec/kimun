(ns kimun.parity.known-gaps-test
  "Known parity gaps: promises sldb v1 held that kimun does not hold today, found
   while closing the parity books (docs/v2/tests/parity-gaps.edn lists each with
   the smallest fix seen). Every deftest asserts kimun's CURRENT behaviour under a
   `;; GAP:` comment, so the suite stays green and the gap stays visible: the day
   a gap is fixed its deftest turns red and must be rewritten as the v1 promise
   and moved next to the ports of its v1 file."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.surface.models.descriptor :as descriptor]
            [sldb.kernel.store :as store]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run (vec argv) env cwd)))

;; ---------------------------------------------------------------- kernel: concurrent writers (log appended before the heads compare-and-swap)
(defn- project []
  (let [p (str (fs/create-temp-dir {:prefix "kimun-gap-internalsa-"}))]
    (run p "stores" "init" "--name" "p")
    (spit (str (fs/path p "note.edn"))
          (pr-str {:model/name "Note" :model/version 1 :base []
                   :fields [{:name "title" :type :string :required true :description "Title."}]
                   :template "# ⸢rev•title⸥\n"}))
    (is (true? (:ok (run p "models" "define" "-f" "note.edn"))))
    p))

(defn- log-lines [p] (count (remove str/blank? (str/split-lines (slurp (str (fs/path p ".kimun" "log.edn")))))))

;; v1: store/test_io_atomic_write.py::test_atomic_write_survives_concurrent_writers
;; v1: store/test_update_fault_tolerance.py::test_store_lock_fails_fast_when_busy
;; v1: store/test_update_fault_tolerance.py::test_store_lock_wait_blocks_until_released
;; GAP: v1 promised that concurrent writers leave a valid store — the one that cannot write fails
;;      fast (`busy`) without damage, and with `--wait` succeeds once the other is done; kimun
;;      rejects the losing writer with `store/stale` AFTER appending its transaction to log.edn,
;;      which leaves the store unreadable (`store/error`: replayed transaction id differs from
;;      log) for every later command, the retry included.
(deftest a-writer-that-loses-the-race-leaves-the-store-unreadable
  (let [p (project)
        real store/commit!
        raced (atom false)
        winner (atom nil)
        before (log-lines p)
        ;; the interleaving, made deterministic: another writer commits between the moment the
        ;; losing command loaded the store and the moment it persists its own transaction
        loser (with-redefs [store/commit! (fn [b s plan]
                                            (when (compare-and-set! raced false true)
                                              (reset! winner (run p "docs" "create" "Note" "winner" "--set" "title=W")))
                                            (real b s plan))]
                (run p "docs" "create" "Note" "loser" "--set" "title=L"))]
    (is (true? @raced))
    (is (true? (:ok @winner)))
    (testing "what holds: the losing writer fails fast, with the conflict exit code and a reason"
      (is (= "store/stale" (get-in loser [:error :type])))
      (is (= 4 (:exit loser))))
    (testing "what does not: its transaction is already in the log"
      (is (= (+ before 2) (log-lines p)) "v1 promise would be (+ before 1): only the winner's"))
    (testing "so the store is unreadable from then on"
      (doseq [argv [["stores" "check"] ["docs" "list"] ["journal" "verify"]
                    ["docs" "create" "Note" "loser" "--set" "title=L"]]]
        (let [e (apply run p argv)]
          (is (false? (:ok e)) (pr-str argv))
          (is (contains? #{"store/error" "store/corrupt"} (get-in e [:error :type])) (pr-str argv))
          (is (= 5 (:exit e))))))))

;; ---------------------------------------------------------------- edges and graph across linked stores; field-add of an optional field
(defn- file [p n] (str (fs/path p n)))

(defn- table-store [doc]
  (let [p (str (fs/create-temp-dir {:prefix "kimun-gap-"}))]
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

;; v1: store/test_optional_field_hash_neutral.py::test_model_extension_is_neutral_for_document_that_does_not_use_field
;; (the `models field-add` form of the promise; the `models define` form is ported in
;; kimun.parity.optional-field-hash-test)
;; GAP: v1 promised adding an optional field leaves a document that does not use it unchanged;
;; `models field-add NAME --field F --type string` appends a trailing `## F` section with an
;; optrev slot to the template, and an existing document whose body is the last slot then
;; extracts with its body moved into the new field (`body ""`, `summary_en "World body"`).
(deftest field-add-of-an-optional-field-rebinds-the-last-slot-of-existing-documents
  (let [p (str (fs/create-temp-dir {:prefix "kimun-gap-fa-"}))]
    (run p "stores" "init" "--name" "p")
    (spit (file p "n.edn")
          (pr-str {:model/name "Note" :model/version 1 :base []
                   :fields [{:name "title" :type :string :required true :description "t"}
                            {:name "body" :type :string :required true :description "b"}]
                   :template "---\ntitle: ⸢rev•title⸥\n---\n\n# ⸢render•title⸥\n\n⸢rev•body⸥"}))
    (is (true? (:ok (run p "models" "define" "-f" "n.edn"))))
    (is (true? (:ok (run p "docs" "create" "Note" "hello" "--set" "title=Hello" "--set" "body=World body"))))
    (is (true? (:ok (run p "models" "field-add" "Note" "--field" "summary_en" "--type" "string"))))
    (is (= {"title" "Hello" "body" "" "summary_en" "World body"}
           (get-in (run p "docs" "show" "hello") [:data :payload])))))

;; ---------------------------------------------------------------- CLI: docs update of an untracked document
(def ticket (str (fs/absolutize "test/fixtures/models/ticket.edn")))


;; v1: api/test_api_documents.py::test_cli_save_payload_still_exits_with_the_message
;; GAP: v1 promised that saving a payload for a document that is not tracked fails
;; with a message naming it ("Doc 'ghost' not registered."); kimun answers a generic
;; usage error (exit 6) about --name / a Markdown FILE that never mentions the document.
(deftest update-payload-of-an-untracked-document-is-a-generic-usage-error
  (let [p (str (fs/create-temp-dir {:prefix "kimun-gap-clistore-"}))]
    (run p "stores" "init" "--name" "p")
    (run p "models" "define" "-f" ticket)
    (let [e (run p "docs" "update" "ghost" "--set" "title=x")]
      (is (false? (:ok e)))
      (is (= [6 "cli/usage"] [(:exit e) (get-in e [:error :type])]))
      (is (not (str/includes? (get-in e [:error :message]) "ghost"))))))

;; ---------------------------------------------------------------- engine: field descriptions
;; v1: test_standalone.py::test_structured_nl_doc_requires_field_descriptions
;; GAP: v1 promised a model whose field has no (or an empty) description is rejected ("fields must define a non-empty description: title"); kimun accepts it
(deftest descriptor-accepts-a-field-without-description
  (let [base {:model/name "MissingDescriptionDoc" :model/version 1 :base []
              :template "# ⸢rev•title⸥"}
        no-description (assoc base :fields [{:name "title" :type :string :required true}])
        empty-description (assoc base :fields [{:name "title" :type :string :required true :description ""}])]
    (is (= no-description (descriptor/validate! no-description)))
    (is (= empty-description (descriptor/validate! empty-description)))))
