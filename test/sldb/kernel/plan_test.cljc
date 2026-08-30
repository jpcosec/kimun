(ns sldb.kernel.plan-test
  (:require [clojure.test :refer [deftest is testing]]
            [sldb.kernel.plan :as plan]))

(def ok {:plan/version 1 :base nil :actor "jp" :engines {} :timestamp "2026-08-29T12:00:00.000Z" :ops []})

(defn- rejected-check [p]
  (try (plan/check-schema p) nil
       (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e (:check (ex-data e)))))

(deftest schema-and-pure-data-check-7
  (is (= ok (plan/check-schema ok)))
  (is (= :pure-data (rejected-check (assoc ok :plan/version 2))))
  (is (= :pure-data (rejected-check (assoc ok :timestamp "2026-08-29T12:00:00Z"))) "millisecond UTC format required")
  (is (= :pure-data (rejected-check (assoc ok :ops [{:op :frobnicate}]))))
  (is (= :pure-data (rejected-check (assoc ok :ops [{:op :add-node :node {:f (fn [] 1)}}]))) "no functions")
  (is (= :pure-data (rejected-check (assoc ok :ops [{:op :add-node :node {:x 1.5}}]))) "no floats"))

(deftest capabilities-check-5
  (let [caps {"jp" :all "ana" #{{:op :add-node} {:op :move :tree "T1"}}}]
    (is (plan/capability-ok? caps "jp" {:op :replace} "T9"))
    (is (plan/capability-ok? caps "ana" {:op :add-node} nil))
    (is (plan/capability-ok? caps "ana" {:op :move} "T1"))
    (is (not (plan/capability-ok? caps "ana" {:op :move} "T2")) "tree-scoped entry")
    (is (not (plan/capability-ok? caps "ana" {:op :replace} "T1")))
    (is (not (plan/capability-ok? caps "nobody" {:op :add-node} nil)) "absent actor is rejected")))

(deftest touched-trees
  (is (= #{:t1 "T2"}
         (plan/touched-trees {:ops [{:op :new-tree :tree {:kind :document} :as :t1}
                                    {:op :add-edge :edge {:type :ownership :tree "T2" :parent [] :to "b" :order 0}}
                                    {:op :add-edge :edge {:type :binding :from "a" :to "b"}}]}))))

(deftest opaque-replace-only-check-6
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
               (plan/check-opaque-replace-only (assoc ok :ops [{:op :add-node :node {} :patch {:blob "x"}}])))))
