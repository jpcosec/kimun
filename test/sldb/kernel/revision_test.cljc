(ns sldb.kernel.revision-test
  (:require [clojure.test :refer [deftest is testing]]
            [sldb.kernel.test-util :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.edn :as edn]
            [sldb.host.default :as host]
            [sldb.kernel.node :as node]
            [sldb.kernel.revision :as rev]
            [sldb.kernel.generators :as g]
            #?(:clj [clojure.java.io :as io])))

(def h host/host)
(def caps {"jp" :all "ana" #{{:op :add-node}}})
(def T "01ARZ3NDEKTSV4RRFFQ69G5FAV")
(def TS "2026-08-29T12:00:00.000Z")

(defn- read-fixture [f]
  #?(:clj (edn/read-string (slurp (io/file f)))
     :cljs (edn/read-string (.readFileSync (js/require "fs") f "utf8"))))

(defn- rejected [store p]
  (try (rev/apply-plan store p) nil
       (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e (ex-data e))))

(def base-plan
  {:plan/version 1 :base nil :actor "jp" :engines {} :timestamp TS
   :ops [{:op :add-node :node {:class :sign :kind :block :content {:format :markdown :type :document :attrs {}}} :as :root}
         {:op :add-node :node {:class :sign :kind :text :content {:text "El cielo es azul."}} :as :p1}
         {:op :add-node :node {:class :sign :kind :text :content {:text "La hierba es verde."}} :as :p2}
         {:op :add-node :node {:class :symbol :kind :term :content {:name "cielo" :lang "es"}} :as :sym}
         {:op :add-node :node {:class :fact :kind :context :content {:name "colores"}} :as :w}
         {:op :new-tree :tree {:kind :document :name "doc"} :id "01ARZ3NDEKTSV4RRFFQ69G5FAV" :root :root :as :t}
         {:op :add-edge :edge {:type :ownership :tree :t :from :root :to :p1 :order 0}}
         {:op :add-edge :edge {:type :ownership :tree :t :from :root :to :p2 :order 1}}
         {:op :add-edge :edge {:type :binding :from :p1 :to :sym :evidence {:ref-hash :sym :actor "jp" :context :w}} :as :b1}]})

(defn- applied [] (rev/apply-plan (rev/empty-store h caps) base-plan))

(deftest fixture-tx-001-yields-exactly-the-expected-revision
  (let [fx (read-fixture "test/fixtures/tx-001.edn")
        store (rev/empty-store h (get-in fx [:store :capabilities]))
        {:keys [revision revision-id]} (rev/apply-plan store (:plan fx))]
    (is (= (get-in fx [:expected :revision]) revision) "eight fields")
    (is (= (get-in fx [:expected :revision-id]) revision-id))
    (is (= #{:roots :trees :edges :parents :tx :actor :engines :timestamp} (set (keys revision))))))

(deftest fixture-tx-002-conflict
  (let [fx (read-fixture "test/fixtures/tx-002-conflict.edn")
        store (reduce (fn [s p] (:store (rev/apply-plan s p))) (rev/empty-store h (get-in fx [:store :capabilities])) (:base-plans fx))
        e (rejected store (:concurrent-plan fx))]
    (is (= :plan/conflict (:type e)))
    (is (= (get-in fx [:expected :conflicts]) (get-in e [:conflict-set :conflicts])))
    (testing "a plan touching only a disjoint tree rebases automatically"
      (let [r (rev/apply-plan store (:disjoint-plan fx))]
        (is (= (:base (:disjoint-plan fx)) (:rebased-from r)) "rebased from the stale base")
        (is (= [(:head store)] (get-in r [:revision :parents])) "onto the current head")))))

(deftest apply-is-deterministic-and-all-or-nothing
  (let [s (rev/empty-store h caps)
        r1 (rev/apply-plan s base-plan) r2 (rev/apply-plan s base-plan)]
    (is (= (:revision-id r1) (:revision-id r2)))
    (is (= (set (keys (:objects (:store r1)))) (set (keys (:objects (:store r2))))))
    (is (= {} (:objects s)) "input store untouched")))

(deftest the-seven-checks-reject-the-whole-plan
  (let [{:keys [store aliases]} (applied)
        head (:head store)
        p1 (aliases :p1) root (aliases :root) sym (aliases :sym)
        base (fn [ops] {:plan/version 1 :base head :actor "jp" :engines {} :timestamp TS :ops ops})
        check (fn [p] (:check (rejected store p)))]
    (is (= :base-cas (check (assoc (base []) :base "nope"))))
    (is (= :ids-exist (check (base [{:op :move :tree T :node "deadbeef" :parent root :order 0}]))))
    (is (= :ids-exist (check (base [{:op :remove-edge :edge :unknown}]))))
    (is (= :tree-integrity (check (base [{:op :move :tree T :node root :parent p1 :order 0}]))) "moving the root")
    (is (= :tree-integrity (check (base [{:op :add-edge :edge {:type :ownership :tree T :from root :to p1 :order 0}}]))) "second parent")
    (is (= :evidence-ref-hash (check (base [{:op :add-edge :edge {:type :binding :from p1 :to sym :evidence {:ref-hash root :actor "jp" :context sym}}}]))))
    (is (= :evidence-ref-hash (check (base [{:op :add-edge :edge {:type :binding :from p1 :to sym :evidence {:ref-hash sym :context sym}}}]))) "missing origin")
    (is (= :capability (check (assoc (base [{:op :move :tree T :node p1 :parent root :order 1}]) :actor "ana"))))
    (is (= :capability (check (assoc (base [{:op :add-node :node {:class :sign :kind :text :content {:text "x"}}}]) :actor "nobody"))) "absent actor")
    (is (= :opaque-replace-only (check (base [{:op :add-node :node {:class :sign :kind :text :content {:text "x"}} :patch {}}]))))
    (is (= :pure-data (check (assoc (base []) :timestamp "now"))))
    (is (= head (:head store)) "no state change after rejections")))

(deftest replace-keeps-order-records-supersedes-and-re-anchors
  (let [{:keys [store aliases]} (applied)
        p1 (aliases :p1) sym (aliases :sym) b1 (aliases :b1) root (aliases :root)
        p1' (:id (node/make h :sign :text {:text "El cielo es celeste."}))
        r (rev/apply-plan store {:plan/version 1 :base (:head store) :actor "jp" :engines {} :timestamp TS
                                 :ops [{:op :add-node :node {:class :sign :kind :text :content {:text "El cielo es celeste."}}}
                                       {:op :replace :tree T :old p1 :new p1'}]})
        s' (:store r)
        kids (get-in s' [:trees T :children root])
        sup (first (filter #(= :supersedes (:type %)) (map #(rev/get-object s' %) (:edges-added r))))
        followers (filter #(and (= :binding (:type %)) (= p1' (:from %))) (map #(rev/get-object s' %) (:edges s')))]
    (is (= p1' (first kids)) "new node takes the old order")
    (is (= [[p1 p1']] (:superseded r)))
    (is (= {:actor "jp"} (:evidence sup)))
    (is (= 1 (count followers)) "binding from the old node is followed to the successor")
    (is (contains? (:edges s') b1) "the original binding stays (superseded, not deleted)")
    (let [d (rev/diff s' (:head store) (:head s'))]
      (is (= [p1'] (get-in d [:trees T :added])))
      (is (= [p1] (get-in d [:trees T :removed])))
      (is (= [[p1 p1']] (:superseded d)))
      (is (= 2 (count (get-in d [:edges :added])))))))

(deftest adding-an-existing-edge-is-a-no-op
  (let [{:keys [store aliases]} (applied)
        p1 (aliases :p1) sym (aliases :sym) w (aliases :w)
        r (rev/apply-plan store {:plan/version 1 :base (:head store) :actor "jp" :engines {} :timestamp TS
                                 :ops [{:op :add-edge :edge {:type :binding :from p1 :to sym :evidence {:ref-hash sym :actor "jp" :context w}}}]})]
    (is (= [] (:edges-added r)))
    (is (= (:edges store) (:edges (:store r))))))

(deftest heads-advance-only-through-a-commit-invariant-3
  (let [{:keys [store]} (applied)]
    (is (= (:head store) (get-in store [:heads T])))
    (is (= 1 (count (:revisions store))))
    (is (every? (fn [[id obj]] (or (= id (sldb.kernel.canon/digest h obj)) (:id obj))) (:objects store))
        "every object is content-addressed (invariant 17)")))

(defspec generated-plans-apply-deterministically 60
  (prop/for-all [p g/gen-valid-plan]
    (let [s (rev/empty-store h caps)
          r1 (rev/apply-plan s p) r2 (rev/apply-plan s p)]
      (and (= (:revision-id r1) (:revision-id r2))
           (= (:revision r1) (:revision r2))
           (= 8 (count (:revision r1)))))))

(defspec generated-plan-sequences-chain-revisions-invariant-15 30
  (prop/for-all [ps g/gen-plan-sequence]
    (let [s (reduce (fn [s p] (:store (rev/apply-plan s (assoc p :base (:head s))))) (rev/empty-store h caps) ps)]
      (and (= (count ps) (count (:revisions s)))
           (every? (fn [[id r]] (= id (sldb.kernel.canon/digest h r))) (:revisions s))))))
