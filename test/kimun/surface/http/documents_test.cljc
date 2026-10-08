(ns kimun.surface.http.documents-test
  "Parity of the document routes of the HTTP surface with sldb v1's
   `test_serve.py` (checklist sections B and H): the document IR, the lifecycle
   (`/docs/create`, `/docs/track`, `/docs/untrack`) and the segment views
   (`/document/segments`, `/extract/segments`), against a real server over a
   project holding a one-field `SimpleBook` model. Bodies are the CLI envelope:
   the v1 payload sits under `:data`, a failure under `:error`."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.test-util :as ktu]
            [kimun.surface.http.support :refer [req run with-server]]))

(def ^:private book-edn
  "The model `SimpleBook`: one required title, rendered as a level-1 heading."
  (str "{:model/name \"SimpleBook\" :base [] :semantics [\"book\"]\n"
       " :fields [{:name \"title\" :type :string :required true :description \"Title.\"}]\n"
       " :template \"# ⸢rev•title⸥\\n\"}"))

(def ^:private moved-edn
  "A second version of `SimpleBook` whose template no longer explains the old
   documents (they become superseded and do not extract)."
  (str "{:model/name \"SimpleBook\" :base [] :semantics [\"book\"]\n"
       " :fields [{:name \"title\" :type :string :required true :description \"Title.\"}]\n"
       " :template \"Title: ⸢rev•title⸥\\n\"}"))

(defn- define!
  "Defines the model `edn` text in project `proj`."
  [proj edn]
  (let [f (str (fs/path proj "model.edn"))]
    (spit f edn)
    (run proj "models" "define" "-f" f)))

(defn- create!
  "Creates the document `name` of `SimpleBook` with `title` through the CLI."
  [proj name title]
  (let [f (str (fs/path proj "payload.json"))]
    (spit f (json/generate-string {:title title}))
    (run proj "docs" "create" "SimpleBook" name "--data" f)))

(defn- book-store
  "Setup: model `SimpleBook` and the tracked document `book` (\"My Book\")."
  [proj]
  (define! proj book-edn)
  (create! proj "book" "My Book"))

(defn- get-json
  "GET `path` on `base`; returns `[status body]`."
  [base path]
  (let [r (req :get (str base path))] [(:status r) (:json r)]))

(defn- post-json
  "POST the map `body` to `path` on `base`; returns `[status body]`."
  [base path body]
  (let [r (req :post (str base path) body)] [(:status r) (:json r)]))

(defn- doc-ids
  "The ids of the documents `/graph` lists."
  [base]
  (set (map :id (get-in (second (get-json base "/graph")) [:data :documents]))))

;; v1: test_document_endpoint_returns_full_ir
(deftest document-endpoint-returns-full-ir
  (with-server book-store
    (fn [base _]
      (let [[status body] (get-json base "/document?id=book")
            d (:data body)]
        (is (= 200 status))
        (is (:ok body))
        (is (= "book" (:id d)))
        (is (= "SimpleBook" (:model d)))
        (is (= "book.md" (:path d)))
        (is (= {:title "My Book"} (:payload d)))
        (is (= "document" (get-in d [:ir :type])))
        (is (= {:type "heading" :text "My Book"}
               (-> (first (get-in d [:ir :children])) (select-keys [:type :text]))))
        (is (= 1 (get-in d [:ir :children 0 :attrs :level])))))))

;; v1: test_document_ir_endpoint_returns_only_ir
(deftest document-ir-endpoint-returns-only-ir
  (with-server book-store
    (fn [base _]
      (let [[_ doc] (get-json base "/document?id=book")
            [status ir] (get-json base "/document/ir?id=book")]
        (is (= 200 status))
        (is (= (get-in doc [:data :ir]) (get-in ir [:data :ir])))
        (is (not (contains? (:data ir) :payload)))))))

;; v1: test_document_unknown_doc_returns_404
(deftest document-unknown-doc-returns-404
  (with-server book-store
    (fn [base _]
      (doseq [path ["/document?id=nope" "/document/ir?id=nope"]]
        (let [[status body] (get-json base path)]
          (is (= 404 status) path)
          (is (false? (:ok body)))
          (is (str/includes? (get-in body [:error :message]) "nope")))))))

