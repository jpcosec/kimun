(ns kimun.cli.find-generic-test
  "`find` and `sections` over documents without a model (generic class
   `generic/md`, K3): their sections and the text of their sections are
   searchable, they coexist with typed documents, the cached index carries
   them, and a file that cannot be read is a warning, not a failure."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.surface.index.build :as build]
            [kimun.surface.index.cache :as cache]
            [kimun.surface.markdown.ast :as ast]
            [kimun.test-util :as ktu]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (binding [ast/*parser* "native"] (:envelope (main/run argv env cwd))))
(defn- file [p & parts] (str (apply fs/path p parts)))

(def notes-text "# Notas\n\nAlgo sobre el cache derivado.\n\n## Otra\n\nMas texto aqui.\n")

(defn- project
  "The reproduction of the report: `notes.md` ingested as the generic `notes`."
  []
  (let [p (str (ktu/temp-dir "kimun-find-generic-"))]
    (is (true? (:ok (run p "stores" "init" "--name" "t"))))
    (spit (file p "notes.md") notes-text)
    (is (true? (:ok (run p "docs" "ingest" "notes.md" "--name" "notes"))))
    p))

(def note-model
  (pr-str {:model/name "Note" :model/version 1 :base [] :semantics {"type" "note"}
           :template "# ⸢rev•title⸥\n\n⸢rev•body⸥\n"
           :fields [{:name "title" :type :string :required true :default "" :description "t."}
                    {:name "body" :type :string :required false :default "" :description "b."}]}))

(defn- add-typed! [p]
  (spit (file p "m.edn") note-model)
  (is (true? (:ok (run p "models" "define" "-f" "m.edn"))))
  (is (true? (:ok (run p "docs" "create" "Note" "typed" "--set" "title=Typed" "--set" "body=the cache of a typed note")))))

(deftest the-reproduction-finds-the-text-of-a-generic-document
  (let [p (project)]
    (doseq [in [[] ["--in" "physical"] ["--in" "all"]]]
      (let [e (apply run p "find" "cache" in)]
        (is (true? (:ok e)) (pr-str in e))
        (is (= [{:kind :section :doc "notes" :section "notas" :path "notes.md#notas" :class "generic/md" :model nil
                 :matches [{:in :physical :value "Algo sobre el cache derivado." :line 3}]}]
               (mapv #(select-keys % [:kind :doc :section :path :class :model :matches]) (:data e)))
            (pr-str in))))
    (testing "the semantic layer has nothing to say about a document without tags"
      (is (= "find/empty" (get-in (run p "find" "cache" "--in" "semantic") [:error :type]))))))

(deftest a-paragraph-names-its-own-section-and-the-title-is-searchable
  (let [p (project)
        para (:data (run p "find" "Mas texto"))
        title (:data (run p "find" "Otra" "--type" "section"))]
    (is (= [["notas/otra" 7]] (mapv (juxt :section #(get-in % [:matches 0 :line])) para)))
    (is (= ["Otra"] (mapv :title title)))
    (is (= [{:in :physical :value "Otra"} {:in :semantic :value "Otra"}] (get-in (first title) [:matches]))
        "the heading is a title match, not repeated as text")
    (testing "the document itself is found by name and path"
      (is (= [["notes" "notes.md" "generic/md"]]
             (mapv (juxt :name :path :class) (:data (run p "find" "notes" "--type" "doc"))))))))

(deftest typed-and-generic-documents-live-in-one-store
  (let [p (project)]
    (add-typed! p)
    (is (= [["notes" nil "generic/md"] ["typed" "Note" nil]]
           (->> (:data (run p "find" "" "--type" "doc")) (mapv (juxt :name :model :class)) (sort-by first))))
    (is (= [["notas" :section "generic/md"] ["typed" :doc nil]]
           (sort-by first (map (juxt :name :kind :class) (:data (run p "find" "cache" "--type" "doc,section")))))
        "both hold the word, each under its own kind")
    (testing "what names a model does not see the generic document"
      (is (= ["typed"] (map :name (:data (run p "find" "st.{Note}"))))))
    (testing "a typed document keeps its fields"
      (is (= ["field"] (distinct (map (comp name :kind) (:data (run p "find" "cache" "--type" "field")))))))))

(deftest type-regex-fuzzy-limit-where-and-everything
  (let [p (project)]
    (is (= #{:store :doc :section} (set (map :kind (:data (run p "find" ""))))) "find \"\" lists the generic document too")
    (is (= ["notas" "otra"] (map :name (:data (run p "find" "" "--type" "section")))))
    (is (= ["notas"] (map :name (:data (run p "find" "" "--type" "section" "--limit" "1")))))
    (is (= ["notas"] (map :name (:data (run p "find" "ca.he d" "--regex" "--type" "section")))))
    (is (= ["notas"] (map :name (:data (run p "find" "cachee" "--fuzzy" "--type" "section")))))
    (is (= "find/empty" (get-in (run p "find" "cachee" "--type" "section") [:error :type])) "without --fuzzy a typo finds nothing")
    (is (= ["otra"] (map :name (:data (run p "find" "" "--type" "section" "--where" "\"Otra\" in about")))))
    (is (= [{:kind :section :class "generic/md"}]
           (:data (run p "find" "Mas texto" "--select" "kind,class"))))))

(deftest a-file-that-cannot-be-read-is-a-warning
  (let [p (project)]
    (fs/delete (file p "notes.md"))
    (fs/delete-tree (file p ".kimun" "cache"))
    (let [e (run p "find" "notes")]
      (is (true? (:ok e)) "the name still matches")
      (is (= ["notes"] (map :name (:data e))))
      (is (= [["find/unreadable-document" "notes" "notes.md"]]
             (mapv (juxt :type :document :path) (:warnings e)))))
    (let [e (run p "find" "cache")]
      (is (= "find/empty" (get-in e [:error :type])))
      (is (= ["notes"] (get-in e [:error :data :unreadable]))))
    (is (= "sections/unreadable" (get-in (run p "sections" "list" "notes") [:error :type])))))

(deftest the-cached-index-carries-the-generic-documents
  (let [p (project)
        r (run p "rebuild-indexes")
        path (file p ".kimun" (get-in r [:data :cache]))]
    (is (= 1 (get-in r [:data :generic-documents])))
    (let [idx (edn/read-string (slurp path))]
      (is (= build/version (:version idx)))
      (is (= [["notes" ["notas" "notas/otra"]]]
             (mapv (juxt :name #(mapv :path (:sections %))) (vals (:generics idx))))))
    (testing "find reads it: the file may be gone"
      (fs/delete (file p "notes.md"))
      (fs/delete-tree (file p ".kimun" "cache"))
      (is (= ["notas"] (map :name (:data (run p "find" "cache"))))))
    (testing "an index of the previous version is discarded and rebuilt"
      (let [old (-> (edn/read-string (slurp path)) (assoc :version 1) (dissoc :generics))
            _ (spit path (pr-str old))
            o (cli-store/open! (file p ".kimun"))
            l (cache/lookup! o)]
        (is (= [:built :stale] ((juxt :source :reason) l)))
        (is (= build/version (get-in l [:index :version])))
        (is (= ["notes"] (map :name (vals (get-in l [:index :generics])))))))))

(deftest sections-see-generic-documents-and-fields-says-why-not
  (let [p (project)
        l (run p "sections" "list" "notes")]
    (is (= [["Notas" 1] ["Otra" 2]] (mapv (juxt :title :level) (get-in l [:data :sections]))))
    (is (= [nil "generic/md"] [(get-in l [:data :model]) (get-in l [:data :class])]))
    (is (= [["Otra" "notes" "generic/md"]]
           (mapv (juxt :title :doc :class) (get-in (run p "sections" "find" "Otra") [:data :results]))))
    (let [e (run p "sections" "fields" "notes" "otra")]
      (is (= "cli/usage" (get-in e [:error :type])))
      (is (str/includes? (get-in e [:error :message]) "has no fields")))))
