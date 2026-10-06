(ns kimun.parity.models-io-test
  "Ports of sldb v1's store/test_models_io.py. v1 serialised the store, models,
   documents and sections indexes to YAML side files and tested save/load
   round trips. kimun keeps no index files: state is kernel objects, and every
   `kimun.cli.main/run` re-opens the store from disk. So each round trip becomes
   'what one invocation commits, a fresh one reads back unchanged'."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [kimun.cli.main :as main]))

(def env {"USER" "tester"})
(def ticket (str (fs/absolutize "test/fixtures/models/ticket.edn")))

(defn- tmp [] (str (fs/create-temp-dir {:prefix "kimun-modelsio-"})))
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- init! [] (let [p (tmp)] (run p "stores" "init" "--name" "p") p))

;; v1: store/test_models_io.py::test_store_index_roundtrip
(deftest store-state-round-trips-through-the-disk
  (let [p (init!)
        _ (run p "models" "define" "-f" ticket)
        shown (:data (run p "models" "show" "TicketDoc"))
        chk (:data (run p "stores" "check"))]
    (is (= shown (:data (run p "models" "show" "TicketDoc"))) "a second open reads the same model")
    (is (= chk (:data (run p "stores" "check"))) "and the same heads and counts")
    (is (= ["TicketDoc"] (mapv :name (:data (run p "models" "list")))))
    (is (true? (:ok (run p "stores" "verify"))))))

;; v1: store/test_models_io.py::test_store_index_empty_defaults
(deftest an-empty-store-reads-back-with-empty-defaults
  (let [p (init! )]
    (is (= [] (:data (run p "models" "list"))))
    (is (= [] (:data (run p "docs" "list"))))
    (is (= [] (get-in (run p "stores" "check") [:data :links])) "no links")
    (is (nil? (get-in (run p "stores" "check") [:data :head])) "no head yet")))

;; v1: store/test_models_io.py::test_models_index_roundtrip
(deftest a-model-entry-round-trips
  (let [p (init! ) defined (:data (run p "models" "define" "-f" ticket))
        entry (first (:data (run p "models" "list")))
        shown (:data (run p "models" "show" "TicketDoc"))]
    (is (= {:name "TicketDoc" :version 1 :id (:id defined) :fields 4 :documents 0}
           (select-keys entry [:name :version :id :fields :documents])))
    (is (= ["title" "status" "tags" "body"] (mapv #(get % :name) (get-in shown [:descriptor :fields]))))))

;; v1: store/test_models_io.py::test_documents_index_roundtrip
(deftest a-document-entry-round-trips
  (let [p (init! )
        _ (run p "models" "define" "-f" ticket)
        _ (run p "docs" "create" "TicketDoc" "login" "--set" "title=Login" "--set" "body=No entra." "--path" "content/login.md")
        entry (first (:data (run p "docs" "list")))]
    (is (= ["login" "TicketDoc" "content/login.md"] ((juxt :name :model :path) entry)))
    (is (= entry (first (:data (run p "docs" "list")))) "a fresh open lists the same entry")
    (is (= 1 (:documents (first (:data (run p "models" "list"))))))))

;; v1: store/test_models_io.py::test_load_store_index_missing_raises
(deftest opening-a-missing-store-is-a-typed-error
  (let [missing (str (fs/path (tmp) "nonexistent" ".kimun"))
        e (run (tmp) "stores" "check" "--store" missing)]
    (is (not (:ok e)))
    (is (= [5 "cli/no-store"] [(:exit e) (get-in e [:error :type])]))
    (is (str/includes? (get-in e [:error :message]) "nonexistent"))))

;; v1: store/test_models_io.py::test_save_creates_parent_dirs
;; v1: saving an index under missing directories creates them. kimun's writer
;; of user-visible files is `docs create --path`.
(deftest writing-a-document-creates-its-parent-directories
  (let [p (init! )
        _ (run p "models" "define" "-f" ticket)
        e (run p "docs" "create" "TicketDoc" "deep" "--set" "title=Deep" "--set" "body=b" "--path" "a/b/c/deep.md")]
    (is (true? (:ok e)) (pr-str e))
    (is (fs/exists? (fs/path p "a" "b" "c" "deep.md")))
    (is (= "a/b/c/deep.md" (:path (first (:data (run p "docs" "list"))))))))

;; v1: store/test_models_io.py::test_sections_index_roundtrip
(deftest a-sections-entry-round-trips
  (let [p (init! )
        _ (run p "models" "define" "-f" ticket)
        _ (run p "docs" "create" "TicketDoc" "login" "--set" "title=Login" "--set" "body=No entra.")
        e (run p "sections" "list" "login")
        titles (mapv :title (get-in e [:data :sections]))]
    (is (true? (:ok e)) (pr-str e))
    (is (= ["Login" "Tags" "Body"] titles))
    (is (= ["Login" "Body"] (:breadcrumbs (nth (get-in e [:data :sections]) 2))))
    (is (= (:data e) (:data (run p "sections" "list" "login"))) "a fresh open reads the same sections")))

;; v1: store/test_models_io.py::test_sections_index_empty_defaults
;; v1: loading a missing sections file yields no documents. kimun: a store with
;; no documents answers with an empty list, and an unknown document is typed.
(deftest sections-of-nothing-default-to-empty
  (let [p (init! )
        e (run p "sections" "list" "ghost")]
    (is (= [1 "docs/missing"] [(:exit e) (get-in e [:error :type])]))
    (is (= [] (:data (run p "docs" "list"))))))
