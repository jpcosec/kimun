(ns kimun.surface.index.corpus-test
  "Pure engine tests of `kimun.surface.index.corpus` over hand-built index maps
   (the contract of `kimun.surface.index.build`). Ports v1 `tests/test_field_corpus.py`
   and the ranking half of `tests/test_serve_corpus_similar.py`; see the comment
   above each test for the v1 function it carries."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.index.corpus :as corpus]
            [kimun.host.default :as host]))

(def uno
  "Payload of the first note."
  {"title" "Deploy del indice de campos"
          "body" "El cuerpo de la nota uno habla del deploy y del indice."})
(def dos
  "Payload of the second note."
  {"title" "Cocina del pan artesanal"
          "body" "El cuerpo de la nota dos habla de cocina y de pan."})

(defn- doc [model name payload & {:as extra}]
  (merge {:tree (str "t-" name) :name name :model model :bases [model] :payload payload
          :tags [] :field-hashes (tdocs/field-hashes host/host payload)}
         extra))

(defn- idx [& docs] {:head "r1" :docs (into {} (map (juxt :tree identity)) docs)})

(def world
  "An index of two notes with disjoint vocabularies."
  (idx (doc "Note" "uno" uno) (doc "Note" "dos" dos)))

(defn- bow
  "Bag-of-words score-fn: cosine over word counts (v1's deterministic `BagOfWordsEmbedder`)."
  [query text]
  (let [words (fn [s] (frequencies (str/split (str/lower-case s) #"\s+")))
        a (words query) b (words text) ks (distinct (concat (keys a) (keys b)))]
    (let [va (map #(get a % 0) ks) vb (map #(get b % 0) ks)
          dot (reduce + 0.0 (map * va vb))
          na (Math/sqrt (reduce + 0.0 (map #(* % %) va)))
          nb (Math/sqrt (reduce + 0.0 (map #(* % %) vb)))]
      (if (and (pos? na) (pos? nb)) (/ dot (* na nb)) 0.0))))

;; v1: test_projection_excludes_identity_fields_and_short_text
(deftest projection-excludes-identity-fields-and-short-text
  (let [e (fn [payload & [o]] (map :field (corpus/entries (idx (doc "Note" "x" payload)) (or o {}))))]
    (is (= ["body"] (e {"body" "texto suficientemente largo" "name" "identidad larga de campo" "tags" ["etiqueta larga uno"]})))
    (is (= [] (e {"body" "corto"})) "under 12 characters is noise")
    (is (= "uno\ndos" (corpus/field-text ["uno " " dos"])))
    (testing "v1 projection of lists, maps, numbers and blanks"
      (is (= "a b\nc" (corpus/field-text ["a b" 3 nil "  " "c"])))
      (is (= "" (corpus/field-text {"k" "valor largo de mapa"})))
      (is (= "" (corpus/field-text 12345678901234)))
      (is (= "" (corpus/field-text "   ")))
      (is (= "" (corpus/field-text nil))))
    (testing "options: include / exclude / min-chars"
      (is (= ["title"] (e uno {:include ["title"]})))
      (is (= ["title" "body"] (e uno {:exclude []})))
      (is (= ["title" "body"] (e uno {:min-chars 1}))))))

;; v1: test_entries_carry_the_fields_own_hash
(deftest entries-carry-the-fields-own-hash
  (let [es (corpus/entries world)
        by-id (into {} (map (juxt :id identity)) es)]
    (is (= #{"docs/Note/uno/body" "docs/Note/uno/title" "docs/Note/dos/body" "docs/Note/dos/title"} (set (keys by-id))))
    (let [e (by-id "docs/Note/uno/body")]
      (is (= "Note:uno" (:document-id e)))
      (is (= uno (:payload e)))
      (is (= (get (tdocs/field-hashes host/host uno) "body") (:hash e))))))

;; v1: test_store_without_field_hashes_still_yields_hashes
(deftest document-without-field-hashes-still-yields-hashes
  (let [bare (assoc (doc "Note" "uno" uno) :field-hashes {})
        e (first (filter #(= "docs/Note/uno/body" (:id %)) (corpus/entries (idx bare))))]
    (is (= (get (tdocs/field-hashes host/host uno) "body") (:hash e)))))

;; v1: test_refresh_reembeds_only_the_field_that_moved  -- kimun keeps no embedding
;; file (the index is derived per head), so the promise left is that an edit
;; moves the hash of the edited field only: that is what an embedder cache keys on.
(deftest an-edit-moves-only-the-hash-of-its-field
  (let [hashes (fn [i] (into {} (map (juxt :id :hash)) (corpus/entries i)))
        before (hashes world)
        after (hashes (idx (doc "Note" "uno" (assoc uno "body" "El cuerpo reescrito con otras palabras."))
                           (doc "Note" "dos" dos)))
        moved (filter #(not= (before %) (after %)) (keys before))]
    (is (= ["docs/Note/uno/body"] moved))
    (is (= 4 (count before) (count after)))))

;; v1: test_rank_fields_returns_the_document_a_hit_belongs_to
(deftest rank-returns-the-document-a-hit-belongs-to
  (let [c (corpus/entries world)
        hits (corpus/rank c "receta de cocina y pan" {:k 4 :score-fn bow})]
    (is (= "Note:dos" (:document-id (first hits))))
    (is (= dos (:payload (first hits))))
    (is (str/starts-with? (:id (first hits)) "docs/Note/dos/"))
    (is (= hits (sort-by (juxt (comp - :score) :id) hits)))
    (let [among (corpus/rank c "deploy del indice" {:k 4 :among ["Note:uno"] :score-fn bow})]
      (is (seq among))
      (is (= #{"Note:uno"} (set (map :document-id among)))))
    (testing "among also accepts a field id"
      (is (= ["docs/Note/uno/body"] (map :id (corpus/rank c "deploy" {:among ["docs/Note/uno/body"]})))))))

;; v1: test_audit_reports_drift_per_field_and_keeps_indexes_apart -- n/a: there is no
;; derived `fields.*.json` file to audit; the drift promise is carried by
;; `an-edit-moves-only-the-hash-of-its-field`, the separation by the index being a pure projection.

;; ---------------------------------------------------------------- serve ranking (engine level)

(def serve-world
  "The two notes of v1 `_corpus_store`."
  (idx (doc "NoteDoc" "pan" {"title" "Cocina del pan artesanal"})
       (doc "NoteDoc" "deploy" {"title" "Deploy del indice de campos"})))

;; v1: test_rank_refresh_builds_the_index_then_plain_calls_read_it (engine part; the refresh
;; HTTP semantics are the CLI test, the route is deferred to D1)
(deftest default-lexical-rank-orders-and-filters
  (let [c (corpus/document-entries serve-world)
        hits (corpus/rank c "cocina del pan" {:k 2})]
    (is (= 2 (count hits)))
    (is (= "NoteDoc:pan" (:id (first hits))))
    (is (= #{:id :model :name :store :text :payload :tags :score} (set (keys (first hits)))))
    (is (str/starts-with? (get-in (first hits) [:payload "title"]) "Cocina"))
    (is (>= (:score (first hits)) (:score (second hits))))
    (is (= "NoteDoc:pan" (:id (first (corpus/rank c "cocina del pan" {:k 1})))))
    (is (= [] (corpus/rank c "cocina" {:threshold 2})) "threshold above 1 keeps nothing")
    (is (= (corpus/rank c "cocina del pan" {}) (corpus/rank c "cocina del pan" {})) "deterministic")))

;; v1: test_rank_among_and_fields (engine part)
(deftest rank-among-and-fields
  (let [c (corpus/document-entries serve-world)
        hits (corpus/rank c "pan" {:among ["NoteDoc:deploy" "NoteDoc:n1"]})]
    (is (= #{"NoteDoc:deploy"} (set (map :id hits)))))
  (let [fs (corpus/rank (corpus/entries serve-world) "pan" {:among ["NoteDoc:pan"] :k 9})]
    (is (seq fs))
    (is (= #{"NoteDoc:pan"} (set (map :document-id fs))))
    (is (= #{:id :document-id :field :model :name :store :score :text :payload :tags} (set (keys (first fs)))))))

(deftest lexical-score-properties
  (is (= 1.0 (corpus/lexical-score "Pan" "cocina del PAN artesanal")) "a word hit scores 1.0, accents and case ignored")
  (is (= 1.0 (corpus/lexical-score "cocína" "cocina")))
  (is (= 0.0 (corpus/lexical-score "" "x")))
  (is (= 0.0 (corpus/lexical-score "x" "   ")))
  (is (< (corpus/lexical-score "zzzz" "cocina del pan") 0.1)))

(deftest ties-break-by-id
  (let [c [{:id "b" :text "pan"} {:id "a" :text "pan"} {:id "c" :text "xx"}]]
    (is (= ["a" "b" "c"] (map :id (corpus/rank c "pan" {}))))))

(deftest federated-view-prefixes-linked-stores
  (let [view [{:store "me" :index world} {:store "other" :index (idx (doc "Note" "uno" uno))}]
        ids (set (map :id (corpus/entries view)))]
    (is (contains? ids "docs/Note/uno/body"))
    (is (contains? ids "other/docs/Note/uno/body"))
    (is (contains? (set (map :document-id (corpus/entries view))) "other:Note:uno"))
    (is (= #{"other"} (set (keep :store (corpus/entries view {:stores ["other"]})))))
    (is (every? #(nil? (:store %)) (corpus/entries view {:stores ["local"]})))))

(deftest failed-extractions-and-model-filters
  (let [bad {:tree "t-bad" :name "bad" :model "Note" :error "no extract" :state :invalid}
        i (idx (doc "Note" "uno" uno) bad (doc "Other" "z" dos))]
    (is (= #{"Note:uno" "Other:z"} (set (map :document-id (corpus/entries i)))))
    (is (= #{"Note:uno"} (set (map :document-id (corpus/entries i {:models ["Note"]})))))
    (is (= #{"Note:uno"} (set (map :document-id (corpus/entries i {:exclude-models ["Other"]})))))))

(deftest dice-is-a-deterministic-grapheme-trigram-coefficient
  (let [h host/host]
    (is (= 1.0 (corpus/dice h "El cielo es azul." "El cielo es azul.")))
    (is (= 1.0 (corpus/dice h "" "")) "two empty texts are identical")
    (is (= 0.0 (corpus/dice h "" "abc")))
    (is (= 0.0 (corpus/dice h "abcdef" "uvwxyz")))
    (is (= (corpus/dice h "a b c" "a b d") (corpus/dice h "a b d" "a b c")) "symmetric")
    (is (< 0.6 (corpus/dice h "La hierba es verde." "La hierba es parda.")))
    (is (> 0.6 (corpus/dice h "La hierba es verde." "Otro asunto por completo.")))
    (testing "a text of fewer than three graphemes contributes the whole text as one gram"
      (is (= 1.0 (corpus/dice h "ab" "ab")))
      (is (= 0.0 (corpus/dice h "ab" "cd"))))
    (testing "grapheme clusters, not code units"
      (is (= 1.0 (corpus/dice h (str "e" "\u0301" "xyz") "\u00e9xyz")) "NFC-equal texts"))))
