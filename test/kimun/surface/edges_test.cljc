(ns kimun.surface.edges-test
  "v1's edge-index tests at surface level: stores are set up through the CLI
   (`kimun.cli.main/run`), then the pure `kimun.surface.edges` is read."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.surface.edges :as edges]
            [kimun.host.default :as host]
            [kimun.kernel.ports :as ports]
            [kimun.kernel.store :as store]))

(def ^:private env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run (vec argv) env cwd)))
(defn- ok! [cwd & argv] (let [e (apply run cwd argv)] (is (true? (:ok e)) (pr-str argv (:error e))) e))
(defn- opened [p] (cli-store/open! (cli-store/resolve-store {} env p)))
(defn- idx ([p] (idx p {})) ([p opts] (edges/index (:store (opened p)) opts)))
(defn- g [p] (:graph (idx p)))
(defn- pairs [es] (mapv (juxt :to :type) es))
(defn- raised [f] (try (f) nil (catch clojure.lang.ExceptionInfo e (:type (ex-data e)))))

(defn- apply!
  "Builds a plan with `build` over the opened store and commits it, as a CLI command would."
  [p build arg]
  (let [{:keys [backend] s :store} (opened p)
        {:keys [changed plan]} (build host/host s arg {:actor "human/tester" :timestamp "2026-01-01T00:00:00.000Z" :tree-id (ports/ulid (:ids host/host))})]
    (when changed (store/commit! backend s plan))
    (boolean changed)))

(defn- model [p n {:keys [semantics fields template extra]}]
  (spit (str (fs/path p (str n ".edn")))
        (pr-str (merge {:model/name n :model/version 1 :base [] :semantics (or semantics {})
                        :fields (mapv (fn [[f t]] {:name f :type t :required false :description (str f ".")}) fields) :template template}
                       extra)))
  (ok! p "models" "define" "-f" (str n ".edn")))

(defn- create [p m n payload & [path]]
  (spit (str (fs/path p "_data.json")) (json/generate-string payload))
  (apply ok! p "docs" "create" m n "--data" "_data.json" (when path ["--path" path])))

(defn- store-dir [] (let [p (str (ktu/temp-dir "kimun-edges-"))] (ok! p "stores" "init" "--name" "p") p))

(def ^:private relation-doc
  {:fields [["title" :string] ["source_id" :string] ["target_id" :string] ["relation_type" :string] ["condition" :string] ["notes" :string]]
   :extra {:py/references ["source_id" "target_id"]}
   :template "---\nsource_id: ⸢rev•source_id⸥\ntarget_id: ⸢rev•target_id⸥\nrelation_type: ⸢rev•relation_type⸥\ncondition: ⸢rev•condition⸥\nnotes: ⸢rev•notes⸥\n---\n\n# ⸢rev•title⸥\n"})

(defn- relation [p n src tgt rtype]
  (create p "RelationDoc" n {"title" n "source_id" src "target_id" tgt "relation_type" rtype "condition" "" "notes" ""}))

(defn- world []
  (let [p (store-dir)]
    (model p "Table" {:semantics {"type" ["restaurant" "table"]} :fields [["title" :string] ["capacity" :int] ["notes" :text]]
                      :template "# ⸢rev•title⸥\n\nCapacity: ⸢rev•capacity⸥\n\n## Notes\n\n⸢rev,markdown•notes⸥\n"})
    (model p "Booking" {:fields [["title" :string] ["party_size" :int]] :template "# ⸢rev•title⸥\n\nSize: ⸢rev•party_size⸥\n"})
    (model p "Move" {:semantics {"type" ["pron" "move"]} :fields [["title" :string]] :template "# ⸢rev•title⸥\n"})
    (model p "Alias" {:semantics {"type" ["knowledge" "anchor"]} :fields [["symbol" :string] ["ref" :string]] :template "# ⸢rev•symbol⸥\n\nRef: ⸢rev•ref⸥\n"})
    (model p "RelationDoc" relation-doc)
    (create p "Table" "t12" {"title" "Table 12" "capacity" 6 "notes" "By the window."})
    (create p "Table" "t14" {"title" "Table 14" "capacity" 8 "notes" "Spare."})
    (create p "Booking" "b1" {"title" "Ana" "party_size" 6})
    (is (apply! p edges/define-relation-type-plan {:name "assigned_to" :cardinality "many_to_one" :axis "WHERE" :source-types ["Booking"] :target-types ["Table"]
                                                   :condition "capacity >= {party_size}"}))
    (relation p "b1-t12" "Booking:b1" "Table:t12" "assigned_to")
    p))

