(ns kimun.surface.markdown.drift-test
  "An external edit of a .md produces an update plan that keeps the document readable."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.kernel.revision :as rev]
            [kimun.kernel.tree :as tree]
            [kimun.surface.markdown.plan :as mdp]))

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

(deftest update-plan-keeps-the-document-readable-and-refuses-a-changed-root
  (let [{:keys [store rev]} (world)
        r (rev/apply-plan store (mdp/markdown->update-plan h store T after
                                                           {:actor "jp" :timestamp TS :base rev}))]
    (is (= after (mdp/store->markdown h (:store r) T)) "render(store) == the edited file")
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs :default)
                 (mdp/markdown->update-plan h (:store r) "01ARZ3NDEKTSV4RRFFQ69G5FAW" after
                                            {:actor "jp" :timestamp TS :base (:revision-id r)})))))
