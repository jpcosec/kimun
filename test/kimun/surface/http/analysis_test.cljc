(ns kimun.surface.http.analysis-test
  "The analysis routes of the HTTP surface over a real server: `/find`,
   `/edges*`, `POST /docs`, `/graph/*`, `/corpus/rank` and `/similar`, ported
   from v1 `tests/test_serve.py`, `tests/test_serve_edges_docs.py` and
   `tests/test_serve_corpus_similar.py`. Bodies are the CLI envelope (payload
   under `data`); the paths, parameters and statuses are v1's."
  (:require [babashka.fs :as fs]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.surface.http.support :refer [req run with-server]]))

(defn- model-edn
  "The EDN of model `n` with `fields` (names; `title` required), optional `semantics`, `references` and `template`."
  [n {:keys [semantics references template fields] :or {fields ["title"]}}]
  (pr-str (cond-> {:model/name n :model/version 1 :base [] :semantics (or semantics {})
                   :fields (mapv (fn [f] {:name f :type :string :required true :description (str f ".")}) fields)
                   :template (or template "# ⸢rev•title⸥\n")}
            references (assoc :py/references references))))

(defn- define!
  "Defines the model `n` (see `model-edn`) in project `p` through the CLI."
  [p n opts]
  (spit (str (fs/path p (str n ".edn"))) (model-edn n opts))
  (is (true? (:ok (run p "models" "define" "-f" (str n ".edn")))) n))

(defn- create!
  "Creates document `n` of `m` in `p` with `k=v` pairs (a map)."
  [p m n kvs]
  (is (true? (:ok (apply run p "docs" "create" m n (mapcat (fn [[k v]] ["--set" (str k "=" v)]) kvs)))) n))

(defn- chapter-store
  "v1 `_build_chapter_store`: documents `book` and `guide`, both mentioning markdown."
  [p]
  (define! p "ChapterDoc" {:semantics {"type" ["knowledge" "chapter"]} :fields ["title" "body"]
                           :template "# ⸢rev•title⸥\n\n⸢rev•body⸥\n"})
  (create! p "ChapterDoc" "book" {"title" "My Book" "body" "A markdown book."})
  (create! p "ChapterDoc" "guide" {"title" "Guide" "body" "A markdown guide."}))

(defn- edge-store
  "v1 `_edge_store`: NoteDoc n1 and ItemDoc i1 referencing it through `source`."
  [p]
  (define! p "NoteDoc" {:semantics {"type" ["knowledge" "note"]}})
  (define! p "ItemDoc" {:semantics {"type" ["knowledge" "item"]} :references ["source"] :fields ["title" "source"]
                        :template "# ⸢rev•title⸥\n\nfrom: ⸢rev•source⸥\n"})
  (create! p "NoteDoc" "n1" {"title" "N"})
  (create! p "ItemDoc" "i1" {"title" "I" "source" "NoteDoc:n1"}))

(defn- corpus-store
  "v1 `_corpus_store`: the edge store plus the notes `pan` and `deploy`."
  [p]
  (edge-store p)
  (create! p "NoteDoc" "pan" {"title" "Cocina del pan artesanal"})
  (create! p "NoteDoc" "deploy" {"title" "Deploy del indice de campos"}))

(defn- get-json [base path] (let [r (req :get (str base path))] [(:status r) (:json r)]))

(defn- doc-id [x] (str "sldb://document/" x))

(defn- err-type [body] (get-in body [:error :type]))

