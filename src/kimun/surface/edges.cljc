(ns kimun.surface.edges
  "The graph of a store as a derived view (v1's edge index), pure.

   `(index store)` rebuilds, from an opened store value (the `:store` of
   `kimun.cli.store/open!`), a `kimun.surface.graph.model` graph with v1's
   node ids (`sldb://document/Model:name` …), node types and relation names;
   nothing is persisted as a side index. Readers (`edges-from`, `edges-to`,
   `node`, `nodes-of-type`) take that graph; `check` takes the index.

   Predicates and relation types are content-addressed `:sign/:opaque` nodes
   in the `relations` taxonomy tree; an authored relation between two
   documents is a kernel `:semantic` edge between their root nodes whose
   evidence context (`kimun/relation/<name>`) carries the relation name, so
   its anchor state (`intact`/`superseded`/`orphan`) is derived by the kernel.
   Plan builders return `{:changed bool :plan p}`; apply `p` with
   `sldb.kernel.store/commit!` as the semantic commands do."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [kimun.surface.docs :as docs]
            [kimun.surface.links :as links]
            [kimun.surface.models.segments :as segments]
            [kimun.surface.models.store :as mstore]
            [kimun.surface.semantic :as sem]
            [sldb.kernel.anchor :as anchor]
            [sldb.kernel.err :as err]
            [sldb.kernel.revision :as revision]
            [sldb.kernel.node :as node]))

;; ---------------------------------------------------------------- ids

(def tree-name "Name of the taxonomy tree holding predicates and relation types." "relations")
(def predicate-format "Blob format of a predicate definition node." "edn/predicate")
(def relation-type-format "Blob format of a relation type node." "edn/relation-type")
(def relation-context "Prefix of the evidence context naming an authored relation." "kimun/relation/")
(def anchor-tag "The semantic tag that makes a document an anchor (v1)." "type.knowledge.anchor")

(defn store-id "Node id of the store." [] "sldb://store")
(defn model-id "Node id of model `m`." [m] (str "sldb://model/" m))
(defn field-id "Node id of field `f` of model `m`." [m f] (str "sldb://field/" m "." f))
(defn doc-id "Node id of the document with export id `Model:name`." [export-id] (str "sldb://document/" export-id))
(defn section-id "Node id of section `path` of document `export-id`." [export-id path] (str "sldb://section/" export-id "#" path))
(defn tag-id "Node id of semantic tag `t`." [t] (str "sldb://semantic_tag/" t))
(defn relation-type-id "Node id of relation type `n`." [n] (str "sldb://relation_type/" n))
(defn anchor-id "Node id of the anchor named `symbol`." [symbol] (str "sldb://anchor/" symbol))
(defn as-node-id
  "v1's `as_node_id`: an `sldb://` id is used as is, anything else is a
   document export id."
  [x]
  (if (str/starts-with? (str x) "sldb://") x (doc-id x)))

;; ---------------------------------------------------------------- registries

(def default-predicates
  "v1's predicates seeded on store creation: `[{:name :axis :description}]`."
  [{:name "is_solved_by" :axis "HOW" :description "Identifies how the source is solved."}
   {:name "implements" :axis "HOW" :description "Identifies what the source implements."}
   {:name "mathematically_proves" :axis "WHY" :description "Provides a mathematical proof."}
   {:name "explains_failure_of" :axis "WHY" :description "Explains why the target fails."}
   {:name "grounded_by" :axis "PROVENANCE" :description "Identifies supporting provenance."}
   {:name "restricts" :axis "WHEN_WHERE" :description "Restricts the target context."}
   {:name "defines" :axis "WHAT" :description "Defines the target concept."}])

(defn- rt [n axis card src tgt]
  {"name" n "title" n "direction" "directed" "cardinality" card "axis" axis
   "source_types" src "target_types" tgt "condition" "" "description" (str n ".") "builtin" true})

