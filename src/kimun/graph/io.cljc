(ns kimun.graph.io
  "The portable node-link snapshot. Conversions are pure over maps with string
   keys (the JSON shape); `cheshire` is touched only by `read-json` and
   `write-json`. Also converts an sldb semantic export (contract
   `sldb_kgdb_semantic_export` v1) to a native snapshot and to a graph."
  (:require [cheshire.core :as json]
            [kimun.kernel.err :as err]
            [kimun.graph.model :as m]))

(defn to-node-link
  "The node-link map of `g`: `{\"directed\" true \"multigraph\" false \"graph\" {}
   \"nodes\" [{\"id\" \"type\" \"status\" \"semantics\" \"facets\"}] \"links\"
   [{\"source\" \"target\" \"relation\" \"metadata\"}]}`, nodes sorted by id,
   links in edge order. `status` is the compliance facet's status or \"unknown\"."
  [g]
  {"directed" true "multigraph" false "graph" {}
   "nodes" (mapv (fn [id]
                   (let [a (get-in g [:nodes id])]
                     {"id" id "type" (:type a)
                      "status" (or (get-in a [:facets "compliance" "status"]) "unknown")
                      "semantics" (or (:semantics a) {}) "facets" (or (:facets a) {})}))
                 (m/node-ids g))
   "links" (mapv (fn [e] {"source" (:from e) "target" (:to e) "relation" (:type e)
                          "metadata" (or (:metadata e) {})})
                 (:edges g))})

(defn- native-node
  "A v1 KnowledgeNode map (identity, edges, facets) as `[id attrs edges]`."
  [n]
  (let [id (get-in n ["identity" "node_id"])
        facets (apply dissoc n ["identity" "edges" "semantics"])]
    [id {:type (get-in n ["identity" "node_type"]) :semantics (or (get n "semantics") {})
         :facets (into {} (remove (comp nil? val)) facets)}
     (mapv (fn [e] {:from id :to (get e "target_id") :type (get e "relation_type")
                    :metadata (or (get e "metadata") {})})
           (get n "edges"))]))

(defn- link-node
  [entry]
  (let [schema (get entry "schema")]
    [(get entry "id")
     (if schema
       (second (native-node schema))
       {:type (get entry "type" "concept") :semantics (or (get entry "semantics") {})
        :facets (or (get entry "facets") {})})]))

(defn from-node-link
  "A graph from a node-link map (`links` or `edges` key; v1 `schema` entries
   accepted) or from a native snapshot (`nodes` carrying `identity`/`edges`,
   no `links`)."
  [data]
  (let [nodes (get data "nodes")
        links (or (get data "links") (get data "edges"))]
    (if (and (nil? links) (some #(contains? % "identity") nodes))
      (let [parsed (map native-node nodes)]
        (m/graph (map (juxt first second) parsed) (mapcat #(nth % 2) parsed)))
      (m/graph (map link-node nodes)
               (map (fn [l] {:from (get l "source") :to (get l "target") :type (get l "relation")
                             :metadata (or (get l "metadata") {})})
                    links)))))

(defn snapshot->graph
  "A graph from a native snapshot map (alias of `from-node-link` for snapshots)."
  [snapshot]
  (from-node-link snapshot))

(defn read-json
  "Parses JSON text to a string-keyed map (the only place cheshire reads)."
  [text]
  (json/parse-string text))

(defn write-json
  "Pretty JSON text for a JSON-compatible map."
  [data]
  (json/generate-string data {:pretty true}))

;; ------------------------------------------------------ semantic export

(def contract-name
  "The semantic-export contract this module reads."
  "sldb_kgdb_semantic_export")

(def contract-version
  "The semantic-export contract version this module reads."
  1)

(defn- edge [target rel] {"target_id" target "relation_type" rel "metadata" {}})

(defn- node
  [id type edges semantics source]
  {"identity" {"node_id" id "node_type" type} "edges" (vec edges) "semantics" semantics "source" source})

(defn- dag-tags
  [dag]
  (into (into #{} (mapcat (fn [n] (cons (get n "id") (get n "parents")))) (get dag "nodes"))
        (mapcat (fn [[t eqs]] (cons t eqs)) (get dag "equivalences"))))

(defn- collect-tags
  [p]
  (into (dag-tags (get p "semantic_dag"))
        (concat (mapcat #(get % "semantics") (get p "models"))
                (mapcat #(get % "semantic_tags") (get p "documents"))
                (mapcat #(get % "semantic_tags") (get p "sections")))))

(defn- tag-node
  [p prov tag]
  (let [dag (get p "semantic_dag")
        parents (some #(when (= tag (get % "id")) (get % "parents")) (get dag "nodes"))]
    (node (m/tag-id tag) "semantic_tag"
          (concat (map #(edge (m/tag-id %) "semantic_parent") parents)
                  (map #(edge (m/tag-id %) "semantic_equivalent") (get-in dag ["equivalences" tag])))
          {"tag" tag} prov)))

(defn- model-node
  [p prov model]
  (let [docs (filter #(= (get model "name") (get % "model")) (get p "documents"))]
    (node (m/model-id (get model "name")) "sldb_model"
          (concat (map #(edge (m/doc-id (get % "id")) "has_document") docs)
                  (map #(edge (m/tag-id %) "tagged_as") (get model "semantics")))
          (-> (select-keys model ["name" "model_ref" "path" "version" "canonical" "family" "base_models" "hash_b"])
              (assoc "semantic_tags" (get model "semantics")))
          (assoc prov "model" model "parent_store_node_id" (m/store-id)))))

(defn- document-node
  [p prov d]
  (let [secs (filter #(= (get d "id") (get % "document_id")) (get p "sections"))]
    (node (m/doc-id (get d "id")) "sldb_document"
          (concat (map #(edge (m/section-id (get % "id")) "has_section") secs)
                  (map #(edge (m/tag-id %) "tagged_as") (get d "semantic_tags")))
          (-> (select-keys d ["name" "model" "path" "semantic_tags" "hash_c" "hash_d"])
              (assoc "export_id" (get d "id")))
          (assoc prov "document" d))))

(defn- section-node
  [prov s]
  (node (m/section-id (get s "id")) "sldb_section"
        (map #(edge (m/tag-id %) "tagged_as") (get s "semantic_tags"))
        (-> (select-keys s ["path" "title" "breadcrumbs" "about" "semantic_tags" "slug" "level" "line_start" "line_end"])
            (assoc "export_id" (get s "id") "document_id" (get s "document_id")))
        (assoc prov "section" s)))

(defn semantic-export->snapshot
  "Converts an sldb semantic export (string-keyed map) to a native snapshot
   `{\"version\" \"1.0\" \"nodes\" [...] \"metadata\" {...}}`: the store node,
   the tags (sorted), then models, documents and sections in export order.
   Raises `:graph/bad-export` when the contract name/version do not match."
  [p]
  (let [c (get p "contract")]
    (when-not (and (= contract-name (get c "name")) (= contract-version (get c "version")))
      (err/raise :graph/bad-export (str "Expected " contract-name " version " contract-version) {:contract c}))
    (let [store (get p "store")
          prov {"contract" c "producer" (get p "producer")
                "store" {"root" (get store "root") "store_path" (get store "store_path")
                         "hash_a" (get store "hash_a") "runtime_sources" (get store "runtime_sources" {})}}]
      {"version" "1.0"
       "metadata" {"source_contract" c "producer" (get p "producer") "store" store "generated_from" contract-name}
       "nodes" (vec (concat
                     [(node (m/store-id) "sldb_store"
                            (map #(edge (m/model-id (get % "name")) "has_model") (get p "models"))
                            {"root" (get store "root") "store_path" (get store "store_path") "hash_a" (get store "hash_a")}
                            prov)]
                     (map #(tag-node p prov %) (sort (collect-tags p)))
                     (map #(model-node p prov %) (get p "models"))
                     (map #(document-node p prov %) (get p "documents"))
                     (map #(section-node prov %) (get p "sections"))))})))

(defn semantic-export->graph
  "A graph straight from a semantic export (`semantic-export->snapshot` then
   `snapshot->graph`)."
  [p]
  (snapshot->graph (semantic-export->snapshot p)))
