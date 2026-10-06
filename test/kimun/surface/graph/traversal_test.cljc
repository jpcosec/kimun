(ns kimun.surface.graph.traversal-test
  "Ports of v1 tests/store/test_graph_neighborhood.py (4 tests) plus checks of
   the one-relation walks of v1 traversal.py and the relation-type filter."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.surface.graph.model :as m]
            [kimun.surface.graph.traversal :as t]))

(def a "sldb://document/a")
(def b "sldb://document/b")
(def c "sldb://document/c")
(def d "sldb://document/d")

(def index
  "The graph of v1's neighborhood tests: a->b, b->c, a->d."
  (m/from-triples [[a "r" b] [b "r" c] [a "r" d]] {} "concept"))

(deftest neighborhood-includes-seeds
  (testing "v1 test_collect_neighborhood_includes_seeds"
    (is (= #{a b d} (t/neighborhood index [a] 1)))))

(deftest neighborhood-outgoing
  (testing "v1 test_collect_neighborhood_by_direction_outgoing"
    (is (= #{b d} (t/neighborhood-by-direction index #{a} 1 "outgoing")))))

(deftest neighborhood-incoming
  (testing "v1 test_collect_neighborhood_by_direction_incoming"
    (is (= #{b} (t/neighborhood-by-direction index #{c} 1 "incoming")))))

(deftest neighborhood-depth-two
  (testing "v1 test_collect_neighborhood_depth_two"
    (is (= #{a b c d} (t/neighborhood index [a] 2)))))

(deftest neighborhood-relation-filter
  (testing "extra: the :types filter restricts which relations are followed"
    (let [g (m/add-edge index a "other" c)]
      (is (= #{b d} (t/neighborhood-by-direction g #{a} 1 "outgoing" :types ["r"])))
      (is (= #{c} (t/neighborhood-by-direction g #{a} 1 "outgoing" :types ["other"]))))))

(deftest one-relation-walks
  (testing "extra: targets, sources, roots, parent, descendants, neighbors-via, edge-between"
    (is (= [b d] (t/targets index a "r")))
    (is (= [a] (t/sources index b "r")))
    (is (= [c d] (t/roots index "concept" "r")))
    (is (= b (t/parent index a "r")))
    (is (= [a b] (t/descendants index c "r")))
    (is (= [b] (t/descendants index c "r" 1)))
    (is (= [a] (t/neighbors-via (m/add-edge index c "r" b) c "r" :same-kind false)) "c and a share the target b")
    (is (= (:type (t/edge-between index a b "r")) "r"))
    (is (nil? (t/edge-between index b a "r")))))
