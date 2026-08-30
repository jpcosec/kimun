(ns sldb.surface.markdown.plan-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [sldb.host.default :as host]
            [sldb.kernel.revision :as rev]
            [sldb.kernel.node :as node]
            [sldb.surface.markdown.ast :as ast]
            [sldb.surface.markdown.render :as render]
            [sldb.surface.markdown.plan :as plan]
            [sldb.surface.markdown.report :as report]
            #?(:clj [clojure.java.io :as io])))

(def h host/host)
(def T "01ARZ3NDEKTSV4RRFFQ69G5FAV")
(def U "01ARZ3NDEKTSV4RRFFQ69G5FAW")
(def TS "2026-08-29T12:00:00.000Z")
(defn- slurp-fixture [f] #?(:clj (slurp (io/file f))))
(defn- read-fixture [f] #?(:clj (edn/read-string (slurp (io/file f)))))

(defn- commit [store text tree-id]
  (rev/apply-plan store (plan/markdown->plan h text {:tree-id tree-id :name "doc" :actor "jp" :timestamp TS :base (:head store)})))

(deftest store-round-trip
  (let [md (slurp-fixture "test/fixtures/markdown/profile.md")
        r (commit (rev/empty-store h {"jp" :all}) md T)
        s (:store r)]
    (is (= (ast/parse h md) (plan/store->ast s T)))
    (is (= (render/render h (ast/parse h md)) (plan/store->markdown h s T)))
    (is (= md (plan/store->markdown h s T)) "profile.md is canonical: the store reproduces it byte-for-byte")
    (testing "nodes are the §8 shapes"
      (let [nodes (filter :class (vals (:objects s)))]
        (is (every? #(contains? #{:block :text :opaque} (:kind %)) nodes))
        (is (every? #(= :markdown (get-in % [:content :format])) (filter #(= :block (:kind %)) nodes)))))))

(deftest text-leaves-are-shared
  (let [s1 (:store (commit (rev/empty-store h {"jp" :all}) "# A\n\nEl cielo es azul.\n" T))
        s2 (:store (commit s1 "# B\n\nEl cielo es *azul*.\n" U))
        leaf (:id (node/make h :sign :text {:text "El cielo es azul."}))]
    (is (rev/get-object s2 leaf))
    (is (= 1 (count (filter #(= "El cielo es azul." (get-in % [:content :text])) (filter :class (vals (:objects s2)))))) "one leaf node")
    (is (sldb.kernel.tree/contains-node? (get-in s2 [:trees T]) leaf))
    (is (sldb.kernel.tree/contains-node? (get-in s2 [:trees U]) leaf) "same text with different marks shares the leaf; the marks differ on the block")))

(deftest not-a-document
  (let [s (:store (rev/apply-plan (rev/empty-store h {"jp" :all})
                                  {:plan/version 1 :base nil :actor "jp" :engines {} :timestamp TS
                                   :ops [{:op :add-node :node {:class :sign :kind :text :content {:text "r"}} :as :r}
                                         {:op :new-tree :tree {:kind :document :name "x"} :id T :root :r}]}))]
    (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) #"store->ast" (plan/store->ast s T)))
    (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) #"unknown tree" (plan/store->ast s U)))))

(deftest golden-outside-profile
  (let [md (slurp-fixture "test/fixtures/markdown/outside-profile.md")
        expected (read-fixture "test/fixtures/markdown/outside-profile.report.edn")
        a (ast/parse h md)]
    (is (= expected (report/report h a)))
    (testing "opaque regions re-emit verbatim and the document is stable"
      (let [r (render/render h a)]
        (is (= a (ast/parse h r)))
        (doseq [{:keys [path]} (:opaque-regions expected)]
          (let [b (reduce (fn [node i] (nth (:children node) i)) a path)]
            (is (clojure.string/includes? r (:blob b)))))))))

(deftest coverage
  (is (= 1.0 (:coverage (report/report h (ast/parse h "# a\n\nb *c*\n")))))
  (let [rep (report/report h (ast/parse h "> <div>\n> x\n\npara\n"))]
    (is (= [{:path [0 0] :format "markdown/html" :graphemes 7}] (:opaque-regions rep)))
    (is (= 3 (:blocks rep)))
    (is (< 0.0 (:coverage rep) 1.0))))
