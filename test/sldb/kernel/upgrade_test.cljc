(ns sldb.kernel.upgrade-test
  "Upgrade of a format-1 store to format 2 (sldb.kernel.upgrade): a history
   built with the legacy engine is re-committed into a fresh format-2 store
   that keeps every node, edge and tree, every transaction's actor, timestamp,
   engines and ops, and the parent chain; the result passes the full
   verification and the source is never written."
  (:require [kimun.test-util :as ktu] [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [sldb.kernel.test-util :refer [defspec]]
            [sldb.host.default :as host]
            [sldb.host.fs-store :as fs]
            [sldb.kernel.generators :as g]
            [sldb.kernel.revision :as rev]
            [sldb.kernel.store :as store]
            [sldb.kernel.tree :as tree]
            [sldb.kernel.upgrade :as upgrade]
            #?(:clj [babashka.fs :as bfs])))

(def h host/host)
(def caps {"jp" :all "kimun/derive" :all})
(def tid "01ARZ3NDEKTSV4RRFFQ69G5FAV")
(def tid2 "01ARZ3NDEKTSV4RRFFQ69G5FAW")

(defn- tmp [] #?(:clj (str (ktu/temp-dir "sldb-upgrade-"))))
(defn- ts [n] (str "2026-08-29T12:00:" (if (< n 10) "0" "") n ".000Z"))
(defn- plan [base n actor ops] {:plan/version 1 :base base :actor actor :engines {"e" "1"} :timestamp (ts n) :ops ops})
(defn- text [s as] {:op :add-node :node {:class :sign :kind :text :content {:text s}} :as as})
(defn- own [t as order] {:op :add-edge :edge {:type :ownership :tree t :parent [] :to as :order order}})
(defn- kids [s t] (mapv :node (:children (:root (get (:trees s) t)))))

(defn- files
  "{relative-path bytes-as-string} of every file under `dir`."
  [dir]
  #?(:clj (into {} (for [f (file-seq (java.io.File. dir)) :when (.isFile f)]
                     [(str (bfs/relativize dir (str f))) (slurp f)]))))

(def steps
  "A format-1 history touching every op kind: two trees, a replace (supersedes
   plus re-anchoring of a reference and a derived edge), an explicit
   supersedes edge, move, detach, remove-edge, two actors."
  [(fn [_] [(text "root" :r) {:op :new-tree :tree {:kind :document :name "a"} :id tid :root :r}
            (text "one" :x) (own tid :x 0) (text "two" :y) (own tid :y 1) (text "three" :z) (own tid :z 2)])
   (fn [s] (let [[a b] (kids s tid)]
             [{:op :add-edge :edge {:type :reference :from a :to b :evidence {:ref-hash b :actor "jp"}}}
              {:op :add-edge :edge {:type :derived :from b :to a :evidence {:ref-hash a :engine "e" :version "1"}}}]))
   (fn [_] [(text "two, edited" :n) {:op :replace :tree tid :at [1] :new :n}])
   (fn [_] [(text "root b" :r) {:op :new-tree :tree {:kind :taxonomy :name "b"} :id tid2 :root :r} (text "b1" :x) (own tid2 :x 0)])
   (fn [s] (let [[a] (kids s tid)] [(text "one, successor" :n) {:op :add-edge :edge {:type :supersedes :from :n :to a :evidence {:actor "jp"}}}]))
   (fn [_] [{:op :move :tree tid :from [2] :to [] :order 0}])
   (fn [_] [{:op :detach :tree tid :at [1]}])
   (fn [s] [{:op :remove-edge :edge (first (sort (:edges s)))}])
   (fn [_] [(text "b2" :x) (own tid2 :x 1)])])

(defn- build-format-1!
  "Commits `steps` into a fresh format-1 store at `dir` (legacy engine); the
   steps alternate between two actors. Returns the final store value."
  [dir steps]
  (let [b (fs/backend dir)]
    (reduce (fn [s [n f]]
              (:store (store/commit! b s (plan (:head s) n (if (even? n) "jp" "kimun/derive") (f s)))))
            (store/init! b h caps {:format-version 1})
            (map-indexed vector steps))))

