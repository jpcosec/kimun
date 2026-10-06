(ns kimun.parity.standalone-test
  "Ports of sldb v1 tests/test_standalone.py: the engine and the direct
   commands (`extract`, `render`, `validate`) used with no store. Tests of the
   payload / rendered Markdown alone are covered by the differential corpus
   (parity-oracle.edn); what the corpus does not compare — exit codes, output
   files, the CLI envelope, validation results, recipe errors — is ported here."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [kimun.cli.main :as main]
            [kimun.cli.out :as out]
            [kimun.surface.models.extract :as extract]
            [kimun.surface.models.recipe :as recipe]
            [kimun.surface.models.render :as render]
            [kimun.surface.models.validate :as validate]))

(def simple-doc
  "v1's SimpleDoc: a heading, a YAML block and a bullet list."
  {:model/name "SimpleDoc" :model/version 1 :base []
   :fields [{:name "title" :type :string :required true :description "Document title heading."}
            {:name "meta" :type :yaml :required true :description "Metadata YAML block."}
            {:name "items" :type :yaml :required true :description "Bullet list items."}]
   :template "# ⸢rev•title⸥\n\n## Metadata\n```yaml\n⸢rev,dict•meta⸥\n```\n\n* ⸢rev,list•items⸥"})

(def input-md "# My Standalone Doc\n\n## Metadata\n```yaml\nversion: 1.0.0\nstatus: stable\n```\n\n* First\n* Second")

(def data-yaml "title: My Standalone Doc\nmeta:\n  version: 1.0.0\n  status: stable\nitems:\n  - First\n  - Second\n")

(def expected {"title" "My Standalone Doc" "meta" {"version" "1.0.0" "status" "stable"} "items" ["First" "Second"]})

(defn- workdir
  "A temp dir holding the model, the input document and the data file."
  []
  (let [d (str (fs/create-temp-dir {:prefix "kimun-standalone-"}))]
    (spit (str (fs/path d "model.edn")) (pr-str simple-doc))
    (spit (str (fs/path d "input.md")) input-md)
    (spit (str (fs/path d "data.yaml")) data-yaml)
    d))

(defn- run [cwd & argv] (main/run argv {"USER" "tester"} cwd))
(defn- env-of [cwd & argv] (:envelope (apply run cwd argv)))