(def builtin-relation-types
  "v1's builtin relation types by name (string-keyed, `RelationTypeDoc` shape)."
  (into (sorted-map)
        (map (juxt #(get % "name") identity))
        [(rt "has_model" "WHAT" "one_to_many" ["sldb_store"] ["sldb_model"])
         (rt "has_document" "WHAT" "one_to_many" ["sldb_model"] [])
         (rt "has_section" "WHERE" "one_to_many" [] ["sldb_section"])
         (rt "tagged_as" "WHAT" "many_to_many" [] ["semantic_tag"])
         (rt "semantic_parent" "WHAT" "many_to_many" ["semantic_tag"] ["semantic_tag"])
         (rt "semantic_equivalent" "WHAT" "many_to_many" ["semantic_tag"] ["semantic_tag"])
         (rt "has_field" "WHAT" "one_to_many" ["sldb_model"] ["sldb_field"])
         (rt "extends" "WHAT" "many_to_many" ["sldb_model"] ["sldb_model"])
         (rt "applies_to_source" "WHAT" "many_to_many" ["relation_type"] ["sldb_model"])
         (rt "applies_to_target" "WHAT" "many_to_many" ["relation_type"] ["sldb_model"])
         (rt "names" "WHAT" "many_to_many" ["anchor"] [])
         (rt "references" "WHAT" "many_to_many" [] [])
         (rt "contains" "WHERE" "one_to_many" [] [])
         (rt "composes" "WHAT" "many_to_many" [] [])]))

(defn- relations-tree [store]
  ;; the first taxonomy tree by id among the trees with that name (the kernel's name index), loading only that tree
  (let [trees (:trees store)]
    (first (keep (fn [tid] (let [t (get trees tid)] (when (= :taxonomy (get-in t [:descriptor :kind])) [tid t])))
                 (revision/trees-named store tree-name)))))

(defn- entries
  "Entries of the relations tree: `[{:position :id :format :value}]`, nil when
   the tree does not exist yet."
  [store]
  (when-let [[_ t] (relations-tree store)]
    (vec (keep-indexed (fn [i pos]
                         (let [c (:content (get-in store [:objects (:node pos)]))]
                           (when (#{predicate-format relation-type-format} (:format c))
                             {:position i :id (:node pos) :format (:format c) :value (edn/read-string (:blob c))})))
                       (:children (:root t))))))

(defn predicates
  "Predicate definitions of `store`, `[{:name :axis :description}]` sorted by
   name: the stored ones, or v1's defaults while none was ever written."
  [store]
  (if-let [es (entries store)]
    (vec (sort-by :name (map :value (filter #(= predicate-format (:format %)) es))))
    (vec (sort-by :name default-predicates))))

(defn predicate
  "The predicate called `n`; raises `:edges/missing` otherwise."
  [store n]
  (or (first (filter #(= n (:name %)) (predicates store)))
      (err/raise :edges/missing (str "Predicate '" n "' is not registered.") {:predicate n})))

(defn predicate-registry
  "`{name axis}` of the predicates of `store` (what `parse-links` takes)."
  [store]
  (into (sorted-map) (map (juxt :name :axis)) (predicates store)))

(defn stored-relation-types
  "Relation types defined in the kernel of `store`, by name."
  [store]
  (into (sorted-map) (keep #(when (= relation-type-format (:format %)) [(get (:value %) "name") (:value %)])) (entries store)))

(defn validate-predicate
  "Problems (v1's sentences) of a predicate `{:name :axis}`; empty when valid."
  [{:keys [name axis]}]
  (vec (keep (fn [[label v]]
               (when-not (and (string? v) (re-matches #"[A-Za-z0-9_-]+" v))
                 (str "Predicate " label " '" v "' must contain only letters, numbers, '_' or '-'.")))
             [["name" name] ["axis" axis]])))

;; ---------------------------------------------------------------- plans

(defn- entry-node [fmt value] {:class :sign :kind :opaque :content {:format fmt :blob (pr-str (into (sorted-map) value))}})
(def ^:private root-node {:class :sign :kind :block :content {:format :markdown :type :document :attrs {:taxonomy tree-name}}})

(defn- plan [store {:keys [actor timestamp]} ops]
  {:changed true
   :plan {:plan/version 1 :base (:head store) :actor actor :timestamp timestamp
          :engines {"kimun.surface.edges" "1"} :ops (vec ops)}})

(defn- create-ops
  "Ops creating the relations tree with `nodes` as its children, in order."
  [{:keys [tree-id]} nodes]
  (when-not tree-id (err/raise :edges/invalid "the relations tree needs a :tree-id (a ULID) to be created" {}))
  (into [{:op :add-node :node root-node :as :root}
         {:op :new-tree :tree {:kind :taxonomy :name tree-name} :id tree-id :root :root :as :t}]
        (mapcat (fn [i n] (let [a (keyword (str "n" i))]
                            [{:op :add-node :node n :as a}
                             {:op :add-edge :edge {:type :ownership :tree :t :parent [] :to a :order i}}]))
                (range) nodes)))

(defn- default-nodes [] (mapv #(entry-node predicate-format %) default-predicates))

(defn- append-ops [store opts n]
  (if-let [[tid t] (relations-tree store)]
    [{:op :add-node :node n :as :n}
     {:op :add-edge :edge {:type :ownership :tree tid :parent [] :to :n :order (count (:children (:root t)))}}]
    (create-ops opts (conj (default-nodes) n))))

(defn add-predicate-plan
  "Plan adding predicate `{:name :axis :description}`. `opts` is
   `{:actor :timestamp :tree-id}` (`:tree-id`, a fresh ULID, is used only when
   the relations tree is created). Raises `:edges/invalid` on a bad name or
   axis and `:edges/conflict` on a duplicate."
  [_host store {:keys [name axis description] :as p} opts]
  (when-let [problems (seq (validate-predicate p))] (err/raise :edges/invalid (str/join " " problems) {:predicate name :problems (vec problems)}))
  (when (contains? (predicate-registry store) name) (err/raise :edges/conflict (str "Predicate '" name "' already exists.") {:predicate name}))
  (plan store opts (append-ops store opts (entry-node predicate-format {:name name :axis axis :description (or description "")}))))

(defn remove-predicate-plan
  "Plan removing predicate `n`; raises `:edges/missing` when unregistered."
  [_host store n opts]
  (predicate store n)
  (plan store opts
        (if-let [[tid _] (relations-tree store)]
          [{:op :detach :tree tid :at [(:position (first (filter #(and (= predicate-format (:format %)) (= n (:name (:value %)))) (entries store))))]}]
          (create-ops opts (mapv #(entry-node predicate-format %) (remove #(= n (:name %)) default-predicates))))))

(defn define-relation-type-plan
  "Plan defining (or redefining) a relation type. `t` takes `:name` and
   optionally `:direction` (`directed`|`undirected`), `:cardinality`
   (`one_to_one`|`one_to_many`|`many_to_one`|`many_to_many`), `:axis`,
   `:source-types`, `:target-types`, `:condition`, `:description`. Raises
   `:edges/invalid` on bad values, `:edges/conflict` for a builtin name;
   `{:changed false}` when identical."
  [_host store {:keys [name direction cardinality axis source-types target-types condition description]} opts]
  (let [v {"name" name "title" name "direction" (or direction "directed") "cardinality" (or cardinality "many_to_many")
           "axis" (or axis "") "source_types" (vec source-types) "target_types" (vec target-types)
           "condition" (or condition "") "description" (or description "")}
        cur (first (filter #(and (= relation-type-format (:format %)) (= name (get (:value %) "name"))) (entries store)))
        n (entry-node relation-type-format v)]
    (when-not (and (string? name) (re-matches #"[A-Za-z0-9_-]+" name)) (err/raise :edges/invalid (str "Relation type name '" name "' must contain only letters, numbers, '_' or '-'.") {:relation name}))
    (when-not (#{"directed" "undirected"} (v "direction")) (err/raise :edges/invalid (str "direction '" (v "direction") "' is not directed|undirected") {:relation name}))
    (when-not (#{"one_to_one" "one_to_many" "many_to_one" "many_to_many"} (v "cardinality")) (err/raise :edges/invalid (str "cardinality '" (v "cardinality") "' is not known") {:relation name}))
    (when (builtin-relation-types name) (err/raise :edges/conflict (str "Relation type '" name "' is builtin.") {:relation name}))
    (cond (and cur (= v (:value cur))) {:changed false}
          cur (plan store opts [{:op :add-node :node n :as :n} {:op :replace :tree (first (relations-tree store)) :at [(:position cur)] :new :n}])
          :else (plan store opts (append-ops store opts n)))))

(defn- context-node [relation] {:class :fact :kind :context :content {:name (str relation-context relation)}})
(defn- id-of [host n] (:id (node/make host (:class n) (:kind n) (:content n))))

(defn- export-ids
  "`{root-node-id \"Model:name\"}` of the tracked documents of `store`."
  [store]
  (into {} (keep #(when (:model %) [(:root %) (str (:model %) ":" (:name %))])) (docs/documents store)))

(defn authored
  "Authored relations of `store` with their anchor state:
   `[{:edge :relation :actor :state :from {:node :document :state} :to {…}}]`
   — `:document` is the `Model:name` of a tracked endpoint or nil, states are
   `:intact`/`:superseded`/`:orphan` (kernel anchor states)."
  [store]
  (let [obj #(get-in store [:objects %]) names (export-ids store)
        end (fn [a] {:node (:node a) :document (names (:node a)) :state (if (names (:node a)) (:state a) (if (= :intact (:state a)) :orphan (:state a)))})]
    (vec (sort-by (juxt :relation #(get-in % [:from :document]) #(get-in % [:to :document]) :edge)
                  (keep (fn [eid]
                          (let [e (obj eid) cn (get-in (obj (get-in e [:evidence :context])) [:content :name])]
                            (when (and (= :semantic (:type e)) (string? cn) (str/starts-with? cn relation-context))
                              (let [a (anchor/state store (:head store) eid) f (end (:from a)) t (end (:to a))]
                                {:edge eid :relation (subs cn (count relation-context)) :actor (get-in e [:evidence :actor])
                                 :from f :to t :state (max-key anchor/state-order (:state f) (:state t))}))))
                        (:edges store))))))

(defn- endpoint [store names token]
  (or (some (fn [[root x]] (when (= x token) root)) names)
      (:root (docs/find-document store token))
      (when (get-in store [:objects token]) token)
      (err/raise :edges/missing (str "'" token "' is not a tracked document") {:document token})))

(defn relate-plan
  "Plan authoring relation `:relation` from document `:from` to document `:to`
   (`Model:name` or a document name). The relation type must be defined
   (`:edges/missing` otherwise, as for an untracked endpoint);
   `{:changed false}` when the relation already stands."
  [host store {:keys [from to relation]} {:keys [actor] :as opts}]
  (when-not (contains? (stored-relation-types store) relation)
    (err/raise :edges/missing (str "Relation type '" relation "' is not defined.") {:relation relation}))
  (let [names (export-ids store) f (endpoint store names from) t (endpoint store names to) c (context-node relation)]
    (if (some #(and (= relation (:relation %)) (= f (get-in % [:from :node])) (= t (get-in % [:to :node]))) (authored store))
      {:changed false}
      (plan store opts [{:op :add-node :node c}
                        {:op :add-edge :edge {:type :semantic :from f :to t :evidence {:ref-hash t :actor actor :context (id-of host c)}}}]))))

(defn unrelate-plan
  "Plan removing the authored relation `{:from :to :relation}`; an endpoint may
   also be given as the root node id `authored` reports (for orphans). Raises
   `:edges/missing` when no such relation stands."
  [_host store {:keys [from to relation]} opts]
  (let [names (export-ids store) f (endpoint store names from) t (endpoint store names to)
        hits (filter #(and (= relation (:relation %)) (= f (get-in % [:from :node])) (= t (get-in % [:to :node]))) (authored store))]
    (when (empty? hits) (err/raise :edges/missing (str "no relation '" relation "' from '" from "' to '" to "'") {:relation relation :from from :to to}))
    (plan store opts (mapv (fn [h] {:op :remove-edge :edge (:edge h)}) hits))))

;; ---------------------------------------------------------------- anchors (v1 anchor_targets)

(defn- form-targets [form]
  (if-not (and (seq? form) (symbol? (first form)))
    []
    (let [[h a b] form nm #(when (or (symbol? %) (string? %)) (str %))]
      (case (name h)
        ("model" "where" "value" "create") (if (nm a) [(model-id (nm a))] [])
        ("relation" "assert") (if (nm a) [(relation-type-id (nm a))] [])
        "doc" (if (nm a) [(doc-id (nm a))] [])
        "field" (if (and (nm a) (nm b)) [(field-id (nm a) (nm b))] [])
        "move" (vec (mapcat form-targets (rest form)))
        ("change" "add" "remove" "clean" "forget")
        (if (and (seq? a) (#{"it" "them"} (str (first a))) (= "of" (str (second a))) (nm (nth a 2 nil)) (nm b))
          [(field-id (nm (nth a 2)) (nm b))] [])
        []))))

(defn anchor-targets
  "v1's `anchor_targets`: the node ids an anchor payload (`{\"ref\" …}`, plus
   `\"steps\"` for `compose:` refs) names — models, fields, relation types,
   documents; `[]` when the ref names nothing known."
  [payload]
  (let [ref (str/trim (str (get payload "ref"))) [head rest*] (str/split ref #":" 2)]
    (cond
      (str/starts-with? ref "(") (form-targets (try (edn/read-string ref) (catch #?(:clj Exception :cljs :default) _ nil)))
      (nil? rest*) []
      :else (case head
              "field" (let [[m f] (str/split rest* #"\." 2)] (if (and (seq m) (seq f)) [(field-id m f)] []))
              "action" (mapv (fn [[_ m f]] (field-id m f)) (re-seq #"\b([A-Za-z_]\w*)\.([A-Za-z_]\w*)=" rest*))
              "model" [(model-id rest*)]
              "predicate" [(model-id (first (str/split rest* #":" 2)))]
              "relation" [(relation-type-id rest*)]
              "doc" [(doc-id rest*)]
              "compose" (vec (keep #(when (map? %)
                                      (cond (seq (get % "model")) (model-id (get % "model"))
                                            (seq (get % "relation")) (relation-type-id (get % "relation"))))
                                   (get payload "steps")))
              []))))

;; ---------------------------------------------------------------- derivation

(defn- edge [from to rel origin & {:as m}] {:from from :to to :type rel :metadata (merge {"origin" origin} m)})
(defn- add-nodes [g pairs] (update g :nodes #(reduce (fn [ns [id attrs]] (if (contains? ns id) ns (assoc ns id attrs))) % pairs)))
(defn- add-edges [g es] (update g :edges into es))
(defn- tag-nodes [tags] (map (fn [t] [(tag-id t) {:type "semantic_tag" :semantics {"tag" t}}]) tags))
(defn- strs [m] (into (sorted-map) (map (fn [[k v]] [(if (keyword? k) (name k) (str k)) v])) m))

(defn- field-kind [t]
  (cond (vector? t) "array"
        :else (get {:string "string" :text "string" :bool "boolean" :int "integer" :float "number" :number "number" :map "object" :yaml "object"} t (name (or t :string)))))

(defn- norm-path [p]
  (let [parts (str/split (str/replace (str/replace (str/trim (str p)) "`" "") "\\" "/") #"/")]
    (str/join "/" (reduce (fn [acc x] (cond (or (= "" x) (= "." x)) acc
                                            (and (= ".." x) (seq acc)) (pop acc)
                                            :else (conj acc x))) [] parts))))

(defn- model-part [g {:keys [name descriptor]} by-name]
  (let [mid (model-id name) tags (sem/model-tags descriptor by-name) bases (vec (sort (set (:base descriptor))))]
    (-> g
        (add-nodes [[mid {:type "sldb_model" :semantics {"name" name "version" (:model/version descriptor) "family" (:py/family descriptor)
                                                         "semantic_tags" (vec tags) "base_models" bases}}]])
        (add-nodes (map (fn [f] [(field-id name (:name f)) {:type "sldb_field" :semantics {"name" (:name f) "model" name "kind" (field-kind (:type f))
                                                                                             "required" (boolean (:required f)) "description" (or (:description f) "")}}])
                        (:fields descriptor)))
        (add-nodes (tag-nodes tags))
        (add-edges (concat [(edge (store-id) mid "has_model" "structural")]
                           (map #(edge mid (tag-id %) "tagged_as" "structural") tags)
                           (map #(edge mid (field-id name (:name %)) "has_field" "schema") (:fields descriptor))
                           (map #(edge mid (model-id %) "extends" "schema") bases))))))

(defn- declared-edges [did descriptor payload]
  (let [containment (:py/containment descriptor)
        ids-in (fn [v] (keep #(when (and (string? %) (str/includes? (str/trim %) ":")) (str/trim %)) (if (sequential? v) v [v])))]
    (concat (for [[field allowed] containment id (ids-in (get payload field))]
              (if (seq allowed) (edge did (doc-id id) "contains" "schema" "field" field "allowed_models" (vec allowed))
                  (edge did (doc-id id) "contains" "schema" "field" field)))
            (for [field (:py/references descriptor) :when (not (contains? containment field)) id (ids-in (get payload field))]
              (edge did (doc-id id) "references" "schema" "field" field)))))

(defn- compose-edges [did descriptor payload owners]
  (for [[field spec] (or (:py/compositions descriptor) (:compositions descriptor))
        :let [spec (strs spec) src (get spec "source_field") v (when src (get payload src))]
        [i ref] (map-indexed vector (if (sequential? v) v (when v [v])))
        :when (string? ref)
        :let [ref (str/replace (str/trim ref) "`" "") target (get owners (norm-path ref) (str "sldb://unresolved_path/" ref))]]
    (edge did target "composes" "schema" (merge {"field" field "source_field" src "ref_path" ref "ref_index" i}
                                                 (when (get spec "model") {"composed_model" (last (str/split (str (get spec "model")) #":"))})))))

(defn- special-kind [doc descriptor tags payload]
  (let [is? (fn [n] (or (= n (:model doc)) (some #{n} (:base descriptor))))]
    (cond (nil? payload) nil
          (is? "RelationTypeDoc") :relation-type
          (is? "RelationDoc") :relation
          (and (some #{anchor-tag} tags) (get payload "symbol") (get payload "ref")) :anchor)))

(defn- doc-part [g store {:keys [descriptors tags owners registry links? excluded]} doc]
  (let [eid (str (:model doc) ":" (:name doc)) did (doc-id eid) d (descriptors (:model doc)) dtags (vec (sort (set (tags eid))))
        needs? (or (:py/references d) (:py/containment d) (:py/compositions d) (:compositions d)
                   (some #{"RelationTypeDoc" "RelationDoc"} (cons (:model doc) (:base d))) (some #{anchor-tag} dtags))
        payload (when needs? (:payload (docs/payload-of store doc)))]
    (if (some excluded dtags)
      (add-nodes g (tag-nodes dtags))
      (case (special-kind doc d dtags payload)
        :relation-type (let [n (get payload "name") rid (relation-type-id n)]
                         (-> g (add-nodes [[rid {:type "relation_type" :semantics (assoc (strs payload) "doc" (:name doc))}]])
                             (update :types assoc n (strs payload))
                             (add-edges (concat (map #(edge rid (model-id %) "applies_to_source" "schema") (get payload "source_types"))
                                                (map #(edge rid (model-id %) "applies_to_target" "schema") (get payload "target_types"))))))
        :relation (add-edges g [(edge (doc-id (get payload "source_id")) (doc-id (get payload "target_id")) (get payload "relation_type") "relation_doc"
                                      "relation_doc" (:name doc) "condition" (or (get payload "condition") "") "axis" "")])
        :anchor (let [aid (anchor-id (get payload "symbol"))]
                  (-> g (add-nodes [[aid {:type "anchor" :semantics (assoc (strs payload) "doc" (:name doc))}]])
                      (add-edges (map #(edge aid % "names" "alias") (anchor-targets payload)))))
        (let [secs (segments/sections (:source doc))]
          (-> g
              (add-nodes [[did {:type (:model doc) :semantics {"export_id" eid "name" (:name doc) "model" (:model doc) "path" (:path doc)
                                                               "semantic_tags" dtags "fingerprint" (:fingerprint doc) "state" (name (:state doc))}}]])
              (add-nodes (map (fn [s] [(section-id eid (:path s)) {:type "sldb_section" :semantics (merge (strs s) {"export_id" (str eid "#" (:path s)) "document_id" eid "semantic_tags" dtags})}]) secs))
              (add-edges (concat [(edge (model-id (:model doc)) did "has_document" "structural")]
                                 (map #(edge did (section-id eid (:path %)) "has_section" "structural") secs)
                                 (map #(edge did (tag-id %) "tagged_as" "structural") dtags)
                                 ;; v1: a section carries its document's tags (every section of v1's real index does)
                                 (for [s secs t dtags] (edge (section-id eid (:path s)) (tag-id t) "tagged_as" "structural"))
                                 (declared-edges did d payload)
                                 (compose-edges did d payload owners)
                                 (when links?
                                   (for [l (links/parse-links (:source doc) registry) :when (= :predicate-link (:kind l))]
                                     (edge did (as-node-id (:target l)) (:predicate l) "link" "predicate" (:predicate l) "axis" (:w5h1-type l))))))
              (add-nodes (tag-nodes dtags))))))))

(defn- resolve-edges
  "v1's compose step: relation documents inherit from their type and report a
   missing end; dangling `schema`/`alias`/`link` edges are dropped."
  [{:keys [nodes edges types] :as g}]
  (let [uniq (fn [name] (let [hits (filter #(= name (get-in (nodes %) [:semantics "name"])) (filter #(str/starts-with? % "sldb://document/") (keys nodes)))]
                          (when (= 1 (count hits)) (first hits))))
        out (reduce
             (fn [acc {:keys [from to type metadata] :as e}]
               (case (metadata "origin")
                 "relation_doc" (let [missing (cond (not (nodes from)) ["source" from] (not (nodes to)) ["target" to]) t (types type)]
                                  (if missing
                                    (update acc :problems conj (str "relation '" (metadata "relation_doc") "': " (first missing) " '"
                                                                    (subs (second missing) (count "sldb://document/")) "' is not a tracked document"))
                                    (let [e (assoc e :metadata (assoc metadata "condition" (if (seq (metadata "condition")) (metadata "condition") (get t "condition" ""))
                                                                      "axis" (get t "axis" "")))]
                                      (update acc :edges into (cons e (when (= "undirected" (get t "direction")) [(assoc e :from to :to from :metadata (assoc (:metadata e) "reverse" true))]))))))
                 ("schema" "alias") (if (nodes to) (update acc :edges conj e) acc)
                 "link" (if-let [to (if (nodes to) to (uniq (subs to (count "sldb://document/"))))] (update acc :edges conj (assoc e :to to)) acc)
                 (update acc :edges conj e)))
             {:edges [] :problems []} edges)]
    (assoc g :edges (:edges out) :problems (:problems out))))

(defn- authored-part [g store]
  (let [as (authored store)]
    (reduce (fn [g {:keys [relation from to state edge]}]
              (let [t (get (:types g) relation) f (some-> (:document from) doc-id) x (some-> (:document to) doc-id)]
                (if (and f x ((:nodes g) f) ((:nodes g) x))
                  (let [e {:from f :to x :type relation
                           :metadata {"origin" "authored" "edge" edge "anchor" (name state) "condition" (get t "condition" "") "axis" (get t "axis" "")}}]
                    (add-edges g (cons e (when (= "undirected" (get t "direction")) [(assoc e :from x :to f :metadata (assoc (:metadata e) "reverse" true))]))))
                  (let [[end ep] (if (and f ((:nodes g) f)) ["target" to] ["source" from])]
                    (update g :problems conj (str "relation '" relation "': " end " '" (or (:document ep) (:node ep)) "' is not a tracked document"))))))
            (assoc g :authored as) as)))

(defn index
  "The derived edge index of an opened `store`:
   `{:graph {:nodes {id {:type :semantics}} :edges [{:from :to :type :metadata}]}
     :problems [string] :relation-types {name type} :authored [...] :revision head}`.
   Edge metadata is string-keyed with v1's keys (`\"origin\"` is
   `structural`/`schema`/`alias`/`relation_doc`/`authored`/`link`).
   `opts`: `:exclude-tags` (documents carrying one contribute only their tag
   nodes) and `:links?` (also derive edges from predicate links in document
   text — off by default because v1's index has none)."
  ([store] (index store {}))
  ([store {:keys [exclude-tags links?]}]
   (let [ms (mstore/models store) by-name (into {} (map (juxt :name :descriptor)) ms)
         ds (filterv #(by-name (:model %)) (docs/documents store))
         dag (sem/dag store) stored (stored-relation-types store)
         tags (into {} (map (fn [e] [(str (:model e) ":" (:name e)) (:tags e)])) (sem/document-tags store))
         ctx {:descriptors by-name :tags tags :registry (predicate-registry store) :links? links? :excluded (set exclude-tags)
              :owners (reduce (fn [m d] (if (and (:path d) (not (contains? m (norm-path (:path d))))) (assoc m (norm-path (:path d)) (doc-id (str (:model d) ":" (:name d)))) m)) {} ds)}
         rtypes (merge builtin-relation-types stored)
         g (-> {:nodes {} :edges [] :types rtypes}
               (add-nodes [[(store-id) {:type "sldb_store" :semantics {}}]])
               (add-nodes (tag-nodes (distinct (concat (keys (:nodes dag)) (mapcat val (:nodes dag)) (keys (:equivalences dag)) (mapcat val (:equivalences dag))))))
               (add-edges (for [[t ps] (:nodes dag) p (sort ps)] (edge (tag-id t) (tag-id p) "semantic_parent" "structural")))
               (add-edges (for [[t es] (:equivalences dag) e (sort es)] (edge (tag-id t) (tag-id e) "semantic_equivalent" "structural")))
               (add-nodes (map (fn [[n t]] [(relation-type-id n) {:type "relation_type" :semantics t}]) rtypes))
               (add-edges (for [[n t] stored [k rel] [["source_types" "applies_to_source"] ["target_types" "applies_to_target"]] m (get t k)]
                            (edge (relation-type-id n) (model-id m) rel "schema"))))
         g (reduce #(model-part %1 %2 by-name) g (sort-by :name ms))
         g (reduce #(doc-part %1 store ctx %2) g (sort-by (juxt :model :name) ds))
         g (authored-part (resolve-edges g) store)]
     {:graph {:nodes (into (sorted-map) (:nodes g)) :edges (:edges g)}
      :problems (:problems g) :relation-types (:types g) :authored (:authored g) :revision (:head store)})))

(defn graph
  "The derived graph of `store` (`(:graph (index store opts))`)."
  ([store] (graph store {}))
  ([store opts] (:graph (index store opts))))

;; ---------------------------------------------------------------- readers

(defn edges-from
  "Edges of graph `g` leaving node `id` (an `sldb://` id or a `Model:name`),
   optionally only those of `relation`."
  ([g id] (edges-from g id nil))
  ([g id relation] (let [id (as-node-id id)] (filterv #(and (= id (:from %)) (or (nil? relation) (= relation (:type %)))) (:edges g)))))

(defn edges-to
  "Edges of graph `g` arriving at node `id`, optionally only of `relation`."
  ([g id] (edges-to g id nil))
  ([g id relation] (let [id (as-node-id id)] (filterv #(and (= id (:to %)) (or (nil? relation) (= relation (:type %)))) (:edges g)))))

(defn node
  "Node `id` of `g` with its facets: `{:id :type :semantics :facets}`, or nil.
   The only facet is `\"source\"` (`{\"generated_from\" \"kimun.surface.edges\"}`)."
  [g id]
  (let [id (as-node-id id)]
    (when-let [n (get-in g [:nodes id])]
      (assoc n :id id :facets (merge {"source" {"generated_from" "kimun.surface.edges"}} (:facets n))))))

(defn nodes-of-type
  "Nodes of `g` whose type is `t` (`node` shape), sorted by id."
  [g t]
  (vec (keep #(when (= t (:type (val %))) (node g (key %))) (sort-by key (:nodes g)))))

;; ---------------------------------------------------------------- check

(defn- py-list [xs] (str "[" (str/join ", " (map #(str "'" % "'") xs)) "]"))

(defn- cycle-of [edges rel]
  (let [adj (reduce (fn [m e] (if (= rel (:type e)) (update m (:from e) (fnil conj (sorted-set)) (:to e)) m)) (sorted-map) edges)
        done (volatile! #{})
        walk (fn walk [path n]
               (cond (some #{n} path) (vec (drop-while #(not= n %) path))
                     (@done n) nil
                     :else (or (some #(walk (conj path n) %) (adj n)) (do (vswap! done conj n) nil))))]
    (some #(walk [] %) (keys adj))))

(defn check
  "v1's `edges check` over an `index`: `{:ok bool :errors [string]
   :violations [{:kind :message …}] :anchors [authored relation, not intact]}`.
   Kinds: `:relation-endpoint` (a relation whose end is not a tracked
   document), `:unknown-relation`, `:missing-endpoint`, `:endpoint-type`,
   `:cardinality`, `:cycle`, `:anchor`. `:errors` are v1's sentences."
  [{:keys [problems authored] {:keys [nodes edges]} :graph types :relation-types}]
  (let [bases #(get-in nodes [(model-id %) :semantics "base_models"])
        allowed? (fn [allowed ntype] (or (empty? allowed) (and (some #{"sldb_document"} allowed) (nodes (model-id ntype))) (some (set allowed) (cons ntype (bases ntype)))))
        per-edge (mapcat (fn [{:keys [from to type metadata] :as e}]
                           (let [where (str "edge " from " -[" type "]-> " to) t (types type) v (fn [k m] {:kind k :edge e :message (str where ": " m)})]
                             (cond (and (nil? t) (= "link" (metadata "origin"))) []
                                   (nil? t) [(v :unknown-relation (str "unknown relation type '" type "'"))]
                                   (not (and (nodes from) (nodes to))) [(v :missing-endpoint "target does not exist")]
                                   :else (keep (fn [[end id k]] (let [nt (:type (nodes id))]
                                                                  (when-not (allowed? (get t k) nt)
                                                                    (v :endpoint-type (str end " class '" nt "' not in " k " " (py-list (get t k)))))))
                                               (if (metadata "reverse") [["source" to "source_types"] ["target" from "target_types"]]
                                                   [["source" from "source_types"] ["target" to "target_types"]])))))
                         edges)
        card (for [[rel es] (sort-by key (group-by :type edges)) :let [c (get (types rel) "cardinality" "many_to_many")]
                   [dir k ok-cards] [["targets" :from #{"one_to_one" "many_to_one"}] ["sources" :to #{"one_to_one" "one_to_many"}]]
                   :when (ok-cards c) [id n] (sort (frequencies (map k es))) :when (> n 1)]
               {:kind :cardinality :node id :relation rel :message (str id " has " n " '" rel "' " dir " but cardinality is " c)})
        cycles (keep (fn [rel] (when-let [c (cycle-of edges rel)]
                                 {:kind :cycle :relation rel :cycle c :message (str "Relation '" rel "' has a cycle: " (str/join " -> " (conj c (first c))) ".")}))
                     ["semantic_parent" "extends"])
        anchors (filterv #(not= :intact (:state %)) authored)
        vs (vec (concat (map (fn [p] {:kind :relation-endpoint :message p}) problems) per-edge card cycles
                        (keep #(when (and (get-in % [:from :document]) (get-in % [:to :document]))
                                 {:kind :anchor :edge (:edge %) :message (str "relation '" (:relation %) "' " (get-in % [:from :document]) " -> " (get-in % [:to :document]) " is " (name (:state %)))})
                              anchors)))]
    {:ok (empty? vs) :errors (mapv :message vs) :violations vs :anchors anchors}))