;; v1: test_find_physical_search
(deftest find-physical-search
  (with-server chapter-store
    (fn [base _]
      (let [[status body] (get-json base "/find?q=book&in=physical&type=doc")]
        (is (= 200 status))
        (is (true? (:ok body)))
        (is (some #{"book"} (map :name (:data body))))
        (is (every? #(= "doc" (:kind %)) (:data body)))))))

;; v1: test_find_semantic_search
(deftest find-semantic-search
  (with-server chapter-store
    (fn [base _]
      (let [[status body] (get-json base "/find?q=markdown&in=semantic&type=doc")
            names (set (map :name (:data body)))]
        (is (= 200 status))
        (is (set/subset? #{"book" "guide"} names))))))

;; v1: test_find_where_predicate_filters_and_invalid_where_is_400
(deftest find-where-predicate-filters-and-invalid-where-is-400
  (with-server chapter-store
    (fn [base _]
      (let [[status body] (get-json base "/find?q=book&in=physical&type=doc&where=has(title)")]
        (is (= 200 status))
        (is (= "book" (:name (first (:data body))))))
      (let [[status body] (get-json base "/find?q=book&in=physical&where=nonsense(missing")]
        (is (= 400 status) "the command exits 3 (query/bad-where); overridden to v1's 400")
        (is (false? (:ok body)))
        (is (= "query/bad-where" (err-type body)))))))

;; v1: test_find_limit_and_select
(deftest find-limit-and-select
  (with-server chapter-store
    (fn [base _]
      (let [[status body] (get-json base "/find?q=markdown&in=semantic&limit=2")]
        (is (= 200 status))
        (is (= 2 (count (:data body)))))
      (let [[status body] (get-json base "/find?q=book&in=physical&type=doc&select=kind,name,doc")]
        (is (= 200 status))
        (is (= #{:kind :name :doc} (set (keys (first (:data body))))))))))

;; v1: test_find_flags_and_param_validation
(deftest find-flags-and-param-validation
  (with-server chapter-store
    (fn [base _]
      (let [[status body] (get-json base "/find?q=books&in=physical&type=doc&fuzzy=1")]
        (is (= 200 status))
        (is (some #{"book"} (map :name (:data body))) "trigram Dice: `bok` of v1 is too short here, `books` matches `book`"))
      (is (= 404 (first (get-json base "/find?q=books&in=physical&type=doc"))) "without fuzzy nothing is found")
      (doseq [[path needle] [["/find?q=book&in=bogus" "--in"] ["/find?q=book&type=bogus" "--type"]
                             ["/find?q=book&limit=abc" "--limit"] ["/find" "Missing parameter: q"]]
              :let [[status body] (get-json base path)]]
        (is (= 400 status) path)
        (is (false? (:ok body)) path)
        (is (str/includes? (get-in body [:error :message]) needle) path)))))

;; v1: test_edges_endpoints_use_real_api
(deftest edges-endpoints-use-real-api
  (with-server edge-store
    (fn [base _]
      (let [[status body] (get-json base "/edges?from=NoteDoc:n1")]
        (is (= 200 status))
        (is (= #{"has_section" "tagged_as"} (set (map :relation (get-in body [:data :edges]))))))
      (let [[status body] (get-json base "/edges?to=NoteDoc:n1")]
        (is (= 200 status))
        (is (= #{"has_document" "references"} (set (map :relation (get-in body [:data :edges]))))))
      (let [[status body] (get-json base "/edges?from=NoteDoc:n1&relation=tagged_as")]
        (is (= 200 status))
        (is (= #{"tagged_as"} (set (map :relation (get-in body [:data :edges]))))))
      (let [[status body] (get-json base "/edges/node?id=NoteDoc:n1")]
        (is (= 200 status))
        (is (= "NoteDoc" (get-in body [:data :type]))))
      (let [[status body] (get-json base "/edges/nodes?type=sldb_model")]
        (is (= 200 status))
        (is (= #{["sldb://model/NoteDoc" "sldb_model"] ["sldb://model/ItemDoc" "sldb_model"]}
               (set (map (juxt :id :type) (get-in body [:data :nodes]))))))
      (let [[status body] (get-json base "/edges?from=")]
        (is (= 400 status))
        (is (false? (:ok body))))
      (let [[status body] (get-json base "/edges/node?id=nope")]
        (is (= 404 status))
        (is (str/includes? (get-in body [:error :message]) "nope"))))))

;; v1: test_edges_lists_document_edges_and_filters_by_id
(deftest edges-lists-document-edges-and-filters-by-id
  (with-server edge-store
    (fn [base _]
      (let [[status body] (get-json base "/edges")
            refs (filter #(= "references" (:relation %)) (get-in body [:data :edges]))]
        (is (= 200 status))
        (is (= [{:source (doc-id "ItemDoc:i1") :target (doc-id "NoteDoc:n1") :relation "references"}]
               (map #(select-keys % [:source :target :relation]) refs))))
      (let [[_ mine] (get-json base "/edges?id=NoteDoc:n1")
            [status other] (get-json base "/edges?id=NoteDoc:zzz")]
        (is (seq (get-in mine [:data :edges])))
        (is (every? #(= (doc-id "NoteDoc:n1") (:source %)) (get-in mine [:data :edges])))
        (is (= 200 status))
        (is (= [] (get-in other [:data :edges])))))))

;; v1: test_docs_creates_a_tracked_document
(deftest docs-creates-a-tracked-document
  (with-server edge-store
    (fn [base _]
      (let [r (req :post (str base "/docs") {:model "ItemDoc" :name "i2" :payload {:title "J" :source "NoteDoc:n1"}})]
        (is (= 200 (:status r)))
        (is (true? (get-in r [:json :ok])))
        (is (= "i2" (get-in r [:json :data :name])))
        (is (get-in r [:json :data :content-hash]) "v1 `version`; here the document's content-hash"))
      (let [[_ docs] (get-json base "/docs")]
        (is (some #{"i2"} (map :name (:data docs)))))
      (let [[_ edges] (get-json base "/edges?id=ItemDoc:i2&relation=references")]
        (is (= [(doc-id "NoteDoc:n1")] (map :target (get-in edges [:data :edges]))))))))

;; v1: test_docs_reports_each_failure_with_its_status
(deftest docs-reports-each-failure-with-its-status
  (with-server edge-store
    (fn [base _]
      (let [good {:model "NoteDoc" :name "n2" :payload {:title "M"}}]
        (doseq [[body code] [[{:model "NoteDoc"} 400] [(assoc good :model "Nope") 404]
                             [(assoc good :name "n1") 409] [(assoc good :payload {:wrong 1}) 422]]
                :let [r (req :post (str base "/docs") body)]]
          (is (= code (:status r)) (pr-str body))
          (is (false? (get-in r [:json :ok])))
          (is (seq (get-in r [:json :error :message]))))))))

;; v1: test_graph_endpoints_use_real_api
(deftest graph-endpoints-use-real-api
  (with-server edge-store
    (fn [base _]
      (let [[status body] (get-json base "/graph/cycles")]
        (is (= 200 status))
        (is (= [] (get-in body [:data :cycles]))))
      (let [[status body] (get-json base "/graph/components")]
        (is (= 200 status))
        (is (some #{(doc-id "NoteDoc:n1")} (first (get-in body [:data :components])))))
      (let [[status body] (get-json base "/graph/central?limit=5")
            items (get-in body [:data :nodes])]
        (is (= 200 status))
        (is (= 5 (count items)))
        (is (every? #(= [:id :score] (sort (keys %))) items) "v1 keys node/score; here id/score"))
      (let [[status body] (get-json base "/graph/isolated")]
        (is (= 200 status))
        (is (every? #(str/starts-with? % "sldb://relation_type/") (get-in body [:data :nodes]))
            "only the relation-type vocabulary is isolated"))
      (let [[status body] (get-json base "/graph/neighborhood?node=NoteDoc:n1")]
        (is (= 200 status))
        (is (some #{"sldb://section/NoteDoc:n1#n"} (get-in body [:data :nodes]))))
      (let [[status body] (get-json base "/graph/path?source=sldb://model/NoteDoc&target=sldb://section/NoteDoc:n1%23n")]
        (is (= 200 status))
        (is (= "sldb://model/NoteDoc" (first (get-in body [:data :path])))))
      (let [[status body] (get-json base "/graph/neighborhood")]
        (is (= 400 status))
        (is (false? (:ok body)))))))

;; v1: test_rank_without_index_is_an_explicit_409_not_an_empty_list
(deftest rank-without-index-is-an-explicit-409-not-an-empty-list
  (with-server corpus-store
    (fn [base _]
      (let [[status body] (get-json base "/corpus/rank?q=pan")]
        (is (= 409 status))
        (is (false? (:ok body)))
        (is (= "not_indexed" (get-in body [:error :data :kind])))
        (is (str/includes? (get-in body [:error :message]) "corpus no indexado")))
      (let [[status body] (get-json base "/corpus/rank?q=pan&fields=1")]
        (is (= 409 status))
        (is (= "not_indexed" (get-in body [:error :data :kind])))))))

;; v1: test_rank_refresh_builds_the_index_then_plain_calls_read_it
(deftest rank-refresh-builds-the-index-then-plain-calls-read-it
  (with-server corpus-store
    (fn [base _]
      (let [[status body] (get-json base "/corpus/rank?q=cocina+del+pan&refresh=1&k=2")
            hits (get-in body [:data :hits])
            top (first hits)]
        (is (= 200 status))
        (is (= 2 (count hits)))
        (is (= "pan" (:name top)))
        (is (= "NoteDoc" (:model top)))
        (is (str/ends-with? (str (:id top)) "NoteDoc:pan"))
        (is (str/starts-with? (get-in top [:payload :title]) "Cocina"))
        (is (>= (:score (first hits)) (:score (second hits)))))
      (let [[status again] (get-json base "/corpus/rank?q=cocina+del+pan&k=1")]
        (is (= 200 status))
        (is (= "pan" (:name (first (get-in again [:data :hits]))))))
      (let [[status none] (get-json base "/corpus/rank?q=cocina&threshold=2")]
        (is (= 200 status))
        (is (= [] (get-in none [:data :hits])))))))

;; v1: test_rank_among_and_fields
(deftest rank-among-and-fields
  (with-server corpus-store
    (fn [base _]
      (let [[_ body] (get-json base "/corpus/rank?q=pan&refresh=1&among=NoteDoc:deploy,NoteDoc:n1")]
        (is (every? #(contains? #{"deploy" "n1"} (:name %)) (get-in body [:data :hits]))))
      (let [[_ body] (get-json base "/corpus/rank?q=pan&fields=1&refresh=1&among=NoteDoc:pan&k=9")
            hits (get-in body [:data :hits])]
        (is (seq hits))
        (is (= #{"pan"} (set (map :name hits))))
        (is (every? #(and (:field %) (:text %) (:score %)) hits))))))

;; v1: test_rank_rejects_bad_parameters_with_400
(deftest rank-rejects-bad-parameters-with-400
  (with-server corpus-store
    (fn [base _]
      (let [[status body] (get-json base "/corpus/rank")]
        (is (= 400 status))
        (is (str/includes? (get-in body [:error :message]) "Missing parameter: q")))
      (let [[status body] (get-json base "/corpus/rank?q=x&k=abc")]
        (is (= 400 status))
        (is (false? (:ok body))))
      (is (= 400 (first (get-json base "/corpus/rank?q=x&threshold=zz")))))))

;; v1: test_similar_lists_nodes_sharing_targets
(deftest similar-lists-nodes-sharing-targets
  (with-server (fn [p] (edge-store p) (create! p "ItemDoc" "i2" {"title" "J" "source" "NoteDoc:n1"}))
    (fn [base _]
      (let [[status body] (get-json base "/similar?id=ItemDoc:i1&types=ItemDoc")]
        (is (= 200 status))
        (is (= [(doc-id "ItemDoc:i2")] (map :id (get-in body [:data :nodes])))
            "v1 [[id shared]] pairs are {:id :shared} maps here")
        (is (pos? (:shared (first (get-in body [:data :nodes]))))))
      (let [[_ limited] (get-json base "/similar?id=ItemDoc:i1&limit=1")]
        (is (= 1 (count (get-in limited [:data :nodes])))))
      (let [[_ by-node] (get-json base (str "/similar?id=" (doc-id "ItemDoc:i1") "&relations=references"))]
        (is (= [(doc-id "ItemDoc:i2")] (map :id (get-in by-node [:data :nodes]))))))))

;; v1: test_similar_reports_missing_and_unknown_id
(deftest similar-reports-missing-and-unknown-id
  (with-server edge-store
    (fn [base _]
      (let [[status body] (get-json base "/similar")]
        (is (= 400 status))
        (is (str/includes? (get-in body [:error :message]) "Missing parameter: id")))
      (let [[status body] (get-json base "/similar?id=NoteDoc:zzz")]
        (is (= 404 status))
        (is (false? (:ok body)))
        (is (str/includes? (get-in body [:error :message]) "NoteDoc:zzz")))
      (is (= 400 (first (get-json base "/similar?id=NoteDoc:n1&limit=0")))))))
