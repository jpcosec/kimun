(ns kimun.cli.edges-test
  "The `edges` group over a real store: v1
   `tests/api/test_api_edges.py::test_cli_edges_uses_the_api`, every
   subcommand, exits and typed errors, and one end-to-end walk through
   references, predicate links, authored relations and their anchor state.
   The groups are registered in `kimun.cli.main` at merge, so they are called
   through their `dispatch`."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.args :as args]
            [kimun.cli.commands.edges :as cedges]
            [kimun.cli.commands.predicates :as cpred]
            [kimun.cli.errors :as errors]
            [kimun.cli.main :as main]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run (vec argv) env cwd)))
(defn- ok! [cwd & argv] (let [e (apply run cwd argv)] (is (true? (:ok e)) (pr-str argv (:error e))) e))

(defn- call
  "Runs a group's `dispatch`; a trailing map is merged into the options."
  [dispatch cwd sub args]
  (let [extra (when (map? (last args)) (last args)) args (if extra (butlast args) args)]
    (errors/wrap #(dispatch sub args (merge {:format "json" :actor "human/tester"} extra) {:env env :cwd cwd}))))
(defn- edges [cwd sub & args] (call cedges/dispatch cwd sub args))
(defn- predicates [cwd sub & args] (call cpred/dispatch cwd sub args))
(defn- edges! [cwd sub & args] (let [e (apply edges cwd sub args)] (is (true? (:ok e)) (pr-str sub args (:error e))) (:data e)))
(defn- failed [e] [(get-in e [:error :type]) (:exit e)])
(defn- head [p] (:head (:store (cli-store/open! (cli-store/resolve-store {} env p)))))

(defn- model [p n {:keys [semantics fields template extra]}]
  (spit (str (fs/path p (str n ".edn")))
        (pr-str (merge {:model/name n :model/version 1 :base [] :semantics (or semantics {})
                        :fields (mapv (fn [[f t]] {:name f :type t :required false :description (str f ".")}) fields) :template template}
                       extra)))
  (ok! p "models" "define" "-f" (str n ".edn")))

(defn- create [p m n payload]
  (spit (str (fs/path p "_data.json")) (json/generate-string payload))
  (ok! p "docs" "create" m n "--data" "_data.json"))

(defn- store-dir [] (let [p (str (fs/create-temp-dir {:prefix "kimun-cli-edges-"}))] (ok! p "stores" "init" "--name" "p") p))

(defn- relation [p n src tgt rtype]
  (create p "RelationDoc" n {"title" n "source_id" src "target_id" tgt "relation_type" rtype "condition" "" "notes" ""}))

(defn- world
  "v1's `EdgeWorld`: tables t12 and t14, booking b1, the relation type
   `assigned_to` (defined through the CLI) and the relation document b1 → t12."
  []
  (let [p (store-dir)]
    (model p "RelationDoc" {:fields [["title" :string] ["source_id" :string] ["target_id" :string] ["relation_type" :string] ["condition" :string] ["notes" :string]]
                            :extra {:py/references ["source_id" "target_id"]}
                            :template "---\nsource_id: ⸢rev•source_id⸥\ntarget_id: ⸢rev•target_id⸥\nrelation_type: ⸢rev•relation_type⸥\ncondition: ⸢rev•condition⸥\nnotes: ⸢rev•notes⸥\n---\n\n# ⸢rev•title⸥\n"})
    (model p "Table" {:semantics {"type" ["domain" "table"]} :fields [["title" :string] ["capacity" :int] ["notes" :text]]
                      :template "# ⸢rev•title⸥\n\nCapacity: ⸢rev•capacity⸥\n\n## Notes\n\n⸢rev,markdown•notes⸥\n"})
    (model p "Booking" {:fields [["title" :string] ["party_size" :int]] :template "# ⸢rev•title⸥\n\nSize: ⸢rev•party_size⸥\n"})
    (create p "Table" "t12" {"title" "Table 12" "capacity" 6 "notes" "By the window."})
    (create p "Table" "t14" {"title" "Table 14" "capacity" 8 "notes" "Spare."})
    (create p "Booking" "b1" {"title" "Ana" "party_size" 6})
    (is (true? (:changed (edges! p "type-define" "assigned_to" {:cardinality "many_to_one" :axis "where" :source-types "Booking" :target-types "Table"
                                                                :condition "capacity >= {party_size}"}))))
    (relation p "b1-t12" "Booking:b1" "Table:t12" "assigned_to")
    p))

(defn- triples [data] (mapv (juxt :source :relation :target) (:edges data)))

;; tests/api/test_api_edges.py::test_cli_edges_uses_the_api
(deftest cli-edges-uses-the-api
  (let [p (world)]
    (testing "show, with the document positional as in v1"
      (let [e (edges p "show" "Booking:b1" {:relation "assigned_to"})]
        (is (= 0 (out/exit-code e)))
        (is (str/includes? (out/emit e "json") "\"target\" : \"sldb://document/Table:t12\""))
        (is (= [["sldb://document/Booking:b1" "assigned_to" "sldb://document/Table:t12"]] (triples (:data e))))))
    (is (= 0 (out/exit-code (edges p "check"))))
    (testing "rebuild writes nothing: there are no shards, the graph is derived"
      (let [before (head p) e (edges p "rebuild")]
        (is (= 0 (out/exit-code e)))
        (is (= {:changed false :derived true} (select-keys (:data e) [:changed :derived])))
        (is (= before (:revision e) (head p)))))
    (relation p "orphan" "Booking:b1" "Table:nowhere" "assigned_to")
    (let [e (edges p "check")]
      (is (= 3 (out/exit-code e)) "v1 exited 1; non-zero here through edges/invalid")
      (is (= "edges/invalid" (get-in e [:error :type])))
      (is (false? (get-in e [:error :data :ok])))
      (is (some #(str/includes? % "Table:nowhere") (get-in e [:error :data :errors]))))))

(deftest show-reads-from-to-and-everything
  (let [p (world)]
    (create p "Table" "t15" {"title" "Table 15" "capacity" 2 "notes" "Next to [implements:: [[Table:t12]]]."})
    (let [from (edges! p "show" {:from "sldb://document/Table:t15"})
          to (edges! p "show" {:to "Table:t12"})
          all (edges! p "show")]
      (is (= "sldb://document/Table:t15" (:from from)))
      (is (some #{["sldb://document/Table:t15" "implements" "sldb://document/Table:t12"]} (triples from)) "a predicate link is an edge by default")
      (is (= {"origin" "link" "predicate" "implements" "axis" "HOW"}
             (:metadata (first (:edges (edges! p "show" {:from "Table:t15" :relation "implements"}))))))
      (is (true? (:links from)))
      (is (= #{"assigned_to" "implements" "has_document"} (set (map :relation (:edges to)))))
      (is (= (:count all) (count (:edges all))))
      (is (< (:count to) (:count all)))
      (is (every? #{"has_model"} (map :relation (:edges (edges! p "show" {:relation "has_model"})))))
      (is (= 3 (:count (edges! p "show" {:relation "has_model"}))) "RelationDoc, Table, Booking"))
    (testing "--no-links leaves the links found in text out"
      (let [d (edges! p "show" {:from "Table:t15" :no-links true})]
        (is (false? (:links d)))
        (is (not-any? #{"implements"} (map :relation (:edges d)))))
      (is (= 1 (- (:count (edges! p "show")) (:count (edges! p "show" {:no-links "true"}))))))))

(deftest node-and-nodes
  (let [p (world)]
    (let [n (edges! p "node" "Table:t12")]
      (is (= {:id "sldb://document/Table:t12" :type "Table"} (select-keys n [:id :type])))
      (is (contains? (:facets n) "source"))
      (is (pos? (:in n)))
      (is (pos? (:out n))))
    (is (= "sldb_model" (:type (edges! p "node" "sldb://model/Table"))))
    (let [tables (edges! p "nodes" {:type "Table"}) all (edges! p "nodes")]
      (is (= ["sldb://document/Table:t12" "sldb://document/Table:t14"] (mapv :id (:nodes tables))))
      (is (= 2 (:count tables)))
      (is (contains? (first (:nodes tables)) :semantics))
      (is (= 2 (get-in all [:types "Table"])))
      (is (= 1 (get-in all [:types "sldb_store"])))
      (is (= (:count all) (reduce + (vals (:types all)))))
      (is (= #{:id :type} (set (keys (first (:nodes all)))))))
    (is (= 0 (:count (edges! p "nodes" {:type "Nothing"}))))))

(deftest types-and-type-define
  (let [p (world) by-name (fn [] (into {} (map (juxt #(get % "name") identity)) (:types (edges! p "types"))))]
    (is (= 15 (:count (edges! p "types"))) "the 14 builtin ones and assigned_to")
    (is (true? (get-in (by-name) ["has_document" "builtin"])))
    (is (= {"direction" "directed" "cardinality" "many_to_one" "axis" "WHERE" "source_types" ["Booking"] "target_types" ["Table"] "builtin" false}
           (select-keys (get (by-name) "assigned_to") ["direction" "cardinality" "axis" "source_types" "target_types" "builtin"])))
    (let [d (edges! p "type-define" "flows_to" {:direction "undirected" :source-types "Table, Booking" :description "Flows."})]
      (is (true? (:changed d)))
      (is (= {"name" "flows_to" "direction" "undirected" "cardinality" "many_to_many" "source_types" ["Table" "Booking"] "description" "Flows."}
             (select-keys (:type d) ["name" "direction" "cardinality" "source_types" "description"]))))
    (is (false? (:changed (edges! p "type-define" "flows_to" {:direction "undirected" :source-types "Table,Booking" :description "Flows."}))) "identical: nothing written")
    (is (= ["edges/conflict" 4] (failed (edges p "type-define" "has_document"))))
    (is (= ["edges/invalid" 3] (failed (edges p "type-define" "bad name"))))))

(deftest relate-and-unrelate
  (let [p (world)]
    (is (= {:changed true :relation "assigned_to" :from "Booking:b1" :to "Table:t14" :anchor "intact"}
           (edges! p "relate" "sldb://document/Booking:b1" "t14" {:relation "assigned_to"})))
    (is (false? (:changed (edges! p "relate" "Booking:b1" "Table:t14" {:relation "assigned_to"}))) "already standing")
    (let [d (edges! p "show" {:from "Booking:b1" :relation "assigned_to"})
          row (first (filter #(= "sldb://document/Table:t14" (:target %)) (:edges d)))]
      (is (= "intact" (:anchor row)) "an authored relation is shown with its anchor state")
      (is (= "authored" (get-in row [:metadata "origin"])))
      (is (= [["assigned_to" "Booking:b1" "Table:t14" "intact"]]
             (mapv (juxt :relation #(get-in % [:from :document]) #(get-in % [:to :document]) :state) (:authored d)))))
    (testing "two tables for one booking break many_to_one, and check says so"
      (let [e (edges p "check")]
        (is (= ["edges/invalid" 3] (failed e)))
        (is (some #{"cardinality"} (map :kind (get-in e [:error :data :violations]))))))
    (is (true? (:changed (edges! p "unrelate" "Booking:b1" "Table:t14" {:relation "assigned_to"}))))
    (is (= [] (:authored (edges! p "show" {:from "Booking:b1"}))))
    (is (true? (:ok (edges! p "check"))))
    (is (= ["edges/missing" 1] (failed (edges p "unrelate" "Booking:b1" "Table:t14" {:relation "assigned_to"}))))))

(deftest init-and-rebuild-are-reported-no-ops
  (let [p (store-dir) before (head p)]
    (doseq [sub ["init" "rebuild"]]
      (let [e (edges p sub) d (:data e)]
        (is (true? (:ok e)))
        (is (= {:changed false :derived true :relations-tree false :predicates 7 :relation-types 14 :authored 0 :problems 0}
               (select-keys d [:changed :derived :relations-tree :predicates :relation-types :authored :problems])))
        (is (string? (:message d)))
        (is (pos? (:nodes d)))
        (is (= before (:revision e) (head p)) (str sub " writes nothing"))))
    (is (true? (:ok (predicates p "add" "depends_on" {:axis "dependency"}))))
    (is (= {:relations-tree true :predicates 8} (select-keys (edges! p "rebuild") [:relations-tree :predicates])))))

(deftest typed-errors-and-exits
  (let [p (world)]
    (testing "an unknown node is edges/missing"
      (is (= ["edges/missing" 1] (failed (edges p "node" "Table:nowhere"))))
      (is (= ["edges/missing" 1] (failed (edges p "show" {:from "Table:nowhere"}))))
      (is (= ["edges/missing" 1] (failed (edges p "show" {:to "sldb://model/Nope"}))))
      (is (= ["edges/missing" 1] (failed (edges p "relate" "Booking:b1" "Table:nowhere" {:relation "assigned_to"}))))
      (is (= ["edges/missing" 1] (failed (edges p "relate" "Booking:b1" "Table:t14" {:relation "undefined_relation"})))))
    (testing "a bad option or a missing argument is cli/usage"
      (is (= ["cli/usage" 6] (failed (edges p "show" {:from "Booking:b1" :to "Table:t12"}))))
      (is (= ["cli/usage" 6] (failed (edges p "show" {:relation true}))))
      (is (= ["cli/usage" 6] (failed (edges p "show" {:no-links "maybe"}))))
      (is (= ["cli/usage" 6] (failed (edges p "node"))))
      (is (= ["cli/usage" 6] (failed (edges p "nodes" {:type true}))))
      (is (= ["cli/usage" 6] (failed (edges p "relate" "Booking:b1" "Table:t14"))) "--relation is required")
      (is (= ["cli/usage" 6] (failed (edges p "relate" "Booking:b1" {:relation "assigned_to"}))))
      (is (= ["cli/usage" 6] (failed (edges p "type-define"))))
      (is (= ["cli/usage" 6] (failed (edges p "type-define" "x" {:direction "sideways"}))))
      (is (= ["cli/usage" 6] (failed (edges p "type-define" "x" {:cardinality "3"}))))
      (is (= ["cli/usage" 6] (failed (edges p "nope"))))
      (is (= ["cli/usage" 6] (failed (edges p nil)))))
    (testing "an actor without capability is refused and nothing is written"
      (let [before (head p)]
        (is (= ["plan/rejected" 7] (failed (edges p "relate" "Booking:b1" "Table:t14" {:relation "assigned_to" :actor "human/intruder"}))))
        (is (= ["plan/rejected" 7] (failed (edges p "type-define" "sneaky" {:actor "human/intruder"}))))
        (is (= before (head p)))
        (is (= [] (:authored (edges! p "show"))))))
    (testing "--exclude-tag is the reader's choice"
      (is (= 2 (:count (edges! p "nodes" {:type "Table"}))))
      (is (= 0 (:count (edges! p "nodes" {:type "Table" :exclude-tag ["type.domain.table"]}))))
      (is (= 0 (:count (edges! p "nodes" {:type "Table" :exclude-tag "type.domain.table"})))))))

(deftest end-to-end
  (let [p (store-dir)]
    (model p "Note" {:semantics {"type" ["knowledge" "note"]} :fields [["title" :string] ["body" :text]] :template "# ⸢rev•title⸥\n\n⸢rev•body⸥\n"})
    (model p "Linked" {:semantics {"type" ["knowledge" "linked"]} :fields [["title" :string] ["about" :string]]
                       :extra {:py/references ["about"]} :template "---\nabout: ⸢rev•about⸥\n---\n\n# ⸢rev•title⸥\n"})
    (create p "Note" "n1" {"title" "n1" "body" "First."})
    (create p "Note" "n2" {"title" "n2" "body" "Second."})
    (create p "Linked" "l1" {"title" "l1" "about" "Note:n1"})
    (testing "the declared reference is an edge that names its field"
      (let [d (edges! p "show" {:from "Linked:l1" :relation "references"})]
        (is (= [["sldb://document/Linked:l1" "references" "sldb://document/Note:n1"]] (triples d)))
        (is (= "about" (get-in d [:edges 0 :metadata "field"])))))
    (testing "a predicate added to the store turns its links in text into edges"
      (is (true? (:ok (predicates p "add" "depends_on" {:axis "dependency" :description "Declares a dependency."}))))
      (create p "Note" "n3" {"title" "n3" "body" "Needs [depends_on:: [[Note:n2]]] first."})
      (let [d (edges! p "show" {:from "Note:n3" :relation "depends_on"})]
        (is (= [["sldb://document/Note:n3" "depends_on" "sldb://document/Note:n2"]] (triples d)))
        (is (= {"origin" "link" "predicate" "depends_on" "axis" "DEPENDENCY"} (get-in d [:edges 0 :metadata]))))
      (is (= 0 (:count (edges! p "show" {:from "Note:n3" :relation "depends_on" :no-links true})))))
    (testing "an authored relation is a kernel edge with an anchor state"
      (edges! p "type-define" "supports" {:axis "why" :source-types "Linked" :target-types "Note"})
      (is (= {:changed true :anchor "intact"} (select-keys (edges! p "relate" "Linked:l1" "Note:n2" {:relation "supports"}) [:changed :anchor])))
      (is (= ["intact"] (mapv :anchor (:edges (edges! p "show" {:to "Note:n2" :relation "supports"})))))
      (is (true? (:ok (edges! p "check")))))
    (testing "untracking the target leaves an orphan that check reports with a non-zero exit"
      (ok! p "docs" "untrack" "n2")
      (let [e (edges p "check") r (get-in e [:error :data])]
        (is (false? (:ok e)))
        (is (pos? (out/exit-code e)))
        (is (= ["edges/invalid" 3] (failed e)))
        (is (= [["supports" "Linked:l1" nil "orphan"]]
               (mapv (juxt :relation #(get-in % [:from :document]) #(get-in % [:to :document]) #(get-in % [:to :state])) (:anchors r))))
        (is (some #(str/includes? % "supports") (:errors r)))
        (is (some #{"relation-endpoint"} (map :kind (:violations r)))))
      (let [d (edges! p "show" {:from "Linked:l1"})]
        (is (not-any? #{"supports"} (map :relation (:edges d))) "the edge left the graph")
        (is (= ["orphan"] (mapv #(get-in % [:to :state]) (:authored d))) "and show still lists the relation, as an orphan")
        (testing "which can be removed by the root node id show reports"
          (is (true? (:changed (edges! p "unrelate" "Linked:l1" (get-in d [:authored 0 :to :node]) {:relation "supports"}))))
          (is (true? (:ok (edges! p "check")))))))))

;; What the integrator's registration amounts to: argv parsed by `kimun.cli.args/parse`
;; against the merged group specs, then the group's `dispatch`.
(deftest the-groups-work-behind-the-main-parser
  (let [p (world)
        specs (merge main/group-specs cedges/spec cpred/spec)
        cli (fn [& argv]
              (errors/wrap #(let [{[group sub & more] :args opts :opts} (args/parse (vec argv) env specs)]
                              (({"edges" cedges/dispatch "predicates" cpred/dispatch} group) sub more opts {:env env :cwd p}))))]
    (is (= [["sldb://document/Booking:b1" "assigned_to" "sldb://document/Table:t12"]]
           (triples (:data (cli "edges" "show" "--from" "Booking:b1" "--relation" "assigned_to" "--no-links")))))
    (is (= 0 (get-in (cli "edges" "nodes" "--type" "Table" "--exclude-tag" "type.domain.table" "--exclude-tag" "x.y") [:data :count])))
    (is (= ["Table" "Booking"]
           (get-in (cli "edges" "type-define" "flows_to" "--direction" "undirected" "--source-types" "Table,Booking" "--description" "Flows.")
                   [:data :type "source_types"])))
    (is (= "intact" (get-in (cli "edges" "relate" "Table:t12" "Table:t14" "--relation" "flows_to") [:data :anchor])))
    (is (= "DEPENDENCY" (get-in (cli "predicates" "add" "depends_on" "--axis" "dependency" "--description" "Declares.") [:data :predicate :axis])))
    (is (= ["cli/usage" 6] (failed (cli "edges" "show" "--from"))) "a value-taking option without a value")
    (is (= 6 (:exit (cli "edges" "show" "--nonsense" "x"))) "an option no group declares")
    (testing "every format renders"
      (doseq [fmt ["json" "edn" "text" "yaml"] e [(cli "edges" "show" "--to" "Table:t12") (cli "edges" "rebuild") (cli "predicates" "list")]]
        (is (not (str/blank? (out/emit e fmt))) fmt)))))
