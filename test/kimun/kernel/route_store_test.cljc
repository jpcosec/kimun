(ns kimun.kernel.route-store-test
  "K2a C2a: a cached tree can carry a full route entry in :register-tree;
   :remove-route removes the tracked route without deleting the tree; the root
   lives in heads and is rebuilt by replay from :route values. No surface/docs."
  (:require [babashka.fs :as bfs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.host.fs-store :as fs]
            [kimun.host.store-cache :as cache]
            [kimun.kernel.node :as node]
            [kimun.kernel.ports :as ports]
            [kimun.kernel.revision :as rev]
            [kimun.kernel.route :as route]
            [kimun.kernel.store :as store]
            [kimun.test-util :as ktu]))

(def h host/host)
(def T "01ARZ3NDEKTSV4RRFFQ69G5FAV")
(def TS "2026-08-29T12:00:00.000Z")

(defn- root-node []
  (node/make h :sign :block {:format :markdown :type :document :attrs {}}))

(defn- route-entry [fp]
  {:path "docs/a.md" :fingerprint fp :class "C"
   :projection-hash "projection" :content-hash "content"})

(defn- register-plan [base root tree-id route]
  (let [root-id (:id root)
        ext (node/make h :sign :external
                       {:locator {:kind :file :path "docs/a.md"}
                        :sample "" :fingerprint "sha-256:1111111111111111111111111111111111111111111111111111111111111111"})
        ext-id (:id ext)]
    {:plan/version 1 :base base :actor "jp" :engines {} :timestamp TS
     :ops [{:op :add-node :node root :as :r}
           {:op :register-tree
            :tree {:kind :document :name "d" :class "C" :content-hash "content" :cached? true
                   :root root-id}
            :id tree-id :root root-id :merkle-root "projection"
            :route route :as :t}
           {:op :add-node :node ext :as :ext}
           {:op :add-edge :edge {:type :reference :from root-id :to ext-id
                                 :evidence {:ref-hash ext-id :actor "jp"}}}]}))

(defn- fresh []
  (let [dir (str (ktu/temp-dir "kimun-route-store-"))
        b (fs/backend dir)
        s0 (store/init! b h {"jp" :all})]
    [dir b s0]))

(deftest register-route-writes-root-and-cache
  (let [[dir b s0] (fresh)
        r (root-node)
        s1 (:store (store/commit! b s0 (register-plan nil r T (route-entry "fp1"))))
        entries {(route/path-key h "docs/a.md") (route/entry h (assoc (route-entry "fp1") :tree-id T))}
        expected (route/root h (vals entries))]
    (is (= expected (:routes-root s1)))
    (is (= expected (:routes-root (store/read-heads b))) "the root is durable in heads.edn")
    (is (= expected (:routes-root (store/open b h))) "open reads the durable root")
    (is (= entries (:routes (store/open b h))) "open reads the route entries from cache")
    (is (bfs/exists? (cache/route-path dir expected (:head s1))))
    (let [id (:routes-object s1)
          text (store/read-object b id)]
      (is (= id (ports/digest-str (ports/hasher h) text))
          "the CAS route-map object hashes to :routes-object"))
    (is (= 1 (rev/tree-count s1)) "the tree remains")))

(deftest update-route-replaces-entry-and-root
  (let [[dir b s0] (fresh)
        r (root-node)
        s1 (:store (store/commit! b s0 (register-plan nil r T (route-entry "fp1"))))
        s2 (:store (store/commit! b s1 (register-plan (:head s1) r T (route-entry "fp2"))))
        e2 (route/entry h (assoc (route-entry "fp2") :tree-id T))]
    (is (not= (:routes-root s1) (:routes-root s2)))
    (is (= e2 (get-in s2 [:routes (route/path-key h "docs/a.md")])))
    (is (= (route/root h [e2]) (:routes-root s2)))
    (is (= 1 (rev/tree-count s2)))))

