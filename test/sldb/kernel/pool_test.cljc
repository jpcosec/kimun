(ns sldb.kernel.pool-test
  (:require [clojure.test :refer [deftest is]]
            [sldb.kernel.test-util :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [sldb.host.default :as host]
            [sldb.kernel.node :as node]
            [sldb.kernel.pool :as pool]
            [sldb.kernel.generators :as g]))

(def h host/host)

(deftest put-get-roundtrip
  (let [n (node/make h :sign :text {:text "hola"})
        p (pool/put h (pool/empty-pool) n)]
    (is (= n (pool/get-node p (:id n))))
    (is (pool/has? p (:id n)))
    (is (nil? (pool/get-node p "missing")))))

(deftest tampered-node-is-rejected-invariant-1
  (let [n (node/make h :sign :text {:text "hola"})
        tampered (assoc-in n [:content :text] "adios")]
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                 (pool/put h (pool/empty-pool) tampered)))))

(defspec put-is-idempotent-invariant-10 100
  (prop/for-all [n g/gen-node]
    (let [p1 (pool/put h (pool/empty-pool) n)
          p2 (pool/put h p1 n)]
      (and (= p1 p2) (= 1 (pool/size p2)) (= n (pool/get-node p2 (:id n)))))))

(defspec pool-is-rebuildable-from-its-nodes-invariant-10 50
  (prop/for-all [ns (gen/vector g/gen-node 0 20)]
    (let [p (reduce #(pool/put h %1 %2) (pool/empty-pool) ns)
          rebuilt (reduce #(pool/put h %1 %2) (pool/empty-pool) (shuffle (vec (vals p))))]
      (= p rebuilt))))
