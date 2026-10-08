(ns kimun.parity.diagnostics-test
  "v1 `store/test_contract_in_model_node.py`: a model's contract moving
   (a field added) must not silently change the payload of documents whose
   text did not move: they keep being read under the contract they were last
   written with, and `stores update` moves them to the new one. Driven with
   `models field-add`, `stores update` and `docs show`."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run (vec argv) env cwd)))
(defn- file [p n] (str (fs/path p n)))

(def ticket
  (pr-str {:model/name "TicketDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}]
           :template "# ⸢rev•title⸥\n"}))

(defn- project []
  (let [p (str (ktu/temp-dir "kimun-parity-diag-"))]
    (run p "stores" "init" "--name" "p")
    (spit (file p "ticket.edn") ticket)
    (is (true? (:ok (run p "models" "define" "-f" "ticket.edn"))))
    (is (true? (:ok (run p "docs" "create" "TicketDoc" "login" "--set" "title=A note"))))
    p))

(defn- show [p] (:data (run p "docs" "show" "login")))
(defn- add-field! [p]
  (is (true? (:ok (run p "models" "field-add" "TicketDoc" "--field" "status" "--type" "string" "--default" "open")))))

;; v1: store/test_contract_in_model_node.py::test_update_re_extracts_documents_whose_text_did_not_move
;; kimun A1 (2026-10-08): the user's text did not move, but moving the document
;; to the new contract re-renders it (its old text cannot be re-read under a
;; template that adds a section after the last slot); the file is rewritten to
;; match and the new field reaches the content hash.
(deftest update-moves-documents-whose-text-did-not-move-to-the-new-contract
  (let [p (project)
        before (show p)
        md (slurp (file p "login.md"))]
    (add-field! p)
    (is (= :degraded (get-in (run p "stores" "health") [:data :status])) "the moved contract is visible")
    (is (true? (:ok (run p "stores" "update"))))
    (let [after (show p)]
      (is (not= md (slurp (file p "login.md"))) "the file is rewritten to the new contract")
      (is (not= (:source-hash before) (:source-hash after)) "the rendered source moved")
      (is (not= (:content-hash before) (:content-hash after)) "and the new field reached the content hash")
      (is (= {"title" "A note" "status" "open"} (:payload after)))
      (is (= :intact (:state after))))
    (is (= :ok (get-in (run p "stores" "health") [:data :status])))))

;; v1: store/test_contract_in_model_node.py::test_a_moved_contract_invalidates_the_document_cache
;; kimun A1 (2026-10-08): v1's promise was read as "the new contract answers at once", which is
;; exactly the silent payload change the A1 fix removes; the promise kept is that the *old* text
;; is never mis-read under the *new* template (body swallowed by a later section). The document
;; keeps its recorded contract until `stores update` moves it, then the new contract answers.
(deftest a-moved-contract-keeps-the-payload-until-the-document-is-updated
  (let [p (project)]
    (is (not (contains? (:payload (show p)) "status")))
    (add-field! p)
    (testing "read with no `stores update` in between: the recorded contract still answers"
      (let [d (show p)]
        (is (not (contains? (:payload d) "status")) "the old text is not re-read under the new contract")
        (is (= :superseded (:state d)) "and the read says the document is behind its model")))
    (testing "`stores update` moves it to the new contract"
      (is (true? (:ok (run p "stores" "update"))))
      (is (= {"title" "A note" "status" "open"} (:payload (show p)))))))
