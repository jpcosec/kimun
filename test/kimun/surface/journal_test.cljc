(ns kimun.surface.journal-test
  "The journal view (`kimun.surface.journal`) over stores with real history
   built through the existing CLI groups: order, actor and timestamp, one entry
   per write, before/after text, filters, and verification of the revision DAG.
   Each v1 test of `tests/api/test_journal.py` is ported or named as deferred."
  (:require [clojure.test :refer [deftest is testing]]
            [babashka.fs :as fs]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cs]
            [kimun.surface.journal :as journal]
            [sldb.kernel.store :as store]))

(def env {"USER" "tester"})

(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))

(defn- project
  "A temp project with an initialised store, `doc.md` holding `text`."
  [text]
  (let [proj (str (fs/create-temp-dir {:prefix "kimun-journal-"}))]
    (run proj "stores" "init" "--name" "p")
    (spit (str (fs/path proj "doc.md")) text)
    proj))

(defn- history
  "Project with `doc` ingested, then updated twice (three transactions)."
  []
  (let [proj (project "# T\n\npara one\n")]
    (run proj "docs" "ingest" "doc.md")
    (spit (str (fs/path proj "doc.md")) "# T\n\npara two\n")
    (run proj "docs" "update" "doc.md" "--name" "doc" "--actor" "kimun/derive")
    (spit (str (fs/path proj "doc.md")) "# T\n\npara three\n")
    (run proj "docs" "update" "doc.md" "--name" "doc")
    proj))

(defn- loaded [proj]
  (let [{:keys [backend store]} (cs/open! (cs/resolve-store {} env proj))]
    {:store store :txs (store/read-log backend) :backend backend}))

;; v1 test_chain_verifies_after_mixed_writes: mixed writes leave a verifiable chain.
(deftest chain-verifies-after-mixed-writes
  (let [{:keys [store txs]} (loaded (history))
        r (journal/verify store txs)]
    (is (true? (:ok? r)) (pr-str r))
    (is (= 3 (:revisions r)))
    (is (= 3 (:checked r)))
    (is (= [] (:bad r)))))

;; v1 test_each_document_route_leaves_an_entry: an entry per write, newest first.
(deftest each-write-leaves-an-entry
  (let [{:keys [store txs]} (loaded (history))
        es (journal/entries store txs)]
    (is (= 3 (count es)))
    (is (= (map :revision (reverse txs)) (map :revision es)) "newest first")
    (is (= [{:add-node 5 :new-tree 1 :add-edge 4}] (take 1 (reverse (map :ops es)))))
    (is (= (:head store) (:revision (first es))))
    (is (= [] (:parents (last es))))
    (is (= [(:revision (second es))] (:parents (first es))) "the parent link is the chain")
    (testing "actor and timestamp are those recorded"
      (is (= "kimun/derive" (:actor (second es))))
      (is (= "human/tester" (:actor (first es))))
      (is (every? string? (map :timestamp es)))
      (is (= (mapv :id (reverse txs)) (mapv :transaction es))))))

;; v1 test_untrack_then_track_repair_leaves_both: successive writes all remain visible and verify.
(deftest successive-writes-all-remain
  (let [proj (project "# A\n")
        _ (run proj "docs" "ingest" "doc.md" "--name" "a")
        _ (run proj "docs" "ingest" "doc.md" "--name" "b")
        _ (run proj "docs" "update" "doc.md" "--name" "a")
        {:keys [store txs]} (loaded proj)]
    (is (= 3 (count (journal/entries store txs))))
    (is (true? (:ok? (journal/verify store txs))))))

;; v1 test_store_without_journal_reads_empty: a store with no writes reads empty and verifies.
(deftest fresh-store-reads-empty
  (let [{:keys [store txs]} (loaded (project "x"))]
    (is (= [] (journal/entries store txs)))
    (is (true? (:ok? (journal/verify store txs))))))

;; v1 test_save_entry_carries_before_and_after (the text part): the entry says what changed.
(deftest entry-carries-before-and-after-text
  (let [{:keys [store txs]} (loaded (history))
        [e2 e1 e0] (journal/entries store txs)]
    (is (= [{:name "doc" :kind :document}] (mapv #(select-keys % [:name :kind]) (:trees e2))))
    (let [c (first (:changes e2))]
      (is (= {:before ["para two"] :after ["para three"]} (:text c)))
      (is (false? (:created? c)))
      (is (pos? (:nodes-added c)))
      (is (pos? (:nodes-removed c))))
    (is (= {:before ["para one"] :after ["para two"]} (:text (first (:changes e1)))))
    (let [c (first (:changes e0))]
      (is (true? (:created? c)))
      (is (= ["T" "para one"] (sort (get-in c [:text :after]))))
      (is (= [] (get-in c [:text :before]))))))

(deftest filters
  (let [{:keys [store txs]} (loaded (history))
        all (journal/entries store txs)
        tid (:tree (first (:trees (first all))))]
    (is (= 2 (count (journal/entries store txs {:limit 2}))))
    (is (= (take 2 all) (journal/entries store txs {:limit 2})) "limit keeps the newest")
    (is (= 1 (count (journal/entries store txs {:actor "kimun/derive"}))))
    (is (= 3 (count (journal/entries store txs {:tree "doc"}))))
    (is (= 3 (count (journal/entries store txs {:tree tid}))))
    (is (= [] (journal/entries store txs {:tree "nope"})))))

;; v1 test_write_cost_with_and_without_journal: the journal adds nothing to a write; n writes, n entries, n log lines.
(deftest journal-adds-no-write-cost
  (let [proj (project "# A\n")
        _ (run proj "docs" "ingest" "doc.md")
        n 5]
    (dotimes [i n]
      (spit (str (fs/path proj "doc.md")) (str "# A\n\nv" i "\n"))
      (run proj "docs" "update" "doc.md" "--name" "doc"))
    (let [{:keys [store txs]} (loaded proj)]
      (is (= (inc n) (count txs)) "one log line per write, no second store")
      (is (>= (count (journal/entries store txs)) n))
      (is (not (fs/exists? (fs/path proj ".kimun" "journal.edn")))))))

;; v1 tampering is detected: the verifier flags a revision that no longer hashes to its id,
;; a missing parent, and a log that disagrees with the store.
(deftest verify-detects-tampering
  (let [{:keys [store txs]} (loaded (history))
        rid (:revision (second txs))]
    (testing "revision content altered"
      (let [r (journal/verify (assoc-in store [:revisions rid :actor] "evil") txs)]
        (is (false? (:ok? r)))
        (is (some #(and (= rid (:revision %)) (= :hash-mismatch (:reason %))) (:bad r)))))
    (testing "parent removed"
      (let [parent (:revision (first txs))
            r (journal/verify (update store :revisions dissoc parent) txs)]
        (is (some #(= :missing-parent (:reason %)) (:bad r)))
        (is (some #(= :missing-revision (:reason %)) (:bad r)))))
    (testing "log disagrees with the head"
      (let [r (journal/verify store (butlast txs))]
        (is (some #(= :head-mismatch (:reason %)) (:bad r)))))
    (testing "transaction id swapped"
      (let [r (journal/verify store (assoc-in (vec txs) [1 :id] "deadbeef"))]
        (is (some #(= :transaction-mismatch (:reason %)) (:bad r)))))))

;; DEFERRED from v1 (needs typed model fields): test_add_model_leaves_an_entry,
;; test_reindex_and_promote_leave_entries, and the field/address part of
;; test_save_entry_carries_before_and_after. See the final report.
