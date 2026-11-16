(ns kimun.graph.export
  "The semantic export (v1 `export.py`): the `sldb_kgdb_semantic_export`
   payload kimun emits from an opened store, and its reading as a graph
   snapshot. The graph side of `kimun.surface.semantic`, whose tag DAG it
   reads (docs/v2/03 §1: the base does not require the graph package)."
  (:require [kimun.graph.io :as gio]
            [kimun.surface.docs :as docs]
            [kimun.surface.models.store :as mstore]
            [kimun.surface.semantic :as sem]))

(def contract-version "Version of the `sldb_kgdb_semantic_export` contract kimun emits." 2)

(defn export
  "The `sldb_kgdb_semantic_export` payload, version 2 (string keys). `opts`:
   `:generated-at`, `:version` (kimun's), `:command` (argv), `:store-path`
   (relative to the project root; nothing absolute is emitted). `sections`
   is empty until the section index lands."
  [store {:keys [generated-at version command store-path]}]
  (let [models (mstore/models store) bases (into {} (map (juxt :name :descriptor)) models) g (sem/dag store)
        tags (into {} (map (juxt :name :tags)) (sem/document-tags store))]
    {"contract" {"name" gio/contract-name "version" contract-version "generated_at" generated-at}
     "producer" {"name" "kimun" "version" version "command" (vec command)}
     "store" {"root" "." "store_path" store-path "store_hash" (:head store)}
     "models" (vec (for [m (sort-by :name models)]
                     {"name" (:name m) "version" (:version m) "model_hash" (:id m)
                      "semantics" (sem/model-tags (:descriptor m) bases) "base_models" (vec (sort (set (:base (:descriptor m)))))}))
     "documents" (vec (for [d (sort-by (juxt :model :name) (docs/documents store)) :when (get bases (:model d))
                            :let [full (docs/document store (:tree d))]]
                        {"id" (str (:model d) ":" (:name d)) "name" (:name d) "model" (:model d) "path" (:path d)
                         "source_hash" (:source-hash full) "content_hash" (:content-hash full)
                         "semantic_tags" (get tags (:name d) [])}))
     "sections" []
     "semantic_dag" {"nodes" (mapv (fn [[t ps]] {"id" t "parents" (vec ps)}) (:nodes g))
                     "equivalences" (into (sorted-map) (map (fn [[k v]] [k (vec v)])) (:equivalences g))}}))

(defn export->snapshot
  "`kimun.graph.io/semantic-export->snapshot` for a version 1 or
   version 2 export: a v2 payload is read through the v1 reader (its semantic
   hash names aliased to v1's letters) and keeps its own contract in the
   snapshot metadata."
  [p]
  (if (= contract-version (get-in p ["contract" "version"]))
    (let [alias (fn [m from to] (cond-> m (contains? m from) (assoc to (get m from))))
          v1 (-> p (assoc-in ["contract" "version"] gio/contract-version)
                 (update "store" alias "store_hash" "hash_a")
                 (update "models" (partial mapv #(alias % "model_hash" "hash_b")))
                 (update "documents" (partial mapv #(-> % (alias "source_hash" "hash_c") (alias "content_hash" "hash_d")))))]
      (assoc-in (gio/semantic-export->snapshot v1) ["metadata" "source_contract"] (get p "contract")))
    (gio/semantic-export->snapshot p)))