;; v1: test_standalone.py::test_sldb_standalone_roundtrip
(deftest standalone-roundtrip-with-no-store
  (let [payload (extract/extract simple-doc input-md)
        rendered (render/render simple-doc payload)]
    (is (= expected payload))
    (is (every? #(str/includes? rendered %) ["# My Standalone Doc" "version: 1.0.0" "* First" "* Second"]))
    (is (= payload (extract/extract simple-doc rendered)) "extract(render(x)) is x")))

;; v1: test_standalone.py::test_cli_extract_json
(deftest cli-extract-writes-a-json-file
  (let [d (workdir)
        e (env-of d "extract" "--model" "model.edn" "--input" "input.md" "-o" "output.json")]
    (is (true? (:ok e)) (pr-str e))
    (is (zero? (out/exit-code e)))
    (is (= (str (fs/path d "output.json")) (get-in e [:data :output])))
    (is (= expected (json/parse-string (slurp (str (fs/path d "output.json"))))))))

;; v1: test_standalone.py::test_cli_render_markdown
(deftest cli-render-writes-a-markdown-file
  (let [d (workdir)
        e (env-of d "render" "--model" "model.edn" "--data" "data.yaml" "-o" "rendered.md")
        md (slurp (str (fs/path d "rendered.md")))]
    (is (true? (:ok e)) (pr-str e))
    (is (zero? (out/exit-code e)))
    (is (every? #(str/includes? md %) ["# My Standalone Doc" "version: 1.0.0" "* First" "* Second"]))))

;; v1: test_standalone.py::test_cli_extract_stdout_with_explicit_format
(deftest cli-extract-to-stdout-as-json
  (let [d (workdir)
        {:keys [envelope fmt]} (run d "extract" "--model" "model.edn" "--input" "input.md" "-o" "-" "--format" "json")
        printed (json/parse-string (out/emit envelope fmt))]
    (is (zero? (out/exit-code envelope)))
    (is (= "json" fmt))
    (is (= "My Standalone Doc" (get-in printed ["data" "payload" "title"])))
    (is (nil? (get-in printed ["data" "output"])) "`-o -` writes no file")))

;; v1: test_standalone.py::test_cli_validate_input_roundtrip
(deftest cli-validate-an-input-document
  (let [e (env-of (workdir) "validate" "--model" "model.edn" "--input" "input.md")]
    (is (zero? (out/exit-code e)) (pr-str e))
    (is (= {:ok true :model "SimpleDoc" :mode :render-validity :errors []} (:data e)))))

;; v1: test_standalone.py::test_cli_validate_data_roundtrip_json
(deftest cli-validate-a-payload-as-json
  (let [{:keys [envelope fmt]} (run (workdir) "validate" "--model" "model.edn" "--data" "data.yaml" "--format" "json")
        printed (json/parse-string (out/emit envelope fmt))]
    (is (zero? (out/exit-code envelope)))
    (is (true? (get-in printed ["data" "ok"])))
    (is (= "SimpleDoc" (get-in printed ["data" "model"])))))

(def pandoc-doc
  {:model/name "PandocCVDoc" :model/version 1 :base []
   :fields [{:name "title" :type :string :required true :description "CV title shown in the H1 heading."}
            {:name "body" :type :text :required true :description "Body after the H1."}]
   :template "# ⸢rev•title⸥\n\n⸢rev•body⸥"})

;; v1: test_standalone.py::test_title_body_model_roundtrips_pandoc_fenced_divs
(deftest pandoc-fenced-divs-survive-and-validate
  (let [md (str "# Curriculum Vitae\n\nThis CV keeps Pandoc fenced div blocks intact.\n\n"
                "::: {.job role=\"Staff Engineer\" org=\"Hum Labs\" dates=\"2021-2024\"}\n## Work\n\nBuilt the document pipeline.\n\n"
                "- Shipped structured review surfaces\n- Kept Markdown editable\n:::\n\n"
                "::: {.education degree=\"MSc\" org=\"UNLP\" dates=\"2018-2020\"}\n## Education\n\nStudied computational linguistics.\n:::")
        payload (extract/extract pandoc-doc md)
        rendered (render/render pandoc-doc payload)]
    (is (= "Curriculum Vitae" (get payload "title")))
    (is (str/includes? (get payload "body") "::: {.job role=\"Staff Engineer\""))
    (is (str/includes? (get payload "body") "::: {.education degree=\"MSc\""))
    (is (str/includes? rendered "::: {.job role=\"Staff Engineer\""))
    (is (true? (:ok (validate/validate pandoc-doc {:markdown md}))))))

(def section-markers-doc
  "v1's SectionMarkersDoc: a required and an optional section body."
  {:model/name "SectionMarkersDoc" :model/version 1 :base []
   :fields [{:name "title" :type :string :required true :description "Document title heading."}
            {:name "required" :type :text :required true :description "Required section body."}
            {:name "optional" :type :text :required false :description "Optional section body."}]
   :template "# ⸢rev•title⸥\n\n## Required\n\n⸢rev•required⸥\n\n## Optional\n\n⸢optrev•optional⸥"})

;; v1: test_standalone.py::test_render_validity_covers_optrev_when_present
(deftest render-validity-compares-optrev-fields
  (is (contains? (recipe/data-fields (:template section-markers-doc)) "optional"))
  (let [md (render/render section-markers-doc {"title" "T" "required" "r" "optional" "L1\nL2"})
        r (validate/validate section-markers-doc {:markdown md})]
    (is (true? (:ok r)) (pr-str (:errors r)))
    (is (= "L1\nL2" (get (:extracted r) "optional")))))

;; v1: test_standalone.py::test_render_validity_flags_missing_optrev_content
(deftest render-validity-flags-an-optrev-lost-in-the-roundtrip
  ;; an inline marker cannot carry a line break, so the optional value does not survive render -> extract
  (let [inline (assoc section-markers-doc :template "# ⸢rev•title⸥\n\nRequired: ⸢rev•required⸥\n\nOptional: ⸢optrev•optional⸥")
        lost (validate/validate inline {:data {"title" "T" "required" "r" "optional" "L1\nL2"}})
        absent (validate/validate inline {:data {"title" "T" "required" "r"}})]
    (is (false? (:ok lost)))
    (is (= ["optional"] (map :field (:errors lost))))
    (is (true? (:ok absent)) "an optional field absent on both sides is valid")))

;; v1: test_standalone.py::test_lone_data_marker_in_unsupported_block_is_rejected
(deftest lone-data-marker-in-a-blockquote-is-rejected
  (let [e (try (recipe/recipes "## T\n\n> ⸢rev•quoted⸥\n") nil (catch clojure.lang.ExceptionInfo e e))]
    (is (= :models/invalid (:type (ex-data e))))
    (is (re-find #"blockquote" (ex-message e)))))
