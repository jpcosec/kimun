(ns sldb.kernel.reconcile-test
  (:require [clojure.test :refer [deftest is testing]]
            [sldb.host.default :as host]
            [sldb.kernel.anchor :as anchor]
            [sldb.kernel.reconcile :as rec]
            [sldb.kernel.revision :as rev]))

(def h host/host)
(def caps {"jp" :all})
(def T "01ARZ3NDEKTSV4RRFFQ69G5FAV")
(def TS "2026-08-29T12:00:00.000Z")

(defn- plan [base ops] {:plan/version 1 :base base :actor "jp" :engines {} :timestamp TS :ops ops})
(defn- txt [t] {:class :sign :kind :text :content {:text t}})
(defn- ext [fp] {:class :sign :kind :external
                 :content {:locator {:kind :file :path "docs/x.pdf"} :sample "Resumen ejecutivo" :fingerprint fp}})
(defn- fp [n] (str "sha-256:" (apply str (repeat 63 "0")) n))
(defn- boom [f] (try (f) nil (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e (ex-data e))))

;; ---------------------------------------------------------------- dice (§6.5)

(deftest dice-is-a-deterministic-grapheme-trigram-coefficient
  (is (= 1.0 (rec/dice h "El cielo es azul." "El cielo es azul.")))
  (is (= 1.0 (rec/dice h "" "")) "two empty texts are identical")
  (is (= 0.0 (rec/dice h "" "abc")))
  (is (= 0.0 (rec/dice h "abcdef" "uvwxyz")))
  (is (= (rec/dice h "a b c" "a b d") (rec/dice h "a b d" "a b c")) "symmetric")
  (is (< 0.6 (rec/dice h "La hierba es verde." "La hierba es parda.")))
  (is (> 0.6 (rec/dice h "La hierba es verde." "Otro asunto por completo.")))
  (testing "a text of fewer than three graphemes contributes the whole text as one gram"
    (is (= 1.0 (rec/dice h "ab" "ab")))
    (is (= 0.0 (rec/dice h "ab" "cd"))))
  (testing "grapheme clusters, not code units"
    (is (= 1.0 (rec/dice h (str "e" "́" "xyz") "éxyz")) "NFC-equal texts")))

;; ---------------------------------------------------------------- methods (§6.5)

(defn- anchored-world
  "A document tree of `contents`, with a reference anchored on the node at `at`."
  [contents at]
  (let [ops (concat [{:op :add-node :node {:class :sign :kind :block :content {:format :markdown :type :document :attrs {}}} :as :root}]
                    (map-indexed (fn [i c] (assoc {:op :add-node :node c} :as (keyword (str "n" i)))) contents)
                    [{:op :new-tree :tree {:kind :document :name "doc"} :id T :root :root :as :t}]
                    (map-indexed (fn [i _] {:op :add-edge :edge {:type :ownership :tree :t :parent [] :to (keyword (str "n" i)) :order i}}) contents))
        r (rev/apply-plan (rev/empty-store h caps) (plan nil (vec ops)))
        a (:aliases r)
        target (a (keyword (str "n" at)))
        r2 (rev/apply-plan (:store r)
                           (plan (:revision-id r)
                                 [{:op :add-edge :edge {:type :reference :from (a :root) :to target
                                                        :evidence {:ref-hash target :actor "jp"}} :as :ref}]))]
    {:store (:store r2) :rev (:revision-id r2) :a a :target target :ref (get (:aliases r2) :ref)}))

(deftest position-names-the-node-that-took-the-path
  (let [{:keys [store rev target ref]} (anchored-world [(txt "uno") (txt "dos")] 0)
        r (rev/apply-plan store (plan rev [{:op :add-node :node (txt "uno bis") :as :new}
                                           {:op :detach :tree T :at [0]}
                                           {:op :add-edge :edge {:type :ownership :tree T :parent [] :to :new :order 0}}]))
        s' (:store r) rev' (:revision-id r) new (get (:aliases r) :new)
        [p & more] (rec/proposals s' rev' {})]
    (is (= :orphan (:state (anchor/state s' rev' ref))))
    (is (nil? more))
    (is (= {:old target :candidate new :method :position :confidence 1.0}
           (select-keys p [:old :candidate :method :confidence])))
    (is (= {:tree T :path [0] :base rev} (:evidence p)))
    (is (= [ref] (:edges p)))))

(deftest a-candidate-that-was-already-in-the-tree-is-proposed-with-less-confidence
  (let [{:keys [store rev a target]} (anchored-world [(txt "uno") (txt "dos")] 0)
        r (rev/apply-plan store (plan rev [{:op :detach :tree T :at [0]}]))
        s' (:store r) rev' (:revision-id r)
        [p] (rec/proposals s' rev' {})]
    (is (= target (:old p)))
    (is (= (a :n1) (:candidate p)) "the node that slid into path [0]")
    (is (= :position (:method p)))
    (is (= 0.9 (:confidence p)) "it already occurred in the tree at the base revision")))

