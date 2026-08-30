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
  ;; root -> [a b], a -> [a1 a2]   paths: a=[0] b=[1] a1=[0 0] a2=[0 1]
  (let [r (text "root") a (text "a") b (text "b") a1 (text "a1") a2 (text "a2")]
    {:ids {:r r :a a :b b :a1 a1 :a2 a2}
     :tree (->> (-> (tree/create h :document "doc" r T)
                    (tree/add-child [] a 0)
                    (tree/add-child [] b 1)
                    (tree/add-child [0] a1 0)
                    (tree/add-child [0] a2 1))
                (tree/commit h))}))

(deftest ulid-ids-are-nominal-invariant-13
  (is (tree/ulid? (ulid/ulid)))
  (is (not= (ulid/ulid) (ulid/ulid)))
  (is (= T (:tree (:tree (sample-tree)))))
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) (tree/create h :document "x" (text "r") "not-a-ulid"))))

(deftest descriptor-is-a-cas-object
  (let [{:keys [tree]} (sample-tree)]
    (is (= 64 (count (tree/descriptor-id h (:descriptor tree)))))
    (is (= #{:tree :kind :name :root} (set (keys (:descriptor tree)))))))

(deftest positions-and-paths
  (let [{:keys [ids tree]} (sample-tree)]
    (is (= (:r ids) (tree/node-at tree [])))
    (is (= (:a2 ids) (tree/node-at tree [0 1])))
    (is (= [(:a ids) (:b ids)] (tree/children-at tree [])))
    (is (= [[] (:r ids)] (first (tree/positions tree))))
    (is (= 5 (count (tree/positions tree))))
    (is (= [[0 0]] (tree/paths-of tree (:a1 ids))))))

(deftest golden-fixture-trees-edn
  (let [fx #?(:clj (edn/read-string (slurp (io/file "test/fixtures/trees.edn")))
              :cljs (edn/read-string (.readFileSync (js/require "fs") "test/fixtures/trees.edn" "utf8")))
        ids (mapv (fn [{:keys [class kind content]}] (:id (node/make h class kind content))) (:nodes fx))
        {:keys [kind root children]} (:tree fx)
        t (reduce (fn [t [p kids]]
                    (let [path (first (tree/paths-of t (ids p)))]
                      (reduce (fn [t [i c]] (tree/add-child t path (ids c) i)) t (map-indexed vector kids))))
                  (tree/create h kind "fixture" (ids root) (:tree-id fx))
                  (sort-by (fn [[p _]] p) children))
        t (tree/commit h t)]
    (is (= (:root (:expected fx)) (tree/merkle-root t)))
    (doseq [[alias th] (:objects (:expected fx))]
      (is (= th (tree/hash-at t (first (tree/paths-of t (ids alias))))) (str "tree object of alias " alias)))))

(deftest sibling-order-enters-the-hash-invariant-14
  (let [{:keys [tree]} (sample-tree)
        swapped (->> (tree/move tree [1] [] 0) (tree/commit h))]
    (is (not= (tree/merkle-root tree) (tree/merkle-root swapped)))
    (is (= (tree/hash-at tree [0]) (tree/hash-at swapped [1])) "untouched subtree keeps its object")))

(deftest dirty-set-is-exactly-the-path-to-root
  (let [{:keys [tree]} (sample-tree)
        t (tree/add-child tree [0] (text "a3") 2)]
    (is (= #{[] [0] [0 2]} (set (tree/dirty-paths t))) "the new position and its ancestors")
    (let [c (tree/commit h t)]
      (is (not (tree/dirty? c)))
      (is (= (tree/hash-at tree [1]) (tree/hash-at c [1])) "sibling subtree object reused")
      (is (not= (tree/hash-at tree [0]) (tree/hash-at c [0])))
      (is (not= (tree/merkle-root tree) (tree/merkle-root c))))))

(deftest one-node-at-several-positions-and-in-several-trees-invariant-5
  (let [{:keys [ids tree]} (sample-tree)
        a (:a ids)
        twice (->> (tree/add-child tree [] a 2) (tree/commit h))
        other (->> (-> (tree/create h :taxonomy "tax" (text "tax-root") "01ARZ3NDEKTSV4RRFFQ69G5FAW")
                       (tree/add-child [] a 0))
                   (tree/commit h))]
    (is (= [[0] [2]] (tree/paths-of twice a)) "same node at two positions of one tree")
    (is (not= (tree/hash-at twice [0]) (tree/hash-at twice [2])) "different subtrees, different objects")
    (is (= (tree/hash-at other [0]) (tree/hash-at twice [2])) "identical subtree (a leaf) shares one object across trees")
    (is (tree/contains-node? other a))))

(deftest identical-subtrees-share-objects
  (let [i (text "item") p (text "p")
        t (->> (-> (tree/create h :document "d" (text "root") T)
                   (tree/add-child [] i 0) (tree/add-child [0] p 0)
                   (tree/add-child [] i 1) (tree/add-child [1] p 0))
               (tree/commit h))]
    (is (= (tree/hash-at t [0]) (tree/hash-at t [1])))
    (is (= 3 (count (tree/objects t))) "root, item subtree, leaf — the two items are one object")))

(deftest replace-keeps-position-and-subtree
  (let [{:keys [ids tree]} (sample-tree)
        n (text "a'")
        t (tree/commit h (tree/replace-node tree [0] n))]
    (is (= [n (:b ids)] (tree/children-at t [])))
    (is (= [(:a1 ids) (:a2 ids)] (tree/children-at t [0])))
    (is (tree/valid? t))
    (testing "replacing the root re-roots"
      (let [t2 (tree/commit h (tree/replace-node tree [] n))]
        (is (= n (tree/root t2)))
        (is (= n (tree/node-at t2 [])))))))

(deftest detach-and-move-keep-validity
  (let [{:keys [ids tree]} (sample-tree)]
    (is (= [(:b ids)] (tree/children-at (tree/detach tree [0]) [])))
    (is (= [(:a1 ids)] (tree/children-at (tree/move tree [0 0] [1] 0) [1])))
    (testing "moving an earlier sibling under a later one shifts the target path"
      (let [t (tree/move tree [0] [1] 0)]
        (is (= [(:b ids)] (tree/children-at t [])))
        (is (= [(:a ids)] (tree/children-at t [0])))))
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) (tree/move tree [0] [0 0] 0)) "cycle rejected")
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) (tree/detach tree [])) "root cannot be detached")
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) (tree/merkle-root (tree/detach tree [0]))) "dirty tree has no root hash")))