;; v1: test_document_missing_id_returns_400
(deftest document-missing-id-returns-400
  (with-server book-store
    (fn [base _]
      (let [[status body] (get-json base "/document")]
        (is (= 400 status))
        (is (= "Missing parameter: id" (get-in body [:error :message])))))))

;; v1: test_document_unbuildable_ir_returns_422
;; A1 (2026-10-08): a moved contract keeps the document readable under the
;; contract it was written with, so the IR still builds; the 422 is for a
;; document with no recorded contract whose current model no longer reads it.
(deftest document-unbuildable-ir-returns-422
  (with-server (fn [proj]
                 (define! proj book-edn)
                 (create! proj "book" "My Book")
                 (ktu/forget-contract! (str (fs/path proj ".kimun")) "book"))
    (fn [base proj]
      (is (= 200 (first (get-json base "/document?id=book"))))
      ;; the model moves on: the stored text no longer extracts under its contract
      (define! proj moved-edn)
      (let [[status body] (get-json base "/document?id=book")]
        (is (= 422 status))
        (is (false? (:ok body)))
        (is (str/includes? (get-in body [:error :message]) "title"))))))

;; A1 (2026-10-08): the moved contract does not unbuild the IR
(deftest a-moved-contract-does-not-unbuild-the-ir
  (with-server book-store
    (fn [base proj]
      (is (= 200 (first (get-json base "/document?id=book"))))
      (define! proj moved-edn)
      (let [[status body] (get-json base "/document?id=book")]
        (is (= 200 status))
        (is (= {:title "My Book"} (get-in body [:data :payload])))))))

