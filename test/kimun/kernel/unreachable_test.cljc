(ns kimun.kernel.unreachable-test
  "K5, first cut: the read-only inventory of stored objects the heads do not
   reach (`kimun.kernel.store/unreachable`, docs/v2/10 §11.3). What it lists is
   exactly what `verify` does not walk; a plan the log names is held, not a
   candidate; what a losing writer left and the roots later commits replaced
   are candidates with their tops marked; and removing every candidate from a
   COPY of the store leaves a store that opens, replays and verifies. The
   inventory itself writes nothing."
  (:require [babashka.fs :as bfs]
            [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.host.fs-store :as fs]
            [kimun.kernel.node :as node]
            [kimun.kernel.ports :as ports]
            [kimun.kernel.revision :as rev]
            [kimun.kernel.store :as store]
            [kimun.test-util :as ktu]))

(def h host/host)
(def TS "2026-08-29T12:00:00.000Z")

(defn- tid [i] (format "01ARZ3NDEKTSV4RRFFQ69G%04d" i))
(defn- path-of [i] (str "d" (mod i 3) "/f" i ".md"))

(defn- register-ops
  "The ops registering document `i` with sensed value `v` and its route."
  [i v]
  (let [root (node/make h :sign :block {:format :markdown :type :document :attrs {:n i}})
        ext (node/make h :sign :external
                       {:locator {:kind :file :path (path-of i)} :sample ""
                        :fingerprint "sha-256:1111111111111111111111111111111111111111111111111111111111111111"})]
    [{:op :add-node :node root}
     {:op :register-tree
      :tree {:kind :document :name (str "d" i) :class "C" :content-hash (str "content-" v) :cached? true
             :root (:id root)}
      :id (tid i) :root (:id root) :merkle-root (str "projection-" v)
      :route {:path (path-of i) :fingerprint (str "sha-256:" v) :class "C"
              :projection-hash (str "projection-" v) :content-hash (str "content-" v)}}
     {:op :add-node :node ext}
     {:op :add-edge :edge {:type :reference :from (:id root) :to (:id ext)
                           :evidence {:ref-hash (:id ext) :actor "jp"}}}]))

(defn- plan [base ops]
  {:plan/version 1 :base base :actor "jp" :engines {} :timestamp TS :ops (vec ops)})

(defn- fresh
  ([] (fresh {}))
  ([opts]
   (let [dir (str (ktu/temp-dir "kimun-unreachable-"))
         b (fs/backend dir)]
     [dir (store/init! b h {"jp" :all} opts)])))

(defn- commit!
  "Commits `ops` on the store at `dir`, opened fresh; returns the new store."
  [dir ops]
  (let [b (fs/backend dir)
        s (store/open b h)]
    (:store (store/commit! b s (plan (:head s) ops)))))

