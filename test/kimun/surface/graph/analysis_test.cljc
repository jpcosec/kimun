(ns kimun.surface.graph.analysis-test
  "Ports of v1 tests/store/test_graph_analysis.py (16 tests) and the
   engine-level part of tests/api/test_api_graph_analysis.py, plus checks of
   the reimplemented networkx numbers against values computed by v1."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [kimun.surface.graph.analysis :as a]
            [kimun.surface.graph.io :as gio]
            [kimun.surface.graph.model :as m]
            [kimun.kernel.err :as err]))

(defn- index-of
  "v1 helper `index_of`: a graph holding exactly these [from rel to] edges."
  ([edges] (m/from-triples edges {} "Doc"))
  ([edges types] (m/from-triples edges types "Doc")))

(def chain [["a" "implements" "b"] ["b" "implements" "c"] ["c" "implements" "d"]])

(defn- near? [x y] (< (Math/abs (- x y)) 1e-5))

(deftest derived-spine-excluded-by-default
  (testing "v1 test_the_derived_spine_is_excluded_by_default_so_answers_are_not_truisms"
    (let [g (index-of [["doc1" "has_document" "Model"] ["doc2" "has_document" "Model"]])]
      (is (= #{} (m/authored g)))
      (is (= 0 (count (:edges (m/view g ["implements"])))))
      (is (nil? (a/path-between g "doc1" "doc2" :relations ["implements"])))
      (is (= ["doc1" "Model" "doc2"] (a/path-between g "doc1" "doc2" :relations ["has_document"]))))))

(deftest path-direction
  (testing "v1 test_a_path_crosses_edges_in_either_direction_unless_asked_to_be_directed"
    (let [g (index-of [["a" "implements" "c"] ["b" "implements" "c"]])]
      (is (= ["a" "c" "b"] (a/path-between g "a" "b")))
      (is (nil? (a/path-between g "a" "b" :directed true))))))

(deftest path-unrelated-or-unknown
  (testing "v1 test_a_path_between_unrelated_or_unknown_nodes_is_none"
    (let [g (index-of chain)]
      (is (nil? (a/path-between g "a" "nowhere")))
      (is (= ["a" "b" "c" "d"] (a/path-between g "a" "d"))))))

(deftest every-route-capped
  (testing "v1 test_every_route_between_two_nodes_shortest_first_and_capped"
    (let [g (index-of [["a" "implements" "b"] ["b" "implements" "d"] ["a" "implements" "c"] ["c" "implements" "d"]])]
      (is (= [["a" "b" "d"] ["a" "c" "d"]] (sort (a/paths-between g "a" "d"))))
      (is (= 1 (count (a/paths-between g "a" "d" :limit 1))))
      (is (= [] (a/paths-between g "a" "d" :cutoff 1))))))

(deftest cycle-found-dag-reports-none
  (testing "v1 test_a_cycle_is_found_and_a_dag_reports_none"
    (is (= [] (a/cycles (index-of chain))))
    (is (a/acyclic? (index-of chain)))
    (let [looped (index-of (conj chain ["d" "implements" "a"]))]
      (is (not (a/acyclic? looped)))
      (is (= ["a" "b" "c" "d"] (sort (first (a/cycles looped))))))))

(deftest order-after-what-it-points-at
  (testing "v1 test_order_puts_a_node_after_everything_it_points_at"
    (let [g (index-of chain)]
      (is (= ["d" "c" "b" "a"] (a/topological-order g)))
      (is (= [["d"] ["c"] ["b"] ["a"]] (a/layers g))))))

(deftest ordering-cyclic-raises
  (testing "v1 test_ordering_a_cyclic_relation_raises_with_the_cycle_in_hand"
    (let [g (index-of [["a" "implements" "b"] ["b" "implements" "a"]])
          e (try (a/topological-order g) nil (catch clojure.lang.ExceptionInfo e e))]
      (is (= :graph/cycle (:type (ex-data e))))
      (is (= ["a" "b"] (sort (:cycle (ex-data e)))))
      (is (re-find #"a -> b|b -> a" (ex-message e))))))

(deftest components-islands-isolated
  (testing "v1 test_components_islands_and_isolated_nodes"
    (let [g (index-of (into chain [["x" "implements" "y"] ["lonely" "tagged_as" "tag"]]))]
      (is (= [["a" "b" "c" "d"] ["x" "y"] ["lonely"] ["tag"]] (a/components g)))
      (is (= [["x" "y"] ["lonely"] ["tag"]] (a/islands g)))
      (is (= ["lonely" "tag"] (a/isolated g))))))

(deftest centrality-ranks
  (testing "v1 test_centrality_ranks_what_the_rest_leans_on"
    (let [g (index-of [["a" "implements" "hub"] ["b" "implements" "hub"] ["c" "implements" "hub"]])]
      (is (= "hub" (ffirst (a/central g :limit 1))))
      (is (= [["hub" 3.0]] (a/central g :kind "in_degree" :limit 1)))
      (is (= ["a" "b" "c"] (map first (a/central g :kind "out_degree" :limit 3)))))))

(deftest centrality-rejects-unknown-kind
  (testing "v1 test_centrality_rejects_a_kind_it_does_not_know"
    (let [e (try (a/central (index-of chain) :kind "eigenvector") nil (catch clojure.lang.ExceptionInfo e e))]
      (is (= :graph/unknown-centrality (:type (ex-data e))))
      (is (re-find #"Unknown centrality" (ex-message e))))))

(deftest centrality-without-edges
  (testing "v1 test_centrality_of_a_graph_with_no_edges_is_zero_not_a_crash"
    (is (= [["a" 0.0]] (a/central (m/graph {"a" {:type "Doc"}} []))))))

(deftest resemblance-counts-shared-targets
  (testing "v1 test_resemblance_counts_shared_targets_not_edges_between_the_two"
    (let [g (index-of [["doc1" "tagged_as" "t1"] ["doc1" "tagged_as" "t2"]
                       ["doc2" "tagged_as" "t1"] ["doc2" "tagged_as" "t2"]
                       ["doc3" "tagged_as" "t1"]])]
      (is (= [["doc2" 2] ["doc3" 1]] (a/similar-to g "doc1")))
      (is (= [["doc2" 2]] (a/similar-to g "doc1" :limit 1)))
      (is (= [] (a/similar-to g "nobody"))))))

(deftest node-types-view-vs-answer
  (testing "v1 test_node_types_restrict_the_view_for_components_and_the_answer_for_the_rest"
    (let [g (index-of chain {"a" "Spec" "b" "Spec" "c" "Note" "d" "Note"})]
      (is (= [["a" "b"]] (a/components g :node-types ["Spec"])))
      (is (= ["b" "a"] (map first (a/central g :kind "degree" :node-types ["Spec"]))))
      (is (= 2.0 (second (first (a/central g :kind "degree" :node-types ["Spec"]))))))))

(deftest resemblance-through-omitted-nodes
  (testing "v1 test_resemblance_still_goes_through_the_nodes_the_answer_leaves_out"
    (let [g (index-of [["doc1" "tagged_as" "t1"] ["doc2" "tagged_as" "t1"]]
                      {"doc1" "Doc" "doc2" "Doc" "t1" "semantic_tag"})]
      (is (= [["doc2" 1]] (a/similar-to g "doc1" :node-types ["Doc"]))))))

(deftest analyses-over-a-real-semantic-export
  (testing "v1 test_the_analyses_run_over_a_real_semantic_export"
    (let [g (gio/semantic-export->graph
             (gio/read-json (slurp (io/resource "kimun/surface/graph/sldb_kgdb_semantic_export.v1.json"))))
          present (sort (m/present g))]
      (is (some #{"has_document"} present))
      (is (= #{} (m/authored g)))
      (is (a/acyclic? g))
      (is (= (count (:nodes g)) (count (a/topological-order g :relations "*"))))
      (is (pos? (second (first (a/central g :limit 1))))))))

(deftest cycle-in-must-be-dag-relation
  (testing "v1 test_a_cycle_in_a_relation_that_must_be_a_dag_is_an_error_no_per_edge_check_can_see"
    (is (= [] (a/cycle-errors (index-of [["child" "semantic_parent" "parent"]]))))
    (let [reported (a/cycle-errors (index-of [["a" "semantic_parent" "b"] ["b" "semantic_parent" "a"] ["m" "extends" "m"]]))]
      (is (= 2 (count reported)))
      (is (.startsWith ^String (first reported) "Relation 'semantic_parent' has a cycle:"))
      (is (= "Relation 'extends' has a cycle: m -> m." (second reported))))))

;; ---- api/test_api_graph_analysis.py, engine-level (store-backed parts deferred)

(def s1 "sldb://document/Spec:s1")
(def s2 "sldb://document/Spec:s2")
(def s3 "sldb://document/Spec:s3")
(def lonely "sldb://document/Spec:lonely")

(def world
  "In-memory stand-in for v1's AnalysisWorld: a model with four Spec documents,
   each tagged three ways, s3 -refines-> s2 -refines-> s1, and a stray field."
  (let [docs [s1 s2 s3 lonely]
        tags ["t1" "t2" "t3"]]
    (m/graph (concat [["sldb://model/Spec" {:type "sldb_model"}]
                      ["sldb://field/RelationDoc.condition" {:type "sldb_field"}]]
                     (map (fn [d] [d {:type "Spec"}]) docs)
                     (map (fn [t] [t {:type "semantic_tag"}]) tags))
             (concat (map (fn [d] {:from "sldb://model/Spec" :to d :type "has_document"}) docs)
                     (for [d docs t tags] {:from d :to t :type "tagged_as"})
                     [{:from s2 :to s1 :type "refines"} {:from s3 :to s2 :type "refines"}]))))

(deftest api-authored-relation-is-walked
  (testing "v1 api test_the_authored_relation_is_what_gets_walked_not_the_structural_spine"
    (is (= [s3 s2 s1] (a/path-between world s3 s1)))
    (is (= [s3 "sldb://model/Spec" s1] (a/path-between world s3 s1 :relations ["has_document"])))))

(deftest api-order-and-cycles
  (testing "v1 api test_order_and_cycles_over_what_the_store_asserts"
    (is (= [] (a/cycles world)))
    (let [order (vec (a/topological-order world))]
      (is (< (.indexOf order s1) (.indexOf order s2) (.indexOf order s3))))))

(deftest api-isolated-document
  (testing "v1 api test_a_document_nobody_relates_to_shows_up_as_isolated"
    (is (= [lonely] (a/isolated world :node-types ["Spec"])))
    (is (= [s1 s2 s3] (first (a/components world :node-types ["Spec"]))))
    (is (some #{"sldb://field/RelationDoc.condition"} (a/isolated world)))))

(deftest api-centrality-and-resemblance
  (testing "v1 api test_centrality_and_resemblance_over_a_real_store"
    (is (= [[s1 1.0]] (a/central world :kind "in_degree" :limit 1)))
    (is (= {s2 3 s3 3 lonely 3} (into {} (a/similar-to world s1 :node-types ["Spec"]))))))

(deftest reimplemented-networkx-numbers
  (testing "pagerank, betweenness, degree, cycles against values printed by v1/networkx 3.6.1"
    (let [g (m/add-node (index-of [["a" "r" "b"] ["a" "r" "c"] ["a" "s" "c"] ["b" "r" "c"]
                                   ["c" "r" "a"] ["d" "r" "c"] ["e" "r" "e"]])
                        "z" {:type "Doc"})
          pr (into {} (a/central g))
          expected {"c" 0.32574027663584026 "a" 0.306005245514102 "e" 0.1941747572815533
                    "b" 0.11582729338403827 "d" 0.029126213592233084 "z" 0.029126213592233084}]
      (doseq [[k v] expected] (is (near? v (pr k)) k))
      (is (= {"c" 0.15 "a" 0.1 "b" 0.0 "d" 0.0 "e" 0.0 "z" 0.0}
             (into {} (map (fn [[k v]] [k (/ (Math/round (* v 1e6)) 1e6)])) (a/central g :kind "betweenness"))))
      (is (= {"c" 4.0 "a" 3.0 "b" 2.0 "e" 2.0 "d" 1.0 "z" 0.0} (into {} (a/central g :kind "degree"))))
      (is (= [["e"] ["a" "c"] ["a" "b" "c"]] (a/cycles g))))))
