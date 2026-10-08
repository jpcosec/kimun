(ns kimun.surface.models.segments-test
  "Segments, extraction segments, sections and field ownership: the ports of
   sldb v1's tests/test_field_ownership.py, tests/test_segment_structure.py,
   tests/test_extraction_segments.py, tests/test_document_segments.py and the
   segments half of tests/api/test_composition_ref_root.py. The model
   descriptors are the ones v1's exporter recorded for those tests' classes
   (the `descriptor` field of test/fixtures/parity/segments.cases.jsonl.gz),
   copied verbatim. v1's store-wide invariants run here over every
   `render_with_spans` case of the segments corpus, which replays that store
   document by document."
  (:require [clj-yaml.core :as yaml]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [kimun.surface.models.render :as render]
            [kimun.surface.models.segments :as segments])
  (:import (java.util.zip GZIPInputStream)))

;; ── tests/test_field_ownership.py ────────────────────────────────────────

;; v1: tests/test_field_ownership.py::test_normal_marker
(deftest normal-marker
  (is (= {"title" 1} (segments/field-lines "# ⸢rev•title⸥"))))

;; v1: tests/test_field_ownership.py::test_list_marker
(deftest list-marker
  (is (= {"tasks" 1} (segments/field-lines "- ⸢rev,list•tasks⸥"))))

;; v1: tests/test_field_ownership.py::test_unknown_extra_modifier
(deftest unknown-extra-modifier
  (is (= {"status" 1} (segments/field-lines "⸢rev,list,opt•status⸥"))))

;; v1: tests/test_field_ownership.py::test_malformed_no_name
(deftest malformed-no-name
  (is (= {} (segments/field-lines "⸢rev•⸥"))))

;; v1: tests/test_field_ownership.py::test_multiple_markers_one_line
(deftest multiple-markers-one-line
  (is (= {"a" 1 "b" 1 "c" 1} (segments/field-lines "⸢rev•a⸥ ⸢rev•b⸥ ⸢rev•c⸥"))))

;; v1: tests/test_field_ownership.py::test_multiple_lines
(deftest multiple-lines
  (is (= {"title" 1 "status" 3 "tasks" 7}
         (segments/field-lines "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n\n## Tasks\n\n- ⸢rev,list•tasks⸥"))))

;; v1: tests/test_field_ownership.py::test_optrev_marker_also_captured
(deftest optrev-marker-also-captured
  (is (= {"title" 1} (segments/field-lines "⸢optrev•title⸥"))))

;; v1: tests/test_field_ownership.py::test_render_marker_ignored
(deftest render-marker-ignored
  (is (= {} (segments/field-lines "⸢render•title⸥"))))

