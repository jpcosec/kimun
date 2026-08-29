(ns sldb.kernel.hardening-test
  "Tests added by the promise→test traceability pass (docs/v2/tests/promises.md):
   negative and boundary cases the tester lanes found untested or weak."
  (:require [clojure.test :refer [deftest is testing]]
            [sldb.kernel.test-util :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [sldb.host.default :as host]
            [sldb.kernel.canon :as canon]
            [sldb.kernel.node :as node]
            [sldb.kernel.edge :as edge]
            [sldb.kernel.tree :as tree]
            [sldb.kernel.revision :as rev]
            [sldb.kernel.heads :as heads]
            [sldb.kernel.store :as store]
            [sldb.host.fs-store :as fs]
            #?@(:clj [[babashka.fs :as bfs]])))

(def h host/host)
(def caps {"jp" :all})
(def T "01ARZ3NDEKTSV4RRFFQ69G5FAV")
(def U "01ARZ3NDEKTSV4RRFFQ69G5FAW")
(def TS "2026-08-29T12:00:00.000Z")

(defn- ex-of [f]
  (try (f) nil (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e (ex-data e))))

(defn- text [s] (:id (node/make h :sign :text {:text s})))
(defn- plan [base ops & {:keys [actor ts] :or {actor "jp" ts TS}}]
  {:plan/version 1 :base base :actor actor :engines {} :timestamp ts :ops ops})

(def doc-ops
  [{:op :add-node :node {:class :sign :kind :block :content {:format :markdown :type :document :attrs {}}} :as :root}
   {:op :add-node :node {:class :sign :kind :text :content {:text "a"}} :as :a}
   {:op :add-node :node {:class :sign :kind :text :content {:text "b"}} :as :b}
   {:op :add-node :node {:class :symbol :kind :term :content {:name "t" :lang "es"}} :as :sym}
   {:op :add-node :node {:class :fact :kind :context :content {:name "w1"}} :as :w1}
   {:op :add-node :node {:class :fact :kind :context :content {:name "w2"}} :as :w2}
   {:op :new-tree :tree {:kind :document :name "d"} :id T :root :root :as :t}
   {:op :add-edge :edge {:type :ownership :tree :t :from :root :to :a :order 0}}
   {:op :add-edge :edge {:type :ownership :tree :t :from :root :to :b :order 1}}])

(defn- base-store []
  (rev/apply-plan (rev/empty-store h caps) (plan nil doc-ops)))

;; ---------------------------------------------------------------- canon / node boundaries (§2.1)

(deftest canon-unicode-beyond-latin
  (testing "Hangul NFD/NFC, Arabic with combining marks, emoji sequences hash equal after NFC"
    (is (= (canon/digest h {:text "한"}) (canon/digest h {:text "한"})))            ; U+D55C vs decomposed jamo
    (is (= (canon/digest h {:text "أ"}) (canon/digest h {:text "أ"})))              ; alef + hamza above vs precomposed
    (is (= (canon/canon-str h "👨‍👩‍👧") (canon/canon-str h "👨‍👩‍👧"))))
  (testing "empty string and whitespace-only are distinct admitted contents"
    (is (not= (text "") (text " ")))
    (is (= (:id (node/make h :sign :text {:text ""})) (text ""))))
  (testing "integers: negative, zero, large; keywords with special characters"
    (is (= "[-1 0 9007199254740993]" (canon/canon-str h [-1 0 9007199254740993])))
    (is (= ":a.b/c-d_e" (canon/canon-str h :a.b/c-d_e)))))

(deftest node-extra-keys-and-literals
  (testing "extra content keys are admitted and enter the id"
    (is (not= (text "x") (:id (node/make h :sign :text {:text "x" :lang "es"})))))
  (testing "proposition literals: only atomic EDN values as args"
    (is (:id (node/make h :symbol :proposition {:form ["p" "s" 1 true nil :k]})))
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                 (node/make h :symbol :proposition {:form ["p" [1 2]]}))))
  (testing "canonical content rejects floats nested anywhere"
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                 (node/make h :sign :block {:format :markdown :type :x :attrs {:a {:b [1.5]}}})))))

;; ---------------------------------------------------------------- edge boundaries (§3.2)

