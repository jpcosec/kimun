(ns kimun.surface.graph.store
  "Where the graph a `graph` command reads comes from, and the one shape it
   has whatever the source (v1 `store/graph/convert.py` and the promise of
   `tests/api/test_graph_source_parity.py`): the derived view of an opened
   store (`kimun.surface.edges/graph`), a node-link or native snapshot file,
   or a semantic export. Every source is brought to the *canonical* graph —
   each node `{:type :semantics :facets}`, each edge `{:from :to :type
   :metadata}` — so a read answers the same from the live store and from the
   snapshot saved from it. Pure: no I/O."
  (:require [kimun.surface.edges :as edges]
            [kimun.surface.graph.io :as io]
            [kimun.surface.graph.model :as m]
            [kimun.surface.semantic :as sem]
            [kimun.kernel.err :as err]))

(defn canonical
  "Graph `g` with every node carrying `:type`, `:semantics` and `:facets`
   (maps, possibly empty) and every edge exactly `:from :to :type :metadata`."
  [g]
  (m/graph (map (fn [[id a]] [id {:type (:type a) :semantics (or (:semantics a) {}) :facets (or (:facets a) {})}])
                (:nodes g))
           (map (fn [e] {:from (:from e) :to (:to e) :type (:type e) :metadata (or (:metadata e) {})})
                (:edges g))))

(defn from-store
  "The canonical graph derived from an opened `store`. Each node carries the
   facets `kimun.surface.edges/node` gives it, so they travel in a snapshot.
   `opts`: `:links?` (predicate links found in document text become edges;
   default true — the CLI view) and `:exclude-tags`."
  ([store] (from-store store {}))
  ([store opts]
   (let [g (edges/graph store (merge {:links? true} opts))]
     (canonical (update g :nodes (fn [ns] (into {} (map (fn [[id _]] [id (dissoc (edges/node g id) :id)])) ns)))))))

(defn snapshot
  "The node-link map of `g` (`kimun.surface.graph.io/to-node-link`): what
   `graph export` prints and `graph snapshot save` writes."
  [g]
  (io/to-node-link (canonical g)))

(defn from-semantic-export
  "The canonical graph of a semantic export payload, contract version 1 or 2
   (`kimun.surface.semantic/export->snapshot`). Raises `:graph/bad-export`."
  [payload]
  (canonical (io/snapshot->graph (sem/export->snapshot payload))))

(defn from-snapshot
  "The canonical graph of parsed JSON `data`: a node-link map, a native
   snapshot, or (a map with `contract`) a semantic export. Raises
   `:graph/invalid` when `data` is none of them."
  [data]
  (cond
    (and (map? data) (contains? data "contract")) (from-semantic-export data)
    (and (map? data) (sequential? (get data "nodes")) (every? map? (get data "nodes")))
    (canonical (io/from-node-link data))
    :else (err/raise :graph/invalid "Not a graph snapshot: expected a JSON object with `nodes`" {})))

(defn node-id
  "The id of an existing node of `g` named `id` (an `sldb://` id or a
   `Model:name`). Raises `:graph/missing` when `g` has no such node."
  [g id]
  (let [nid (edges/as-node-id id)]
    (if (m/has-node? g nid)
      nid
      (err/raise :graph/missing (str "No node " id " in the graph") {:node id}))))

(defn node
  "Node `id` of `g` as `{:id :type :semantics :facets :edges}`, `:edges` being
   the edges leaving it. Raises `:graph/missing`."
  [g id]
  (let [nid (node-id g id)]
    (assoc (get-in g [:nodes nid]) :id nid :edges (filterv #(= nid (:from %)) (:edges g)))))

(defn ids
  "Sorted node ids of `g`, only those whose type is in `types` when given."
  ([g] (ids g nil))
  ([g types]
   (let [keep? (if (seq types) (comp (set types) :type val) (constantly true))]
     (vec (sort (map key (filter keep? (:nodes g))))))))

(defn summary
  "Counts of `g`: `{:nodes n :edges n :types {type n} :relations {relation n}}`."
  [g]
  {:nodes (count (:nodes g)) :edges (count (:edges g))
   :types (into (sorted-map) (frequencies (map (comp str :type val) (:nodes g))))
   :relations (into (sorted-map) (frequencies (map :type (:edges g))))})
