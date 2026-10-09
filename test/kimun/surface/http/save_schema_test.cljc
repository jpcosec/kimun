(ns kimun.surface.http.save-schema-test
  "Ports of sldb v1's `/schema`, `/graph` and `POST /save` server tests
   (tests/test_serve.py) over a real server: the status and the information v1
   asserted, in kimun's envelope (payload under `data`, failures as
   `{ok false error {type message data} exit}`)."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.surface.http.support :as sup :refer [req run with-server]]))

(def ^:private rich-edn
  "A model with semantics, a default, a list default and a template."
  (pr-str {:model/name "RichBook" :base [] :semantics ["book" "library"]
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "level" :type [:enum "low" "high"] :required false :default "low" :description "Level."}
                    {:name "tags" :type [:list :string] :required false :default [] :description "Tags."}]
           :template "# ⸢rev•title⸥\n"}))

(def ^:private plain-edn
  "A bare model: one required field, no semantics."
  (pr-str {:model/name "PlainBook" :base [] :semantics []
           :fields [{:name "title" :type :string :required true :description "Title."}]
           :template "# ⸢rev•title⸥\n"}))

(defn- define!
  "Defines the model whose EDN source is `edn` in `proj`."
  [proj fname edn]
  (let [f (str (fs/path proj fname))]
    (spit f edn)
    (run proj "models" "define" "-f" f)))

(defn- setup-plain
  "Store with the PlainBook model."
  [proj]
  (define! proj "plain.edn" plain-edn))

(defn- setup-ticket
  "Store with the ticket model."
  [proj]
  (run proj "models" "define" "-f" sup/ticket-edn))

(defn- ticket
  "A TicketDoc payload titled `title`."
  [title]
  {:title title :status "open" :tags [] :body "b"})

(defn- save!
  "POST /save of `body`."
  [base body]
  (req :post (str base "/save") body))

(defn- docs-of
  "The `documents` of GET /graph keyed by id."
  [base]
  (into {} (map (juxt :id identity)) (get-in (req :get (str base "/graph")) [:json :data :documents])))

(defn- create-change
  "A batch create change for PlainBook `id` titled `title`."
  [id title]
  {:id id :action "create" :model "PlainBook" :payload {:title title}})