(deftest edge-evidence-extra-keys-enter-the-id
  (let [a (text "a") b (text "b")
        e1 (edge/make h {:type :reference :from a :to b :evidence {:ref-hash b :actor "jp"}})
        e2 (edge/make h {:type :reference :from a :to b :evidence {:ref-hash b :actor "jp" :note "x"}})]
    (is (not= (:id e1) (:id e2)))))

(deftest edge-empty-origin-is-still-a-string
  (let [a (text "a") b (text "b")]
    (is (:id (edge/make h {:type :reference :from a :to b :evidence {:ref-hash b :actor ""}})))
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                 (edge/make h {:type :reference :from a :to b :evidence {:ref-hash b :actor nil}})))))

;; ---------------------------------------------------------------- tree boundaries (§3.1)

(deftest tree-boundaries
  (let [r (text "r") a (text "a") b (text "b")
        t (tree/create h :document "d" r T)]
    (testing "a single-node tree commits and has a merkle-root"
      (is (= 64 (count (tree/merkle-root (tree/commit h t))))))
    (testing "order boundary: count is allowed, count+1 is not"
      (is (tree/add-child t r a 0))
      (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) (tree/add-child t r a 1))))
    (testing "duplicate child rejected"
      (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error)
                   (-> t (tree/add-child r a 0) (tree/add-child r a 1)))))
    (testing "replacing the root re-roots the tree"
      (let [t2 (tree/commit h (-> t (tree/add-child r a 0) (tree/replace-node r b)))]
        (is (= b (tree/root t2)))
        (is (= [a] (get-in t2 [:children b])))
        (is (tree/valid? t2))))
    (testing "deep tree: 200 levels commit and only the changed path is dirty"
      (let [ids (mapv #(text (str "deep-" %)) (range 200))
            deep (reduce (fn [t i] (tree/add-child t (ids (dec i)) (ids i) 0)) (tree/create h :document "deep" (ids 0) U) (range 1 200))
            c (tree/commit h deep)
            c2 (tree/replace-node c (ids 199) (text "leaf'"))]
        (is (= 200 (count (:dirty c2))) "leaf + 199 ancestors")
        (is (tree/valid? (tree/commit h c2)))))))

;; ---------------------------------------------------------------- plan / revision (§5.1, §5.2, §6.1)

(deftest empty-plan-is-a-valid-no-op-revision
  (let [{:keys [store]} (base-store)
        r (rev/apply-plan store (plan (:head store) []))]
    (is (= [(:head store)] (get-in r [:revision :parents])))
    (is (= (:roots (rev/revision store (:head store))) (:roots (:revision r))))
    (is (= 2 (count (:revisions (:store r)))))))

(deftest alias-misuse-is-rejected
  (let [{:keys [store]} (base-store)]
    (is (= :ids-exist (:check (ex-of #(rev/apply-plan store (plan (:head store) [{:op :add-node :node {:class :sign :kind :text :content {:text "x"}} :as :n}
                                                                              {:op :add-node :node {:class :sign :kind :text :content {:text "y"}} :as :n}])))))
        "duplicate alias")
    (is (= :ids-exist (:check (ex-of #(rev/apply-plan store (plan (:head store) [{:op :add-edge :edge {:type :ownership :tree T :from :later :to :later2 :order 0}}
                                                                              {:op :add-node :node {:class :sign :kind :text :content {:text "x"}} :as :later}])))))
        "alias used before declaration")))