(deftest remove-route-does-not-delete-the-tree
  (let [[dir b s0] (fresh)
        r (root-node)
        s1 (:store (store/commit! b s0 (register-plan nil r T (route-entry "fp1"))))
        p {:plan/version 1 :base (:head s1) :actor "jp" :engines {} :timestamp TS
           :ops [{:op :remove-route :tree T :path "docs/a.md"}]}
        s2 (:store (store/commit! b s1 p))]
    (is (= {} (:routes s2)))
    (is (= (route/root h []) (:routes-root s2)) "the root reflects the exit")
    (is (= 1 (rev/tree-count s2)) "untracking does not detach the tree")
    (is (= (route/root h []) (:routes-root (store/open b h))))
    (is (= 1 (rev/tree-count (store/open b h))))))

(deftest replay-rebuilds-routes-from-plan-route-values
  (let [[dir b s0] (fresh)
        r (root-node)
        s1 (:store (store/commit! b s0 (register-plan nil r T (route-entry "fp1"))))
        root (:routes-root s1)
        _ (bfs/delete (cache/route-path dir root (:head s1)))
        opened (store/open b h)
        replayed (store/replay-open b h)]
    (is (true? (:routes-available? opened)) "the CAS map is the durable source; cache is optional")
    (is (= root (:routes-root opened)))
    (is (= (:routes s1) (:routes opened)) "open reads the CAS route map without cache")
    (is (= (:routes s1) (:routes replayed)) "replay reconstructs from :route in the plan")
    (is (= root (:routes-root replayed)))))

(deftest a-route-update-refuses-without-the-entry-source
  (let [[dir b s0] (fresh)
        r (root-node)
        s1 (:store (store/commit! b s0 (register-plan nil r T (route-entry "fp1"))))
        _ (bfs/delete (cache/route-path dir (:routes-root s1) (:head s1)))
        opened (store/open b h)
        s2 (:store (store/commit! b opened (register-plan (:head opened) r T (route-entry "fp2"))))]
    (is (= "fp2" (get-in (store/open b h) [:routes "docs/a.md" :fingerprint]))
        "cache deletion is recovered from the CAS route map")
    (is (bfs/exists? (cache/route-path dir (:routes-root s2) (:head s2)))
        "the write repopulates cache under the new head")))

(deftest same-route-root-after-a-new-tree-id-in-one-store
  (let [[dir b s0] (fresh)
        r (root-node)
        t2 "01ARZ3NDEKTSV4RRFFQ69G5FAW"
        s1 (:store (store/commit! b s0 (register-plan nil r T (route-entry "fp1"))))
        root (:routes-root s1)
        p {:plan/version 1 :base (:head s1) :actor "jp" :engines {} :timestamp TS
           :ops [{:op :remove-route :tree T :path "docs/a.md"}]}
        s2 (:store (store/commit! b s1 p))
        s3 (:store (store/commit! b s2 (register-plan (:head s2) r t2 (route-entry "fp1"))))]
    (is (= root (:routes-root s3)) "same five fields: the root survives the new tree id")
    (is (not= (:head s1) (:head s3)))
    (is (= t2 (get-in (store/open b h) [:routes "docs/a.md" :tree-id])))
    (is (bfs/exists? (cache/route-path dir root (:head s3)))
        "the cache is keyed by root+head, not by the old locator")))

(deftest a-route-path-cannot-move-to-another-tree-implicitly
  (let [[_ b s0] (fresh)
        r (root-node)
        s1 (:store (store/commit! b s0 (register-plan nil r T (route-entry "fp1"))))
        other-tree "01ARZ3NDEKTSV4RRFFQ69G5FAW"
        e (try (store/commit! b s1 (register-plan (:head s1) r other-tree (route-entry "fp2")))
               nil
               (catch clojure.lang.ExceptionInfo e (ex-data e)))]
    (is (= :plan/rejected (:type e)))
    (is (= :route/ambiguous (:check e)))))

