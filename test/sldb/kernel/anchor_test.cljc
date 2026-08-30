(ns sldb.kernel.anchor-test
  (:require [clojure.test :refer [deftest is testing]]
            [sldb.host.default :as host]
            [sldb.kernel.anchor :as anchor]
            [sldb.kernel.revision :as rev]))

(def h host/host)
(def caps {"jp" :all})
(def T "01ARZ3NDEKTSV4RRFFQ69G5FAV")
(def TS "2026-08-29T12:00:00.000Z")
(def zeros {:intact 0 :superseded 0 :orphan 0})
(def fp (str "sha-256:" (apply str (repeat 64 "0"))))

(defn- plan [base ops] {:plan/version 1 :base base :actor "jp" :engines {} :timestamp TS :ops ops})

(def seed
  (plan nil
        [{:op :add-node :node {:class :sign :kind :block :content {:format :markdown :type :document :attrs {}}} :as :root}
         {:op :add-node :node {:class :sign :kind :text :content {:text "El cielo es azul."}} :as :p1}
         {:op :add-node :node {:class :sign :kind :text :content {:text "La hierba es verde."}} :as :p2}
         {:op :add-node :node {:class :symbol :kind :term :content {:name "cielo" :lang "es"}} :as :sym}
         {:op :add-node :node {:class :fact :kind :context :content {:name "colores"}} :as :w}
         {:op :add-node :node {:class :sign :kind :external
                               :content {:locator {:kind :file :path "docs/x.pdf"} :sample "Resumen" :fingerprint fp}} :as :ext}
         {:op :new-tree :tree {:kind :document :name "doc"} :id T :root :root :as :t}
         {:op :add-edge :edge {:type :ownership :tree :t :parent [] :to :p1 :order 0}}
         {:op :add-edge :edge {:type :ownership :tree :t :parent [] :to :p2 :order 1}}
         {:op :add-edge :edge {:type :ownership :tree :t :parent [] :to :ext :order 2}}
         {:op :add-edge :edge {:type :reference :from :p1 :to :p2 :evidence {:ref-hash :p2 :actor "jp"}} :as :r1}
         {:op :add-edge :edge {:type :binding :from :p1 :to :sym :evidence {:ref-hash :sym :actor "jp" :context :w}} :as :b1}]))

(defn- world
  "Seed plus a :span over p1 anchored by a reference from p2."
  []
  (let [r0 (rev/apply-plan (rev/empty-store h caps) seed)
        a (:aliases r0)
        r1 (rev/apply-plan (:store r0)
                           (plan (:head (:store r0))
                                 [{:op :add-node :node {:class :sign :kind :span :content {:leaf (a :p1) :range [0 3]}} :as :sp}
                                  {:op :add-edge :edge {:type :reference :from (a :p2) :to :sp
                                                        :evidence {:ref-hash :sp :actor "jp"}} :as :r2}]))]
    {:store (:store r1) :rev (:revision-id r1) :a (merge a (:aliases r1))}))

(defn- state-of [{:keys [store rev a]} k] (:state (anchor/state store rev (a k))))

;; ---------------------------------------------------------------- §6.2 states