;; v1: test_docs_create_tracks_and_returns_version
(deftest docs-create-tracks-and-returns-version
  (with-server book-store
    (fn [base proj]
      (let [[status body] (post-json base "/docs/create" {:model "SimpleBook" :name "newbook" :payload {:title "New Book"}})
            d (:data body)
            graph-doc (->> (get-in (second (get-json base "/graph")) [:data :documents])
                           (filter #(= "newbook" (:id %))) first)]
        (is (= 200 status))
        (is (:ok body))
        (is (= "newbook" (:name d)))
        (is (= "SimpleBook" (:model d)))
        (is (= "newbook.md" (:path d)))
        (is (= {:title "New Book"} (:payload d)))
        (is (= (:content-hash d) (:version graph-doc)) "the returned version is the one /graph lists")
        (is (= "# New Book\n" (slurp (str (fs/path proj "newbook.md")))))
        (is (= #{"book" "newbook"} (doc-ids base)))))))

;; v1: test_docs_create_duplicate_conflict
(deftest docs-create-duplicate-conflict
  (with-server book-store
    (fn [base _]
      (let [[status body] (post-json base "/docs/create" {:model "SimpleBook" :name "book" :payload {:title "Nope"}})]
        (is (= 409 status))
        (is (false? (:ok body)))
        (is (= "docs/exists" (get-in body [:error :type])))
        (is (str/includes? (get-in body [:error :message]) "book"))))))

;; v1: test_docs_create_invalid_payload_422
(deftest docs-create-invalid-payload-422
  (with-server book-store
    (fn [base proj]
      (let [[status body] (post-json base "/docs/create" {:model "SimpleBook" :name "bad" :payload {:title 123}})]
        (is (= 422 status))
        (is (false? (:ok body)))
        (is (str/includes? (get-in body [:error :message]) "title"))
        (is (not (fs/exists? (fs/path proj "bad.md"))))))))

;; v1: test_docs_create_target_file_conflict
(deftest docs-create-target-file-conflict
  (with-server book-store
    (fn [base proj]
      (spit (str (fs/path proj "taken.md")) "# Taken\n")
      (let [[status body] (post-json base "/docs/create" {:model "SimpleBook" :name "taken" :payload {:title "Nope"}})]
        (is (= 409 status))
        (is (false? (:ok body)))
        (is (str/includes? (get-in body [:error :message]) "taken.md"))
        (is (= "# Taken\n" (slurp (str (fs/path proj "taken.md")))) "the file is untouched")))))

;; v1: test_docs_create_unregistered_model_404
(deftest docs-create-unregistered-model-404
  (with-server book-store
    (fn [base _]
      (let [[status body] (post-json base "/docs/create" {:model "Nope" :name "other" :payload {:title "X"}})]
        (is (= 404 status))
        (is (false? (:ok body)))
        (is (str/includes? (get-in body [:error :message]) "Nope"))))))

;; v1: test_docs_track_existing_file
(deftest docs-track-existing-file
  (with-server book-store
    (fn [base proj]
      (spit (str (fs/path proj "loose.md")) "# Loose Book\n")
      (let [[status body] (post-json base "/docs/track" {:model "SimpleBook" :path "loose.md"})
            graph-doc (->> (get-in (second (get-json base "/graph")) [:data :documents])
                           (filter #(= "loose" (:id %))) first)]
        (is (= 200 status))
        (is (:ok body))
        (is (= "loose" (get-in body [:data :name])))
        (is (= {:title "Loose Book"} (get-in body [:data :payload])))
        (is (= (get-in body [:data :content-hash]) (:version graph-doc)))
        (is (= #{"book" "loose"} (doc-ids base)))))))

;; v1: test_docs_track_missing_path_404
(deftest docs-track-missing-path-404
  (with-server book-store
    (fn [base _]
      (let [[status body] (post-json base "/docs/track" {:model "SimpleBook" :path "nope.md"})]
        (is (= 404 status))
        (is (false? (:ok body)))
        (is (str/includes? (get-in body [:error :message]) "nope.md"))))))

;; v1: test_docs_untrack_keeps_file_on_disk
(deftest docs-untrack-keeps-file-on-disk
  (with-server book-store
    (fn [base proj]
      (let [before (:content-hash (:data (second (get-json base "/document?id=book"))))
            [status body] (post-json base "/docs/untrack" {:name "book"})]
        (is (= 200 status))
        (is (:ok body))
        (is (= "book" (get-in body [:data :name])))
        (is (= "book.md" (get-in body [:data :path])))
        (is (= before (get-in body [:data :content-hash])))
        (is (= "# My Book\n" (slurp (str (fs/path proj "book.md")))))
        (is (not (contains? (doc-ids base) "book")))))))

;; v1: test_docs_untrack_missing_doc_404
(deftest docs-untrack-missing-doc-404
  (with-server book-store
    (fn [base _]
      (let [[status body] (post-json base "/docs/untrack" {:name "ghost"})]
        (is (= 404 status))
        (is (false? (:ok body)))
        (is (str/includes? (get-in body [:error :message]) "ghost"))))))

;; v1: test_document_segments_returns_literals_and_field_spans
(deftest document-segments-returns-literals-and-field-spans
  (with-server book-store
    (fn [base _]
      (let [[status body] (get-json base "/document/segments?id=book")
            d (:data body)
            field (second (:segments d))]
        (is (= 200 status))
        (is (= "book" (:doc d)))
        (is (= "SimpleBook" (:model d)))
        (is (= "book.md" (:path d)))
        (is (true? (:canonical d)))
        (is (= "# My Book" (:text d)))
        (is (= {:title "My Book"} (:payload d)))
        (is (= (:text d) (apply str (map :text (:segments d)))) "segments recompose the canonical text")
        (is (= ["literal" "field"] (map :type (:segments d))))
        (is (= "title" (:path field)))
        (is (= "rev" (:marker field)))
        (is (true? (:editable field)))
        (is (= "string" (:kind field)))
        (is (= "My Book" (:value field)))
        (is (= "My Book" (subs (:text d) (:start field) (:end field))))))))

;; v1: test_document_segments_unknown_doc_returns_404
(deftest document-segments-unknown-doc-returns-404
  (with-server book-store
    (fn [base _]
      (let [[status body] (get-json base "/document/segments?id=nope")]
        (is (= 404 status))
        (is (false? (:ok body)))
        (is (str/includes? (get-in body [:error :message]) "nope"))))))

;; v1: test_document_segments_missing_id_returns_400
(deftest document-segments-missing-id-returns-400
  (with-server book-store
    (fn [base _]
      (let [[status body] (get-json base "/document/segments")]
        (is (= 400 status))
        (is (= "Missing parameter: id" (get-in body [:error :message])))))))

;; v1: test_document_segments_unrenderable_doc_returns_422
(deftest document-segments-unrenderable-doc-returns-422
  (with-server book-store
    (fn [base proj]
      (is (= 200 (first (get-json base "/document/segments?id=book"))))
      ;; the model moves on: the stored text no longer extracts under it
      (define! proj moved-edn)
      (let [[status body] (get-json base "/document/segments?id=book")]
        (is (= 422 status))
        (is (false? (:ok body)))
        (is (str/includes? (get-in body [:error :message]) "title"))))))

;; v1: test_extract_segments_locates_fields_in_raw_markdown
(deftest extract-segments-locates-fields-in-raw-markdown
  (with-server book-store
    (fn [base _]
      (let [[status body] (post-json base "/extract/segments" {:model "SimpleBook" :markdown "# Some Title"})
            d (:data body)
            title (first (:fields d))]
        (is (= 200 status))
        (is (= "SimpleBook" (:model d)))
        (is (= "# Some Title" (:text d)))
        (is (= {:title "Some Title"} (:payload d)))
        (is (= "# Some Title" (:canonical_text d)))
        (is (= ["title"] (map :path (:fields d))))
        (is (= "exact" (:alignment title)))
        (is (true? (:editable title)))
        (is (= "Some Title" (:text title) (subs (:text d) (:start title) (:end title))))
        (is (= [] (:unmatched d)))
        (is (<= 0.0 (:coverage d) 1.0))))))

;; v1: test_extract_segments_reports_text_the_template_does_not_explain
(deftest extract-segments-reports-text-the-template-does-not-explain
  (with-server book-store
    (fn [base _]
      (let [[status body] (post-json base "/extract/segments" {:model "SimpleBook" :markdown "# T\n\nparrafo suelto"})
            d (:data body)]
        (is (= 200 status))
        (is (= ["parrafo suelto"] (map :text (:unmatched d))))
        (doseq [run (:unmatched d)]
          (is (= (:text run) (subs (:text d) (:start run) (:end run)))))))))

;; v1: test_extract_segments_rejects_a_malformed_body_with_400
(deftest extract-segments-rejects-a-malformed-body-with-400
  (with-server book-store
    (fn [base _]
      (doseq [body [{} {:model "SimpleBook"} {:markdown "# T"}
                    {:model 7 :markdown "# T"} {:model "" :markdown "# T"}
                    {:model "SimpleBook" :markdown nil}]]
        (let [[status resp] (post-json base "/extract/segments" body)]
          (is (= 400 status) (pr-str body))
          (is (false? (:ok resp)))
          (is (= "Expected {model: str, markdown: str}." (get-in resp [:error :message]))))))))

;; v1: test_extract_segments_distinguishes_unknown_model_from_bad_markdown
(deftest extract-segments-distinguishes-unknown-model-from-bad-markdown
  (with-server book-store
    (fn [base _]
      (testing "an unknown model is 404"
        (let [[status body] (post-json base "/extract/segments" {:model "NoSuchModel" :markdown "# T"})]
          (is (= 404 status))
          (is (= "models/missing" (get-in body [:error :type])))
          (is (str/includes? (get-in body [:error :message]) "NoSuchModel"))))
      (testing "markdown the model does not extract is 422, readable"
        (let [[status body] (post-json base "/extract/segments" {:model "SimpleBook" :markdown "sin heading, no extrae"})]
          (is (= 422 status))
          (is (= "SimpleBook" (get-in body [:error :data :model])))
          (is (not (str/includes? (get-in body [:error :message]) "Traceback"))))))))
