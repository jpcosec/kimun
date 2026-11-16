(ns kimun.kernel.route-tree-store-test
  "K2a C5: the route map of a store as a Merkle tree by path (route schema 2,
   docs/v2/10 §11.2). A new store is of schema 2: a write rewrites the nodes on
   the paths it touched, open reads the root node only, and a lookup goes down
   one node per segment. A path stays a locator; no identity is decided."
  (:require [babashka.fs :as bfs]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.host.fs-store :as fs]
            [kimun.kernel.node :as node]
            [kimun.kernel.ports :as ports]
            [kimun.kernel.revision :as rev]
            [kimun.kernel.route :as route]
            [kimun.kernel.route-tree :as rt]
            [kimun.kernel.store :as store]
            [kimun.test-util :as ktu]))

(def h host/host)
(def TS "2026-08-29T12:00:00.000Z")

(defn- tid "The tree id of document `i`." [i] (format "01ARZ3NDEKTSV4RRFFQ69G%04d" i))

(defn- path-of "The tracked path of document `i`: 20 directories of 7." [i]
  (str "d" (mod i 20) "/s" (mod (quot i 20) 7) "/f" i ".md"))

(defn- entry
  "The route value of document `i` at `path` with sensed value `v`."
  [path v]
  {:path path :fingerprint (str "sha-256:" v) :class "C"
   :projection-hash (str "projection-" v) :content-hash (str "content-" v)})

(defn- register-ops
  "The ops registering document `i` (tree `tree-id`) at `path` with value `v`."
  [i tree-id path v]
  (let [root (node/make h :sign :block {:format :markdown :type :document :attrs {:n i}})
        ext (node/make h :sign :external
                       {:locator {:kind :file :path path} :sample ""
                        :fingerprint "sha-256:1111111111111111111111111111111111111111111111111111111111111111"})]
    [{:op :add-node :node root}
     {:op :register-tree
      :tree {:kind :document :name (str "d" i) :class "C" :content-hash (str "content-" v) :cached? true
             :root (:id root)}
      :id tree-id :root (:id root) :merkle-root (str "projection-" v)
      :route (entry path v)}
     {:op :add-node :node ext}
     {:op :add-edge :edge {:type :reference :from (:id root) :to (:id ext)
                           :evidence {:ref-hash (:id ext) :actor "jp"}}}]))

(defn- plan [base ops]
  {:plan/version 1 :base base :actor "jp" :engines {} :timestamp TS :ops (vec ops)})

(defn- fresh
  "[dir backend empty-store] of a new store."
  []
  (let [dir (str (ktu/temp-dir "kimun-route-tree-store-"))
        b (fs/backend dir)]
    [dir b (store/init! b h {"jp" :all})]))

