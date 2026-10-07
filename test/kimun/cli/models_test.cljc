(ns kimun.cli.models-test
  "The `models` group and the direct commands end to end on temporary stores:
   the ports of sldb v1's api/test_api_models.py, api/test_model_lifecycle.py,
   api/test_api_model_drafts.py, store/test_contract_in_model_node.py,
   store/test_models_io.py, store/test_resolver.py and the CLI-level promises
   of test_standalone.py. Each deftest names the v1 test(s) it ports."
  (:require [kimun.test-util :as ktu] [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [cheshire.core :as json]
            [kimun.cli.main :as main]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.models.edit :as edit]
            [kimun.surface.models.store :as mstore]
            [sldb.host.default :as host]))

(def env {"USER" "tester"})
(def ticket (str (fs/absolutize "test/fixtures/models/ticket.edn")))
(def ticket-d (edn/read-string (slurp ticket)))
(def doc "# Login roto\n\nStatus: open\n\n## Tags\n\n- auth\n- urgente\n\n## Body\n\nNo deja entrar.\n")
(def v1-src "/home/jp/proyectos/hum-ecosystem/tools/sldb/src")

(defn- tmp [] (str (ktu/temp-dir "kimun-models-")))
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- project [] (let [p (tmp)] (run p "stores" "init" "--name" "p") p))
(defn- write! [dir name text] (let [p (str (fs/path dir name))] (spit p text) p))
(defn- store-of [proj] (:store (cli-store/open! (str (fs/path proj ".kimun")))))

(deftest define-registers-version-one
  ;; v1: test_add_model_registers_version_one, test_load_registered_model_keeps_the_entry_inside_the_store_index
  (let [p (project) e (run p "models" "define" "-f" ticket)]
    (is (true? (:ok e)) (pr-str e))
    (is (= {:name "TicketDoc" :version 1 :created? true :unchanged? false :backfill [] :backfilled []} (dissoc (:data e) :id)))
    (is (= [{:name "TicketDoc" :version 1 :id (get-in e [:data :id]) :fields 4 :documents 0 :not-intact 0}] (:data (run p "models" "list"))))
    (is (= 1 (get-in (run p "stores" "check") [:data :trees])) "the models tree lives in the store")
    (is (true? (:ok (run p "stores" "verify"))))))

(deftest version-bumps-only-when-the-contract-changes
  ;; v1: test_add_model_refuses_an_already_registered_model (v2: same contract ⇒ unchanged, not an error),
  ;;     test_reindex_model_bumps_the_version_only_when_asked, test_promote_model_draft_installs_bumps_and_refreshes_the_class
  (let [p (project) _ (run p "models" "define" "-f" ticket)
        again (run p "models" "define" "-f" ticket)
        revisions (get-in (run p "stores" "check") [:data :revisions])
        draft (write! p "draft.edn" (pr-str (assoc ticket-d :semantics ["ticket" "support"])))
        promoted (run p "models" "define" "-f" draft)]
    (is (= {:unchanged? true :version 1} (select-keys (:data again) [:unchanged? :version])))
    (is (= 1 revisions) "an unchanged define writes nothing")
    (is (= {:version 2 :created? false :unchanged? false} (select-keys (:data promoted) [:version :created? :unchanged?])))
    (is (= ["ticket" "support"] (get-in (run p "models" "show" "TicketDoc") [:data :descriptor :semantics])))))

(deftest unknown-model-is-missing
  ;; v1: test_load_registered_model_raises_for_an_unknown_model
  (let [p (project) _ (run p "models" "define" "-f" ticket)]
    (doseq [sub ["show" "history" "template"]]
      (let [e (run p "models" sub "Nope")]
        (is (= [1 "models/missing" ["TicketDoc"]] [(:exit e) (get-in e [:error :type]) (get-in e [:error :data :known])]))))
    (is (= 1 (:exit (run p "models" "define" "-f" "absent.edn"))))
    (is (= 6 (:exit (run p "models" "define"))))
    (is (= 6 (:exit (run p "models" "show"))))
    (is (= ["check" "define" "field-add" "field-remove" "history" "list" "show" "template"]
           (get-in (run p "models" "nope") [:error :data :known])))))

