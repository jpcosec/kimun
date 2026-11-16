(ns kimun.parity.cli-store-test
  "Ports of sldb v1's store/test_cli_store.py: `stores init` and `models define`
   (v1 `models add`) on temporary stores. The global-catalog tests, `--force`
   and the extraction-cache purge are n/a (see parity-v1.md, deviations)."
  (:require [kimun.test-util :as ktu] [clojure.test :refer [deftest is]]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [kimun.cli.main :as main]))

(def env {"USER" "tester"})
(def ticket (str (fs/absolutize "test/fixtures/models/ticket.edn")))

(defn- tmp [] (str (ktu/temp-dir "kimun-clistore-")))
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- init! [] (let [p (tmp)] (run p "stores" "init" "--name" "p") p))
(defn- with-model! [] (let [p (init!)] (run p "models" "define" "-f" ticket) p))

;; v1: store/test_cli_store.py::test_store_init_creates_index
(deftest store-init-creates-an-empty-store
  (let [p (tmp) e (run p "stores" "init" "--name" "p")]
    (is (true? (:ok e)) (pr-str e))
    (is (fs/directory? (fs/path p ".kimun")))
    (is (= [] (:data (run p "models" "list"))) "no models yet")
    (is (= [0 0 0] ((juxt :revisions :trees :objects) (:data (run p "stores" "check")))))
    (is (true? (:ok (run p "stores" "verify"))))))

;; v1: store/test_cli_store.py::test_store_init_fails_if_exists
;; v1 answered exit 0 ("idempotent"); kimun refuses explicitly (`cli/store-exists`).
;; What both promise is that a second init leaves the existing store intact.
(deftest second-store-init-leaves-the-store-intact
  (let [p (with-model!) before (:data (run p "stores" "check"))
        again (run p "stores" "init" "--name" "other")]
    (is (= "cli/store-exists" (get-in again [:error :type])))
    (is (= before (:data (run p "stores" "check"))) "heads and objects untouched")
    (is (= ["TicketDoc"] (mapv :name (:data (run p "models" "list")))))))

;; v1: store/test_cli_store.py::test_model_add_registers_model
(deftest model-define-registers-the-model
  (let [p (init!) e (run p "models" "define" "-f" ticket)]
    (is (true? (:ok e)) (pr-str e))
    (is (= ["TicketDoc"] (mapv :name (:data (run p "models" "list")))))
    (is (= "TicketDoc" (get-in (run p "models" "show" "TicketDoc") [:data :descriptor :model/name])))))

;; v1: store/test_cli_store.py::test_model_add_creates_index_files
;; v1 wrote a models index and an empty documents index per model; kimun has no
;; side files: the model is a node of the store and starts with zero documents.
(deftest model-define-starts-with-no-documents-and-no-side-files
  (let [p (with-model!)
        entry (first (:data (run p "models" "list")))]
    (is (= [1 0 0] ((juxt :version :documents :not-intact) entry)))
    (is (= [] (:data (run p "docs" "list"))))
    (is (empty? (filter #(str/includes? (str/lower-case (str %)) "ticketdoc")
                        (fs/glob (fs/path p ".kimun") "**" {:follow-links false})))
        "no per-model index file: the model is a node inside the store's objects")))

;; v1: store/test_cli_store.py::test_get_store_context_fails_when_no_local_and_no_global
(deftest no-store-anywhere-says-how-to-init
  (let [e (run (tmp) "models" "list")]
    (is (= [5 "cli/no-store"] [(:exit e) (get-in e [:error :type])]))
    (is (str/includes? (get-in e [:error :message]) "stores init"))))

;; v1: store/test_cli_store.py::test_model_add_sets_hash_a
;; v1 recorded the store-wide hash A at registration; kimun's counterpart is the
;; head revision that the define commits and that `stores verify` recomputes.
(deftest model-define-commits-a-verifiable-head
  (let [p (init!) e (run p "models" "define" "-f" ticket)
        chk (:data (run p "stores" "check"))]
    (is (string? (:revision e)))
    (is (= (:revision e) (:head chk)))
    (is (= 1 (:revisions chk)))
    (is (true? (:ok (run p "stores" "verify"))))))

;; v1: store/test_cli_store.py::test_model_add_invalid_ref_reports_not_found_and_lists_available
(deftest unknown-model-reports-missing-and-lists-the-known-ones
  (let [p (with-model!)
        e (run p "docs" "create" "FooBar" "x" "--set" "title=a")]
    (is (not (:ok e)))
    (is (= "models/missing" (get-in e [:error :type])))
    (is (str/includes? (get-in e [:error :message]) "FooBar"))
    (is (= ["TicketDoc"] (get-in e [:error :data :known])) "the available models are listed")))

;; v1: store/test_cli_store.py::test_model_add_missing_store_reports_tried_path
(deftest missing-store-reports-the-tried-path
  (let [missing (str (fs/path (tmp) "no-such-store" ".kimun"))
        e (run (tmp) "models" "define" "-f" ticket "--store" missing)]
    (is (= [5 "cli/no-store"] [(:exit e) (get-in e [:error :type])]))
    (is (str/includes? (get-in e [:error :message]) missing))
    (is (not (fs/exists? missing)) "nothing is created at the tried path")))

;; v1: store/test_cli_store.py::test_model_add_already_registered_is_noop
(deftest redefining-an-unchanged-model-is-a-noop
  (let [p (with-model!) before (:data (run p "stores" "check"))
        again (run p "models" "define" "-f" ticket)]
    (is (true? (:ok again)))
    (is (= {:unchanged? true :created? false :version 1} (select-keys (:data again) [:unchanged? :created? :version])))
    (is (= before (:data (run p "stores" "check"))) "no new revision")
    (is (= 1 (count (:data (run p "models" "list")))))))

;; v1: store/test_cli_store.py::test_model_add_sets_correct_hash_b_for_zero_documents
;; Regression there: a model registered with no documents needed a follow-up update
;; to pass `stores check`. Here: right after define, check, verify and `models check`
;; pass with zero documents, and the contract is recorded.
(deftest a-model-with-zero-documents-passes-checks-immediately
  (let [p (with-model!)]
    (is (true? (:ok (run p "stores" "check"))))
    (is (true? (:ok (run p "stores" "verify"))))
    (let [c (run p "models" "check")]
      (is (true? (:ok c)) (pr-str c)))
    (is (= 4 (count (:fields (edn/read-string (slurp ticket)))))
        "fixture sanity")
    (is (= 4 (:fields (first (:data (run p "models" "list"))))) "the contract is recorded")))

;; v1: store/test_cli_store.py::test_model_add_passes_stores_check_immediately_with_no_documents
(deftest stores-check-passes-right-after-define
  (let [p (with-model!) e (run p "stores" "check")]
    (is (true? (:ok e)))
    (is (empty? (:warnings e)))))
