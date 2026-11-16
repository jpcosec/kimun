(ns kimun.kernel.route-test
  "The pure route entries (K2a): validated, sorted by path, duplicate and unsafe
   paths visible; the route root (the tree of `kimun.kernel.route-tree`) does
   not depend on `:tree-id`. No store and no new identity are involved."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.kernel.route :as route]
            [kimun.kernel.route-tree :as rt]))

(def h host/host)

(defn- fixture []
  (edn/read-string (slurp (io/resource "fixtures/kernel/route-entries.edn"))))

(def a {:path "docs/a.md" :fingerprint "sha-256:111" :class "TestDoc"
        :projection-hash "aaaa" :content-hash "bbbb" :tree-id "T1"})
(def b {:path "docs/b.md" :fingerprint "sha-256:222" :class "TestDoc"
        :projection-hash "cccc" :content-hash "dddd" :tree-id "T2"})

(defn- root [entries] (:hash (rt/from-entries h entries)))

(deftest canonical-entries-sort-by-path-and-keep-tree-id-out-of-the-root
  (let [es (route/canonical-entries h [b a])]
    (is (= ["docs/a.md" "docs/b.md"] (mapv :path es)))
    (is (= (root [a b]) (root [(assoc a :tree-id "OTHER") b])) "the nominal tree-id is a locator, not part of the route root")
    (is (= "T1" (:tree-id (first es))))
    (is (= "29412cd5b340ef30104b604b148973feaa3c97cd360240bba88c52b714154f37"
           (root (fixture)))
        "golden root of the fixture; rebuilds with another tree-id keep it")))

(deftest a-stable-field-change-moves-the-root
  (let [r (root [a b])]
    (doseq [k [:fingerprint :class :projection-hash :content-hash]]
      (is (not= r (root [(assoc a k "changed") b])) (name k)))
    (is (not= r (root [(assoc a :path "docs/c.md") b])) "path is part of the route key")))

(deftest duplicate-and-unsafe-paths-are-visible
  (doseq [p ["" "/abs.md" "../x.md" "a//b.md" "a/./b.md" "a\u0000b.md"]]
    (is (thrown? clojure.lang.ExceptionInfo (route/canonical-entries h [(assoc a :path p)])) p))
  (let [e (try (route/canonical-entries h [a (assoc a :tree-id "T9")]) nil
               (catch clojure.lang.ExceptionInfo e e))]
    (is (= :route/ambiguous (:type (ex-data e))))))

(deftest unicode-equivalent-paths-collide-after-nfc
  (let [precomposed (assoc a :path "docs/é.md")
        decomposed (assoc a :path "docs/e\u0301.md")
        e (try (route/canonical-entries h [precomposed decomposed]) nil
               (catch clojure.lang.ExceptionInfo e e))]
    (is (= :route/ambiguous (:type (ex-data e))))
    (is (= "docs/é.md" (:path (route/entry h decomposed)))
        "an entry's path is normalized to NFC")))

(deftest windows-drive-prefixes-are-rejected
  (doseq [p ["C:/x.md" "C:x.md" "c:\\x.md" "//server/share.md"]]
    (is (thrown? clojure.lang.ExceptionInfo (route/canonical-entries h [(assoc a :path p)])) p)))
