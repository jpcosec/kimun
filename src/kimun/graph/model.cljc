(ns kimun.graph.model
  "The in-memory graph value: a directed, typed multi-graph on plain data.

   `{:nodes {id {:type \"Spec\" :semantics {...} :facets {...}}}
     :edges [{:from id :to id :type \"implements\" :metadata {...}}]}`

   Node attrs are free-form; the engine reads `:type`, `:semantics` and
   `:facets`. Nothing is derived and stored: adjacency, relation views and the
   collapsed simple digraph are computed on demand from this value."
  (:require [clojure.set :as set]
            [clojure.string :as str]))

(def derived-relations
  "Relations the edge index itself writes from the store's structure (v1
   BUILTIN_RELATION_TYPES). Analyses skip them unless asked, because they make
   every pair of documents neighbours."
  #{"has_model" "has_document" "has_section" "tagged_as" "semantic_parent"
    "semantic_equivalent" "has_field" "extends" "applies_to_source"
    "applies_to_target" "names" "references" "contains" "composes"})

(def all-relations
  "Sentinel for the `relations` argument meaning every relation present."
  "*")

(defn graph
  "Builds a graph from a node map (id -> attrs) and a seq of edges. Edges are
   `{:from :to :type}` maps (extra keys kept); order is preserved."
  ([nodes edges] {:nodes (into {} nodes) :edges (vec edges)})
  ([] (graph {} [])))

(defn from-triples
  "A graph holding exactly the `[from type to]` triples; every endpoint becomes
   a node of `:type` `default-type` unless `types` (id -> type) says otherwise."
  ([triples] (from-triples triples {} "Doc"))
  ([triples types default-type]
   (let [ids (into (sorted-set) (mapcat (fn [[f _ t]] [f t])) triples)]
     (graph (map (fn [i] [i {:type (get types i default-type)}]) ids)
            (map (fn [[f t* to]] {:from f :to to :type t*}) triples)))))

(defn add-node
  "Adds (or replaces) node `id` with `attrs`."
  [g id attrs]
  (assoc-in g [:nodes id] attrs))

(defn add-edge
  "Appends the edge `from -[type]-> to`, with optional extra `attrs`."
  ([g from type to] (add-edge g from type to {}))
  ([g from type to attrs]
   (update g :edges conj (merge attrs {:from from :to to :type type}))))

(defn node-ids
  "All node ids, sorted."
  [g]
  (vec (sort (keys (:nodes g)))))

(defn node-type
  "The `:type` of node `id`, or nil when absent."
  [g id]
  (get-in g [:nodes id :type]))

(defn has-node?
  "True when `id` is a node of `g`."
  [g id]
  (contains? (:nodes g) id))

(defn live-edges
  "Edges whose both endpoints are nodes of `g` (dangling ones are ignored by
   every analysis, as in v1)."
  [g]
  (filterv (fn [e] (and (has-node? g (:from e)) (has-node? g (:to e)))) (:edges g)))

(defn edges-from
  "Edges leaving `id`, optionally only those of relation `type`."
  ([g id] (filterv #(= id (:from %)) (:edges g)))
  ([g id type] (filterv #(and (= id (:from %)) (= type (:type %))) (:edges g))))

(defn edges-to
  "Edges arriving at `id`, optionally only those of relation `type`."
  ([g id] (filterv #(= id (:to %)) (:edges g)))
  ([g id type] (filterv #(and (= id (:to %)) (= type (:type %))) (:edges g))))

(defn adjacency
  "`{:out {id [edge]} :in {id [edge]}}` over the edges of `g`, built once so
   walks do not rescan the edge list."
  [g]
  {:out (group-by :from (:edges g))
   :in (group-by :to (:edges g))})

(defn present
  "The set of relation types that appear on some edge."
  [g]
  (into #{} (map :type) (:edges g)))

(defn authored
  "Relation types present that are not derived structure."
  [g]
  (set/difference (present g) derived-relations))

(defn resolve-relations
  "Relations an analysis walks: `\"*\"` is all present; a collection is taken
   as given; nil means the authored ones, falling back to all present when the
   graph has none (v1 `relations.resolve`)."
  [g relations]
  (cond
    (= relations all-relations) (present g)
    (some? relations) (set relations)
    :else (let [a (authored g)] (if (seq a) a (present g)))))

(defn view
  "The projection analyses run on: nodes restricted to `node-types` (nil keeps
   all) and edges restricted to the resolved `relations` and to surviving
   endpoints. Duplicate `(from, to, type)` edges collapse to one."
  ([g relations] (view g relations nil))
  ([g relations node-types]
   (let [kept (resolve-relations g relations)
         wanted (some-> node-types set)
         nodes (into {} (filter (fn [[_ a]] (or (nil? wanted) (contains? wanted (:type a))))) (:nodes g))
         sub (graph nodes (:edges g))]
     (assoc sub :edges
            (->> (live-edges sub)
                 (filter #(contains? kept (:type %)))
                 (reduce (fn [[seen out] e]
                           (let [k [(:from e) (:to e) (:type e)]]
                             (if (seen k) [seen out] [(conj seen k) (conj out e)])))
                         [#{} []])
                 second)))))

(defn collapsed
  "The simple digraph of a view: `{:nodes [sorted ids] :succ {id [sorted ids]}
   :pred {id [sorted ids]} :weight {[from to] n}}` where weight is the number of
   distinct relations between the pair (v1 `collapsed`)."
  ([g relations] (collapsed g relations nil))
  ([g relations node-types]
   (let [v (view g relations node-types)
         w (frequencies (map (juxt :from :to) (:edges v)))
         ids (node-ids v)
         side (fn [key-of val-of]
                (let [m (reduce (fn [m pair] (update m (key-of pair) (fnil conj #{}) (val-of pair))) {} (keys w))]
                  (into {} (map (fn [i] [i (vec (sort (get m i)))])) ids)))]
     {:nodes ids :succ (side first second) :pred (side second first) :weight w})))

(defn undirected-neighbors
  "Sorted neighbours of `id` in a `collapsed` digraph, ignoring direction."
  [c id]
  (vec (sort (set/union (set (get-in c [:succ id])) (set (get-in c [:pred id]))))))

(defn relation-label
  "A readable `a -> b -> c` rendering of a path or cycle."
  [ids]
  (str/join " -> " ids))
