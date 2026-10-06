(ns kimun.cli.corpus-test
  "The `corpus` group over real stores: the ranking half of v1
   `tests/test_serve_corpus_similar.py` (`GET /corpus/rank`) and the field
   corpus of `tests/test_field_corpus.py` end to end. The group is registered in
   `kimun.cli.main` at merge, so it is called through its `dispatch`; the HTTP
   transport (status codes, JSON bodies, `/similar`) is deferred to the `serve`
   task D1."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.commands.corpus :as ccorpus]
            [kimun.cli.errors :as errors]
            [kimun.cli.main :as main]))

(def env
  "Environment of the CLI runs."
  {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- corpus [cwd sub args & {:as opts}]
  (errors/wrap #(ccorpus/dispatch sub args (merge {:format "json" :actor "human/tester"} opts) {:env env :cwd cwd})))

(def note
  "The NoteDoc model descriptor."
  (pr-str {:model/name "NoteDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}]
           :template "# ⸢rev•title⸥\n"}))

(defn- project
  "A project with a store, the NoteDoc model, and the `pan` and `deploy` notes of v1's `_corpus_store`."
  []
  (let [p (str (fs/create-temp-dir {:prefix "kimun-corpus-"}))]
    (run p "stores" "init" "--name" "p")
    (spit (str (fs/path p "note.edn")) note)
    (is (true? (:ok (run p "models" "define" "-f" "note.edn"))))
    (doseq [[n t] [["pan" "Cocina del pan artesanal"] ["deploy" "Deploy del indice de campos"]]]
      (is (true? (:ok (run p "docs" "create" "NoteDoc" n "--set" (str "title=" t))))))
    p))

;; v1: test_rank_without_index_is_an_explicit_409_not_an_empty_list
;; (the 409 transport is deferred to D1; the state is `index/not-indexed`, exit 5)
(deftest rank-without-index-is-explicit-not-an-empty-list
  (let [p (project) e (corpus p "rank" ["pan"])]
    (is (false? (:ok e)))
    (is (= "index/not-indexed" (get-in e [:error :type])))
    (is (= 5 (:exit e)))
    (is (str/includes? (get-in e [:error :message]) "corpus no indexado"))
    (is (= ["p"] (get-in e [:error :data :not-indexed])))
    (testing "--fields is the same state"
      (is (= "index/not-indexed" (get-in (corpus p "rank" ["pan"] :fields true) [:error :type]))))
    (testing "reading wrote nothing"
      (is (empty? (when (fs/exists? (fs/path p ".kimun" "index")) (fs/glob (fs/path p ".kimun" "index") "*.edn")))))))

;; v1: test_rank_refresh_builds_the_index_then_plain_calls_read_it
(deftest refresh-builds-the-index-then-plain-calls-read-it
  (let [p (project)
        e (corpus p "rank" ["cocina" "del" "pan"] :refresh true :k "2")
        top (first (get-in e [:data :hits]))]
    (is (true? (:ok e)) (pr-str e))
    (is (= 2 (count (get-in e [:data :hits]))))
    (is (= "NoteDoc:pan" (:id top)))
    (is (= "NoteDoc" (:model top)) "model and name travel with the hit")
    (is (= "pan" (:name top)))
    (is (str/starts-with? (get-in top [:payload "title"]) "Cocina"))
    (is (>= (:score top) (:score (second (get-in e [:data :hits])))))
    (let [again (corpus p "rank" ["cocina" "del" "pan"] :k "1")]
      (is (true? (:ok again)) "the index now exists: a plain call reads it")
      (is (= "NoteDoc:pan" (:id (first (get-in again [:data :hits]))))))
    (is (= [] (get-in (corpus p "rank" ["cocina"] :threshold "2") [:data :hits])) "no matches is an empty list, not an error")
    (testing "a new revision invalidates what a plain call may read"
      (run p "docs" "create" "NoteDoc" "extra" "--set" "title=Otra nota distinta")
      (is (= "index/not-indexed" (get-in (corpus p "rank" ["pan"]) [:error :type]))))
    (testing "`corpus refresh` rebuilds and reports counts"
      (let [r (corpus p "refresh" [])]
        (is (true? (:ok r)))
        (is (= [{:store "p" :documents 3 :fields 3}] (mapv #(select-keys % [:store :documents :fields]) (get-in r [:data :stores]))))
        (is (true? (:ok (corpus p "rank" ["pan"]))))))))

;; v1: test_rank_among_and_fields
(deftest rank-among-and-fields
  (let [p (project)
        a (corpus p "rank" ["pan"] :refresh true :among "NoteDoc:deploy,NoteDoc:n1")
        f (corpus p "rank" ["pan"] :fields true :refresh true :among "NoteDoc:pan" :k "9")]
    (is (every? #{"NoteDoc:deploy" "NoteDoc:n1"} (map :id (get-in a [:data :hits]))))
    (is (seq (get-in f [:data :hits])))
    (is (= #{"NoteDoc:pan"} (set (map :document-id (get-in f [:data :hits])))))
    (is (= #{:id :document-id :field :model :name :score :text :payload :tags :store}
           (set (map keyword (map name (keys (first (get-in f [:data :hits]))))))))
    (is (= "docs/NoteDoc/pan/title" (:id (first (get-in f [:data :hits])))))))

;; v1: test_rank_rejects_bad_parameters_with_400 (the 400 transport is deferred to D1; usage is exit 6)
(deftest rank-rejects-bad-parameters-as-usage
  (let [p (project)]
    (testing "missing q"
      (is (= ["cli/usage" 6] ((juxt #(get-in % [:error :type]) :exit) (corpus p "rank" [])))))
    (doseq [[k v] [[:k "abc"] [:k "0"] [:threshold "zz"]]]
      (let [e (corpus p "rank" ["x"] k v)]
        (is (= ["cli/usage" 6] [(get-in e [:error :type]) (:exit e)]) (str k v))
        (is (str/includes? (get-in e [:error :message]) (name k)))))
    (is (= "cli/usage" (get-in (corpus p "frobnicate" []) [:error :type])))))

;; v1: test_similar_lists_nodes_sharing_targets, test_similar_reports_missing_and_unknown_id
;; deferred to D1 (`/similar`: a graph query, not the corpus; asserts the
;; `similar` list `[[node, shared-count]]` filtered by `types`, `limit`, `relations`,
;; 400 on a missing id or `limit=0`, 404 on an unknown id).

;; v1: test_field_corpus end-to-end (entries, linked stores)
(deftest linked-stores-join-the-corpus-with-prefixed-ids
  (let [p (project)
        other (str (fs/create-temp-dir {:prefix "kimun-corpus-other-"}))]
    (run other "stores" "init" "--name" "other")
    (spit (str (fs/path other "note.edn")) note)
    (run other "models" "define" "-f" "note.edn")
    (run other "docs" "create" "NoteDoc" "pan" "--set" "title=Pan de masa madre del vecino")
    (is (true? (:ok (run p "stores" "link" "other" other))))
    (let [e (corpus p "rank" ["pan"] :refresh true :fields true :k "9")
          ids (set (map :id (get-in e [:data :hits])))]
      (is (true? (:ok e)) (pr-str e))
      (is (contains? ids "docs/NoteDoc/pan/title"))
      (is (contains? ids "other/docs/NoteDoc/pan/title")))
    (testing "a linked store with no index of its own is reported by name"
      (run other "docs" "create" "NoteDoc" "mas" "--set" "title=Una nota mas de otro")
      (let [e (corpus p "rank" ["pan"])]
        (is (= "index/not-indexed" (get-in e [:error :type])))
        (is (= ["other"] (get-in e [:error :data :not-indexed])))))))