;; tests/api/test_api_edges.py::test_structural_edges_and_typed_nodes
(deftest structural-edges-and-typed-nodes
  (let [g (g (world))]
    (is (some #{["sldb://document/Table:t12" "has_document"]} (pairs (edges/edges-from g "sldb://model/Table"))))
    (is (some #{["sldb://section/Table:t12#table-12/notes" "has_section"]} (pairs (edges/edges-from g "Table:t12"))))
    (is (some #{["sldb://semantic_tag/type.restaurant.table" "tagged_as"]} (pairs (edges/edges-from g "Table:t12" "tagged_as"))))
    (is (= "Table" (:type (edges/node g "Table:t12"))))
    (is (= ["sldb://document/Booking:b1"] (mapv :id (edges/nodes-of-type g "Booking"))))
    (is (= ["sldb_field" "integer"] ((juxt :type #(get-in % [:semantics "kind"])) (edges/node g "sldb://field/Table.capacity"))))
    (is (= ["sldb://store"] (mapv :from (edges/edges-to g "sldb://model/Table" "has_model"))))
    (is (contains? (:facets (edges/node g "Table:t12")) "source"))))

;; tests/api/test_api_edges.py::test_a_relation_doc_is_an_edge_that_inherits_from_its_type
(deftest a-relation-doc-is-an-edge-that-inherits-from-its-type
  (let [i (idx (world)) g (:graph i) es (edges/edges-from g "Booking:b1" "assigned_to")]
    (is (= 1 (count es)))
    (is (= ["sldb://document/Booking:b1" "sldb://document/Table:t12"] ((juxt :from :to) (first es))))
    (is (= {"origin" "relation_doc" "relation_doc" "b1-t12" "condition" "capacity >= {party_size}" "axis" "WHERE"} (:metadata (first es))))
    (is (= es (edges/edges-to g "Table:t12" "assigned_to")))
    (is (nil? (edges/node g "RelationDoc:b1-t12")))
    (is (= [["sldb://model/Booking" "applies_to_source"] ["sldb://model/Table" "applies_to_target"]] (pairs (edges/edges-from g "sldb://relation_type/assigned_to"))))
    (is (:ok (edges/check i)) (pr-str (:errors (edges/check i))))))

;; tests/api/test_api_edges.py::test_untracking_a_document_drops_its_shard_and_reports_the_orphan
;; plus the kernel side: an authored relation whose endpoint was untracked reports its anchor state.
(deftest untracking-a-document-reports-the-orphan
  (let [p (world)]
    (is (apply! p edges/relate-plan {:from "Booking:b1" :to "Table:t12" :relation "assigned_to"}))
    (is (false? (apply! p edges/relate-plan {:from "b1" :to "t12" :relation "assigned_to"})) "already standing")
    (let [i (idx p) a (first (:authored i))]
      (is (= [:intact "Booking:b1" "Table:t12"] [(:state a) (get-in a [:from :document]) (get-in a [:to :document])]))
      (is (= #{"relation_doc" "authored"} (set (map #(get-in % [:metadata "origin"]) (edges/edges-from (:graph i) "Booking:b1" "assigned_to")))))
      (is (= ["sldb://document/Booking:b1 has 2 'assigned_to' targets but cardinality is many_to_one"] (:errors (edges/check i)))))
    (ok! p "docs" "untrack" "t12")
    (let [i (idx p) c (edges/check i) a (first (:authored i))]
      (is (nil? (edges/node (:graph i) "Table:t12")))
      (is (= [] (edges/edges-from (:graph i) "Booking:b1" "assigned_to")))
      (is (= "relation 'b1-t12': target 'Table:t12' is not a tracked document" (first (:errors c))))
      (is (not= :intact (:state a)))
      (is (= :orphan (get-in a [:to :state])))
      (is (= [(:edge a)] (mapv :edge (:anchors c))))
      (is (apply! p edges/unrelate-plan {:from "Booking:b1" :to (get-in a [:to :node]) :relation "assigned_to"}))
      (is (= [] (:authored (idx p)))))))

;; tests/api/test_api_edges.py::test_undirected_types_read_both_ways_and_bad_classes_are_reported
(deftest undirected-types-read-both-ways-and-bad-classes-are-reported
  (let [p (world)]
    (apply! p edges/define-relation-type-plan {:name "near" :direction "undirected" :source-types ["Table"] :target-types ["Table"]})
    (relation p "t12-t14" "Table:t12" "Table:t14" "near")
    (let [es (edges/edges-from (g p) "Table:t14" "near")]
      (is (= 1 (count es)))
      (is (= ["sldb://document/Table:t12" true] [(:to (first es)) (get-in (first es) [:metadata "reverse"])])))
    (relation p "b1-near" "Booking:b1" "Table:t14" "near")
    (is (some #(re-find #"source class 'Booking' not in source_types \['Table'\]" %) (:errors (edges/check (idx p)))))))

;; tests/api/test_api_edges.py::test_exclude_tags_is_the_readers_choice
(deftest exclude-tags-is-the-readers-choice
  (let [p (world) _ (create p "Move" "m1" {"title" "a move"}) hidden (:graph (idx p {:exclude-tags ["type.pron.move"]}))]
    (is (some? (edges/node (g p) "Move:m1")))
    (is (nil? (edges/node hidden "Move:m1")))
    (is (= [] (edges/edges-from hidden "sldb://model/Move" "has_document")))
    (is (some? (edges/node hidden "sldb://semantic_tag/type.pron.move")))))

;; tests/api/test_api_edges.py::test_anchors_name_what_their_ref_points_at
(deftest anchors-name-what-their-ref-points-at
  (let [p (world)]
    (create p "Alias" "mesa" {"symbol" "mesa" "ref" "model:Table"})
    (create p "Alias" "cupo" {"symbol" "cupo" "ref" "(field Table capacity)"})
    (let [g (g p)]
      (is (= [["sldb://model/Table" "names"]] (pairs (edges/edges-from g "sldb://anchor/mesa"))))
      (is (= [["sldb://field/Table.capacity" "names"]] (pairs (edges/edges-from g "sldb://anchor/cupo"))))
      (is (nil? (edges/node g "Alias:mesa"))))))

;; tests/api/test_api_edges.py::test_init_relations_is_idempotent — the builtin types need no init; redefining is a no-op.
(deftest relation-types-are-there-without-init-and-defining-is-idempotent
  (let [p (world)]
    (is (some #{"sldb://relation_type/has_document"} (map :id (edges/nodes-of-type (g p) "relation_type"))))
    (is (false? (apply! p edges/define-relation-type-plan {:name "assigned_to" :cardinality "many_to_one" :axis "WHERE" :source-types ["Booking"] :target-types ["Table"]
                                                           :condition "capacity >= {party_size}"})))
    (is (= :edges/conflict (raised #(apply! p edges/define-relation-type-plan {:name "has_document"}))))))

;; tests/api/test_declared_references.py (6 functions, named in each `testing`)
(deftest declared-references
  (let [p (store-dir)
        link (fn [n about steps] (create p "Linked" n {"title" n "about" about "steps" steps}))
        of (fn [n rel] (edges/edges-from (g p) (str "Linked:" n) rel))]
    (model p "Note" {:semantics {"type" ["knowledge" "note"]} :fields [["title" :string] ["body" :text]] :template "# ⸢rev•title⸥\n\n⸢rev•body⸥\n"})
    (model p "Linked" {:semantics {"type" ["knowledge" "linked"]} :fields [["title" :string] ["about" :string] ["steps" [:list :string]]]
                       :extra {:py/references ["about"] :py/containment {"steps" ["Note"]}}
                       :template "---\nabout: ⸢rev•about⸥\nsteps: ⸢rev,list•steps⸥\n---\n\n# ⸢rev•title⸥\n"})
    (model p "RelationDoc" relation-doc)
    (doseq [n ["n1" "n2" "n3"]] (create p "Note" n {"title" n "body" "text"}))
    (link "l1" "Note:n1" ["Note:n1"]) (link "l2" "Note:missing" ["Note:n2" "Note:n3"]) (link "l3" "n1" [])
    (testing "test_a_declared_reference_becomes_an_edge_that_names_its_field"
      (is (= [["sldb://document/Note:n1" "references" "about"]] (mapv (juxt :to :type #(get-in % [:metadata "field"])) (of "l1" "references")))))
    (testing "test_a_containment_field_becomes_contains_and_carries_the_models_it_allows"
      (is (= ["sldb://document/Note:n1" ["Note"]] ((juxt :to #(get-in % [:metadata "allowed_models"])) (first (of "l1" "contains"))))))
    (testing "test_a_list_field_produces_one_edge_per_id" (is (= 2 (count (of "l2" "contains")))))
    (testing "test_a_reference_to_an_untracked_document_is_dropped_not_reported"
      (is (= [] (of "l2" "references"))) (is (= [] (:errors (edges/check (idx p))))))
    (testing "test_a_bare_name_without_its_model_is_not_guessed_at" (is (= [] (of "l3" "references"))))
    (testing "test_a_relation_document_does_not_assert_its_own_ends_twice"
      (apply! p edges/define-relation-type-plan {:name "x" :axis "WHAT"})
      (relation p "r1" "Note:n1" "Note:n2" "x")
      (is (= [] (edges/edges-from (g p) "RelationDoc:r1" "references")))
      (is (= ["x"] (mapv :type (edges/edges-to (g p) "Note:n2" "x"))))
      (is (:ok (edges/check (idx p)))))))

;; tests/api/test_composition_edges.py (7 functions, named in each `testing`)
(deftest composition-edges
  (let [p (store-dir)
        board (fn [n tasks] (create p "Board" n {"title" n "tasks" tasks}))
        composes (fn [n] (edges/edges-from (g p) (str "Board:" n) "composes"))]
    (model p "Task" {:semantics {"type" ["workflow" "task"]} :fields [["title" :string] ["status" :string]] :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"})
    (model p "Board" {:semantics {"type" ["workflow" "board"]} :fields [["title" :string] ["tasks" [:list :string]]]
                      :extra {:py/compositions {"task_summaries" {"source_field" "tasks" "model" "mods:Task" "template" "- {title} [{status}]"}}}
                      :template "# ⸢rev•title⸥\n\n## Tasks\n\n- ⸢rev,list•tasks⸥\n"})
    (model p "Sourceless" {:fields [["title" :string]] :extra {:py/compositions {"summary" {"model" "mods:Task" "template" "- {title}"}}} :template "# ⸢rev•title⸥\n"})
    (fs/create-dirs (fs/path p "sub"))
    (create p "Task" "task-a" {"title" "Ship it" "status" "active"} "sub/task-a.md")
    (board "board-one" ["sub/task-a.md"]) (board "board-two" ["./sub/task-a.md"]) (board "board-three" ["sub/task-a.md" "nowhere.md"])
    (create p "Sourceless" "lonely" {"title" "Lonely"})
    (testing "test_a_composed_field_contributes_a_composes_edge" (is (= ["sldb://document/Task:task-a"] (mapv :to (composes "board-one")))))
    (testing "test_the_edge_carries_the_field_and_the_ref_as_authored"
      (is (= {"field" "task_summaries" "source_field" "tasks" "ref_path" "sub/task-a.md" "composed_model" "Task" "origin" "schema"}
             (dissoc (:metadata (first (composes "board-one"))) "ref_index"))))
    (testing "test_impact_radius_finds_the_parents_that_compose_a_document"
      (is (= ["sldb://document/Board:board-one" "sldb://document/Board:board-three" "sldb://document/Board:board-two"]
             (sort (map :from (edges/edges-to (g p) "Task:task-a" "composes"))))))
    (testing "test_a_ref_written_differently_resolves_to_the_same_document" (is (= ["sldb://document/Task:task-a"] (mapv :to (composes "board-two")))))
    (testing "test_a_ref_no_document_owns_keeps_no_edge" (is (= ["sub/task-a.md"] (mapv #(get-in % [:metadata "ref_path"]) (composes "board-three")))))
    (testing "test_a_composed_field_without_a_source_field_contributes_nothing" (is (= [] (edges/edges-from (g p) "Sourceless:lonely" "composes"))))
    (testing "test_edges_check_accepts_the_composes_relation" (is (= [] (:errors (edges/check (idx p))))))))

;; tests/test_relation_models.py::test_relation_instance_roundtrip, ::test_relation_type_roundtrip,
;; ::test_relation_is_content_blind, ::test_relation_type_literal_fields_expose_enum_schema
(deftest relation-models
  (let [p (world)]
    (testing "a relation instance reads back as the edge it was written as"
      (relation p "step" "Table:t12" "Table:t14" "assigned_to")
      (is (= ["sldb://document/Table:t14"] (mapv :to (edges/edges-from (g p) "Table:t12" "assigned_to")))))
    (testing "a relation type reads back as it was defined"
      (apply! p edges/define-relation-type-plan {:name "flows_to" :source-types ["Table"] :target-types ["Table"] :description "Step A can transition to step B."})
      (is (= {"name" "flows_to" "title" "flows_to" "direction" "directed" "cardinality" "many_to_many" "axis" "" "source_types" ["Table"]
              "target_types" ["Table"] "condition" "" "description" "Step A can transition to step B."}
             (get (edges/stored-relation-types (:store (opened p))) "flows_to") (:semantics (edges/node (g p) "sldb://relation_type/flows_to")))))
    (testing "a relation is content blind: an authored one carries only its ends, its type and its evidence"
      (apply! p edges/relate-plan {:from "Table:t12" :to "Table:t14" :relation "flows_to"})
      (is (= #{:edge :relation :actor :state :from :to} (set (keys (first (:authored (idx p))))))))
    (testing "direction and cardinality are closed enums"
      (is (= :edges/invalid (raised #(apply! p edges/define-relation-type-plan {:name "bad" :direction "sideways"}))))
      (is (= :edges/invalid (raised #(apply! p edges/define-relation-type-plan {:name "bad" :cardinality "some_to_some"})))))))

;; tests/store/test_edge_anchor_targets.py::test_anchor_targets, ::test_compose_refs_name_their_steps
(deftest anchor-targets
  (let [table "sldb://model/Table" cap "sldb://field/Table.capacity" asg "sldb://relation_type/assigned_to" doc "sldb://document/Table:table-12"]
    (doseq [[ref expected] [["model:Table" [table]] ["field:Table.capacity" [cap]] ["field:Table" []] ["predicate:Table:capacity >= 6" [table]]
                            ["relation:assigned_to" [asg]] ["doc:Table:table-12" [doc]] ["action:Table.capacity=8 Table.title=x" [cap "sldb://field/Table.title"]]
                            ["nothing:known" []] ["(model Table)" [table]] ["(where Table \"capacity >= 6\")" [table]] ["(field Table capacity)" [cap]]
                            ["(relation assigned_to)" [asg]] ["(doc \"Table:table-12\")" [doc]] ["(change (it of Table) capacity 8)" [cap]]
                            ["(change capacity 8)" []] ["(move (create Reservation) (assert assigned_to (created) (a Table)))" ["sldb://model/Reservation" asg]]
                            ["(\"not a symbol\" Table)" []] ["(" []]]]
      (is (= expected (edges/anchor-targets {"ref" ref})) ref))
    (is (= ["sldb://model/Reservation" asg]
           (edges/anchor-targets {"ref" "compose:book" "steps" [{"model" "Reservation"} {"relation" "assigned_to"} "free text" {"model" ""}]})))))

;; tests/api/test_api_semantic.py::test_a_declared_parent_is_read_by_the_closure_by_the_graph_and_by_the_journal (the edge-index assertion)
(deftest a-declared-semantic-parent-is-read-through-the-graph
  (let [p (store-dir)]
    (model p "Spec" {:semantics {"type" ["knowledge" "spec"]} :fields [["title" :string]] :template "# ⸢rev•title⸥\n"})
    (model p "Topology" {:semantics {"layer" ["topology"]} :fields [["title" :string]] :template "# ⸢rev•title⸥\n"})
    (create p "Spec" "s1" {"title" "S1"}) (create p "Topology" "t1" {"title" "T1"})
    (ok! p "semantic" "parent-add" "layer.topology" "type.knowledge")
    (is (some #{"sldb://semantic_tag/type.knowledge"} (map :to (edges/edges-from (g p) "sldb://semantic_tag/layer.topology" "semantic_parent"))))))

(deftest predicates-live-in-the-kernel
  (let [p (store-dir) reg #(edges/predicate-registry (:store (opened p)))]
    (is (= {"is_solved_by" "HOW" "implements" "HOW" "mathematically_proves" "WHY" "explains_failure_of" "WHY" "grounded_by" "PROVENANCE"
            "restricts" "WHEN_WHERE" "defines" "WHAT"} (reg)) "v1's defaults before any write")
    (is (apply! p edges/add-predicate-plan {:name "blocks" :axis "WHEN" :description "Blocks the target."}))
    (is (= "WHEN" (get (reg) "blocks")))
    (is (= 8 (count (reg))) "the first write seeds the defaults")
    (is (= :edges/conflict (raised #(apply! p edges/add-predicate-plan {:name "blocks" :axis "WHY"}))))
    (is (= :edges/invalid (raised #(apply! p edges/add-predicate-plan {:name "bad name" :axis "WHY"}))))
    (is (apply! p edges/remove-predicate-plan "defines"))
    (is (apply! p edges/remove-predicate-plan "blocks"))
    (is (= 6 (count (reg))))
    (is (= :edges/missing (raised #(apply! p edges/remove-predicate-plan "blocks"))))
    (is (= {:name "implements" :axis "HOW" :description "Identifies what the source implements."} (edges/predicate (:store (opened p)) "implements")))))

(deftest predicate-links-are-edges-when-asked-for
  (let [p (world)]
    (create p "Table" "t15" {"title" "Table 15" "capacity" 2 "notes" "Next to [implements:: [[Table:t12]]] and [defines:: [[nowhere]]]."})
    (is (= [] (edges/edges-from (g p) "Table:t15" "implements")) "v1's index has no link edges")
    (let [i (idx p {:links? true})]
      (is (= [["sldb://document/Table:t12" {"origin" "link" "predicate" "implements" "axis" "HOW"}]]
             (mapv (juxt :to :metadata) (edges/edges-from (:graph i) "Table:t15" "implements"))))
      (is (= [] (edges/edges-from (:graph i) "Table:t15" "defines")))
      (is (:ok (edges/check i))))))

(def ^:private relation-type-doc
  {:semantics {"type" ["relation" "type"]}
   :fields [["title" :string] ["name" :string] ["direction" :string] ["cardinality" :string] ["axis" :string]
            ["source_types" [:list :string]] ["target_types" [:list :string]] ["condition" :string]]
   :template "---\nname: ⸢rev•name⸥\ndirection: ⸢rev•direction⸥\ncardinality: ⸢rev•cardinality⸥\naxis: ⸢rev•axis⸥\nsource_types: ⸢rev,list•source_types⸥\ntarget_types: ⸢rev,list•target_types⸥\ncondition: ⸢rev•condition⸥\n---\n\n# ⸢rev•title⸥\n"})

;; v1 `RelationTypeDoc` (tests/test_relation_models.py, store/edge_index relation_contribution): a document of the
;; model, or of a model based on it, IS a relation type — the real ones are compared in edges_parity_test.
(deftest a-relation-type-document-defines-a-relation-type
  (let [p (world)
        rtype (fn [m n src tgt] (create p m (str "reltype-" n) {"title" n "name" n "direction" "directed" "cardinality" "many_to_many" "axis" "WHO"
                                                              "source_types" src "target_types" tgt "condition" "always"}))]
    (model p "RelationTypeDoc" relation-type-doc)
    (model p "HouseRelationType" (assoc relation-type-doc :extra {:base ["RelationTypeDoc"]}))
    (rtype "RelationTypeDoc" "served_at" ["Booking"] ["Table" "Nowhere"])
    (rtype "HouseRelationType" "next_to" ["Table"] ["Table"])
    (relation p "b1-served" "Booking:b1" "Table:t14" "served_at")
    (let [i (idx p) g (:graph i)]
      (is (= "relation_type" (:type (edges/node g "sldb://relation_type/served_at")) (:type (edges/node g "sldb://relation_type/next_to"))))
      (is (= ["reltype-served_at" "WHO"] (mapv (:semantics (edges/node g "sldb://relation_type/served_at")) ["doc" "axis"])))
      (testing "it contributes applies_to_* to the models that exist (a dangling schema edge is dropped) and nothing a plain document does"
        (is (= [["sldb://model/Booking" "applies_to_source"] ["sldb://model/Table" "applies_to_target"]] (pairs (edges/edges-from g "sldb://relation_type/served_at"))))
        (is (= [["sldb://model/Table" "applies_to_source"] ["sldb://model/Table" "applies_to_target"]] (pairs (edges/edges-from g "sldb://relation_type/next_to"))))
        (is (nil? (edges/node g "RelationTypeDoc:reltype-served_at")))
        (is (= [] (edges/edges-from g "sldb://model/RelationTypeDoc" "has_document") (edges/edges-from g "sldb://model/HouseRelationType" "has_document"))))
      (testing "relation documents of that type inherit from it and are checked against it"
        (is (= {"sldb://document/Table:t14" {"origin" "relation_doc" "relation_doc" "b1-served" "condition" "always" "axis" "WHO"}}
               (into {} (map (juxt :to :metadata)) (edges/edges-from g "Booking:b1" "served_at"))))
        (is (= "many_to_many" (get-in i [:relation-types "served_at" "cardinality"])))
        (is (:ok (edges/check i)) (pr-str (:errors (edges/check i))))))
    (relation p "t12-served" "Table:t12" "Table:t14" "served_at")
    (is (some #(= :endpoint-type (:kind %)) (:violations (edges/check (idx p)))) "a Table may not be the source of served_at")))

;; store/test_edge_anchor_targets.py, kernel side: what the anchor state of an authored relation does under succession.
;; `:superseded` needs an active `supersedes` edge leaving an endpoint (kimun.kernel.anchor), which only a `:replace` of
;; that node records; `docs update` never replaces a document's root (kimun.surface.markdown.plan raises
;; `:markdown/root-changed` rather than invent a succession), so no surface operation makes an authored relation superseded.
(deftest an-authored-relation-under-updates-removal-and-re-adding
  (let [p (world) rel {:from "Booking:b1" :to "Table:t14" :relation "assigned_to"}
        one #(first (:authored (idx p)))
        standing #(mapv (juxt :state (fn [a] (get-in a [:from :document])) (fn [a] (get-in a [:to :document]))) (:authored (idx p)))]
    (is (apply! p edges/relate-plan rel))
    (let [before (one)]
      (ok! p "docs" "update" "t14" "--set" "notes=Moved to the terrace.")
      (testing "updating the target keeps its root node: same edge, still intact, never superseded"
        (is (= before (one)))
        (is (= [:intact :intact :intact] [(:state before) (get-in before [:from :state]) (get-in before [:to :state])]))
        (is (= ["sldb://document/Table:t14"] (mapv :to (filter #(= "authored" (get-in % [:metadata "origin"])) (edges/edges-from (g p) "Booking:b1" "assigned_to")))))
        (is (= [] (:anchors (edges/check (idx p)))))))
    (testing "removing and re-adding it (same actor, hence the same content-addressed edge) leaves one standing relation"
      (is (apply! p edges/unrelate-plan rel))
      (is (= [] (:authored (idx p))))
      (is (= :edges/missing (raised #(apply! p edges/unrelate-plan rel))))
      (is (apply! p edges/relate-plan rel))
      (is (= [[:intact "Booking:b1" "Table:t14"]] (standing)))
      (is (false? (apply! p edges/relate-plan rel))))
    ;; regression: `docs update` of the SOURCE document keeps the authored relation — the docs surface
    ;; removes only the edges it owns (model, source, file), never an edge someone else wrote from the root.
    (testing "updating the source document keeps the relation"
      (ok! p "docs" "update" "b1" "--set" "party_size=5")
      (is (= [[:intact "Booking:b1" "Table:t14"]] (standing)))
      (is (false? (apply! p edges/relate-plan rel)) "still there: authoring it again changes nothing"))))

(deftest links-only-add-edges-of-origin-link-to-the-default-view
  (let [p (world)]
    (create p "Table" "t15" {"title" "Table 15" "capacity" 2 "notes" "Next to [implements:: [[Table:t12]]] and [refines:: [[t14]]]."})
    (let [plain (idx p) linked (idx p {:links? true})
          added (remove (set (:edges (:graph plain))) (:edges (:graph linked)))]
      (is (= (:nodes (:graph plain)) (:nodes (:graph linked))))
      (is (= (:edges (:graph plain)) (vec (remove #(= "link" (get-in % [:metadata "origin"])) (:edges (:graph linked))))))
      (is (= #{["sldb://document/Table:t15" "sldb://document/Table:t12" "implements"] ["sldb://document/Table:t15" "sldb://document/Table:t14" "refines"]}
             (set (map (juxt :from :to :type) added))))
      (is (every? #(= "link" (get-in % [:metadata "origin"])) added))
      (is (= (dissoc plain :graph) (dissoc linked :graph))))))
