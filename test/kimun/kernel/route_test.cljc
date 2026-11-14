(ns kimun.kernel.route-test
  "The pure route Merkle (K2a): entries sorted by path, the root independent of
   `:tree-id`/revision/time, duplicate paths visible, and a path diff for local
   reconciliation. No store and no new identity are involved."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.kernel.route :as route]))

(def h host/host)

(defn- fixture []
  (edn/read-string (slurp (io/resource "fixtures/kernel/route-entries.edn"))))

(def a {:path "docs/a.md" :fingerprint "sha-256:111" :class "TestDoc"
        :projection-hash "aaaa" :content-hash "bbbb" :tree-id "T1"})
(def b {:path "docs/b.md" :fingerprint "sha-256:222" :class "TestDoc"
        :projection-hash "cccc" :content-hash "dddd" :tree-id "T2"})

(deftest canonical-entries-sort-by-path-and-keep-tree-id-out-of-the-root
  (let [es (route/canonical-entries h [b a])
        root (route/root h [a b])
        root' (route/root h [(assoc a :tree-id "OTHER") b])]
    (is (= ["docs/a.md" "docs/b.md"] (mapv :path es)))
    (is (= root root') "the nominal tree-id is a locator, not part of the route root")
    (is (= "T1" (:tree-id (route/lookup h es "docs/a.md"))))
    (is (= "348a08fe24e1a340377e8cd399bbf3cad0cd46fb0d75777e868c3ba845b9d757"
           (route/root h (fixture)))
        "golden root of the fixture; rebuilds with another tree-id keep it")))

(deftest a-stable-field-change-moves-the-root
  (let [root (route/root h [a b])]
    (doseq [k [:fingerprint :class :projection-hash :content-hash]]
      (is (not= root (route/root h [(assoc a k "changed") b])) (name k)))
    (is (not= root (route/root h [(assoc a :path "docs/c.md") b])) "path is part of the route key")))

(deftest duplicate-and-unsafe-paths-are-visible
  (doseq [p ["" "/abs.md" "../x.md" "a//b.md" "a/./b.md" "a\u0000b.md"]]
    (is (thrown? clojure.lang.ExceptionInfo (route/root h [(assoc a :path p)])) p))
  (let [e (try (route/root h [a (assoc a :tree-id "T9")]) nil
               (catch clojure.lang.ExceptionInfo e e))]
    (is (= :route/ambiguous (:type (ex-data e))))))

(deftest lookup-normalizes-the-path-and-diff-compares-stable-fields
  (let [es [a b]
        changed (assoc a :content-hash "new")
        d (route/diff h es [changed (assoc b :tree-id "NEW") (assoc a :path "docs/c.md")])]
    (is (= "T1" (:tree-id (route/lookup h es "./docs/a.md"))))
    (is (= ["docs/c.md"] (mapv :path (:added d))))
    (is (= [] (:removed d)) "docs/a.md changed, it was not removed")
    (is (= ["docs/a.md"] (mapv :path (:changed d))))
    (is (= 1 (count (:changed d))))
    (is (= "new" (get-in d [:changed 0 :after :content-hash])))
    (testing "a tree-id-only move is not a content change"
      (is (= [] (:changed (route/diff h es [(assoc a :tree-id "NEW") b])))))))

(deftest unicode-equivalent-paths-collide-after-nfc
  (let [precomposed (assoc a :path "docs/é.md")
        decomposed (assoc a :path "docs/e\u0301.md")
        e (try (route/root h [precomposed decomposed]) nil
               (catch clojure.lang.ExceptionInfo e e))]
    (is (= :route/ambiguous (:type (ex-data e))))
    (is (= "docs/é.md" (:path (route/lookup h [precomposed] "docs/e\u0301.md")))
        "lookup normalizes the query path to NFC")))

(deftest windows-drive-prefixes-are-rejected
  (doseq [p ["C:/x.md" "C:x.md" "c:\\x.md" "//server/share.md"]]
    (is (thrown? clojure.lang.ExceptionInfo (route/root h [(assoc a :path p)])) p)))
