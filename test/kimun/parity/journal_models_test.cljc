(ns kimun.parity.journal-models-test
  "Model writes leave journal entries: ports of sldb v1 `api/test_journal.py`
   `test_add_model_leaves_an_entry` and `test_reindex_and_promote_leave_entries`.
   v1 named operations (`add_model`, `reindex_model`, `promote_model_draft`); v2's
   journal is a view of the revision log, so an entry is a revision on the `models`
   tree, and `journal verify` is the validity check."
  (:require [clojure.test :refer [deftest is]]
            [babashka.fs :as fs]
            [kimun.cli.main :as main]))

(def env {"USER" "tester"})
(def ticket (str (fs/absolutize "test/fixtures/models/ticket.edn")))

(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- entries [p] (:data (run p "journal" "show")))
(defn- models-entry? [e] (some #(= "models" (:name %)) (:trees e)))

;; v1: api/test_journal.py::test_add_model_leaves_an_entry
(deftest defining-a-model-leaves-a-journal-entry
  (let [p (str (fs/create-temp-dir {:prefix "kimun-parity-jm-"}))
        _ (run p "stores" "init" "--name" "p")
        _ (is (empty? (entries p)) "a fresh store has no history")
        _ (run p "models" "define" "-f" ticket)
        es (entries p)]
    (is (= 1 (count es)))
    (is (models-entry? (first es)))
    (is (= "human/tester" (:actor (first es))))
    (is (= [] (get-in (run p "journal" "verify") [:data :bad])))
    (is (true? (:ok (run p "journal" "verify"))))))

;; v1: api/test_journal.py::test_reindex_and_promote_leave_entries
;; (v1 `reindex_model` and `promote_model_draft` are `models field-add|template` here, each one
;; revision; `rebuild-indexes` writes only the derived cache and so, by design, no entry)
(deftest field-add-and-template-edits-each-leave-an-entry-while-reindexing-leaves-none
  (let [p (str (fs/create-temp-dir {:prefix "kimun-parity-jm-"}))
        _ (run p "stores" "init" "--name" "p")
        _ (run p "models" "define" "-f" ticket)
        n0 (count (entries p))
        _ (run p "models" "field-add" "TicketDoc" "--field" "priority" "--type" "int" "--default" "5")
        n1 (count (entries p))
        _ (spit (str (fs/path p "t.md")) "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n\n## Body\n\n⸢rev•body⸥\n")
        _ (run p "models" "template" "TicketDoc" "-f" "t.md")
        n2 (count (entries p))
        head (get-in (run p "stores" "check") [:data :head])
        _ (run p "rebuild-indexes")]
    (is (= [1 2 3] [n0 n1 n2]) "define, field-add, template: one revision each")
    (is (every? models-entry? (entries p)))
    (is (= head (get-in (run p "stores" "check") [:data :head])) "rebuilding the derived index writes no revision")
    (is (= 3 (:version (first (:data (run p "models" "list"))))))
    (is (= [] (get-in (run p "journal" "verify") [:data :bad])))))