(defn- tree-entries [s r]
  (into {} (map (fn [t] [t (rev/tree-entry s t r)])) (rev/tree-ids s r)))

(deftest a-format-1-history-upgrades-to-format-2-and-keeps-everything
  #?(:clj
     (let [src-dir (tmp) dst-dir (tmp)
           f1 (build-format-1! src-dir steps)
           src (fs/backend src-dir) dst (fs/backend dst-dir)
           before (files src-dir)
           r (upgrade/upgrade! src dst h)
           f1 (store/replay-open src h)
           f2 (store/open (fs/backend dst-dir) h)
           log1 (store/read-log src) log2 (store/read-log dst)
           revs (:revisions r)]
       (is (= before (files src-dir)) "the source is only read")
       (is (rev/legacy? f1))
       (is (= 2 (:format-version (store/read-descriptor dst)) (:format-version f2)))
       (is (= (dissoc (store/read-descriptor src) :format-version) (dissoc (store/read-descriptor dst) :format-version))
           "the descriptor keeps every other key")
       (is (= (count steps) (:transactions r) (count log2)))
       (is (= (:head f2) (:head r) (revs (:head f1))))
       (is (= (update-vals (:heads f1) revs) (:heads f2)) "every per-tree head maps to the new id of the same revision")
       (testing "every revision: same trees (descriptor + merkle root) and active edges; the parent chain maps"
         (doseq [{old :revision} log1 :let [new (revs old)]]
           (is (= (tree-entries f1 old) (tree-entries f2 new)))
           (is (= (vec (rev/active-edges f1 old)) (vec (rev/active-edges f2 new))))
           (is (= (mapv revs (:parents (rev/revision f1 old))) (:parents (rev/revision f2 new))))))
       (testing "every transaction keeps actor, timestamp, engines and ops; only :base is the new id"
         (doseq [[e1 e2] (map vector log1 log2)]
           (is (= (dissoc (:plan e1) :base) (dissoc (:plan e2) :base)))
           (is (= (some-> (:base (:plan e1)) revs) (:base (:plan e2))))
           (is (= (:id e2) ((:transaction-ids r) (:id e1))))
           (is (= (select-keys (rev/revision f1 (:revision e1)) [:actor :timestamp :engines])
                  (select-keys (rev/revision f2 (:revision e2)) [:actor :timestamp :engines])))))
       (testing "every node and edge object of the source is in the new store, under the same id"
         (let [o1 (filter #(or (:class %) (:type %)) (vals (:objects f1)))]
           (is (seq o1))
           (doseq [o o1] (is (= o (rev/get-object f2 (:id o)))))))
       (is (= (:trees f1) (into {} (:trees f2))) "the head trees, rebuilt from the CAS, are equal")
       (is (= (:edges f1) (:edges f2)))
       (is (:ok? (:verify r)))
       (is (:ok? (store/verify-full dst h)))
       (testing "the source still opens and commits through the legacy path"
         (let [s (store/open src h)]
           (is (rev/legacy? s))
           (is (some? (store/commit! src s (plan (:head s) 99 "jp" [(text "later" :x) (own tid2 :x 0)])))))))))

(deftest upgrade-refuses-what-it-cannot-do-safely
  #?(:clj
     (let [f2-dir (tmp) f1-dir (tmp) busy (tmp)]
       (store/init! (fs/backend f2-dir) h caps)
       (build-format-1! f1-dir (take 2 steps))
       (store/init! (fs/backend busy) h caps)
       (is (= :store/error (:type (ex-data (try (upgrade/upgrade! (fs/backend f2-dir) (fs/backend (tmp)) h) (catch Exception e e)))))
           "a format-2 store is not a source")
       (is (= :store/error (:type (ex-data (try (upgrade/upgrade! (fs/backend f1-dir) (fs/backend busy) h) (catch Exception e e)))))
           "the destination must be empty")
       (testing "a log that does not end at its heads is refused"
         (let [d (tmp)]
           (build-format-1! d (take 3 steps))
           (let [hp (str d "/heads.edn") lines (clojure.string/split-lines (slurp (str d "/log.edn")))]
             (spit hp (clojure.string/replace (slurp hp) (:revision (edn/read-string (peek lines))) (:revision (edn/read-string (first lines))))))
           (is (= :store/error (:type (ex-data (try (upgrade/upgrade! (fs/backend d) (fs/backend (tmp)) h) (catch Exception e e))))))))
       (testing "an uncommitted tail of the source is dropped and reported"
         (let [d (tmp)]
           (build-format-1! d (take 2 steps))
           (spit (str d "/log.edn") "{:id \"dead\" :plan" :append true)
           (let [r (upgrade/upgrade! (fs/backend d) (fs/backend (tmp)) h)]
             (is (= 2 (:transactions r)))
             (is (= {:torn-line 3} (:dropped-tail r)))))))))

