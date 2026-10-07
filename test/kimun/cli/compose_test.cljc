(ns kimun.cli.compose-test
  "A composed field resolves its refs against the project root on every write
   path, whatever the working directory of the caller: the port of sldb v1
   `tests/api/test_composition_ref_root.py` over `kimun.cli.compose/context`.
   Each test names in a comment the v1 function whose promise it carries."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.compose :as compose]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.surface.models.segments :as segments]
            [kimun.surface.models.store :as mstore]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))

(def task
  {:model/name "Task" :model/version 1 :base []
   :fields [{:name "title" :type :string :required true :description "Title."}
            {:name "status" :type :string :required true :description "Status."}]
   :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥"})

(def board
  {:model/name "Board" :model/version 1 :base []
   :py/compositions {"summary" {"source_field" "tasks" "model" "sldb_compose_root_models:Task"
                                "template" "- {title} [{status}]"}}
   :fields [{:name "title" :type :string :required true :description "Title."}
            {:name "tasks" :type [:list :string] :required false :default [] :description "Task refs, project-relative."}]
   :template "# ⸢rev•title⸥\n\n## Tasks\n\n- ⸢rev,list•tasks⸥\n\n## Summary\n\n⸢render•summary⸥"})

(def summary "- Ship the CLI [active]")

(defn- world
  "v1 `World`: a store with a Board whose composed summary reads a Task tracked
   beside it. `{:base :root :elsewhere}` — `:elsewhere` is a directory that is
   not the project root, from which any real caller may run."
  []
  (let [base (str (ktu/temp-dir "kimun-compose-"))
        root (str (fs/create-dirs (fs/path base "project")))
        elsewhere (str (fs/create-dirs (fs/path base "elsewhere")))]
    (is (true? (:ok (run root "stores" "init" "--name" "p"))))
    (doseq [[f d] [["task.edn" task] ["board.edn" board]]]
      (spit (str (fs/path base f)) (pr-str d))
      (is (true? (:ok (run root "models" "define" "-f" (str (fs/path base f)))))))
    (is (true? (:ok (run root "docs" "create" "Task" "task-a" "--set" "title=Ship the CLI" "--set" "status=active"))))
    {:base base :root root :elsewhere elsewhere}))

(defn- payload-file [{:keys [base]} name payload]
  (let [f (str (fs/path base name))] (spit f (json/generate-string payload)) f))

(defn- create-board
  "v1 `World.board`: creates `board.md` at the project root, running from `cwd`."
  [{:keys [root] :as w} cwd payload]
  (let [e (run cwd "docs" "create" "Board" "board" "--store" root "--path" (str (fs/path root "board.md"))
               "--data" (payload-file w "board.json" payload))]
    (is (true? (:ok e)) (pr-str e))
    (str (fs/path root "board.md"))))

;; v1 test_create_document_composes_against_the_project_root
(deftest create-composes-against-the-project-root
  (let [{:keys [elsewhere] :as w} (world)
        path (create-board w elsewhere {"title" "Board" "tasks" ["task-a.md"]})]
    (is (str/includes? (slurp path) summary))))

;; v1 test_save_document_payload_composes_against_the_project_root
(deftest update-payload-composes-against-the-project-root
  (let [{:keys [root elsewhere] :as w} (world)
        path (create-board w root {"title" "Board" "tasks" ["task-a.md"]})
        e (run elsewhere "docs" "update" "board" "--store" root
               "--data" (payload-file w "renamed.json" {"title" "Renamed" "tasks" ["task-a.md"]}))
        text (slurp path)]
    (is (true? (:ok e)) (pr-str e))
    (is (str/includes? text "# Renamed"))
    (is (str/includes? text summary))))

;; v1 test_document_segments_compose_against_the_project_root
(deftest document-segments-compose-against-the-project-root
  (let [{:keys [root] :as w} (world)
        path (create-board w root {"title" "Board" "tasks" ["task-a.md"]})
        ;; opened from outside the project: the process cwd is not the root either
        opened (cli-store/open! (cli-store/resolve-store {:store root} env (:elsewhere w)))
        d (:descriptor (mstore/model (:store opened) "Board"))
        s (segments/document-segments d (slurp path) (assoc (compose/context opened) :id "Board/board" :doc "board" :path "board.md"))]
    (is (str/includes? (:text s) summary))
    (is (true? (:canonical s)))))

(deftest context-resolves-refs-and-models
  (let [{:keys [root]} (world)
        {:keys [store path] :as opened} (cli-store/open! (cli-store/resolve-store {:store root} env root))
        {:keys [ref-text ref-model]} (compose/context store path)]
    (testing "a tracked document answers with its stored source, by path or by name, even when its file is gone"
      (fs/delete (fs/path root "task-a.md"))
      (is (str/includes? (ref-text "task-a.md") "# Ship the CLI"))
      (is (= (ref-text "task-a.md") (ref-text "./task-a.md") (ref-text "task-a"))))
    (testing "an untracked file under the root is read; nothing else resolves"
      (spit (str (fs/path root "loose.md")) "# Loose\n")
      (is (= "# Loose\n" (ref-text "loose.md")))
      (is (nil? (ref-text "absent.md")))
      (is (nil? (ref-text ""))))
    (testing "a ref that leaves the project root names no file"
      (let [outside (ktu/temp-file "kimun-outside-" ".md")]
        (spit (str outside) "# Outside\n")
        (is (nil? (ref-text (str outside))) "absolute path")
        (is (nil? (ref-text (str (fs/relativize (fs/canonicalize root) (fs/canonicalize outside))))) "../ path")))
    (testing "a model reference resolves by class name"
      (is (= "Task" (:model/name (ref-model "sldb_compose_root_models:Task"))))
      (is (= "Task" (:model/name (ref-model "Task"))))
      (is (nil? (ref-model "mod:Unknown"))))
    (is (= (keys (compose/context opened)) (keys (compose/context store path))))))
