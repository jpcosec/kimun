(ns sldb.surface.markdown.drift-test
  "Exit criterion of milestone 5b (docs/v2/02 §9 row 5b): a .md edited outside
   the kernel leaves anchors orphan, reconciliation reclassifies them as
   drifted by naming the substitute, and accepting the proposal leaves them
   superseded and re-anchored."
  (:require [clojure.test :refer [deftest is testing]]
            [sldb.host.default :as host]
            [sldb.kernel.anchor :as anchor]
            [sldb.kernel.reconcile :as rec]
            [sldb.kernel.revision :as rev]
            [sldb.kernel.tree :as tree]
            [sldb.surface.markdown.plan :as mdp]))

(def h host/host)
(def caps {"jp" :all "surface/markdown" :all})
(def T "01ARZ3NDEKTSV4RRFFQ69G5FAV")
(def TS "2026-08-29T12:00:00.000Z")

(def before "El cielo es azul.\n\nLa hierba es verde.\n")
(def after  "El cielo es azul.\n\nLa hierba es parda.\n")

(defn- world []
  (let [s0 (rev/empty-store h caps)
        r1 (rev/apply-plan s0 (mdp/markdown->plan h before {:tree-id T :name "doc" :actor "jp" :timestamp TS :base nil}))
        s1 (:store r1) rev1 (:revision-id r1)
        old-leaf (tree/node-at {:root (rev/tree-at s1 rev1 T)} [1 0])
        first-leaf (tree/node-at {:root (rev/tree-at s1 rev1 T)} [0 0])
        r2 (rev/apply-plan s1 {:plan/version 1 :base rev1 :actor "jp" :engines {} :timestamp TS
                               :ops [{:op :add-edge :edge {:type :reference :from first-leaf :to old-leaf
                                                           :evidence {:ref-hash old-leaf :actor "jp"}} :as :ref}]})]
    {:store (:store r2) :rev (:revision-id r2) :ref (get (:aliases r2) :ref)
     :old-leaf old-leaf :first-leaf first-leaf}))

(deftest external-edit-orphans-the-anchor
  (let [{:keys [store rev ref old-leaf first-leaf]} (world)
        _ (is (= :intact (:state (anchor/state store rev ref))))
        r (rev/apply-plan store (mdp/markdown->update-plan h store T after
                                                           {:actor "jp" :timestamp TS :base rev}))
        s' (:store r) rev' (:revision-id r)]
    (testing "the surface records no succession: that is what an external edit is"
      (is (= [] (:superseded r)))
      (is (empty? (filter (fn [eid] (= :supersedes (:type (rev/get-object s' eid))))
                          (:edges s')))))
    (testing "what survived the edit is still placed and intact"
      (is (= first-leaf (tree/node-at {:root (rev/tree-at s' rev' T)} [0 0])))
      (is (= :intact (:state (anchor/endpoint-state s' rev' first-leaf)))))
    (testing "what the edit replaced is orphan"
      (is (= :orphan (:state (anchor/endpoint-state s' rev' old-leaf))))
      (is (= :orphan (:state (anchor/state s' rev' ref))))
      (is (= [ref] (:orphans (anchor/report s' rev')))))
    (testing "reconciliation names the substitute by position, and accepting it re-anchors"
      (let [new-leaf (tree/node-at {:root (rev/tree-at s' rev' T)} [1 0])
            [p & more] (rec/proposals s' rev' {:base rev})]
        (is (nil? more) "one proposal, the best one for the single orphan")
        (is (= {:old old-leaf :candidate new-leaf :method :position :confidence 1.0}
               (select-keys p [:old :candidate :method :confidence])))
        (is (= [ref] (:edges p)))
        (let [r3 (rev/apply-plan s' (rec/accept-plan s' rev' p {:actor "jp" :timestamp TS}))
              s3 (:store r3) rev3 (:revision-id r3)]
          (is (= [[old-leaf new-leaf]] (:superseded r3)))
          (is (= :superseded (:state (anchor/state s3 rev3 ref))))
          (is (= 1 (count (filter (fn [s] (and (= :reference (:type s))
                                               (= new-leaf (get-in s [:to :node]))
                                               (= :intact (:state s))))
                                  (anchor/states s3 rev3))))
              "the reference followed the successor (§6.1)")
          (is (= [] (:orphans (anchor/report s3 rev3)))))))))

(deftest update-plan-keeps-the-document-readable-and-refuses-a-changed-root
  (let [{:keys [store rev]} (world)
        r (rev/apply-plan store (mdp/markdown->update-plan h store T after
                                                           {:actor "jp" :timestamp TS :base rev}))]
    (is (= after (mdp/store->markdown h (:store r) T)) "render(store) == the edited file")
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs :default)
                 (mdp/markdown->update-plan h (:store r) "01ARZ3NDEKTSV4RRFFQ69G5FAW" after
                                            {:actor "jp" :timestamp TS :base (:revision-id r)})))))