(deftest ownership-edges-are-not-removed-by-id
  (let [{:keys [store aliases]} (base-store)
        eid (:id (edge/make h {:type :ownership :tree T :from (aliases :root) :to (aliases :a) :order 0}))]
    (is (= :ids-exist (:check (ex-of #(rev/apply-plan store (plan (:head store) [{:op :remove-edge :edge eid}]))))))))

(deftest move-to-own-descendant-is-tree-integrity
  (let [{:keys [store aliases]} (base-store)
        r (aliases :root) a (aliases :a)]
    (is (= :tree-integrity (:check (ex-of #(rev/apply-plan store (plan (:head store) [{:op :move :tree T :node r :parent a :order 0}]))))))))

(deftest move-marks-dirty-and-changes-the-root
  (let [{:keys [store aliases]} (base-store)
        r (rev/apply-plan store (plan (:head store) [{:op :move :tree T :node (aliases :b) :parent (aliases :root) :order 0}]))]
    (is (not= (get-in (rev/revision store (:head store)) [:roots T]) (get-in r [:revision :roots T])))))

(deftest multi-context-bindings-are-distinct-edges
  (let [{:keys [store aliases]} (base-store)
        a (aliases :a) sym (aliases :sym) w1 (aliases :w1) w2 (aliases :w2)
        r (rev/apply-plan store (plan (:head store) [{:op :add-edge :edge {:type :binding :from a :to sym :evidence {:ref-hash sym :actor "jp" :context w1}}}
                                                     {:op :add-edge :edge {:type :binding :from a :to sym :evidence {:ref-hash sym :actor "jp" :context w2}}}]))]
    (is (= 2 (count (:edges-added r))) "one binding per W_i")))

(deftest span-nodes-are-materialized-by-the-plan
  (let [{:keys [store aliases]} (base-store)
        a (aliases :a) sym (aliases :sym) w1 (aliases :w1)
        r (rev/apply-plan store (plan (:head store) [{:op :add-node :node {:class :sign :kind :span :content {:leaf a :range [0 1]}} :as :sp}
                                                     {:op :add-edge :edge {:type :binding :from :sp :to sym :evidence {:ref-hash sym :actor "jp" :context w1}}}]))
        sp (get-in r [:aliases :sp])]
    (is (= :structural (:address (rev/get-object (:store r) sp))))
    (is (= sp (:id (node/make h :sign :span {:leaf a :range [0 1]}))) "content-addressed: same span twice is the same node")))

(deftest timestamp-enters-the-revision-id-invariant-16
  (let [{:keys [store]} (base-store)
        r1 (rev/apply-plan store (plan (:head store) [] :ts "2026-08-29T12:00:01.000Z"))
        r2 (rev/apply-plan store (plan (:head store) [] :ts "2026-08-29T12:00:02.000Z"))]
    (is (not= (:revision-id r1) (:revision-id r2)))))

(deftest heads-advance-only-for-touched-trees
  (let [{:keys [store]} (base-store)
        r (rev/apply-plan store (plan (:head store) [{:op :add-node :node {:class :sign :kind :block :content {:format :markdown :type :document :attrs {:n 2}}} :as :r2}
                                                     {:op :new-tree :tree {:kind :taxonomy :name "u"} :id U :root :r2}]))
        s (:store r)]
    (is (= (:head store) (get-in s [:heads T])) "T untouched keeps its head")
    (is (= (:head s) (get-in s [:heads U])))))

(deftest replace-in-one-tree-leaves-other-trees-untouched
  (let [{:keys [store aliases]} (base-store)
        a (aliases :a)
        s1 (:store (rev/apply-plan store (plan (:head store) [{:op :add-node :node {:class :sign :kind :block :content {:format :markdown :type :document :attrs {:n 3}}} :as :r2}
                                                              {:op :new-tree :tree {:kind :taxonomy :name "u"} :id U :root :r2}
                                                              {:op :add-edge :edge {:type :ownership :tree U :from :r2 :to a :order 0}}])))
        u-root-before (get-in s1 [:trees U :objects (tree/root (get-in s1 [:trees U]))])
        a' (text "a'")
        r (rev/apply-plan s1 (plan (:head s1) [{:op :add-node :node {:class :sign :kind :text :content {:text "a'"}}}
                                               {:op :replace :tree T :old a :new a'}]))
        s2 (:store r)]
    (is (tree/contains-node? (get-in s2 [:trees U]) a) "a still in U")
    (is (= u-root-before (get-in s2 [:trees U :objects (tree/root (get-in s2 [:trees U]))])) "U's merkle-root unchanged")
    (is (= (:head s1) (get-in s2 [:heads U])))))

(deftest chained-replace-follows-step-by-step
  (let [{:keys [store aliases]} (base-store)
        a (aliases :a) sym (aliases :sym) w1 (aliases :w1)
        s1 (:store (rev/apply-plan store (plan (:head store) [{:op :add-edge :edge {:type :binding :from a :to sym :evidence {:ref-hash sym :actor "jp" :context w1}}}])))
        a' (text "a'") a'' (text "a''")
        s2 (:store (rev/apply-plan s1 (plan (:head s1) [{:op :add-node :node {:class :sign :kind :text :content {:text "a'"}}} {:op :replace :tree T :old a :new a'}])))
        s3 (:store (rev/apply-plan s2 (plan (:head s2) [{:op :add-node :node {:class :sign :kind :text :content {:text "a''"}}} {:op :replace :tree T :old a' :new a''}])))
        bindings-from (fn [s n] (filter #(and (= :binding (:type %)) (= n (:from %))) (map #(rev/get-object s %) (:edges s))))
        sups (filter #(= :supersedes (:type %)) (map #(rev/get-object s3 %) (:edges s3)))]
    (is (= 1 (count (bindings-from s3 a''))) "binding followed to the latest successor")
    (is (= 1 (count (bindings-from s3 a'))) "intermediate successor keeps its (superseded) binding")
    (is (= #{[a' a] [a'' a']} (set (map (juxt :from :to) sups))) "chain X'' → X' → X")))

(deftest semantic-and-projection-stay-derived-is-invalidated-on-replace
  (let [{:keys [store aliases]} (base-store)
        a (aliases :a) sym (aliases :sym) w1 (aliases :w1)
        sym2 (:id (node/make h :symbol :term {:name "t2" :lang "es"}))
        s1 (:store (rev/apply-plan store (plan (:head store)
                                              [{:op :add-node :node {:class :symbol :kind :term :content {:name "t2" :lang "es"}}}
                                               {:op :add-edge :edge {:type :semantic :from sym :to a :evidence {:ref-hash a :actor "jp" :context w1}} :as :sem}
                                               {:op :add-edge :edge {:type :projection :from sym :to a :evidence {:ref-hash a :engine "m" :version "1" :context w1 :status :sinnvoll}} :as :proj}
                                               {:op :add-edge :edge {:type :derived :from a :to sym2 :evidence {:ref-hash sym2 :engine "emb" :version "1"}} :as :der}])))
        a' (text "a'")
        r (rev/apply-plan s1 (plan (:head s1) [{:op :add-node :node {:class :sign :kind :text :content {:text "a'"}}} {:op :replace :tree T :old a :new a'}]))
        s2 (:store r)
        types-to (fn [n] (set (map :type (filter #(= n (:to %)) (map #(rev/get-object s2 %) (:edges s2))))))]
    (is (= #{:semantic :projection :supersedes} (types-to a)) "semantic/projection stay on the old node; supersedes points to it")
    (is (empty? (filter #(= :derived (:type %)) (map #(rev/get-object s2 %) (:edges s2)))) "derived edge invalidated")
    (is (empty? (filter #(and (contains? #{:semantic :projection} (:type %)) (= a' (:to %))) (map #(rev/get-object s2 %) (:edges s2)))) "not followed")))

(deftest conflict-kinds-removed-and-superseded-target
  (let [{:keys [store aliases]} (base-store)
        base (:head store) root (aliases :root) a (aliases :a) b (aliases :b)
        a' (text "a'")
        ;; head: a replaced by a'
        s1 (:store (rev/apply-plan store (plan base [{:op :add-node :node {:class :sign :kind :text :content {:text "a'"}}} {:op :replace :tree T :old a :new a'}])))
        e1 (ex-of #(rev/apply-plan s1 (plan base [{:op :move :tree T :node a :parent root :order 1}])))
        ;; head: b removed via move-away is not possible; remove by replacing subtree owner — use a second store where b is gone
        s2 (:store (rev/apply-plan store (plan base [{:op :add-node :node {:class :sign :kind :block :content {:format :markdown :type :document :attrs {:n 9}}} :as :r2}
                                                     {:op :new-tree :tree {:kind :document :name "d2"} :id U :root :r2}
                                                     {:op :add-node :node {:class :sign :kind :text :content {:text "b'"}} :as :b2}
                                                     {:op :replace :tree T :old b :new :b2}])))
        e2 (ex-of #(rev/apply-plan s2 (plan base [{:op :move :tree T :node b :parent root :order 0}])))]
    (is (= #{{:tree T :node a :kind :superseded-target} {:tree T :node root :kind :same-parent-edit}}
           (set (get-in e1 [:conflict-set :conflicts]))) "the moved node was superseded; its parent's children changed")
    (is (= :plan/conflict (:type e2)))
    (is (contains? (set (map :kind (get-in e2 [:conflict-set :conflicts]))) :superseded-target) "superseded wins over removed")))

(deftest remove-edge-already-removed-by-head-conflicts
  (let [{:keys [store aliases]} (base-store)
        base (:head store) a (aliases :a) sym (aliases :sym) w1 (aliases :w1)
        s1 (rev/apply-plan store (plan base [{:op :add-edge :edge {:type :binding :from a :to sym :evidence {:ref-hash sym :actor "jp" :context w1}} :as :b}]))
        eid (get-in s1 [:aliases :b])
        base2 (:revision-id s1)
        s2 (:store (rev/apply-plan (:store s1) (plan base2 [{:op :remove-edge :edge eid}])))
        e (ex-of #(rev/apply-plan s2 (plan base2 [{:op :remove-edge :edge eid}])))]
    (is (= [{:tree nil :node eid :kind :removed-target}] (get-in e [:conflict-set :conflicts])))))

(deftest rebase-merges-edge-sets-by-union
  (let [{:keys [store aliases]} (base-store)
        base (:head store) a (aliases :a) b (aliases :b) sym (aliases :sym) w1 (aliases :w1) w2 (aliases :w2)
        s1 (:store (rev/apply-plan store (plan base [{:op :add-edge :edge {:type :binding :from a :to sym :evidence {:ref-hash sym :actor "jp" :context w1}}}])))
        r (rev/apply-plan s1 (plan base [{:op :add-edge :edge {:type :binding :from b :to sym :evidence {:ref-hash sym :actor "jp" :context w2}}}]))]
    (is (= base (:rebased-from r)))
    (is (= 2 (count (:edges (:store r)))) "both concurrent bindings survive")))

;; ---------------------------------------------------------------- heads under contention

(deftest concurrent-commits-on-disjoint-trees-all-land
  (let [ref (atom (rev/empty-store h caps))
        tids (mapv #(str "01ARZ3NDEKTSV4RRFFQ69G5FA" (nth "ABCDEFGH" %)) (range 6))
        mk (fn [i] (plan nil [{:op :add-node :node {:class :sign :kind :block :content {:format :markdown :type :document :attrs {:i i}}} :as :r}
                             {:op :new-tree :tree {:kind :document :name (str i)} :id (tids i) :root :r}]))
        ;; plan as a function of the store the commit actually sees: retries rebuild :base
        results #?(:clj (doall (map deref (mapv (fn [i] (future (heads/commit! ref (fn [s] (assoc (mk i) :base (:head s)))))) (range 6))))
                   :cljs (mapv (fn [i] (heads/commit! ref (fn [s] (assoc (mk i) :base (:head s))))) (range 6)))]
    (is (= 6 (count results)))
    (is (= 6 (count (:revisions @ref))))
    (is (= (set tids) (set (keys (:heads @ref)))))))

;; ---------------------------------------------------------------- store failure modes (§8.1)

#?(:clj
   (deftest store-failure-modes
     (let [dir (str (bfs/create-temp-dir {:prefix "sldb-hard-"}))
           b (fs/backend dir)
           s (:store (store/commit! b (store/init! b h caps) (plan nil doc-ops)))]
       (testing "store.edn carries the descriptor fields"
         (is (= #{:format-version :hash-alg :capabilities} (set (keys (store/read-descriptor b)))))
         (is (= :sha-256 (:hash-alg (store/read-descriptor b)))))
       (testing "a missing object file is reported by verify"
         (let [victim (first (sort (store/reachable s)))]
           (bfs/delete (str dir "/objects/" victim))
           (is (= [{:id victim :reason :missing}] (:bad (store/verify b s))))))
       (testing "a corrupt log line is a :store/corrupt-log error with its line number"
         (spit (str dir "/log.edn") "{:id \"x\" :plan" :append true)
         (is (= :store/corrupt-log (:type (ex-of #(store/replay b h)))))
         (is (= 2 (:line (ex-of #(store/replay b h))))))
       (testing "hash algorithm mismatch on open"
         (let [dir2 (str (bfs/create-temp-dir {:prefix "sldb-alg-"})) b2 (fs/backend dir2)]
           (store/init! b2 h caps)
           (spit (str dir2 "/store.edn") (pr-str {:format-version 1 :hash-alg :blake3 :capabilities caps}))
           (is (= :store/error (:type (ex-of #(store/open b2 h))))))))))