(deftest a-tampered-route-cache-is-not-corruption-and-cannot-feed-a-plan
  (let [[dir b s0] (fresh)
        r (root-node)
        s1 (:store (store/commit! b s0 (register-plan nil r T (route-entry "fp1"))))
        root (:routes-root s1)
        f (cache/route-path dir root (:head s1))
        entries (cache/read-routes dir root (:head s1))
        _ (spit f (pr-str (assoc-in entries ["docs/a.md" :fingerprint] "tampered")))
        opened (store/open b h)
        e (try (store/commit! b opened (register-plan (:head opened) r T (route-entry "fp2")))
               nil
               (catch clojure.lang.ExceptionInfo e (ex-data e)))]
    (is (true? (:routes-available? opened)) "the CAS map is used when cache is bad")
    (is (true? (:routes-invalid? opened)))
    (is (:ok? (store/verify b opened)) "a bad route cache is not object corruption")
    (is (nil? e) "the update uses the durable CAS map")
    (is (= "fp2" (get-in (store/open b h) [:routes "docs/a.md" :fingerprint])))
    (is (= (:routes (store/open b h)) (:routes (store/replay-open b h)))
        "replay recovers the entries from :route in the log")))

(deftest a-missing-or-tampered-cas-route-map-is-corruption
  (let [[dir b s0] (fresh)
        r (root-node)
        s1 (:store (store/commit! b s0 (register-plan nil r T (route-entry "fp1"))))
        id (:routes-object s1)
        f (str dir "/objects/" id)]
    (testing "missing"
      (let [saved (slurp f)]
        (bfs/delete f)
        (let [e (try (store/open b h) nil
                     (catch clojure.lang.ExceptionInfo e (ex-data e)))]
          (is (= :store/corrupt-head (:type e)))
          (is (= :missing (:reason e)))
          (spit f saved))))
    (testing "tampered"
      (let [saved (slurp f)]
        (spit f (str saved " "))
        (let [e (try (store/open b h) nil
                     (catch clojure.lang.ExceptionInfo e (ex-data e)))]
          (is (= :store/corrupt-head (:type e)))
          (is (= :hash-mismatch (:reason e)))
          (spit f saved))))))

(deftest a-route-cache-with-adulterated-keys-is-not-available
  (let [[dir b s0] (fresh)
        r (root-node)
        s1 (:store (store/commit! b s0 (register-plan nil r T (route-entry "fp1"))))
        root (:routes-root s1)
        f (cache/route-path dir root (:head s1))
        entries (cache/read-routes dir root (:head s1))
        _ (spit f (pr-str (into {} (map (fn [[k v]] ["wrong/path.md" v]) entries))))
        opened (store/open b h)]
    (is (true? (:routes-available? opened)) "the CAS map is still valid")
    (is (true? (:routes-invalid? opened)))
    (is (:ok? (store/verify b opened)) "a bad route key is cache, not object corruption")))

(deftest an-un-normalized-cache-key-is-not-available
  (let [[dir b s0] (fresh)
        r (root-node)
        s1 (:store (store/commit! b s0 (register-plan nil r T (route-entry "fp1"))))
        root (:routes-root s1)
        f (cache/route-path dir root (:head s1))
        entries (cache/read-routes dir root (:head s1))
        e (get entries "docs/a.md")
        _ (spit f (pr-str {"./docs/a.md" (assoc e :path "./docs/a.md")}))
        opened (store/open b h)]
    (is (true? (:routes-available? opened)))
    (is (true? (:routes-invalid? opened)))
    (is (:ok? (store/verify b opened)))))

(deftest a-tampered-cache-tree-id-does-not-replace-the-durable-locator
  (let [[dir b s0] (fresh)
        r (root-node)
        s1 (:store (store/commit! b s0 (register-plan nil r T (route-entry "fp1"))))
        root (:routes-root s1)
        f (cache/route-path dir root (:head s1))
        entries (cache/read-routes dir root (:head s1))
        _ (spit f (pr-str (assoc-in entries ["docs/a.md" :tree-id]
                                    "01ARZ3NDEKTSV4RRFFQ69G5FAW")))
        opened (store/open b h)]
    (is (= root (:routes-root opened)) "the stable root cannot see the locator")
    (is (true? (:routes-available? opened)))
    (is (true? (:routes-invalid? opened)))
    (is (= "01ARZ3NDEKTSV4RRFFQ69G5FAV" (get-in opened [:routes "docs/a.md" :tree-id])))
    (is (:ok? (store/verify b opened)) "it is a cache/locator problem, not object corruption")))

