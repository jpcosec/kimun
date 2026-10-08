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
            [kimun.kernel.store :as store]
            [kimun.surface.models.descriptor :as descriptor]))

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

;; ---------------------------------------------------------------- engine: field-add neutrality
;; v1: store/test_optional_field_hash_neutral.py::test_model_extension_is_neutral_for_document_that_does_not_use_field
;; (the `models field-add` form of the promise; the `models define` form is ported in
;; kimun.parity.optional-field-hash-test)
;; The promise: adding an optional field leaves a document that does not use it unchanged.
;; `models field-add` appends a trailing `## F` section with an `optrev` slot; an absent
;; optional heading must be absent, not re-bind the block that belongs to the previous
;; section (the last slot of the document).
(defn- neutrality-store []
  (let [p (str (ktu/temp-dir "kimun-closed-field-add-"))]
    (run p "stores" "init" "--name" "p")
    (spit (str (fs/path p "n.edn"))
          (pr-str {:model/name "Note" :model/version 1 :base []
                   :fields [{:name "title" :type :string :required true :description "Title."}
                            {:name "body" :type :string :required true :description "Body."}]
                   :template "---\ntitle: ⸢rev•title⸥\n---\n\n# ⸢render•title⸥\n\n⸢rev•body⸥"}))
    (is (true? (:ok (run p "models" "define" "-f" "n.edn"))))
    (is (true? (:ok (run p "docs" "create" "Note" "hello" "--set" "title=Hello" "--set" "body=World body"))))
    p))

(deftest field-add-of-an-optional-field-is-neutral-for-existing-documents
  (let [p (neutrality-store)]
    (is (= {"title" "Hello" "body" "World body"}
           (get-in (run p "docs" "show" "hello") [:data :payload]))
        "the document before the model moves")
    (is (true? (:ok (run p "models" "field-add" "Note" "--field" "summary_en" "--type" "string"
                         "--description" "Optional summary."))))
    (is (= {"title" "Hello" "body" "World body"}
           (get-in (run p "docs" "show" "hello") [:data :payload]))
        "no value chased into the new field; the body keeps its content")
    (testing "a document that uses the field keeps the value in the new field"
      (is (true? (:ok (run p "docs" "create" "Note" "bonjour" "--set" "title=H" "--set" "body=B"
                           "--set" "summary_en=Salut"))))
      (is (= {"title" "H" "body" "B" "summary_en" "Salut"}
             (get-in (run p "docs" "show" "bonjour") [:data :payload]))))))

(deftest field-add-of-a-required-field-with-default-is-neutral-for-existing-fields
  (let [p (neutrality-store)]
    (is (true? (:ok (run p "models" "field-add" "Note" "--field" "priority" "--type" "int"
                         "--description" "Priority." "--default" "5" "--required"))))
    (is (= {"title" "Hello" "body" "World body" "priority" 5}
           (get-in (run p "docs" "show" "hello") [:data :payload]))
        "the default lands in the new field; the old fields are untouched")))

;; ---------------------------------------------------------------- engine: field descriptions
;; v1: test_standalone.py::test_structured_nl_doc_requires_field_descriptions
(deftest descriptor-requires-a-non-empty-description-per-field
  (let [base {:model/name "MissingDescriptionDoc" :model/version 1 :base []
              :template "# ⸢rev•title⸥"}
        fail (fn [fields] (try (descriptor/validate! (assoc base :fields fields)) nil
                               (catch clojure.lang.ExceptionInfo e e)))]
    (doseq [fields [[{:name "title" :type :string :required true}]
                    [{:name "title" :type :string :required true :description ""}]
                    [{:name "title" :type :string :required true :description "   "}]]]
      (let [e (fail fields)]
        (is (= :models/invalid (:type (ex-data e))))
        (is (str/includes? (ex-message e) "fields must define a non-empty description"))))
    (is (str/includes? (ex-message (fail [{:name "title" :type :string :required true}
                                          {:name "body" :type :string :required true}]))
                       "title, body")
        "every undescribed field is named")
    (is (nil? (fail [{:name "title" :type :string :required true :description "Title."}])))))

(deftest field-add-requires-a-description
  (let [p (neutrality-store)
        version (fn [] (get-in (run p "models" "list") [:data 0 :version]))
        before (version)
        e (run p "models" "field-add" "Note" "--field" "summary_en" "--type" "string")]
    (is (= [6 "cli/usage"] [(:exit e) (get-in e [:error :type])]) "the flag is mandatory")
    (is (= before (version)) "nothing was defined without a description")))