;; v1: test_serve_endpoints_use_real_store
(deftest endpoints-use-real-store
  (with-server setup-ticket
    (fn [base proj]
      (save! base {:changes [{:id "t1" :action "create" :model "TicketDoc" :payload (ticket "My Book")}]})
      (let [schema (req :get (str base "/schema"))
            [m] (get-in schema [:json :data :models])]
        (is (= 200 (:status schema)))
        (is (= ["TicketDoc"] (map :id (get-in schema [:json :data :models]))))
        (is (= "TicketDoc" (:model_ref m)))
        (is (= [{:name "title" :kind "string" :required true :description "Ticket title."}
                {:name "status" :kind ["enum" "open" "closed"] :required true :description "Lifecycle state."}
                {:name "tags" :kind ["list" "string"] :required false :description "Free tags."}
                {:name "body" :kind "string" :required true :description "What happened."}]
               (map #(select-keys % [:name :kind :required :description]) (:fields m))))
        (is (= ["ticket"] (:semantics m)))
        (is (str/starts-with? (:template m) "# ⸢rev•title⸥")))
      (let [graph (req :get (str base "/graph"))
            d (get (docs-of base) "t1")]
        (is (= 200 (:status graph)))
        (is (= ["t1"] (keys (docs-of base))))
        (is (= {:id "t1" :model_name "TicketDoc" :path "t1.md" :payload (ticket "My Book")
                :semantic_tags ["representation.markdown" "source.document.markdown"]}
               (dissoc d :version)))
        (is (= (get-in (run proj "docs" "show" "t1") [:data :content-hash]) (:version d))
            "version is the content hash of the payload")
        (let [r (save! base {:doc "t1" :payload (ticket "Updated Title")})
              d2 (get (docs-of base) "t1")]
          (is (= 200 (:status r)))
          (is (true? (get-in r [:json :ok])))
          (is (= "t1" (get-in r [:json :data :doc])))
          (is (= (ticket "Updated Title") (:payload d2)))
          (is (not= (:version d) (:version d2)))
          (is (= (get-in (run proj "docs" "show" "t1") [:data :content-hash]) (:version d2)))
          (is (str/includes? (slurp (str (fs/path proj "t1.md"))) "# Updated Title"))))
      (let [r (save! base {:doc "missing" :payload (ticket "Nope")})]
        (is (= 404 (:status r)))
        (is (false? (get-in r [:json :ok])))
        (is (= "docs/missing" (get-in r [:json :error :type])))
        (is (= "missing" (get-in r [:json :error :data :document])))))))

;; v1: test_batch_save_create_update_delete
(deftest batch-save-create-update-delete
  (with-server setup-plain
    (fn [base proj]
      (is (= 200 (:status (save! base {:changes [(create-change "book" "My Book") (create-change "other" "Other Book")]}))))
      (let [r (save! base {:changes [{:id "book" :action "update" :model "PlainBook" :payload {:title "Batch Updated"}
                                      :expected {:title "My Book"}}
                                     {:id "other" :action "delete" :expected {:title "Other Book"}}
                                     (create-change "book2" "Batch New")]})]
        (is (= 200 (:status r)))
        ;; create applied before update before delete, regardless of request order
        (is (= {:ok true :saved ["book2" "book" "other"]}
               {:ok (get-in r [:json :ok]) :saved (get-in r [:json :data :saved])})))
      (let [docs (docs-of base)]
        (is (not (contains? docs "other")))
        (is (= {:title "Batch Updated"} (:payload (docs "book"))))
        (is (= "PlainBook" (:model_name (docs "book2"))))
        (is (str/includes? (slurp (str (fs/path proj "book2.md"))) "# Batch New"))
        ;; delete removes the file, not just the tracking
        (is (not (fs/exists? (fs/path proj "other.md"))))))))

;; v1: test_batch_save_stale_expected_conflict
(deftest batch-save-stale-expected-conflict
  (with-server setup-plain
    (fn [base _]
      (save! base {:changes [(create-change "book" "My Book")]})
      (let [r (save! base {:changes [{:id "book" :action "update" :model "PlainBook" :payload {:title "X"}
                                      :expected {:title "OLD"}}]})]
        (is (= 409 (:status r)))
        (is (false? (get-in r [:json :ok])))
        (is (str/includes? (get-in r [:json :error :message]) "book changed in kimun; reload to avoid overwriting it.")))
      (is (= {:title "My Book"} (:payload ((docs-of base) "book")))))))

;; v1: test_batch_save_duplicate_create_rejected
(deftest batch-save-duplicate-create-rejected
  (with-server setup-plain
    (fn [base proj]
      (save! base {:changes [(create-change "book" "My Book")]})
      (testing "existing document"
        (let [r (save! base {:changes [(create-change "book" "Nope")]})]
          (is (= 409 (:status r)))
          (is (false? (get-in r [:json :ok])))
          (is (= "docs/exists" (get-in r [:json :error :type])))
          (is (= "book" (get-in r [:json :error :data :document])))))
      (testing "target file already exists"
        (spit (str (fs/path proj "taken.md")) "# Taken\n")
        (let [r (save! base {:changes [(create-change "taken" "Nope")]})]
          (is (= 409 (:status r)))
          (is (false? (get-in r [:json :ok])))
          (is (= "taken.md" (get-in r [:json :error :data :path])))
          (is (str/includes? (get-in r [:json :error :message]) "taken.md already exists")))))))

;; v1: test_batch_save_invalid_payload_422
(deftest batch-save-invalid-payload-422
  (with-server setup-plain
    (fn [base proj]
      (let [r (save! base {:changes [{:id "bad" :action "create" :model "PlainBook" :payload {:title 123}}]})]
        (is (= 422 (:status r)))
        (is (false? (get-in r [:json :ok])))
        (is (= "validate/invalid" (get-in r [:json :error :type])))
        (is (= "title" (get-in r [:json :error :data :field])))
        (is (not (fs/exists? (fs/path proj "bad.md"))))))))

;; v1: test_batch_save_partial_failure_reports_completed
;; v1 monkeypatched the second create to raise; here the second create fails for real
;; because its target file already exists after the first one succeeded.
(deftest batch-save-partial-failure-reports-completed
  (with-server setup-plain
    (fn [base proj]
      (spit (str (fs/path proj "c2.md")) "# Taken\n")
      (let [r (save! base {:changes [(create-change "c1" "1") (create-change "c2" "2") (create-change "c3" "3")]})]
        (is (= 500 (:status r)))
        (is (false? (get-in r [:json :ok])))
        (is (= "serve/partial-failure" (get-in r [:json :error :type])))
        (is (= ["c1"] (get-in r [:json :error :data :completed])))
        (is (= "c2" (get-in r [:json :error :data :failed])))
        (is (str/includes? (slurp (str (fs/path proj "c1.md"))) "# 1"))
        (is (not (fs/exists? (fs/path proj "c3.md"))))))))

;; v1: test_batch_save_delete_removes_the_file_it_created
(deftest batch-save-delete-removes-the-file-it-created
  (with-server setup-plain
    (fn [base proj]
      (is (= 200 (:status (save! base {:changes [(create-change "zz" "Z")]}))))
      (is (fs/exists? (fs/path proj "zz.md")))
      (let [r (save! base {:changes [{:id "zz" :action "delete" :expected {:title "Z"}}]})]
        (is (= 200 (:status r)))
        (is (= ["zz"] (get-in r [:json :data :saved]))))
      (is (not (fs/exists? (fs/path proj "zz.md"))) "delete reported ok but left the file on disk")
      (is (not (contains? (docs-of base) "zz"))))))

;; v1: test_schema_includes_model_graph_dunders
;; kimun models have no containment/references: both are always empty (v1 asserted
;; {"chapters": [...]} / ["owner_id" ...]); semantics are a list, not a map of lists.
(deftest schema-includes-model-graph-dunders
  (with-server (fn [proj] (define! proj "rich.edn" rich-edn) (define! proj "plain.edn" plain-edn))
    (fn [base _]
      (let [r (req :get (str base "/schema"))
            models (into {} (map (juxt :id identity)) (get-in r [:json :data :models]))
            rich (models "RichBook")
            bare (models "PlainBook")
            fields (into {} (map (juxt :name identity)) (:fields rich))]
        (is (= 200 (:status r)))
        (is (= ["book" "library"] (:semantics rich)))
        (is (= "# ⸢rev•title⸥\n" (:template rich)))
        (is (= "low" (:default (fields "level"))))
        (is (= [] (:default (fields "tags"))))
        (is (nil? (:default (fields "title"))) "no declared default: nil (v1 omitted the key)")
        (is (= [] (:semantics bare)))
        (is (empty? (:containment rich)))
        (is (empty? (:references rich)))
        (is (empty? (:containment bare)))
        (is (empty? (:references bare)))))))

;; v1 test_schema_includes_model_graph_dunders, the half the port above left weak: a model
;; that DOES declare containment and references (what the exporter carries from a v1 class
;; as :py/containment and :py/references) shows them in /schema.
(deftest schema-carries-declared-containment-and-references
  (with-server (fn [proj]
                 (define! proj "linked.edn"
                          (pr-str {:model/name "LinkedBook" :base [] :semantics {"type" ["book"] "workspace" ["library"]}
                                   :fields [{:name "title" :type :string :required true :description "Title."}]
                                   :template "# ⸢rev•title⸥\n"
                                   :py/containment {"chapters" ["ChapterDoc"] "notes" ["NoteDoc"]}
                                   :py/references ["owner_id" "related"]})))
    (fn [base _]
      (let [r (req :get (str base "/schema"))
            m (first (filter #(= "LinkedBook" (:id %)) (get-in r [:json :data :models])))]
        (is (= 200 (:status r)))
        (is (= {:chapters ["ChapterDoc"] :notes ["NoteDoc"]} (:containment m)))
        (is (= ["owner_id" "related"] (:references m)))
        (is (= {:type ["book"] :workspace ["library"]} (:semantics m)))))))

;; n/a: test_schema_declares_models_that_cannot_load — kimun models are EDN descriptors
;; stored in the store, with no module to import: a registered model cannot fail to load
;; (v1 deleted/broke a Python module after registering it).
;; n/a: test_schema_degrades_per_model_instead_of_losing_the_response — same: needs a
;; Python model whose import breaks after registration; the `unavailable` list exists
;; in the route but no store state makes a model unbuildable.
;; n/a: test_graph_declares_models_whose_documents_are_missing — same reason (a
;; model that cannot be resolved so its documents drop out of /graph).

;; v1: test_schema_reports_no_unavailable_models_for_a_healthy_store
(deftest schema-reports-no-unavailable-models-for-a-healthy-store
  (with-server setup-plain
    (fn [base _]
      (let [r (req :get (str base "/schema"))]
        (is (= 200 (:status r)))
        (is (= [] (get-in r [:json :data :unavailable])))))))

;; v1: test_graph_reports_no_unavailable_models_for_a_healthy_store
(deftest graph-reports-no-unavailable-models-for-a-healthy-store
  (with-server setup-plain
    (fn [base _]
      (save! base {:changes [(create-change "book" "My Book")]})
      (let [r (req :get (str base "/graph"))]
        (is (= 200 (:status r)))
        (is (= [] (get-in r [:json :data :unavailable])))
        (is (= 1 (count (get-in r [:json :data :documents]))))))))
