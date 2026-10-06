(ns kimun.parity.cli-v2-test
  "Ports of the `models` CLI promises of sldb v1 `tests/test_cli_v2.py`, driven in
   process through `kimun.cli.main/run` on temporary stores: listing, the no-store
   failure (and no global fallback), a template draft that touches nothing until it
   is applied, applying it, a refused apply leaving the store as it was, and pure
   JSON output."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [cheshire.core :as json]
            [kimun.cli.main :as main]
            [kimun.cli.out :as out]))

(def env {"USER" "tester"})
(def ticket (str (fs/absolutize "test/fixtures/models/ticket.edn")))

(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- tmp [] (str (fs/create-temp-dir {:prefix "kimun-parity-cliv2-"})))
(defn- write! [dir name text] (let [p (str (fs/path dir name))] (spit p text) p))
(defn- head [p] (get-in (run p "stores" "check") [:data :head]))
(defn- template [p] (:data (run p "models" "template" "TicketDoc")))
(defn- project []
  (let [p (tmp)]
    (run p "stores" "init" "--name" "p")
    (run p "models" "define" "-f" ticket)
    p))

(def new-template
  "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n\n## Tags\n\n- ⸢rev,list•tags⸥\n\n## Body\n\n⸢rev•body⸥\n\n## Review\n\nReady for validation.\n")

;; v1: test_cli_v2.py::test_models_list_lists_registered_models
(deftest models-list-lists-registered-models
  (let [p (project)
        _ (spit (str (fs/path p "t.md")) "# Login\n\nStatus: open\n\n## Tags\n\n- a\n\n## Body\n\nx\n")
        _ (run p "docs" "track" "TicketDoc" "t.md" "--name" "t")
        e (run p "models" "list")
        m (first (:data e))]
    (is (true? (:ok e)) (pr-str e))
    (is (= 1 (count (:data e))))
    (is (= ["TicketDoc" 1 4 1] ((juxt :name :version :fields :documents) m)))
    (is (= 0 (:not-intact m)))
    (testing "the text rendering names the model and its document count"
      (let [txt (out/emit (run p "models" "list") "text")]
        (is (str/includes? txt "TicketDoc"))))))

;; v1: test_cli_v2.py::test_models_list_fails_on_no_store_and_no_global
(deftest models-list-without-a-store-fails-even-with-a-global-one
  (let [home (tmp) empty-dir (tmp)
        _ (run home "stores" "init" "--name" "global")
        _ (run home "models" "define" "-f" ticket)
        e (:envelope (main/run ["models" "list"] (assoc env "HOME" home "KIMUN_HOME" home) empty-dir))]
    (is (false? (:ok e)))
    (is (= ["cli/no-store" 5] [(get-in e [:error :type]) (:exit e)]) "no walk-up store: failure, never a global catalog")))

;; v1: test_cli_v2.py::test_models_template_edit_writes_draft_without_mutating_active_source
(deftest a-draft-checks-without-touching-the-defined-model
  (let [p (project)
        before-head (head p) before-tpl (template p)
        draft (write! p "draft.edn" (pr-str (assoc (edn/read-string (slurp ticket)) :template new-template)))
        e (run p "models" "check" "-f" draft)]
    (is (true? (:ok e)) (pr-str e))
    (is (= before-head (head p)) "checking a draft writes no revision")
    (is (= before-tpl (template p)) "the defined template is untouched")
    (is (not (str/includes? (template p) "## Review")))))

;; v1: test_cli_v2.py::test_models_validate_promotes_valid_template_draft_and_reindexes
(deftest applying-a-valid-template-bumps-the-version-and-the-store-stays-sound
  (let [p (project)
        e (run p "models" "template" "TicketDoc" "-f" (write! p "t.md" new-template))]
    (is (true? (:ok e)) (pr-str e))
    (is (str/includes? (template p) "## Review"))
    (is (= 2 (:version (first (:data (run p "models" "list"))))))
    (is (= {:ok true :models [{:name "TicketDoc" :ok true :errors []}]} (:data (run p "models" "check"))))
    (is (true? (:ok (run p "stores" "check"))))
    (is (true? (:ok (run p "stores" "verify"))))
    (is (true? (:ok (run p "rebuild-indexes"))) "the derived index rebuilds against the new contract")))

;; v1: test_cli_v2.py::test_models_promote_failure_restores_model_draft_and_indexes
;; (v1 injected a failure at seven stages of promote; v2 writes the model in one atomic
;; transaction, so the observable promise is: a refused apply leaves model, head and store as they were)
(deftest a-refused-apply-leaves-the-model-the-head-and-the-store-as-they-were
  (let [p (project)
        before-head (head p) before-tpl (template p)
        bad (write! p "bad.md" "# ⸢rev•title⸥\n\n## Review\n\n⸢rev•ghost_field⸥\n")
        e (run p "models" "template" "TicketDoc" "-f" bad)]
    (is (false? (:ok e)))
    (is (= "models/invalid" (get-in e [:error :type])))
    (is (= before-head (head p)) "nothing was committed")
    (is (= before-tpl (template p)))
    (is (= 1 (:version (first (:data (run p "models" "list"))))))
    (is (true? (:ok (run p "stores" "check"))))
    (is (= "# ⸢rev•title⸥\n\n## Review\n\n⸢rev•ghost_field⸥\n" (slurp bad)) "the input file is untouched")))

;; v1: test_cli_v2.py::test_models_promote_json_output_is_pure_json
(deftest json-output-of-an-apply-is-pure-json
  (let [p (project)
        r (main/run ["models" "template" "TicketDoc" "-f" (write! p "t.md" new-template) "--format" "json"] env p)
        s (out/emit (:envelope r) (:fmt r))
        parsed (json/parse-string s true)]
    (is (map? parsed) "stdout parses as one JSON document, no progress line before it")
    (is (true? (:ok parsed)))
    (is (= 2 (get-in parsed [:data :version])))))
