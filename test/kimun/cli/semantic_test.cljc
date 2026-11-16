(ns kimun.cli.semantic-test
  "The `semantic` group over a real store: v1 `tests/api/test_api_semantic.py`
   (parents, equivalences, guards, show, the CLI) and
   `tests/store/test_semantic_export.py`. The group is registered in
   `kimun.cli.main` at merge, so it is called through its `dispatch`."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.commands.semantic :as csem]
            [kimun.cli.errors :as errors]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.graph.export :as sexport]
            [kimun.surface.semantic :as sem]
            [kimun.host.default :as host]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- semantic [cwd sub & args]
  (errors/wrap #(csem/dispatch sub args {:format "json" :actor "human/tester"} {:env env :cwd cwd})))

(defn- model [n semantics]
  (pr-str {:model/name n :model/version 1 :base [] :semantics semantics
           :fields [{:name "title" :type :string :required true :description "Title."}]
           :template "# ⸢rev•title⸥\n"}))

(defn- world
  "v1's fixture `world`: Spec (type.knowledge.spec) with s1, Topology (layer.topology) with t1."
  []
  (let [p (str (ktu/temp-dir "kimun-semantic-"))]
    (run p "stores" "init" "--name" "p")
    (doseq [[n s] [["Spec" {"type" ["knowledge" "spec"]}] ["Topology" {"layer" ["topology"]}]]]
      (spit (str (fs/path p (str n ".edn"))) (model n s))
      (is (true? (:ok (run p "models" "define" "-f" (str n ".edn"))))))
    (is (true? (:ok (run p "docs" "create" "Spec" "s1" "--set" "title=S1"))))
    (is (true? (:ok (run p "docs" "create" "Topology" "t1" "--set" "title=T1"))))
    p))

(defn- opened [p] (:store (cli-store/open! (cli-store/resolve-store {} env p))))
(defn- docs-of [p tag] (get-in (semantic p "show" tag) [:data :documents]))

;; v1: test_a_declared_parent_is_read_by_the_closure_by_the_graph_and_by_the_journal
(deftest a-declared-parent-is-read-by-the-closure-and-the-kernel
  (let [p (world)]
    (is (= ["Spec:s1"] (docs-of p "type.knowledge")))
    (let [before (:head (opened p)) e (semantic p "parent-add" "layer.topology" "type.knowledge")]
      (is (true? (:ok e)))
      (is (true? (get-in e [:data :changed])))
      (is (not= before (:revision e)) "the write is a revision of the log")
      (is (= ["Spec:s1" "Topology:t1"] (docs-of p "type.knowledge")))
      (testing "it is a :semantic edge between two term nodes of the kernel, not a side file"
        (let [s (opened p) d (sem/declared s) [eid] (get-in d [:edges [:parent "layer.topology" "type.knowledge"]])
              edge (get-in s [:objects eid])]
          (is (= {"layer.topology" #{"type.knowledge"}} (:parents d)))
          (is (= :semantic (:type edge)))
          (is (= {:name "layer.topology" :lang "sldb/tag"} (get-in s [:objects (:from edge) :content])))
          (is (= "human/tester" (get-in edge [:evidence :actor])))
          (is (= {:name sem/parent-context} (get-in s [:objects (get-in edge [:evidence :context]) :content]))))))))

;; v1: test_declaring_it_twice_changes_nothing_and_journals_nothing
(deftest declaring-it-twice-changes-nothing
  (let [p (world) head (:revision (semantic p "parent-add" "layer.topology" "type.knowledge"))
        again (semantic p "parent-add" "layer.topology" "type.knowledge")]
    (is (false? (get-in again [:data :changed])))
    (is (= head (:revision again) (:head (opened p))) "no revision")))

;; v1: test_a_declared_parent_survives_the_rebuild_that_rederives_the_names
(deftest a-declared-parent-survives-new-documents
  (let [p (world)]
    (semantic p "parent-add" "layer.topology" "type.knowledge")
    (is (true? (:ok (run p "docs" "create" "Topology" "t2" "--set" "title=T2"))))
    (is (= ["layer" "type.knowledge"] (get-in (semantic p "show" "layer.topology") [:data :parents])))))

;; v1: test_the_guards_unknown_tags_cycles_and_parents_the_name_implies
(deftest the-guards
  (let [p (world) failed (fn [e] [(get-in e [:error :type]) (:exit e)])]
    (is (= ["semantic/missing" 1] (failed (semantic p "parent-add" "nope" "type"))))
    (is (= ["semantic/missing" 1] (failed (semantic p "parent-add" "layer.topology" "nope"))))
    (is (str/includes? (get-in (semantic p "parent-add" "nope" "type") [:error :message]) "Unknown semantic tag 'nope'"))
    (let [e (semantic p "parent-add" "type" "type.knowledge")]
      (is (= ["semantic/cycle" 3] (failed e)))
      (is (str/includes? (get-in e [:error :message]) "'type.knowledge' is already a kind of 'type'")))
    (is (str/includes? (get-in (semantic p "parent-add" "type" "type") [:error :message]) "kind of itself"))
    (let [e (semantic p "parent-remove" "type.knowledge" "type")]
      (is (= ["semantic/implied" 3] (failed e)))
      (is (str/includes? (get-in e [:error :message]) "gets from its own name")))
    (is (= 6 (:exit (semantic p "nope"))))))

;; v1: test_removing_a_declared_parent_takes_it_out_of_the_closure
(deftest removing-a-declared-parent-takes-it-out-of-the-closure
  (let [p (world)]
    (semantic p "parent-add" "layer.topology" "type.knowledge")
    (is (true? (get-in (semantic p "parent-remove" "layer.topology" "type.knowledge") [:data :changed])))
    (is (= ["Spec:s1"] (docs-of p "type.knowledge")))
    (is (false? (get-in (semantic p "parent-remove" "layer.topology" "type.knowledge") [:data :changed])))))

;; v1: test_an_equivalence_finally_has_a_way_in
(deftest an-equivalence-has-a-way-in
  (let [p (world)]
    (is (true? (get-in (semantic p "equivalent-add" "type.knowledge.spec" "global.spec") [:data :changed])))
    (is (false? (get-in (semantic p "equivalent-add" "type.knowledge.spec" "global.spec") [:data :changed])))
    (is (= ["global.spec"] (get-in (semantic p "show" "type.knowledge.spec") [:data :equivalents])))
    (is (= ["Spec:s1"] (docs-of p "type.knowledge.spec")))
    (testing "two local tags declared equivalent match each other"
      (semantic p "equivalent-add" "layer.topology" "type.knowledge.spec")
      (is (= ["Spec:s1" "Topology:t1"] (docs-of p "layer.topology") (docs-of p "type.knowledge.spec"))))))

;; v1: test_show_reports_both_directions
(deftest show-reports-both-directions
  (let [p (world)]
    (semantic p "parent-add" "layer.topology" "type.knowledge")
    (let [shown (:data (semantic p "show" "type.knowledge"))]
      (is (= ["type"] (:parents shown)))
      (is (some #{"layer.topology"} (:children shown)))
      (is (some #{"type.knowledge.spec"} (:below shown))))
    (is (= ["layer" "type" "type.knowledge"] (get-in (semantic p "show" "layer.topology") [:data :above])))))

;; v1: test_the_cli_writes_refuses_and_shows
(deftest the-cli-writes-refuses-shows-and-lists
  (let [p (world)]
    (is (true? (:ok (semantic p "parent-add" "layer.topology" "type.knowledge"))))
    (is (false? (:ok (semantic p "parent-add" "type" "type.knowledge"))))
    (is (some #{"type.knowledge"} (get-in (semantic p "show" "layer.topology") [:data :parents])))
    (is (= ["layer" "representation" "source" "type"] (get-in (semantic p "tags") [:data :tags])))
    (is (= ["layer.topology" "spec"] (get-in (semantic p "tags" "type.knowledge") [:data :tags])))
    (is (= ["Spec:s1" "Topology:t1"] (get-in (semantic p "tags" "type.knowledge") [:data :documents])))
    (is (= ["layer.topology" "representation.markdown" "source.document" "type.knowledge"] (get-in (semantic p "tags" "*.*") [:data :tags])))
    (is (= ["layer" "representation" "source" "type"] (get-in (semantic p "show") [:data :roots])))))

;; v1 test_semantic_export.py: test_stores_semantic_export_kgdb_json_from_tracked_store
(deftest export-is-the-v2-contract-with-relative-paths
  (let [p (world) _ (semantic p "parent-add" "layer.topology" "type.knowledge")
        payload (:data (semantic p "export"))]
    (is (= {"name" "sldb_kgdb_semantic_export" "version" 2} (dissoc (get payload "contract") "generated_at")))
    (is (= "kimun" (get-in payload ["producer" "name"])))
    (is (= ["semantic" "export"] (get-in payload ["producer" "command"])))
    (is (= {"root" "." "store_path" ".kimun"} (select-keys (get payload "store") ["root" "store_path"])))
    (is (= (:head (opened p)) (get-in payload ["store" "store_hash"])))
    (is (not (str/includes? (json/generate-string payload) p)) "nothing absolute leaves the store")
    (is (= [["Spec" ["representation.markdown" "source.document.markdown" "type.knowledge.spec"] []]
            ["Topology" ["layer.topology" "representation.markdown" "source.document.markdown"] []]]
           (mapv (juxt #(get % "name") #(get % "semantics") #(get % "base_models")) (get payload "models"))))
    (let [d (first (get payload "documents"))]
      (is (= ["Spec:s1" "s1" "Spec" "s1.md"] (mapv d ["id" "name" "model" "path"])))
      (is (= ["representation.markdown" "source.document.markdown" "type.knowledge.spec"] (get d "semantic_tags")))
      (is (and (string? (get d "source_hash")) (string? (get d "content_hash")))))
    (is (some #{{"id" "layer.topology" "parents" ["layer" "type.knowledge"]}} (get-in payload ["semantic_dag" "nodes"])))
    (testing "-o writes the file and the snapshot reader takes it"
      (let [e (errors/wrap #(csem/dispatch "export" [] {:format "json" :actor "human/tester" :output "out/export.json"} {:env env :cwd p}))
            written (json/parse-string (slurp (str (fs/path p "out" "export.json"))))]
        (is (= {:path "out/export.json" :models 2 :documents 2} (select-keys (:data e) [:path :models :documents])))
        (is (= (dissoc payload "contract") (dissoc written "contract")))
        (is (some #(= "sldb://document/Topology:t1" (get-in % ["identity" "node_id"])) (get (sexport/export->snapshot written) "nodes")))))))

;; v1 test_semantic_export.py: test_export_kgdb_semantic_payload_api
(deftest export-payload-api
  (let [p (world)
        payload (sexport/export (opened p) {:generated-at "2026-01-01T00:00:00.000Z" :version "0" :command ["test"] :store-path ".kimun"})]
    (is (= ["test"] (get-in payload ["producer" "command"])))
    (is (= "Spec:s1" (get-in payload ["documents" 0 "id"])))
    (is (= [] (get payload "sections")) "sections arrive with the section index (deferred)")))
