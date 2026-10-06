(ns kimun.cli.diagnostics-test
  "`stores check`, `stores health`, `stores update` and `stores reconcile` end
   to end on temporary stores. Each test names in a comment the v1 function
   whose promise it carries."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [cheshire.core :as json]
            [kimun.cli.main :as main]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [sldb.kernel.store :as store]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- file [p & parts] (str (apply fs/path p parts)))

(def ticket
  (pr-str {:model/name "TicketDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "status" :type :string :required true :description "Status."}]
           :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"}))

(defn- project
  "A project with TicketDoc and the tracked document `login` (login.md)."
  []
  (let [p (str (fs/create-temp-dir {:prefix "kimun-diag-cli-"}))]
    (run p "stores" "init" "--name" "p")
    (spit (file p "ticket.edn") ticket)
    (is (true? (:ok (run p "models" "define" "-f" "ticket.edn"))))
    (is (true? (:ok (run p "docs" "create" "TicketDoc" "login" "--set" "title=T" "--set" "status=open"))))
    p))

(deftest check-lists-every-model-and-document-when-clean
  ;; v1: test_check_lists_every_model_and_document_when_clean (the report-shape promise)
  (let [p (project)
        e (run p "stores" "check")
        r (get-in e [:data :documents])]
    (is (true? (:ok e)))
    (is (= [] (:warnings e)))
    (is (= [true 1 1 []] [(:valid r) (:checked r) (:tracked r) (:findings r)]))
    (is (str/starts-with? (:summary r) "PASS: store integrity"))
    (is (= [["TicketDoc" true :ok]] (mapv (juxt :model :contract-ok :category) (:models r))))
    (is (= [["login" "login.md" :ok true true]] (mapv (juxt :document :path :category :content-ok :fields-ok) (:documents r))))))

(deftest check-names-the-damaged-document-and-its-fix
  ;; v1: test_report_names_the_damaged_document_and_its_fix (v1 exits 1; here the envelope stays ok with `:valid false`, as test_store_check_data_mutation_fails was already ported)
  (let [p (project)]
    (spit (file p "login.md") "# T\n\nStatus: closed\n")
    (let [e (run p "stores" "check")
          w (first (:warnings e))]
      (is (false? (get-in e [:data :documents :valid])))
      (is (str/includes? (get-in e [:data :documents :summary]) "FAIL: store integrity"))
      (is (str/includes? (get-in e [:data :documents :summary]) "1 edited"))
      (is (= ["docs/file-modified" :edited "TicketDoc" "login" "login.md" "kimun docs update login"]
             ((juxt :type :category :model :document :path :action) w)) "the document is named, not just counted")
      (is (str/includes? (:message w) "login.md"))
      (is (str/includes? (:message w) "kimun docs update login") "the fix is stated in the message")
      (is (str/includes? (:message w) "decide first")))))

(deftest check-distinguishes-reformatting-from-damage
  ;; v1: test_report_distinguishes_reformatting_from_damage
  (let [p (project)]
    (spit (file p "login.md") "# T\n\nStatus: open\n\n\n")
    (let [e (run p "stores" "check")
          s (get-in e [:data :documents :summary])]
      (is (true? (:ok e)))
      (is (true? (get-in e [:data :documents :valid])) "an expected mutation passes")
      (is (str/includes? s "PASS: store integrity"))
      (is (str/includes? s "reformatted (ok)"))
      (is (not (str/includes? s "edited"))))))

(deftest check-reports-divergence-and-a-missing-file-together
  ;; v1: test_check_reports_file_divergence_and_missing_file
  (let [p (project)]
    (run p "docs" "create" "TicketDoc" "gone" "--set" "title=G" "--set" "status=open")
    (spit (file p "login.md") "# T\n\nStatus: closed\n")
    (fs/delete (file p "gone.md"))
    (let [r (get-in (run p "stores" "check") [:data :documents])
          by (into {} (map (juxt :document identity)) (:documents r))]
      (is (false? (:valid r)))
      (is (= [false :edited] [(get-in by ["login" :content-ok]) (get-in by ["login" :category])]))
      (is (= [false :missing] [(get-in by ["gone" :path-exists]) (get-in by ["gone" :category])]))
      (is (seq (get-in by ["gone" :message])))
      (is (= {:edited 1 :missing 1} (:counts r))))))

(deftest check-in-json-and-yaml
  ;; v1: test_json_carries_both_letter_and_semantic_keys (semantic keys), test_store_check_json_format,
  ;;     test_store_check_yaml_format
  (let [p (project)]
    (spit (file p "login.md") "# T\n\nStatus: closed\n")
    (let [e (run p "stores" "check")
          j (json/parse-string (out/emit e "json") true)
          d (first (get-in j [:data :documents :documents]))
          y (out/emit e "yaml")]
      (is (= ["edited" false false] [(:category d) (:content-ok d) (:fields-ok d)]))
      (is (str/includes? (:message d) "docs update"))
      (is (= 1 (get-in j [:data :documents :counts :edited])))
      (is (str/includes? y "valid: false"))
      (is (str/includes? y "models:"))
      (is (str/includes? y "category: edited")))))

(deftest check-reports-a-tampered-object
  ;; v1: test_tampered_hash_b — the stored signature no longer matches; the fix is named
  (let [p (project)
        dir (file p ".kimun")
        victim (first (sort (store/reachable (:store (cli-store/open! dir)))))]
    (spit (file dir "objects" victim) "tampered")
    (let [e (run p "stores" "check")
          w (first (:warnings e))]
      (is (false? (get-in e [:data :documents :valid])))
      (is (= ["store/corrupt" "kimun stores verify"] [(:type w) (:action w)]))
      (is (= :needs-human (get-in (run p "stores" "health") [:data :status]))))))

(deftest health-names-the-document-the-category-and-the-command
  ;; v1: test_health_names_what_to_look_at_and_what_to_run, test_health_demands_a_human_when_a_field_was_edited (CLI)
  (let [p (project)]
    (is (= :ok (get-in (run p "stores" "health") [:data :status])))
    (spit (file p "login.md") "# T\n\nStatus: closed\n")
    (let [h (run p "stores" "health")]
      (is (true? (:ok h)))
      (is (= :needs-human (get-in h [:data :status])))
      (is (= [{:category :edited :model "TicketDoc" :document "login" :path "login.md"
               :action "kimun docs update login" :needs-human true}]
             (get-in h [:data :findings])))
      (is (= "inspect: login.md" (get-in h [:data :next]))))
    (is (true? (:ok (run p "docs" "update" "login"))) "the named command repairs it")
    (is (= {:status :ok :findings [] :actions [] :next nil}
           (dissoc (:data (run p "stores" "health")) :summary)))))

(deftest update-repairs-what-needs-no-decision
  ;; v1: test_store_is_valid_again_after_update
  (let [p (project)]
    (run p "docs" "create" "TicketDoc" "other" "--set" "title=O" "--set" "status=open")
    (is (true? (:ok (run p "models" "field-add" "TicketDoc" "--field" "priority" "--type" "int" "--default" "5"))))
    (is (= :degraded (get-in (run p "stores" "health") [:data :status])))
    (testing "an edited file is left for a human, the rest is repaired"
      (spit (file p "other.md") "# O\n\nStatus: closed\n")
      (let [u (run p "stores" "update")]
        (is (true? (:ok u)) (pr-str u))
        (is (= ["login"] (get-in u [:data :updated])))
        (is (= [{:document "other" :category :edited :action "kimun docs update other"}] (get-in u [:data :skipped])))
        (is (= :needs-human (get-in u [:data :status])))))
    (is (true? (:ok (run p "docs" "update" "other"))))
    (let [chk (run p "stores" "check")]
      (is (true? (get-in chk [:data :documents :valid])) (pr-str (:warnings chk)))
      (is (= [] (:warnings chk))))
    (is (= {:updated [] :skipped [] :status :ok} (:data (run p "stores" "update"))) "nothing to do is not an error")))

(deftest reconcile-reports-and-applies-only-when-requested
  ;; v1: test_reconcile_reports_and_applies_only_when_requested
  (let [base (str (fs/create-temp-dir {:prefix "kimun-reconcile-"}))
        catalog (file base "catalog") one (file base "projects" "one") gone (file base "gone")]
    (doseq [d [catalog one gone]] (fs/create-dirs d) (run d "stores" "init"))
    (run catalog "stores" "link" "gone" gone)
    (fs/delete-tree gone)
    (let [one-store (str (fs/canonicalize (file one ".kimun")))
          r (:data (run catalog "stores" "reconcile" (file base "projects")))]
      (is (= [one-store] (:discovered r)))
      (is (= [one-store] (:missing r)))
      (is (= ["gone"] (mapv :name (:stale r))))
      (is (false? (:applied r)))
      (is (= ["gone"] (mapv :name (get-in (run catalog "stores" "links") [:data :links]))) "nothing written without --apply")
      (is (true? (:applied (:data (run catalog "stores" "reconcile" (file base "projects") "--apply")))))
      (let [links (get-in (run catalog "stores" "links") [:data :links])]
        (is (= [["one" :ok]] (mapv (juxt :name :status) links)))
        (is (= one-store (str (fs/canonicalize (:resolved (first links))))))))))

(deftest check-reports-noise-already-tracked-without-blocking
  ;; v1: test_stores_check_reports_noise_already_tracked_without_blocking,
  ;;     test_stores_check_prints_every_noisy_heading_not_just_a_count
  (let [p (str (fs/create-temp-dir {:prefix "kimun-diag-noise-"}))]
    (run p "stores" "init" "--name" "p")
    (spit (file p "note.edn")
          (pr-str {:model/name "NoteDoc" :model/version 1 :base []
                   :fields [{:name "title" :type :string :required true :description "Note title."}
                            {:name "body" :type :string :required true :description "Body."}]
                   :template "# ⸢rev•title⸥\n\n## Answer\n\n⸢rev•body⸥"}))
    (is (true? (:ok (run p "models" "define" "-f" "note.edn"))))
    (spit (file p "noisy.md") "# Alpha\n\n## Answer\n\nBody text\n\n## Entidades\n\n- one\n\n## Medicion 1\n\n- two\n")
    (is (= "docs/structural-noise" (get-in (run p "docs" "track" "NoteDoc" "noisy.md") [:error :type])))
    (is (true? (:ok (run p "docs" "track" "NoteDoc" "noisy.md" "--force"))))
    (let [e (run p "stores" "check")
          f (first (get-in e [:data :documents :findings]))
          w (first (:warnings e))]
      (is (true? (:ok e)) "the report does not block")
      (is (= [:noise "noisy" "NoteDoc" ["## Entidades" "## Medicion 1"]] ((juxt :category :document :model :undeclared) f)))
      (is (true? (:needs-human f)))
      (is (= "docs/structural-noise" (:type w)))
      (is (= ["## Entidades" "## Medicion 1"] (:undeclared w)))
      (is (every? #(str/includes? (:message w) %) ["structural noise" "noisy" "NoteDoc" "## Entidades" "## Medicion 1"])
          "every noisy heading is named, not just counted")
      (is (str/includes? (get-in e [:data :documents :summary]) "1 noisy")))))
