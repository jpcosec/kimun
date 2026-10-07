(ns kimun.cli.noise-test
  "Structural noise through the CLI (v1 `tests/api/test_structural_noise.py`):
   `docs track`, `docs create` and `docs update` refuse a document carrying
   headings or frontmatter keys its model does not declare, leave the store
   untouched, and `docs show --ast` prints the document's SLDB-MD AST. The pure
   rules are in `kimun.surface.models.noise-test`."
  (:require [kimun.test-util :as ktu] [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [kimun.cli.main :as main]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- file [p & parts] (str (apply fs/path p parts)))

(def note
  (pr-str {:model/name "NoteDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Note title."}
                    {:name "body" :type :string :required true :description "Note body."}]
           :template "# ⸢rev•title⸥\n\n## Answer\n\n⸢rev•body⸥"}))

(def note-with-sections
  (pr-str {:model/name "NoteWithSections" :model/version 1 :base ["NoteDoc"]
           :fields [{:name "title" :type :string :required true :description "Note title."}
                    {:name "body" :type :string :required true :description "Note body."}
                    {:name "entidades" :type :string :default "" :description "Referenced atoms."}
                    {:name "medicion_1" :type :string :default "" :description "First verifiable criterion."}]
           :template "# ⸢rev•title⸥\n\n## Answer\n\n⸢rev•body⸥\n\n## Entidades\n\n⸢optrev•entidades⸥\n\n## Medicion 1\n\n⸢optrev•medicion_1⸥"}))

(def card
  (pr-str {:model/name "CardDoc" :model/version 1 :base []
           :fields [{:name "id" :type :string :required true :description "Id."}
                    {:name "title" :type :string :required true :description "Title."}]
           :template "---\nid: ⸢rev•id⸥\ntitle: ⸢rev•title⸥\n---\n\n# Card"}))

(def clean "# Alpha\n\n## Answer\n\nJust prose, no headings inside the body.\n")
(def noisy "# Alpha\n\n## Answer\n\nBody text\n\n## Entidades\n\n- one\n\n## Medicion 1\n\n- two\n")

(defn- project []
  (let [p (str (ktu/temp-dir "kimun-noise-"))]
    (run p "stores" "init" "--name" "p")
    (doseq [[f src] [["note.edn" note] ["sections.edn" note-with-sections] ["card.edn" card]]]
      (spit (file p f) src)
      (is (true? (:ok (run p "models" "define" "-f" f)))))
    p))

(defn- tracked [p] (mapv :name (:data (run p "docs" "list"))))

(deftest track-refuses-a-document-the-model-does-not-declare
  ;; v1: test_track_refuses_a_document_whose_headings_the_model_does_not_declare,
  ;;     test_the_cli_refusal_is_loud_and_exits_nonzero
  (let [p (project)
        _ (spit (file p "noisy.md") noisy)
        e (run p "docs" "track" "NoteDoc" "noisy.md")]
    (is (false? (:ok e)))
    (is (= "docs/structural-noise" (get-in e [:error :type])))
    (is (= 3 (:exit e)) "loud: a nonzero exit, not a warning")
    (let [msg (get-in e [:error :message])]
      (is (str/includes? msg "noisy"))
      (is (str/includes? msg "## Entidades"))
      (is (str/includes? msg "## Medicion 1"))
      (is (str/includes? msg "extends NoteDoc") "names the way out: a child model with optrev fields"))
    (is (= ["## Entidades" "## Medicion 1"] (get-in e [:error :data :undeclared])))
    (is (= ["Entidades" "Medicion 1"] (mapv :text (get-in e [:error :data :headings]))))
    (is (= "NoteDoc" (get-in e [:error :data :model])))
    (is (= "noisy" (get-in e [:error :data :document])))))

