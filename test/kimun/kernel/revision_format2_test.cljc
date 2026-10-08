(ns kimun.kernel.revision-format2-test
  "Format 2 of the revision engine: accessors, the legacy engine as oracle,
   what a committer persists, and the format-1 path of kimun.kernel.store."
  (:require [kimun.test-util :as ktu] [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [kimun.kernel.test-util :as tu :refer [defspec]]
            [kimun.host.default :as host]
            [kimun.host.fs-store :as fs]
            [kimun.kernel.canon :as canon]
            [kimun.kernel.generators :as g]
            [kimun.kernel.legacy.revision :as legacy]
            [kimun.kernel.mmap :as mmap]
            [kimun.kernel.ports :as ports]
            [kimun.kernel.revision :as rev]
            [kimun.kernel.store :as store]
            [kimun.kernel.tree :as tree]
            #?(:clj [babashka.fs :as bfs])))

(def h host/host)
(def caps {"jp" :all})
(def TS "2026-08-29T12:00:00.000Z")

(defn- plan [base ops] {:plan/version 1 :base base :actor "jp" :engines {} :timestamp TS :ops ops})
(defn- text-op [s as] {:op :add-node :node {:class :sign :kind :text :content {:text s}} :as as})
(defn- check-of [f] (try (f) nil (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e (or (:check (ex-data e)) (:type (ex-data e))))))

;; ---------------------------------------------------------------- random histories

(def gen-step
  (gen/one-of [(gen/tuple (gen/return :add) gen/nat g/gen-text)
               (gen/tuple (gen/return :replace) gen/nat g/gen-text)
               (gen/tuple (gen/return :detach) gen/nat)
               (gen/tuple (gen/return :move) gen/nat gen/nat)
               (gen/tuple (gen/elements [:reference :derived]) gen/nat gen/nat)
               (gen/tuple (gen/return :unedge) gen/nat)]))

(defn- step-ops
  "Concrete ops of an abstract step against the current state (`s`: any store)."
  [s tid n [kind i j]]
  (let [kids (mapv :node (:children (:root (get (:trees s) tid))))
        k (count kids) edges (vec (sort (:edges s)))
        fresh (text-op (str "s" n "-" j) :x)]
    (case kind
      :add [(text-op (str "s" n "-" j) :x) {:op :add-edge :edge {:type :ownership :tree tid :parent [] :to :x :order 0}}]
      :replace (if (pos? k) [fresh {:op :replace :tree tid :at [(mod i k)] :new :x}] [fresh])
      :detach [{:op :detach :tree tid :at [(if (pos? k) (mod i k) 0)]}]
      :move [{:op :move :tree tid :from [(if (pos? k) (mod i k) 0)] :to [] :order (if (pos? k) (mod j k) 0)}]
      (:reference :derived)
      (if (pos? k)
        (let [a (kids (mod i k)) b (kids (mod j k))]
          [{:op :add-edge :edge {:type kind :from a :to b
                                 :evidence (if (= kind :reference) {:ref-hash b :actor "jp"} {:ref-hash b :engine "e" :version "1"})}}])
        [])
      :unedge (if (seq edges) [{:op :remove-edge :edge (edges (mod i (count edges)))}] []))))

(defn- incidence-of [s]
  (reduce (fn [m id] (let [e (rev/get-object s id)]
                       (reduce #(update %1 %2 (fnil conj (sorted-set)) id) m (distinct [(:from e) (:to e)]))))
          {} (rev/active-edges s)))

(defn- expand
  "The sorted edge ids of an :incidence value: the inline vector itself, or the
   keys of the nested set of a hub `{:set root}`."
  [s v]
  (if (rev/hub-value? v) (vec (mmap/keys #(rev/get-object s %) (:set v))) v))

(defn- incidence-entries [s]
  (into {} (map (fn [[n v]] [n (expand s v)]))
        (mmap/entries #(rev/get-object s %) (:incidence (rev/revision s (:head s))))))

(defn- canonical-incidence-value?
  "The shape rule: a non-empty sorted vector up to the threshold, `{:set root}`
   with nothing else and more than the threshold above it."
  [s v]
  (let [ids (expand s v)]
    (and (seq ids) (= ids (vec (sort (distinct ids))))
         (if (> (count ids) rev/hub-threshold)
           (and (map? v) (= [:set] (keys v)) (string? (:set v)))
           (vector? v)))))

(defn- same-state? [l s]
  (let [head (rev/revision s (:head s))]
    (and (= (:trees l) (:trees s))
         (= (into {} (map (fn [[tid t]] [tid [(tree/descriptor-id h (:descriptor t)) (tree/merkle-root t)]])) (:trees l))
            (into {} (map (fn [tid] [tid (rev/tree-entry s tid)])) (rev/tree-ids s)))
         (every? (fn [tid] (= (get (:trees s) tid) (rev/load-tree s tid (:head s)))) (rev/tree-ids s))
         (= (:edges l) (:edges s) (set (rev/active-edges s)))
         (= (count (:trees l)) (rev/tree-count s)) (= (count (:edges l)) (rev/edge-count s))
         (= (set (filter :class (vals (:objects l)))) (set (filter :class (vals (:objects s)))))
         (= (into {} (map (fn [[n ids]] [n (vec ids)])) (incidence-of s)) (incidence-entries s))
         (every? (fn [[_ v]] (canonical-incidence-value? s v)) (mmap/entries #(rev/get-object s %) (:incidence head)))
         (every? (fn [[n ids]] (= (vec ids) (rev/edges-touching s n))) (incidence-of s)))))

(defn- run-both
  "Applies the same steps to the legacy engine and to format 2; true when every
   step is accepted/rejected alike and leaves the same trees, edges and nodes."
  [first-plan steps]
  (let [tid (some :id (filter #(= :new-tree (:op %)) (:ops first-plan)))]
    (loop [l (:store (legacy/apply-plan (legacy/empty-store h caps) first-plan))
           s (:store (rev/apply-plan (rev/empty-store h caps) first-plan))
           n 0 steps steps]
      (cond
        (not (same-state? l s)) false
        (empty? steps) true
        :else
        (let [ops (step-ops l tid n (first steps))
              cl (check-of #(legacy/apply-plan l (plan (:head l) ops)))
              cs (check-of #(rev/apply-plan s (plan (:head s) ops)))]
          (cond
            (not= cl cs) false
            cl (recur l s (inc n) (rest steps))
            :else (let [rl (legacy/apply-plan l (plan (:head l) ops)) rs (rev/apply-plan s (plan (:head s) ops))]
                    (and (= (select-keys rl [:aliases :edges-added :superseded]) (select-keys rs [:aliases :edges-added :superseded]))
                         (= (:ops (:plan (:transaction rl))) (:ops (:plan (:transaction rs))))
                         (recur (:store rl) (:store rs) (inc n) (rest steps))))))))))

(defspec format-2-and-legacy-engines-agree-on-random-histories 40
  (prop/for-all [p g/gen-valid-plan steps (gen/vector gen-step 0 12)]
    (run-both p steps)))

;; ---------------------------------------------------------------- hubs

(def ^:private pool (mapv #(canon/digest h [:edge %]) (range 48)))

(defn- value-after
  "Folds `incidence-value` over `batches` ([add-ids del-ids] each), carrying the
   emitted mmap nodes as the loader: {:value v :objs {id node} :set #{ids}}."
  [batches]
  (reduce (fn [{:keys [value objs ids]} [add del]]
            (let [r (rev/incidence-value h objs (when-not (= mmap/removal value) value) (set add) (set (remove (set add) del)))]
              {:value (:value r) :objs (merge objs (:new r)) :ids (into (reduce disj ids del) add)}))
          {:value mmap/removal :objs {} :ids #{}} batches))

(defspec the-incidence-value-of-a-node-depends-only-on-its-set-of-edges 60
  (prop/for-all [batches (gen/vector (gen/tuple (gen/vector (gen/elements pool) 0 12) (gen/vector (gen/elements pool) 0 12)) 0 14)
                 seed gen/nat]
    (let [{:keys [value objs ids]} (value-after batches)
          shuffled (map second (sort-by first (map-indexed (fn [i id] [(hash [seed i id]) id]) (sort ids))))
          other (value-after (map (fn [c] [c []]) (partition-all 5 shuffled)))
          direct (rev/incidence-value h {} nil ids #{})]
      (and (= value (:value other) (:value direct))
           (cond (empty? ids) (= mmap/removal value)
                 (<= (count ids) rev/hub-threshold) (= (vec (sort ids)) value)
                 :else (and (= [:set] (keys value))
                            (= (vec (sort ids)) (vec (mmap/keys objs (:set value))))
                            ;; a hub change emits mmap nodes only, each hashing to its id
                            (every? (fn [[id o]] (and (mmap/node? o) (= id (canon/digest h o)))) objs)))))))

(defn- hub-store
  "A document whose root is the target of one reference edge from each of the
   text nodes `texts`, added in that order, `per-plan` edges per transaction."
  [texts per-plan]
  (let [T "01ARZ3NDEKTSV4RRFFQ69G5FAV"
        r0 (rev/apply-plan (rev/empty-store h caps)
                           (plan nil [{:op :add-node :node {:class :sign :kind :block :content {:format :markdown :type :document :attrs {}}} :as :root}
                                      {:op :new-tree :tree {:kind :document :name "d"} :id T :root :root}]))
        hub (get-in r0 [:aliases :root])]
    (reduce (fn [{:keys [store edges]} chunk]
              (let [r (rev/apply-plan store (plan (:head store)
                                                  (vec (mapcat (fn [t] [(text-op t (keyword t))
                                                                   {:op :add-edge :edge {:type :reference :from (keyword t) :to hub :evidence {:ref-hash hub :actor "jp"}} :as (keyword (str "e-" t))}])
                                                                chunk))))]
                {:store (:store r) :hub hub :tree T
                 :edges (into edges (map (fn [t] [t (get-in r [:aliases (keyword (str "e-" t))])])) chunk)}))
            {:store (:store r0) :hub hub :tree T :edges {}}
            (partition-all per-plan texts))))

(defn- incidence-root [s] (:incidence (rev/revision s (:head s))))
(defn- hub-value [s hub] (mmap/lookup h #(rev/get-object s %) (incidence-root s) hub nil))

(deftest a-hub-keeps-its-edges-in-a-nested-set-and-costs-a-few-nodes-per-edge
  (let [texts (mapv #(str "t" %) (range 40))
        {:keys [store hub edges]} (hub-store texts 1)
        other (hub-store (reverse texts) 7)]
    (testing "above the threshold the value is {:set root}; the accessor still answers the sorted ids"
      (is (= [:set] (keys (hub-value store hub))))
      (is (= 40 (count (rev/edges-touching store hub))))
      (is (= (vec (sort (vals edges))) (rev/edges-touching store hub)))
      (is (canonical-incidence-value? store (hub-value store hub))))
    (testing "the same edges in another order and batching give the same roots"
      (is (= (incidence-root store) (incidence-root (:store other))))
      (is (= (:edges (rev/revision store (:head store))) (:edges (rev/revision (:store other) (:head (:store other)))))))
    (testing "one more edge on the hub writes a few mmap nodes, none carrying the whole set"
      (let [r (rev/apply-plan store (plan (:head store) [(text-op "one-more" :n)
                                                         {:op :add-edge :edge {:type :reference :from :n :to hub :evidence {:ref-hash hub :actor "jp"}}}]))
            nodes (filter mmap/node? (vals (:new-objects r)))]
        (is (= 41 (count (rev/edges-touching (:store r) hub))))
        (is (every? #(<= (count (or (:entries %) (:children %))) 16) nodes))
        (is (not-any? (fn [n] (some (fn [[_ v]] (and (vector? v) (> (count v) rev/hub-threshold))) (:entries n))) nodes))))
    (testing "shrinking back to the threshold collapses to the inline vector with the ids of a store that never grew"
      (let [kept (take rev/hub-threshold texts)
            shrunk (:store (rev/apply-plan store (plan (:head store) (mapv (fn [t] {:op :remove-edge :edge (edges t)}) (drop rev/hub-threshold texts)))))
            small (hub-store kept 3)
            regrown (:store (reduce (fn [r t] (rev/apply-plan (:store r) (plan (:head (:store r)) [{:op :add-edge :edge {:type :reference :from (get-in (rev/get-object store (edges t)) [:from]) :to hub :evidence {:ref-hash hub :actor "jp"}}}])))
                                    {:store shrunk} (drop rev/hub-threshold texts)))]
        (is (vector? (hub-value shrunk hub)))
        (is (= rev/hub-threshold (count (hub-value shrunk hub))))
        (is (= (hub-value shrunk hub) (hub-value (:store small) hub) (rev/edges-touching shrunk hub)))
        (is (= (incidence-root store) (incidence-root regrown)) "grown past the threshold again: the same nested root")
        (is (= (rev/edges-touching store hub) (rev/edges-touching regrown hub)))
        (is (= (rev/edges-touching store hub) (rev/edges-touching shrunk hub (:head store))) "the older revision still answers from its nested set")))
    (testing "reachable walks the nested sets: everything this history wrote is reachable from its head"
      (let [reach (store/reachable store)]
        (is (contains? reach (:set (hub-value store hub))))
        (is (every? reach (keys (:objects store))))
        (let [root (:set (hub-value store hub))
              damaged (store/reachable (update store :objects dissoc root))]
          (is (contains? damaged root) "a missing nested root is in the result (verify reports it), not raised")
          (is (< (count damaged) (count reach))))))))

;; ---------------------------------------------------------------- accessors

(deftest accessors-answer-from-the-mmaps
  (let [T "01ARZ3NDEKTSV4RRFFQ69G5FAV"
        r1 (rev/apply-plan (rev/empty-store h caps)
                           (plan nil [{:op :add-node :node {:class :sign :kind :block :content {:format :markdown :type :document :attrs {}}} :as :root}
                                      (text-op "a" :a) (text-op "b" :b)
                                      {:op :new-tree :tree {:kind :document :name "d"} :id T :root :root}
                                      {:op :add-edge :edge {:type :ownership :tree T :parent [] :to :a :order 0}}
                                      {:op :add-edge :edge {:type :ownership :tree T :parent [] :to :b :order 1}}
                                      {:op :add-edge :edge {:type :reference :from :a :to :b :evidence {:ref-hash :b :actor "jp"}} :as :e}]))
        s1 (:store r1) {:keys [a b e]} (:aliases r1)
        r2 (rev/apply-plan s1 (plan (:head s1) [(text-op "b2" :b2) {:op :replace :tree T :at [1] :new :b2}]))
        s2 (:store r2) b2 (get-in r2 [:aliases :b2])
        empty (rev/empty-store h caps)]
    (testing "an empty store answers without objects"
      (is (= [] (rev/tree-ids empty) (vec (rev/active-edges empty)) (rev/edges-touching empty a)))
      (is (= 0 (rev/tree-count empty) (rev/edge-count empty)))
      (is (false? (rev/edge-active? empty e)))
      (is (nil? (rev/tree-entry empty T))))
    (testing "head"
      (is (= [T] (rev/tree-ids s1)))
      (is (= 1 (rev/tree-count s1) (rev/edge-count s1)))
      (is (= [e] (vec (rev/active-edges s1)) (rev/edges-touching s1 a) (rev/edges-touching s1 b)))
      (is (rev/edge-active? s1 e))
      (is (= [(tree/descriptor-id h (:descriptor (get (:trees s1) T))) (tree/merkle-root (get (:trees s1) T))] (rev/tree-entry s1 T)))
      (is (= {:tree T :kind :document :name "d"} (dissoc (rev/tree-descriptor s1 T) :root)))
      (is (= (get (:trees s1) T) (rev/committed-tree s1 T) (rev/load-tree s1 T (:head s1)))))
    (testing "replace re-anchors through :incidence; older revisions stay readable"
      (is (rev/edge-active? s2 e) "§6.1 adds a successor; the old reference stays")
      (is (rev/edge-active? s2 e (:head s1)))
      (is (= [e] (vec (rev/active-edges s2 (:head s1))) (rev/edges-touching s2 b (:head s1))))
      (is (= 3 (rev/edge-count s2)) "old reference + supersedes + re-anchored reference")
      (is (= (:edges s2) (set (rev/active-edges s2))))
      (is (= 2 (count (rev/edges-touching s2 b2))))
      (is (= #{:reference :supersedes} (set (map #(:type (rev/get-object s2 %)) (rev/edges-touching s2 b2)))))
      (is (= {:from a :to b2} (select-keys (rev/get-object s2 (first (remove #{e} (rev/edges-touching s2 a)))) [:from :to])))
      (is (= (rev/edges-touching s2 b) (vec (sort (rev/edges-touching s2 b)))))
      (is (= #{:reference :supersedes} (set (map #(:type (rev/get-object s2 %)) (rev/edges-touching s2 b)))))
      (is (= (get (:trees s1) T) (rev/committed-tree s2 T (:head s1))))
      (is (= (rev/tree-at s2 (:head s2) T) (:root (rev/committed-tree s2 T))))
      (is (= #{(:head s1) (:head s2)} (rev/ancestors s2 (:head s2))))
      (is (= (:revision r2) (rev/revision s2 (:head s2)) (rev/get-object s2 (:head s2)))))))

;; ---------------------------------------------------------------- what a committer persists

(defrecord CountingBackend [state]
  store/Backend
  (read-descriptor [_] (:descriptor @state))
  (write-descriptor! [_ d] (swap! state assoc :descriptor d))
  (read-object [_ id] (get-in @state [:objects id]))
  (write-object! [_ id content] (swap! state #(-> % (assoc-in [:objects id] content) (update :written conj id))))
  (object-ids [_] (swap! state update :listings inc) (keys (:objects @state)))
  (read-log [_] (:log @state))
  (read-heads [_] (:heads @state))
  (commit-tx! [_ expected tx new]
    (let [[old _] (swap-vals! state (fn [st] (if (= expected (or (:heads st) store/no-heads))
                                              (-> st (update :log conj tx) (assoc :heads new))
                                              st)))]
      (= expected (or (:heads old) store/no-heads)))))

(deftest commit-writes-exactly-the-new-objects-and-never-lists-the-backend
  (let [state (atom {:objects {} :written [] :listings 0 :log []})
        b (->CountingBackend state)
        plans (gen/generate g/gen-plan-sequence 30 7)
        heads-roots (atom [])
        final (reduce (fn [s p]
                        (swap! state assoc :written [])
                        (let [r (store/commit! b s (assoc p :base (:head s)))]
                          (swap! heads-roots conj (:heads-root (:store r)))
                          (is (= (set (keys (:new-objects r))) (set (:written @state))))
                          (is (= (count (:new-objects r)) (count (:written @state))) "each object once")
                          (is (every? (fn [[id o]] (= id (ports/digest-str (ports/hasher h) (store/serialize h o)))) (:new-objects r)))
                          (is (every? #(contains? (:objects s) %) (remove (set (keys (:new-objects r))) (keys (:objects (:store r)))))
                              "nothing else entered the CAS")
                          (:store r)))
                      (store/init! b h caps) plans)]
    (is (= 2 (:format-version (store/read-descriptor b))))
    (is (= 0 (:listings @state)) "apply-plan and commit! never call object-ids")
    (is (= (set (keys (:objects final))) (set (keys (:objects @state)))))
    (is (= (select-keys final [:head :heads :revisions :trees :edges]) (select-keys (store/open b h) [:head :heads :revisions :trees :edges])))
    (is (:ok? (store/verify b final)))
    (is (= (count (store/reachable final)) (:checked (store/verify b final))))
    ;; heads.edn names the root of the per-tree heads mmap: the nodes a later commit replaced
    ;; are the only objects no head reaches any more (nothing references an old heads root)
    (let [mmap-nodes (fn mmap-nodes [id] (cons id (mapcat mmap-nodes (mmap/child-ids (get (:objects final) id)))))
          old-heads (set (mapcat mmap-nodes (remove #{(:heads-root final)} @heads-roots)))
          unreached (remove (set (store/reachable final)) (keys (:objects final)))]
      (is (every? old-heads unreached) "every other object is reachable from the heads"))))

;; ---------------------------------------------------------------- format 1 through the store

(deftest a-format-1-store-opens-commits-and-reopens-through-the-legacy-path
  #?(:clj
     (let [dir (str (ktu/temp-dir "sldb-f1-"))
           b (fs/backend dir)
           fx1 (edn/read-string (slurp "test/fixtures/tx-001.edn"))
           fx2 (edn/read-string (slurp "test/fixtures/tx-002-conflict.edn"))
           s0 (store/init! b h (get-in fx1 [:store :capabilities]) {:format-version 1})
           r1 (store/commit! b s0 (:plan fx1))
           s1 (store/open b h)
           r2 (store/commit! b s1 (second (:base-plans fx2)))
           s2 (store/open b h)]
       (is (= 1 (:format-version (store/read-descriptor b)) (:format-version s0) (:format-version s2)))
       (is (rev/legacy? s2))
       (is (= (get-in fx1 [:expected :revision-id]) (:revision-id r1) (tu/tx-001-revision-ids 1)) "format-1 revision ids are unchanged")
       (is (contains? (:revision r2) :roots))
       (is (= (select-keys (:store r2) [:head :heads :revisions :trees :edges :objects])
              (select-keys s2 [:head :heads :revisions :trees :edges :objects])))
       (is (:ok? (store/verify b s2)))
       (is (= (sort (:edges s2)) (rev/active-edges s2)))
       (is (= (sort (keys (:trees s2))) (rev/tree-ids s2)))
       (is (every? (fn [[tid t]] (= [(tree/descriptor-id h (:descriptor t)) (tree/merkle-root t)] (rev/tree-entry s2 tid))) (:trees s2)))
       (is (= :plan/conflict (check-of #(store/commit! b s2 (:concurrent-plan fx2)))) "conflict detection of the legacy engine"))))
