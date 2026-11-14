(ns kimun.parity.adapters-parity-test
  "Parity with sldb v1's selfdoc on v1's own data, runnable without v1.

   `test/fixtures/parity/adapters/` (see its generate.py) freezes a sample of v1's source tree (`src/`, 14 files,
   70 symbols), the argparse adapter's facts for three v1 CLI groups (`cli-facts.json`), and for every document
   v1 generates from them: `v1_live` (the parser-owned fields v1's own scanner + planner give today),
   `v1_live_authored` (the rest of the payload v1 would write) and `v1_tracked` (the payload v1 actually tracks,
   possibly stale, or null).

   The tests run `python_symbols.py` live over the frozen sources (and take the frozen CLI facts), plan with
   `kimun.surface.adapters`, and assert: every parser-owned field equals `v1_live` (no exception allowed); the
   merged payload equals v1's; and against `v1_tracked` the only differences are the ones listed, one by one, in
   EXPECTED-DIFFS.edn — an entry that is no longer observed fails too. The last tests port the v1 selfdoc tests
   that could only be written against the planner / CLI."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.commands.adapters :as cmd]
            [kimun.cli.errors :as errors]
            [kimun.cli.main :as main]
            [kimun.surface.adapters :as ad]
            [kimun.host.hash :as hash]))

(def ^:private py (or (System/getenv "KIMUN_ADAPTER_PYTHON") "python3"))

(def ^:private root
  "The worktree root: the directory holding `resources/` (and so `python/`, `test/`)."
  (-> (io/resource "VERSION") .getPath fs/path fs/parent fs/parent))

(defn- fx [& parts] (str (apply fs/path root "test" "fixtures" "parity" "adapters" parts)))

(defn- frozen [file] (json/parse-string (slurp (fx file))))

(defn- sha256 [s] (hash/sha256-hex (.getBytes ^String s "UTF-8")))

(defn- adapter
  "Parsed stdout of a built-in adapter run live; `env` is added to the environment."
  [env script & args]
  (let [r (apply process/shell {:out :string :err :string :continue true :dir (str root)
                                :extra-env (merge {"PYTHONDONTWRITEBYTECODE" "1"} env)}
                 py (str (fs/path root "python" "kimun" "adapters" script)) args)]
    (is (zero? (:exit r)) (:err r))
    (json/parse-string (:out r))))

(defn- plan-symbols
  "kimun's plan for the frozen source sample, invoked as v1 invoked its own python-sync."
  []
  (let [{:strs [package system output architecture_spec]} ((frozen "symbols.json") "invocation")]
    (ad/plan-documents (adapter {} "python_symbols.py" (fx "src") "--system" system "--package" package)
                       {:system system :output output :architecture-spec architecture_spec :sha256 sha256})))

(defn- plan-cli
  "kimun's plan for the frozen argparse facts of v1's parser."
  []
  (let [{:strs [factory system output]} ((frozen "cli.json") "invocation")]
    (ad/plan-documents (frozen "cli-facts.json") {:system system :output output :factory factory :sha256 sha256})))

(defn- planned-key [d] [(:name d) (get-in d [:fields "source_span"])])
(defn- frozen-key [d] [(d "name") (get-in d ["v1_live" "source_span"])])

(defn- assert-equals-v1-live
  "Field by field: planned documents = what v1 generates now. Returns the planned docs keyed like the frozen ones."
  [planned docs]
  (let [by-key (into {} (map (juxt planned-key identity)) planned)]
    (is (= (count planned) (count by-key)) "a document is identified by its name (+ span when v1 collides)")
    (is (= (set (map frozen-key docs)) (set (keys by-key))) "same document set as v1")
    (doseq [d docs :let [p (by-key (frozen-key d)) live (d "v1_live")]]
      (testing (d "name")
        (is (= (d "path") (:path p)))
        (is (= (set (keys live)) (set (keys (:fields p)))) "same parser-owned field set")
        (doseq [[field v] live]
          (is (= v (get-in p [:fields field])) field))
        (is (= (merge (d "v1_live_authored") live) (ad/merge-payload p (d "v1_tracked")))
            "v1 `{**existing, **fields}`: authored fields kept, defaults for a new document")))
    by-key))

