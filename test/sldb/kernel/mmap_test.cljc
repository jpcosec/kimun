(ns sldb.kernel.mmap-test
  (:require [clojure.test :refer [deftest is testing]]
            [sldb.kernel.test-util :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [sldb.host.default :as host]
            [sldb.kernel.canon :as canon]
            [sldb.kernel.mmap :as mmap]))

(def h host/host)
(def B mmap/leaf-capacity)

(defn- hid "A 64-hex id key." [i] (canon/digest h [:k i]))

(defn- counting
  "[load calls]: a load fn over the object map `objs` and the atom of ids it was asked for."
  [objs]
  (let [calls (atom [])]
    [(fn [id] (swap! calls conj id) (get objs id)) calls]))

(defn- step
  "Applies `changes` to state {:root :objs}, accumulating the new objects."
  [{:keys [root objs]} changes]
  (let [{:keys [root new]} (mmap/update-many h objs root changes)]
    {:root root :objs (merge objs new) :new new}))

(def start (let [{:keys [root new]} (mmap/empty h)] {:root root :objs new}))

(defn- build [m] (step start m))

(defn- reachable [objs root]
  (loop [todo [root] seen #{}]
    (if-some [id (first todo)]
      (let [o (get objs id)]
        (assert (mmap/node? o) (str "not loadable: " id))
        (recur (into (vec (rest todo)) (mmap/child-ids o)) (conj seen id)))
      seen)))

(defn- model-step [m changes]
  (reduce (fn [m [k v]] (if (= mmap/removal v) (dissoc m k) (assoc m k v))) m changes))

(defn- model-diff [a b]
  (into {} (for [k (distinct (concat (keys a) (keys b)))
                 :when (or (not= (contains? a k) (contains? b k)) (not= (get a k) (get b k)))]
             [k (cond-> {} (contains? a k) (assoc :before (get a k)) (contains? b k) (assoc :after (get b k)))])))

;; ---------------------------------------------------------------- units

(deftest empty-map
  (let [{:keys [root new]} (mmap/empty h)]
    (is (= {root mmap/empty-leaf} new))
    (is (= root (canon/digest h mmap/empty-leaf)))
    (is (= 0 (mmap/count new root)))
    (is (nil? (seq (mmap/entries new root))))
    (is (nil? (mmap/lookup h new root (hid 1))))
    (is (= :nf (mmap/lookup h new root (hid 1) :nf)))
    (is (false? (mmap/has-key? h new root (hid 1))))
    (is (= {} (mmap/diff new root root)))
    (is (= root (:root (mmap/from-entries h {}))))
    (testing "an empty change set is a no-op that loads nothing"
      (let [[load calls] (counting new)]
        (is (= {:root root :new {}} (mmap/update-many h load root {})))
        (is (empty? @calls))))))

(deftest node-shapes
  (is (mmap/node? mmap/empty-leaf))
  (is (mmap/leaf? {:mmap :leaf :entries [["a" 1]]}))
  (is (mmap/branch? {:mmap :branch :count 40 :children {"0" "x"}}))
  (is (= ["x" "y"] (mmap/child-ids {:mmap :branch :count 40 :children {"f" "y" "0" "x"}})))
  (is (= [] (mmap/child-ids mmap/empty-leaf)))
  (is (= 40 (mmap/node-count {:mmap :branch :count 40 :children {"0" "x"}})))
  (doseq [o [nil "id" [] ["a" "b"] [["a" "b"]] {:class :m :kind :k :content "x"} {:type :reference :from "a" :to "b"}
             {:node "n" :children []} {:tree "t" :root "r"} {:trees "a" :edges "b" :tx "c" :parents []}]]
    (is (not (mmap/node? o)) (pr-str o))))

(deftest key-paths
  (is (= (hid 1) (mmap/path h (hid 1))) "a 64-hex id is its own path")
  (doseq [k ["01HZXW5K3M9QJ2N8R7T6V4Y0AB" :kw ["a" 1] (subs (hid 1) 1) (str (hid 1) "0")
             (.toUpperCase ^String (hid 1))]]
    (is (= (canon/digest h k) (mmap/path h k)) (pr-str k))))

(deftest single-leaf
  (let [m (into {} (for [i (range B)] [(hid i) i]))
        {:keys [root objs new]} (build m)]
    (is (= 1 (count new)) "B entries fit in one leaf")
    (is (mmap/leaf? (get objs root)))
    (is (= B (mmap/count objs root)))
    (is (= (sort-by first m) (mmap/entries objs root)) "entries in path (= id) order")
    (is (= (sort (keys m)) (mmap/keys objs root)))
    (is (= 7 (mmap/lookup h objs root (hid 7))))
    (is (mmap/has-key? h objs root (hid 7)))
    (is (not (mmap/has-key? h objs root (hid B))))
    (testing "nil and false values are present values"
      (let [s (step {:root root :objs objs} {(hid 0) nil (hid 1) false})]
        (is (mmap/has-key? h (:objs s) (:root s) (hid 0)))
        (is (= :nf (mmap/lookup h (:objs s) (:root s) (hid B) :nf)))
        (is (nil? (mmap/lookup h (:objs s) (:root s) (hid 0) :nf)))
        (is (false? (mmap/lookup h (:objs s) (:root s) (hid 1) :nf)))))))

(deftest split-and-collapse
  (let [ks (map hid (range (inc B)))
        small (build (zipmap (butlast ks) (repeat true)))
        big (step small {(last ks) true})]
    (testing "the (B+1)-th entry splits the leaf into a branch carrying the count"
      (let [r (get (:objs big) (:root big))]
        (is (mmap/branch? r))
        (is (= (inc B) (:count r) (mmap/count (:objs big) (:root big))))
        (is (every? mmap/leaf? (map (:objs big) (mmap/child-ids r))))
        (is (= (set (keys (:new big))) (reachable (:objs big) (:root big))) "every node of the split is new")
        (is (= (:root big) (:root (mmap/from-entries h (zipmap ks (repeat true))))))))
    (testing "removing it collapses back to the very same leaf"
      (let [back (step big {(last ks) mmap/removal})]
        (is (= (:root small) (:root back)))
        (is (= [(:root small)] (keys (:new back))))))
    (testing "removing everything returns to the empty map"
      (is (= (:root start) (:root (step big (zipmap ks (repeat mmap/removal)))))))))

;; A leaf does not record its depth: the leaf of an old map can be the node of
;; a subtree one or more levels away in the new map. `:new` must not hold it.

(defn- hx "A 64-hex key whose path starts with `prefix`." [prefix]
  (apply str prefix (repeat (- 64 (clojure.core/count prefix)) "0")))

(defn- fresh-only?
  "True when `:new` of state `after` holds exactly the nodes reachable from its
   root that are not reachable from the root of `before`."
  [before after]
  (= (set (keys (:new after)))
     (reduce disj (reachable (:objs after) (:root after)) (reachable (:objs before) (:root before)))))

(deftest a-split-does-not-emit-the-old-leaf-again
  (testing "counterexample of new-objects-hash-to-their-ids-and-close-the-root (seeds 1791284032337, 1791284909047)"
    (let [before (build {(hid 0) true})
          after (step before (into {"tree-0" true} (for [i (range 1 16)] [(hid i) true])))
          r (get (:objs after) (:root after))]
      (is (mmap/branch? r))
      (is (= (:root before) (get (:children r) (subs (hid 0) 0 1)))
          "the child holding only the old entry is the old root leaf, one level down")
      (is (not (contains? (:new after) (:root before))))
      (is (fresh-only? before after))
      (is (= (:root after) (:root (mmap/from-entries h (into {"tree-0" true} (for [i (range 16)] [(hid i) true]))))))))
  (testing "the old leaf survives several levels down a chain of single-child branches"
    (let [old (into {} (for [i (range B)] [(hx (str "abc" (format "%x" i))) i]))
          before (build old)
          after (step before {(hx "abd") :d})
          chain (take-while some? (iterate #(some->> (mmap/child-ids (get (:objs after) %)) seq first) (:root after)))]
      (is (= 4 (clojure.core/count (:new after))) "the three branches down to the split point and the leaf of the new key")
      (is (not (contains? (:new after) (:root before))))
      (is (= (:root before) (get (:children (get (:objs after) (nth chain 2))) "c")) "the old root leaf, three levels down")
      (is (fresh-only? before after))
      (is (= (:root after) (:root (mmap/from-entries h (assoc old (hx "abd") :d)))))
      (testing "and a rewritten old leaf is new"
        (let [after (step before {(hx "abd") :d (hx "abc0") :changed})]
          (is (fresh-only? before after))
          (is (= 5 (clojure.core/count (:new after)))))))))

(deftest a-collapse-does-not-emit-the-surviving-leaf-again
  (let [keep (into {} (for [i (range 3)] [(hx (str "a" i)) i]))
        gone (into {} (for [i (range B)] [(hx (str "b" (format "%x" i))) i]))
        before (build (merge keep gone))
        kept-leaf (get (:children (get (:objs before) (:root before))) "a")]
    (testing "all the entries left sit in one untouched child: the root becomes that child, nothing is new, nothing is loaded below it"
      (let [[load calls] (counting (:objs before))
            r (mmap/update-many h load (:root before) (zipmap (keys gone) (repeat mmap/removal)))]
        (is (= {:root kept-leaf :new {}} r))
        (is (= (:root (build keep)) kept-leaf))
        (is (not (some #{kept-leaf} @calls)))))
    (testing "through two levels"
      (let [deep (into {} (for [i (range 3)] [(hx (str "cc" i)) i]))
            side (into {} (for [i (range B)] [(hx (str "cd" (format "%x" i))) i]))
            before (build (merge deep side gone))
            after (step before (zipmap (concat (keys side) (keys gone)) (repeat mmap/removal)))]
        (is (= {} (:new after)))
        (is (= (:root (build deep)) (:root after)))
        (is (contains? (reachable (:objs before) (:root before)) (:root after)))))
    (testing "a collapse that changes the surviving leaf, or merges two children, is one new leaf"
      (let [a (step before (assoc (zipmap (keys gone) (repeat mmap/removal)) (hx "a0") :changed))
            b (step before (zipmap (drop 2 (sort (keys gone))) (repeat mmap/removal)))]
        (is (fresh-only? before a))
        (is (= 1 (clojure.core/count (:new a))))
        (is (= (:root a) (:root (build (assoc keep (hx "a0") :changed)))))
        (is (fresh-only? before b))
        (is (= 1 (clojure.core/count (:new b))))
        (is (mmap/leaf? (get (:objs b) (:root b))))))
    (testing "a branch that loses a child but stays a branch is new, its untouched children are not"
      (let [more (into {} (for [i (range B)] [(hx (str "e" (format "%x" i))) i]))
            before (build (merge keep gone more))
            after (step before (zipmap (keys gone) (repeat mmap/removal)))]
        (is (= [(:root after)] (keys (:new after))))
        (is (fresh-only? before after))))))

(deftest no-ops-create-nothing
  (let [m (into {} (for [i (range 500)] [(hid i) [i]]))
        {:keys [root objs]} (build m)]
    (testing "writing the same value again"
      (is (= {:root root :new {}} (mmap/update-many h objs root {(hid 3) [3] (hid 400) '(400)}))))
    (testing "removing an absent key, also where the branch has no child"
      (is (= {:root root :new {}}
             (mmap/update-many h objs root (into {} (for [i (range 1000 1100)] [(hid i) mmap/removal]))))))
    (testing "a real change writes only the nodes on its path"
      (let [[load calls] (counting objs)
            {r2 :root new :new} (mmap/update-many h load root {(hid 3) :changed})
            depth (count @calls)]
        (is (not= root r2))
        (is (= depth (count new)) "one new node per level of the path")
        (is (<= depth 3))
        (is (= :changed (mmap/lookup h (merge objs new) r2 (hid 3))))))))

(deftest reads-touch-one-node-per-level
  (let [{:keys [root objs]} (build (into {} (for [i (range 2000)] [(hid i) i])))
        [load calls] (counting objs)]
    (is (= 1234 (mmap/lookup h load root (hid 1234))))
    (is (<= 2 (count @calls) 4))
    (reset! calls [])
    (is (= 2000 (mmap/count load root)))
    (is (= [root] @calls))
    (reset! calls [])
    (is (= 3 (count (take 3 (mmap/entries load root)))))
    (is (< (count @calls) 12) "entries is lazy")))

(deftest diff-descends-only-where-ids-differ
  (let [m (into {} (for [i (range 2000)] [(hid i) i]))
        a (build m)
        b (step a {(hid 5) :five (hid 6) mmap/removal (hid 5000) :new})
        objs (:objs b)]
    (testing "equal roots load nothing"
      (let [[load calls] (counting objs)]
        (is (= {} (mmap/diff load (:root a) (:root a))))
        (is (empty? @calls))))
    (let [[load calls] (counting objs)]
      (is (= {(hid 5) {:before 5 :after :five} (hid 6) {:before 6} (hid 5000) {:after :new}}
             (mmap/diff load (:root a) (:root b))))
      (is (<= (count @calls) (* 2 3 4)) "only the paths of the three changed keys, on both sides")
      (is (< (count @calls) (/ (count (reachable objs (:root a))) 4))))
    (testing "leaf against branch"
      (let [s (build {(hid 1) 1})]
        (is (= (model-diff {(hid 1) 1} m) (mmap/diff (merge objs (:objs s)) (:root s) (:root a))))))))

(deftest non-id-keys-and-normalization
  (let [ulids (for [i (range 100)] (str "01HZXW5K3M9QJ2N8R7T6V4" (char (+ 65 (quot i 10))) (char (+ 65 (mod i 10))) "00"))
        m (into {} (for [u ulids] [u [(hid 1) (hid 2)]]))
        {:keys [root objs]} (build m)]
    (is (= 100 (mmap/count objs root)))
    (is (= m (into {} (mmap/entries objs root))))
    (is (= [(hid 1) (hid 2)] (mmap/lookup h objs root (first ulids))))
    (is (= root (:root (mmap/from-entries h (shuffle (seq m))))))
    (testing "values are stored normalized, so a list equals the vector already there"
      (is (= {:root root :new {}} (mmap/update-many h objs root {(first ulids) (list (hid 1) (hid 2))}))))
    (testing "keys are normalized (NFC) on write and on read"
      (let [s (build {"é" 1})]
        (is (= 1 (mmap/lookup h (:objs s) (:root s) "é")))
        (is (= ["é"] (mmap/keys (:objs s) (:root s))))))))

(deftest missing-node-raises
  (is (thrown-with-msg? #?(:clj clojure.lang.ExceptionInfo :cljs js/Error) #"mmap"
                        (mmap/lookup h {} "nope" (hid 1)))))

;; ---------------------------------------------------------------- properties

(def gen-key
  "Keys from a small universe so removals and overwrites hit; ids and non-ids."
  (gen/one-of [(gen/fmap hid (gen/choose 0 150))
               (gen/fmap #(str "tree-" %) (gen/choose 0 40))]))

(def gen-val (gen/one-of [(gen/return true) gen/small-integer (gen/vector (gen/fmap hid (gen/choose 0 9)) 0 3)]))

(def gen-contents (gen/map gen-key gen-val {:max-elements 120}))

(def gen-changes
  (gen/map gen-key (gen/frequency [[3 gen-val] [2 (gen/return mmap/removal)]]) {:max-elements 60}))

(defn- run [batches] (reduce step start batches))

(defspec history-independence 60
  (prop/for-all [m gen-contents
                 noise gen-contents
                 seed gen/small-integer
                 sizes (gen/vector (gen/choose 1 20) 1 30)]
    (let [canonical (:root (mmap/from-entries h m))
          shuffled (let [v (vec (sort-by (fn [[k _]] (canon/digest h [seed k])) m))] v)
          batches (loop [es shuffled ss (cycle sizes) acc []]
                    (if (empty? es) acc (recur (drop (first ss) es) (rest ss) (conj acc (into {} (take (first ss) es))))))
          noise-only (apply dissoc noise (keys m))
          detour (concat [noise] batches [(zipmap (keys noise-only) (repeat mmap/removal))])]
      (and (= canonical (:root (build m)))
           (= canonical (:root (run batches)))
           (= canonical (:root (run (reverse batches))))
           (= canonical (:root (run detour)))))))

(defspec insert-then-remove-returns-to-the-smaller-set 60
  (prop/for-all [m gen-contents extra gen-contents]
    (let [extra (apply dissoc extra (keys m))
          small (build m)
          big (step small extra)
          back (step big (zipmap (keys extra) (repeat mmap/removal)))]
      (and (= (:root small) (:root back))
           (= (:root big) (:root (mmap/from-entries h (merge m extra))))))))

(defspec agrees-with-a-map-model 60
  (prop/for-all [ops (gen/vector gen-changes 1 12)]
    (loop [s start model {} ops ops]
      (let [{:keys [root objs]} s]
        (and (= model (into {} (mmap/entries objs root)))
             (= (count model) (mmap/count objs root) (count (mmap/keys objs root)))
             (= (map first (mmap/entries objs root)) (sort-by #(mmap/path h %) (keys model)))
             (every? (fn [[k v]] (and (= v (mmap/lookup h objs root k ::nf)) (mmap/has-key? h objs root k))) model)
             (= ::nf (mmap/lookup h objs root "absent" ::nf))
             (= root (:root (mmap/from-entries h model)))
             (or (empty? ops) (recur (step s (first ops)) (model-step model (first ops)) (rest ops))))))))

(defspec diff-equals-the-model-diff 60
  (prop/for-all [a gen-contents changes (gen/vector gen-changes 0 3) b gen-contents]
    (let [sa (build a)
          near (reduce step sa changes)
          sb (build b)
          objs (merge (:objs near) (:objs sb))]
      (and (= (model-diff a (reduce model-step a changes)) (mmap/diff objs (:root sa) (:root near)))
           (= (model-diff a b) (mmap/diff objs (:root sa) (:root sb)))
           (= (model-diff b a) (mmap/diff objs (:root sb) (:root sa)))))))

(defn- gen-keys-under
  "Generator of 64-hex keys (their own path) under one of `prefixes`."
  [prefixes]
  (gen/fmap (fn [[prefix i]] (hx (str prefix (format "%x" i)))) (gen/tuple (gen/elements prefixes) (gen/choose 0 15))))

(def gen-moving-leaf-ops
  "Change batches built so that a leaf moves between depths: a few entries under
   one deep prefix (one leaf), then entries under sibling prefixes at several
   depths (the leaf overflows and the first entries stay together, levels down),
   then their removal (the branches collapse back onto that leaf), then noise."
  (let [side-prefixes ["0" "1" "a0" "a1" "ab0" "ab1" "abd"]
        gen-change (gen/frequency [[3 gen/small-integer] [2 (gen/return mmap/removal)]])]
    (gen/fmap (fn [[kept side noise]]
                (let [side (apply dissoc side (keys kept))]
                  (concat [kept side (zipmap (keys side) (repeat mmap/removal))] noise)))
              (gen/tuple (gen/map (gen-keys-under ["abc"]) gen/small-integer {:min-elements 1 :max-elements B})
                         (gen/map (gen-keys-under side-prefixes) gen/small-integer {:max-elements 40})
                         (gen/vector (gen/map (gen-keys-under (conj side-prefixes "abc")) gen-change {:max-elements 25})
                                     0 4)))))

(defn- new-is-exactly-the-fresh-reachable-nodes [ops]
  (loop [s start model {} ops ops]
    (or (empty? ops)
        (let [s' (step s (first ops))
              model' (model-step model (first ops))]
          (and (every? (fn [[id o]] (and (mmap/node? o) (= id (canon/digest h o)))) (:new s'))
               (fresh-only? s s')
               (= model' (into {} (mmap/entries (:objs s') (:root s'))))
               (= (:root s') (:root (mmap/from-entries h model')))
               (recur s' model' (rest ops)))))))

(defspec new-is-exactly-the-nodes-not-reachable-from-the-old-root 60
  (prop/for-all [ops (gen/vector gen-changes 1 8)]
    (new-is-exactly-the-fresh-reachable-nodes ops)))

(defspec moved-leaves-are-never-emitted-again 60
  (prop/for-all [ops gen-moving-leaf-ops]
    (new-is-exactly-the-fresh-reachable-nodes ops)))

(defspec new-objects-hash-to-their-ids-and-close-the-root 60
  (prop/for-all [ops (gen/vector gen-changes 1 8)]
    (loop [s start ops ops]
      (or (empty? ops)
          (let [s' (step s (first ops))
                new (:new s')
                live (reachable (:objs s') (:root s'))]
            (and (every? (fn [[id o]] (and (mmap/node? o) (= id (canon/digest h o)))) new)
                 (every? live (keys new))
                 (empty? (filter (reachable (:objs s) (:root s)) (keys new)))
                 (every? #(contains? (merge (:objs s) new) %) live)
                 (recur s' (rest ops))))))))
