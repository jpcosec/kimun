(ns kimun.test-util
  "Helpers shared by the test suites. Every test temporary lives inside the
   run's own root so a run deletes only its own temporaries: `bb test` creates
   one root per run, points `kimun.test.tmp` at it and removes it at the end.
   Without that property (a direct `bb -e (t/run-tests …)`) `root` falls back to
   the system temp dir, and `KIMUN_TEST_TMP` overrides it too, so two runs in
   different worktrees never touch each other's directories."
  (:require [babashka.fs :as fs]
            [kimun.cli.store :as cli-store]
            [kimun.kernel.store :as kstore]
            [kimun.surface.docs :as tdocs]))

(defn root
  "The temp root of this test run: the `kimun.test.tmp` system property, else
   `$KIMUN_TEST_TMP`, else the system temp dir. Created when absent; the caller
   (or `bb test`) owns its deletion."
  []
  (let [r (or (System/getProperty "kimun.test.tmp")
              (System/getenv "KIMUN_TEST_TMP")
              (System/getProperty "java.io.tmpdir"))]
    (fs/create-dirs r)
    (str r)))

(defn temp-dir
  "A fresh directory inside this run's temp root, named with `prefix`."
  ([] (temp-dir "kimun-"))
  ([prefix] (str (fs/create-temp-dir {:dir (root) :prefix prefix}))))

(defn temp-file
  "A fresh file inside this run's temp root: `prefix` and optional `suffix`."
  ([prefix] (str (fs/create-temp-file {:dir (root) :prefix prefix})))
  ([prefix suffix] (str (fs/create-temp-file {:dir (root) :prefix prefix :suffix suffix}))))

(defn forget-contract!
  "Rewrites the anchor edge of document `name` in the store directory `dir` so
   it carries no `:contract` in its evidence: the shape a document tracked
   before the recorded contract existed has. Reads then fall back to the
   model's current descriptor. Returns the commit result."
  [dir name]
  (let [{:keys [backend store]} (cli-store/open! dir)
        doc (tdocs/find-document store name)
        eid (first (filter #(get-in store [:objects % :evidence :contract]) (:edges doc)))
        _ (when-not eid (throw (ex-info (str "no anchor edge for " name) {:document name})))
        e (get-in store [:objects eid])]
    (kstore/commit! backend store
                    {:plan/version 1 :base (:head store) :actor "human/tester" :engines {}
                     :timestamp "2026-01-01T00:00:00.000Z"
                     :ops [{:op :remove-edge :edge eid}
                           {:op :add-edge :edge (dissoc (update e :evidence dissoc :contract) :id)}]})))