(deftest show-projects-the-descriptor
  ;; v1: test_describe_model_matches_the_models_show_ast, test_describe_field_reports_kind_enum_annotation_and_description
  (let [p (project) _ (run p "models" "define" "-f" ticket)
        e (run p "models" "show" "TicketDoc") md (get-in e [:data :markdown])]
    (is (= (assoc ticket-d :model/version 1) (get-in e [:data :descriptor])))
    (is (str/includes? md "# TicketDoc (v1)"))
    (is (str/includes? md "| status | `[:enum \"open\" \"closed\"]` | yes | `\"open\"` | Lifecycle state. |"))
    (is (str/includes? md "# ⸢rev•title⸥") "the template with its markers")
    (is (= md (out/emit e "md")) "--format md prints the projection itself")))

(deftest history-carries-each-version
  ;; v1: test_draft_edits_leave_journal_entries, test_promote_entry_carries_schema_before_and_after,
  ;;     test_reindex_entry_carries_schema, test_locate_model_source_points_at_the_defining_file (v2: node id + revision)
  (let [p (project) _ (run p "models" "define" "-f" ticket)
        _ (run p "models" "field-add" "TicketDoc" "--field" "priority" "--type" "int" "--default" "5")
        h (:data (run p "models" "history" "TicketDoc"))
        j (:data (run p "journal" "show"))]
    (is (= [2 1] (mapv :version h)))
    (is (every? #(and (string? (:revision %)) (= "human/tester" (:actor %)) (string? (:timestamp %))) h))
    (is (= (mapv :revision h) (mapv :revision j)) "every model edit is a journal entry, newest first")
    (is (= 1 (get-in (first j) [:ops :replace])) "a new version is a :replace, which records supersedes")
    (let [s (store-of p)]
      (is (= 4 (count (:fields (edn/read-string (get-in s [:objects (:id (second h)) :content :blob]))))) "the old schema stays readable")
      (is (= 5 (count (:fields (:descriptor (mstore/model s "TicketDoc")))))))))

(deftest field-add-adds-the-template-marker
  ;; v1: test_add_model_field_adds_the_template_marker, test_add_model_field_required_uses_rev_marker,
  ;;     test_add_model_field_empty_string_default_is_a_real_default
  (let [p (project) _ (run p "models" "define" "-f" ticket)
        _ (run p "models" "field-add" "TicketDoc" "--field" "priority" "--type" "int" "--default" "5")
        _ (run p "models" "field-add" "TicketDoc" "--field" "note" "--type" "string" "--required")
        e (run p "models" "field-add" "TicketDoc" "--field" "extra" "--type" "string" "--default" "")
        d (get-in (run p "models" "show" "TicketDoc") [:data :descriptor])
        field (fn [n] (first (filter #(= n (:name %)) (:fields d))))]
    (is (= 4 (get-in e [:data :version])))
    (is (str/includes? (:template d) "## Priority\n\n⸢optrev•priority⸥"))
    (is (str/includes? (:template d) "⸢rev•note⸥"))
    (is (= 5 (:default (field "priority"))))
    (is (= "" (:default (field "extra"))) "an empty string default is a real default")
    (is (not (contains? (field "note") :default)))
    (let [dup (run p "models" "field-add" "TicketDoc" "--field" "note" "--type" "string")]
      (is (= [3 "models/field-exists"] [(:exit dup) (get-in dup [:error :type])])))
    (is (= 3 (:exit (run p "models" "field-add" "TicketDoc" "--field" "x" "--type" "float"))))))

(deftest section-markers-match-the-field-type
  ;; v1: test_section_markers_match_the_field_annotation
  (is (str/includes? (edit/section-for-field "title" :string false) "⸢rev•title⸥"))
  (is (str/includes? (edit/section-for-field "labels" [:list :string] false) "- ⸢rev,list•labels⸥"))
  (is (str/includes? (edit/section-for-field "meta" [:map :yaml] false) "⸢rev,dict•meta⸥"))
  (is (str/includes? (edit/section-for-field "tasks" [:list [:map :string]] false) "⸢rev,table•tasks⸥")))

(deftest field-remove-and-template-edits
  ;; v1: test_remove_model_field_edits_the_draft, test_edit_model_template_writes_the_draft_template,
  ;;     test_validate_model_draft_rejects_template_referencing_unknown_fields
;;     test_cli_v2::test_models_fields_remove_requires_existing_field_and_bumps_version
  (let [p (project) _ (run p "models" "define" "-f" ticket)
        _ (run p "models" "field-add" "TicketDoc" "--field" "priority" "--type" "int" "--default" "5")
        _ (run p "models" "field-remove" "TicketDoc" "--field" "priority")
        d (get-in (run p "models" "show" "TicketDoc") [:data :descriptor])]
    (is (not (str/includes? (pr-str d) "priority")))
    (is (= (:template ticket-d) (:template d)) "removing the field restores the template")
    (is (= 1 (:exit (run p "models" "field-remove" "TicketDoc" "--field" "ghost"))))
    (is (= 3 (some #(when (= "TicketDoc" (:name %)) (:version %)) (:data (run p "models" "list"))))
        "add and remove each defined a version; the refused removal defined none")
    (is (= (:template ticket-d) (:data (run p "models" "template" "TicketDoc"))))
    (let [bad (run p "models" "template" "TicketDoc" "-f" (write! p "t.md" "# ⸢rev•headline⸥\n"))]
      (is (= [3 "models/invalid" ["headline"]] [(:exit bad) (get-in bad [:error :type]) (get-in bad [:error :data :fields])])))
    (let [ok (run p "models" "template" "TicketDoc" "-f" (write! p "t2.md" "# Ticket ⸢rev•title⸥\n"))]
      (is (true? (:ok ok)) (pr-str ok))
      (is (= "# Ticket ⸢rev•title⸥\n" (:data (run p "models" "template" "TicketDoc")))))))

(deftest a-draft-file-leaves-the-defined-model
  ;; v1: test_add_model_field_writes_a_draft_and_leaves_the_active_model, test_validate_model_draft_reports_documents_without_promoting
  (let [p (project) _ (run p "models" "define" "-f" ticket)
        good (write! p "good.edn" (pr-str (edit/add-field ticket-d {:name "priority" :type :int :required false :default 5})))
        bad (write! p "bad.edn" (pr-str (-> ticket-d (assoc :base ["GhostDoc"]) (assoc-in [:fields 0 :type] :float))))
        before (get-in (run p "stores" "check") [:data :head])]
    (is (= {:ok true :models [{:name "TicketDoc" :ok true :errors []}]} (:data (run p "models" "check" "-f" good))))
    (is (= {:ok true :models [{:name "TicketDoc" :ok true :errors []}]} (:data (run p "models" "check"))))
    (let [e (run p "models" "check" "-f" bad)]
      (is (= [3 "models/invalid"] [(:exit e) (get-in e [:error :type])]))
      (is (str/includes? (get-in e [:error :message]) "unknown type")))
    (let [e (run p "models" "check" "-f" (write! p "b2.edn" (pr-str (assoc ticket-d :base ["GhostDoc"]))))]
      (is (str/includes? (get-in e [:error :message]) "base model `GhostDoc` is not defined")))
    (is (= before (get-in (run p "stores" "check") [:data :head])) "checking a draft never promotes it")
    (is (= 4 (:fields (first (:data (run p "models" "list"))))))))

(deftest contract-change-moves-the-model-node
  ;; v1: test_model_node_is_its_two_children, test_contract_hash_records_the_registered_class,
  ;;     test_contract_hash_covers_family_and_semantics, test_a_moved_contract_moves_the_root,
  ;;     store/test_models_io.py roundtrips (v2: one content-addressed node, no index files)
  (let [id #(mstore/node-id host/host %)]
    (is (= (id ticket-d) (id (into {} (reverse (seq ticket-d))))) "key order does not enter the identity")
    (is (= ticket-d (edn/read-string (mstore/serialize ticket-d))) "a descriptor reads back as written")
    (doseq [changed [(assoc ticket-d :semantics ["other"]) (assoc ticket-d :template "# ⸢rev•title⸥\n")
                     (update ticket-d :fields pop) (assoc-in ticket-d [:fields 0 :description] "x") (assoc ticket-d :base ["B"])]]
      (is (not= (id ticket-d) (id changed)))))
  (let [p (project) _ (run p "models" "define" "-f" ticket)
        tid (first (mstore/models-tree (store-of p)))
        root #(get-in (store-of p) [:trees tid :root :hash])
        r1 (root)
        _ (run p "models" "define" "-f" (write! p "d.edn" (pr-str (assoc ticket-d :semantics ["moved"]))))]
    (is (string? r1))
    (is (not= r1 (root)) "a moved contract moves the Merkle root of the models tree")))

(deftest nested-store-wins-over-ancestor
  ;; v1: store/test_resolver.py test_project_store_prefers_nested_store_over_ancestor
  (let [p (project) inner (str (fs/create-dirs (fs/path p "inner")))]
    (run inner "stores" "init" "--name" "inner")
    (run inner "models" "define" "-f" ticket)
    (is (= 1 (count (:data (run inner "models" "list")))))
    (is (= [] (:data (run p "models" "list"))))))

(deftest create-model-end-to-end
  ;; v1: test_create_model_end_to_end; test_standalone.py CLI: extract / render / validate, output files, model lookup
  (let [p (project) _ (run p "models" "define" "-f" ticket) _ (write! p "t.md" doc)
        x (run p "extract" "TicketDoc" "--input" "t.md")
        payload {"title" "Login roto" "status" "open" "tags" ["auth" "urgente"] "body" "No deja entrar."}]
    (is (= {:model "TicketDoc" :payload payload} (:data x)) (pr-str x))
    (is (= payload (get-in (run (tmp) "extract" "--model" ticket "--input" (str (fs/path p "t.md"))) [:data :payload])) "no store needed")
    (let [o (run p "extract" ticket "--input" "t.md" "-o" "p.json")]
      (is (= (str (fs/path p "p.json")) (get-in o [:data :output])))
      (is (= payload (json/parse-string (slurp (str (fs/path p "p.json")))))))
    (is (= (str/trim doc) (str/trim (:data (run p "render" "TicketDoc" "--data" "p.json")))))
    (let [o (run p "render" ticket "--data" "p.json" "-o" "out.md")]
      (is (= doc (slurp (get-in o [:data :output])))))
    (is (= {:ok true :model "TicketDoc" :mode :render-validity :errors []} (:data (run p "validate" "TicketDoc" "--input" "t.md"))))
    (is (true? (:ok (run p "validate" ticket "--data" "p.json" "--mode" "strict-roundtrip"))))))

(deftest direct-failures-are-typed
  ;; v1: test_standalone.py — a missing rev field, missing files, bad arguments
  (let [p (project) _ (write! p "bad.md" "# Sin cuerpo\n\nStatus: open\n\n## Tags\n\n- a\n")]
    (let [e (run p "extract" ticket "--input" "bad.md")]
      (is (= [3 "validate/missing" ["body"]] [(:exit e) (get-in e [:error :type]) (get-in e [:error :data :fields])]) (pr-str e)))
    (is (= "" (get-in (run p "extract" ticket "--input" "bad.md" "--allow-empty") [:data :payload "body"])) "v1's empty value, on request")
    (is (= 3 (:exit (run p "validate" ticket "--input" "bad.md"))))
    (is (= [1 "docs/no-file"] ((juxt :exit #(get-in % [:error :type])) (run p "extract" ticket "--input" "ghost.md"))))
    (is (= 6 (:exit (run p "extract" ticket))))
    (is (= 6 (:exit (run p "extract" "--input" "bad.md"))))
    (is (= 6 (:exit (run p "render" ticket))))
    (is (= 6 (:exit (run p "validate" ticket "--input" "bad.md" "--mode" "nope"))))
    (is (= [1 "models/missing"] ((juxt :exit #(get-in % [:error :type])) (run p "extract" "GhostDoc" "--input" "bad.md"))))
    (is (= 3 (:exit (run p "render" ticket "--data" (write! p "l.json" "[1,2]")))))))

(deftest define-from-python
  ;; v1: `models add module:Class` — 06 §5 `models define --from-python`
  (when (fs/exists? (fs/path v1-src "sldb" "examples" "reference_bundle" "guide_model.py"))
    (let [p (project)
          e (run p "models" "define" "--from-python" "sldb.examples.reference_bundle.guide_model:SLDBGuide" "--pythonpath" v1-src)
          input (str (fs/path v1-src "sldb" "examples" "reference_bundle" "guide.input.md"))]
      (is (true? (:ok e)) (pr-str e))
      (is (= "SLDBGuide" (get-in e [:data :name])))
      (is (= "SLDB Example Guide" (get-in (run p "extract" "SLDBGuide" "--input" input) [:data :payload "title"])))
      (is (true? (:ok (run p "validate" "SLDBGuide" "--input" input))))
      (let [bad (run p "models" "define" "--from-python" "no.such:Thing")]
        (is (= [3 "models/python-export"] [(:exit bad) (get-in bad [:error :type])]))))))
