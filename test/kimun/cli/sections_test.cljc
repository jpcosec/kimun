(ns kimun.cli.sections-test
  "The `sections` group end to end on temporary stores: `list` (v1 `sections
   show`), `find` and `fields`, called through `kimun.cli.commands.sections/dispatch`
   (the group is not registered in `main` yet; the store is set up through
   `main/run`). Each test names in a comment the v1 functions whose promise it carries."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.commands.sections :as cs]
            [kimun.cli.errors :as errors]
            [kimun.cli.main :as main]))

(def env {"USER" "tester"})

(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))

(def roadmap-model
  (pr-str {:model/name "RoadmapDoc" :model/version 1 :base [] :semantics {"type" ["documentation" "Readme"]}
           :fields [{:name "title" :type :string :required true :description "Document title."}
                    {:name "status" :type :string :required true :description "Workflow status."}
                    {:name "tasks" :type [:list :string] :required true :description "Task items."}
                    {:name "semantic_tags" :type [:list :string] :required true :description "Semantic tags."}]
           :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n\n## Tasks\n\n- ⸢rev,list•tasks⸥\n\n## Semantic Tags\n\n- ⸢rev,list•semantic_tags⸥\n"}))

(defn- project
  "A project with a store, the RoadmapDoc model and the document `roadmap`
   (v1 `_setup_store`)."
  []
  (let [p (str (ktu/temp-dir "kimun-sections-"))]
    (run p "stores" "init" "--name" "p")
    (spit (str (fs/path p "roadmap.edn")) roadmap-model)
    (is (true? (:ok (run p "models" "define" "-f" "roadmap.edn"))))
    (is (true? (:ok (run p "docs" "create" "RoadmapDoc" "roadmap" "--set" "title=Roadmap" "--set" "status=draft"
                         "--set" "tasks=[Ship CLI, Write docs]" "--set" "semantic_tags=[project.sldb.database]"))))
    p))

(defn- sections
  "The envelope of `sections SUB ARGS…` in project `p` with `opts` (as strings, as the CLI parses them)."
  ([p sub args] (sections p sub args {}))
  ([p sub args opts]
   (errors/wrap #(cs/dispatch sub args (merge {:format "json"} opts) {:env env :cwd p}))))

(defn- titles [e] (mapv :title (get-in e [:data :sections])))

(deftest list-shows-the-section-tree
  ;; v1: test_sections_show_command
  (let [p (project) e (sections p "list" ["roadmap"])
        by (into {} (map (juxt :title identity)) (get-in e [:data :sections]))]
    (is (true? (:ok e)) (pr-str e))
    (is (= ["Roadmap" "Tasks" "Semantic Tags"] (titles e)))
    (is (= "roadmap/tasks" (:path (by "Tasks"))))
    (is (= [2 "tasks" ["Roadmap" "Tasks"]] ((juxt :level :slug :breadcrumbs) (by "Tasks"))))
    (is (some #{"type.documentation.Readme"} (:about (by "Tasks"))))
    (is (every? #(and (:line_start %) (:line_end %)) (vals by)))
    (is (= {:document "roadmap" :model "RoadmapDoc" :path "roadmap.md"} (select-keys (:data e) [:document :model :path])))
    (testing "`show` is the v1 name of `list`"
      (is (= (:data e) (:data (sections p "show" ["roadmap"])))))))

(deftest list-failures
  ;; v1: `sections show` on an unknown document / a document with no headings (exit 1)
  (let [p (project)]
    (is (= ["docs/missing" 1] ((juxt #(get-in % [:error :type]) :exit) (sections p "list" ["ghost"]))))
    (is (= "cli/usage" (get-in (sections p "list" []) [:error :type])))
    (is (= 6 (:exit (sections p "list" []))))
    (is (= "cli/usage" (get-in (cs/dispatch "frob" [] {} {:env env :cwd p}) [:error :type])))))

(deftest find-matches-semantic-and-where
  ;; v1: test_sections_find_command
  (let [p (project)
        e (sections p "find" ["tasks"] {:in "semantic"})
        r (first (get-in e [:data :results]))]
    (is (true? (:ok e)) (pr-str e))
    (is (= "Tasks" (:title r)))
    (is (= {:kind "section" :doc "roadmap" :model "RoadmapDoc" :path "roadmap.md#roadmap/tasks" :breadcrumbs ["Roadmap" "Tasks"]}
           (select-keys r [:kind :doc :model :path :breadcrumbs])))
    (testing "--where over the breadcrumbs"
      (is (>= (count (get-in (sections p "find" [""] {:where "\"Roadmap\" in breadcrumbs"}) [:data :results])) 2)))
    (testing "physical search does not read the tags"
      (is (= "sections/empty" (get-in (sections p "find" ["Readme"] {:in "physical"}) [:error :type])))
      (is (= 1 (:exit (sections p "find" ["Readme"] {:in "physical"})))))
    (testing "regex and fuzzy arrive as strings"
      (is (= ["Tasks"] (mapv :title (get-in (sections p "find" ["^Tasks$"] {:in "physical" :regex "true"}) [:data :results]))))
      (is (= ["Tasks"] (mapv :title (get-in (sections p "find" ["taks"] {:in "physical" :fuzzy true}) [:data :results])))))
    (testing "--model keeps the documents of that model"
      (is (true? (:ok (sections p "find" [""] {:model "RoadmapDoc"}))))
      (is (= 1 (:exit (sections p "find" [""] {:model "Other"})))))))

(deftest find-rejects-bad-options
  ;; v1: argparse `choices` of --in; here every option arrives as a string.
  (let [p (project)]
    (is (= 6 (:exit (sections p "find" ["x"] {:in "sideways"}))))
    (is (= 6 (:exit (sections p "find" ["x"] {:regex "maybe"}))))
    (is (= "query/bad-where" (get-in (sections p "find" ["x"] {:where "\"x\" in"}) [:error :type])))))

(deftest fields-lists-what-a-section-owns
  ;; v1: test_sections_fields_command (the section is the path `roadmap` / `roadmap/tasks`;
  ;; v1 spelled it `docs/roadmap/<path>`)
  (let [p (project)
        paths #(mapv :field_path (get-in % [:data :fields]))
        top (sections p "fields" ["roadmap" "roadmap"])
        tasks (sections p "fields" ["roadmap" "roadmap/tasks"])]
    (is (= ["title" "status"] (paths top)))
    (is (= ["tasks"] (paths tasks)))
    (is (= [{:field_path "tasks" :value ["Ship CLI" "Write docs"] :owning_section "roadmap/tasks"}] (get-in tasks [:data :fields])))
    (testing "a slug is enough when it is unique"
      (is (= ["tasks"] (paths (sections p "fields" ["roadmap" "tasks"])))))))

(deftest fields-section-errors
  ;; kimun's (06 §5): unknown → sections/missing (1), ambiguous slug → sections/ambiguous (2)
  (let [p (project)
        md "# Same\n\n## Child\n\n# Same\n\n## Child\n"]
    (is (= ["sections/missing" 1] ((juxt #(get-in % [:error :type]) :exit) (sections p "fields" ["roadmap" "nope"]))))
    (is (= 6 (:exit (sections p "fields" ["roadmap"]))))
    (testing "a repeated slug"
      (spit (str (fs/path p "twice.edn")) (pr-str {:model/name "Twice" :model/version 1 :base []
                                                  :fields [{:name "body" :type :text :required true :description "b"}]
                                                  :template "⸢rev•body⸥\n"}))
      (spit (str (fs/path p "twice.md")) md)
      (is (true? (:ok (run p "models" "define" "-f" "twice.edn"))))
      (is (true? (:ok (run p "docs" "track" "Twice" "twice.md" "--force"))))
      (let [e (sections p "fields" ["twice" "child"])]
        (is (= ["sections/ambiguous" 2] ((juxt #(get-in % [:error :type]) :exit) e)))
        (is (= ["same/child" "same-2/child"] (get-in e [:error :data :paths])))))))

(deftest list-is-independent-of-other-store-state
  ;; v1: test_sections_show_identical_with_and_without_persisted_index. There is no
  ;; persisted section index to keep in step: the sections are read from the tracked
  ;; source, so what the test promises is that unrelated writes do not change them.
  (let [p (project)
        before (:data (sections p "list" ["roadmap"]))]
    (is (true? (:ok (run p "docs" "create" "RoadmapDoc" "other" "--set" "title=Other" "--set" "status=x"
                         "--set" "tasks=[a]" "--set" "semantic_tags=[z]"))))
    (is (true? (:ok (run p "stores" "check"))))
    (is (= before (:data (sections p "list" ["roadmap"]))))))