;; v1: tests/test_field_ownership.py::test_unknown_field_logs_warning
;; The result is ported; v1's second assertion (`"bogus" in caplog.text`) is
;; about its logger, which a pure engine does not have.
(deftest unknown-field-logs-warning
  (is (= {"bogus" 1} (segments/field-lines "⸢rev•bogus⸥" #{"title" "status"}))))

;; v1: tests/test_field_ownership.py::test_ownership_follows_template_heading_order_not_rendered_lines
(deftest ownership-follows-template-heading-order-not-rendered-lines
  (let [template "# ⸢rev•title⸥\n\n⸢rev,list•items⸥\n\n## Details\n\n⸢rev•detail⸥"
        rendered "# A title\n\n- one\n- two\n- three\n\n## Details\n\nA value\n"]
    (is (= {"title" "a-title" "items" "a-title" "detail" "a-title/details"}
           (segments/field-owners template (segments/sections rendered))))))

;; v1: tests/test_field_ownership.py::test_section_span_covers_body_until_next_peer_or_parent
(deftest section-span-covers-body-until-next-peer-or-parent
  (is (= [["parent" 1 4] ["parent/child" 3 4] ["next" 5 6]]
         (mapv (juxt :path :line_start :line_end) (segments/sections "# Parent\nbody\n## Child\nchild\n# Next\nnext\n")))))

;; v1: tests/test_field_ownership.py::test_repeated_sibling_titles_receive_distinct_deterministic_paths
(deftest repeated-sibling-titles-receive-distinct-deterministic-paths
  (is (= ["same" "same-2"] (mapv :path (segments/sections "# Same\nfirst\n# Same\nsecond\n")))))

;; v1: tests/test_field_ownership.py::test_single_section_template_keeps_ownership_when_a_field_value_has_headings
(deftest single-section-template-keeps-ownership-when-a-field-value-has-headings
  (let [template "# ⸢rev•title⸥\n\n⸢rev•body⸥"
        rendered "# A title\n\n## Section inside the body\n\nText.\n\n## Another one\n\nMore.\n"
        owners (segments/field-owners template (segments/sections rendered))]
    (is (= #{"title" "body"} (set (keys owners))))
    (is (= #{"a-title"} (set (vals owners))))))

;; v1: tests/test_field_ownership.py::test_multiple_section_template_still_refuses_when_counts_differ
(deftest multiple-section-template-still-refuses-when-counts-differ
  (let [template "# ⸢rev•title⸥\n\n## Goal\n\n⸢rev•goal⸥"
        rendered "# A title\n\n## Goal\n\nText.\n\n## Injected by a value\n\nMore.\n"]
    (is (= {} (segments/field-owners template (segments/sections rendered))))))

;; ── tests/test_segment_structure.py ──────────────────────────────────────

(def ^:private segmented-doc
  {:model/name "SegmentedDoc" :model/version 1 :base [] :fields [{:name "title" :type :string :required true :description "The document's title." :py/annotation "<class 'str'>"} {:name "note" :type :string :required false :description "An optional inline note." :py/annotation "str | None"} {:name "tags" :type [:list :string] :required false :description "Free-form tags." :py/annotation "list[str]" :default []}] :template "# ⸢rev•title⸥\n\nNote: ⸢optrev•note⸥ (end)\n\n- ⸢rev,list•tags⸥"})

(def ^:private trailing-newline-doc
  {:model/name "TrailingNewlineDoc" :model/version 1 :base [] :fields [{:name "title" :type :string :required true :description "The document's title." :py/annotation "<class 'str'>"} {:name "body" :type :string :required true :description "A block value that may end in newlines." :py/annotation "<class 'str'>"}] :template "# ⸢rev•title⸥\n\n## Body\n\n⸢render•body⸥\n\n## After"})

(defn- fields-by-path
  "Every field segment of a tree, flattened and keyed by its path (v1 `_fields`)."
  [tree]
  (reduce (fn [out segment]
            (merge (if (= "literal" (:type segment)) out (assoc out (:path segment) segment))
                   (fields-by-path (:children segment))))
          {} tree))

;; v1: tests/test_segment_structure.py::test_field_paths_addresses_and_values_follow_the_fields_space
(deftest field-paths-addresses-and-values-follow-the-fields-space
  (let [{:keys [text] tree :segments} (segments/segments segmented-doc {"title" "T" "note" "N" "tags" ["a" "b"]})
        fields (fields-by-path tree)]
    (is (= ["note" "tags/0" "tags/1" "title"] (sort (keys fields))))
    (is (= ["T" "string"] ((juxt :value :kind) (fields "title"))))
    (is (= ["b" "string"] ((juxt :value :kind) (fields "tags/1"))))
    (is (= "b" (subs text (:start (fields "tags/1")) (:end (fields "tags/1")))))))

;; v1: tests/test_segment_structure.py::test_editable_comes_from_the_marker_not_from_the_value
(deftest editable-comes-from-the-marker-not-from-the-value
  (let [fields (fields-by-path (:segments (segments/segments segmented-doc {"title" "T" "note" "N" "tags" []})))]
    (is (true? (:editable (fields "title"))))
    (is (true? (:editable (fields "note"))))
    (is (= "optrev" (:marker (fields "note"))))))

;; v1: tests/test_segment_structure.py::test_absent_optional_sharing_a_line_leaves_a_zero_length_insertion_point
(deftest absent-optional-sharing-a-line-leaves-a-zero-length-insertion-point
  (let [{:keys [text] tree :segments} (segments/segments segmented-doc {"title" "T" "note" nil "tags" ["a"]})
        note ((fields-by-path tree) "note")]
    (is (= (:start note) (:end note)))
    (is (= "" (:text note)))
    (is (nil? (:value note)))
    (is (= " (end" (subs text (:start note) (+ (:start note) 5))))))

;; v1: tests/test_segment_structure.py::test_literals_and_fields_tile_the_text_in_reading_order
(deftest literals-and-fields-tile-the-text-in-reading-order
  (let [{:keys [text] tree :segments} (segments/segments segmented-doc {"title" "T" "note" "N" "tags" ["a" "b"]})]
    (is (= text (apply str (map :text tree))))
    (is (= (sort (map :start tree)) (map :start tree)))))

;; v1: tests/test_segment_structure.py::test_a_single_piece_composition_nests_inside_its_own_field
(deftest a-single-piece-composition-nests-inside-its-own-field
  (let [piece {:start 0 :end 4 :_order 0 :children [] :type "composition" :text "abcd"}
        field {:start 0 :end 4 :_order 1 :children [] :type "field" :text "abcd"}
        roots (segments/segment-tree [piece field] "abcd")]
    (is (= ["field"] (mapv :type roots)))
    (is (= ["composition"] (mapv :type (:children (first roots)))))))

;; v1: tests/test_segment_structure.py::test_sentinels_do_not_shield_the_newline_jinja_drops
(deftest sentinels-do-not-shield-the-newline-jinja-drops
  (doseq [body ["text\n\n" "text\n" "text" "a\nb\n\n\n"]]
    (let [payload {"title" "T" "body" body}]
      (is (= (render/render trailing-newline-doc payload) (:text (segments/segments trailing-newline-doc payload)))
          (pr-str body)))))

;; ── tests/test_extraction_segments.py ────────────────────────────────────

(def ^:private extractable-doc
  {:model/name "ExtractableDoc" :model/version 1 :base [] :fields [{:name "title" :type :string :required true :description "The document's title." :py/annotation "<class 'str'>"} {:name "body" :type :string :required true :description "The document's body text." :py/annotation "<class 'str'>"}] :template "# ⸢rev•title⸥\n\n## Body\n\n⸢rev•body⸥"})

(defn- align
  "v1 `align(canonical, raw)`: the engine's private difflib opcodes over the
   code points of both texts."
  [canonical raw]
  (#'segments/opcodes (#'segments/cps canonical) (#'segments/cps raw)))

;; v1: tests/test_extraction_segments.py::test_canonical_markdown_locates_every_field_exactly
(deftest canonical-markdown-locates-every-field-exactly
  (let [markdown (render/render extractable-doc {"title" "T" "body" "B"})
        result (segments/extract-segments extractable-doc markdown)]
    (is (= "ExtractableDoc" (:model result)))
    (is (= markdown (:text result)))
    (is (= markdown (:canonical_text result)))
    (is (= {"title" "T" "body" "B"} (:payload result)))
    (is (= #{"exact"} (set (map :alignment (:fields result)))))
    (is (= ["title" "body"] (mapv :path (:fields result))))
    (is (= [] (:unmatched result)))))

;; v1: tests/test_extraction_segments.py::test_field_offsets_address_the_raw_text_not_the_canonical_one
(deftest field-offsets-address-the-raw-text-not-the-canonical-one
  (let [result (segments/extract-segments extractable-doc "#    T\n\n## Body\n\nB")]
    (is (seq (:fields result)))
    (doseq [field (:fields result)]
      (is (= (:text field) (subs (:text result) (:start field) (:end field))) (:path field)))))

;; v1: tests/test_extraction_segments.py::test_fields_come_back_in_reading_order
(deftest fields-come-back-in-reading-order
  (let [markdown (render/render extractable-doc {"title" "T" "body" "B"})
        starts (mapv :start (:fields (segments/extract-segments extractable-doc markdown)))]
    (is (= (sort starts) starts))))

;; v1: tests/test_extraction_segments.py::test_coverage_is_a_fraction_of_the_meaningful_text
(deftest coverage-is-a-fraction-of-the-meaningful-text
  (let [small (:coverage (segments/extract-segments extractable-doc "# T\n\n## Body\n\nB"))
        large (:coverage (segments/extract-segments extractable-doc "# TTTTTTTTTT\n\n## Body\n\nBBBBBBBBBB"))]
    (is (<= 0.0 small 1.0))
    (is (<= 0.0 large 1.0))
    (is (> large small))))

;; v1: tests/test_extraction_segments.py::test_changed_text_yields_approximate_borders
(deftest changed-text-yields-approximate-borders
  (let [ops (align "a HOLA b" "a hola b")
        [start exact-start] (#'segments/map-offset ops 2 false)
        [end exact-end] (#'segments/map-offset ops 6 true)]
    (is (= [2 6] [start end]))
    (is (not exact-start))
    (is (not exact-end))))

;; v1: tests/test_extraction_segments.py::test_unmatched_runs_are_trimmed_and_blank_ones_dropped
(deftest unmatched-runs-are-trimmed-and-blank-ones-dropped
  (is (= [] (#'segments/unmatched-runs (align "ab" "a   \n  b") "a   \n  b")))
  (let [runs (#'segments/unmatched-runs (align "ab" "a EXTRA b") "a EXTRA b")]
    (is (= ["EXTRA"] (mapv :text runs)))
    (is (every? #(= (:text %) (str/trim (:text %))) runs))))

;; ── tests/test_document_segments.py ──────────────────────────────────────
;; v1 walks its own tracked store (`.sldb`), which the engine cannot reach.
;; The segments corpus recorded one `render_with_spans` case per document of
;; that store (plus the ones v1's unit tests render), so the same invariants
;; run here over every such case: descriptor, payload and ref world as
;; recorded, v1's recorded output never read.

(def ^:private corpus "test/fixtures/parity/segments.cases.jsonl.gz")

(defn- corpus-cases
  "Every case of the packed segments corpus, sorted by id; each is a map of the
   fields the case directory used to hold, as the byte-exact text of each file."
  []
  (with-open [r (io/reader (GZIPInputStream. (io/input-stream corpus)))]
    (mapv #(json/parse-string % true) (line-seq r))))

(defn- cp-slice [^String s a b]
  (subs s (.offsetByCodePoints s 0 (int a)) (.offsetByCodePoints s 0 (int b))))

(defn- flatten-tree
  "Every segment of a tree, parents before children (v1 `_flatten`)."
  [tree]
  (mapcat #(cons % (flatten-tree (:children %))) tree))

(defn- case-verdict
  "`{:id :identical? :tiles? :offsets?}` for one recorded case record, nil when
   it is not a `render_with_spans` case."
  [rec]
  (when (= :render_with_spans (:op (edn/read-string (:meta rec))))
    (let [descriptor (edn/read-string (:descriptor rec))
          payload (yaml/parse-string (:payload rec) :keywords false)
          refs (some-> (:refs rec) (yaml/parse-string :keywords false))
          models (some-> (:ref-models rec) edn/read-string)
          ctx (when (or refs models) {:ref-text #(get refs %) :ref-model #(get models %)})
          {:keys [text] tree :segments} (segments/segments descriptor payload ctx)]
      {:id (:id rec)
       :identical? (= text (render/render descriptor payload ctx))
       :tiles? (= text (apply str (map :text tree)))
       :offsets? (every? #(= (:text %) (cp-slice text (:start %) (:end %))) (flatten-tree tree))})))

(def ^:private verdicts
  (delay (vec (keep case-verdict (corpus-cases)))))

;; v1: tests/test_document_segments.py::test_instrumented_render_matches_canonical_render_for_every_document
(deftest instrumented-render-matches-canonical-render-for-every-document
  (is (> (count @verdicts) 1500))
  (is (= [] (mapv :id (remove :identical? @verdicts)))))

;; v1: tests/test_document_segments.py::test_segments_concatenate_back_into_the_document_text
(deftest segments-concatenate-back-into-the-document-text
  (is (= [] (mapv :id (remove :tiles? @verdicts)))))

;; v1: tests/test_document_segments.py::test_every_segment_text_matches_its_own_offsets
(deftest every-segment-text-matches-its-own-offsets
  (is (= [] (mapv :id (remove :offsets? @verdicts)))))

;; ── tests/api/test_composition_ref_root.py (segments half) ───────────────

(def ^:private task
  {:model/name "Task" :model/version 1 :base [] :fields [{:name "title" :type :string :required true :description "Title." :py/annotation "<class 'str'>"} {:name "status" :type :string :required true :description "Status." :py/annotation "<class 'str'>"}] :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥"})

(def ^:private board
  {:model/name "Board" :model/version 1 :base [] :fields [{:name "title" :type :string :required true :description "Title." :py/annotation "<class 'str'>"} {:name "tasks" :type [:list :string] :required false :description "Task refs, project-relative." :py/annotation "list[str]" :default []}] :template "# ⸢rev•title⸥\n\n## Tasks\n\n- ⸢rev,list•tasks⸥\n\n## Summary\n\n⸢render•summary⸥" :py/compositions {"summary" {"source_field" "tasks" "model" "sldb_compose_root_models:Task" "template" "- {title} [{status}]"}}})

(defn- world
  "A render context over in-memory `files` keyed by absolute path: a relative
   ref resolves against `root` (the project root), never against a cwd."
  [files root]
  {:ref-model {"sldb_compose_root_models:Task" task}
   :ref-text (fn [ref] (get files (if (str/starts-with? ref "/") ref (str root "/" ref))))})

(def ^:private summary "- Ship the CLI [active]")

;; v1: tests/api/test_composition_ref_root.py::test_document_segments_compose_against_the_project_root
;; `create_document` wrote the composed render to board.md; the document is
;; then segmented from its file text with refs resolved against the project.
(deftest document-segments-compose-against-the-project-root
  (let [files {"/project/task-a.md" (str (render/render task {"title" "Ship the CLI" "status" "active"}) "\n")}
        ctx (world files "/project")
        on-disk (str (render/render board {"title" "Board" "tasks" ["task-a.md"]} ctx) "\n")
        result (segments/document-segments board on-disk (assoc ctx :id "Board/board" :doc "board" :path "board.md"))]
    (is (str/includes? on-disk summary))
    (is (str/includes? (:text result) summary))
    (is (true? (:canonical result)))))

;; ── kimun-only units (no v1 test function behind them) ───────────────────

;; kimun: `document-segments` mirrors every field of v1's DocumentSegments;
;; `:canonical` turns false when a composed source changed after the save
;; (v1 build_document_segments docstring).
(deftest document-segments-mirrors-v1-document-segments
  (let [task-md #(str (render/render task {"title" "Ship the CLI" "status" %}) "\n")
        ctx (world {"/project/task-a.md" (task-md "active")} "/project")
        on-disk (str (render/render board {"title" "Board" "tasks" ["task-a.md"]} ctx) "\n")
        result (segments/document-segments board on-disk (assoc ctx :id "Board/board" :doc "board" :path "board.md"))
        later (segments/document-segments board on-disk (world {"/project/task-a.md" (task-md "done")} "/project"))]
    (is (= #{:id :doc :model :path :text :canonical :payload :segments} (set (keys result))))
    (is (= ["Board/board" "board" "Board" "board.md"] ((juxt :id :doc :model :path) result)))
    (is (= {"title" "Board" "tasks" ["task-a.md"]} (:payload result)))
    (is (= (:text result) (apply str (map :text (:segments result)))))
    (is (= (select-keys (segments/segments board (:payload result) ctx) [:text :segments]) (select-keys result [:text :segments])))
    (is (= [nil nil nil] ((juxt :id :doc :path) later)))
    (is (false? (:canonical later)))
    (is (str/includes? (:text later) "- Ship the CLI [done]"))
    ;; Python's strip also drops U+00A0 and U+0085, which clojure.string/trim keeps
    (is (true? (:canonical (segments/document-segments board (str on-disk "\u00a0\n\u0085") ctx))))))

(def ^:private coverage-doc
  {:model/name "CoverageDoc" :model/version 1 :fields [{:name "title" :type :string :required true :description "title."}]
   :template "# ⸢rev•title⸥\n\nabcdefghijklmnopqrstuvwxyz."})

;; kimun: coverage is rounded as Python's `round(x, 4)`. One covered character
;; out of 32 is the exact tie 0.03125: v1 answers 0.0312 (half-even), where
;; `Math/round` answered 0.0313. Both expected values were obtained by running
;; v1's `build_extraction_segments` on a pydantic `CoverageDoc` with this
;; template and these two texts.
(deftest coverage-rounds-as-python-round
  (is (= 0.0312 (:coverage (segments/extract-segments coverage-doc "# T\n\nabcdefghijklmnopqrstuvwxyz."))))
  (is (= 0.1389 (:coverage (segments/extract-segments coverage-doc "# TTTTT\n\nabcdefghijklmnopqrstuvwxyz.")))))
