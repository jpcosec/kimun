(ns kimun.surface.graph.store-test
  "`kimun.surface.graph.store`: one canonical graph whatever the source, so a
   snapshot round trip is the identity and readers answer the same."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.surface.graph.io :as gio]
            [kimun.surface.graph.model :as m]
            [kimun.surface.graph.store :as gs]))

(def g
  (m/graph {"sldb://document/Doc:a" {:type "Doc" :semantics {"name" "a"}}
            "sldb://document/Doc:b" {:type "Doc"}
            "sldb://semantic_tag/t" {:type "semantic_tag" :facets {"source" {"k" "v"}}}}
           [{:from "sldb://document/Doc:a" :to "sldb://semantic_tag/t" :type "tagged_as" :metadata {"origin" "structural"}}
            {:from "sldb://document/Doc:a" :to "sldb://document/Doc:b" :type "depends_on" :extra 1}]))

(defn- raised [f] (try (f) nil (catch clojure.lang.ExceptionInfo e (:type (ex-data e)))))

(deftest canonical-shape-and-round-trip
  (let [c (gs/canonical g)]
    (is (= {:type "Doc" :semantics {} :facets {}} (get-in c [:nodes "sldb://document/Doc:b"])))
    (is (= {:from "sldb://document/Doc:a" :to "sldb://document/Doc:b" :type "depends_on" :metadata {}} (second (:edges c))))
    (is (= c (gs/canonical c)))
    (testing "a snapshot, also through JSON text, reads back as the same graph"
      (is (= c (gs/from-snapshot (gs/snapshot g))))
      (is (= c (gs/from-snapshot (gio/read-json (gio/write-json (gs/snapshot g)))))))))

(deftest sources-and-rejections
  (is (= :graph/invalid (raised #(gs/from-snapshot {"a" 1}))))
  (is (= :graph/invalid (raised #(gs/from-snapshot [1 2]))))
  (is (= :graph/bad-export (raised #(gs/from-snapshot {"contract" {"name" "nope" "version" 9}})))))

(deftest readers
  (let [c (gs/canonical g)]
    (is (= "sldb://document/Doc:a" (gs/node-id c "Doc:a") (gs/node-id c "sldb://document/Doc:a")))
    (is (= :graph/missing (raised #(gs/node-id c "Doc:zzz"))))
    (is (= ["sldb://semantic_tag/t" "sldb://document/Doc:b"] (mapv :to (:edges (gs/node c "Doc:a")))))
    (is (= {"name" "a"} (:semantics (gs/node c "Doc:a"))))
    (is (= ["sldb://document/Doc:a" "sldb://document/Doc:b"] (gs/ids c ["Doc"])))
    (is (= 3 (count (gs/ids c))))
    (is (= {:nodes 3 :edges 2 :types {"Doc" 2 "semantic_tag" 1} :relations {"depends_on" 1 "tagged_as" 1}} (gs/summary c)))))
