(ns kimun.surface.semantic-test
  "Semantic layer, pure half: tags of a document, the DAG walks of v1
   `tests/store/test_semantic_dag_walk.py`, the empty-store queries of
   `test_queries_expanded.py`."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.surface.semantic :as sem]))

(def derived
  (sem/dag-of ["type" "type.knowledge" "type.knowledge.anchor" "type.knowledge.spec" "layer" "layer.topology"]))
(def authored (sem/dag-of (keys (:nodes derived)) {:parents {"layer.topology" #{"type.knowledge"}}}))
(def tagged [{:model "Doc" :name "spec-01" :tags ["type.knowledge.spec"]}
             {:model "Doc" :name "anchor-a" :tags ["type.knowledge.anchor"]}
             {:model "Doc" :name "elsewhere" :tags ["layer.topology"]}])

;; v1: test_naming_a_tag_reaches_its_subtree_and_a_document_is_reached_by_its_ancestors
(deftest scope-looks-down-and-reach-looks-up
  (is (= #{"type.knowledge" "type.knowledge.anchor" "type.knowledge.spec"} (sem/scope derived "type.knowledge")))
  (is (= #{"type.knowledge.spec"} (sem/scope derived "type.knowledge.spec")))
  (is (= #{"nobody"} (sem/scope derived "nobody")))
  (is (= ["type" "type.knowledge"] (sem/above derived "type.knowledge.spec")))
  (is (= #{"type.knowledge.spec" "type.knowledge" "type"} (sem/reach derived ["type.knowledge.spec"]))))

;; v1: test_the_dag_is_a_graph_of_child_to_parent_edges
(deftest the-dag-is-child-to-parent-edges
  (is (= 6 (count (:nodes derived))))
  (is (= ["type"] (sem/parents derived "type.knowledge")))
  (is (= ["layer" "type"] (sem/roots derived)))
  (is (= ["type.knowledge.anchor" "type.knowledge.spec"] (sem/children derived "type.knowledge")))
  (is (= ["type.knowledge" "type.knowledge.anchor" "type.knowledge.spec"] (sem/under derived "type")))
  (is (= [] (sem/children derived "nobody") (sem/under derived "nobody"))))

;; v1: test_a_parent_that_is_not_a_prefix_is_an_edge_like_any_other
(deftest a-declared-parent-is-an-edge-like-any-other
  (is (= ["layer" "type.knowledge"] (sem/parents authored "layer.topology")))
  (is (some #{"layer.topology"} (sem/children authored "type.knowledge")))
  (is (some #{"layer.topology"} (sem/under authored "type"))))

;; v1: test_a_child_reads_relative_to_its_parent_unless_the_name_says_otherwise
(deftest a-child-reads-relative-to-its-parent
  (is (= "knowledge" (sem/relative "type.knowledge" "type")))
  (is (= "layer.topology" (sem/relative "layer.topology" "type.knowledge")))
  (is (= "type" (sem/relative "type" ""))))

;; v1: test_listing_walks_the_dag_and_leaves_stop_being_invisible
(deftest listing-walks-the-dag
  (is (= ["layer" "type"] (sem/list-tags derived "")))
  (is (= ["anchor" "spec"] (sem/list-tags derived "type.knowledge")))
  (is (= ["topology"] (sem/list-tags derived "layer"))))

;; v1: test_listing_sees_a_parent_declared_by_hand_in_the_dag_file
(deftest listing-sees-a-declared-parent
  (is (some #{"layer.topology"} (sem/list-tags authored "type.knowledge"))))

;; v1: test_naming_a_parent_now_reaches_the_documents_under_it
(deftest naming-a-parent-reaches-the-documents-under-it
  (is (= ["Doc:anchor-a" "Doc:spec-01"] (sem/documents-for derived tagged "type.knowledge")))
  (is (= ["Doc:spec-01"] (sem/documents-for derived tagged "type.knowledge.spec")))
  (is (= ["Doc:elsewhere"] (sem/documents-for derived tagged "layer"))))

;; v1: test_a_glob_still_matches_tag_by_tag_while_a_plain_tag_names_its_subtree
(deftest a-glob-matches-tag-by-tag-and-a-plain-tag-names-its-subtree
  (let [spec ["type.knowledge.spec"]]
    (is (sem/in-scope? derived "type" spec))
    (is (sem/in-scope? derived "type.knowledge.spec" spec))
    (is (not (sem/in-scope? derived "layer" spec)))
    (is (sem/in-scope? derived "type.knowledge.*" spec))
    (is (not (sem/in-scope? derived "type.*" spec)))
    (is (sem/in-scope? derived "type.**" spec))))

;; v1 test_queries_expanded.py: test_semantic_list_root, test_glob_semantic_empty,
;; test_get_global_semantic_empty (its local half: no tag, no equivalence, no document)
(deftest a-store-without-tags-lists-nothing
  (let [empty-dag (sem/dag-of [])]
    (is (= [] (sem/list-tags empty-dag "")))
    (is (= [] (sem/glob-tags empty-dag "*")))
    (is (= [] (sem/documents-for empty-dag [] "type")))))

(deftest equivalent-tags-match-each-other
  (let [g (sem/dag-of (keys (:nodes derived)) {:equivalences {"layer.topology" #{"type.knowledge.spec"}}})]
    (is (= ["type.knowledge.spec"] (sem/equivalents g "layer.topology")))
    (is (= ["Doc:elsewhere" "Doc:spec-01"] (sem/documents-for g tagged "type.knowledge.spec")))
    (is (= ["Doc:elsewhere" "Doc:spec-01"] (sem/documents-for g tagged "layer.topology")))
    (is (= ["Doc:elsewhere"] (sem/documents-for g tagged "layer")) "an ancestor does not inherit its descendant's equivalence")))

(deftest model-tags-follow-v1-flattening
  (let [base {:model/name "Base" :base [] :semantics {"source" ["code" "python"] "system" "desk"}}
        child {:model/name "Child" :base ["Base"] :semantics {:type {"knowledge" ["a" "b"] "x" "y"} "empty" []}}]
    (is (= ["representation.markdown" "source.document.markdown"] (sem/model-tags {:model/name "Plain"} {})))
    (is (= ["representation.markdown" "source.code.python" "system.desk" "type.knowledge.a.b" "type.x.y"]
           (sem/model-tags child {"Base" base})) "a base replaces a key whole; the model adds its own")
    (is (= ["a:b" "representation.markdown" "source.document.markdown" "t"]
           (sem/tags-of {:model/name "Plain"} {} {:payload {"tags" [" t " "" 3] "semantic_tags" ["a:b"]}})))))
