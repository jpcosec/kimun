(ns kimun.surface.models.noise-test
  "Structural noise (v1 `tests/api/test_structural_noise.py`), the pure rules:
   which headings and frontmatter keys a document carries that its model's
   template does not declare. The CLI refusals live in `kimun.cli.noise-test`."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.surface.models.extract :as extract]
            [kimun.surface.models.noise :as noise]))

(def note
  {:model/name "NoteDoc" :template "# ⸢rev•title⸥\n\n## Answer\n\n⸢rev•body⸥"
   :fields [{:name "title" :type :string :required true :description "title."} {:name "body" :type :string :required true :description "body."}]})

(def note-with-sections
  {:model/name "NoteWithSections"
   :template "# ⸢rev•title⸥\n\n## Answer\n\n⸢rev•body⸥\n\n## Entidades\n\n⸢optrev•entidades⸥\n\n## Medicion 1\n\n⸢optrev•medicion_1⸥"
   :fields [{:name "title" :type :string :required true :description "title."} {:name "body" :type :string :required true :description "body."}
            {:name "entidades" :type :string :default "" :description "entidades."} {:name "medicion_1" :type :string :default "" :description "medicion_1."}]})

(def clean "# Alpha\n\n## Answer\n\nJust prose, no headings inside the body.\n")
(def noisy "# Alpha\n\n## Answer\n\nBody text\n\n## Entidades\n\n- one\n\n## Medicion 1\n\n- two\n")

(deftest names-every-heading-the-template-does-not-declare
  ;; v1: test_track_refuses_a_document_whose_headings_the_model_does_not_declare (the detection half; the refusal is in kimun.cli.noise-test)
  (let [r (noise/structural-noise note noisy)]
    (is (noise/noisy? r))
    (is (= ["## Entidades" "## Medicion 1"] (mapv :raw (:headings r))))
    (is (= [{:text "Entidades" :level 2 :line 7} {:text "Medicion 1" :level 2 :line 11}]
           (mapv #(select-keys % [:text :level :line]) (:headings r))))
    (is (= [] (:frontmatter r)))
    (let [line (noise/describe r)]
      (is (not (re-find #"\n" line)) "one line")
      (is (and (str/includes? line "## Entidades") (str/includes? line "## Medicion 1"))))))

(deftest a-document-that-fits-its-model-is-clean
  ;; v1: test_a_document_that_fits_its_model_still_tracks (the pure half)
  (let [r (noise/structural-noise note clean)]
    (is (= {:headings [] :frontmatter []} r))
    (is (not (noise/noisy? r)))))

(deftest a-child-model-declaring-the-sections-accepts-the-document
  ;; v1: test_a_child_model_declaring_the_sections_optrev_accepts_the_document
  (is (= {:headings [] :frontmatter []} (noise/structural-noise note-with-sections noisy)))
  (is (= {:headings [] :frontmatter []} (noise/structural-noise note-with-sections noisy {:related [note]}))
      "related descriptors are accepted; v1's child carries the whole template, so they decide nothing"))

(deftest the-noise-is-swallowed-into-a-field-value-under-the-parent
  ;; v1: test_the_noise_is_swallowed_into_a_field_value_under_the_parent
  (is (str/includes? (get (extract/extract note noisy) "body") "## Entidades"))
  (is (= "Body text" (get (extract/extract note-with-sections noisy) "body"))))

(deftest undeclared-frontmatter-key-is-named
  ;; v1: test_undeclared_frontmatter_key_is_refused (the detection half)
  (let [card {:model/name "CardDoc" :template "---\nid: ⸢rev•id⸥\ntitle: ⸢rev•title⸥\n---\n\n# ⸢rev•title⸥"
              :fields [{:name "id" :type :string :required true :description "id."} {:name "title" :type :string :required true :description "title."}]}
        clean "---\nid: c1\ntitle: Card\n---\n\n# Card"
        owner (str/replace-first clean "---\nid:" "---\nowner: jp\nid:")]
    (is (= [] (:frontmatter (noise/structural-noise card clean))))
    (let [r (noise/structural-noise card owner)]
      (is (= ["owner"] (:frontmatter r)))
      (is (noise/noisy? r))
      (is (str/includes? (noise/describe r) "owner")))
    (testing "a template without frontmatter declares no key; broken YAML is not noise"
      (is (= ["id"] (:frontmatter (noise/structural-noise note "---\nid: 1\n---\n# A\n\n## Answer\n\nx"))))
      (is (= [] (:frontmatter (noise/structural-noise card "---\nid: [unclosed\n---\n\n# Card")))))))

(deftest heading-scanning-follows-v1
  ;; v1: heading_scan.scan_headings / structural_noise.declared_headings (rules shared by every test above)
  (testing "a marker heading opens its whole level; a literal heading is exact on level and text"
    (is (= ["### Deep"] (mapv :raw (:headings (noise/structural-noise note "# Anything\n\n## Answer\n\n### Deep\n")))))
    (is (= ["## answer"] (mapv :raw (:headings (noise/structural-noise note "# T\n\n## answer\n"))))))
  (testing "a heading inside a code fence, a list or a quote is not a heading"
    (is (= [] (:headings (noise/structural-noise note "# T\n\n## Answer\n\n```\n## Fenced\n```\n\n- ## in a list\n\n> ## quoted\n")))))
  (testing "setext headings count, and an empty heading is skipped"
    (is (= ["## Setext"] (mapv :raw (:headings (noise/structural-noise note "# T\n\nSetext\n------\n\n##\n"))))))
  (testing "a clean result describes itself"
    (is (= "clean" (noise/describe {:headings [] :frontmatter []})))))