(deftest from-objects-rebuilds-a-committed-tree
  (let [{:keys [tree]} (sample-tree)
        objs (tree/objects tree)
        rebuilt (tree/from-objects (tree/merkle-root tree) objs)]
    (is (= (:root tree) rebuilt))))

(defspec generated-trees-are-valid-and-commit-deterministically-invariant-4 100
  (prop/for-all [t g/gen-tree]
    (let [c1 (tree/commit h t) c2 (tree/commit h t)]
      (and (tree/valid? c1)
           (= (tree/merkle-root c1) (tree/merkle-root c2))
           (= (:root c1) (:root c2))))))

(defspec changing-one-leaf-changes-only-its-path 100
  (prop/for-all [t g/gen-tree]
    (let [c (tree/commit h t)
          leaves (filter (fn [[p _]] (empty? (tree/children-at c p))) (tree/positions c))
          [leaf-path _] (rand-nth (vec leaves))]
      (if (empty? leaf-path)
        true
        (let [new (text (str "leaf-" (rand-int 1000000)))
              c2 (tree/commit h (tree/replace-node c leaf-path new))
              on-path (set (map #(subvec leaf-path 0 %) (range (inc (count leaf-path)))))]
          (every? (fn [[p _]]
                    (if (on-path p)
                      (not= (tree/hash-at c p) (tree/hash-at c2 p))
                      (= (tree/hash-at c p) (tree/hash-at c2 p))))
                  (tree/positions c)))))))

(defspec reordering-siblings-changes-parent-hash 100
  (prop/for-all [t g/gen-tree]
    (let [c (tree/commit h t)
          parents (filter (fn [[p _]] (>= (count (tree/children-at c p)) 2)) (tree/positions c))]
      (if (empty? parents)
        true
        (let [[p _] (rand-nth (vec parents))
              n (count (tree/children-at c p))
              c2 (tree/commit h (tree/move c (conj p 0) p (dec n)))
              kids (fn [t] (map (juxt :node :hash) (:children (tree/position-at t p))))]
          ;; the parent hash changes iff the (node, subtree-hash) sequence changes —
          ;; reordering identical subtrees is a no-op (positions share objects)
          (= (not= (kids c) (kids c2)) (not= (tree/hash-at c p) (tree/hash-at c2 p))))))))
