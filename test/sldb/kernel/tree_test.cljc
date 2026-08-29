(ns sldb.kernel.tree-test
  (:require [clojure.test :refer [deftest is testing]]
            [sldb.kernel.test-util :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.edn :as edn]
            [sldb.host.default :as host]
            [sldb.host.ulid :as ulid]
            [sldb.kernel.node :as node]
            [sldb.kernel.tree :as tree]
            [sldb.kernel.generators :as g]
            #?(:clj [clojure.java.io :as io])))

(def h host/host)
(def T "01ARZ3NDEKTSV4RRFFQ69G5FAV")

(defn- text [s] (:id (node/make h :sign :text {:text s})))

(defn- sample-tree []
  ;; root -> [a b], a -> [a1 a2]
  (let [r (text "root") a (text "a") b (text "b") a1 (text "a1") a2 (text "a2")]
    {:ids {:r r :a a :b b :a1 a1 :a2 a2}
     :tree (-> (tree/create h :document "doc" r T)
               (tree/add-child r a 0)
               (tree/add-child r b 1)
               (tree/add-child a a1 0)
               (tree/add-child a a2 1)
               (->> (tree/commit h)))}))

(deftest ulid-ids-are-nominal-invariant-13
  (is (tree/ulid? (ulid/ulid)))
  (is (not= (ulid/ulid) (ulid/ulid)))
  (is (= T (:tree (:tree (sample-tree)))))
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) (tree/create h :document "x" (text "r") "not-a-ulid"))))

(deftest descriptor-is-a-cas-object
  (let [{:keys [tree]} (sample-tree)]
    (is (= 64 (count (tree/descriptor-id h (:descriptor tree)))))
    (is (= #{:tree :kind :name :root} (set (keys (:descriptor tree)))))))

(deftest golden-fixture-trees-edn
  (let [fx #?(:clj (edn/read-string (slurp (io/file "test/fixtures/trees.edn")))
              :cljs (edn/read-string (.readFileSync (js/require "fs") "test/fixtures/trees.edn" "utf8")))
        ids (mapv (fn [{:keys [class kind content]}] (:id (node/make h class kind content))) (:nodes fx))
        {:keys [kind root children]} (:tree fx)
        t (reduce (fn [t [p kids]]
                    (reduce (fn [t [i c]] (tree/add-child t (ids p) (ids c) i)) t (map-indexed vector kids)))
                  (tree/create h kind "fixture" (ids root) (:tree-id fx))
                  ;; parents in breadth-first order of aliases so parents exist before children
                  (sort-by (fn [[p _]] p) children))
        t (tree/commit h t)]
    (is (= (:root (:expected fx)) (tree/merkle-root t)))
    (doseq [[alias th] (:objects (:expected fx))]
      (is (= th (get-in t [:objects (ids alias)])) (str "tree object of alias " alias)))))

(deftest sibling-order-enters-the-hash-invariant-14
  (let [{:keys [ids tree]} (sample-tree)
        swapped (-> tree (tree/move (:b ids) (:r ids) 0) (->> (tree/commit h)))]
    (is (not= (tree/merkle-root tree) (tree/merkle-root swapped)))
    (is (= (get-in tree [:objects (:a ids)]) (get-in swapped [:objects (:a ids)])) "untouched subtree keeps its object")))

(deftest dirty-set-is-exactly-the-path-to-root
  (let [{:keys [ids tree]} (sample-tree)
        n (text "a3")
        t (tree/add-child tree (:a ids) n 2)]
    (is (= #{n (:a ids) (:r ids)} (:dirty t)))
    (let [c (tree/commit h t)]
      (is (empty? (:dirty c)))
      (is (= (get-in tree [:objects (:b ids)]) (get-in c [:objects (:b ids)])) "sibling subtree object reused")
      (is (not= (get-in tree [:objects (:a ids)]) (get-in c [:objects (:a ids)])))
      (is (not= (tree/merkle-root tree) (tree/merkle-root c))))))

(deftest one-node-in-two-trees-one-parent-per-tree-invariant-5
  (let [{:keys [ids tree]} (sample-tree)
        other (-> (tree/create h :taxonomy "tax" (text "tax-root") "01ARZ3NDEKTSV4RRFFQ69G5FAW")
                  (tree/add-child (text "tax-root") (:a ids) 0)
                  (->> (tree/commit h)))]
    (is (tree/contains-node? tree (:a ids)))
    (is (tree/contains-node? other (:a ids)))
    (is (not= (get-in tree [:objects (:a ids)]) (get-in other [:objects (:a ids)])) "independent tree hashes")
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) (tree/add-child tree (:b ids) (:a ids) 0)) "second parent in the same tree rejected")))

(deftest replace-keeps-order-and-subtree
  (let [{:keys [ids tree]} (sample-tree)
        n (text "a'")
        t (tree/commit h (tree/replace-node tree (:a ids) n))]
    (is (= [n (:b ids)] (get-in t [:children (:r ids)])))
    (is (= [(:a1 ids) (:a2 ids)] (get-in t [:children n])))
    (is (tree/valid? t))))

(deftest remove-and-move-keep-validity
  (let [{:keys [ids tree]} (sample-tree)]
    (is (tree/valid? (tree/commit h (tree/remove-subtree tree (:a ids)))))
    (is (tree/valid? (tree/commit h (tree/move tree (:a1 ids) (:b ids) 0))))
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) (tree/move tree (:a ids) (:a1 ids) 0)) "cycle rejected")
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) (tree/merkle-root (tree/remove-subtree tree (:a ids)))) "dirty tree has no root hash")))

(defspec generated-trees-are-valid-and-commit-deterministically-invariant-4 100
  (prop/for-all [t g/gen-tree]
    (let [c1 (tree/commit h t) c2 (tree/commit h t)]
      (and (tree/valid? c1)
           (= (tree/merkle-root c1) (tree/merkle-root c2))
           (= (:objects c1) (:objects c2))))))

(defspec changing-one-leaf-changes-only-its-path 100
  (prop/for-all [t g/gen-tree]
    (let [c (tree/commit h t)
          leaves (filter #(empty? (get-in c [:children %])) (tree/nodes c))
          leaf (rand-nth (vec leaves))]
      (if (= leaf (tree/root c))
        true
        (let [new (text (str "leaf-" (rand-int 1000000)))
              c2 (tree/commit h (tree/replace-node c leaf new))
              path (set (cons new (tree/ancestors c2 new)))
              unchanged (remove path (tree/nodes c2))]
          (and (every? #(not= (get-in c [:objects %]) (get-in c2 [:objects %])) (disj path new))
               (every? #(= (get-in c [:objects %]) (get-in c2 [:objects %])) unchanged)))))))

(defspec reordering-siblings-changes-parent-hash 100
  (prop/for-all [t g/gen-tree]
    (let [c (tree/commit h t)
          parents (filter #(>= (count (get-in c [:children %])) 2) (tree/nodes c))]
      (if (empty? parents)
        true
        (let [p (rand-nth (vec parents))
              [x & _] (get-in c [:children p])
              n (count (get-in c [:children p]))
              c2 (tree/commit h (tree/move c x p (dec n)))]
          (not= (get-in c [:objects p]) (get-in c2 [:objects p])))))))
