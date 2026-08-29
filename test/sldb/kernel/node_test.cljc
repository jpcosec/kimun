(ns sldb.kernel.node-test
  (:require [clojure.test :refer [deftest is testing]]
            [sldb.kernel.test-util :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.edn :as edn]
            [sldb.host.hash :as hash]
            [sldb.kernel.node :as node]
            [sldb.kernel.generators :as g]
            #?(:clj [clojure.java.io :as io])))

(def h hash/sha-256)

(defn- read-fixture []
  #?(:clj  (edn/read-string (slurp (io/file "test/fixtures/nodes.edn")))
     :cljs (edn/read-string (.readFileSync (js/require "fs") "test/fixtures/nodes.edn" "utf8"))))

(deftest golden-fixture-covers-every-row-and-freezes-ids
  (let [fx (read-fixture)
        rows (set (for [[c ks] node/shapes k (keys ks)] [c k]))]
    (is (= rows (set (map (juxt :class :kind) fx))) "one entry per class/kind row of §2.1")
    (doseq [{:keys [class kind content expected-id]} fx]
      (let [n (node/make h class kind content)]
        (is (= expected-id (:id n)) (str "frozen id for " class "/" kind))))))

(deftest invalid-shapes-are-rejected
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) (node/make h :sign :text {:txt "x"})))
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) (node/make h :sign :nope {:text "x"})))
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) (node/make h :nope :text {:text "x"})))
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) (node/make h :sign :span {:leaf "a" :range [3 1]})))
  (testing "floats are rejected from canonical content (invariant 2 precondition)"
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                 (node/make h :sign :block {:format :markdown :type :heading :attrs {:w 1.5}})))))

(deftest address-is-derived
  (is (= :structural (:address (node/make h :sign :text {:text "a"}))))
  (is (= :opaque (:address (node/make h :sign :opaque {:format "html" :blob "<b>"}))))
  (is (= :external (:address (node/make h :sign :external {:locator {:kind :file :path "x"} :sample "" :fingerprint "0"})))))

(deftest class-and-kind-enter-the-id
  (let [t (node/make h :sign :text {:text "x"})
        o (node/make h :sign :opaque {:format "html" :blob "x"})]
    (is (not= (:id t) (:id o))))
  (let [a (node/make h :symbol :term {:name "x" :lang "es"})
        b (node/make h :fact :context {:name "x"})]
    (is (not= (:id a) (:id b)))))

(deftest nfc-equivalent-content-is-the-same-node
  (is (= (:id (node/make h :sign :text {:text (str "e" "\u0301")}))
         (:id (node/make h :sign :text {:text "\u00e9"}))))
  (is (= "\u00e9" (get-in (node/make h :sign :text {:text (str "e" "\u0301")}) [:content :text]))
      "stored content is normalized"))

(defspec every-generated-node-validates-and-hashes-stably 200
  (prop/for-all [n g/gen-node]
    (and (= (:id n) (node/node-id h n))
         (= n (node/validate n))
         (= (:address n) (node/address n)))))

(defspec same-content-same-id-invariant-2 100
  (prop/for-all [n g/gen-node]
    (= (:id n) (:id (node/make h (:class n) (:kind n) (:content n))))))

(defspec different-kind-different-id 100
  (prop/for-all [t (g/gen-node-of :sign :text)]
    (not= (:id t) (:id (node/make h :sign :opaque {:format "html" :blob (get-in t [:content :text])})))))
