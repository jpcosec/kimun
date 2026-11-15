(ns kimun.kernel.route-tree-test
  "The route map as a Merkle tree by path (route schema 2): the tree is a
   function of its entries alone, it answers what the flat map of schema 1
   answers, the root ignores tree ids, and a change or a comparison reads the
   nodes on the way to what changed and no others. No store is involved."
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [kimun.kernel.test-util :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [kimun.host.default :as host]
            [kimun.kernel.canon :as canon]
            [kimun.kernel.route :as route]
            [kimun.kernel.route-tree :as rt]))

(def h host/host)

(defn- entry
  "A route entry at `path`; `v` varies its sensed fields, `tid` its tree id."
  ([path v] (entry path v (str "T-" path)))
  ([path v tid]
   {:path path :fingerprint (str "sha-256:" v) :class "TestDoc"
    :projection-hash (str "p" v) :content-hash (str "c" v) :tree-id tid}))

(defn- counting
  "[load calls]: a load fn over the object map `objs` and the atom of the ids
   it was asked for."
  [objs]
  (let [calls (atom [])]
    [(fn [id] (swap! calls conj id) (get objs id)) calls]))

(def start (let [e (rt/empty h)] {:object (:object e) :hash (:hash e) :count 0 :objs (:new e)}))

(defn- step
  "Applies `changes` to state `{:object :hash :objs}`, accumulating objects."
  [{:keys [object objs]} changes]
  (let [r (rt/update h objs object changes)]
    (assoc r :objs (merge objs (:new r)))))

(defn- build
  "The state of the tree of `entries`, built from nothing."
  [entries]
  (let [r (rt/from-entries h entries)] (assoc r :objs (:new r))))

(defn- corpus
  "`n` entries spread over `dirs` directories two levels deep."
  [n dirs]
  (for [i (range n)]
    (entry (str "d" (mod i dirs) "/s" (mod (quot i dirs) 7) "/f" i ".md") i)))

;; ---------------------------------------------------------------- shape

(deftest the-empty-tree-is-a-loadable-node
  (let [e (rt/empty h)]
    (is (rt/node? (get (:new e) (:object e))))
    (is (= 0 (:count e)))
    (is (= [] (rt/entries h (:new e) (:object e))))
    (is (nil? (rt/lookup h (:new e) (:object e) "a.md")))
    (is (not= (:hash e) (:object e)) "the route hash and the object id are two digests")))

(deftest an-entry-is-found-at-its-path-and-nowhere-else
  (let [s (build [(entry "docs/a.md" 1) (entry "docs/sub/b.md" 2) (entry "top.md" 3)])]
    (is (= 3 (:count s)))
    (is (= (entry "docs/sub/b.md" 2) (rt/lookup h (:objs s) (:object s) "docs/sub/b.md")))
    (is (= (entry "top.md" 3) (rt/lookup h (:objs s) (:object s) "./top.md")) "the path is normalized")
    (is (nil? (rt/lookup h (:objs s) (:object s) "docs")) "a directory is not an entry")
    (is (nil? (rt/lookup h (:objs s) (:object s) "docs/a.md/x")))
    (is (nil? (rt/lookup h (:objs s) (:object s) "../a.md")) "an unsafe path finds nothing")
    (is (= ["docs/a.md" "docs/sub/b.md" "top.md"] (mapv :path (rt/entries h (:objs s) (:object s)))))))

