(ns sldb.kernel.edge-test
  (:require [clojure.test :refer [deftest is testing]]
            [sldb.kernel.test-util :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [sldb.host.default :as host]
            [sldb.kernel.edge :as edge]
            [sldb.kernel.generators :as g]))

(def h host/host)
(def a (apply str (repeat 64 "a")))
(def b (apply str (repeat 64 "b")))
(def c (apply str (repeat 64 "c")))

(deftest ownership-and-non-ownership-shapes
  (is (:id (edge/make h {:type :ownership :parent [0 2] :to b :tree "01ARZ3NDEKTSV4RRFFQ69G5FAV" :order 0})))
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
               (edge/make h {:type :ownership :parent [] :to b})) "no tree")
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
               (edge/make h {:type :ownership :from a :to b :tree "01ARZ3NDEKTSV4RRFFQ69G5FAV" :order 0})) "ownership addresses a parent path, not :from")
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
               (edge/make h {:type :binding :from a :to b :tree "x" :evidence {:ref-hash b :actor "jp" :context c}}))))

(deftest evidence-requirements
  (testing "binding needs ref-hash, one origin and context"
    (is (:id (edge/make h {:type :binding :from a :to b :evidence {:ref-hash b :actor "jp" :context c}})))
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                 (edge/make h {:type :binding :from a :to b :evidence {:ref-hash b :actor "jp"}})))
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                 (edge/make h {:type :binding :from a :to b :evidence {:ref-hash b :actor "jp" :engine "e" :version "1" :context c}}))
        "exactly one origin"))
  (testing "projection needs a sense status"
    (is (:id (edge/make h {:type :projection :from a :to b :evidence {:ref-hash b :engine "m" :version "1" :context c :status :sinnvoll}})))
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                 (edge/make h {:type :projection :from a :to b :evidence {:ref-hash b :engine "m" :version "1" :context c :status :maybe}}))))
  (testing "derived needs engine+version; context optional"
    (is (:id (edge/make h {:type :derived :from a :to b :evidence {:ref-hash b :engine "parser" :version "2"}}))))
  (testing "supersedes needs actor"
    (is (:id (edge/make h {:type :supersedes :from a :to b :evidence {:actor "jp"}})))))

(deftest timestamp-is-not-part-of-an-edge-invariant-16
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
               (edge/make h {:type :reference :from a :to b :timestamp "2026-08-29T00:00:00.000Z" :evidence {:ref-hash b :actor "jp"}})))
  (let [e1 (edge/make h {:type :reference :from a :to b :evidence {:ref-hash b :actor "jp"}})
        e2 (edge/make h {:type :reference :from a :to b :evidence {:ref-hash b :actor "jp"}})
        e3 (edge/make h {:type :reference :from a :to b :evidence {:ref-hash b :actor "ana"}})]
    (is (= (:id e1) (:id e2)) "same assertion by the same origin is one edge")
    (is (not= (:id e1) (:id e3)) "different origins are two evidences")))

(defspec generated-edges-validate-and-hash-stably 200
  (prop/for-all [e g/gen-edge]
    (and (= (:id e) (edge/edge-id h e))
         (= e (edge/validate h e)))))
