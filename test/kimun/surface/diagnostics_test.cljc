(ns kimun.surface.diagnostics-test
  "The diagnosis of a store (`kimun.surface.diagnostics`): categories, actions
   and the health verdict. Stores are built with the real commands, then the
   pure `report` is fed the files as a map. Each test names the v1 function
   (`tests/store/test_diagnostics.py`, `test_contract_in_model_node.py`) whose
   promise it carries."
  (:require [kimun.test-util :as ktu] [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.surface.diagnostics :as diag]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))

(def ticket
  {:model/name "TicketDoc" :model/version 1 :base []
   :fields [{:name "title" :type :string :required true :description "Title."}
            {:name "status" :type :string :required true :description "Status."}]
   :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"})

(def source "# T\n\nStatus: open\n")

(defn- project
  "A project with TicketDoc and the tracked document `login` (login.md)."
  []
  (let [p (str (ktu/temp-dir "kimun-diag-"))]
    (run p "stores" "init" "--name" "p")
    (spit (str (fs/path p "ticket.edn")) (pr-str ticket))
    (is (true? (:ok (run p "models" "define" "-f" "ticket.edn"))))
    (is (true? (:ok (run p "docs" "create" "TicketDoc" "login" "--set" "title=T" "--set" "status=open"))))
    p))

(defn- move-contract! [p]
  (is (true? (:ok (run p "models" "field-add" "TicketDoc" "--field" "priority" "--type" "int" "--description" "Field int." "--default" "5")))))

(defn- report
  "The report of project `p` when its files are `files` (`{path text}`)."
  ([p files] (report p files {}))
  ([p files opts] (diag/report (:store (cli-store/open! (str (fs/path p ".kimun")))) files opts)))

(defn- doc [r] (first (:documents r)))

(deftest clean-store-is-valid
  ;; v1: test_clean_store_is_valid
  (let [r (report (project) {"login.md" source})]
    (is (:valid r))
    (is (= [] (:findings r)))
    (is (= {:category :ok :model "TicketDoc" :document "login" :path "login.md" :action nil :content-ok true :fields-ok true}
           (select-keys (doc r) [:category :model :document :path :action :content-ok :fields-ok])))
    (is (= [{:category :ok :model "TicketDoc" :documents 1 :contract-ok true}]
           (mapv #(select-keys % [:category :model :documents :contract-ok]) (:models r))))
    (is (= {:status :ok :findings [] :actions [] :next nil} (diag/health r)))))

(deftest reformatting-moves-the-text-and-holds-the-fields
  ;; v1: test_benign_text_mutation, test_reformatting_moves_content_and_holds_fields_still,
  ;;     test_reformatted_documents_are_listed_but_keep_the_store_valid
  (let [r (report (project) {"login.md" (str source "\n\n")})
        d (doc r)]
    (is (= :reformatted (:category d)))
    (is (false? (:content-ok d)) "the markdown text changed")
    (is (true? (:fields-ok d)) "no field's value changed")
    (is (false? (:needs-human d)))
    (is (= "kimun docs update login" (:action d)))
    (is (= ["login"] (mapv :document (:findings r))) "listed")
    (is (:valid r) "an expected mutation must not fail the store")
    (is (str/includes? (:summary r) "reformatted (ok)"))
    (is (= :degraded (:status (diag/health r))))
    (is (= "run: kimun docs update login" (:next (diag/health r))))))

(deftest a-field-edit-is-a-data-change-that-needs-a-human
  ;; v1: test_data_mutation_is_invalid, test_a_field_edit_moves_both_content_and_fields,
  ;;     test_damaged_document_is_not_reported_as_roster_only, test_health_demands_a_human_when_a_field_was_edited
  (let [r (report (project) {"login.md" "# T\n\nStatus: closed\n"})
        d (doc r)
        h (diag/health r)]
    (is (= :edited (:category d)))
    (is (= [false false] [(:content-ok d) (:fields-ok d)]))
    (is (= ["status"] (:fields d)) "the field that moved is named")
    (is (true? (:needs-human d)))
    (is (not (:valid r)))
    (is (= {:edited 1} (:counts r)) "damage is counted as damage and as nothing else")
    (is (str/includes? (:message d) "decide first"))
    (is (= :needs-human (:status h)))
    (is (str/starts-with? (:next h) "inspect: login.md") "the action says inspect, not run")
    (is (some :needs-human (:findings h)))))

(deftest a-missing-file-is-a-decision
  ;; v1: test_missing_document
  (let [r (report (project) {})
        d (doc r)]
    (is (= :missing (:category d)))
    (is (not (:valid r)))
    (is (true? (:needs-human d)))
    (is (= "kimun docs untrack login" (:action d)))
    (is (str/includes? (:message d) "restore the file"))))

(deftest a-failed-verification-is-the-finding-corrupt
  ;; v1: test_tampered_hash_b — a stored hash that no longer signs its content
  (let [r (report (project) {"login.md" source} {:verify {:ok? false :checked 9 :bad ["abc"]}})
        f (first (:findings r))]
    (is (not (:valid r)))
    (is (= [:corrupt "kimun stores verify" ["abc"]] [(:category f) (:action f) (:bad f)]))
    (is (= :ok (:category (doc r))) "no document is accused")
    (is (= :needs-human (:status (diag/health r))))
    (is (:valid (report (project) {"login.md" source} {:verify {:ok? true :checked 9 :bad []}})))))

(deftest a-moved-contract-is-its-own-category
  ;; v1: test_a_moved_contract_is_reported_as_its_own_category,
  ;;     test_check_report_names_the_source_file_of_a_moved_contract,
  ;;     test_health_names_what_to_look_at_and_what_to_run
  (let [p (project)]
    (is (= :ok (:status (diag/health (report p {"login.md" source})))))
    (move-contract! p)
    (let [r (report p {"login.md" source})
          d (doc r)
          m (first (:models r))
          h (diag/health r)]
      (is (not (:valid r)))
      (is (= :contract-moved (:category d)))
      (is (nil? (:edited (:counts r))) "the document's text never moved, so it is not accused")
      (is (true? (:content-ok d)))
      (is (= [:contract-moved false] [(:category m) (:contract-ok m)]))
      (is (str/includes? (:message m) "contract"))
      (is (str/includes? (:inspect d) "kimun models history TicketDoc") "what to open is the model, not the document")
      (is (str/includes? (:inspect d) "not this document"))
      (is (= :degraded (:status h)))
      (is (= [{:category :contract-moved :model "TicketDoc" :document "login" :path "login.md"
               :action "kimun docs update login" :needs-human false}]
             (:findings h)))
      (is (= "run: kimun docs update login" (:next h))))))

(deftest a-moved-contract-does-not-excuse-an-edited-file
  ;; v1: test_a_moved_contract_does_not_excuse_an_edited_file
  (let [p (project)]
    (move-contract! p)
    (let [d (doc (report p {"login.md" "# Edited By Hand\n\nStatus: open\n"}))]
      (is (= :edited (:category d)) "an edited file must not be excused by the contract")
      (is (true? (:needs-human d))))))

(deftest a-text-that-no-longer-extracts-needs-a-human
  ;; kimun category without a v1 note: v1 hashed an empty payload and called it data_mutation
  (let [d (doc (report (project) {"login.md" "no heading at all\n"}))]
    (is (= :unextractable (:category d)))
    (is (true? (:needs-human d)))
    (is (string? (:error d)))))

(deftest several-safe-findings-are-one-command
  (let [p (project)]
    (run p "docs" "create" "TicketDoc" "other" "--set" "title=O" "--set" "status=open")
    (let [h (diag/health (report p {"login.md" (str source "\n") "other.md" "# O\n\nStatus: open\n\n"}))]
      (is (= :degraded (:status h)))
      (is (= ["kimun docs update login" "kimun docs update other"] (:actions h)))
      (is (= "run: kimun stores update" (:next h))))))

(deftest an-edit-that-adds-noise-is-noise-not-a-safe-reformat
  ;; an undeclared frontmatter key leaves the payload intact; it must not pass as reformatting
  (let [r (report (project) {"login.md" (str "---\nowner: me\n---\n\n" source)})
        d (doc r)]
    (is (= :noise (:category d)))
    (is (= ["owner"] (get-in d [:noise :frontmatter])))
    (is (true? (:needs-human d)))
    (is (not (:valid r)))))