(defn- populated
  "[dir backend store result] after ONE commit registering documents 0..n-1."
  [n]
  (let [[dir b s0] (fresh)
        r (store/commit! b s0 (plan nil (mapcat #(register-ops % (tid %) (path-of %) %) (range n))))]
    [dir b (:store r) r]))

(defn- stored-entry [i path v] (route/entry h (assoc (entry path v) :tree-id (tid i))))

(defn- route-nodes "The route tree nodes among the objects a commit wrote." [result]
  (filter rt/node? (vals (:new-objects result))))

(defn- thrown-type [f] (:type (ex-data (try (f) (catch Exception e e)))))

;; ---------------------------------------------------------------- a new store

(deftest a-new-store-declares-route-schema-2-and-keeps-format-4
  (let [[_ b s0] (fresh)]
    (is (= 4 (:format-version (store/read-descriptor b))))
    (is (= 2 (:route-schema (store/read-descriptor b))))
    (is (nil? (:routes-root s0)))
    (is (nil? (:routes-root (store/open b h))) "before any route there is no root")))

(deftest the-heads-name-the-root-of-the-tree
  (let [[_ b s1] (populated 3)
        es (map #(stored-entry % (path-of %) %) (range 3))
        tree (rt/from-entries h es)
        hd (store/read-heads b)]
    (is (= #{:head :heads-root :routes-root :routes-object} (set (keys hd))) "the heads keep their format-4 shape")
    (is (= (:hash tree) (:routes-root hd) (:routes-root s1)))
    (is (= (:object tree) (:routes-object hd) (:routes-object s1)))
    (is (rt/node? (edn/read-string (store/read-object b (:routes-object hd)))))
    (let [opened (store/open b h)]
      (is (true? (:routes-available? opened)))
      (is (= (stored-entry 1 (path-of 1) 1) (get-in opened [:routes (path-of 1)])))
      (is (= (set es) (set (vals (:routes opened)))))
      (is (= 3 (count (:routes opened)))))
    (is (not (bfs/exists? (str (bfs/path (:dir b) "cache" "routes")))) "the tree needs no derived route cache")))

(deftest the-root-survives-a-new-tree-id
  (let [[_ b s1] (populated 2)
        root (:routes-root s1)
        s2 (:store (store/commit! b s1 (plan (:head s1) [{:op :remove-route :tree (tid 0) :path (path-of 0)}])))
        s3 (:store (store/commit! b s2 (plan (:head s2) (register-ops 0 (tid 77) (path-of 0) 0))))]
    (is (not= root (:routes-root s2)))
    (is (= root (:routes-root s3)) "same sensed fields under a new tree id: the same route root")
    (is (not= (:routes-object s1) (:routes-object s3)) "the locator moved, and the object id covers it")
    (is (= (tid 77) (get-in (store/open b h) [:routes (path-of 0) :tree-id])))))

(deftest a-moved-path-leaves-and-enters-in-one-commit
  (let [[_ b s1] (populated 2)
        s2 (:store (store/commit! b s1 (plan (:head s1)
                                             (concat [{:op :remove-route :tree (tid 0) :path (path-of 0)}]
                                                     (register-ops 0 (tid 0) "elsewhere/f0.md" 0)))))
        opened (store/open b h)]
    (is (nil? (get-in opened [:routes (path-of 0)])))
    (is (= (tid 0) (get-in opened [:routes "elsewhere/f0.md" :tree-id])))
    (is (= 2 (count (:routes opened))))
    (is (= (:routes-root s2)
           (:hash (rt/from-entries h [(stored-entry 0 "elsewhere/f0.md" 0) (stored-entry 1 (path-of 1) 1)]))))))

(deftest the-last-route-leaving-still-gives-a-root
  (let [[_ b s1] (populated 1)
        s2 (:store (store/commit! b s1 (plan (:head s1) [{:op :remove-route :tree (tid 0) :path (path-of 0)}])))]
    (is (= (:hash (rt/empty h)) (:routes-root s2) (:routes-root (store/open b h))))
    (is (= {} (into {} (:routes (store/open b h)))))
    (is (= 1 (rev/tree-count (store/open b h))) "untracking does not detach the tree")))

(deftest a-path-another-tree-holds-is-refused
  (let [[_ b s1] (populated 1)]
    (is (some? (try (store/commit! b s1 (plan (:head s1) (register-ops 5 (tid 5) (path-of 0) 9))) nil
                    (catch Exception e e)))
        "one path, one tree: the kernel rejects the plan")
    (is (= (:routes-root s1) (:routes-root (store/open b h))))))

(deftest replay-rebuilds-the-same-tree-from-the-log
  (let [[_ b s1] (populated 5)
        s2 (:store (store/commit! b s1 (plan (:head s1) (register-ops 2 (tid 2) (path-of 2) "edited"))))
        s3 (:store (store/commit! b s2 (plan (:head s2) [{:op :remove-route :tree (tid 4) :path (path-of 4)}])))
        replayed (store/replay-open b h)]
    (is (= (:routes-root s3) (:routes-root replayed)))
    (is (= (:routes-object s3) (:routes-object replayed)) "replay-open compares the heads: they match")
    (is (= (set (vals (:routes (store/open b h)))) (set (vals (:routes replayed)))))
    (is (true? (:ok? (store/verify b (store/open b h)))))))

;; ---------------------------------------------------------------- local cost

(deftest a-write-costs-the-path-it-touches
  ;; 400 routes in 20 directories of 7: a change in one file writes three nodes
  (let [[_ b s1] (populated 400)
        r (store/commit! b s1 (plan (:head s1) (register-ops 43 (tid 43) (path-of 43) "edited")))
        written (route-nodes r)]
    (is (= 3 (count written)) "root, directory and subdirectory of the changed path")
    (is (= 400 (count (:routes (:store r)))))))

(deftest open-reads-the-root-node-and-a-lookup-its-path
  (let [[dir _ s1] (populated 400)
        b (fs/backend dir)   ; a backend with an empty object cache: what it holds is what was read
        nodes #(count (filter rt/node? (vals @(:cache b))))
        opened (store/open b h)]
    (is (= 1 (nodes)) "open reads and checks the root node, whatever the number of routes")
    (is (= (stored-entry 43 (path-of 43) 43) (get-in opened [:routes (path-of 43)])))
    (is (= 3 (nodes)) "one node per segment of the path")
    (is (nil? (get-in opened [:routes "nowhere/x.md"])))
    (is (= 3 (nodes)) "a path that is not there stops at the root")
    (is (= 400 (count (:routes opened))))
    (is (= 161 (nodes)) "only an enumeration reads the whole tree: 1 + 20 + 140 nodes")
    (is (= (:routes-root s1) (:routes-root opened)))))

;; ---------------------------------------------------------------- damage

(deftest damage-below-the-root-is-local-and-verify-reports-it
  (let [[dir b s1] (populated 40)
        root (edn/read-string (store/read-object b (:routes-object s1)))
        d3 (get-in root [:children "d3" :dir :object])
        file (str (bfs/path dir "objects" d3))]
    ;; still a node, but what it says of the directory below is no longer what the root recorded
    (spit file (pr-str (assoc-in (edn/read-string (slurp file)) [:children "s0" :dir :hash] "tampered")))
    (let [opened (store/open (fs/backend dir) h)]
      (is (true? (:routes-available? opened)) "the root node is intact: the store opens")
      (is (= (tid 4) (get-in opened [:routes (path-of 4) :tree-id])) "another branch answers")
      (is (= :route/corrupt (thrown-type #(get-in opened [:routes (path-of 3)])))
          "the damaged branch is refused where it is read")
      (let [v (store/verify (fs/backend dir) opened)]
        (is (false? (:ok? v)))
        (is (= [{:id d3 :reason :hash-mismatch}] (:bad v)))))))

(deftest a-tree-id-forged-below-the-root-is-refused-where-it-is-read
  ;; the forged node is canonical EDN with the same route hash and count: only its
  ;; object id gives it away, and the store reads objects without rehashing them
  (let [[dir b s1] (populated 40)
        root (edn/read-string (store/read-object b (:routes-object s1)))
        d3 (get-in root [:children "d3" :dir :object])
        d3-node (edn/read-string (store/read-object b d3))
        s0 (get-in d3-node [:children "s0" :dir :object])
        s0-node (edn/read-string (store/read-object b s0))
        forged (assoc-in s0-node [:children "f3.md" :entry :tree-id] (tid 999))]
    (is (= (rt/node-hash h s0-node) (rt/node-hash h forged)) "the route hash does not see a tree id")
    (spit (str (bfs/path dir "objects" s0)) (store/serialize h forged))
    (let [opened (store/open (fs/backend dir) h)]
      (is (true? (:routes-available? opened)))
      (is (= :route/corrupt (thrown-type #(get-in opened [:routes (path-of 3)])))
          "the forged locator is never handed out")
      (is (= :route/corrupt (thrown-type #(count (:routes opened)))) "nor by an enumeration")
      (is (= (tid 4) (get-in opened [:routes (path-of 4) :tree-id])) "another branch answers")
      (is (some? (try (store/commit! (fs/backend dir) opened
                                     (plan (:head opened) (register-ops 3 (tid 3) (path-of 3) "edited")))
                      nil
                      (catch Exception e e)))
          "and no write goes through the forged node")
      (is (= [{:id s0 :reason :hash-mismatch}] (:bad (store/verify (fs/backend dir) opened)))))))

(deftest a-route-root-the-root-node-does-not-hash-to-is-not-available
  (let [[dir b s1] (populated 3)
        hd (store/read-heads b)]
    (spit (str (bfs/path dir "heads.edn")) (pr-str (assoc hd :routes-root (:hash (rt/empty h)))))
    (let [opened (store/open (fs/backend dir) h)]
      (is (false? (:routes-available? opened)))
      (is (true? (:routes-invalid? opened)))
      (is (some? (try (store/commit! (fs/backend dir) opened
                                     (plan (:head opened) (register-ops 9 (tid 9) (path-of 9) 9)))
                      nil
                      (catch Exception e e)))
          "no route is written over a root that cannot be checked"))))

(deftest the-digest-of-a-node-is-its-object-id
  (let [[_ b s1] (populated 3)
        text (store/read-object b (:routes-object s1))]
    (is (= (:routes-object s1) (ports/digest-str (ports/hasher h) text)))))
