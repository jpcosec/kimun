;; ports of v1 promises that were parity gaps and are now held
(ns kimun.parity.closed-gaps-test
  "Promises of sldb v1 that the closing audit found broken in kimun
   (docs/v2/tests/parity-gaps.edn) and that were fixed afterwards: each deftest
   is the v1 promise itself, moved here from `known_gaps_test.cljc` when its
   gap closed."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.kernel.store :as store]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run (vec argv) env cwd)))

(defn- project []
  (let [p (str (ktu/temp-dir "kimun-closed-gap-"))]
    (run p "stores" "init" "--name" "p")
    (spit (str (fs/path p "note.edn"))
          (pr-str {:model/name "Note" :model/version 1 :base []
                   :fields [{:name "title" :type :string :required true :description "Title."}]
                   :template "# ⸢rev•title⸥\n"}))
    (is (true? (:ok (run p "models" "define" "-f" "note.edn"))))
    p))

;; v1: api/test_api_documents.py::test_cli_save_payload_still_exits_with_the_message
(deftest update-payload-of-an-untracked-document-names-it
  (let [p (project)]
    (doseq [argv [["docs" "update" "ghost" "--set" "title=x"]
                  ["docs" "update" "--name" "ghost" "--set" "title=x"]]]
      (let [e (apply run p argv)]
        (is (false? (:ok e)) (pr-str argv))
        (is (= [1 "docs/missing"] [(:exit e) (get-in e [:error :type])]) (pr-str argv))
        (is (str/includes? (get-in e [:error :message]) "ghost") "the message names the document")
        (is (= "ghost" (get-in e [:error :data :document])))))))

;; ---------------------------------------------------------------- kernel: concurrent writers
;; Closed by the kernel-scale work (docs/v2/10-escala.md §7): `commit-tx!` compares the heads,
;; appends the log line and replaces heads.edn as one operation under an exclusive lock, so a
;; writer that loses writes no log line; a lock held by another process fails fast with
;; store/busy, or is waited for with --wait.
(defn- log-lines [p] (count (remove str/blank? (str/split-lines (slurp (str (fs/path p ".kimun" "log.edn")))))))

(defn- readable?
  "Every reader and the full verification succeed on the store of `p`."
  [p]
  (doseq [argv [["stores" "check"] ["stores" "verify"] ["docs" "list"] ["journal" "verify"]]]
    (let [e (apply run p argv)] (is (true? (:ok e)) (pr-str argv (:error e))))))

;; v1: store/test_io_atomic_write.py::test_atomic_write_survives_concurrent_writers
;; v1: store/test_update_fault_tolerance.py::test_store_lock_fails_fast_when_busy
;; v1: store/test_update_fault_tolerance.py::test_store_lock_wait_blocks_until_released
;; v1 promise: concurrent writers leave a valid store — the one that cannot write fails fast
;; without damage, and a retry (or --wait) succeeds once the other is done.
(deftest a-writer-that-loses-the-race-is-stale-and-the-store-stays-readable
  (let [p (project)
        real store/commit!
        raced (atom false)
        winner (atom nil)
        before (log-lines p)
        ;; the interleaving, made deterministic: another writer commits between the moment the
        ;; losing command loaded the store and the moment it persists its own transaction
        loser (with-redefs [store/commit! (fn [b s plan]
                                            (when (compare-and-set! raced false true)
                                              (reset! winner (run p "docs" "create" "Note" "winner" "--set" "title=W")))
                                            (real b s plan))]
                (run p "docs" "create" "Note" "loser" "--set" "title=L"))]
    (is (true? @raced))
    (is (true? (:ok @winner)))
    (testing "the losing writer fails fast, with the conflict exit code and a reason"
      (is (= "store/stale" (get-in loser [:error :type])))
      (is (= 4 (:exit loser))))
    (testing "and writes nothing to the log: only the winner's transaction is there"
      (is (= (inc before) (log-lines p))))
    (testing "the store still opens and verifies"
      (readable? p))
    (testing "a retry on a freshly opened store succeeds"
      (is (true? (:ok (run p "docs" "create" "Note" "loser" "--set" "title=L"))))
      (is (= (+ before 2) (log-lines p)))
      (is (= #{"winner" "loser"} (set (keep #(#{"winner" "loser"} (:name %)) (:data (run p "docs" "list"))))))
      (readable? p))))

;; v1: store/test_io_atomic_write.py::test_atomic_write_survives_concurrent_writers
(deftest many-concurrent-writers-leave-a-valid-store
  (let [p (project)
        before (log-lines p)
        results (mapv deref (mapv (fn [i] (future (run p "docs" "create" "Note" (str "t" i) "--set" (str "title=T" i)))) (range 4)))
        ok (filter :ok results)]
    (is (seq ok) "at least one writer wins")
    (is (every? #(or (:ok %) (= ["store/stale" 4] [(get-in % [:error :type]) (:exit %)])) results)
        "every other one is a clean rejection")
    (is (= (+ before (count ok)) (log-lines p)) "one log line per successful writer, none from the others")
    (is (= (set (map #(get-in % [:data :name]) ok))
           (set (keep #(re-matches #"t\d" (:name %)) (:data (run p "docs" "list"))))))
    (readable? p)))

(defn- hold-lock!
  "Starts another bb process that takes the store's write lock (<store>/lock) of
   project `p`, signals by creating `ready`, holds it `ms` milliseconds and
   exits. Returns the process once the lock is held."
  [p ms]
  (let [ready (str (fs/path (ktu/temp-dir "kimun-lock-ready-") "ready"))
        bb (.orElse (.command (.info (java.lang.ProcessHandle/current))) "bb")
        code (str "(let [ch (java.nio.channels.FileChannel/open (java.nio.file.Paths/get " (pr-str (str (fs/path p ".kimun" "lock"))) " (into-array String []))"
                  " (into-array java.nio.file.StandardOpenOption [java.nio.file.StandardOpenOption/CREATE java.nio.file.StandardOpenOption/WRITE]))]"
                  " (.lock ch) (spit " (pr-str ready) " \"\") (Thread/sleep " ms ") (.close ch))")
        proc (process/process [bb "-e" code])]
    (loop [i 0] (when (and (< i 200) (not (fs/exists? ready))) (Thread/sleep 50) (recur (inc i))))
    (is (fs/exists? ready) "the other process holds the lock")
    proc))

;; v1: store/test_update_fault_tolerance.py::test_store_lock_fails_fast_when_busy
(deftest a-write-while-another-process-holds-the-store-fails-fast
  (let [p (project)
        before (log-lines p)
        holder (hold-lock! p 4000)
        t0 (System/nanoTime)
        e (run p "docs" "create" "Note" "busy" "--set" "title=B")
        ms (/ (- (System/nanoTime) t0) 1e6)]
    (is (= ["store/busy" 4] [(get-in e [:error :type]) (:exit e)]) (pr-str e))
    (is (str/includes? (get-in e [:error :message]) "--wait"))
    (is (< ms 3000) "it does not wait for the other writer")
    @holder
    (is (= before (log-lines p)) "nothing was committed")
    (readable? p)
    (is (true? (:ok (run p "docs" "create" "Note" "busy" "--set" "title=B"))) "once the other is done, the retry succeeds")))

;; v1: store/test_update_fault_tolerance.py::test_store_lock_wait_blocks_until_released
(deftest with-wait-a-write-blocks-until-the-lock-is-released-then-succeeds
  (let [p (project)
        before (log-lines p)
        holder (hold-lock! p 1500)
        t0 (System/nanoTime)
        e (run p "docs" "create" "Note" "patient" "--set" "title=P" "--wait")
        ms (/ (- (System/nanoTime) t0) 1e6)]
    (is (true? (:ok e)) (pr-str e))
    (is (> ms 500) "it waited for the other writer")
    (is (= 0 (:exit @holder)))
    (is (= (inc before) (log-lines p)))
    (readable? p)))

;; ---------------------------------------------------------------- edges and graph across linked stores
;; Closed by the linked-store federation of `kimun.surface.edges/index`
;; (`docs/v2/06`): the caller opens the reachable linked stores and passes them
;; under `:linked`, and their documents and sections join qualified by the link
;; name (v1 `store/edge_index/federation.qualify`); `--local` leaves them out.

(defn- table-file [p n] (str (fs/path p n)))

(defn- table-store
  "A store with model `Table` and one tracked document named `doc`."
  [doc]
  (let [p (str (ktu/temp-dir "kimun-closed-gap-"))]
    (run p "stores" "init" "--name" "p")
    (spit (table-file p "table.edn")
          (pr-str {:model/name "Table" :model/version 1 :base []
                   :fields [{:name "title" :type :string :required true :description "Title."}]
                   :template "# ⸢rev•title⸥\n"}))
    (is (true? (:ok (run p "models" "define" "-f" "table.edn"))))
    (is (true? (:ok (run p "docs" "create" "Table" doc "--set" (str "title=" doc)))))
    p))

(defn- linked-tables
  "Store `p` (document `t12`) with the store `q` (document `t99`) linked under
   `annex`."
  []
  (let [p (table-store "t12") q (table-store "t99")]
    (is (true? (:ok (run p "stores" "link" "annex" q))))
    p))

(defn- node-ids [e] (set (map :id (get-in e [:data :nodes]))))
(defn- export-ids [e] (set (map #(get % "id") (get-in e [:data "nodes"]))))

;; v1: api/test_api_edges.py::test_linked_store_documents_come_back_qualified
(deftest linked-store-documents-come-back-qualified
  (let [p (linked-tables)]
    (testing "the edge index carries the linked store's document, qualified by the link name"
      (is (contains? (node-ids (run p "edges" "nodes")) "sldb://document/annex:Table:t99")))
    (testing "the qualified node names its store and the model has a has_document edge to it"
      (is (= "annex" (get-in (run p "edges" "node" "annex:Table:t99") [:data :semantics "store"])))
      (is (= ["sldb://document/Table:t12" "sldb://document/annex:Table:t99"]
             (mapv :target (filter #(= "has_document" (:relation %))
                                   (get-in (run p "edges" "show" "--from" "sldb://model/Table") [:data :edges]))))))
    (testing "include_linked=False (--local) drops them"
      (let [ids (node-ids (run p "edges" "nodes" "--local"))]
        (is (contains? ids "sldb://document/Table:t12"))
        (is (not-any? #(str/includes? % "annex") ids))))))

;; v1: api/test_graph_source_parity.py::test_snapshot_save_keeps_the_legacy_route_for_linked_stores_gap_G5
(deftest graph-export-composes-linked-stores
  (let [p (linked-tables)]
    (testing "graph export composes the linked store's documents, qualified by the link name"
      (is (contains? (export-ids (run p "graph" "export")) "sldb://document/annex:Table:t99")))
    (testing "and the same for graph list and graph snapshot save"
      (is (some #{"sldb://document/annex:Table:t99"} (get-in (run p "graph" "list") [:data :nodes])))
      (is (= "with-links.json" (get-in (run p "graph" "snapshot" "save" "with-links.json") [:data :path]))
          "snapshot save reports the file it wrote"))
    (testing "include_linked=False (--local) exports only the local store"
      (let [ids (export-ids (run p "graph" "export" "--local"))]
        (is (contains? ids "sldb://document/Table:t12"))
        (is (not-any? #(str/includes? % "annex") ids))))))