(deftest a-no-route-commit-with-missing-cache-preserves-root-and-writes-no-empty-cache
  (let [[dir b s0] (fresh)
        r (root-node)
        s1 (:store (store/commit! b s0 (register-plan nil r T (route-entry "fp1"))))
        root (:routes-root s1)
        object-id (:routes-object s1)
        _ (bfs/delete (cache/route-path dir root (:head s1)))
        opened (store/open b h)
        p {:plan/version 1 :base (:head opened) :actor "jp" :engines {} :timestamp TS
           :ops [{:op :add-node :node {:class :sign :kind :text :content {:text "x"}}}]}
        s2 (:store (store/commit! b opened p))]
    (is (= root (:routes-root s2)))
    (is (= object-id (:routes-object s2)) "the durable route-map id is preserved")
    (is (true? (:routes-available? s2)) "the CAS map survives a no-route commit")
    (is (= (:routes s1) (:routes s2)))
    (is (not (bfs/exists? (cache/route-path dir root (:head s2))))
        "a no-route commit does not fabricate an empty cache under the new head")))

(deftest route-coverage-reports-complete-and-partial-stores
  (let [[_ b s0] (fresh)
        r (root-node)
        s1 (:store (store/commit! b s0 (register-plan nil r T (route-entry "fp1"))))
        t2 "01ARZ3NDEKTSV4RRFFQ69G5FAW"
        root2 (node/make h :sign :block {:format :markdown :type :document :attrs {:name "other"}})
        s2 (:store (store/commit! b s1 (register-plan (:head s1) root2 t2 nil)))
        c1 (store/route-coverage s1)
        c2 (store/route-coverage s2)]
    (is (= {:complete? true :cached-trees 1 :routed-trees 1
            :missing-trees [] :orphan-routes []}
           (select-keys c1 [:complete? :cached-trees :routed-trees :missing-trees :orphan-routes])))
    (is (false? (:complete? c2)))
    (is (= 2 (:cached-trees c2)))
    (is (= 1 (:routed-trees c2)))
    (is (= [t2] (:missing-trees c2)))
    (let [orphan (assoc s1 :routes (assoc (:routes s1) "ghost.md"
                                          (assoc (route-entry "fp1") :path "ghost.md" :tree-id t2)))]
      (is (false? (:complete? (store/route-coverage orphan))))
      (is (= ["ghost.md"] (:orphan-routes (store/route-coverage orphan)))))))

(deftest untracking-a-file-does-not-leave-a-partial-route-root
  (let [[_ b s0] (fresh)
        r (root-node)
        s1 (:store (store/commit! b s0 (register-plan nil r T (route-entry "fp1"))))
        anchor (first (filter #(let [e (rev/get-object s1 %)]
                                 (and (= :reference (:type e))
                                      (= :external (:kind (rev/get-object s1 (:to e))))))
                              (rev/active-edges s1)))
        p {:plan/version 1 :base (:head s1) :actor "jp" :engines {} :timestamp TS
           :ops [{:op :remove-route :tree T :path "docs/a.md"}
                 {:op :remove-edge :edge anchor}]}
        s2 (:store (store/commit! b s1 p))]
    (is (= {:complete? true :cached-trees 0 :routed-trees 0
            :missing-trees [] :orphan-routes []}
           (select-keys (store/route-coverage s2)
                        [:complete? :cached-trees :routed-trees :missing-trees :orphan-routes])))
    (is (= 1 (rev/tree-count s2)) "the historical tree remains in the store")))
