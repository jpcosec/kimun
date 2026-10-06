(ns kimun.cli.graph-test
  "The `graph` group over a real store: v1 `tests/api/test_api_graph.py`,
   `tests/api/test_graph_source_parity.py`, the two CLI functions of
   `tests/api/test_api_graph_analysis.py` and the two `similar` functions of
   `tests/test_serve_corpus_similar.py`. The group is registered in
   `kimun.cli.main` at merge, so it is called through its `dispatch`."
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.commands.docs :as cdocs]
            [kimun.cli.commands.graph :as cg]
            [kimun.cli.errors :as errors]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.surface.edges :as edges]
            [kimun.surface.graph.io :as gio]
            [kimun.surface.graph.query :as q]
            [kimun.surface.graph.store :as gs]
            [sldb.host.default :as host]
            [sldb.kernel.ports :as ports]
            [sldb.kernel.store :as store]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- graph
  ([cwd sub args] (graph cwd sub args {}))
  ([cwd sub args opts]
   (errors/wrap #(cg/dispatch sub args (merge {:format "json" :actor "human/tester"} opts) {:env env :cwd cwd}))))
(defn- failure [e] [(get-in e [:error :type]) (:exit e)])
(defn- doc [x] (str "sldb://document/" x))

(defn- apply! [p build arg]
  (let [{:keys [backend] s :store} (cli-store/open! (cli-store/resolve-store {} env p))
        {:keys [changed plan]} (build host/host s arg {:actor "human/tester" :timestamp (cdocs/timestamp)
                                                       :tree-id (ports/ulid (:ids host/host))})]
    (when changed (store/commit! backend s plan))
    (boolean changed)))

(defn- store-with
  "A temp store with `models` (`[name semantics]`) and `docs` (`[model name]`)."
  [models docs]
  (let [p (str (fs/create-temp-dir {:prefix "kimun-graph-"}))]
    (run p "stores" "init" "--name" "p")
    (doseq [[n s] models]
      (spit (str (fs/path p (str n ".edn")))
            (pr-str {:model/name n :model/version 1 :base [] :semantics s
                     :fields [{:name "title" :type :string :required true :description "Title."}]
                     :template "# ⸢rev•title⸥\n"}))
      (is (true? (:ok (run p "models" "define" "-f" (str n ".edn"))))))
    (doseq [[m n] docs] (is (true? (:ok (run p "docs" "create" m n "--set" (str "title=" n))))))
    p))

(defn- analysis-world
  "v1 `AnalysisWorld`: s3 -> s2 -> s1 by an authored relation, `lonely` apart."
  []
  (let [p (store-with [["Spec" {"type" ["knowledge" "spec"]}] ["Note" {"type" ["knowledge" "note"]}]]
                      [["Spec" "s1"] ["Spec" "s2"] ["Spec" "s3"] ["Spec" "lonely"] ["Note" "n1"]])]
    (apply! p edges/define-relation-type-plan {:name "depends_on" :source-types ["Spec"] :target-types ["Spec"]})
    (apply! p edges/relate-plan {:from "Spec:s3" :to "Spec:s2" :relation "depends_on"})
    (apply! p edges/relate-plan {:from "Spec:s2" :to "Spec:s1" :relation "depends_on"})
    p))

(defn- graph-world
  "v1 `GraphWorld`: Table (t12, t14), Booking (b1), Move (no document), the
   equivalence type.restaurant.table = exo.table; `relations?` adds the
   authored vocabulary (assigned_to: b1 -> t12)."
  [relations?]
  (let [p (store-with [["Table" {"type" ["restaurant" "table"]}] ["Booking" {"type" ["restaurant" "booking"]}] ["Move" {"type" ["restaurant" "move"]}]]
                      [["Table" "t12"] ["Table" "t14"] ["Booking" "b1"]])]
    (is (true? (:ok (run p "semantic" "equivalent-add" "type.restaurant.table" "exo.table"))))
    (when relations?
      (apply! p edges/define-relation-type-plan {:name "assigned_to" :cardinality "many_to_one" :axis "WHERE"
                                                 :source-types ["Booking"] :target-types ["Table"]})
      (apply! p edges/relate-plan {:from "Booking:b1" :to "Table:t12" :relation "assigned_to"}))
    p))

(defn- old-route "The derived view (v1: composed from the edge shards)." [p]
  (gs/from-snapshot (:data (graph p "export" [] {:no-links true}))))
(defn- new-route "v1's source route: semantic export -> snapshot." [p]
  (is (true? (:ok (run p "semantic" "export" "-o" "export.json"))))
  (gs/from-snapshot (:data (graph p "ingest-sldb" ["export.json"]))))
(defn- ids [g] (set (keys (:nodes g))))
(defn- kind? [prefixes id] (boolean (some #(str/starts-with? id %) prefixes)))
(defn- triples
  ([g] (set (map (juxt :from :to :type) (:edges g))))
  ([g rels] (set (filter (comp rels last) (triples g)))))
(def structural-kinds ["sldb://store" "sldb://model/" "sldb://document/" "sldb://section/" "sldb://semantic_tag/"])
(def structural-relations #{"has_model" "has_document" "has_section" "tagged_as" "semantic_parent" "semantic_equivalent"})

;; ------------------------------------------------- tests/api/test_api_graph.py

;; v1: test_api_graph.py::test_executor_matches_kgdb_on_the_five_example_queries
;; (the kgdb oracle is not available here: the CLI over the export must answer what the engine does)
(deftest the-five-example-queries-run-over-a-semantic-export
  (let [p (str (fs/create-temp-dir {:prefix "kimun-graph-"}))
        export (slurp (io/resource "kimun/surface/graph/sldb_kgdb_semantic_export.v1.json"))
        g (gio/semantic-export->graph (gio/read-json export))]
    (spit (str (fs/path p "export.json")) export)
    (doseq [f ["documents_near_domain_workflow_task_tag" "documents_near_taskdoc_model" "documents_tagged_domain_contract"
               "sections_for_taskdoc_document" "semantic_neighborhood_integration_kgdb"]]
      (let [text (slurp (io/resource (str "kimun/surface/graph/queries/" f ".json")))]
        (spit (str (fs/path p "q.json")) text)
        (is (= (mapv :id (q/execute g (gio/read-json text)))
               (mapv :id (get-in (graph p "query" [] {:file "q.json" :snapshot "export.json"}) [:data :nodes]))) f)))))

(def task-query
  "{\"scope\": {\"descendant_of\": \"sldb://model/Spec\"}, \"filters\": [{\"facet\": \"identity\", \"conditions\": [{\"field\": \"node_type\", \"op\": \"eq\", \"value\": \"Spec\"}]}]}")

;; v1: test_api_graph.py::test_graph_reads_the_store_edge_index
(deftest graph-reads-the-derived-view-of-the-store
  (let [p (store-with [["Spec" {}]] [["Spec" "task"]])]
    (spit (str (fs/path p "q.json")) task-query)
    (is (some #{(doc "Spec:task")} (get-in (graph p "list" []) [:data :nodes])))
    (is (= "Spec" (get-in (graph p "get" [(doc "Spec:task")]) [:data :type])))
    (is (= [(doc "Spec:task")] (mapv :id (get-in (graph p "query" [] {:file "q.json"}) [:data :nodes]))))))

;; v1: test_api_graph.py::test_store_typed_graph_matches_kgdb
;; (kgdb's typed snapshot is replaced by the snapshot saved from the same store: same ids)
(deftest a-query-answers-the-same-from-the-store-and-from-its-snapshot
  (let [p (store-with [["Spec" {}]] [["Spec" "task"]])]
    (spit (str (fs/path p "q.json")) task-query)
    (graph p "snapshot" ["save" "g.json"])
    (is (= (:data (graph p "query" [] {:file "q.json"}))
           (:data (graph p "query" [] {:file "q.json" :snapshot "g.json"}))))))

;; v1: test_api_graph.py::test_snapshot_round_trip_and_cli
(deftest snapshot-round-trip-and-cli
  (let [p (store-with [["Spec" {}]] [["Spec" "task"]])
        saved (graph p "snapshot" ["save" "out/graph.json"])]
    (is (= "out/graph.json" (get-in saved [:data :path])))
    (is (contains? (ids (gs/from-snapshot (gio/read-json (slurp (str (fs/path p "out" "graph.json")))))) "sldb://model/Spec"))
    (is (= (dissoc (:data saved) :path) (dissoc (:data (graph p "snapshot" ["load" "out/graph.json"])) :path)))
    (is (= "sldb_model" (get-in (graph p "get" ["sldb://model/Spec"]) [:data :type])))
    (is (true? (:ok (graph p "list" []))))
    (is (true? (:ok (graph p "neighborhood" ["sldb://model/Spec"]))))))

;; ------------------------------------- tests/api/test_graph_source_parity.py

(deftest source-parity-of-a-clean-world
  (let [p (graph-world false) old (old-route p) new (new-route p)]
    (testing "test_the_five_structural_kinds_have_the_same_node_ids"
      (let [only-old (set/difference (ids old) (ids new))]
        (is (seq (filter #(kind? ["sldb://field/"] %) only-old)))
        (is (every? #(kind? ["sldb://field/" "sldb://relation_type/" "sldb://section/"] %) only-old)
            "G1 fields, kimun's builtin relation types, and sections: kimun's semantic export lists none")
        (is (empty? (set/difference (ids new) (ids old))))))
    (testing "test_every_document_section_and_tag_node_is_in_both_routes"
      (is (every? (ids new) (remove #(kind? ["sldb://section/"] %) (filter #(kind? structural-kinds %) (ids old))))
          "sections excepted: kimun's semantic export lists none, the derived view has them")
      (is (seq (filter #(kind? ["sldb://section/"] %) (ids old)))))
    (testing "test_a_model_without_documents_is_present_in_both"
      (is (contains? (ids old) "sldb://model/Move"))
      (is (contains? (ids new) "sldb://model/Move"))
      (is (not-any? #(= ["sldb://model/Move" "has_document"] [(:from %) (:type %)]) (:edges new))))
    (testing "test_document_section_and_tag_semantics_are_identical"
      (doseq [id (sort (filter #(kind? ["sldb://document/" "sldb://section/" "sldb://semantic_tag/"] %) (ids old)))
              :let [o (get-in old [:nodes id :semantics]) n (get-in new [:nodes id :semantics])]]
        (is (= (select-keys o (keys n)) (select-keys n (keys o))) id)))
    (testing "test_store_and_model_nodes_carry_the_old_semantics_plus_documented_extras"
      (doseq [id (sort (filter #(kind? ["sldb://store" "sldb://model/"] %) (ids old)))
              :let [o (get-in old [:nodes id :semantics]) n (get-in new [:nodes id :semantics])]]
        (is (= (select-keys o (keys n)) (select-keys n (keys o))) id)))
    (testing "test_the_six_structural_edges_are_identical"
      (let [o (triples old structural-relations) n (triples new structural-relations)]
        (is (set/subset? n o))
        (is (seq n))
        (is (every? (fn [[from to _]] (some #(kind? ["sldb://section/"] %) [from to])) (set/difference o n))
            "the only structural edges the export route lacks touch a section")))
    (testing "test_structure_edge_metadata_difference_is_declared"
      (let [meta-of (fn [g] (into {} (map (juxt (juxt :from :to :type) :metadata)) (:edges g)))
            o (meta-of old) n (meta-of new) shared (filter n (keys o))]
        (is (seq shared))
        (is (every? #(contains? #{"structural" "alias"} (get-in o [% "origin"])) shared))
        (is (every? #(= {} (n %)) shared))))
    (testing "test_snapshot_save_generates_from_the_source_indexes (kimun saves the derived view, fields included)"
      (graph p "snapshot" ["save" "graph.json"] {:no-links true})
      (let [loaded (gs/from-snapshot (gio/read-json (slurp (str (fs/path p "graph.json")))))]
        (is (= old loaded))
        (is (some #(kind? ["sldb://field/"] %) (ids loaded)))
        (is (set/subset? (triples new structural-relations) (triples loaded)))))
    (testing "test_snapshot_save_keeps_the_legacy_route_for_exclude_tags_gap_G6"
      (let [g (gs/from-snapshot (:data (graph p "export" [] {:exclude-tag ["type.restaurant.table"]})))]
        (is (not-any? #(kind? ["sldb://document/Table:"] %) (ids g)))
        (is (contains? (ids g) (doc "Booking:b1")))))))

(deftest source-parity-declared-gaps
  (let [p (graph-world true) old (old-route p) new (new-route p)]
    (testing "test_gap_G1_field_nodes_and_edges_have_no_source_producer"
      (is (seq (filter #(kind? ["sldb://field/"] %) (ids old))))
      (is (empty? (filter #(kind? ["sldb://field/"] %) (ids new))))
      (is (seq (triples old #{"has_field" "extends"})))
      (is (empty? (triples new #{"has_field" "extends"}))))
    (testing "test_gap_G2_typed_contribution_is_old_only_and_the_docs_appear_raw_in_new"
      (is (contains? (ids old) "sldb://relation_type/assigned_to"))
      (is (empty? (filter #(kind? ["sldb://relation_type/" "sldb://anchor/"] %) (ids new))))
      (is (seq (triples old #{"assigned_to" "applies_to_source" "applies_to_target"})))
      (is (empty? (triples new #{"assigned_to" "applies_to_source" "applies_to_target"})))
      (is (set/subset? (triples new structural-relations) (triples old structural-relations))))
    (testing "test_gap_G3_authored_edge_resolution_only_exists_in_the_old_route"
      (let [authored (filter #(= "assigned_to" (:type %)) (:edges old))]
        (is (= [[(doc "Booking:b1") (doc "Table:t12")]] (mapv (juxt :from :to) authored)))
        (is (every? #(contains? #{"authored" "relation_doc"} (get-in % [:metadata "origin"])) authored))
        (is (not-any? #(contains? #{"authored" "relation_doc"} (get-in % [:metadata "origin"])) (:edges new)))))))

;; ------------------------------------ tests/api/test_api_graph_analysis.py

;; v1: test_api_graph_analysis.py::test_the_cli_prints_each_analysis
(deftest the-cli-prints-each-analysis
  (let [p (analysis-world) s1 (doc "Spec:s1")]
    (doseq [[sub args opts] [["cycles" []] ["order" []] ["components" []] ["central" [] {:kind "degree"}]
                             ["similar" [s1]] ["path" [(doc "Spec:s3") s1]]]
            :let [e (graph p sub args (or opts {}))]]
      (is (true? (:ok e)) sub)
      (when-not (= "cycles" sub) (is (str/includes? (pr-str (:data e)) s1) sub)))))

;; v1: test_api_graph_analysis.py::test_the_cli_says_so_when_there_is_no_path_and_exits_nonzero
(deftest no-path-says-so-and-exits-nonzero
  (let [p (analysis-world) e (graph p "path" ["Spec:s1" "Spec:lonely"])]
    (is (= ["graph/empty" 1] (failure e)))
    (is (str/includes? (get-in e [:error :message]) "No path"))))

;; -------------------------------------- tests/test_serve_corpus_similar.py

;; v1: test_serve_corpus_similar.py::test_similar_lists_nodes_sharing_targets
(deftest similar-lists-nodes-sharing-targets
  (let [p (analysis-world)]
    (is (= [{:id (doc "Note:n1") :shared 2}] (get-in (graph p "similar" ["Spec:s1"] {:type "Note"}) [:data :nodes])))
    (is (= 1 (count (get-in (graph p "similar" ["Spec:s1"] {:limit "1"}) [:data :nodes]))))
    (is (= [] (get-in (graph p "similar" [(doc "Spec:s1")] {:relation ["references"]}) [:data :nodes])))))

;; v1: test_serve_corpus_similar.py::test_similar_reports_missing_and_unknown_id
(deftest similar-reports-missing-and-unknown-id
  (let [p (analysis-world)]
    (is (= ["cli/usage" 6] (failure (graph p "similar" []))))
    (let [e (graph p "similar" ["Spec:zzz"])]
      (is (= ["graph/missing" 1] (failure e)))
      (is (str/includes? (get-in e [:error :message]) "Spec:zzz")))
    (is (= ["cli/usage" 6] (failure (graph p "similar" ["Spec:s1"] {:limit "0"}))))))

;; ---------------------------------------------------------- the group itself

(deftest every-subcommand-answers-the-same-from-store-and-snapshot
  (let [p (analysis-world) s1 (doc "Spec:s1") s2 (doc "Spec:s2") s3 (doc "Spec:s3") lonely (doc "Spec:lonely")]
    (spit (str (fs/path p "q.json")) task-query)
    (is (true? (:ok (graph p "snapshot" ["save" "g.json"]))))
    (doseq [[sub args opts] [["get" ["Spec:s2"]] ["list" []] ["list" [] {:type "Spec"}] ["query" [] {:file "q.json"}]
                             ["neighborhood" ["Spec:s2"] {:depth "2" :direction "outgoing" :relation ["depends_on"]}]
                             ["path" ["Spec:s3" "Spec:s1"]] ["cycles" []] ["order" []] ["components" [] {:type "Spec"}]
                             ["central" [] {:metric "degree" :limit "2"}] ["isolated" [] {:type "Spec"}]
                             ["similar" ["Spec:s1"] {:limit "3"}] ["export" []]]
            :let [live (graph p sub args (or opts {})) snap (graph p sub args (assoc opts :snapshot "g.json"))]]
      (is (true? (:ok live)) sub)
      (is (= (:data live) (:data snap)) sub)
      (is (nil? (:store snap)) "a snapshot read opens no store"))
    (is (= [s2] (keep #(when (= "depends_on" (:type %)) (:to %)) (get-in (graph p "get" ["Spec:s3"]) [:data :edges]))))
    (is (= [s1 s2] (get-in (graph p "neighborhood" ["Spec:s2"] {:direction "outgoing" :relation ["depends_on"]}) [:data :nodes])))
    (is (= {:from s3 :to s1 :length 2 :path [s3 s2 s1]} (:data (graph p "path" ["Spec:s3" "Spec:s1"]))))
    (is (= ["graph/empty" 1] (failure (graph p "path" ["Spec:s1" "Spec:s3"] {:directed true}))))
    (is (= {:count 0 :cycles []} (:data (graph p "cycles" []))))
    (is (< (.indexOf (get-in (graph p "order" []) [:data :order]) s1) (.indexOf (get-in (graph p "order" []) [:data :order]) s3)))
    (is (= [[s1 s2 s3] [lonely]] (get-in (graph p "components" [] {:type "Spec"}) [:data :components])))
    (is (= [{:id s2 :score 2.0} {:id s1 :score 1.0}] (get-in (graph p "central" [] {:metric "degree" :limit "2"}) [:data :nodes])))
    (is (= {:count 1 :nodes [lonely]} (:data (graph p "isolated" [] {:type "Spec"}))))
    (testing "paths in the output are never absolute; nothing is written without -o"
      (let [e (graph p "export" [] {:output (str (fs/path p "abs" "e.json"))})]
        (is (= "abs/e.json" (get-in e [:data :path])))
        (is (fs/exists? (fs/path p "abs" "e.json")))
        (is (not (str/includes? (pr-str (:data (graph p "get" ["Spec:s1"]))) p)))))
    (testing "ingest-sldb writes the node-link snapshot of a semantic export"
      (is (true? (:ok (run p "semantic" "export" "-o" "export.json"))))
      (let [e (graph p "ingest-sldb" ["export.json"] {:output "ingested.json"})]
        (is (= "ingested.json" (get-in e [:data :path])))
        (is (= "sldb_kgdb_semantic_export" (get-in e [:data :contract "name"])))
        (is (= (get-in e [:data :nodes]) (get-in (graph p "snapshot" ["load" "ingested.json"]) [:data :nodes])))
        (is (= "sldb_document" (get-in (graph p "get" ["Spec:s1"] {:snapshot "export.json"}) [:data :type])) "an export is a source too")))))

(deftest bad-options-and-arguments
  (let [p (analysis-world)]
    (spit (str (fs/path p "bad.json")) "{nope")
    (spit (str (fs/path p "notgraph.json")) "{\"a\": 1}")
    (spit (str (fs/path p "badq.json")) "{\"filters\": [{\"facet\": \"identity\", \"conditions\": [{\"field\": \"node_type\", \"op\": \"nope\", \"value\": 1}]}]}")
    (doseq [[expected sub args opts]
            [[["cli/usage" 6] nil []] [["cli/usage" 6] "nope" []] [["cli/usage" 6] "get" []]
             [["graph/missing" 1] "get" ["Spec:zzz"]] [["cli/usage" 6] "query" []]
             [["graph/missing" 1] "query" [] {:file "absent.json"}] [["graph/invalid" 3] "query" [] {:file "bad.json"}]
             [["graph/bad-query" 3] "query" [] {:file "badq.json"}]
             [["cli/usage" 6] "neighborhood" ["Spec:s1"] {:depth "deep"}] [["cli/usage" 6] "neighborhood" ["Spec:s1"] {:direction "sideways"}]
             [["graph/missing" 1] "neighborhood" ["Spec:zzz"]] [["cli/usage" 6] "path" ["Spec:s1"]]
             [["graph/missing" 1] "path" ["Spec:s1" "Spec:zzz"]] [["cli/usage" 6] "cycles" [] {:limit "-1"}]
             [["cli/usage" 6] "central" [] {:metric "fame"}] [["cli/usage" 6] "central" [] {:limit "x"}]
             [["cli/usage" 6] "snapshot" []] [["cli/usage" 6] "snapshot" ["save"]] [["cli/usage" 6] "snapshot" ["burn" "x.json"]]
             [["graph/missing" 1] "snapshot" ["load" "absent.json"]] [["graph/invalid" 3] "snapshot" ["load" "notgraph.json"]]
             [["graph/invalid" 3] "list" [] {:snapshot "bad.json"}] [["cli/usage" 6] "ingest-sldb" []]
             [["graph/bad-export" 3] "ingest-sldb" ["notgraph.json"]]]]
      (is (= expected (failure (graph p sub args (or opts {})))) (str sub " " args " " opts)))))
