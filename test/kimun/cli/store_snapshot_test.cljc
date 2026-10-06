(ns kimun.cli.store-snapshot-test
  "The optional open snapshot of `kimun.cli.store`: `open!` uses it only when
   it matches the heads and the log on disk, and falls back to the replay."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.commands.migrate :as cmd]
            [kimun.cli.store :as cli-store]
            [sldb.host.default :as host]
            [sldb.kernel.store :as store]))

(defn- migrated [only]
  (let [p (str (fs/create-temp-dir {:prefix "kimun-snapshot-"}))
        ex #(edn/read-string (slurp (str "test/fixtures/migrate/descriptors/" (last (re-seq #"[A-Za-z]+" %)) ".edn")))]
    (fs/copy-tree "test/fixtures/migrate/project" p)
    (cmd/dispatch "sldb-v1" [] {:from-v1 true :only only :actor "tester"} {:env {} :cwd p :exporter ex})
    p))

(deftest snapshot-is-used-only-when-it-matches
  (let [p (migrated "NoteDoc") dir (str (fs/path p ".kimun"))
        replayed (store/open (cli-store/backend dir) host/host)]
    (testing "migrate left a snapshot of what a replay gives"
      (is (= replayed (cli-store/read-snapshot dir host/host)))
      (fs/delete (cli-store/snapshot-path dir)))
    (testing "no snapshot: replay"
      (is (nil? (cli-store/read-snapshot dir host/host)))
      (is (= replayed (:store (cli-store/open! dir)))))
    (testing "a snapshot of the current head is the replayed store"
      (is (= (cli-store/snapshot-path dir) (cli-store/write-snapshot! dir replayed)))
      (is (= replayed (cli-store/read-snapshot dir host/host)))
      (is (= replayed (:store (cli-store/open! dir)))))
    (testing "open! really reads it: a marked snapshot shows through"
      (let [f (cli-store/snapshot-path dir)]
        (spit f (pr-str (assoc-in (edn/read-string (slurp f)) [:store :capabilities "marker"] :all)))
        (is (= :all (get-in (:store (cli-store/open! dir)) [:capabilities "marker"])))
        (cli-store/write-snapshot! dir replayed)))
    (testing "a commit after the snapshot invalidates it: open! replays"
      (let [ex #(edn/read-string (slurp (str "test/fixtures/migrate/descriptors/" (last (re-seq #"[A-Za-z]+" %)) ".edn")))
            before (slurp (cli-store/snapshot-path dir))]
        (cmd/dispatch "sldb-v1" [] {:from-v1 true :only "TaskDoc" :actor "tester"} {:env {} :cwd p :exporter ex})
        (spit (cli-store/snapshot-path dir) before))
      (let [now (store/open (cli-store/backend dir) host/host)]
        (is (not= (:head replayed) (:head now)))
        (is (nil? (cli-store/read-snapshot dir host/host)))
        (is (= now (:store (cli-store/open! dir))))
        (is (nil? (cli-store/write-snapshot! dir replayed)) "a stale store value is not written as a snapshot")
        (testing "an unreadable snapshot is ignored"
          (spit (cli-store/snapshot-path dir) "{:snapshot/version 1 :store {")
          (is (nil? (cli-store/read-snapshot dir host/host)))
          (is (= now (:store (cli-store/open! dir)))))
        (testing "a snapshot whose store disagrees with its own key is ignored"
          (cli-store/write-snapshot! dir now)
          (let [f (cli-store/snapshot-path dir)]
            (spit f (pr-str (assoc-in (edn/read-string (slurp f)) [:store :head] "0000")))
            (is (nil? (cli-store/read-snapshot dir host/host)))))))))
