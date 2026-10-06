(ns kimun.cli.find-test
  "`find` over real stores, through `kimun.cli.commands.find/dispatch` (the
   group is registered in `kimun.cli.main` at merge). Ports the `find` tests
   of v1 `tests/test_cli_v2.py`, `tests/test_linked_structural.py`,
   `tests/store/test_where_empty_and_errors.py` (store halves) and the
   store-level functions lane B left from `tests/test_address_surface.py` and
   `tests/store/test_queries_expanded.py`."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.commands.find :as cfind]
            [kimun.cli.errors :as errors]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.surface.index.cache :as cache]))

(def env "Environment of the CLI runs." {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- ok! [cwd & argv] (let [e (apply run cwd argv)] (is (true? (:ok e)) (pr-str argv e)) e))
(defn- find* [cwd term & {:as opts}]
  (errors/wrap #(cfind/dispatch term [] (merge {:format "json" :actor "human/tester"} opts) {:env env :cwd cwd})))
(defn- names [e] (mapv :name (:data e)))
(defn- addresses [e] (mapv :address (:data e)))
(defn- failed [e] [(get-in e [:error :type]) (:exit e)])

(defn- model [n base semantics fields template]
  (pr-str {:model/name n :model/version 1 :base base :semantics semantics :template template
           :fields (mapv (fn [f] {:name f :type :string :required (= f "title") :default "" :description (str f ".")}) fields)}))

(defn- store!
  "A project in a fresh temp dir (or `dir`) with a store named `n`, the models `[file-name edn]` and the documents `[model name & k=v]`."
  [dir n models docs]
  (fs/create-dirs dir)
  (ok! dir "stores" "init" "--name" n)
  (doseq [[f m] models] (spit (str (fs/path dir f)) m) (ok! dir "models" "define" "-f" f))
  (doseq [[m d & sets] docs] (apply ok! dir "docs" "create" m d (mapcat (fn [s] ["--set" s]) sets)))
  (str dir))

(defn- tmp [] (str (fs/create-temp-dir {:prefix "kimun-find-"})))

(def roadmap-model "v1 `_setup_store`'s RoadmapDoc: sections Tasks and Semantic Tags, tag type.documentation.Readme."
  (model "RoadmapDoc" [] {"type" {"documentation" "Readme"}} ["title" "tasks" "notes"]
         "# ⸢rev•title⸥\n\n## Tasks\n\n⸢rev•tasks⸥\n\n## Semantic Tags\n\n⸢rev•notes⸥\n"))
(def base-model "BaseNote of v1 `test_address_surface`."
  (model "BaseNote" [] {"type" "note"} ["title" "status"] "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"))
(def memo-model "MemoDoc (base BaseNote, semantics type/note/memo)."
  (model "MemoDoc" ["BaseNote"] {"type" {"note" "memo"}} ["title" "status"] "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"))
(def note-model "NoteDoc of v1 `test_linked_structural`."
  (model "NoteDoc" [] {} ["title" "body"] "# ⸢rev•title⸥\n\n⸢rev•body⸥\n"))

(defn- roadmap-project []
  (store! (tmp) "p" [["roadmap.edn" roadmap-model]]
          [["RoadmapDoc" "roadmap" "title=Roadmap" "tasks=ship the index" "notes=type.documentation.Readme"]
           ["RoadmapDoc" "other" "title=Other" "tasks=nothing" "notes=none"]]))
(defn- memo-project []
  (store! (tmp) "p" [["base.edn" base-model] ["memo.edn" memo-model]]
          [["MemoDoc" "memo1" "title=Memo1" "status=open"] ["MemoDoc" "memo2" "title=Memo2" "status=closed"]]))
(defn- where-project []
  (store! (tmp) "p" [["base.edn" base-model] ["memo.edn" memo-model]]
          [["MemoDoc" "empty" "title=Empty"] ["MemoDoc" "open" "title=Open" "status=open"]]))

;; v1: test_cli_v2.py::test_find_supports_physical_and_semantic
(deftest find-supports-physical-and-semantic
  (let [p (roadmap-project)
        physical (find* p "roadmap" :in "physical" :type "doc")
        semantic (find* p "type.documentation.Readme" :in "semantic" :type "doc")]
    (is (true? (:ok physical)) (pr-str physical))
    (is (= "roadmap" (:doc (first (:data physical)))))
    (is (= #{:store :tree :name :model :path :kind :matches :doc :tags} (set (keys (first (:data physical))))))
    (is (some #{"roadmap"} (names semantic)))
    (is (every? #(= :semantic (:in %)) (mapcat :matches (:data semantic))))
    (testing "a tag is not found in the physical layer of another document, nor a name in the semantic one"
      (is (= ["find/empty" 1] (failed (find* p "other" :in "semantic" :type "doc")))))))

;; v1: test_cli_v2.py::test_find_section_where_predicates
;; (v1 test_cli_v2.py::test_sections_find_command exercises `sldb sections find`, another group: n/a here)
(deftest find-section-where-predicates
  (let [p (roadmap-project)
        titles (fn [term w] (mapv :title (:data (find* p term :type "section" :where w))))]
    (is (some #{"Tasks"} (titles "tasks" "\"Tasks\" in about")))
    (is (= "Semantic Tags" (first (titles "" "\"Semantic Tags\" in about"))))
    (is (= ["Roadmap" "Tasks" "Semantic Tags"] (titles "" "\"Roadmap\" in breadcrumbs")))
    (is (= ["Semantic Tags" "Semantic Tags"] (titles "" "title ~ \"^Sem\"")))
    (is (= ["Tasks"] (titles "" "path = \"roadmap/tasks\"")))
    (is (= 6 (count (titles "" "\"type.documentation.Readme\" in semantic_tags"))))
    (is (= ["find/empty" 1] (failed (find* p "" :type "section" :where "\"Nope\" in about"))))))

;; v1: test_cli_v2.py::test_find_field_shows_owning_section
(deftest find-field-shows-owning-section
  (let [p (roadmap-project) rs (:data (find* p "title" :type "field"))]
    (is (= "roadmap" (:owning-section (first rs))))
    (is (= [:field "title" "Roadmap" "roadmap.md:title"] ((juxt :kind :field :value :path) (first rs))))
    (is (= "roadmap/tasks" (:owning-section (first (:data (find* p "tasks" :type "field" :where "doc = \"roadmap\""))))))))

;; v1: test_linked_structural.py::test_a_linked_store_is_reached_by_prefix_and_answers_with_it
(deftest a-linked-store-is-reached-by-prefix-and-answers-with-it
  (let [root (tmp)
        a (store! (str (fs/path root "a")) "a" [["note.edn" note-model]] [["NoteDoc" "alpha" "title=alpha" "body=body of alpha"]])
        _ (store! (str (fs/path root "b")) "b" [["note.edn" note-model]]
                  [["NoteDoc" "beta" "title=beta" "body=body of beta"] ["NoteDoc" "gamma" "title=gamma" "body=body of gamma"]])]
    (ok! a "stores" "link" "b" "../b")
    (is (= ["alpha"] (names (find* a "st.{NoteDoc}"))))
    (is (= ["beta" "gamma"] (names (find* a "b:st.{NoteDoc}"))))
    (is (= ["b:st.{NoteDoc+}.gamma"] (addresses (find* a "b:st.{NoteDoc+}" :where "body ~ \"gamma\""))))
    (is (= ["find/empty" 1] (failed (find* a "st.{NoteDoc+}" :where "body ~ \"gamma\""))))
    (is (= ["body of beta"] (mapv :value (:data (find* a "b:st.{NoteDoc}.beta.body")))))
    (is (= ["body of alpha"] (mapv :value (:data (find* a "local:st.{NoteDoc}.alpha.body")))))
    (is (= ["b:st.{NoteDoc}.beta" "b:st.{NoteDoc}.gamma"] (addresses (find* a "b:st.{NoteDoc}.*"))))
    (is (= ["find/empty" 1] (failed (find* a "nope:st.{NoteDoc+}" :where "has(body)"))))
    (testing "--global: every result carries its store, ordered by store then tree"
      (let [rs (:data (find* a "body of" :in "physical" :type "doc" :global true))]
        (is (= [["a" "alpha"] ["b" "beta"] ["b" "gamma"]] (mapv (juxt :store :name) rs))))
      (is (= ["alpha"] (names (find* a "body of" :in "physical" :type "doc")))))
    (testing "reading wrote nothing"
      (is (not (fs/exists? (fs/path a ".kimun" "index")))))))

;; v1: store/test_where_empty_and_errors.py::test_find_empty_literal_matches_only_the_present_empty_document
(deftest find-empty-literal-matches-only-the-present-empty-document
  (is (= ["st.{MemoDoc}.empty"] (addresses (find* (where-project) "st.{MemoDoc}" :where "status = \"\"")))))

;; v1: store/test_where_empty_and_errors.py::test_find_matches_the_escaped_title_and_only_it
(deftest find-matches-the-escaped-title-and-only-it
  (let [p (where-project)]
    (spit (str (fs/path p "quoted.json")) "{\"title\": \"doesn't exist \\\" here\", \"status\": \"open\"}")
    (ok! p "docs" "create" "MemoDoc" "quoted" "--data" "quoted.json")
    (is (= ["st.{MemoDoc}.quoted"] (addresses (find* p "st.{MemoDoc}" :where "title = \"doesn't exist \\\" here\""))))))

;; v1: store/test_where_empty_and_errors.py::test_unparseable_predicate_raises_from_the_engine
;; v1: store/test_where_empty_and_errors.py::test_cli_find_prints_the_error_and_exits_non_zero
(deftest unparseable-predicate-is-bad-where-with-nothing-else-printed
  (let [p (where-project) e (atom nil)
        printed (with-out-str (reset! e (find* p "st.{MemoDoc}" :where "bogus predicate")))]
    (is (= ["query/bad-where" 3] (failed @e)))
    (is (str/includes? (get-in @e [:error :message]) "bogus predicate"))
    (is (= "" printed))
    (is (not (contains? @e :data)))
    (testing "the predicate is rejected before the term is looked at"
      (is (= ["query/bad-where" 3] (failed (find* p "no-such-term" :where "bogus predicate")))))))

;; v1: test_address_surface.py::test_find_filters_by_family_from_the_public_surface
(deftest find-filters-by-family-from-the-public-surface
  (let [e (find* (memo-project) "" :in "physical" :type "doc" :where "model <= BaseNote")]
    (is (true? (:ok e)))
    (is (= ["memo1" "memo2"] (names e)))))

;; v1: test_address_surface.py::test_field_update_is_visible_at_the_address (store half)
(deftest field-update-is-visible-at-the-address
  (let [p (memo-project) q #(addresses (find* p "st.{BaseNote+}" :where "status = \"open\""))]
    (is (= ["st.{BaseNote+}.memo1"] (q)))
    (ok! p "docs" "update" "memo2" "--set" "status=open")
    (is (= ["st.{BaseNote+}.memo1" "st.{BaseNote+}.memo2"] (q)))
    (is (str/includes? (slurp (str (fs/path p "memo2.md"))) "Status: open"))))

;; v1: test_address_surface.py::test_models_add_records_family_and_bases (v1 models_index `__family__` → the index)
(deftest models-record-family-and-bases
  (let [p (memo-project) idx (cache/load! (cli-store/open! (cli-store/resolve-store {} env p)))]
    (is (= ["BaseNote"] (get-in idx [:models "MemoDoc" :base])))
    (is (some #{"type.note.memo"} (get-in idx [:models "MemoDoc" :tags])))
    (is (= ["memo1" "memo2"] (names (find* p "type.note.memo" :in "semantic" :type "doc"))))))

;; v1: store/test_queries_expanded.py::test_model_inheritance_query (`st.{Base+}` on a real store with lineage)
(deftest model-inheritance-query
  (let [p (memo-project)]
    (ok! p "docs" "create" "BaseNote" "base1" "--set" "title=Base one")
    (is (= ["memo1" "memo2" "base1"] (names (find* p "st.{BaseNote+}"))))
    (is (= ["base1"] (names (find* p "st.{BaseNote}"))))
    (is (= ["memo1" "memo2"] (names (find* p "st.{MemoDoc+}"))))))

;; v1: store/test_queries_expanded.py::test_get_global_semantic_empty (`gse.` half)
;; v1: store/test_queries_expanded.py::test_find_structural_invalid_address (CLI: an empty list is find/empty)
(deftest global-semantic-empty-and-invalid-addresses
  (let [p (memo-project)]
    (is (= ["find/empty" 1] (failed (find* p "gse.topic.nothing"))))
    (is (= ["memo1" "memo2"] (names (find* p "gse.type.note"))))
    (is (= ["find/empty" 1] (failed (find* p "st.{Nope}"))))
    (is (= ["query/bad-address" 3] (failed (find* p "st.{MemoDoc"))))
    (is (= ["find/empty" 1] (failed (find* p "invalid.address"))))))

(deftest options-are-checked-and-applied
  (let [p (memo-project)]
    (doseq [[k v] [[:in "bogus"] [:limit "x"] [:limit "-1"] [:type "bogus"] [:select ","]]]
      (is (= ["cli/usage" 6] (failed (find* p "memo" k v))) (str k)))
    (is (= ["cli/usage" 6] (failed (find* p "memo" :regex true :fuzzy true))))
    (is (= ["cli/usage" 6] (failed (find* p nil))))
    (is (= ["query/bad-regex" 3] (failed (find* p "(" :regex true))))
    (is (= [{:store "p" :name "memo1"}] (:data (find* p "memo" :type "doc" :in "both" :limit "1" :select "store,name"))))
    (is (= ["memo2"] (names (find* p "^memo[2-9]$" :regex true :type "doc"))))
    (testing "--fuzzy: containment ignoring case, or Dice >= 0.7 against a value or one of its words"
      (is (= ["memo1" "memo2"] (names (find* p "MEMO" :fuzzy true :type "doc" :in "physical"))))
      (is (= ["memo1"] (names (find* p "memo11" :fuzzy true :type "doc" :in "physical"))))
      (is (= ["find/empty" 1] (failed (find* p "memo11" :type "doc"))))
      (is (= ["find/empty" 1] (failed (find* p "MEMO" :type "doc" :in "physical")))))))
