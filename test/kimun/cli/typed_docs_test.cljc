(ns kimun.cli.typed-docs-test
  "Typed documents end to end on temporary stores: the `docs` lifecycle of
   sldb v1 (create, track, update, untrack, delete, show, list, status), its
   consequences for models (counts, superseded, backfill) and for the journal.
   Each test names in a comment the v1 functions whose promise it carries."
  (:require [kimun.test-util :as ktu] [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [kimun.cli.main :as main]
            [kimun.surface.docs :as tdocs]
            [kimun.host.default :as host]))

(def env {"USER" "tester"})
(defn- run
  "The envelope of a CLI run. An invalid store makes `stores check` the failure
   `store/invalid` (exit 5) whose error data is the whole report: it is lifted
   to `:data`/`:warnings` so an assertion reads one shape either way."
  [cwd & argv]
  (let [e (:envelope (main/run argv env cwd))]
    (if (= "store/invalid" (get-in e [:error :type]))
      (assoc e :data (get-in e [:error :data]) :warnings (get-in e [:error :data :warnings]))
      e)))

(def ticket
  (pr-str {:model/name "TicketDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "status" :type :string :required true :description "Status."}]
           :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"}))

(defn- project
  "A project with a store and the TicketDoc model defined."
  []
  (let [p (str (ktu/temp-dir "kimun-typed-"))]
    (run p "stores" "init" "--name" "p")
    (spit (str (fs/path p "ticket.edn")) ticket)
    (is (true? (:ok (run p "models" "define" "-f" "ticket.edn"))))
    p))

(defn- file [p & parts] (str (apply fs/path p parts)))

(deftest create-writes-markdown-and-tracks-it
  ;; v1: test_create_document_writes_markdown_and_tracks_it, test_cli_docs_create_names_the_document,
  ;;     test_doc_add_creates_file_and_tracks, test_doc_add_from_yaml_file, test_docs_create_accepts_long_inline_json_payload
  (let [p (project)
        e (run p "docs" "create" "TicketDoc" "login" "--set" "title=Broken login" "--set" "status=open")]
    (is (true? (:ok e)) (pr-str e))
    (is (= "# Broken login\n\nStatus: open\n" (slurp (file p "login.md"))))
    (is (= {"title" "Broken login" "status" "open"} (get-in e [:data :payload])))
    (is (= "login" (get-in e [:data :name])))
    (is (= "login.md" (get-in e [:data :path])) "the path is relative to the project root")
    (is (= :intact (get-in e [:data :state])))
    (is (= [] (:warnings e)))
    (testing "from a YAML payload file, with an explicit path"
      (spit (file p "b.yaml") "title: Second\nstatus: closed\n")
      (let [e2 (run p "docs" "create" "TicketDoc" "second" "--data" "b.yaml" "--path" "notes/second.md")]
        (is (true? (:ok e2)) (pr-str e2))
        (is (= "notes/second.md" (get-in e2 [:data :path])))
        (is (str/includes? (slurp (file p "notes" "second.md")) "Status: closed"))))
    (is (= ["login" "second"] (mapv :name (:data (run p "docs" "list" "--model" "TicketDoc")))))
    (is (true? (:ok (run p "stores" "verify"))))))

(deftest create-writes-nothing-for-a-rejected-payload
  ;; v1: test_create_document_writes_nothing_for_a_payload_the_model_rejects, test_doc_track_fails_model_not_registered,
  ;;     test_doc_track_fails_duplicate, test_save_document_payload_refuses_unknown_documents_and_models, test_doc_update_fails_unknown_doc
  (let [p (project)
        bad (run p "docs" "create" "TicketDoc" "nope" "--set" "title=Only a title")]
    (is (false? (:ok bad)))
    (is (= 3 (:exit bad)))
    (is (not (fs/exists? (file p "nope.md"))))
    (is (= [] (:data (run p "docs" "list"))))
    (is (= 1 (:exit (run p "docs" "create" "GhostDoc" "x" "--set" "title=t"))) "unknown model is missing")
    (run p "docs" "create" "TicketDoc" "one" "--set" "title=T" "--set" "status=s")
    (let [dup (run p "docs" "create" "TicketDoc" "one" "--set" "title=T" "--set" "status=s" "--path" "other.md")]
      (is (= "docs/exists" (get-in dup [:error :type])))
      (is (= 3 (:exit dup))))
    (let [u (run p "docs" "update" "ghost" "--set" "title=x")]
      (is (false? (:ok u))))
    (is (= "docs/missing" (get-in (run p "docs" "show" "ghost") [:error :type])))))

(deftest track-registers-an-existing-file
  ;; v1: test_doc_track_registers_document, test_doc_track_custom_name, test_doc_track_fails_idempotency,
  ;;     test_doc_track_force_bypasses_idempotency, test_track_document_file_refuses_a_file_the_model_cannot_read,
  ;;     test_track_document_file_resolves_relative_paths_against_the_project_root, test_a_document_that_fits_its_model_still_tracks
  (let [p (project)
        sub (str (fs/create-dirs (fs/path p "docs")))]
    (spit (file sub "a.md") "# A title\n\nStatus: open\n")
    (let [e (run sub "docs" "track" "TicketDoc" "a.md" "--name" "alpha")]
      (is (true? (:ok e)) (pr-str e))
      (is (= "alpha" (get-in e [:data :name])))
      (is (= "docs/a.md" (get-in e [:data :path])) "relative to the project root, not to the cwd")
      (is (= {"title" "A title" "status" "open"} (get-in e [:data :payload])))
      (is (= "# A title\n\nStatus: open\n" (slurp (file sub "a.md"))) "tracking does not rewrite the file"))
    (testing "a file that does not round-trip is refused unless forced"
      (spit (file p "loose.md") "#   Loose   \n\nStatus:    open\n")
      (let [e (run p "docs" "track" "TicketDoc" "loose.md")]
        (is (= "docs/not-idempotent" (get-in e [:error :type])))
        (is (= 3 (:exit e))))
      (is (true? (:ok (run p "docs" "track" "TicketDoc" "loose.md" "--force")))))
    (testing "a file the model cannot read is refused and stays untracked"
      (spit (file p "bad.md") "nothing the template explains\n")
      (is (false? (:ok (run p "docs" "track" "TicketDoc" "bad.md" "--force"))))
      (is (= ["alpha" "loose"] (mapv :name (:data (run p "docs" "list"))))))))

(deftest update-rewrites-and-records-what-changed
  ;; v1: test_save_document_payload_rewrites_markdown_and_indexes, test_doc_update_rewrites_and_reindexes,
  ;;     test_save_entry_carries_before_and_after (field part), test_save_entry_derives_field,
  ;;     test_each_document_route_leaves_an_entry, test_hash_fields_differs_on_data_change
  (let [p (project)
        c (run p "docs" "create" "TicketDoc" "login" "--set" "title=Broken login" "--set" "status=open")
        u (run p "docs" "update" "login" "--set" "title=New")]
    (is (true? (:ok u)) (pr-str u))
    (is (= "# New\n\nStatus: open\n" (slurp (file p "login.md"))))
    (is (= {"title" "New" "status" "open"} (get-in u [:data :payload])))
    (is (not= (get-in c [:data :content-hash]) (get-in u [:data :content-hash])))
    (is (not= (get-in c [:data :source-hash]) (get-in u [:data :source-hash])))
    (is (= (get-in c [:data :field-hashes "status"]) (get-in u [:data :field-hashes "status"])) "only the changed field's hash moves")
    (let [[save create] (:data (run p "journal" "show" "--limit" "2"))]
      (is (= "docs update" (:operation save)))
      (is (= "TicketDoc:login" (:address save)))
      (is (= {"title" {:before "Broken login" :after "New"}} (:fields save)))
      (is (= "docs create" (:operation create)))
      (is (= [(:revision create)] (:parents save))))
    (testing "an identical update writes nothing"
      (let [head (:revision (run p "stores" "check"))
            same (run p "docs" "update" "login")]
        (is (true? (get-in same [:data :unchanged?])))
        (is (= head (:revision (run p "stores" "check"))))))))

(deftest a-file-edited-outside-is-reported-then-reconciled
  ;; v1: test_store_check_data_mutation_fails, test_store_update_recomputes_hashes, test_store_update_skips_missing_doc,
  ;;     test_store_check_clean_passes, test_model_add_passes_stores_check_immediately_with_no_documents
  (let [p (project)]
    (is (= [] (:warnings (run p "stores" "check"))) "a model without documents checks clean")
    (run p "docs" "create" "TicketDoc" "login" "--set" "title=T" "--set" "status=open")
    (is (= [] (:warnings (run p "stores" "check"))))
    (spit (file p "login.md") "# T\n\nStatus: closed\n")
    (let [chk (run p "stores" "check")]
      (is (= [false 5 "store/invalid"] [(:ok chk) (:exit chk) (get-in chk [:error :type])]) "an edited file makes the store invalid: check fails, as v1's did")
      (is (= [{:document "login" :model "TicketDoc" :state :intact :category :edited}]
             (mapv #(select-keys % [:document :model :state :category]) (get-in chk [:data :documents :findings]))))
      (is (= "docs/file-modified" (:type (first (:warnings chk))))))
    (is (= [{:name "login" :path "login.md" :status :modified}] (:data (run p "docs" "status"))))
    (is (= "closed" (get-in (run p "docs" "update" "login") [:data :payload "status"])))
    (is (= [] (:warnings (run p "stores" "check"))))
    (fs/delete (file p "login.md"))
    (is (= :missing (:status (first (:data (run p "docs" "status"))))))
    (is (= "docs/file-missing" (:type (first (:warnings (run p "stores" "check"))))))))

(deftest untrack-keeps-the-file-and-delete-removes-it
  ;; v1: test_untrack_document_drops_it_from_the_store, test_doc_untrack_removes_document_from_store,
  ;;     test_doc_untrack_keeps_the_markdown_file, test_doc_delete_removes_both_the_store_entry_and_the_file,
  ;;     test_doc_delete_without_yes_aborts_and_touches_nothing, test_untrack_then_track_repair_leaves_both
  (let [p (project)]
    (run p "docs" "create" "TicketDoc" "a" "--set" "title=A" "--set" "status=s")
    (run p "docs" "create" "TicketDoc" "b" "--set" "title=B" "--set" "status=s")
    (is (true? (:ok (run p "docs" "untrack" "a"))))
    (is (fs/exists? (file p "a.md")))
    (is (= ["b"] (mapv :name (:data (run p "docs" "list")))))
    (is (true? (:ok (run p "docs" "track" "TicketDoc" "a.md"))) "the same name can be tracked again")
    (is (= ["docs track" "docs untrack"] (mapv :operation (take 2 (:data (run p "journal" "show"))))))
    (let [no (run p "docs" "delete" "b")]
      (is (= "docs/confirmation-required" (get-in no [:error :type])))
      (is (fs/exists? (file p "b.md")))
      (is (= ["a" "b"] (mapv :name (:data (run p "docs" "list"))))))
    (is (true? (:ok (run p "docs" "delete" "b" "--yes"))))
    (is (not (fs/exists? (file p "b.md"))))
    (is (= ["a"] (mapv :name (:data (run p "docs" "list")))))
    (is (true? (:ok (run p "journal" "verify"))))))

(deftest redefining-a-model-with-documents
  ;; v1: test_promote_required_field_without_default_is_rejected_with_clear_error, test_dry_run_reports_backfill_documents,
  ;;     test_promote_without_backfill_leaves_document_unchanged, test_promote_with_backfill_rewrites_documents,
  ;;     test_full_roundtrip_add_backfill_write_and_reread, test_a_moved_contract_is_reported_as_its_own_category,
  ;;     test_store_is_valid_again_after_update, test_models_fields_add_and_promote_bumps_model_version, test_model_update_reindexes_docs
  (let [p (project)]
    (run p "docs" "create" "TicketDoc" "login" "--set" "title=T" "--set" "status=open")
    (let [rej (run p "models" "field-add" "TicketDoc" "--field" "owner" "--type" "string" "--description" "Field string." "--required")]
      (is (false? (:ok rej)))
      (is (= 3 (:exit rej)))
      (is (str/includes? (get-in rej [:error :message]) "lacks required field"))
      (is (= 1 (:version (first (:data (run p "models" "list"))))) "nothing was defined"))
    (let [dry (run p "models" "field-add" "TicketDoc" "--field" "priority" "--type" "int" "--description" "Field int." "--default" "5" "--dry-run")]
      (is (= ["login"] (get-in dry [:data :backfill])))
      (is (= 1 (:version (first (:data (run p "models" "list")))))))
    (let [before (slurp (file p "login.md"))
          e (run p "models" "field-add" "TicketDoc" "--field" "priority" "--type" "int" "--description" "Field int." "--default" "5")]
      (is (= 2 (get-in e [:data :version])))
      (is (= before (slurp (file p "login.md"))) "without --backfill the document is untouched")
      (is (= :superseded (:state (first (:data (run p "docs" "list"))))))
      (is (= {:documents 1 :not-intact 1} (select-keys (first (:data (run p "models" "list"))) [:documents :not-intact])))
      (is (= "docs/superseded" (:type (first (:warnings (run p "stores" "check")))))))
    (let [e (run p "models" "field-add" "TicketDoc" "--field" "area" "--type" "string" "--description" "Field string." "--default" "core" "--backfill")]
      (is (= ["login"] (get-in e [:data :backfilled])))
      (let [entry (first (:data (run p "journal" "show" "--limit" "1")))]
        (is (= "models backfill" (:operation entry)))
        (is (= {"priority" {:before nil :after 5} "area" {:before nil :after "core"}} (:fields entry))
            "the journal names the fields the backfill added"))
      (is (str/includes? (slurp (file p "login.md")) "core"))
      (let [d (:data (run p "docs" "show" "login"))]
        (is (= :intact (:state d)))
        (is (= 5 (get-in d [:payload "priority"])))
        (is (= "core" (get-in d [:payload "area"]))))
      (is (= [] (:warnings (run p "stores" "check"))))
      (is (true? (:ok (run p "stores" "verify")))))))

;; v1: test_validate_reports_without_promoting (against documents), test_models_validate_rejects_draft_with_unknown_field_reference
(deftest models-check-validates-the-documents
  (let [p (project)]
    (run p "docs" "create" "TicketDoc" "login" "--set" "title=T" "--set" "status=open")
    (let [ok (run p "models" "check" "TicketDoc")]
      (is (true? (:ok ok)) (pr-str ok))
      (is (= 1 (get-in ok [:data :models 0 :documents]))))
    (testing "a draft that the tracked document cannot satisfy is reported, and nothing is defined"
      (spit (file p "draft.edn")
            (pr-str {:model/name "TicketDoc" :model/version 1 :base []
                     :fields [{:name "title" :type :string :required true :description "Title."}
                              {:name "status" :type [:enum "todo" "done"] :required true :description "Status."}]
                     :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"}))
      (let [bad (run p "models" "check" "-f" "draft.edn")]
        (is (false? (:ok bad)))
        (is (= 3 (:exit bad)))
        (is (re-find #"document `login`" (get-in bad [:error :message])))
        (is (= 1 (:version (first (:data (run p "models" "list"))))))))))

(deftest hashes-are-v1-s
  ;; v1: test_hash_text_deterministic, test_hash_text_sensitive, test_hash_text_hex_64, test_hash_fields_deterministic,
  ;;     test_hash_fields_ignores_non_field_content, test_hash_fields_differs_on_data_change
  (let [h host/host]
    (is (= (tdocs/text-hash h "hola") (tdocs/text-hash h "hola")))
    (is (not= (tdocs/text-hash h "hola") (tdocs/text-hash h "hola ")))
    (is (re-matches #"[0-9a-f]{64}" (tdocs/text-hash h "x")))
    (is (= "071500e481796d46fe26f258fcdb0c93512f8ec504d93ff9e7b624f7d58a362d"
           (tdocs/content-hash h {"title" "Broken_login" "status" "open"}))
        "sha256 of Python's json.dumps(payload, sort_keys=True, ensure_ascii=False)")
    (is (= (tdocs/content-hash h {"a" 1 "b" [1 "x"]}) (tdocs/content-hash h {"b" [1 "x"] "a" 1})) "key order does not matter")
    (is (= "{\"a\": [1, \"é\\n\"], \"b\": null, \"c\": true}" (tdocs/py-json {"c" true "b" nil "a" [1 "é\n"]})))
    (let [p (project)
          a (run p "docs" "create" "TicketDoc" "a" "--set" "title=T" "--set" "status=s")]
      (spit (file p "b.md") "# T\n\nStatus: s\n")
      (let [b (run p "docs" "track" "TicketDoc" "b.md")]
        (is (= (get-in a [:data :content-hash]) (get-in b [:data :content-hash])) "same payload, same content hash")))))

;; v1: test_changed_paths_include_dotted_nested_keys
(deftest changed-paths-include-dotted-nested-keys
  (is (= {"meta.owner" {:before "a" :after "b"} "tags" {:before ["x"] :after ["x" "y"]} "new" {:before nil :after 1}}
         (tdocs/changed-fields {"meta" {"owner" "a" "kind" "k"} "tags" ["x"] "same" 1}
                               {"meta" {"owner" "b" "kind" "k"} "tags" ["x" "y"] "same" 1 "new" 1}))))

(deftest untyped-documents-still-work
  (let [p (project)]
    (spit (file p "free.md") "# Free\n\ntext\n")
    (is (true? (:ok (run p "docs" "ingest" "free.md"))))
    (run p "docs" "create" "TicketDoc" "t" "--set" "title=T" "--set" "status=s")
    (is (= [{:name "free"} {:name "t" :model "TicketDoc"}] (mapv #(select-keys % [:name :model]) (:data (run p "docs" "list")))))
    (is (= "# Free\n\ntext\n" (:data (run p "docs" "render" "free"))))
    (is (string? (:data (run p "docs" "render" "t"))) "a typed document renders its tree too")))

(deftest backfill-reads-under-the-old-contract-and-renders-under-the-new
  ;; regression (smoke 2026-10-06): the backfill extracted the old text with the
  ;; NEW template, so a section added after `## Body` swallowed the body text
  ;; and the default never appeared.
  (let [proj (str (ktu/temp-dir "kimun-backfill-"))
        run (fn [& argv] (:envelope (main/run argv {"USER" "tester"} proj)))]
    (run "stores" "init")
    (spit (str (fs/path proj "ticket.edn")) (slurp "test/fixtures/models/ticket.edn"))
    (is (true? (:ok (run "models" "define" "-f" "ticket.edn"))))
    (is (true? (:ok (run "docs" "create" "TicketDoc" "login" "--set" "title=Broken login" "--set" "body=Users cannot log in."))))
    (let [e (run "models" "field-add" "TicketDoc" "--field" "priority" "--type" "int" "--description" "Field int." "--default" "5" "--backfill")
          payload (get-in (run "docs" "show" "login") [:data :payload])
          text (slurp (str (fs/path proj "login.md")))]
      (is (true? (:ok e)) (pr-str e))
      (is (= ["login"] (get-in e [:data :backfilled])))
      (is (= "Users cannot log in." (get payload "body")) "the body keeps its text")
      (is (= 5 (get payload "priority")) "the new field carries its default")
      (is (= "Broken login" (get payload "title")))
      (is (re-find #"## Body\n\nUsers cannot log in\." text))
      (is (re-find #"(?i)priority[\s\S]*5" text))
      (is (= "intact" (name (get-in (run "docs" "show" "login") [:data :state])))))))
