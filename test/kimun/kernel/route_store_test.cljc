(ns kimun.kernel.route-store-test
  "K2a C2a: a cached tree can carry a full route entry in :register-tree;
   :remove-route removes the tracked route without deleting the tree; the root
   lives in heads and is rebuilt by replay from :route values. No surface/docs."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.host.fs-store :as fs]
            [kimun.kernel.node :as node]
            [kimun.kernel.revision :as rev]
            [kimun.kernel.route :as route]
            [kimun.kernel.store :as store]
            [kimun.test-util :as ktu]))

(def h host/host)
(def T "01ARZ3NDEKTSV4RRFFQ69G5FAV")
(def TS "2026-08-29T12:00:00.000Z")

(defn- root-node []
  (node/make h :sign :block {:format :markdown :type :document :attrs {}}))

(defn- route-entry [fp]
  {:path "docs/a.md" :fingerprint fp :class "C"
   :projection-hash "projection" :content-hash "content"})

(defn- register-plan [base root tree-id route]
  (let [root-id (:id root)
        ext (node/make h :sign :external
                       {:locator {:kind :file :path "docs/a.md"}
                        :sample "" :fingerprint "sha-256:1111111111111111111111111111111111111111111111111111111111111111"})
        ext-id (:id ext)]
    {:plan/version 1 :base base :actor "jp" :engines {} :timestamp TS
     :ops [{:op :add-node :node root :as :r}
           {:op :register-tree
            :tree {:kind :document :name "d" :class "C" :content-hash "content" :cached? true
                   :root root-id}
            :id tree-id :root root-id :merkle-root "projection"
            :route route :as :t}
           {:op :add-node :node ext :as :ext}
           {:op :add-edge :edge {:type :reference :from root-id :to ext-id
                                 :evidence {:ref-hash ext-id :actor "jp"}}}]}))

(defn- fresh []
  (let [dir (str (ktu/temp-dir "kimun-route-store-"))
        b (fs/backend dir)
        s0 (store/init! b h {"jp" :all})]
    [dir b s0]))

(deftest register-route-writes-the-root-into-the-heads
  (let [[_ b s0] (fresh)
        r (root-node)
        s1 (:store (store/commit! b s0 (register-plan nil r T (route-entry "fp1"))))
        entry (route/entry h (assoc (route-entry "fp1") :tree-id T))
        opened (store/open b h)]
    (is (some? (:routes-root s1)))
    (is (= (select-keys (store/read-heads b) [:routes-root :routes-object])
           (select-keys s1 [:routes-root :routes-object])) "the root is durable in heads.edn")
    (is (= (:routes-root s1) (:routes-root opened)) "open reads the durable root")
    (is (= entry (get (:routes opened) "docs/a.md")) "open reads the route entry from the tree")
    (is (= 1 (rev/tree-count s1)) "the tree remains")))

(deftest update-route-replaces-entry-and-root
  (let [[_ b s0] (fresh)
        r (root-node)
        s1 (:store (store/commit! b s0 (register-plan nil r T (route-entry "fp1"))))
        s2 (:store (store/commit! b s1 (register-plan (:head s1) r T (route-entry "fp2"))))
        e2 (route/entry h (assoc (route-entry "fp2") :tree-id T))]
    (is (not= (:routes-root s1) (:routes-root s2)))
    (is (= e2 (get (:routes s2) "docs/a.md")))
    (is (= e2 (get (:routes (store/open b h)) "docs/a.md")))
    (is (= 1 (rev/tree-count s2)))))

(deftest remove-route-does-not-delete-the-tree
  (let [[_ b s0] (fresh)
        r (root-node)
        s1 (:store (store/commit! b s0 (register-plan nil r T (route-entry "fp1"))))
        p {:plan/version 1 :base (:head s1) :actor "jp" :engines {} :timestamp TS
           :ops [{:op :remove-route :tree T :path "docs/a.md"}]}
        s2 (:store (store/commit! b s1 p))]
    (is (nil? (get (:routes s2) "docs/a.md")))
    (is (not= (:routes-root s1) (:routes-root s2)) "the root reflects the exit")
    (is (= 1 (rev/tree-count s2)) "untracking does not detach the tree")
    (is (nil? (get (:routes (store/open b h)) "docs/a.md")))
    (is (= 1 (rev/tree-count (store/open b h))))))

(deftest replay-rebuilds-routes-from-plan-route-values
  (let [[_ b s0] (fresh)
        r (root-node)
        s1 (:store (store/commit! b s0 (register-plan nil r T (route-entry "fp1"))))
        replayed (store/replay-open b h)]
    (is (= (get (:routes s1) "docs/a.md") (get (:routes replayed) "docs/a.md")) "replay reconstructs from :route in the plan")
    (is (= (:routes-root s1) (:routes-root replayed)))))

(deftest route-coverage-reports-complete-and-partial-stores
  (let [[_ b s0] (fresh)
        r (root-node)
        s1 (:store (store/commit! b s0 (register-plan nil r T (route-entry "fp1"))))
        t2 "01ARZ3NDEKTSV4RRFFQ69G5FAW"
        root2 (node/make h :sign :block {:format :markdown :type :document :attrs {:name "other"}})
        s2 (:store (store/commit! b s1 (register-plan (:head s1) root2 t2 nil)))
        c1 (store/route-coverage s1)
        c2 (store/route-coverage s2)]
    (is (= {:complete? true :cached-trees 1 :routed-trees 1
            :missing-trees [] :orphan-routes []}
           (select-keys c1 [:complete? :cached-trees :routed-trees :missing-trees :orphan-routes])))
    (is (false? (:complete? c2)))
    (is (= 2 (:cached-trees c2)))
    (is (= 1 (:routed-trees c2)))
    (is (= [t2] (:missing-trees c2)))
    (let [orphan (assoc s1 :routes (assoc (:routes s1) "ghost.md"
                                          (assoc (route-entry "fp1") :path "ghost.md" :tree-id t2)))]
      (is (false? (:complete? (store/route-coverage orphan))))
      (is (= ["ghost.md"] (:orphan-routes (store/route-coverage orphan)))))))

(deftest untracking-a-file-does-not-leave-a-partial-route-root
  (let [[_ b s0] (fresh)
        r (root-node)
        s1 (:store (store/commit! b s0 (register-plan nil r T (route-entry "fp1"))))
        anchor (first (filter #(let [e (rev/get-object s1 %)]
                                 (and (= :reference (:type e))
                                      (= :external (:kind (rev/get-object s1 (:to e))))))
                              (rev/active-edges s1)))
        p {:plan/version 1 :base (:head s1) :actor "jp" :engines {} :timestamp TS
           :ops [{:op :remove-route :tree T :path "docs/a.md"}
                 {:op :remove-edge :edge anchor}]}
        s2 (:store (store/commit! b s1 p))]
    (is (= {:complete? true :cached-trees 0 :routed-trees 0
            :missing-trees [] :orphan-routes []}
           (select-keys (store/route-coverage s2)
                        [:complete? :cached-trees :routed-trees :missing-trees :orphan-routes])))
    (is (= 1 (rev/tree-count s2)) "the historical tree remains in the store")))