(deftest fingerprint-names-the-same-locator-with-a-different-digest
  (let [{:keys [store rev target ref]} (anchored-world [(ext (fp "1")) (txt "relleno")] 0)
        r (rev/apply-plan store (plan rev [{:op :add-node :node (ext (fp "2")) :as :new}
                                           {:op :detach :tree T :at [0]}
                                           {:op :add-edge :edge {:type :ownership :tree T :parent [] :to :new :order 1}}]))
        s' (:store r) rev' (:revision-id r) new (get (:aliases r) :new)
        ps (rec/proposals s' rev' {:all? true})
        by (into {} (map (juxt :method identity)) ps)]
    (is (= :orphan (:state (anchor/state s' rev' ref))))
    (is (= new (:candidate (by :fingerprint))))
    (is (= 1.0 (:confidence (by :fingerprint))))
    (is (nil? (by :position)) "path [0] now holds a node of another kind")
    (is (= new (:candidate (first ps))) "fingerprint wins the ranking")))

(deftest sample-names-the-best-dice-match-above-the-threshold
  (let [{:keys [store rev target ref]} (anchored-world [(txt "La hierba es verde.")] 0)
        r (rev/apply-plan store (plan rev [{:op :add-node :node {:class :sign :kind :opaque :content {:format "html" :blob "<hr>"}} :as :op}
                                           {:op :add-node :node (txt "La hierba es parda.") :as :new}
                                           {:op :add-node :node (txt "Nada que ver.") :as :other}
                                           {:op :detach :tree T :at [0]}
                                           {:op :add-edge :edge {:type :ownership :tree T :parent [] :to :op :order 0}}
                                           {:op :add-edge :edge {:type :ownership :tree T :parent [] :to :new :order 1}}
                                           {:op :add-edge :edge {:type :ownership :tree T :parent [] :to :other :order 2}}]))
        s' (:store r) rev' (:revision-id r) new (get (:aliases r) :new)
        [p & more] (rec/proposals s' rev' {})]
    (is (nil? more))
    (is (= :sample (:method p)))
    (is (= new (:candidate p)) "the best match, not merely a match")
    (is (= (rec/dice h "La hierba es verde." "La hierba es parda.") (:confidence p)))
    (testing "raising the threshold above the match removes the proposal"
      (is (= [] (rec/proposals s' rev' {:min-confidence 0.99}))))))

;; ---------------------------------------------------------------- acceptance (§6.5)

(deftest a-proposal-is-outside-the-pool-until-an-actor-accepts-it
  (let [{:keys [store rev target ref]} (anchored-world [(txt "uno") (txt "dos")] 0)
        r (rev/apply-plan store (plan rev [{:op :add-node :node (txt "uno bis") :as :new}
                                           {:op :detach :tree T :at [0]}
                                           {:op :add-edge :edge {:type :ownership :tree T :parent [] :to :new :order 0}}]))
        s' (:store r) rev' (:revision-id r) new (get (:aliases r) :new)
        [p] (rec/proposals s' rev' {})]
    (testing "computing proposals persists nothing: no supersedes edge appears"
      (is (empty? (filter (fn [eid] (= :supersedes (:type (rev/get-object s' eid)))) (:edges s')))))
    (let [ap (rec/accept-plan s' rev' p {:actor "jp" :timestamp TS})]
      (is (= [{:op :add-edge :edge {:type :supersedes :from new :to target :evidence {:actor "jp"}}}] (:ops ap)))
      (is (= rev' (:base ap)))
      (is (= "jp" (:actor ap)))
      (testing "no confidence and no method reach the edge (§3.2: the evidence is the actor)"
        (is (= #{:actor} (set (keys (get-in ap [:ops 0 :edge :evidence]))))))
      (let [r2 (rev/apply-plan s' ap) s2 (:store r2) rev2 (:revision-id r2)]
        (is (= [[target new]] (:superseded r2)))
        (is (= :superseded (:state (anchor/state s2 rev2 ref))))
        (is (= [] (:orphans (anchor/report s2 rev2))))
        (is (= [] (rec/proposals s2 rev2 {:base rev'})) "nothing is orphan any more")))))

(deftest base-is-required-when-the-revision-has-no-single-parent
  (let [{:keys [store rev]} (anchored-world [(txt "uno")] 0)]
    (is (vector? (rec/proposals store rev {})) "one parent: the default works")
    (let [r0 (rev/apply-plan (rev/empty-store h caps) (plan nil [{:op :add-node :node (txt "solo") :as :n}]))]
      (is (= :reconcile/base-required (:type (boom #(rec/proposals (:store r0) (:revision-id r0) {}))))))))
