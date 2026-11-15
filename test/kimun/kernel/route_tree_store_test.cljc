(ns kimun.kernel.route-tree-store-test
  "K2a C5: the route map of a store as a Merkle tree by path (route schema 2,
   docs/v2/10 §11.2). A new store is of schema 2: a write rewrites the nodes on
   the paths it touched, open reads the root node only, and a lookup goes down
   one node per segment. A store of schema 1 (the flat map object, every store
   written before the tree) keeps opening and writing as it did until
   `upgrade-routes!` is asked for, which keeps entries, documents and history
   and changes the route root. A path stays a locator; no identity is decided."
  (:require [babashka.fs :as bfs]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.host.fs-store :as fs]
            [kimun.host.store-cache :as cache]
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
  "[dir backend empty-store] of a new store; `opts` as `store/init!`."
  ([] (fresh {}))
  ([opts]
   (let [dir (str (ktu/temp-dir "kimun-route-tree-store-"))
         b (fs/backend dir)]
     [dir b (store/init! b h {"jp" :all} opts)])))

(defn- populated
  "[dir backend store result] after ONE commit registering documents 0..n-1."
  ([n] (populated n {}))
  ([n opts]
   (let [[dir b s0] (fresh opts)
         r (store/commit! b s0 (plan nil (mapcat #(register-ops % (tid %) (path-of %) %) (range n))))]
     [dir b (:store r) r])))

(defn- stored-entry [i path v] (route/entry h (assoc (entry path v) :tree-id (tid i))))

(defn- route-nodes "The route tree nodes among the objects a commit wrote." [result]
  (filter rt/node? (vals (:new-objects result))))

(defn- thrown-type [f] (:type (ex-data (try (f) (catch Exception e e)))))

;; ---------------------------------------------------------------- a new store

(deftest a-new-store-declares-route-schema-2-and-keeps-format-4
  (let [[_ b s0] (fresh)]
    (is (= 4 (:format-version (store/read-descriptor b))))
    (is (= 2 (:route-schema (store/read-descriptor b))))
    (is (= 2 (rev/route-schema s0)))
    (is (= 2 (rev/route-schema (store/open b h))) "before any route, store.edn decides")))

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
      (is (= 2 (rev/route-schema opened)))
      (is (true? (:routes-available? opened)))
      (is (= (stored-entry 1 (path-of 1) 1) (get-in opened [:routes (path-of 1)])))
      (is (= (set es) (set (vals (:routes opened)))))
      (is (= 3 (count (:routes opened)))))
    (is (not (bfs/exists? (cache/routes-dir (:dir b)))) "the tree needs no derived route cache")))

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
    (is (= 400 (count (:routes (:store r)))))
    (testing "against schema 1, which rewrites the whole map"
      (let [[_ b1 t1] (populated 400 {:route-schema 1})
            r1 (store/commit! b1 t1 (plan (:head t1) (register-ops 43 (tid 43) (path-of 43) "edited")))
            flat (get (:new-objects r1) (:routes-object (:store r1)))
            bytes #(count (store/serialize h %))]
        (is (= 400 (count (:entries flat))) "the one object schema 1 writes holds every route")
        (is (< (* 10 (reduce + (map bytes written))) (bytes flat))
            "the three nodes are under a tenth of the bytes of the flat map")))))

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

;; ---------------------------------------------------------------- schema 1 stays

(defn- as-written-before-the-tree!
  "Makes the store at `dir` what a store created before C5 is: its store.edn
   has no `:route-schema`."
  [dir]
  (let [f (str (bfs/path dir "store.edn"))]
    (spit f (pr-str (dissoc (edn/read-string (slurp f)) :route-schema)))))

