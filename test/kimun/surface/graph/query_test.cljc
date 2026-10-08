(ns kimun.surface.graph.query-test
  "Ports of v1 tests/store/test_graph_executor.py (6 tests) and
   tests/store/test_graph_language.py (3 tests)."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [kimun.surface.graph.io :as gio]
            [kimun.surface.graph.model :as m]
            [kimun.surface.graph.query :as q]
            [kimun.kernel.err :as err]))

(def model "sldb://model/TaskDoc")
(def doc "sldb://document/TaskDoc:001")
(def section "sldb://section/TaskDoc:001#s")
(def tag "sldb://semantic_tag/domain.workflow.task")

(def index
  "The graph of v1's executor tests `_index()`."
  (m/graph [[model {:type "sldb_model"}]
            [doc {:type "TaskDoc" :semantics {"name" "001" "path" "a.md"}}]
            [section {:type "sldb_section"}]
            [tag {:type "semantic_tag" :facets {"source" {"producer" "sldb"}}}]]
           [{:from model :to doc :type "has_document"}
            {:from doc :to section :type "has_section"}
            {:from doc :to tag :type "tagged_as"}]))

(defn- ids [query] (mapv :id (q/execute index query)))

(deftest descendant-scope
  (testing "v1 test_descendant_scope_reaches_all_nodes_except_the_root"
    (is (= [doc section tag] (ids {"scope" {"descendant_of" model}})))))

(deftest identity-filter
  (testing "v1 test_identity_filter_narrows_by_node_type"
    (is (= [section]
           (ids {"scope" {"descendant_of" model}
                 "filters" [{"facet" "identity" "conditions" [{"field" "node_type" "op" "eq" "value" "sldb_section"}]}]})))))

(deftest ancestor-scope-and-semantics-filter
  (testing "v1 test_ancestor_scope_and_semantics_filter"
    (is (= [doc]
           (ids {"scope" {"ancestor_of" section}
                 "filters" [{"facet" "semantics" "conditions" [{"field" "name" "op" "eq" "value" "001"}]}]})))))

(deftest prefix-scope
  (testing "v1 test_prefix_scope_finds_tag_nodes"
    (is (= [tag] (ids {"scope" {"node_id_prefix" "sldb://semantic_tag/"}})))))

(deftest source-facet-filterable
  (testing "v1 test_source_facet_is_filterable"
    (is (= [tag] (ids {"filters" [{"facet" "source" "conditions" [{"field" "producer" "op" "eq" "value" "sldb"}]}]})))))

(deftest relation-filter-honors-direction
  (testing "v1 test_relation_filter_honors_direction"
    (let [[node] (q/execute index {"scope" {"node_id_prefix" "sldb://document/"} "relations" [{"direction" "incoming"}]})]
      (is (= ["has_document"] (map :type (:edges node)))))))

(deftest operators-beyond-eq
  (testing "extra: the operators v1 test_graph_language only constructs, executed"
    (let [run (fn [op v] (ids {"filters" [{"facet" "semantics" "conditions" [{"field" "name" "op" op "value" v}]}]}))]
      (is (= [doc] (run "starts_with" "00")))
      (is (= [doc] (run "is_not_null" nil)))
      (is (= [model section tag] (run "is_null" nil)))
      (is (= [model section tag] (run "ne" "001")) "nil differs from 001, as in Python")
      (is (= [doc] (run "gt" "000")))
      (is (= [doc] (run "lt" "002")))
      (is (= [] (run "contains" "0"))))))

(def query-files
  ["documents_near_domain_workflow_task_tag" "documents_near_taskdoc_model"
   "documents_tagged_domain_contract" "sections_for_taskdoc_document"
   "semantic_neighborhood_integration_kgdb"])

(deftest example-queries-parse
  (testing "v1 test_example_queries_parse"
    (doseq [f query-files]
      (let [parsed (q/parse-query (gio/read-json (slurp (io/resource (str "kimun/surface/graph/queries/" f ".json")))))]
        (is (map? parsed) f)))))

(deftest field-condition-accepts-all-eight-operators
  (testing "v1 test_field_condition_accepts_all_eight_operators"
    (doseq [op ["eq" "ne" "is_null" "is_not_null" "contains" "gt" "lt" "starts_with"]]
      (is (= op (get-in (q/parse-query {"filters" [{"facet" "f" "conditions" [{"field" "name" "op" op "value" "x"}]}]})
                        [:filters 0 :conditions 0 :op]))))
    (is (= :graph/bad-query
           (try (q/parse-query {"filters" [{"facet" "f" "conditions" [{"field" "n" "op" "regex"}]}]})
                (catch clojure.lang.ExceptionInfo e (:type (ex-data e))))))))

(deftest relation-filter-declares-direction
  (testing "v1 test_relation_filter_declares_direction"
    (is (= "both" (get-in (q/parse-query {"relations" [{}]}) [:relations 0 :direction])))
    (is (= "incoming" (get-in (q/parse-query {"relations" [{"direction" "incoming"}]}) [:relations 0 :direction])))))
