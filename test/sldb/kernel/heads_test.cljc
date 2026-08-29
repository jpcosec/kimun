(ns sldb.kernel.heads-test
  (:require [clojure.test :refer [deftest is testing]]
            [sldb.host.default :as host]
            [sldb.kernel.revision :as rev]
            [sldb.kernel.heads :as heads]))

(def h host/host)
(def TS "2026-08-29T12:00:00.000Z")
(def T "01ARZ3NDEKTSV4RRFFQ69G5FAV")
(def U "01ARZ3NDEKTSV4RRFFQ69G5FAW")

(defn- doc-plan [base tid text]
  {:plan/version 1 :base base :actor "jp" :engines {} :timestamp TS
   :ops [{:op :add-node :node {:class :sign :kind :block :content {:format :markdown :type :document :attrs {}}} :as :root}
         {:op :add-node :node {:class :sign :kind :text :content {:text text}} :as :p}
         {:op :new-tree :tree {:kind :document :name text} :id tid :root :root :as :t}
         {:op :add-edge :edge {:type :ownership :tree :t :from :root :to :p :order 0}}]})

(deftest cas-per-entry
  (let [s (rev/empty-store h {"jp" :all})
        s1 (:store (rev/apply-plan s (doc-plan nil T "a")))
        ok (heads/cas s1 {T (:head s1)} {T "r2"} "r2")
        stale (heads/cas s1 {T "old"} {T "r2"} "r2")]
    (is (= "r2" (:head ok)))
    (is (= {:stale {T "old"} :heads {T (:head s1)}} (:conflict stale)))))

(deftest commit-through-atom-and-conflict-set
  (let [ref (atom (rev/empty-store h {"jp" :all}))
        r1 (heads/commit! ref (doc-plan nil T "a"))
        base (:revision-id r1)
        root (get-in r1 [:aliases :root]) p (get-in r1 [:aliases :p])
        ;; writer A edits the children of root
        _ (heads/commit! ref {:plan/version 1 :base base :actor "jp" :engines {} :timestamp TS
                              :ops [{:op :add-node :node {:class :sign :kind :text :content {:text "b"}} :as :q}
                                    {:op :add-edge :edge {:type :ownership :tree T :from root :to :q :order 1}}]})
        ;; writer B, still on the old base, edits the same parent
        e (try (heads/commit! ref {:plan/version 1 :base base :actor "jp" :engines {} :timestamp TS
                                   :ops [{:op :add-node :node {:class :sign :kind :text :content {:text "c"}} :as :r}
                                         {:op :add-edge :edge {:type :ownership :tree T :from root :to :r :order 0}}]})
               nil
               (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e e))
        cs (heads/conflict-set e)]
    (is (some? cs))
    (is (= base (:base cs)))
    (is (= (:head @ref) (:head cs)))
    (is (= [{:tree T :node root :kind :same-parent-edit}] (:conflicts cs)))
    (testing "writer C on the old base but touching another tree rebases and commits"
      (let [r (heads/commit! ref (doc-plan base U "other"))]
        (is (= base (:rebased-from r)))
        (is (= 3 (count (:revisions @ref))))
        (is (= (:head @ref) (get-in @ref [:heads U])))
        (is (not= (:head @ref) (get-in @ref [:heads T])) "T's head is the last revision that touched it")))))