(deftest a-store-without-the-key-is-schema-1-and-stays-so
  (let [[dir b _] (fresh)
        _ (as-written-before-the-tree! dir)
        s0 (store/open (fs/backend dir) h)]
    (is (= 1 (rev/route-schema s0)))
    (let [b (fs/backend dir)
          s1 (:store (store/commit! b s0 (plan nil (mapcat #(register-ops % (tid %) (path-of %) %) (range 4)))))
          es (map #(stored-entry % (path-of %) %) (range 4))]
      (is (= (route/root h es) (:routes-root s1)) "the root is the flat root of schema 1, as before")
      (is (= (route/entries-digest h es) (:routes-object s1)))
      (is (bfs/exists? (cache/route-path dir (:routes-root s1) (:head s1))) "with its derived cache, as before")
      (let [opened (store/open (fs/backend dir) h)
            s2 (:store (store/commit! (fs/backend dir) opened
                                      (plan (:head opened) (register-ops 1 (tid 1) (path-of 1) "edited"))))]
        (is (= 1 (rev/route-schema opened)))
        (is (= 1 (rev/route-schema s2)) "a write does not change the schema")
        (is (= (route/root h (vals (:routes s2))) (:routes-root s2)))
        (is (nil? (:route-schema (store/read-descriptor (fs/backend dir)))) "store.edn is not touched")
        (is (= (:routes-root s2) (:routes-root (store/replay-open (fs/backend dir) h))))))))

;; ---------------------------------------------------------------- the upgrade

(defn- schema-1-store
  "[dir head-store] of a schema-1 store with `n` routes written over two commits."
  [n]
  (let [[dir b s1] (populated n {:route-schema 1})
        s2 (:store (store/commit! b s1 (plan (:head s1) (register-ops 0 (tid 0) (path-of 0) "edited"))))]
    [dir s2]))

(deftest the-upgrade-keeps-everything-but-the-root
  (let [[dir before] (schema-1-store 30)
        entries (set (vals (:routes before)))
        log (slurp (str (bfs/path dir "log.edn")))
        r (store/upgrade-routes! (fs/backend dir) h)
        after (store/open (fs/backend dir) h)]
    (is (true? (:upgraded r)))
    (is (= 30 (:routes r)))
    (is (= (:routes-root before) (:routes-root-before r)))
    (is (not= (:routes-root-before r) (:routes-root r)) "the root is computed over a tree now: it changes, and is said")
    (is (= 2 (:route-schema (store/read-descriptor (fs/backend dir)))))
    (is (= 2 (rev/route-schema after)))
    (is (= (:head before) (:head after)) "no revision is added")
    (is (= (:heads-root before) (:heads-root after)))
    (is (= log (slurp (str (bfs/path dir "log.edn")))) "the log does not move")
    (is (= entries (set (vals (:routes after)))) "every entry, tree ids included")
    (is (= (:routes-root r) (:routes-root after) (:hash (rt/from-entries h entries))))
    (is (= (rev/tree-count before) (rev/tree-count after)))
    (is (true? (:ok? (store/verify (fs/backend dir) after))))
    (is (= (:routes-object after) (:routes-object (store/replay-open (fs/backend dir) h)))
        "replaying the whole log under schema 2 leads to the same heads")
    (testing "a second upgrade has nothing to do"
      (is (= {:upgraded false :route-schema 2 :routes 30} (store/upgrade-routes! (fs/backend dir) h))))
    (testing "the next write goes through the tree"
      (let [b (fs/backend dir)
            r2 (store/commit! b (store/open b h) (plan (:head after) (register-ops 7 (tid 7) (path-of 7) "edited")))]
        (is (seq (route-nodes r2)))
        (is (= "sha-256:edited" (get-in (store/open (fs/backend dir) h) [:routes (path-of 7) :fingerprint])))))))

(deftest an-upgraded-store-has-the-root-of-a-store-born-with-the-tree
  (let [[dir1 _] (schema-1-store 30)
        _ (store/upgrade-routes! (fs/backend dir1) h)
        [_ b2 s] (populated 30)
        born (:store (store/commit! b2 s (plan (:head s) (register-ops 0 (tid 0) (path-of 0) "edited"))))]
    (is (= (:routes-root born) (:routes-root (store/open (fs/backend dir1) h))))
    (is (= (:routes-object born) (:routes-object (store/open (fs/backend dir1) h))))))

(deftest a-store-with-no-route-yet-only-records-the-schema
  (let [[dir _ _] (fresh {:route-schema 1})]
    (is (= {:upgraded true :route-schema 2 :routes 0} (store/upgrade-routes! (fs/backend dir) h)))
    (is (= 2 (:route-schema (store/read-descriptor (fs/backend dir)))))
    (is (nil? (store/read-heads (fs/backend dir))) "nothing else was written")
    (is (= {:upgraded false :route-schema 2 :routes 0} (store/upgrade-routes! (fs/backend dir) h)))))

(deftest an-upgrade-stopped-before-store-edn-is-completed-by-the-next-one
  (let [[dir before] (schema-1-store 6)
        _ (store/upgrade-routes! (fs/backend dir) h)
        _ (as-written-before-the-tree! dir)   ; the heads were replaced, store.edn was not
        opened (store/open (fs/backend dir) h)]
    (is (= 2 (rev/route-schema opened)) "the object the heads name says which schema it is")
    (is (= (set (vals (:routes before))) (set (vals (:routes opened)))))
    (is (= {:upgraded true :route-schema 2 :routes 6} (store/upgrade-routes! (fs/backend dir) h)))
    (is (= 2 (:route-schema (store/read-descriptor (fs/backend dir)))))
    (is (= (:routes-object opened) (:routes-object (store/replay-open (fs/backend dir) h))))))

(deftest an-upgrade-does-not-overwrite-a-concurrent-write
  (let [[dir before] (schema-1-store 6)
        b (fs/backend dir)
        expected (store/read-heads b)]
    (is (false? (store/swap-heads! b (assoc expected :head "other") (assoc expected :routes-root "x")))
        "heads that are not the expected ones are left alone")
    (is (= expected (store/read-heads (fs/backend dir))))
    (is (= (:routes-root before) (:routes-root (store/open (fs/backend dir) h))))))

(deftest an-unreadable-flat-map-is-not-upgraded
  (let [[dir before] (schema-1-store 6)
        hd (store/read-heads (fs/backend dir))]
    ;; a root the flat map does not hash to: schema 1 calls that unavailable, and nothing is invented
    (spit (str (bfs/path dir "heads.edn")) (pr-str (assoc hd :routes-root (route/root h []))))
    (bfs/delete-tree (cache/routes-dir dir))
    (is (= :store/error (thrown-type #(store/upgrade-routes! (fs/backend dir) h))))
    (is (nil? (:route-schema (store/read-descriptor (fs/backend dir)))))
    (is (= (:routes-object before) (:routes-object (store/read-heads (fs/backend dir)))))))

(deftest only-a-format-4-store-has-routes-to-upgrade
  (let [dir (str (ktu/temp-dir "kimun-route-tree-store-"))
        b (fs/backend dir)]
    (store/init! b h {"jp" :all} {:format-version 3})
    (is (nil? (:route-schema (store/read-descriptor b))))
    (is (= :store/error (thrown-type #(store/upgrade-routes! (fs/backend dir) h))))))

(deftest the-digest-of-a-node-is-its-object-id
  (let [[_ b s1] (populated 3)
        text (store/read-object b (:routes-object s1))]
    (is (= (:routes-object s1) (ports/digest-str (ports/hasher h) text)))))
