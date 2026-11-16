(ns kimun.cli.adapters-test
  "The `adapters` group end to end on temporary stores: scan, sync, check and
   list over a fake adapter (`test/fixtures/adapters/fake/fake_adapter.py`,
   which prints the facts file named after `--`), and over the real
   `python-symbols` adapter when it is present. The group is called through
   `dispatch` (it is not registered in `kimun.cli.main` yet); stores are set
   up and read with the existing groups. Each test carrying a v1 promise
   names the v1 function in a comment."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.commands.adapters :as cmd]
            [kimun.cli.errors :as errors]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.host.hash :as hash]
            [kimun.surface.adapters :as planner]))

(def env
  "Test environment; forwards the real `KIMUN_ADAPTER_PYTHON` to dispatch when set."
  (cond-> {"USER" "tester"} (System/getenv "KIMUN_ADAPTER_PYTHON") (assoc "KIMUN_ADAPTER_PYTHON" (System/getenv "KIMUN_ADAPTER_PYTHON"))))
(def fixtures (str (fs/absolutize "test/fixtures/adapters/fake")))
(def fake (str (fs/path fixtures "fake_adapter.py")))
(def real-adapter (str (fs/absolutize "python/kimun/adapters/python_symbols.py")))
(def pytree (str (fs/absolutize "test/fixtures/adapters/pytree")))

(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- file [p & parts] (str (apply fs/path p parts)))

