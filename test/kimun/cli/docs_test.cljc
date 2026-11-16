(ns kimun.cli.docs-test
  "The `docs` group end to end on temporary stores: ingest a Markdown file,
   list it, render it back canonically, update it from an outside edit, and
   the failures (missing file, missing document, duplicate name, usage)."
  (:require [kimun.test-util :as ktu] [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [kimun.cli.main :as main]
            [kimun.cli.out :as out]
            [kimun.host.default :as host]
            [kimun.surface.markdown.ast :as ast]
            [kimun.surface.markdown.render :as render]))

(def env {"USER" "tester"})

(defn- tmp [] (str (ktu/temp-dir "kimun-docs-")))

(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))

(def doc-1 "#  Título  \n\nUn párrafo\ncon soft break.\n\n* a\n* b\n\n```clj\n(+ 1 2)\n```\n")
(def doc-2 "# Título\n\nOtro párrafo.\n\n- solo uno\n")

(defn- canonical [text] (render/render host/host (ast/parse host/host text)))

(defn- project-with-doc
  "A project with an initialised store and `doc.md` holding `text`."
  [text]
  (let [proj (tmp)]
    (run proj "stores" "init" "--name" "p")
    (spit (str (fs/path proj "doc.md")) text)
    proj))

(deftest ingest-list-render-round-trip
  (let [proj (project-with-doc doc-1)
        e (run proj "docs" "ingest" "doc.md")]
    (is (true? (:ok e)) (pr-str e))
    (is (= "docs ingest" (:command e)))
    (is (= "doc" (get-in e [:data :name])) "name defaults to the file name without extension")
    (is (string? (get-in e [:data :tree])))
    (is (= 4 (get-in e [:data :blocks])) "heading, paragraph, list, code")
    (is (string? (:revision e)) "the envelope revision is the new head")
    (testing "the store now holds one tree and one revision"
      (let [chk (run proj "stores" "check")]
        (is (= 1 (get-in chk [:data :trees])))
        (is (= 1 (get-in chk [:data :revisions])))
        (is (= (:revision e) (get-in chk [:data :head])))))
    (testing "list names it"
      (let [l (run proj "docs" "list")]
        (is (true? (:ok l)) (pr-str l))
        ;; K3: a document without a model is registered under the generic class
        (is (= [{:name "doc" :tree (get-in e [:data :tree]) :root (get-in l [:data 0 :root]) :class "generic/md"}] (:data l)))))
    (testing "render by name and by tree id is the canonical Markdown"
      (let [by-name (run proj "docs" "render" "doc")
            by-id (run proj "docs" "render" (get-in e [:data :tree]))]
        (is (true? (:ok by-name)) (pr-str by-name))
        (is (= (canonical doc-1) (:data by-name)))
        (is (= (:data by-name) (:data by-id)))
        (is (= "# Título\n\nUn párrafo con soft break.\n\n- a\n- b\n\n```clj\n(+ 1 2)\n```\n" (:data by-name)))
        (is (= (:data by-name) (out/emit by-name "text")) "--format text prints the document itself")))
    (testing "verify still passes after the ingest"
      (is (true? (:ok (run proj "stores" "verify")))))))

(deftest ingest-honours-name-and-rejects-duplicates
  (let [proj (project-with-doc doc-1)
        e (run proj "docs" "ingest" "doc.md" "--name" "guide")
        dup (run proj "docs" "ingest" "doc.md" "--name" "guide")]
    (is (= "guide" (get-in e [:data :name])))
    (is (false? (:ok dup)))
    (is (= 3 (:exit dup)))
    (is (= "docs/exists" (get-in dup [:error :type])))
    (is (= "guide" (get-in dup [:error :data :document])))))

(deftest update-reingests-an-outside-edit
  (let [proj (project-with-doc doc-1)
        _ (run proj "docs" "ingest" "doc.md")
        _ (spit (str (fs/path proj "doc.md")) doc-2)
        e (run proj "docs" "update" "doc.md" "--name" "doc")]
    (is (true? (:ok e)) (pr-str e))
    (is (= 3 (get-in e [:data :blocks])))
    (is (= (canonical doc-2) (:data (run proj "docs" "render" "doc"))))
    (is (= 2 (get-in (run proj "stores" "check") [:data :revisions])))
    (testing "the second positional token also names the document"
      (is (true? (:ok (run proj "docs" "update" "doc.md" "doc")))))
    (testing "updating an unknown document is docs/missing (exit 1)"
      (let [m (run proj "docs" "update" "doc.md" "--name" "nope")]
        (is (= 1 (:exit m)))
        (is (= "docs/missing" (get-in m [:error :type])))))))

(deftest failures-are-typed
  (let [proj (project-with-doc doc-1)]
    (testing "no such file"
      (let [e (run proj "docs" "ingest" "missing.md")]
        (is (= 1 (:exit e)))
        (is (= "docs/no-file" (get-in e [:error :type])))
        (is (str/includes? (get-in e [:error :message]) "missing.md"))))
    (testing "render of an unknown document"
      (let [e (run proj "docs" "render" "ghost")]
        (is (= 1 (:exit e)))
        (is (= "docs/missing" (get-in e [:error :type])))
        (is (= "ghost" (get-in e [:error :data :document])))))
    (testing "missing FILE argument"
      (let [e (run proj "docs" "ingest")]
        (is (= 6 (:exit e)))
        (is (= "cli/usage" (get-in e [:error :type])))))
    (testing "unknown subcommand lists the known ones"
      (let [e (run proj "docs" "nope")]
        (is (= 6 (:exit e)))
        (is (= ["anchors" "create" "delete" "ingest" "list" "mark" "marks" "reanchor" "render" "show" "track" "unmark" "untrack" "update"] (get-in e [:error :data :known])))))
    (testing "no store"
      (let [e (run (tmp) "docs" "list")]
        (is (= 5 (:exit e)))
        (is (= "cli/no-store" (get-in e [:error :type])))))))