;; ---------------------------------------------------------------- random histories

(def gen-step
  (gen/one-of [(gen/tuple (gen/return :add) gen/nat g/gen-text)
               (gen/tuple (gen/return :replace) gen/nat g/gen-text)
               (gen/tuple (gen/return :detach) gen/nat)
               (gen/tuple (gen/return :move) gen/nat gen/nat)
               (gen/tuple (gen/elements [:reference :derived]) gen/nat gen/nat)
               (gen/tuple (gen/return :unedge) gen/nat)]))

(defn- step-fn
  "A step of `steps` from an abstract random step; steps the legacy engine
   rejects are not committed (see `build-accepted!`)."
  [n [kind i j]]
  (fn [s]
    (let [ks (kids s tid) k (count ks) edges (vec (sort (:edges s)))]
      (case kind
        :add [(text (str "s" n "-" j) :x) (own tid :x 0)]
        :replace (if (pos? k) [(text (str "s" n "-" j) :x) {:op :replace :tree tid :at [(mod i k)] :new :x}] [(text (str "s" n) :x)])
        :detach (if (pos? k) [{:op :detach :tree tid :at [(mod i k)]}] [(text (str "d" n) :x)])
        :move (if (pos? k) [{:op :move :tree tid :from [(mod i k)] :to [] :order (mod j k)}] [(text (str "m" n) :x)])
        (:reference :derived)
        (if (pos? k)
          (let [a (ks (mod i k)) b (ks (mod j k))]
            [{:op :add-edge :edge {:type kind :from a :to b
                                   :evidence (if (= kind :reference) {:ref-hash b :actor "jp"} {:ref-hash b :engine "e" :version "1"})}}])
          [(text (str "e" n) :x)])
        :unedge (if (seq edges) [{:op :remove-edge :edge (edges (mod i (count edges)))}] [(text (str "u" n) :x)])))))

(defn- build-accepted!
  "Like `build-format-1!`, skipping a step the engine rejects."
  [dir fs]
  (let [b (fs/backend dir)]
    (reduce (fn [s [n f]]
              (try (:store (store/commit! b s (plan (:head s) n "jp" (f s))))
                   (catch clojure.lang.ExceptionInfo _ s)))
            (store/init! b h caps {:format-version 1})
            (map-indexed vector fs))))

(defspec any-format-1-history-upgrades-to-the-same-trees-and-edges 12
  (prop/for-all [raw (gen/vector gen-step 0 10)]
    #?(:clj
       (let [src-dir (tmp) dst-dir (tmp)
             f1 (build-accepted! src-dir (cons (first steps) (map-indexed step-fn raw)))
             r (upgrade/upgrade! (fs/backend src-dir) (fs/backend dst-dir) h)
             f2 (store/open (fs/backend dst-dir) h)]
         (and (:ok? (:verify r))
              (= (:trees f1) (into {} (:trees f2)))
              (= (:edges f1) (:edges f2))
              (= (count (store/read-log (fs/backend src-dir))) (:transactions r))
              (every? (fn [{old :revision}] (= (tree-entries f1 old) (tree-entries f2 ((:revisions r) old))))
                      (store/read-log (fs/backend src-dir)))))
       :cljs true)))