(defn- adapters
  "The envelope of `adapters SUB ARGS…`; a trailing map is merged into the options."
  [cwd sub & args]
  (let [opts (when (map? (last args)) (last args))
        args (if opts (butlast args) args)]
    (errors/wrap #(cmd/dispatch sub (vec args) (merge {:format "json" :actor "human/tester"} opts) {:env env :cwd cwd}))))

(defn- project
  "A store in a temp dir with a private copy of the facts fixture `facts.json`."
  ([] (project "symbols.json"))
  ([fixture]
   (let [p (str (ktu/temp-dir "kimun-adapters-"))]
     (is (true? (:ok (run p "stores" "init" "--name" "p"))))
     (fs/copy (file fixtures fixture) (file p "facts.json"))
     p)))

(defn- fake-run
  "`adapters SUB <fake> . --system demo [opts] -- --facts facts.json`."
  [p sub & [opts]]
  (adapters p sub fake "." "--facts" (file p "facts.json") (merge {:system "demo"} opts)))

(defn- journal [p] (:data (run p "journal" "show")))
(defn- error-type [e] (get-in e [:error :type]))

(defn- edit-facts!
  "Rewrites the project's facts file with `(f facts-vector)`."
  [p f]
  (let [d (json/parse-string (slurp (file p "facts.json")))]
    (spit (file p "facts.json") (json/generate-string (update d "facts" (comp vec f))))))

(defn- set-doc! [p id doc]
  (edit-facts! p (fn [facts] (map #(if (= id (get % "id")) (assoc % "doc" doc) %) facts))))

(defn- doc-named [names fragment] (first (filter #(str/ends-with? % fragment) names)))

(deftest scan-plans-and-writes-nothing
  (let [p (project) before (count (journal p))
        e (fake-run p "scan")]
    (is (true? (:ok e)) (pr-str (:error e)))
    (is (= {:adapter "python-symbols" :profile "symbols" :system "demo" :output "knowledge"}
           (select-keys (:data e) [:adapter :profile :system :output])))
    (is (seq (:documents (:data e))))
    (is (every? #(and (string? (:name %)) (= "PythonSymbolDoc" (:model %)) (map? (:fields %))
                      (str/starts-with? (:path %) "knowledge/symbols/") (str/ends-with? (:path %) (str (:name %) ".md")))
                (:documents (:data e))))
    (is (= before (count (journal p))) "scan commits nothing")
    (is (not (fs/exists? (file p "knowledge"))))
    (is (empty? (:data (run p "docs" "list"))))
    (testing "scan needs no store"
      (let [bare (str (ktu/temp-dir "kimun-adapters-bare-"))]
        (is (true? (:ok (adapters bare "scan" fake "." "--facts" (file p "facts.json") {:system "demo"}))))))
    (testing "--system is required"
      (is (= "cli/usage" (error-type (adapters p "scan" fake "." "--facts" (file p "facts.json"))))))))

;; v1: tests/selfdoc/test_sync.py::test_sync_from_subdirectory_tracks_documents_and_then_is_noop
(deftest sync-from-subdirectory-creates-then-is-noop
  (let [p (project) nested (file p "deep" "er")
        _ (fs/create-dirs nested)
        e (adapters nested "sync" fake "." "--facts" (file p "facts.json") {:system "demo"})
        names (:created (:data e))
        greet (doc-named names "greet")]
    (is (true? (:ok e)) (pr-str (:error e)))
    (is (pos? (count names)))
    (is (= (count names) (:created (:counts (:data e)))))
    (is (= ["PythonSymbolDoc"] (:defined (:models (:data e)))))
    (is (some? greet))
    (is (fs/regular-file? (file p "knowledge" "symbols" (str greet ".md"))) "files live under the project root, whatever the cwd")
    (is (= (set names) (set (map :name (:data (run p "docs" "list"))))))
    (is (true? (:ok (fake-run p "check"))))
    (is (true? (:ok (run p "fields" "get" greet "id"))) "the document is a normal typed document")
    (let [n (count (journal p)) f (file p "knowledge" "symbols" (str greet ".md"))
          bytes (slurp f) mtime (fs/last-modified-time f)
          again (fake-run p "sync")]
      (is (true? (:ok again)))
      (is (= {:created 0 :updated 0 :untracked 0 :restored 0 :adopted 0 :unchanged (count names)} (:counts (:data again))))
      (is (= {:defined [] :present ["PythonSymbolDoc"]} (:models (:data again))) "models are defined once")
      (is (= n (count (journal p))) "a second sync commits nothing")
      (is (= bytes (slurp f)))
      (is (= mtime (fs/last-modified-time f)) "unchanged documents are not rewritten")
      (is (true? (:ok (run p "stores" "check")))))))

;; v1: tests/selfdoc/test_sync.py::test_parser_drift_preserves_authored_explanations_and_examples
(deftest drift-is-reported-then-synced-keeping-authored-fields
  (let [p (project)
        names (:created (:data (fake-run p "sync")))
        greet (doc-named names "greet")
        f (file p "knowledge" "symbols" (str greet ".md"))]
    (is (true? (:ok (run p "docs" "update" greet "--set" "purpose=Authored purpose." "--set" "architecture=Authored architecture.")))
        "authored fields are edited through the normal docs path")
    (is (true? (:ok (fake-run p "check"))) "authored fields are not drift")
    (set-doc! p "demo.tools.greet" "Return a much better greeting.")
    (let [n (count (journal p)) before (slurp f)
          c (fake-run p "check")]
      (is (false? (:ok c)))
      (is (= (subs (str cmd/drift-type) 1) (error-type c)))
      (is (= 1 (:exit c)) "v1 `selfdoc check` exits 1 on drift")
      (is (= 1 (errors/exit-for cmd/drift-type)))
      (is (= "drift" (get-in c [:error :data :reason])))
      (is (= [[greet "changed"]] (mapv (juxt :name :status) (get-in c [:error :data :documents]))))
      (is (= before (slurp f)) "check writes nothing")
      (is (= n (count (journal p))))
      (let [s (fake-run p "sync") entries (journal p)
            payload (get-in (run p "docs" "show" greet) [:data :payload])]
        (is (true? (:ok s)) (pr-str (:error s)))
        (is (= [greet] (mapv :name (:updated (:data s)))) "exactly the edited document is updated")
        (is (seq (:changed (first (:updated (:data s))))))
        (is (= {:created 0 :updated 1 :untracked 0 :restored 0 :adopted 0 :unchanged (dec (count names))} (:counts (:data s))))
        (is (= (inc n) (count entries)) "one commit")
        (is (string? (:operation (first entries))))
        (is (some? (:address (first entries))))
        (is (str/includes? (slurp f) "much better greeting"))
        (is (= "Authored purpose." (get payload "purpose" (get payload :purpose))))
        (is (= "Authored architecture." (get payload "architecture" (get payload :architecture))))
        (is (true? (:ok (fake-run p "check"))))))))

;; v1: tests/selfdoc/test_sync.py::test_removed_commands_are_reported_and_retained
;; v1: tests/selfdoc/test_sync.py::test_removed_and_deleted_command_remains_visible_through_its_registration
(deftest removed-symbol-is-orphan-then-untracked
  (let [p (project)
        names (:created (:data (fake-run p "sync")))
        put (doc-named names "put")
        f (file p "knowledge" "symbols" (str put ".md"))]
    (edit-facts! p (fn [facts] (remove #(= "demo.tools.Box.put" (get % "id")) facts)))
    (let [c (fake-run p "check")]
      (is (= 1 (:exit c)))
      (is (= [[put "orphan"]] (mapv (juxt :name :status) (get-in c [:error :data :documents])))))
    (testing "a removed symbol whose file was deleted too stays visible through its registration"
      (let [kept (slurp f)]
        (fs/delete f)
        (is (= [[put "orphan"]] (mapv (juxt :name :status) (get-in (fake-run p "check") [:error :data :documents]))))
        (spit f kept)))
    (let [s (fake-run p "sync")]
      (is (true? (:ok s)) (pr-str (:error s)))
      (is (= [put] (:untracked (:data s))))
      (is (= {:created 0 :updated 0 :untracked 1 :restored 0 :adopted 0 :unchanged (dec (count names))} (:counts (:data s))))
      (is (fs/regular-file? f) "the file is retained, as in v1")
      (is (not (contains? (set (map :name (:data (run p "docs" "list")))) put)))
      (is (= "docs untrack" (:operation (first (journal p)))))
      (is (true? (:ok (fake-run p "check")))))))

;; v1: tests/selfdoc/test_sync.py::test_missing_generated_file_is_recreated_and_tracking_repaired
(deftest missing-generated-file-is-recreated
  (let [p (project)
        names (:created (:data (fake-run p "sync")))
        greet (doc-named names "greet")
        f (file p "knowledge" "symbols" (str greet ".md"))
        text (slurp f) n (count (journal p))]
    (fs/delete f)
    (let [c (fake-run p "check")]
      (is (= 1 (:exit c)))
      (is (= [[greet "missing-file"]] (mapv (juxt :name :status) (get-in c [:error :data :documents])))))
    (let [s (fake-run p "sync")]
      (is (true? (:ok s)) (pr-str (:error s)))
      (is (= [greet] (:restored (:data s))))
      (is (= text (slurp f)))
      (is (= n (count (journal p))) "restoring a file commits nothing")
      (is (true? (:ok (fake-run p "check")))))))

;; v1: tests/selfdoc/test_preflight.py::test_invalid_existing_document_prevents_any_sync
(deftest foreign-file-at-a-planned-path-prevents-any-sync
  (let [p (project)
        planned (:documents (:data (fake-run p "scan")))
        target (file p (:path (first planned)))
        n (count (journal p))]
    (fs/create-dirs (fs/parent target))
    (spit target "# not a generated document\n")
    (let [s (fake-run p "sync")]
      (is (= "adapters/conflict" (error-type s)))
      (is (= 4 (:exit s)))
      (is (= n (count (journal p))) "nothing committed, not even the models")
      (is (= "# not a generated document\n" (slurp target)))
      (is (= 1 (count (fs/glob (file p "knowledge") "**.md")))))))

;; v1: tests/selfdoc/test_preflight.py::test_output_outside_store_is_rejected
(deftest output-outside-the-project-is-rejected
  (let [p (project) n (count (journal p))]
    (doseq [out ["../outside" "/tmp/kimun-adapters-outside"]]
      (let [s (fake-run p "sync" {:output out})]
        (is (false? (:ok s)) out)
        (is (= "adapters/invalid-output" (error-type s)))))
    (is (= n (count (journal p))))
    (testing "--output moves the layout"
      (let [s (fake-run p "sync" {:output "docs/ref"})]
        (is (true? (:ok s)) (pr-str (:error s)))
        (is (seq (fs/glob (file p "docs" "ref" "symbols") "*.md")))))))

;; v1: tests/selfdoc/test_model_refs.py::test_old_registry_path_follows_public_reexports_to_editable_definition
;; (the `models template show CliCommandDoc` half; kimun has no Python registry refs to resolve)
(deftest cli-profile-syncs-commands-and-surface
  (let [p (project "cli.json")
        s (fake-run p "sync" {:factory "app:build_parser"})
        docs (:data (run p "docs" "list"))]
    (is (true? (:ok s)) (pr-str (:error s)))
    (is (= "cli" (:profile (:data s))))
    (is (= ["CliCommandDoc" "SurfaceDoc"] (:defined (:models (:data s)))))
    (is (= #{"CliCommandDoc" "SurfaceDoc"} (set (map :model docs))))
    (is (seq (fs/glob (file p "knowledge" "commands") "*.md")))
    (is (seq (fs/glob (file p "knowledge" "surfaces") "*.md")))
    (is (str/includes? (pr-str (:data (run p "models" "template" "CliCommandDoc"))) "command_path"))
    (let [n (count (journal p)) again (fake-run p "sync" {:factory "app:build_parser"})]
      (is (zero? (+ (:created (:counts (:data again))) (:updated (:counts (:data again))) (:untracked (:counts (:data again))))))
      (is (= n (count (journal p))))
      (is (true? (:ok (fake-run p "check" {:factory "app:build_parser"})))))
    (testing "a symbols sync of the same system leaves the cli documents alone (owned?)"
      (fs/copy (file fixtures "symbols.json") (file p "facts.json") {:replace-existing true})
      (let [s2 (fake-run p "sync")]
        (is (true? (:ok s2)) (pr-str (:error s2)))
        (is (empty? (:untracked (:data s2))))))))

(deftest adapter-failures-are-typed
  (let [p (project) n (count (journal p))
        mode (fn [m] (adapters p "sync" fake "." "--facts" (file p "facts.json") "--mode" m {:system "demo"}))]
    (testing "unknown adapter"
      (let [e (adapters p "sync" "no-such-adapter" "." {:system "demo"})]
        (is (= "adapters/missing" (error-type e)))
        (is (= 1 (:exit e)))
        (is (= "unknown-adapter" (get-in e [:error :data :reason])))))
    (testing "adapter exit 1"
      (let [e (mode "fail")]
        (is (= "adapters/failed" (error-type e)))
        (is (= 1 (get-in e [:error :data :exit])))
        (is (str/includes? (get-in e [:error :data :stderr]) "warning: about to fail"))
        (is (str/ends-with? (get-in e [:error :message]) "error: fake scan failed"))))
    (testing "adapter exit 3: runtime missing"
      (let [e (mode "runtime")]
        (is (= "adapters/runtime" (error-type e)))
        (is (str/includes? (get-in e [:error :message]) "runtime missing: fake_runtime"))))
    (testing "garbage on stdout"
      (is (= "adapters/invalid-output" (error-type (mode "garbage")))))
    (testing "contract violation"
      (edit-facts! p (fn [facts] (map #(dissoc % "source_hash") facts)))
      (let [e (fake-run p "sync")]
        (is (= "adapters/invalid-output" (error-type e)))
        (is (seq (get-in e [:error :data :problems])))))
    (testing "usage"
      (is (= "cli/usage" (error-type (adapters p "sync"))))
      (is (= "cli/usage" (error-type (adapters p "nope")))))
    (is (= n (count (journal p))) "a failed sync writes nothing")))

(deftest adapter-given-by-relative-path
  (let [p (project)]
    (fs/create-dirs (file p "tools"))
    (fs/copy fake (file p "tools" "myadapter") {:copy-attributes true})
    (is (true? (:ok (adapters p "scan" "tools/myadapter" "." "--facts" "facts.json" {:system "demo"}))))
    (fs/copy fake (file p "myadapter") {:copy-attributes true})
    (is (true? (:ok (adapters p "scan" "myadapter" "." "--facts" "facts.json" {:system "demo"})))
        "a bare name that is a file relative to the cwd")))

(deftest list-reports-builtins-and-runtime
  (let [p (project)
        fake-python (file p "py") dir (file fake-python "kimun" "adapters")]
    (fs/create-dirs dir)
    (fs/copy fake (file dir "fake_tool.py"))
    (spit (file dir "__init__.py") "")
    (spit (file dir "_private.py") "")
    (spit (file dir "broken.py") "import sys\nsys.stderr.write('error: boom\\n')\nsys.exit(1)\n")
    (let [e (errors/wrap #(cmd/dispatch "list" [] {:format "json"} {:env (assoc env "KIMUN_PYTHON" fake-python) :cwd p}))
          by-name (into {} (map (juxt :name identity)) (:data e))]
      (is (true? (:ok e)))
      (is (= ["broken" "fake-tool"] (mapv :name (:data e))) "underscores become dashes; private files are skipped")
      (is (true? (:available? (by-name "fake-tool"))))
      (is (= "symbols" (:profile (by-name "fake-tool"))))
      (is (false? (:available? (by-name "broken"))))
      (is (= "error: boom" (:error (by-name "broken")))))
    (testing "a built-in is run by name"
      (let [e (errors/wrap #(cmd/dispatch "scan" ["fake-tool" "." "--facts" "facts.json"] {:format "json" :system "demo"}
                                          {:env (assoc env "KIMUN_PYTHON" fake-python) :cwd p}))]
        (is (true? (:ok e)) (pr-str (:error e)))))
    (when (fs/exists? real-adapter)
      (is (contains? (set (map :name (:data (adapters p "list")))) "python-symbols")))))

(def tools-py
  "A module of the small tree the real adapter scans (no colliding document ids)."
  (str "\"\"\"Tools of the demo.\"\"\"\nimport os\n\n\ndef greet(name, loud=False):\n    \"\"\"Return a greeting for name.\"\"\"\n    return name\n\n\n"
       "class Box:\n    \"\"\"A box of things.\"\"\"\n\n    def put(self, item):\n        \"\"\"Put an item in the box.\"\"\"\n        return item\n"))

(defn- real-project []
  (let [p (str (ktu/temp-dir "kimun-adapters-real-"))]
    (is (true? (:ok (run p "stores" "init" "--name" "p"))))
    (fs/create-dirs (file p "src" "demo"))
    (spit (file p "src" "demo" "__init__.py") "\"\"\"Demo package.\"\"\"\n")
    (spit (file p "src" "demo" "tools.py") tools-py)
    p))

(deftest real-python-symbols-adapter
  (if-not (fs/exists? real-adapter)
    (println "skipped real-python-symbols-adapter: python/kimun/adapters/python_symbols.py is absent")
    (let [p (real-project)
          s (adapters p "sync" "python-symbols" "src" {:system "demo"})
          names (:created (:data s))]
      (is (true? (:ok s)) (pr-str (:error s)))
      (is (pos? (count names)))
      (is (every? #(str/starts-with? % "python-demo-") names))
      (is (not (fs/exists? (file p "src" "demo" "__pycache__"))) "the adapter writes nothing into the tree")
      (let [n (count (journal p))]
        (is (zero? (:created (:counts (:data (adapters p "sync" "python-symbols" "src" {:system "demo"}))))))
        (is (= n (count (journal p))))
        (let [core (file p "src" "demo" "tools.py")]
          (spit core (str/replace-first (slurp core) "A box of things." "A box of things, edited."))
          (let [c (adapters p "check" "python-symbols" "src" {:system "demo"})]
            (is (= 1 (:exit c)))
            (is (seq (get-in c [:error :data :documents]))))
          (let [u (adapters p "sync" "python-symbols" "src" {:system "demo"})]
            (is (true? (:ok u)) (pr-str (:error u)))
            ;; as in v1, `source_sha256` (the file's hash) is parser-owned: every symbol of the edited file is updated
            (is (= ["python-demo-demo-tools-box" "python-demo-demo-tools-box-put" "python-demo-demo-tools-greet"] (mapv :name (:updated (:data u)))))
            (let [changed (into {} (map (juxt :name (comp set :changed))) (:updated (:data u)))
                  box (changed "python-demo-demo-tools-box") sibling (changed "python-demo-demo-tools-greet")]
              (is (every? box sibling) "a sibling changes only by the file hash and what derives from it")
              (is (< (count sibling) (count box)) "the edited symbol also changes its documentation fields"))
            (is (= (+ n 3) (count (journal p))) "one commit per updated document")
            (is (zero? (+ (:created (:counts (:data u))) (:untracked (:counts (:data u))))))
            (is (string? (:operation (first (journal p)))))
            (is (some? (:address (first (journal p)))))
            (is (true? (:ok (adapters p "check" "python-symbols" "src" {:system "demo"})))))))
      (testing "v1 alias python-check: --source-root, --system"
        (is (true? (:ok (adapters p "python-check" {:system "demo" :source-root "src"}))))
        (is (true? (:ok (adapters p "python-scan" {:system "demo"})))))
      (testing "a tree whose symbols collide as document ids is refused before any write (v1 `collide`)"
        (when (fs/directory? pytree)
          (let [n (count (journal p)) e (adapters p "sync" "python-symbols" pytree {:system "other"})]
            (when-not (:ok e)
              (is (= "adapters/invalid-output" (error-type e)))
              (is (= n (count (journal p))))))))
      (testing "v1 alias selfdoc-scan needs --factory"
        (is (= "cli/usage" (error-type (adapters p "selfdoc-scan"))))))))

;; v1: src/sldb/cli/selfdoc_write.py `_save_document` (untracked branch) + `_prepare`
(deftest returning-symbol-adopts-its-retained-file
  (let [p (project)
        original (slurp (file p "facts.json"))
        names (:created (:data (fake-run p "sync")))
        put (doc-named names "put")]
    (is (true? (:ok (run p "docs" "update" put "--set" "purpose=Kept purpose."))))
    (edit-facts! p (fn [facts] (remove #(= "demo.tools.Box.put" (get % "id")) facts)))
    (is (= [put] (:untracked (:data (fake-run p "sync")))))
    (spit (file p "facts.json") original)
    (is (= [[put "untracked"]] (mapv (juxt :name :status) (get-in (fake-run p "check") [:error :data :documents]))))
    (let [s (fake-run p "sync")
          payload (get-in (run p "docs" "show" put) [:data :payload])]
      (is (true? (:ok s)) (pr-str (:error s)))
      (is (= [put] (:adopted (:data s))))
      (is (= {:created 0 :adopted 1 :updated 0} (select-keys (:counts (:data s)) [:created :adopted :updated])))
      (is (= "docs track" (:operation (first (journal p)))))
      (is (some? (:address (first (journal p)))))
      (is (= "Kept purpose." (get payload "purpose" (get payload :purpose))) "the authored field survives removal and return")
      (let [n (count (journal p)) third (fake-run p "sync")]
        (is (= {:created 0 :adopted 0 :updated 0 :untracked 0} (select-keys (:counts (:data third)) [:created :adopted :updated :untracked])))
        (is (= n (count (journal p))) "a third sync writes nothing")
        (is (true? (:ok (fake-run p "check"))))))))

(deftest v1-aliases-with-the-real-adapters
  (if-not (and (fs/exists? real-adapter) (fs/exists? (str (fs/absolutize "python/kimun/adapters/argparse_cli.py"))))
    (println "skipped v1-aliases-with-the-real-adapters: built-in python adapters are absent")
    (do
      (testing "python-sync / python-check over src/"
        (let [p (real-project) s (adapters p "python-sync" {:system "demo"})]
          (is (true? (:ok s)) (pr-str (:error s)))
          (is (= 3 (:created (:counts (:data s)))))
          (is (true? (:ok (adapters p "python-check" {:system "demo"}))))))
      (testing "selfdoc-sync / selfdoc-check with an argparse factory"
        (let [p (str (ktu/temp-dir "kimun-adapters-selfdoc-"))]
          (is (true? (:ok (run p "stores" "init" "--name" "p"))))
          (spit (file p "app.py")
                (str "import argparse\n\n\ndef build():\n    parser = argparse.ArgumentParser(prog=\"widget\")\n"
                     "    sub = parser.add_subparsers(dest=\"command\")\n    run = sub.add_parser(\"run\", help=\"Run a widget.\")\n"
                     "    run.add_argument(\"--count\", type=int, default=3)\n    return parser\n"))
          (let [s (adapters p "selfdoc-sync" {:factory "app:build" :system "demo"})]
            (is (true? (:ok s)) (pr-str (:error s)))
            (is (= #{"CliCommandDoc" "SurfaceDoc"} (set (map :model (:data (run p "docs" "list"))))))
            (is (true? (:ok (adapters p "selfdoc-check" {:factory "app:build" :system "demo"}))))
            (is (not (fs/exists? (file p "__pycache__"))))))))))

(deftest treesitter-symbols-adapter-is-retired
  (let [p (project) e (adapters p "scan" "treesitter-symbols" "src" "--language" "python" {:system "demo"})]
    (is (= "adapters/missing" (error-type e)))
    (is (= 1 (:exit e)))
    (is (= "unknown-adapter" (get-in e [:error :data :reason])))
    (is (= "treesitter-symbols" (get-in e [:error :data :adapter])))
    (is (not-any? #{"treesitter-symbols"} (get-in e [:error :data :known])))
    (is (some #{"python-symbols"} (get-in e [:error :data :known])) "the other built-ins are still there")))

;; v1: src/sldb/cli/selfdoc_write.py `_save_document` (untracked branch, `{**existing, **fields}`)
(deftest returning-symbol-with-changed-facts-is-adopted-then-updated
  (let [p (project)
        names (:created (:data (fake-run p "sync")))
        put (doc-named names "put")
        original (slurp (file p "facts.json"))]
    (is (true? (:ok (run p "docs" "update" put "--set" "purpose=Kept purpose."))))
    (edit-facts! p (fn [facts] (remove #(= "demo.tools.Box.put" (get % "id")) facts)))
    (is (= [put] (:untracked (:data (fake-run p "sync")))))
    (is (fs/regular-file? (file p "knowledge" "symbols" (str put ".md"))))
    (spit (file p "facts.json") original)
    (set-doc! p "demo.tools.Box.put" "Put an item, carefully.")
    (is (= [[put "untracked"]] (mapv (juxt :name :status) (get-in (fake-run p "check") [:error :data :documents]))))
    (let [n (count (journal p)) s (fake-run p "sync")
          payload (get-in (run p "docs" "show" put) [:data :payload])
          g #(get payload % (get payload (keyword %)))]
      (is (true? (:ok s)) (pr-str (:error s)))
      (is (= [put] (:adopted (:data s))))
      (is (= [put] (mapv :name (:updated (:data s)))))
      (is (empty? (:created (:data s))))
      (is (= "Put an item, carefully." (g "docstring")))
      (is (= "Kept purpose." (g "purpose")))
      (is (= ["docs update" "docs track"] (mapv :operation (take 2 (journal p)))))
      (is (= (+ n 2) (count (journal p))))
      (let [m (count (journal p))]
        (is (true? (:ok (fake-run p "sync"))))
        (is (= m (count (journal p))) "the next sync writes nothing")
        (is (true? (:ok (fake-run p "check"))))))))

(deftest valid-file-is-adopted-before-the-model-is-defined
  (let [donor (project) fresh (project)
        names (:created (:data (fake-run donor "sync")))
        greet (doc-named names "greet")
        rel ["knowledge" "symbols" (str greet ".md")]]
    (fs/create-dirs (file fresh "knowledge" "symbols"))
    (fs/copy (apply file donor rel) (apply file fresh rel))
    (is (empty? (:data (run fresh "models" "list"))))
    (let [s (fake-run fresh "sync")]
      (is (true? (:ok s)) (pr-str (:error s)))
      (is (= [greet] (:adopted (:data s))))
      (is (not (contains? (set (:created (:data s))) greet)))
      (is (= (dec (count names)) (count (:created (:data s)))))
      (is (= ["PythonSymbolDoc"] (:defined (:models (:data s)))))
      (is (contains? (set (map :name (:data (run fresh "docs" "list")))) greet))
      (let [n (count (journal fresh)) again (fake-run fresh "sync")]
        (is (true? (:ok again)))
        (is (= n (count (journal fresh))) "a second sync writes nothing")))))

;; v1: tests/selfdoc/test_preflight.py::test_before_image_guard_prevents_clobbering_concurrent_edits
(deftest before-image-guard-prevents-clobbering-concurrent-edits
  (let [sha256 (fn [s] (hash/sha256-hex (.getBytes ^String s "UTF-8")))
        plan-for (fn [p]
                   (let [output (json/parse-string (slurp (file p "facts.json")))]
                     {:planned (:documents (planner/plan-documents output {:system "demo" :output "knowledge" :sha256 sha256}))
                      :scope {:adapter "python-symbols" :profile "symbols" :language "python" :system "demo" :output "knowledge"}}))]
    (testing "concurrent edit during planning refuses apply-plan! before writing"
      (let [p (project)
            store (:store (cli-store/open! (cli-store/resolve-store {} env p)))
            planned (plan-for p)
            inspected (cmd/inspect store p planned)
            target-file (file p (:path (first (:planned planned))))
            n (count (journal p))]
        (fs/create-dirs (fs/parent target-file))
        (spit target-file "A concurrent edit")
        (let [e (errors/wrap #(cmd/apply-plan! inspected {:format "json" :actor "human/tester"} {:env env :cwd p} p))]
          (is (false? (:ok e)))
          (is (= "adapters/conflict" (error-type e)))
          (is (= 4 (:exit e)))
          (is (= "before-image" (get-in e [:error :data :reason])))
          (is (str/includes? (get-in e [:error :message]) "changed during planning"))
          (is (= "A concurrent edit" (slurp target-file)))
          (is (= n (count (journal p)))))))
    (testing "external edit on disk prevents adapters sync from overwriting silently, while normal sync writes"
      (let [p (project)
            names (:created (:data (fake-run p "sync")))
            greet (doc-named names "greet")
            f (file p "knowledge" "symbols" (str greet ".md"))
            tracked-text (slurp f)]
        (is (str/includes? tracked-text "Return a greeting") "normal create wrote the selfdoc file")
        (set-doc! p "demo.tools.greet" "Return a much better greeting.")
        (let [n (count (journal p))]
          (spit f "External edit on disk\n")
          (let [c (fake-run p "sync")]
            (is (false? (:ok c)))
            (is (= "adapters/conflict" (error-type c)))
            (is (= 4 (:exit c)))
            (is (= "before-image" (get-in c [:error :data :reason])))
            (is (= [(:path (first (filter #(= greet (:name %)) (:planned (plan-for p)))))]
                   (get-in c [:error :data :paths])))
            (is (= "External edit on disk\n" (slurp f)) "externally modified source is not overwritten")
            (is (= n (count (journal p))) "nothing committed on before-image conflict"))
          (spit f tracked-text)
          (testing "concurrent edit between inspect and apply-plan! on a tracked update is also refused"
            (let [store (:store (cli-store/open! (cli-store/resolve-store {} env p)))
                  inspected (cmd/inspect store p (plan-for p))]
              (spit f "Edited between inspect and apply\n")
              (let [c2 (errors/wrap #(cmd/apply-plan! inspected {:format "json" :actor "human/tester"} {:env env :cwd p} p))]
                (is (false? (:ok c2)))
                (is (= "adapters/conflict" (error-type c2)))
                (is (= 4 (:exit c2)))
                (is (= "Edited between inspect and apply\n" (slurp f)))
                (is (= n (count (journal p)))))))
          (spit f tracked-text)
          (let [s (fake-run p "sync")]
            (is (true? (:ok s)) (pr-str (:error s)))
            (is (= [greet] (mapv :name (:updated (:data s)))))
            (is (str/includes? (slurp f) "much better greeting") "normal sync writes updated source")
            (is (= (inc n) (count (journal p))))))))))

;; v1: tests/selfdoc/test_python_scanner.py::test_kgdb_graph_check_rejects_missing_or_stale_source_nodes
;; v1: tests/selfdoc/test_python_scanner.py::test_source_graph_check_rejects_missing_or_stale_source_nodes
(deftest kgdb-graph-check-rejects-missing-or-stale-source-nodes
  (if-not (fs/exists? real-adapter)
    (println "skipped kgdb-graph-check-rejects-missing-or-stale-source-nodes: python/kimun/adapters/python_symbols.py is absent")
    (let [p (real-project)
          tools (file p "src" "demo" "tools.py")]
      (testing "missing: python-check rejects unsynced source nodes before sync"
        (let [c (adapters p "python-check" {:system "demo"})]
          (is (false? (:ok c)))
          (is (= 1 (:exit c)))
          (is (= :missing (get-in c [:error :data :source-graph :status])))
          (is (false? (get-in c [:error :data :source-graph :current?])))
          (is (= 3 (count (get-in c [:error :data :source-graph :missing]))))))
      (testing "current: python-check passes immediately after python-sync"
        (is (true? (:ok (adapters p "python-sync" {:system "demo"}))))
        (let [c (adapters p "python-check" {:system "demo"})]
          (is (true? (:ok c)))
          (is (true? (get-in c [:data :source-graph :current?])))
          (is (= :current (get-in c [:data :source-graph :status])))))
      (testing "stale source: editing function implementation without re-sync fails python-check"
        (spit tools (str/replace-first (slurp tools) "return name" "return name.upper() if loud else name"))
        (let [c (adapters p "python-check" {:system "demo"})
              sg (get-in c [:error :data :source-graph])]
          (is (false? (:ok c)))
          (is (= 1 (:exit c)))
          (is (= :stale (:status sg)))
          (is (false? (:current? sg)))
          (is (contains? (set (:stale sg)) "python-demo-demo-tools-greet"))))
      (is (true? (:ok (adapters p "python-sync" {:system "demo"}))))
      (testing "modified-externally: tampering symbol markdown on disk fails check"
        (let [f (file p "knowledge" "symbols" "python-demo-demo-tools-greet.md")
              orig (slurp f)]
          (spit f (str orig "\n<!-- stale edit -->\n"))
          (let [c (adapters p "python-check" {:system "demo"})
                sg (get-in c [:error :data :source-graph])]
            (is (false? (:ok c)))
            (is (= 1 (:exit c)))
            (is (= :stale (:status sg)))
            (is (= ["python-demo-demo-tools-greet"] (:stale sg)))
            (is (some #(= ["python-demo-demo-tools-greet" "modified-externally"] [(:name %) (:status %)])
                      (get-in c [:error :data :documents]))))
          (spit f orig)))
      (testing "extra orphan node: removing a symbol from source fails python-check until re-sync"
        (spit tools (str "\"\"\"Tools of the demo.\"\"\"\nimport os\n\n\ndef greet(name, loud=False):\n    \"\"\"Return a greeting for name.\"\"\"\n    return name.upper() if loud else name\n"))
        (let [c (adapters p "python-check" {:system "demo"})
              sg (get-in c [:error :data :source-graph])]
          (is (false? (:ok c)))
          (is (= 1 (:exit c)))
          (is (= :stale (:status sg)))
          (is (= #{"python-demo-demo-tools-box" "python-demo-demo-tools-box-put"} (set (:orphan sg)))))))))
