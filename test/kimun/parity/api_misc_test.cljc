(ns kimun.parity.api-misc-test
  "Ports of sldb v1 api/ tests about creating stores, editing derived models,
   resolving a store location and saving payloads. kimun has one surface (the
   CLI over `kimun.surface.*`), so each v1 `api.*` call is driven as its command."
  (:require [kimun.test-util :as ktu] [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [kimun.cli.main :as main]))

(def env {"USER" "tester"})
(def ticket (str (fs/absolutize "test/fixtures/models/ticket.edn")))

(defn- tmp [] (str (ktu/temp-dir "kimun-apimisc-")))
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- real [x] (str (fs/real-path x)))

(def party
  (pr-str {:model/name "Party" :model/version 1 :base [] :semantics ["party"]
           :template "# ⸢rev•title⸥\n"
           :fields [{:name "title" :type :string :required true :description "Name."}]}))
(def reservation
  (pr-str {:model/name "Reservation" :model/version 1 :base ["Party"] :semantics ["reservation"]
           :template "# ⸢rev•title⸥\n\nSize: ⸢rev•party_size⸥\n"
           :fields [{:name "title" :type :string :required true :description "Name."}
                    {:name "party_size" :type :int :required true :description "Size."}
                    {:name "extra" :type :string :required false :default "" :description "Extra."}]}))

(defn- family!
  "A project with the base model Party and its derived Reservation, plus the document res-1."
  []
  (let [p (tmp)]
    (run p "stores" "init" "--name" "p")
    (spit (str (fs/path p "party.edn")) party)
    (spit (str (fs/path p "res.edn")) reservation)
    (run p "models" "define" "-f" "party.edn")
    (run p "models" "define" "-f" "res.edn")
    (run p "docs" "create" "Reservation" "res-1" "--set" "title=Ana" "--set" "party_size=6")
    p))

(defn- model [p n] (:data (run p "models" "show" n)))

;; v1: api/test_api_creation.py::test_init_store_creates_an_empty_store_registered_in_the_global_catalog
;; The global-catalog half is a deliberate deviation (parity-v1.md); the empty store
;; at <project>/.kimun with no models and no links is the part kimun keeps.
(deftest init-creates-an-empty-store-in-the-project
  (let [p (tmp) e (run p "stores" "init" "--name" "project")]
    (is (true? (:ok e)) (pr-str e))
    (is (= (real (fs/path p ".kimun")) (real (get-in e [:data :path]))))
    (is (= [] (:data (run p "models" "list"))))
    (is (= [] (get-in (run p "stores" "check") [:data :links])))
    (is (= [] (get-in (run p "stores" "show") [:data :links])))))

;; v1: api/test_api_creation.py::test_init_store_refuses_an_existing_store_unless_forced
;; kimun has no `--force`; the refusal and that it keeps the store's links hold.
(deftest init-refuses-an-existing-store-and-keeps-its-links
  (let [p (tmp) other (str (fs/path p "other"))]
    (run p "stores" "init" "--name" "p")
    (fs/create-dirs other)
    (run other "stores" "init" "--name" "other")
    (is (true? (:ok (run p "stores" "link" "other" "other"))))
    (let [e (run p "stores" "init" "--name" "p")]
      (is (= "cli/store-exists" (get-in e [:error :type])))
      (is (str/includes? (get-in e [:error :message]) "already exists")))
    (is (= ["other"] (mapv :name (get-in (run p "stores" "show") [:data :links]))) "the link survives the refused init")))

;; v1: api/test_api_model_drafts.py::test_add_model_field_materializes_an_inherited_template
;; v1: a derived Python class inheriting its template gets its own template when a field is
;; added, the base file is never edited. kimun: the derived model carries its own template;
;; field-add extends only that model (new version), the base keeps its id and version.
(deftest field-add-on-a-derived-model-leaves-the-base-alone
  (let [p (family!) base-id (:id (model p "Party"))
        e (run p "models" "field-add" "Reservation" "--field" "zone" "--type" "string" "--default" "indoor")
        tpl (get-in (model p "Reservation") [:descriptor :template])]
    (is (true? (:ok e)) (pr-str e))
    (is (= 2 (get-in e [:data :version])))
    (is (str/includes? tpl "## Zone"))
    (is (str/includes? tpl "⸢optrev•zone⸥"))
    (is (< (str/index-of tpl "⸢rev•title⸥") (str/index-of tpl "⸢rev•party_size⸥") (str/index-of tpl "⸢optrev•zone⸥")) "keeps the order")
    (is (= [1 base-id] ((juxt :version :id) (model p "Party"))) "the base is never edited")
    (is (not (str/includes? (get-in (model p "Party") [:descriptor :template]) "zone")))
    (let [u (run p "docs" "update" "res-1" "--set" "zone=patio")]
      (is (true? (:ok u)) (pr-str u))
      (is (str/includes? (slurp (str (fs/path p "res-1.md"))) "## Zone\n\npatio"))
      (is (= "patio" (get-in (run p "docs" "show" "res-1") [:data :payload "zone"]))))))

;; v1: api/test_api_model_drafts.py::test_remove_model_field_does_not_touch_an_inherited_template
(deftest field-remove-on-a-derived-model-does-not-touch-the-base
  (let [p (family!) base-id (:id (model p "Party"))
        e (run p "models" "field-remove" "Reservation" "--field" "extra")]
    (is (true? (:ok e)) (pr-str e))
    (is (= 2 (get-in e [:data :version])))
    (is (= ["title" "party_size"] (mapv :name (get-in (model p "Reservation") [:descriptor :fields]))))
    (is (= base-id (:id (model p "Party"))) "only the derived model changed")
    (is (= ["Party"] (get-in (model p "Reservation") [:descriptor :base])))))

;; v1: api/test_api_stores.py::test_open_store_resolves_store_and_project_root
;; --store names the .kimun; the project root is its parent. A document created from another
;; directory is recorded relative to that root and resolves back to the file that was written.
(deftest an-explicit-store-resolves-its-project-root
  (let [p (tmp) elsewhere (tmp) store (str (fs/path p ".kimun"))]
    (run p "stores" "init" "--name" "p")
    (run p "models" "define" "-f" ticket)
    (let [shown (run elsewhere "stores" "show" "--store" store)
          c (run elsewhere "docs" "create" "TicketDoc" "t1" "--set" "title=A" "--set" "body=b" "--path" "t1.md" "--store" store)
          recorded (:path (first (:data (run p "docs" "list"))))]
      (is (= (real store) (real (get-in shown [:store :path]))))
      (is (true? (:ok c)) (pr-str c))
      (is (not (fs/absolute? recorded)) "stored relative to the project root")
      (is (= (real (fs/path elsewhere "t1.md")) (real (fs/path p recorded)))
          "the recorded path, taken from the project root, is the file written"))))

;; v1: api/test_api_stores.py::test_open_store_without_store_outside_any_project_raises
(deftest no-store-outside-any-project-says-to-init
  (let [e (run (tmp) "stores" "check")]
    (is (= [5 "cli/no-store"] [(:exit e) (get-in e [:error :type])]))
    (is (str/includes? (get-in e [:error :message]) "stores init"))))