(deftest the-refusal-leaves-the-document-untracked
  ;; v1: test_the_refusal_leaves_the_document_untracked
  ;; (v1 observed it with `stores check`; kimun's observable is `docs list` and the head revision)
  (let [p (project)
        head (:revision (run p "stores" "info"))
        _ (spit (file p "noisy.md") noisy)]
    (is (false? (:ok (run p "docs" "track" "NoteDoc" "noisy.md"))))
    (is (= [] (tracked p)))
    (is (= noisy (slurp (file p "noisy.md"))) "the file is not rewritten")
    (is (= head (:revision (run p "stores" "info"))) "no revision was committed")
    (testing "--force tracks it anyway, as v1's `force=True`"
      (is (true? (:ok (run p "docs" "track" "NoteDoc" "noisy.md" "--force"))))
      (is (= ["noisy"] (tracked p))))))

(deftest a-child-model-declaring-the-sections-accepts-the-same-bytes
  ;; v1: test_a_child_model_declaring_the_sections_optrev_accepts_the_document
  (let [p (project)
        _ (spit (file p "noisy.md") noisy)
        e (run p "docs" "track" "NoteWithSections" "noisy.md")]
    (is (true? (:ok e)) (pr-str e))
    (is (= "Body text" (get-in e [:data :payload "body"])))
    (is (= "NoteWithSections" (get-in e [:data :model])))))

(deftest an-undeclared-frontmatter-key-is-refused-on-track
  ;; v1: test_undeclared_frontmatter_key_is_refused (through `docs track`, as v1's track_document_file does)
  (let [p (project)
        _ (spit (file p "ok.md") "---\nid: c1\ntitle: Card\n---\n\n# Card\n")
        _ (spit (file p "owned.md") "---\nowner: jp\nid: c1\ntitle: Card\n---\n\n# Card\n")]
    (is (true? (:ok (run p "docs" "track" "CardDoc" "ok.md"))))
    (let [e (run p "docs" "track" "CardDoc" "owned.md")]
      (is (= "docs/structural-noise" (get-in e [:error :type])))
      (is (str/includes? (get-in e [:error :message]) "owner"))
      (is (= ["owner"] (get-in e [:error :data :frontmatter])))
      (is (= [] (get-in e [:error :data :undeclared]))))
    (is (= ["ok"] (tracked p)))))

(deftest create-refuses-a-payload-that-renders-noise
  ;; v1: create_document.refuse_noise (v1 has no test of it; the promise: nothing is written)
  (let [p (project)
        _ (spit (file p "p.yaml") "title: T\nbody: |-\n  intro\n\n  ## Sneaky\n\n  more\n")
        e (run p "docs" "create" "NoteDoc" "n" "--data" "p.yaml")]
    (is (= "docs/structural-noise" (get-in e [:error :type])) (pr-str e))
    (is (= ["## Sneaky"] (get-in e [:error :data :undeclared])))
    (is (not (fs/exists? (file p "n.md"))) "no file written")
    (is (= [] (tracked p)))
    (is (true? (:ok (run p "docs" "create" "NoteDoc" "n" "--set" "title=T" "--set" "body=fine"))))))

(deftest update-refuses-a-text-that-introduces-noise
  ;; v1: none (v1's update does not check); kimun refuses so a tracked document cannot gain noise
  (let [p (project)]
    (spit (file p "a.md") clean)
    (is (true? (:ok (run p "docs" "track" "NoteDoc" "a.md"))))
    (let [before (:revision (run p "stores" "info"))
          _ (spit (file p "a.md") noisy)
          e (run p "docs" "update" "a")]
      (is (= "docs/structural-noise" (get-in e [:error :type])) (pr-str e))
      (is (= ["## Entidades" "## Medicion 1"] (get-in e [:error :data :undeclared])))
      (is (= before (:revision (run p "stores" "info"))) "nothing committed"))))

(deftest show-prints-the-ast
  ;; v1: none (v1 has no AST view). `--format ast` is rejected by kimun.cli.args/formats (not editable here);
  ;; `--ast` is the docs-group option that works today.
  (let [p (project)]
    (spit (file p "a.md") clean)
    (is (true? (:ok (run p "docs" "track" "NoteDoc" "a.md"))))
    (let [e (run p "docs" "show" "a" "--ast")
          ast (get-in e [:data :ast])]
      (is (true? (:ok e)) (pr-str e))
      (is (= "a" (get-in e [:data :name])))
      (is (= :document (:type ast)))
      (is (= [:heading :heading :paragraph] (mapv :type (:children ast))))
      (is (= "Alpha" (get-in ast [:children 0 :text])))
      (is (nil? (get-in e [:data :markdown])) "the AST replaces the payload view"))
    (is (some? (get-in (run p "docs" "show" "a") [:data :markdown])) "without --ast, show is unchanged")))
