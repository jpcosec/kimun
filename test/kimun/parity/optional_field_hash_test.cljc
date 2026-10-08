(ns kimun.parity.optional-field-hash-test
  "v1 `store/test_optional_field_hash_neutral.py`: adding an optional (`optrev`)
   field to a model leaves no trace in a document that does not use it. Driven
   through the CLI `render` / `extract` on draft models and through
   `models define` / `docs create|show` on a real store (the content hash of
   an existing document must not move)."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run (vec argv) env cwd)))
(defn- file [p n] (str (fs/path p n)))
(defn- tmp [] (str (ktu/temp-dir "kimun-optfield-")))

(defn- fld [n t req] {:name n :type t :required req :description (str n ".")})
(defn- model [n fields template]
  {:model/name n :model/version 1 :base [] :fields fields :template template})

(def note (model "Note" [(fld "title" :string true) (fld "body" :string true)]
                 "---\ntitle: ⸢rev•title⸥\n---\n\n# ⸢render•title⸥\n\n⸢rev•body⸥"))
(def note+ (model "NoteOpt" [(fld "title" :string true) (fld "body" :string true) (fld "summary_en" :string false)]
                  "---\ntitle: ⸢rev•title⸥\nsummary_en: ⸢optrev•summary_en⸥\n---\n\n# ⸢render•title⸥\n\n⸢rev•body⸥"))
(def shared (model "Shared" [(fld "title" :string true) (fld "suffix" :string false)]
                   "---\ntitle: ⸢rev•title⸥\n---\n\n⸢rev•title⸥ — ⸢optrev•suffix⸥\n"))
(def table (model "Cells" [(fld "title" :string true) (fld "a" :string true) (fld "b" :string false)]
                  "# ⸢rev•title⸥\n\n| a | b |\n| --- | --- |\n| ⸢rev•a⸥ | ⸢optrev•b⸥ |\n"))
(def plain (model "Plain" [(fld "title" :string true) (fld "body" :string true) (fld "summary_en" :string false)]
                  "# ⸢rev•title⸥\n\nsummary_en: ⸢optrev•summary_en⸥\n\n⸢rev•body⸥"))

(def without-field "---\ntitle: Hello\n---\n\n# Hello\n\nWorld body\n")
(def using-field "---\ntitle: Hello\nsummary_en: Bonjour\n---\n\n# Hello\n\nWorld body\n")

(defn- render
  "Markdown of PAYLOAD (a map; nil values kept as JSON null) under MODEL."
  [m payload]
  (let [p (tmp)]
    (spit (file p "m.edn") (pr-str m))
    (spit (file p "d.json") (json/generate-string payload))
    (let [e (run p "render" "m.edn" "--data" "d.json")]
      (is (true? (:ok e)) (pr-str (:error e)))
      (let [d (:data e)] (if (map? d) (or (:markdown d) (:rendered d) (:text d)) d)))))

(defn- extract [m markdown]
  (let [p (tmp)]
    (spit (file p "m.edn") (pr-str m))
    (spit (file p "doc.md") markdown)
    (let [e (run p "extract" "m.edn" "--input" "doc.md")]
      (is (true? (:ok e)) (pr-str (:error e)))
      (let [d (:data e)] (or (:payload d) d)))))

(def base-payload {"title" "Hello" "body" "World body"})

;; v1: store/test_optional_field_hash_neutral.py::test_model_extension_is_neutral_for_document_that_does_not_use_field
(deftest model-extension-is-neutral-for-a-document-that-does-not-use-the-field
  (let [extracted (extract note+ without-field)
        base (render note base-payload)]
    (is (= base (render note+ extracted)))
    (testing "and in a real store: defining the next version of the model with the optional field keeps the document's hash and file"
      (let [p (tmp)]
        (run p "stores" "init" "--name" "p")
        (spit (file p "n.edn") (pr-str note))
        (is (true? (:ok (run p "models" "define" "-f" "n.edn"))))
        (is (true? (:ok (run p "docs" "create" "Note" "hello" "--set" "title=Hello" "--set" "body=World body"))))
        (let [before (:data (run p "docs" "show" "hello"))
              md (slurp (file p "hello.md"))]
          (spit (file p "n2.edn") (pr-str (assoc note+ :model/name "Note" :model/version 2)))
          (is (true? (:ok (run p "models" "define" "-f" "n2.edn"))))
          (is (true? (:ok (run p "stores" "update"))))
          (let [after (:data (run p "docs" "show" "hello"))]
            (is (= md (slurp (file p "hello.md"))) "the file on disk does not mutate")
            (is (= (:source-hash before) (:source-hash after)))
            (is (= (:tree before) (:tree after)))
            (is (= (:payload before) (:payload after)) "the absent field adds no key")
            (is (= 2 (:model-version after)))
            (is (= :intact (:state after)))
            (is (true? (get-in (run p "stores" "check") [:data :documents :valid])))))))))

;; v1: store/test_optional_field_hash_neutral.py::test_document_using_the_field_extracts_the_value
(deftest a-document-using-the-field-extracts-the-value
  (is (= {"title" "Hello" "body" "World body" "summary_en" "Bonjour"} (extract note+ using-field))))

;; v1: store/test_optional_field_hash_neutral.py::test_model_extension_is_neutral_for_document_that_does_not_use_field
;; (the `models field-add` form; the `models define` form is `model-extension-is-neutral-...`)
;; A1 (2026-10-08): this was a known gap — the appended `## F` section swallowed
;; the body of existing documents. The document is now read under the contract it
;; was written with, and `stores update` re-renders the same payload under the new one.
(deftest field-add-of-an-optional-field-leaves-existing-documents-unchanged
  (let [p (tmp)]
    (run p "stores" "init" "--name" "p")
    (spit (file p "n.edn") (pr-str note))
    (is (true? (:ok (run p "models" "define" "-f" "n.edn"))))
    (is (true? (:ok (run p "docs" "create" "Note" "hello" "--set" "title=Hello" "--set" "body=World body"))))
    (is (true? (:ok (run p "models" "field-add" "Note" "--field" "summary_en" "--type" "string" "--description" "Optional summary."))))
    (testing "the document keeps the payload of the contract it was written with"
      (is (= base-payload (get-in (run p "docs" "show" "hello") [:data :payload]))))
    (testing "`stores update` re-renders it under the new contract with the body intact"
      (is (true? (:ok (run p "stores" "update"))))
      (is (= base-payload (get-in (run p "docs" "show" "hello") [:data :payload])))
      (is (= :intact (get-in (run p "docs" "show" "hello") [:data :state]))))))

;; v1: store/test_optional_field_hash_neutral.py::test_rendered_markdown_omits_absent_optional_line
(deftest rendered-markdown-omits-the-absent-optional-line
  (is (not (str/includes? (render note+ (assoc base-payload "summary_en" nil)) "summary_en"))))

;; v1: store/test_optional_field_hash_neutral.py::test_render_is_byte_identical_to_model_without_the_field
(deftest render-is-byte-identical-to-the-model-without-the-field
  (is (= (render note base-payload) (render note+ (assoc base-payload "summary_en" nil)))))

;; v1: store/test_optional_field_hash_neutral.py::test_roundtrip_exact_with_optional_field_model
(deftest roundtrip-with-an-optional-field-model-drops-the-absent-key
  (is (= base-payload (extract note+ (render note+ (assoc base-payload "summary_en" nil))))))

;; v1: store/test_optional_field_hash_neutral.py::test_extract_omits_absent_optional_field
(deftest extract-omits-the-absent-optional-field
  (is (= base-payload (extract note+ without-field))))

;; v1: store/test_optional_field_hash_neutral.py::test_roundtrip_exact_with_optional_field_absent_from_disk
(deftest roundtrip-with-the-field-absent-from-disk
  (let [extracted (extract note+ without-field)]
    (is (= extracted (extract note+ (render note+ extracted))))))

;; v1: store/test_optional_field_hash_neutral.py::test_shared_line_optrev_replaced_with_empty_string_keeps_line
(deftest a-shared-line-keeps-its-text-when-the-optional-is-absent
  (let [r (render shared {"title" "Hello" "suffix" nil})]
    (is (str/includes? r "Hello —"))
    (is (not (str/includes? r "suffix")))))

;; v1: store/test_optional_field_hash_neutral.py::test_table_row_with_optrev_cell_keeps_all_cells
(deftest a-table-row-keeps-all-its-cells
  (let [r (render table {"title" "Hello" "a" "A1" "b" nil})]
    (is (str/includes? r "| A1 |  |"))
    (is (not (str/includes? r "| ⸢")))))

;; v1: store/test_optional_field_hash_neutral.py::test_render_absent_optrev_leaves_no_trace_in_frontmatter
(deftest an-absent-optional-leaves-no-trace-in-frontmatter
  (let [r (render note+ (assoc base-payload "summary_en" nil))]
    (is (not (str/includes? r "summary_en")))
    (is (= (extract note+ r) (extract note+ without-field)))))

;; v1: store/test_optional_field_hash_neutral.py::test_render_absent_optrev_leaves_no_trace_on_plain_line
(deftest an-absent-optional-leaves-no-trace-on-a-plain-line
  (let [r (render plain (assoc base-payload "summary_en" nil))]
    (is (not (str/includes? r "summary_en")))
    (is (and (str/includes? r "Hello") (str/includes? r "World body")))))

;; v1: store/test_optional_field_hash_neutral.py::test_render_optrev_with_value_is_unchanged
(deftest a-present-optional-renders-and-extracts-symmetrically
  (let [payload (assoc base-payload "summary_en" "Bonjour")
        r (render note+ payload)]
    (is (str/includes? r "summary_en: Bonjour"))
    (is (= payload (extract note+ r)))))
