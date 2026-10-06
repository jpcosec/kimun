(ns kimun.parity.diagnostics-test
  "v1 `store/test_contract_in_model_node.py`: a model's contract moving
   (a field added) must reach the content hash of documents whose text did not
   move, and a read must never serve the payload of the old contract. Driven
   with `models field-add`, `stores update` and `docs show`."
  (:require [babashka.fs :as fs]
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
  (let [p (str (fs/create-temp-dir {:prefix "kimun-parity-diag-"}))]
    (run p "stores" "init" "--name" "p")
    (spit (file p "ticket.edn") ticket)
    (is (true? (:ok (run p "models" "define" "-f" "ticket.edn"))))
    (is (true? (:ok (run p "docs" "create" "TicketDoc" "login" "--set" "title=A note"))))
    p))

(defn- show [p] (:data (run p "docs" "show" "login")))
(defn- add-field! [p]
  (is (true? (:ok (run p "models" "field-add" "TicketDoc" "--field" "status" "--type" "string" "--default" "open")))))

;; v1: store/test_contract_in_model_node.py::test_update_re_extracts_documents_whose_text_did_not_move
(deftest update-re-extracts-documents-whose-text-did-not-move
  (let [p (project)
        before (show p)
        md (slurp (file p "login.md"))]
    (add-field! p)
    (is (= :degraded (get-in (run p "stores" "health") [:data :status])) "the moved contract is visible")
    (is (true? (:ok (run p "stores" "update"))))
    (let [after (show p)]
      (is (= md (slurp (file p "login.md"))) "no rewrite of the Markdown happened")
      (is (= (:source-hash before) (:source-hash after)) "the text did not move")
      (is (not= (:content-hash before) (:content-hash after)) "but the new field reached the content hash")
      (is (= {"title" "A note" "status" "open"} (:payload after)))
      (is (= :intact (:state after))))
    (is (= :ok (get-in (run p "stores" "health") [:data :status])))))

;; v1: store/test_contract_in_model_node.py::test_a_moved_contract_invalidates_the_document_cache
(deftest a-moved-contract-is-never-served-from-the-old-payload
  (let [p (project)]
    (is (not (contains? (:payload (show p)) "status")))
    (add-field! p)
    (testing "read with no `stores update` in between"
      (let [d (show p)]
        (is (= "open" (get-in d [:payload "status"])) "the stale payload did not win")
        (is (= :superseded (:state d)) "and the read says the document is behind its model")))))
