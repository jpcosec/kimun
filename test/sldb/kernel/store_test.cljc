(ns sldb.kernel.store-test
  (:require [clojure.test :refer [deftest is testing]]
            [sldb.kernel.test-util :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.edn :as edn]
            [sldb.host.hash :as hash]
            [sldb.kernel.revision :as rev]
            [sldb.kernel.store :as store]
            [sldb.host.fs-store :as fs]
            [sldb.kernel.generators :as g]
            #?@(:clj [[clojure.java.io :as io]
                      [babashka.fs :as bfs]
                      [babashka.process :as p]])))

(def h hash/sha-256)

(defn- read-fixture [f] #?(:clj (edn/read-string (slurp (io/file f)))))
(defn- tmp-dir [] #?(:clj (str (bfs/create-temp-dir {:prefix "sldb-store-"}))))

(defn- snapshot [s]
  {:head (:head s) :heads (:heads s)
   :revisions (:revisions s)
   :roots (into {} (map (fn [[tid t]] [tid (get-in t [:objects (get-in t [:descriptor :root])])])) (:trees s))
   :edges (:edges s)
   :nodes (set (keep (fn [[id o]] (when (:class o) id)) (:objects s)))
   :tree-sets (set (map :trees (vals (:revisions s))))
   :edge-sets (set (map :edges (vals (:revisions s))))})

(defn- populate!
  "init + tx-001 + the base plans of tx-002 in `dir`; returns the final in-memory store."
  [dir]
  (let [b (fs/backend dir)
        fx1 (read-fixture "test/fixtures/tx-001.edn")
        fx2 (read-fixture "test/fixtures/tx-002-conflict.edn")
        s0 (store/init! b h (get-in fx1 [:store :capabilities]))
        s1 (:store (store/commit! b s0 (:plan fx1)))
        s2 (:store (store/commit! b s1 (second (:base-plans fx2))))]
    s2))

(deftest close-and-reopen-reproduces-state-and-hashes-invariants-1-3-10
  (let [dir (tmp-dir)
        before (populate! dir)
        after (store/open (fs/backend dir) h)]
    (is (= (snapshot before) (snapshot after)))
    (is (= 2 (count (:revisions after))))))

(deftest replay-from-log-and-objects-only
  (let [dir (tmp-dir)
        before (populate! dir)
        _ (bfs/delete (str dir "/heads.edn"))
        after (store/replay (fs/backend dir) h)]
    (is (= (:head before) (:head after)))
    (is (= (:heads before) (:heads after)))
    (is (= (:revisions before) (:revisions after)))))

(deftest every-object-file-hashes-to-its-name-invariant-17
  (let [dir (tmp-dir)
        s (populate! dir)
        b (fs/backend dir)
        v (store/verify b h s)]
    (is (:ok? v) (pr-str (:bad v)))
    (is (pos? (:checked v)))
    (is (every? #(= % (hash/hash-bytes h (hash/utf8-bytes (store/read-object b %)))) (store/object-ids b)))))

(deftest corrupting-one-object-makes-verify-fail-naming-it
  (let [dir (tmp-dir)
        s (populate! dir)
        b (fs/backend dir)
        victim (first (sort (store/reachable s)))]
    (spit (str dir "/objects/" victim) "tampered")
    (let [v (store/verify b h s)]
      (is (not (:ok? v)))
      (is (= [{:id victim :reason :hash-mismatch}] (:bad v))))))

(deftest stale-heads-cas-is-rejected-without-writing
  (let [dir (tmp-dir)
        s (populate! dir)
        b (fs/backend dir)
        fx2 (read-fixture "test/fixtures/tx-002-conflict.edn")
        stale (store/open b h)
        ;; another writer commits first
        _ (store/commit! b s (assoc (:disjoint-plan fx2) :base (:head s)))
        heads-before (store/read-heads b)
        e (try (store/commit! b stale (assoc (:disjoint-plan fx2) :base (:head stale) :timestamp "2026-08-29T12:00:09.000Z"
                                             :ops [{:op :add-node :node {:class :sign :kind :text :content {:text "late"}} :as :x}
                                                   {:op :add-node :node {:class :sign :kind :block :content {:format :markdown :type :document :attrs {:name "late"}}} :as :r}
                                                   {:op :new-tree :tree {:kind :document :name "late"} :id "01ARZ3NDEKTSV4RRFFQ69G5FAX" :root :r}]))
               nil
               (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e (ex-data e)))]
    (is (= :store/stale (:type e)))
    (is (= heads-before (store/read-heads b)) "heads.edn untouched")))

(deftest reopen-from-a-separate-bb-process
  (let [dir (tmp-dir)
        before (populate! dir)
        out (-> (p/shell {:out :string :err :string :continue true}
                         "bb" "-cp" "src" "-e"
                         (str "(require '[sldb.kernel.store :as s] '[sldb.host.fs-store :as fs] '[sldb.host.hash :as h]) "
                              "(let [st (s/open (fs/backend \"" dir "\") h/sha-256)] (prn [(:head st) (:heads st) (count (:revisions st))]))"))
                :out edn/read-string)]
    (is (= [(:head before) (:heads before) 2] out))))

(defspec any-valid-plan-sequence-reloads-identically 20
  (prop/for-all [ps g/gen-plan-sequence]
    (let [dir (tmp-dir)
          b (fs/backend dir)
          s (reduce (fn [s p] (:store (store/commit! b s (assoc p :base (:head s)))))
                    (store/init! b h {"jp" :all})
                    ps)
          reloaded (store/open b h)]
      (and (= (snapshot s) (snapshot reloaded))
           (:ok? (store/verify b h reloaded))))))