(defn- history
  "A store at `dir` after `n` commits: three documents, then edits of the first."
  ([n] (history n {}))
  ([n opts]
   (let [[dir _] (fresh opts)]
     (commit! dir (mapcat #(register-ops % 0) (range 3)))
     (doseq [k (range 1 n)] (commit! dir (register-ops 0 k)))
     dir)))

(defn- inventory [dir]
  (let [b (fs/backend dir)] (store/unreachable b (store/open b h))))

(defn- snapshot
  "Every file under `dir` with its content: what a read-only command must leave equal."
  [dir]
  (into (sorted-map)
        (map (fn [f] [(str (bfs/relativize dir f)) (slurp (str f))]))
        (filter bfs/regular-file? (bfs/glob dir "**" {:hidden true}))))

(defn- classes [items] (set (map :class items)))

;; ---------------------------------------------------------------- what is listed

(deftest the-inventory-is-what-verify-does-not-walk
  (let [dir (history 4)
        b (fs/backend dir)
        s (store/open b h)
        r (store/unreachable b s)
        stored (set (store/object-ids b))
        live (set/intersection stored (store/reachable s))
        listed (set (map :id (concat (:candidates r) (:held r) (:damaged r))))]
    (is (= (count stored) (:stored r)))
    (is (= (count live) (:reached r)))
    (is (= (:unreachable r) (count listed)))
    (is (= stored (set/union live listed)) "every stored object is reached or listed")
    (is (empty? (set/intersection live listed)) "and none is both")
    (is (pos? (:unreachable r)) "three later commits replaced roots")
    (is (true? (:stable? r)))
    (is (nil? (:uncommitted-tail r)))
    (is (= [] (:damaged r)))))

(deftest a-store-with-one-commit-has-replaced-nothing
  (let [dir (history 1)
        r (inventory dir)]
    (is (= [] (:candidates r)))
    (is (= [] (:held r)))
    (is (= (:stored r) (:reached r)))))

(deftest replaced-roots-are-candidates-and-their-tops-are-marked
  ;; each later commit replaces the heads map and the route tree nodes of the path it wrote
  (let [dir (history 4)
        r (inventory dir)
        roots (filter :root? (:candidates r))]
    (is (set/subset? (classes (:candidates r)) #{:mmap-leaf :mmap-branch :route-node})
        "only nodes of the heads map and of the route tree: nothing a revision names")
    (is (contains? (classes roots) :route-node))
    (is (contains? (classes roots) :mmap-leaf))
    (is (= 3 (count (filter #(= :route-node (:class %)) roots))) "one replaced route root per later commit")
    (is (= [] (:held r)) "format 4 keeps plans in the cache, not in objects/")))

(deftest the-flat-route-map-of-schema-1-is-a-candidate-once-replaced
  (let [dir (history 3 {:route-schema 1})
        r (inventory dir)]
    (is (= 2 (count (filter #(= :route-map (:class %)) (:candidates r)))))
    (is (every? :root? (filter #(= :route-map (:class %)) (:candidates r))))
    (testing "and the one the heads name is not"
      (is (not (contains? (set (map :id (:candidates r))) (:routes-object (store/read-heads (fs/backend dir)))))))
    (testing "after the route upgrade the last flat map is one more"
      (store/upgrade-routes! (fs/backend dir) h)
      (is (= 3 (count (filter #(= :route-map (:class %)) (:candidates (inventory dir)))))))))

;; ---------------------------------------------------------------- a losing writer

(deftest what-a-losing-writer-left-is-a-candidate-under-its-revision
  (let [dir (history 1)
        b1 (fs/backend dir) b2 (fs/backend dir)
        s1 (store/open b1 h) s2 (store/open b2 h)
        lost-plan (plan (:head s2) (register-ops 7 "lost"))
        lost-rev (:revision-id (rev/apply-plan s2 lost-plan))
        _ (store/commit! b1 s1 (plan (:head s1) (register-ops 5 "won")))
        refused (try (store/commit! b2 s2 lost-plan) nil (catch Exception e (:type (ex-data e))))
        r (inventory dir)
        by-id (into {} (map (juxt :id identity)) (:candidates r))]
    (is (= :store/stale refused))
    (is (= {:id lost-rev :class :revision :root? false} (get by-id lost-rev))
        "the revision that never became a head is listed; the heads map the loser wrote mentions it")
    (is (some #(and (:root? %) (#{:mmap-leaf :mmap-branch} (:class %))) (:candidates r))
        "and the top of what the loser left is that heads map")
    (is (= [lost-rev] (map :id (filter #(= :revision (:class %)) (:candidates r)))))
    (is (set/subset? #{:revision :node :edge :descriptor :mmap-leaf} (classes (:candidates r))))
    (is (every? (complement :root?) (filter #(#{:node :edge :descriptor} (:class %)) (:candidates r)))
        "what that revision reaches is below it, not a top")
    (is (nil? (get-in (store/open (fs/backend dir) h) [:routes (path-of 7)])) "the lost write is not in the store")
    (is (true? (:ok? (store/verify (fs/backend dir) (store/open (fs/backend dir) h)))))))

;; ---------------------------------------------------------------- plans in the CAS

(deftest a-plan-the-log-names-is-held-not-a-candidate
  ;; format 3 keeps the resolved plan of every transaction in objects/; the walk does
  ;; not follow it and replay needs it
  (let [[dir _] (fresh {:format-version 3})
        doc (fn [i] [{:op :add-node :node (node/make h :sign :block {:format :markdown :type :document :attrs {:n i}}) :as :r}
                     {:op :new-tree :tree {:kind :document :name (str i)} :id (tid i) :root :r}])
        _ (commit! dir (doc 0))
        _ (commit! dir (doc 1))
        logged (set (map :id (store/read-log (fs/backend dir))))
        r (inventory dir)]
    (is (= 2 (count logged)))
    (is (= logged (set (map :id (:held r)))) "both resolved plans, and nothing else")
    (is (every? #(= :plan (:class %)) (:held r)))
    (is (empty? (set/intersection logged (set (map :id (:candidates r))))))
    (testing "the plan of a transaction that lost the race is not in the log: a candidate, under its revision"
      (let [b1 (fs/backend dir) b2 (fs/backend dir)
            s1 (store/open b1 h) s2 (store/open b2 h)]
        (store/commit! b1 s1 (plan (:head s1) (doc 2)))
        (is (= :store/stale (try (store/commit! b2 s2 (plan (:head s2) (doc 3))) nil
                                 (catch Exception e (:type (ex-data e))))))
        (let [r (inventory dir)
              lost (filter #(= :plan (:class %)) (:candidates r))]
          (is (= 3 (count (:held r))))
          (is (= 1 (count lost)))
          (is (false? (:root? (first lost))) "its revision mentions it"))))))

;; ---------------------------------------------------------------- an unfinished commit

(deftest a-log-line-the-heads-do-not-record-is-named-and-its-objects-listed
  ;; the crash window of docs/v2/10 §7: the line was appended, the heads were not replaced
  (let [dir (history 2)
        heads-file (str (bfs/path dir "heads.edn"))
        after (slurp heads-file)
        s2 (store/open (fs/backend dir) h)
        _ (commit! dir (register-ops 0 "third"))
        third (:head (store/open (fs/backend dir) h))
        _ (spit heads-file after)
        r (inventory dir)]
    (is (= (:head s2) (:head (store/open (fs/backend dir) h))) "the store opens at the recorded heads")
    (is (= third (:revision (:uncommitted-tail r))))
    (is (= [third] (map :id (filter #(= :revision (:class %)) (:candidates r))))
        "what that line wrote is unreachable, and the inventory says which line it is")
    (is (true? (:stable? r)))))

;; ---------------------------------------------------------------- damage, stability, read-only

(deftest a-damaged-unreachable-object-is-reported-not-classified
  (let [dir (history 3)
        victim (:id (first (:candidates (inventory dir))))
        file (str (bfs/path dir "objects" victim))]
    (spit file (str (slurp file) " tampered"))
    (let [r (inventory dir)]
      (is (= [{:id victim :reason :hash-mismatch}] (:damaged r)))
      (is (not (contains? (set (map :id (:candidates r))) victim))))
    (spit file "{:not closed")
    (is (= [{:id victim :reason :hash-mismatch}] (:damaged (inventory dir)))
        "bytes that do not hash to the name are a mismatch whatever they read as")))

(deftest heads-that-moved-since-the-store-was-opened-are-said
  (let [dir (history 2)
        b (fs/backend dir)
        opened (store/open b h)]
    (commit! dir (register-ops 0 "later"))
    (is (false? (:stable? (store/unreachable (fs/backend dir) opened))))
    (is (true? (:stable? (inventory dir))))))

(deftest the-inventory-writes-nothing
  (let [dir (history 4)
        before (snapshot dir)]
    (inventory dir)
    (inventory dir)
    (is (= before (snapshot dir)) "every file of the store, cache and lock included, is byte-identical")))

;; ---------------------------------------------------------------- soundness

(deftest a-copy-without-its-candidates-still-opens-replays-and-verifies
  ;; never on the store itself: the copy is the experiment
  (doseq [[label dir] [["format 4, route schema 2" (history 5)]
                       ["format 4, route schema 1" (history 5 {:route-schema 1})]]]
    (testing label
      (let [copy (str (ktu/temp-dir "kimun-unreachable-copy-"))
            _ (bfs/copy-tree dir copy {:replace-existing true})
            r (inventory copy)
            before (store/open (fs/backend copy) h)
            routes (set (vals (:routes before)))]
        (is (pos? (count (:candidates r))))
        (doseq [c (:candidates r)] (bfs/delete (bfs/path copy "objects" (:id c))))
        (let [b (fs/backend copy)
              after (store/open b h)]
          (is (= (:head before) (:head after)))
          (is (= routes (set (vals (:routes after)))))
          (is (= (rev/tree-count before) (rev/tree-count after)))
          (is (true? (:ok? (store/verify b after))))
          (is (true? (:ok? (store/verify-full (fs/backend copy) h))) "the log still replays to the same heads")
          (is (= 0 (:unreachable (store/unreachable (fs/backend copy) after))) "and nothing unreachable is left")
          (is (some? (:store (store/commit! (fs/backend copy) (store/open (fs/backend copy) h)
                                            (plan (:head after) (register-ops 1 "after")))))
              "and it takes a new write"))
        (is (= (:stored (inventory dir)) (count (store/object-ids (fs/backend dir)))) "the original was not touched")))))

(deftest a-held-plan-is-needed-without-it-the-log-does-not-replay
  ;; why `:held` is not a candidate list
  (let [[dir _] (fresh {:format-version 3})
        _ (commit! dir [{:op :add-node :node (node/make h :sign :block {:format :markdown :type :document :attrs {}}) :as :r}
                        {:op :new-tree :tree {:kind :document :name "0"} :id (tid 0) :root :r}])
        copy (str (ktu/temp-dir "kimun-unreachable-copy-"))
        _ (bfs/copy-tree dir copy {:replace-existing true})
        held (:held (inventory copy))]
    (is (= 1 (count held)))
    (is (nil? (:replay-unavailable (store/verify-full (fs/backend copy) h))) "with the plan, the log replays")
    (bfs/delete (bfs/path copy "objects" (:id (first held))))
    (is (true? (:ok? (store/verify (fs/backend copy) (store/open (fs/backend copy) h))))
        "the hash walk does not notice")
    (is (= [(:id (first held))] (:missing (:replay-unavailable (store/verify-full (fs/backend copy) h))))
        "but the log can no longer be replayed, and verify-full says which plan is missing")))

(deftest object-class-names-each-shape
  (let [dir (history 2)
        b (fs/backend dir)
        s (store/open b h)
        of (fn [id] (store/object-class (store/deserialize id (store/read-object b id))))]
    (is (= :revision (of (:head s))))
    (is (= :route-node (of (:routes-object s))))
    (is (contains? #{:mmap-leaf :mmap-branch} (of (:heads-root s))))
    (is (= :unknown (store/object-class {:something "else"})))
    (is (= :unknown (store/object-class nil)))
    (is (= :plan (store/object-class (plan nil []))))
    (is (= (ports/algorithm (ports/hasher h)) (:hash-alg (edn/read-string (slurp (str (bfs/path dir "store.edn")))))))))
