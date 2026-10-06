(ns kimun.surface.sections-test
  "The pure section surface: the section extraction cases of sldb v1
   (`store/test_semantic_rebuild.py`, `store/test_section_rebuild.py`, both
   served by `kimun.surface.models.segments/sections`), the section index with
   breadcrumbs and `about`, the lookup of one section, and the search that
   `find --type section` will call (`find-sections`). Each test names in a
   comment the v1 functions whose promise it carries."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.surface.models.segments :as segments]
            [kimun.surface.sections :as sections]))

(defn- raised [f] (try (f) nil (catch #?(:clj Exception :cljs :default) e (:type (ex-data e)))))

(defn- paths [md] (mapv :path (segments/sections md)))

(deftest extraction-yields-no-sections-without-a-titled-heading
  ;; v1 (test_semantic_rebuild.py::TestExtractSectionsEdgeCases):
  ;;   test_plain_paragraph_no_headings, test_empty_string,
  ;;   test_only_thematic_breaks_and_code_blocks, test_malformed_heading_no_title
  (is (= [] (segments/sections "This is a plain paragraph with no headings.\n\nJust text.")))
  (is (= [] (segments/sections "")))
  (is (= [] (segments/sections "---\n\n```python\nprint('hello')\n```\n\n***")))
  (is (= [] (segments/sections "# \n\nContent after empty heading."))))

(deftest extraction-reads-title-level-slug-path-and-line
  ;; v1: test_heading_with_no_map_attribute, test_single_heading
  (let [[s :as all] (segments/sections "# Hello\n\nWorld")]
    (is (= 1 (count all)))
    (is (= "Hello" (:title s)))
    (is (= 1 (:level s)))
    (is (= "hello" (:slug s)))
    (is (= "hello" (:path s)))
    (is (= 1 (:line_start s)) "a heading always has a line")))

(deftest nested-headings-join-their-slugs
  ;; v1: test_multiple_headings_with_hierarchy
  (is (= ["a" "a/b" "a/b/c" "d"] (paths "# A\n\n## B\n\n### C\n\n# D"))))

(deftest repeated-headings-get-numbered-paths
  ;; v1 (test_section_rebuild.py):
  ;;   test_persisted_section_rebuild_matches_ir_paths_for_repeated_headings.
  ;; v1 had two extractors that had to agree; here there is one, so the promise
  ;; is the paths themselves and that the index built on top keeps them.
  (let [md "# Same\nfirst\n## Child\nbody\n# Same\nsecond\n"]
    (is (= ["same" "same/child" "same-2"] (paths md)))
    (is (= ["same" "same/child" "same-2"] (mapv :path (sections/entries md []))))))

(def ^:private roadmap
  "# Roadmap\n\nStatus: draft\n\n## Tasks\n\n- Ship CLI\n\n## Semantic Tags\n\n- x\n")

(deftest entries-carry-breadcrumbs-about-and-lines
  ;; v1 (test_cli_v2.py): test_sections_show_command (titles and entry keys),
  ;; section_record_to_entry / _about_terms.
  (let [es (sections/entries roadmap ["type.documentation.Readme"])
        by (into {} (map (juxt :title identity)) es)]
    (is (= ["Roadmap" "Tasks" "Semantic Tags"] (mapv :title es)))
    (is (= ["Roadmap" "Semantic Tags"] (:breadcrumbs (by "Semantic Tags"))) "an ancestor, then itself")
    (is (= ["Roadmap" "roadmap" "Semantic Tags" "semantic tags" "type.documentation.Readme"] (:about (by "Semantic Tags"))))
    (is (= "roadmap/semantic-tags" (:path (by "Semantic Tags"))))
    (is (= [1 2 2] (mapv :level es)))
    (is (= [1 5 9] (mapv :line_start es)))
    (is (= [11 8 11] (mapv :line_end es)))
    (is (= #{:path :title :breadcrumbs :about :slug :semantic_tags :level :line_start :line_end} (set (keys (first es)))))))

(deftest a-sibling-closes-the-previous-branch-in-the-breadcrumbs
  ;; v1 (section_rebuild._process_doc_sections): the stack pops by level.
  (let [es (sections/entries "# A\n\n## B\n\n### C\n\n## D\n\n# E" [])]
    (is (= [["A"] ["A" "B"] ["A" "B" "C"] ["A" "D"] ["E"]] (mapv :breadcrumbs es)))))

(deftest resolve-section-by-path-slug-and-errors
  ;; v1 (test_sections_fields_command: section by path); the slug lookup and its
  ;; two error types are kimun's (06 §5).
  (let [es (sections/entries "# Same\n\n## Child\n\n# Same\n\n## Child\n" [])]
    (is (= "same/child" (:path (sections/resolve-section es "same/child"))))
    (is (= "same-2/child" (:path (sections/resolve-section es "same-2/child"))))
    (is (= "same-2" (:path (sections/resolve-section es "same-2"))))
    (is (= :sections/missing (raised #(sections/resolve-section es "nope"))))
    (is (= :sections/ambiguous (raised #(sections/resolve-section es "child"))))
    (is (= ["same/child" "same-2/child"]
           (:paths (try (sections/resolve-section es "child") (catch #?(:clj Exception :cljs :default) e (ex-data e))))))))

(def ^:private doc {:store "p" :name "roadmap" :model "RoadmapDoc" :path "roadmap.md" :tags ["type.documentation.Readme"]})
(def ^:private recs (sections/records doc (sections/entries roadmap ["type.documentation.Readme"])))
(defn- titles [found] (mapv :title found))

(deftest find-sections-searches-physical-and-semantic
  ;; v1: test_sections_find_command (first half: `tasks --in semantic`),
  ;; and the physical/semantic split of find_supports_physical_and_semantic
  ;; restricted to sections.
  (is (= ["Tasks"] (titles (sections/find-sections recs "tasks" {:in "semantic"}))))
  (is (= ["Tasks"] (titles (sections/find-sections recs "Tasks" {:in "physical"}))))
  (is (= ["Roadmap" "Tasks" "Semantic Tags"] (titles (sections/find-sections recs "" {}))) "an empty term keeps all")
  (is (= ["Roadmap" "Tasks" "Semantic Tags"] (titles (sections/find-sections recs "Readme" {:in "semantic"}))) "the document tags")
  (is (= [] (sections/find-sections recs "Readme" {:in "physical"})))
  (is (= 3 (count (sections/find-sections recs "^roadmap\\.md" {:in "physical" :regex true}))) "the file name is physical text of every section")
  (is (= ["Tasks"] (titles (sections/find-sections recs "^tasks$" {:in "physical" :regex true}))))
  (is (= ["Tasks"] (titles (sections/find-sections recs "taks" {:in "physical" :fuzzy true})))))

(deftest find-sections-where-predicates
  ;; v1: test_find_section_where_predicates (the section half of `find`: the
  ;; predicates over about, breadcrumbs, semantic_tags and path), and the
  ;; `--where` of test_sections_find_command.
  (is (= ["Tasks"] (titles (sections/find-sections recs "tasks" {:where "\"Tasks\" in about"}))))
  (is (= ["Semantic Tags"] (titles (sections/find-sections recs "" {:where "\"Semantic Tags\" in about"}))))
  (is (= ["Roadmap" "Tasks" "Semantic Tags"] (titles (sections/find-sections recs "" {:where "\"Roadmap\" in breadcrumbs"}))))
  (is (= 3 (count (sections/find-sections recs "" {:where "\"type.documentation.Readme\" in semantic_tags"}))))
  (is (= ["Roadmap"] (titles (sections/find-sections recs "" {:where "path = \"roadmap.md#roadmap\""}))))
  (is (= ["Tasks"] (titles (sections/find-sections recs "" {:where "path = \"roadmap.md#roadmap/tasks\""}))))
  (is (= ["Tasks" "Semantic Tags"] (titles (sections/find-sections recs "" {:where "level = 2"}))))
  (is (= :query/bad-where (raised #(sections/find-sections recs "" {:where "\"x\" in"})))))

(deftest result-has-v1-search-record-keys
  ;; v1: SearchRecord.as_dict for kind section.
  (is (= #{:kind :store :name :model :doc :field :path :title :value :semantic :about :breadcrumbs}
         (set (keys (sections/result (first recs))))))
  (is (= "roadmap.md#roadmap" (:path (sections/result (first recs))))))

(deftest fields-of-uses-the-template-owners
  ;; v1: test_sections_fields_command, test_ast_show_field_nodes_have_owning_section
  ;; (the field → section map; the `ast` node itself belongs to the `ast` command).
  (let [d {:template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n\n## Tasks\n\n- ⸢rev,list•tasks⸥\n\n## Semantic Tags\n\n- ⸢rev,list•semantic_tags⸥\n"}
        payload {"title" "Roadmap" "status" "draft" "tasks" ["Ship CLI"] "semantic_tags" ["x"]}
        owners (fn [sec] (into {} (map (juxt :field_path :owning_section)) (sections/fields-of d roadmap payload sec)))]
    (is (= {"title" "roadmap" "status" "roadmap"} (owners "roadmap")))
    (is (= {"tasks" "roadmap/tasks"} (owners "roadmap/tasks")))
    (is (= {"title" "roadmap" "status" "roadmap" "tasks" "roadmap/tasks" "semantic_tags" "roadmap/semantic-tags"} (owners "")))))
