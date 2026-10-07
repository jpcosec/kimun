(ns kimun.cli.predicates-test
  "The `predicates` group over a real store: v1 `tests/test_predicates_cli.py`
   (4 functions) plus exits and typed errors. The group is registered in
   `kimun.cli.main` at merge, so it is called through its `dispatch`."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.commands.predicates :as cpred]
            [kimun.cli.errors :as errors]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.surface.edges :as edges]
            [kimun.surface.links :as links]
            [sldb.host.default :as host]
            [sldb.kernel.ports :as ports]
            [sldb.kernel.store :as store]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run (vec argv) env cwd)))
(defn- predicates
  "Runs `predicates SUB ARGS…`; a trailing map is merged into the options."
  [cwd sub & args]
  (let [extra (when (map? (last args)) (last args)) args (if extra (butlast args) args)]
    (errors/wrap #(cpred/dispatch sub args (merge {:format "json" :actor "human/tester"} extra) {:env env :cwd cwd}))))

(defn- store-dir []
  (let [p (str (ktu/temp-dir "kimun-predicates-"))]
    (is (true? (:ok (run p "stores" "init" "--name" "p"))))
    p))

(defn- opened [p] (cli-store/open! (cli-store/resolve-store {} env p)))
(defn- names [p] (set (map :name (get-in (predicates p "list") [:data :predicates]))))

;; tests/test_predicates_cli.py::test_store_init_seeds_editable_predicates
(deftest store-init-seeds-editable-predicates
  (let [p (store-dir)]
    (is (every? (names p) ["implements" "grounded_by"]))
    (is (= 7 (get-in (predicates p "list") [:data :count])))
    (let [e (predicates p "show" "implements")]
      (is (true? (:ok e)))
      (is (= "predicates show" (:command e)))
      (is (= {:name "implements" :axis "HOW"} (select-keys (:data e) [:name :axis]))))
    (testing "editable: a default can be removed, the rest stay"
      (is (= {:changed true :removed "implements" :count 6} (:data (predicates p "remove" "implements"))))
      (is (not ((names p) "implements")))
      (is ((names p) "grounded_by")))))

;; tests/test_predicates_cli.py::test_predicates_add_list_validate_and_remove
(deftest predicates-add-list-validate-and-remove
  (let [p (store-dir) before (:head (:store (opened p)))
        added (predicates p "add" "depends_on" {:axis "dependency" :description "Declares a dependency."})
        custom {:name "depends_on" :axis "DEPENDENCY" :description "Declares a dependency."}]
    (is (true? (:ok added)) (pr-str (:error added)))
    (is (= {:changed true :predicate custom} (:data added)))
    (is (not= before (:revision added)) "the write is a revision of the store")
    (is (= custom (first (filter #(= "depends_on" (:name %)) (get-in (predicates p "list") [:data :predicates])))))
    (is (= 8 (count (names p))) "the seven defaults are kept when the tree is first written")
    (is (= {:valid true :errors [] :checked 8} (:data (predicates p "validate"))))
    (is (= {:valid true :errors [] :checked 1} (:data (predicates p "validate" "depends_on"))))
    (is (= "DEPENDENCY" (get (edges/predicate-registry (:store (opened p))) "depends_on")))
    (is (true? (:ok (predicates p "remove" "depends_on"))))
    (is (not ((names p) "depends_on")))
    (is (= 7 (count (names p))))))

;; tests/test_predicates_cli.py::test_predicates_validate_reports_duplicate_yaml_entries
;; v1 hand-edits the index YAML; here the duplicate is a second node of the same name in the
;; relations tree, written by a plan that skipped the registry check `predicates add` makes.
(deftest predicates-validate-reports-duplicate-entries
  (let [p (store-dir)
        {:keys [backend] s :store} (opened p)
        {:keys [plan]} (with-redefs [edges/predicate-registry (constantly {})]
                         (edges/add-predicate-plan host/host s {:name "implements" :axis "HOW" :description "Again."}
                                                   {:actor "human/tester" :timestamp "2026-01-01T00:00:00.000Z"
                                                    :tree-id (ports/ulid (:ids host/host))}))]
    (store/commit! backend s plan)
    (let [e (predicates p "validate")]
      (is (false? (:ok e)))
      (is (= "edges/invalid" (get-in e [:error :type])))
      (is (= 3 (:exit e)) "v1 exited 1; a failed validation is a rejection in kimun's exit table")
      (is (false? (get-in e [:error :data :valid])))
      (is (some #(str/includes? % "Duplicate predicate") (get-in e [:error :data :errors]))))
    (is (false? (:ok (predicates p "validate" "implements"))))
    (is (true? (:ok (predicates p "validate" "defines"))) "the other entries are valid on their own")))

;; tests/test_predicates_cli.py::test_docs_recover_uses_custom_store_predicate
;; The registry a custom predicate lands in is what the link parser takes; `docs recover`
;; itself still passes its hardcoded map (kimun.cli.commands.links, not ours to edit).
(deftest the-link-parser-reads-a-custom-store-predicate
  (let [p (store-dir)]
    (is (true? (:ok (predicates p "add" "depends_on" {:axis "DEPENDENCY"}))))
    (let [registry (edges/predicate-registry (:store (opened p)))
          found (links/parse-links "[depends_on:: [[target.md]]]" registry)]
      (is (= "DEPENDENCY" (get registry "depends_on")))
      (is (= 1 (count found)))
      (is (str/includes? (pr-str (first found)) "depends_on"))
      (is (str/includes? (pr-str (first found)) "DEPENDENCY")))))

(deftest typed-errors-and-exits
  (let [p (store-dir)
        failed (fn [e] [(get-in e [:error :type]) (:exit e)])]
    (testing "usage"
      (is (= ["cli/usage" 6] (failed (predicates p "add" "x"))) "--axis is required")
      (is (= ["cli/usage" 6] (failed (predicates p "add" "x" {:axis true}))) "--axis without a value")
      (is (= ["cli/usage" 6] (failed (predicates p "add" {:axis "HOW"}))))
      (is (= ["cli/usage" 6] (failed (predicates p "show"))))
      (is (= ["cli/usage" 6] (failed (predicates p "remove"))))
      (is (= ["cli/usage" 6] (failed (predicates p "nope"))))
      (is (= ["cli/usage" 6] (failed (predicates p nil)))))
    (testing "missing"
      (is (= ["edges/missing" 1] (failed (predicates p "show" "nope"))))
      (is (= ["edges/missing" 1] (failed (predicates p "remove" "nope"))))
      (is (= ["edges/missing" 1] (failed (predicates p "validate" "nope")))))
    (testing "conflict and invalid"
      (is (= ["edges/conflict" 4] (failed (predicates p "add" "implements" {:axis "HOW"}))))
      (is (= ["edges/invalid" 3] (failed (predicates p "add" "bad name" {:axis "HOW"}))))
      (is (= ["edges/invalid" 3] (failed (predicates p "add" "fine" {:axis "no good"})))))
    (testing "an actor without capability is refused and nothing is written"
      (let [head (:head (:store (opened p)))
            e (predicates p "add" "sneaky" {:axis "HOW" :actor "human/intruder"})]
        (is (false? (:ok e)))
        (is (= ["plan/rejected" 7] (failed e)))
        (is (= head (:head (:store (opened p)))))
        (is (not ((names p) "sneaky")))))))
