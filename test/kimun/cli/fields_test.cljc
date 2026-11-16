(ns kimun.cli.fields-test
  "The `fields` group end to end on temporary stores: v1's `sldb fields`
   (show, query, create, update, remove, append, clean) under both
   vocabularies, and what a field mutation leaves in the store, the file and
   the journal. The group is called through `dispatch` (it is not registered
   in `kimun.cli.main` yet); stores are set up with the existing groups. Each
   test carrying a v1 promise names the v1 function in a comment."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.commands.fields :as cmd]
            [kimun.cli.errors :as errors]
            [kimun.cli.main :as main]
            [kimun.surface.fields-test :as pure]))

(def env {"USER" "tester"})

(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))

(defn- fields
  "The envelope of `fields SUB ARGS…`; a trailing map is merged into the options."
  [cwd sub & args]
  (let [opts (when (map? (last args)) (last args))
        args (if opts (butlast args) args)]
    (errors/wrap #(cmd/dispatch sub (vec args) (merge {:format "json" :actor "human/tester"} opts) {:env env :cwd cwd}))))

(defn- file [p & parts] (str (apply fs/path p parts)))

(defn- project
  "v1 `_setup_store`: a store with RoadmapDoc and the document `roadmap`."
  []
  (let [p (str (ktu/temp-dir "kimun-fields-"))]
    (run p "stores" "init" "--name" "p")
    (spit (file p "roadmap.edn") (pr-str pure/roadmap))
    (spit (file p "roadmap.json") (json/generate-string pure/payload))
    (is (true? (:ok (run p "models" "define" "-f" "roadmap.edn"))))
    (is (true? (:ok (run p "docs" "create" "RoadmapDoc" "roadmap" "--data" "roadmap.json"))))
    p))

(defn- value [p & target] (get-in (apply fields p "get" target) [:data :value]))
(defn- head [p] (:revision (run p "docs" "list")))

(deftest v1-update-append-and-clean
  ;; v1: test_fields_update_append_and_clean
  (let [p (project)]
    (is (true? (:ok (fields p "append" "docs/roadmap/tasks" "\"Ship CLI\""))))
    (is (= ["Ship CLI" "Write docs" "Ship CLI"] (value p "roadmap" "tasks")))
    (is (true? (:ok (fields p "clean" "docs/roadmap/tasks" {:dedupe true}))))
    (is (true? (:ok (fields p "update" "docs/roadmap/status" "\"accepted\""))))
    (is (= "accepted" (get-in (fields p "show" "docs/roadmap/status") [:data :value])))
    (is (= ["Ship CLI" "Write docs"] (get-in (fields p "show" "docs/roadmap/tasks") [:data :value])))
    (is (= ["Ship CLI" "Write docs"] (get-in (fields p "show" "docs/RoadmapDoc/roadmap/tasks") [:data :value])) "docs/Model/doc/field")
    (testing "the file is the document"
      (is (str/includes? (slurp (file p "roadmap.md")) "Status: accepted"))
      (is (true? (:ok (run p "stores" "verify")))))
    (testing "v1 update refuses an absent field, v1 create (= set) makes it"
      (is (= 1 (:exit (fields p "update" "docs/roadmap/extra" "x"))))
      (is (= "fields/missing" (get-in (fields p "update" "docs/roadmap/extra" "x") [:error :type]))))
    (is (true? (:ok (fields p "clean" "roadmap" "tasks" {:dedupe "true"}))) "a string flag is coerced")
    (is (= 6 (:exit (fields p "clean" "roadmap" "tasks" {:dedupe "maybe"}))))))

(deftest v1-update-can-clear-a-list-field
  ;; v1: test_fields_update_can_clear_list_field
  (let [p (project)]
    (is (true? (:ok (fields p "update" "docs/roadmap/tasks" "[]"))))
    (is (= [] (get-in (fields p "show" "docs/roadmap/tasks") [:data :value])))
    (testing "kimun's `clear` says the same"
      (fields p "set" "roadmap" "tasks" "[a, b]")
      (is (= {:before ["a" "b"] :after []} (select-keys (:data (fields p "clear" "roadmap" "tasks")) [:before :after])))
      (is (= [] (value p "roadmap" "tasks"))))))

(deftest v1-field-query-includes-owning-section
  ;; v1: test_field_query_includes_owning_section; test_find_field_shows_owning_section (the rows a
  ;; `find --type field` hit is built from: kimun.surface.fields/rows through fields/query)
  (let [p (project)
        e (fields p "query" "title")]
    (is (= "fields query" (:command e)))
    (is (= "roadmap" (get-in e [:data :results 0 :owning_section])))
    (is (= {:model "RoadmapDoc" :doc "roadmap" :path "roadmap.md" :field "title" :value "Roadmap" :owning_section "roadmap"}
           (get-in e [:data :results 0])))
    (is (= "roadmap/tasks" (get-in (fields p "query" "tasks") [:data :results 0 :owning_section])))
    (is (= "roadmap/tasks" (get-in (fields p "get" "roadmap" "tasks.0") [:data :owning_section])))
    (testing "--where and --model narrow the documents"
      (spit (file p "b.json") (json/generate-string (assoc pure/payload "title" "Second" "status" "done")))
      (run p "docs" "create" "RoadmapDoc" "second" "--data" "b.json")
      (is (= ["roadmap" "second"] (mapv :doc (get-in (fields p "query" "title") [:data :results]))))
      (is (= ["second"] (mapv :doc (get-in (fields p "query" "title" {:where "status = \"done\""}) [:data :results]))))
      (is (= [] (get-in (fields p "query" "title" {:model "GhostDoc"}) [:data :results])))
      (is (= [] (get-in (fields p "query" "nope") [:data :results])) "no hit is an empty result")
      (is (= "query/bad-where" (get-in (fields p "query" "title" {:where "status = "}) [:error :type])))
      (is (= 6 (:exit (fields p "query")))))))

(deftest get-set-unset-append-under-the-kimun-names
  (let [p (project)
        e (fields p "set" "roadmap" "status" "accepted")]
    (is (= "fields set" (:command e)))
    (is (= {:doc "roadmap" :model "RoadmapDoc" :address "RoadmapDoc:roadmap" :operation "fields set" :field "status" :path "status"
            :before "draft" :after "accepted" :unchanged? false :owning_section "roadmap" :file "roadmap.md"}
           (:data e)))
    (is (= [] (:warnings e)))
    (is (= "accepted" (value p "RoadmapDoc:roadmap" "status")) "the address Model:doc")
    (is (= "2024" (:after (:data (fields p "set" "roadmap" "title" "2024")))) "coerced to the declared string")
    (testing "list indexes"
      (is (= "Write tests" (:after (:data (fields p "set" "roadmap" "tasks.1" "Write tests")))))
      (is (= ["Ship CLI" "Write tests"] (value p "roadmap" "tasks")))
      (is (= "Ship CLI" (:before (:data (fields p "unset" "roadmap" "tasks.0")))))
      (is (= ["Write tests" "More"] (:after (:data (fields p "append" "roadmap" "tasks" "More")))))
      (is (= "Write tests" (:before (:data (fields p "remove" "docs/roadmap/tasks/0")))) "v1 remove, slash path"))
    (testing "a mutation that changes nothing writes nothing"
      (let [before (head p) again (fields p "set" "roadmap" "status" "accepted")]
        (is (true? (get-in again [:data :unchanged?])))
        (is (= before (:revision again) (head p)))))))

(deftest errors-are-typed
  (let [p (project) type-of #(get-in % [:error :type])]
    (testing "missing field"
      (is (= ["fields/missing" 1] ((juxt type-of :exit) (fields p "get" "roadmap" "nope"))))
      (is (= ["fields/missing" 1] ((juxt type-of :exit) (fields p "get" "roadmap" "tasks.9"))))
      (is (= ["fields/missing" 1] ((juxt type-of :exit) (fields p "append" "roadmap" "nope" "x")))))
    (is (= ["fields/wrong-type" 3] ((juxt type-of :exit) (fields p "set" "roadmap" "tasks" "5"))) "wrong type")
    (is (= ["fields/not-a-list" 3] ((juxt type-of :exit) (fields p "append" "roadmap" "status" "x"))) "append on a non-list")
    (is (= ["fields/not-a-list" 3] ((juxt type-of :exit) (fields p "clean" "roadmap" "status" {:dedupe true}))))
    (is (= ["docs/missing" 1] ((juxt type-of :exit) (fields p "get" "ghost" "title"))))
    (is (= ["docs/missing" 1] ((juxt type-of :exit) (fields p "show" "docs/ghost/title"))))
    (is (= ["models/missing" 1] ((juxt type-of :exit) (fields p "schema" "GhostDoc"))))
    (is (= ["fields/missing" 1] ((juxt type-of :exit) (fields p "show" "models/RoadmapDoc/nope"))))
    (testing "usage"
      (is (= 6 (:exit (fields p "set" "roadmap" "status"))) "no VALUE")
      (is (= 6 (:exit (fields p "get" "roadmap"))) "no PATH")
      (is (= 6 (:exit (fields p "show" "docs/roadmap"))) "no field in a v1 target")
      (is (= 6 (:exit (fields p "set" "roadmap" "status" "[unclosed"))) "VALUE is not YAML")
      (is (= ["cli/usage" 6] ((juxt type-of :exit) (fields p "explode")))))))

(deftest schema-under-both-vocabularies
  (let [p (project)]
    (is (= (pure/roadmap :fields)
           (mapv #(assoc (select-keys % [:name :description :required]) :type (get {"string" :string} (:type %) [:list :string]))
                 (get-in (fields p "schema" "RoadmapDoc") [:data :fields]))))
    (is (= (:data (fields p "schema" "RoadmapDoc")) (:data (fields p "show" "models/RoadmapDoc"))))
    (is (= {:model "RoadmapDoc" :field "tasks" :type "list[string]"}
           (select-keys (:data (fields p "show" "models/RoadmapDoc/tasks")) [:model :field :type])))
    (is (= (:data (fields p "schema" "RoadmapDoc" "tasks")) (:data (fields p "get" "models/RoadmapDoc/tasks"))))))

(deftest a-refused-mutation-leaves-store-and-file-untouched
  (let [p (project)
        before-head (head p) before-file (slurp (file p "roadmap.md"))
        untouched? #(and (= before-head (head p)) (= before-file (slurp (file p "roadmap.md"))))]
    (doseq [[why e exit] [["a required field removed" (fields p "unset" "roadmap" "title") 3]
                          ["a value that does not read back" (fields p "set" "roadmap" "tasks" "[\"one\\n- two\"]") 3]
                          ["a wrong type" (fields p "set" "roadmap" "tasks" "{a: 1}") 3]
                          ["an actor without the capability" (fields p "set" "roadmap" "status" "x" {:actor "human/intruder"}) 7]]]
      (is (false? (:ok e)) why)
      (is (= exit (:exit e)) (str why ": " (get-in e [:error :type])))
      (is (untouched?) why))
    (is (= "validate/missing" (get-in (fields p "unset" "roadmap" "title") [:error :type])))
    (is (= "fields/not-roundtrip" (get-in (fields p "set" "roadmap" "tasks" "[\"one\\n- two\"]") [:error :type])))
    (is (= "draft" (value p "roadmap" "status")))))

(deftest a-mutation-names-operation-address-and-field
  (let [p (project)
        set-status (:data (fields p "update" "docs/roadmap/status" "accepted"))
        append (:data (fields p "append" "roadmap" "tasks" "Third"))
        unset (:data (fields p "remove" "docs/roadmap/tasks/0"))]
    (is (= {:operation "fields set" :address "RoadmapDoc:roadmap" :field "status" :before "draft" :after "accepted"}
           (select-keys set-status [:operation :address :field :before :after])))
    (is (= {:operation "fields append" :address "RoadmapDoc:roadmap" :field "tasks"
            :before ["Ship CLI" "Write docs"] :after ["Ship CLI" "Write docs" "Third"]}
           (select-keys append [:operation :address :field :before :after])))
    (is (= "fields unset" (:operation unset)) "v1's `remove` is named by the kimun name")
    (is (= "tasks" (:field unset)))
    (is (true? (:ok (run p "stores" "verify"))))))

(deftest a-mutation-renders-with-the-composition-context
  ;; A composed field (`⸢render•summary⸥`) is expanded from the refs of the payload, resolved
  ;; against the project root and never the working directory (kimun.cli.compose/context).
  (let [base (str (ktu/temp-dir "kimun-fields-compose-"))
        root (str (fs/create-dirs (fs/path base "project")))
        elsewhere (str (fs/create-dirs (fs/path base "elsewhere")))
        task {:model/name "Task" :model/version 1 :base []
              :fields [{:name "title" :type :string :required true :description "title."} {:name "status" :type :string :required true :description "status."}]
              :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥"}
        board {:model/name "Board" :model/version 1 :base []
               :py/compositions {"summary" {"source_field" "tasks" "model" "sldb_compose_root_models:Task" "template" "- {title} [{status}]"}}
               :fields [{:name "title" :type :string :required true :description "title."} {:name "tasks" :type [:list :string] :required false :default [] :description "tasks."}]
               :template "# ⸢rev•title⸥\n\n## Tasks\n\n- ⸢rev,list•tasks⸥\n\n## Summary\n\n⸢render•summary⸥"}]
    (run root "stores" "init" "--name" "p")
    (doseq [[f d] [["task.edn" task] ["board.edn" board]]]
      (spit (file base f) (pr-str d))
      (is (true? (:ok (run root "models" "define" "-f" (file base f))))))
    (is (true? (:ok (run root "docs" "create" "Task" "task-a" "--set" "title=Ship the CLI" "--set" "status=active"))))
    (is (true? (:ok (run root "docs" "create" "Board" "board" "--set" "title=Board"))))
    (let [e (fields elsewhere "append" "board" "tasks" "task-a.md" {:store root})]
      (is (true? (:ok e)) (pr-str (:error e)))
      (is (= ["task-a.md"] (get-in e [:data :after])))
      (is (str/includes? (slurp (file root "board.md")) "- Ship the CLI [active]") "the summary is composed from the tracked task")
      (is (not (fs/exists? (file elsewhere "board.md")))))
    (is (true? (:ok (fields elsewhere "set" "board" "title" "Renamed" {:store root}))))
    (is (str/includes? (slurp (file root "board.md")) "- Ship the CLI [active]") "…and survives a mutation of another field")
    (is (str/starts-with? (slurp (file root "board.md")) "# Renamed"))))