(deftest a-path-may-be-an-entry-and-a-directory
  ;; schema 1 accepts `a` and `a/b.md` together; the tree must not reject what it accepted
  (let [es [(entry "a" 1) (entry "a/b.md" 2)]
        s (build es)]
    (is (= (set es) (set (rt/entries h (:objs s) (:object s)))))
    (is (= (entry "a" 1) (rt/lookup h (:objs s) (:object s) "a")))
    (let [s' (step s {"a" rt/removal})]
      (is (= [(entry "a/b.md" 2)] (rt/entries h (:objs s') (:object s')))))))

(deftest unsafe-paths-and-duplicates-are-refused
  (is (= :route/invalid (:type (ex-data (try (step start {"../x.md" (entry "../x.md" 1)}) (catch Exception e e))))))
  (is (= :route/invalid (:type (ex-data (try (step start {"/abs.md" rt/removal}) (catch Exception e e))))))
  (is (= :route/ambiguous (:type (ex-data (try (build [(entry "a.md" 1) (entry "./a.md" 2)]) (catch Exception e e)))))))

;; ---------------------------------------------------------------- the root

(deftest the-root-ignores-tree-ids-and-the-object-id-does-not
  (let [a (build [(entry "docs/a.md" 1 "T1") (entry "docs/b.md" 2 "T2")])
        b (build [(entry "docs/a.md" 1 "OTHER") (entry "docs/b.md" 2 "T2")])]
    (is (= (:hash a) (:hash b)) "new ULIDs, same route root")
    (is (not= (:object a) (:object b)) "the locators are covered by the object id")
    (is (= "OTHER" (:tree-id (rt/lookup h (:objs b) (:object b) "docs/a.md"))))))

(deftest every-sensed-field-and-the-path-move-the-root
  (let [base (build [(entry "docs/a.md" 1)])]
    (doseq [k [:fingerprint :class :projection-hash :content-hash]]
      (is (not= (:hash base) (:hash (build [(assoc (entry "docs/a.md" 1) k "other")]))) (str k)))
    (is (not= (:hash base) (:hash (build [(assoc (entry "docs/a.md" 1) :path "docs/b.md")]))))
    (is (not= (:hash base) (:hash (build [(assoc (entry "docs/a.md" 1) :path "a.md")]))))))

(deftest removing-everything-gives-the-empty-tree
  (let [es (corpus 40 3)
        s (step (build es) (into {} (map (fn [e] [(:path e) rt/removal])) es))]
    (is (= (:hash start) (:hash s)))
    (is (= (:object start) (:object s)))
    (is (= 0 (:count s)))))

(deftest removing-an-absent-path-changes-nothing
  (let [s (build (corpus 10 2))
        s' (step s {"nowhere/x.md" rt/removal "d0/s0/none.md" rt/removal})]
    (is (= (:object s) (:object s')))
    (is (= (:hash s) (:hash s')))))

;; ---------------------------------------------------------------- equivalence

(def gen-path
  (gen/fmap (fn [segs] (clojure.string/join "/" segs))
            (gen/vector (gen/elements ["a" "b" "c" "d" "x.md" "y.md" "z"]) 1 4)))

(def gen-ops
  "A history: a vector of `[path value-or-nil]`; nil removes the path."
  (gen/vector (gen/tuple gen-path (gen/one-of [(gen/return nil) (gen/choose 0 5)])) 0 40))

(defn- replayed
  "The entry map a history leads to: last write wins, as in a plain map."
  [ops]
  (reduce (fn [m [p v]] (if (nil? v) (dissoc m p) (assoc m p (entry p v)))) {} ops))

(defspec the-tree-is-a-function-of-its-entries 60
  ;; one write per operation, one batch, and a build from nothing: the same tree
  (prop/for-all [ops gen-ops]
    (let [final (replayed ops)
          one-by-one (reduce (fn [s [p v]] (step s {p (if (nil? v) rt/removal (entry p v))})) start ops)
          batch (step start (into {} (map (fn [[p v]] [p (if (nil? v) rt/removal (entry p v))])) ops))
          scratch (build (vals final))
          last-writes (step start (into {} (map (fn [[p e]] [p e])) final))]
      (and (= (:object scratch) (:object one-by-one) (:object last-writes))
           (= (:hash scratch) (:hash one-by-one) (:hash last-writes))
           (= (count final) (:count one-by-one) (:count scratch))
           ;; a batch holds one value per path: it equals the history only when no path repeats
           (or (not= (count ops) (count (distinct (map first ops))))
               (= (:object scratch) (:object batch)))))))

(defspec the-tree-answers-what-the-flat-map-answers 60
  (prop/for-all [ops gen-ops]
    (let [final (replayed ops)
          s (reduce (fn [s [p v]] (step s {p (if (nil? v) rt/removal (entry p v))})) start ops)
          flat (route/canonical-entries h (vals final))]
      (and (= flat (rt/entries h (:objs s) (:object s)))
           (every? (fn [e] (= e (rt/lookup h (:objs s) (:object s) (:path e)))) flat)
           ;; the schema-1 root and digest of the entries read back are the ones of the flat map
           (= (route/root h (vals final)) (route/root h (rt/entries h (:objs s) (:object s))))
           (= (route/entries-digest h (vals final)) (route/entries-digest h (rt/entries h (:objs s) (:object s))))))))

(defspec the-diff-of-two-trees-is-the-diff-of-their-entries 60
  (prop/for-all [ops-a gen-ops ops-b gen-ops]
    (let [a (replayed ops-a) b (replayed ops-b)
          sa (build (vals a)) sb (build (vals b))]
      (= (route/diff h (vals a) (vals b))
         (rt/diff h (:objs sa) (:object sa) (:objs sb) (:object sb))))))

(defspec equal-roots-mean-equal-sensed-entries 60
  (prop/for-all [ops-a gen-ops ops-b gen-ops]
    (let [strip (fn [m] (set (map #(dissoc % :tree-id) (vals m))))
          a (replayed ops-a) b (replayed ops-b)]
      (= (= (strip a) (strip b))
         (= (:hash (build (vals a))) (:hash (build (vals b))))))))

(deftest a-tree-id-change-alone-is-not-a-diff
  (let [a (build [(entry "docs/a.md" 1 "T1")])
        b (build [(entry "docs/a.md" 1 "T9")])]
    (is (= {:added [] :removed [] :changed []} (rt/diff h (:objs a) (:object a) (:objs b) (:object b))))))

;; ---------------------------------------------------------------- local cost

(deftest a-write-reads-and-writes-the-nodes-on-its-path-only
  ;; 2000 entries in 20 directories of 7 subdirectories: a change in one file
  ;; touches three NODES (the root, its directory and its subdirectory) whatever
  ;; the total. This counts nodes, not work: each of the three is hashed and
  ;; rewritten whole, see `a-wide-directory-is-rewritten-whole`
  (doseq [n [200 2000]]
    (let [s (build (corpus n 20))
          total (count (:objs s))
          [load calls] (counting (:objs s))
          r (rt/update h load (:object s) {"d3/s2/f43.md" (entry "d3/s2/f43.md" "edited")})]
      (is (= n (:count r)))
      (is (= 3 (count @calls)) (str "nodes read with " n " entries"))
      (is (= 3 (count (:new r))) (str "nodes written with " n " entries"))
      (is (< 3 total))
      (testing "a new directory adds its own nodes"
        (let [[load calls] (counting (:objs s))
              r (rt/update h load (:object s) {"fresh/deep/x.md" (entry "fresh/deep/x.md" 1)})]
          (is (= 1 (count @calls)) "only the root exists on that path")
          (is (= 3 (count (:new r)))))))))

(deftest a-lookup-reads-one-node-per-segment
  (let [s (build (corpus 2000 20))
        [load calls] (counting (:objs s))]
    (is (some? (rt/lookup h load (:object s) "d3/s2/f43.md")))
    (is (= 3 (count @calls)))
    (reset! calls [])
    (is (nil? (rt/lookup h load (:object s) "nowhere/at/all.md")))
    (is (= 1 (count @calls)) "the descent stops at the first missing segment")))

(deftest a-diff-goes-down-only-where-the-hashes-differ
  (let [a (build (corpus 2000 20))
        b (step a {"d3/s2/f43.md" (entry "d3/s2/f43.md" "edited")
                   "d3/s2/new.md" (entry "d3/s2/new.md" 7)
                   "d9/s0/f9.md" rt/removal})
        [load-a calls-a] (counting (:objs a))
        [load-b calls-b] (counting (:objs b))
        d (rt/diff h load-a (:object a) load-b (:object b))]
    (is (= ["d3/s2/new.md"] (mapv :path (:added d))))
    (is (= ["d9/s0/f9.md"] (mapv :path (:removed d))))
    (is (= ["d3/s2/f43.md"] (mapv :path (:changed d))))
    ;; root + d3 + d3/s2 + d9 + d9/s0 on each side, out of 161 nodes (1 + 20 + 140;
    ;; the object map also holds the empty root the build started from)
    (is (= 5 (count @calls-a)))
    (is (= 5 (count @calls-b)))
    (is (= 162 (count (:objs a))))
    (testing "equal trees are compared at the root"
      (let [[la ca] (counting (:objs a)) [lb cb] (counting (:objs a))]
        (is (= {:added [] :removed [] :changed []} (rt/diff h la (:object a) lb (:object a))))
        (is (= [1 1] [(count @ca) (count @cb)]))))))

(deftest a-wide-directory-is-rewritten-whole
  ;; the limit of one node per directory: there is no fan-out inside it. With every
  ;; file in ONE directory a write still touches two nodes, but the directory node
  ;; holds every entry and is rewritten whole, so the bytes grow with the routes.
  (let [bytes (fn [r] (reduce + (map #(count (canon/canon-str h %)) (vals (:new r)))))
        flat (fn [n] (build (for [i (range n)] (entry (str "all/f" i ".md") i))))
        write (fn [s] (rt/update h (:objs s) (:object s) {"all/f7.md" (entry "all/f7.md" "edited")}))
        small (write (flat 200))
        large (write (flat 2000))
        spread (let [s (build (corpus 2000 20))]
                 (rt/update h (:objs s) (:object s) {"d3/s2/f43.md" (entry "d3/s2/f43.md" "edited")}))]
    (is (= 2 (count (:new small)) (count (:new large))) "two nodes either way")
    (is (= 2000 (count (:children (some #(when (contains? (:children %) "f7.md") %) (vals (:new large))))))
        "and one of them carries all 2000 entries")
    (is (< (* 8 (bytes small)) (bytes large)) "ten times the routes in the directory, about ten times the bytes")
    (is (< (* 20 (bytes spread)) (bytes large))
        "the same 2000 routes spread over directories cost a fraction: the shape of the tree decides")))

;; ---------------------------------------------------------------- damage

(deftest a-node-that-does-not-match-its-parent-is-reported
  (let [s (build [(entry "docs/a.md" 1) (entry "docs/b.md" 2) (entry "other/c.md" 3)])
        root (get (:objs s) (:object s))
        docs-id (get-in root [:children "docs" :dir :object])
        other-id (get-in root [:children "other" :dir :object])
        swapped (assoc (:objs s) docs-id (get (:objs s) other-id))
        thrown (fn [f] (:type (ex-data (try (f) (catch Exception e e)))))]
    (is (= :route/corrupt (thrown #(rt/lookup h swapped (:object s) "docs/a.md"))))
    (is (= :route/corrupt (thrown #(rt/entries h swapped (:object s)))))
    (is (= (entry "other/c.md" 3) (rt/lookup h swapped (:object s) "other/c.md"))
        "the damage is local: the other branch still answers")
    (is (= :route/missing-node (thrown #(rt/lookup h (dissoc (:objs s) docs-id) (:object s) "docs/a.md"))))
    (is (= :route/missing-node (thrown #(rt/lookup h (assoc (:objs s) docs-id {:not "a node"}) (:object s) "docs/a.md"))))
    (is (= :route/corrupt (thrown #(rt/root-node h (:objs s) (:hash start) (:object s)))))
    (is (rt/node? (rt/root-node h (:objs s) (:hash s) (:object s))))))

(deftest a-node-altered-only-in-a-tree-id-is-reported
  ;; the route hash and the count leave the locator out, so only the object id
  ;; can tell: every read must check it, at the root and below
  (let [s (build [(entry "docs/sub/a.md" 1 "T1") (entry "docs/b.md" 2 "T2") (entry "top.md" 3 "T3")
                  (entry "other/c.md" 4 "T4")])
        root (get (:objs s) (:object s))
        docs-ref (get-in root [:children "docs" :dir])
        docs (get (:objs s) (:object docs-ref))
        sub-ref (get-in docs [:children "sub" :dir])
        sub (get (:objs s) (:object sub-ref))
        thrown (fn [f] (:type (ex-data (try (f) (catch Exception e e)))))
        ;; differs inside docs/sub, so a diff has to go down to every node altered below
        other (build [(entry "docs/sub/a.md" 7 "T1") (entry "docs/b.md" 2 "T2") (entry "top.md" 3 "T3")
                      (entry "other/c.md" 4 "T4")])]
    (doseq [[where id node at]
            [["a leaf directory" (:object sub-ref) sub [:children "a.md" :entry :tree-id]]
             ["a middle directory" (:object docs-ref) docs [:children "b.md" :entry :tree-id]]
             ["the root" (:object s) root [:children "top.md" :entry :tree-id]]]]
      (testing where
        (let [altered (assoc-in node at "FORGED")
              objs (assoc (:objs s) id altered)]
          (is (= (rt/node-hash h node) (rt/node-hash h altered)) "the route hash does not see the change")
          (is (= (rt/node-count node) (rt/node-count altered)))
          (is (= :route/corrupt (thrown #(rt/lookup h objs (:object s) "docs/sub/a.md"))) "lookup")
          (is (= :route/corrupt (thrown #(rt/entries h objs (:object s)))) "entries")
          (is (= :route/corrupt (thrown #(rt/update h objs (:object s) {"docs/sub/a.md" (entry "docs/sub/a.md" 9)}))) "update")
          (is (= :route/corrupt (thrown #(rt/diff h objs (:object s) (:objs other) (:object other)))) "diff, damaged side first")
          (is (= :route/corrupt (thrown #(rt/diff h (:objs other) (:object other) objs (:object s)))) "diff, damaged side second")
          (when (= id (:object s))
            (is (= :route/corrupt (thrown #(rt/root-node h objs (:hash s) (:object s)))) "root-node")))))
    (testing "the damage stays local: a branch that does not go through the altered node answers"
      (let [objs (assoc (:objs s) (:object sub-ref) (assoc-in sub [:children "a.md" :entry :tree-id] "FORGED"))]
        (is (= (entry "other/c.md" 4 "T4") (rt/lookup h objs (:object s) "other/c.md")))
        (is (= (entry "docs/b.md" 2 "T2") (rt/lookup h objs (:object s) "docs/b.md")))
        (is (rt/node? (rt/root-node h objs (:hash s) (:object s))) "the root itself is intact")))))

(deftest the-object-ids-below-a-node-are-its-directories
  (let [s (build [(entry "docs/a.md" 1) (entry "other/c.md" 3) (entry "top.md" 4)])
        root (get (:objs s) (:object s))]
    (is (= 2 (count (rt/child-ids root))))
    (is (set/subset? (set (rt/child-ids root)) (set (keys (:objs s)))))
    (is (= 3 (rt/node-count root)))))