(deftest anchors-are-the-five-cross-layer-types
  (is (= #{:reference :binding :projection :semantic :derived} anchor/anchor-types))
  (is (not (contains? anchor/anchor-types :ownership)))
  (is (not (contains? anchor/anchor-types :supersedes))))

(deftest a-placed-referent-is-intact
  (let [w (world)]
    (is (= :intact (state-of w :r1)) "reference between two placed leaves")
    (is (= :intact (state-of w :b1)) "binding to a symbol that lives in no tree (§6.2, first slice)")
    (is (= :intact (state-of w :r2)) "reference to a span whose leaf is placed and whose range fits")
    (is (= 3 (:anchors (anchor/report (:store w) (:rev w)))))))

(deftest detaching-the-referent-orphans-the-anchor
  (let [{:keys [store rev a]} (world)
        r (rev/apply-plan store (plan rev [{:op :detach :tree T :at [1]}]))
        w' {:store (:store r) :rev (:revision-id r) :a a}]
    (is (= :orphan (state-of w' :r1)) "the :to endpoint is in no tree any more")
    (is (= :orphan (state-of w' :r2)) "the :from endpoint is in no tree any more")
    (is (= :intact (state-of w' :b1)) "an untouched anchor does not move")
    (is (= (sort [(a :r1) (a :r2)]) (:orphans (anchor/report (:store r) (:revision-id r))))
        "and the report lists them, by ascending edge id")))

(deftest a-span-whose-leaf-is-replaced-is-orphan-and-its-leaf-is-superseded
  (let [{:keys [store rev a]} (world)
        r (rev/apply-plan store (plan rev [{:op :add-node :node {:class :sign :kind :text :content {:text "El cielo es gris."}} :as :p1b}
                                           {:op :replace :tree T :at [0] :new :p1b}]))
        s' (:store r) rev' (:revision-id r)]
    (is (= :superseded (:state (anchor/endpoint-state s' rev' (a :p1)))))
    (is (= [(get (:aliases r) :p1b)] (:successors (anchor/endpoint-state s' rev' (a :p1)))))
    (is (= :orphan (:state (anchor/endpoint-state s' rev' (a :sp))))
        "the stand-off address over the replaced leaf no longer resolves")
    (is (= :orphan (:state (anchor/state s' rev' (a :r2)))))))

(deftest a-span-whose-range-runs-past-its-leaf-is-orphan
  (let [{:keys [store rev a]} (world)
        r (rev/apply-plan store (plan rev [{:op :add-node :node {:class :sign :kind :span :content {:leaf (a :p1) :range [0 99]}} :as :big}]))]
    (is (= :orphan (:state (anchor/endpoint-state (:store r) (:revision-id r) (get (:aliases r) :big)))))))

(deftest an-endpoint-that-is-not-in-the-pool-is-orphan
  (let [{:keys [store rev]} (world)]
    (is (= :orphan (:state (anchor/endpoint-state store rev "no-such-node"))))))

;; ---------------------------------------------------------------- §6.1 supersedes trigger

(deftest adding-a-supersedes-edge-re-anchors-and-records-the-pair
  (let [{:keys [store rev a]} (world)
        ;; the shape an accepted reconciliation has: the successor is already in the
        ;; tree (an external edit put it there) and the actor then records the succession
        r (rev/apply-plan store (plan rev [{:op :add-node :node {:class :sign :kind :text :content {:text "La hierba es parda."}} :as :p2b}
                                           {:op :detach :tree T :at [1]}
                                           {:op :add-edge :edge {:type :ownership :tree T :parent [] :to :p2b :order 1}}
                                           {:op :add-edge :edge {:type :supersedes :from :p2b :to (a :p2) :evidence {:actor "jp"}} :as :sup}]))
        s' (:store r) rev' (:revision-id r) p2b (get (:aliases r) :p2b)]
    (is (= [[(a :p2) p2b]] (:superseded r)) "the [old new] pair reaches the transaction result")
    (is (= :superseded (:state (anchor/state s' rev' (a :r1)))) "the old reference is superseded, not deleted")
    (testing "reference and binding follow the successor at either endpoint (§6.1)"
      (let [ss (anchor/states s' rev')
            followed (filter (fn [s] (and (= :reference (:type s))
                                          (= p2b (get-in s [:to :node]))
                                          (= (a :p1) (get-in s [:from :node]))))
                             ss)]
        (is (= 1 (count followed)))
        (is (= :intact (:state (first followed))))))
    (testing "the idempotent re-add is a no-op and never re-anchors again"
      (let [r2 (rev/apply-plan s' (plan rev' [{:op :add-edge :edge {:type :supersedes :from p2b :to (a :p2) :evidence {:actor "jp"}}}]))]
        (is (= [] (:superseded r2)))
        (is (= (:edges s') (:edges (:store r2))))))))

(deftest two-successors-make-the-chain-ambiguous
  (let [{:keys [store rev a]} (world)
        r (rev/apply-plan store (plan rev [{:op :add-node :node {:class :sign :kind :text :content {:text "La hierba es parda."}} :as :x}
                                           {:op :add-node :node {:class :sign :kind :text :content {:text "La hierba es seca."}} :as :y}
                                           {:op :add-edge :edge {:type :supersedes :from :x :to (a :p2) :evidence {:actor "jp"}}}
                                           {:op :add-edge :edge {:type :supersedes :from :y :to (a :p2) :evidence {:actor "ana"}}}]))
        es (anchor/endpoint-state (:store r) (:revision-id r) (a :p2))]
    (is (= :superseded (:state es)))
    (is (:ambiguous? es))
    (is (nil? (:latest es)))
    (is (= 2 (count (:successors es))))
    (is (= (sort (:successors es)) (:successors es)) "successors come sorted and deduplicated")))

(deftest a-chain-of-succession-is-followed-to-the-last-node
  (let [{:keys [store rev a]} (world)
        r1 (rev/apply-plan store (plan rev [{:op :add-node :node {:class :sign :kind :text :content {:text "b"}} :as :b}
                                            {:op :add-edge :edge {:type :supersedes :from :b :to (a :p2) :evidence {:actor "jp"}}}]))
        b (get (:aliases r1) :b)
        r2 (rev/apply-plan (:store r1) (plan (:revision-id r1) [{:op :add-node :node {:class :sign :kind :text :content {:text "c"}} :as :c}
                                                                {:op :add-edge :edge {:type :supersedes :from :c :to b :evidence {:actor "jp"}}}]))
        c (get (:aliases r2) :c)
        es (anchor/endpoint-state (:store r2) (:revision-id r2) (a :p2))]
    (is (= [b] (:successors es)) "only the direct successor")
    (is (= c (:latest es)) "the chain is followed to the end")
    (is (not (:ambiguous? es)))))

;; ---------------------------------------------------------------- §6.3 queries

(deftest anchored-in-answers-what-is-anchored-in-what
  (let [{:keys [store rev a]} (world)
        r (anchor/anchored-in store rev (a :p1))]
    (is (= (a :p1) (:node r)))
    (is (= [[T [0]]] (:positions r)))
    (is (= [(a :b1) (a :r1)] (sort (map :edge (:as-from r)))))
    (is (= [] (:as-to r)))
    (testing "a node at several positions lists them all in document order"
      (let [r2 (rev/apply-plan store (plan rev [{:op :add-edge :edge {:type :ownership :tree T :parent [] :to (a :p1) :order 3}}]))]
        (is (= [[T [0]] [T [3]]] (:positions (anchor/anchored-in (:store r2) (:revision-id r2) (a :p1)))))))))

(deftest report-always-carries-the-three-states-and-the-five-types
  (let [{:keys [store rev]} (world)
        r (anchor/report store rev)]
    (is (= rev (:revision r)))
    (is (= 3 (:anchors r)))
    (is (= {:intact 3 :superseded 0 :orphan 0} (:by-state r)))
    (is (= anchor/anchor-types (set (keys (:by-type r)))))
    (is (= (assoc zeros :intact 2) (get-in r [:by-type :reference])))
    (is (= zeros (get-in r [:by-type :derived])))
    (is (= [] (:orphans r)))))

(deftest diff-reports-added-removed-and-changed-anchors
  (let [{:keys [store rev a]} (world)
        r (rev/apply-plan store (plan rev [{:op :detach :tree T :at [1]}
                                           {:op :add-edge :edge {:type :semantic :from (a :sym) :to (a :sym)
                                                                 :evidence {:ref-hash (a :sym) :actor "jp" :context (a :w)}} :as :self}
                                           {:op :remove-edge :edge (a :b1)}]))
        d (anchor/diff (:store r) rev (:revision-id r))]
    (is (= [(get (:aliases r) :self)] (map :edge (:added d))))
    (is (= [(a :b1)] (:removed d)))
    (is (= (sort-by :edge [{:edge (a :r1) :type :reference :before :intact :after :orphan}
                           {:edge (a :r2) :type :reference :before :intact :after :orphan}])
           (:changed d)))
    (testing "an edge whose two endpoints are the same node is listed on both sides"
      (let [ai (anchor/anchored-in (:store r) (:revision-id r) (a :sym))]
        (is (= [(get (:aliases r) :self)] (map :edge (:as-from ai))))
        (is (= [(get (:aliases r) :self)] (map :edge (:as-to ai))))))))

(deftest queries-are-pure-derived-and-deterministic
  (let [{:keys [store rev]} (world)]
    (is (= (anchor/states store rev) (anchor/states store rev)))
    (is (= (anchor/report store rev) (anchor/report store rev)))
    (is (= (sort (map :edge (anchor/states store rev))) (map :edge (anchor/states store rev)))
        "anchors come ordered by edge id")))

(deftest the-two-error-types
  (let [{:keys [store rev a]} (world)
        boom (fn [f] (try (f) nil (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e (ex-data e))))]
    (is (= :anchor/unknown-revision (:type (boom #(anchor/states store "nope")))))
    (is (= :anchor/unknown-revision (:type (boom #(anchor/report store "nope")))))
    (is (= :anchor/unknown-revision (:type (boom #(anchor/placements store "nope")))))
    (is (= :anchor/not-an-anchor (:type (boom #(anchor/state store rev "nope")))))
    (testing "an active edge that is not an anchor is refused too"
      (let [r (rev/apply-plan store (plan rev [{:op :add-node :node {:class :sign :kind :text :content {:text "z"}} :as :z}
                                               {:op :add-edge :edge {:type :supersedes :from :z :to (a :p2) :evidence {:actor "jp"}} :as :sup}]))]
        (is (= :anchor/not-an-anchor
               (:type (boom #(anchor/state (:store r) (:revision-id r) (get (:aliases r) :sup))))))))))