(defn- observed-diffs
  "name → what differs between kimun's plan and the document v1 tracks, in EXPECTED-DIFFS.edn's shape."
  [by-key docs]
  (into {}
        (for [d docs
              :let [tracked (d "v1_tracked") fields (:fields (by-key (frozen-key d)))
                    stale (vec (sort (for [[k v] fields :when (and tracked (not= v (tracked k)))] k)))]
              :when (or (nil? tracked) (seq stale))]
          [(d "name") (if tracked {:fields stale} {:absent "v1-tracked"})])))

(defn- tracked-entries [docs model-of]
  (for [d docs :when (d "v1_tracked")]
    {:name (d "name") :model (model-of d) :path (d "path") :payload (d "v1_tracked")}))

(defn- assert-sync-plan
  "v1's `changed` flag, per document: create = never generated, update = stale (with exactly the stale fields)."
  [planned docs model-of]
  (let [plan (ad/sync-plan planned (tracked-entries docs model-of))
        diffs (observed-diffs (into {} (map (juxt planned-key identity)) planned) docs)
        names (fn [k] (set (map :name (k plan))))]
    (is (= (set (for [[n d] diffs :when (:absent d)] n)) (names :create)))
    (is (= (into {} (for [[n d] diffs :when (:fields d)] [n (:fields d)]))
           (into {} (map (juxt :name :changed)) (:update plan))))
    (is (= (set (remove diffs (map #(% "name") docs))) (names :unchanged)))
    (is (empty? (:untrack plan)))
    (is (= (boolean (seq diffs)) (:drift? (ad/drift-report plan))))))

(deftest python-symbols-equal-v1-live
  (let [{:strs [documents collisions collision_error]} (frozen "symbols.json")
        {planned :documents problems :problems} (plan-symbols)]
    (is (<= 60 (count documents)))
    (is (<= 10 (count (fs/glob (fx "src") "**.py"))))
    (assert-equals-v1-live planned documents)
    (testing "v1 refuses the same id collisions (python_materialize._validate_paths)"
      (is (= (count collisions) (count problems)))
      (is (every? #(= :adapters/collision (:code %)) problems))
      (doseq [c collisions]
        (is (some #(str/starts-with? (:message %) (str collision_error ": " c " <- ")) problems) c)))
    (assert-sync-plan planned documents (constantly "PythonSymbolDoc"))))

(deftest cli-documents-equal-v1-live
  (let [{:strs [documents]} (frozen "cli.json")
        {planned :documents problems :problems} (plan-cli)]
    (is (empty? problems))
    (is (= (frequencies (map #(% "model") documents)) (frequencies (map :model planned))))
    (assert-equals-v1-live planned documents)
    (is (= (into {} (map (juxt #(% "name") #(% "model"))) documents) (into {} (map (juxt :name :model)) planned)))
    (assert-sync-plan planned documents #(% "model"))))

(deftest differences-with-v1-tracked-are-exactly-the-expected-ones
  (let [expected (into {} (map (juxt :document #(select-keys % [:fields :absent]))) (edn/read-string (slurp (fx "EXPECTED-DIFFS.edn"))))
        by-key (fn [r] (into {} (map (juxt planned-key identity)) (:documents r)))
        observed (merge (observed-diffs (by-key (plan-symbols)) ((frozen "symbols.json") "documents"))
                        (observed-diffs (by-key (plan-cli)) ((frozen "cli.json") "documents")))]
    (is (every? (comp seq :reason) (edn/read-string (slurp (fx "EXPECTED-DIFFS.edn")))) "every entry says why")
    (doseq [n (sort (into (set (keys expected)) (keys observed)))]
      (is (= (expected n) (observed n))
          (cond (nil? (expected n)) (str n ": differs from v1's tracked document and is not in EXPECTED-DIFFS.edn")
                (nil? (observed n)) (str n ": stale EXPECTED-DIFFS.edn entry, the difference is no longer observed")
                :else (str n ": differs in other fields than the listed ones"))))))

(defn- run-provenance
  "Provenance kimun plans for command `run` of a stand-alone argparse factory, labelled like v1's test."
  [factory env]
  (let [out (adapter env "argparse_cli.py" (fx "cliapp") "--factory" (str "hashapp:" factory))
        doc (first (filter #(= "cmd-demo-run" (:name %))
                           (:documents (ad/plan-documents out {:system "demo" :factory "fixture:build" :sha256 sha256}))))]
    (is (some? doc))
    (get-in doc [:fields "provenance"])))

;; v1: tests/selfdoc/test_parser.py::test_contract_hash_changes_with_parser_default
(deftest contract-hash-changes-with-parser-default
  (let [first-run (run-provenance "build_one" {})]
    (is (str/starts-with? first-run "parser:fixture:build#run; contract-sha256:"))
    (is (= first-run (run-provenance "build_one" {})))
    (is (not= first-run (run-provenance "build_two" {})))))

;; v1: tests/selfdoc/test_parser.py::test_contract_hash_does_not_depend_on_terminal_width
(deftest contract-hash-does-not-depend-on-terminal-width
  (is (= (run-provenance "build_wide" {"COLUMNS" "40"}) (run-provenance "build_wide" {"COLUMNS" "160"}))))

;; v1: tests/selfdoc/test_python_scanner.py::test_rename_candidates_reject_ambiguous_matches
;; kimun has no rename heuristic (v1 cli/selfdoc_inspect.rename_candidates): what is kept is the outcome the v1
;; test protects — two new documents that could both be "the renamed one" are never paired with the removed one.
(deftest ambiguous-renames-are-never-paired
  (let [doc (first (:documents (plan-symbols)))
        tracked {:name "old" :model (:model doc) :path "knowledge/symbols/old.md" :payload (assoc (ad/merge-payload doc nil) "id" "old")}
        plan (ad/sync-plan [(assoc doc :name "new-a") (assoc doc :name "new-b")] [tracked])]
    (is (= ["new-a" "new-b"] (map :name (:create plan))))
    (is (= ["old"] (map :name (:untrack plan))))
    (is (empty? (:update plan)))
    (is (= [["new-a" "missing"] ["new-b" "missing"] ["old" "orphan"]]
           (map (juxt :name :status) (:documents (ad/drift-report plan)))))))

;; v1: tests/selfdoc/test_python_scanner.py::test_python_check_returns_failure_when_report_is_not_ok
(deftest python-check-fails-when-the-report-is-not-ok
  (let [p (str (ktu/temp-dir "kimun-adapters-parity-"))
        env {"USER" "tester"}]
    (try
      (is (true? (get-in (main/run ["stores" "init" "--name" "p"] env p) [:envelope :ok])))
      (fs/create-dirs (fs/path p "src" "sldb" "links"))
      (fs/copy (fx "src" "sldb" "links" "composer.py") (fs/path p "src" "sldb" "links" "composer.py"))
      (let [c (errors/wrap #(cmd/dispatch "python-check" [] {:format "json" :actor "human/tester" :system "sldb" :source-root "src"}
                                          {:env env :cwd p}))]
        (is (not (:ok c)))
        (is (= 1 (:exit c)))
        (is (= (subs (str cmd/drift-type) 1) (get-in c [:error :type])))
        (is (not (fs/exists? (fs/path p "knowledge"))) "check writes nothing"))
      (finally (fs/delete-tree p)))))

;; v1: tests/selfdoc/test_python_scanner.py::test_kgdb_graph_check_rejects_missing_or_stale_source_nodes
;; v1: tests/selfdoc/test_python_scanner.py::test_source_graph_check_rejects_missing_or_stale_source_nodes
(deftest kgdb-graph-check-rejects-missing-or-stale-source-nodes
  (testing "pure source-graph check matches v1 graph_is_current contract"
    (let [snapshot {"nodes" [{"identity" {"node_id" "node" "node_type" "python_symbol"}
                              "source" {"sha256" "a"}
                              "ast" {"line" 1}}]}]
      (is (false? (ad/source-graph-current? snapshot nil)))
      (is (false? (ad/source-graph-current?
                   snapshot
                   {"nodes" [{"id" "node" "schema" {"source" {"sha256" "b"} "ast" {"line" 1}}}]})))
      (is (true? (ad/source-graph-current?
                  snapshot
                  {"nodes" [{"id" "node" "schema" {"source" {"sha256" "a"} "ast" {"line" 1}}}]})))
      (is (false? (ad/source-graph-current?
                   snapshot
                   {"nodes" [{"id" "node" "schema" {"source" {"sha256" "a"} "ast" {"line" 1}}}
                             {"id" "stale" "schema" {}}]})))))
  (testing "CLI python-check rejects missing, stale source sha256/ast, and extra tracked source nodes"
    (let [p (str (ktu/temp-dir "kimun-adapters-parity-kgdb-"))
          env {"USER" "tester"}
          mod (fs/path p "src" "sldb" "links" "composer.py")]
      (try
        (is (true? (get-in (main/run ["stores" "init" "--name" "p"] env p) [:envelope :ok])))
        (fs/create-dirs (fs/parent mod))
        (fs/copy (fx "src" "sldb" "links" "composer.py") mod)
        (let [missing (errors/wrap #(cmd/dispatch "python-check" [] {:format "json" :actor "human/tester" :system "sldb" :source-root "src"}
                                                  {:env env :cwd p}))]
          (is (not (:ok missing)))
          (is (= 1 (:exit missing)))
          (is (= :missing (get-in missing [:error :data :source-graph :status])))
          (is (seq (get-in missing [:error :data :source-graph :missing]))))
        (is (true? (:ok (errors/wrap #(cmd/dispatch "python-sync" [] {:format "json" :actor "human/tester" :system "sldb" :source-root "src"}
                                                    {:env env :cwd p})))))
        (let [ok-res (errors/wrap #(cmd/dispatch "python-check" [] {:format "json" :actor "human/tester" :system "sldb" :source-root "src"}
                                                 {:env env :cwd p}))]
          (is (:ok ok-res))
          (is (true? (get-in ok-res [:data :source-graph :current?]))))
        (spit (str mod) (str (slurp (str mod)) "\n\ndef extra_symbol():\n    return 1\n"))
        (let [new-node (errors/wrap #(cmd/dispatch "python-check" [] {:format "json" :actor "human/tester" :system "sldb" :source-root "src"}
                                                   {:env env :cwd p}))]
          (is (not (:ok new-node)))
          (is (= 1 (:exit new-node)))
          (is (contains? (set (get-in new-node [:error :data :source-graph :missing]))
                         "python-sldb-sldb-links-composer-extra_symbol")))
        (is (true? (:ok (errors/wrap #(cmd/dispatch "python-sync" [] {:format "json" :actor "human/tester" :system "sldb" :source-root "src"}
                                                    {:env env :cwd p})))))
        (spit (str mod) (str/replace-first (slurp (str mod)) "return 1" "return 2"))
        (let [stale (errors/wrap #(cmd/dispatch "python-check" [] {:format "json" :actor "human/tester" :system "sldb" :source-root "src"}
                                                {:env env :cwd p}))]
          (is (not (:ok stale)))
          (is (= 1 (:exit stale)))
          (is (= :stale (get-in stale [:error :data :source-graph :status])))
          (is (contains? (set (get-in stale [:error :data :source-graph :stale]))
                         "python-sldb-sldb-links-composer-extra_symbol")))
        (fs/copy (fx "src" "sldb" "links" "composer.py") mod {:replace-existing true})
        (let [orphan (errors/wrap #(cmd/dispatch "python-check" [] {:format "json" :actor "human/tester" :system "sldb" :source-root "src"}
                                                 {:env env :cwd p}))]
          (is (not (:ok orphan)))
          (is (= 1 (:exit orphan)))
          (is (= :stale (get-in orphan [:error :data :source-graph :status])))
          (is (contains? (set (get-in orphan [:error :data :source-graph :orphan]))
                         "python-sldb-sldb-links-composer-extra_symbol")))
        (finally (fs/delete-tree p))))))
