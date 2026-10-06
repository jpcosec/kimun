(ns kimun.surface.index.build
  "The derived index of a store (docs/v2/06 §6): one pure map computed from an
   opened store, reconstructible from `(store, head)` alone and never
   authoritative (02 §10 inv. 10) — every value in it is read again from the
   store, so deleting it loses nothing.

   `build` does no I/O and is deterministic: the same store gives an equal
   map, every vector of ids or tags is sorted, and the map survives
   `(clojure.edn/read-string (pr-str idx))` unchanged (strings, keywords,
   numbers, vectors, maps and sets only). `kimun.surface.index.cache` is the
   only place that reads or writes it on disk."
  (:require [kimun.surface.docs :as tdocs]
            [kimun.surface.models.extract :as extract]
            [kimun.surface.models.segments :as segments]
            [kimun.surface.models.store :as mstore]
            [kimun.surface.semantic :as sem]))

(def version
  "Format version of the index map (its `:version`). A cached index of another
   version is discarded and rebuilt."
  1)

(def contract-keys
  "The keys every index map carries (docs/v2/06 §6), plus `:version`."
  [:version :head :models :docs :by-model :by-name :tags :tag-dag :equivalences :sections :links :anchors])

(defn- lineage
  "Names of every base of `descriptor`, nearest first (as
   `kimun.surface.semantic` linearises them); `bases` is `{name descriptor}`."
  [descriptor bases]
  (loop [todo (vec (:base descriptor)) seen []]
    (if-let [b (first todo)]
      (if (some #{b} seen)
        (recur (subvec todo 1) seen)
        (recur (into (subvec todo 1) (:base (get bases b))) (conj seen b)))
      seen)))

(defn- model-entry [bases {:keys [name version id position descriptor]}]
  {:name name :version version :id id :position position
   :base (lineage descriptor bases)
   :semantics (:semantics descriptor)
   :fields (vec (:fields descriptor))
   :template (:template descriptor)
   :tags (sem/model-tags descriptor bases)})

(defn- extraction
  "`{:payload p}` of `source` under `descriptor`, or `{:error message}` when
   the model is gone or the source no longer extracts (never raises)."
  [descriptor model source]
  (if-not descriptor
    {:error (str "model `" model "` is not defined")}
    (try {:payload (extract/extract descriptor source)}
         (catch #?(:clj Exception :cljs :default) e {:error (or (ex-message e) (str e))}))))

(defn- doc-entry [host bases models d]
  (let [descriptor (get bases (:model d))
        {:keys [payload error]} (extraction descriptor (:model d) (:source d))
        base {:tree (:tree d) :doc (:doc d) :name (:name d) :model (:model d)
              :model-version (:model-version d)
              :bases (get-in models [(:model d) :base] [])
              :tags (if descriptor (sem/tags-of descriptor bases {:payload payload}) [])
              :hash-d (tdocs/text-hash host (or (:source d) ""))
              :path (:path d) :source (:source d) :state (:state d)}]
    (if error
      (assoc base :error error)
      (assoc base :payload payload
             :hash-c (tdocs/content-hash host payload)
             :field-hashes (tdocs/field-hashes host payload)))))

(defn- grouped
  "`{(k doc) [tree-id … sorted]}` over `docs`, `k` returning one key or a seq of keys."
  [k docs]
  (->> docs
       (reduce (fn [m d] (let [ks (k d)] (reduce #(update %1 %2 (fnil conj (sorted-set)) (:tree d)) m (if (coll? ks) ks [ks]))))
               (sorted-map))
       (into (sorted-map) (map (fn [[x ids]] [x (vec ids)])))))

(defn- symmetric
  "`{tag [tag … sorted]}` of the declared equivalences read in both directions."
  [equivalences]
  (->> (for [[a bs] equivalences b bs pair [[a b] [b a]]] pair)
       (reduce (fn [m [a b]] (update m a (fnil conj (sorted-set)) b)) (sorted-map))
       (into (sorted-map) (map (fn [[t ts]] [t (vec ts)])))))

(defn- model-sections [descriptor]
  (let [ss (vec (segments/sections (or (:template descriptor) "")))]
    {:sections ss :owners (into (sorted-map) (segments/field-owners descriptor ss))}))

(defn build
  "The index of the opened kernel `store` (what `sldb.kernel.store/open`
   returns, i.e. the `:store` of `kimun.cli.store/open!`). Pure, deterministic,
   EDN round-trippable. Shape (all maps keyed by strings unless said):

   - `:version` — `version`; `:head` — id of the head revision, or nil;
   - `:models {name {:name :version :id :position :base [lineage, nearest
     first] :semantics :fields :template :tags [model tags]}}`;
   - `:docs {tree-id entry}`, one per tracked typed document: `{:tree :doc
     :name :model :model-version :bases [model lineage, nearest first] :tags
     [sorted] :hash-d :path :source :state}` plus, when the source extracts
     under the model's current contract, `:payload`, `:hash-c` and
     `:field-hashes`; otherwise `:error \"message\"` and none of those three
     (the model is gone, or the text no longer satisfies it). An entry is a
     valid argument of `kimun.surface.index.where/matches?`;
   - `:by-model {model [tree-id …]}`, `:by-name {doc-name tree-id}`,
     `:tags {tag [tree-id …]}` (the tags each document carries, not their
     ancestors), every vector sorted;
   - `:tag-dag` — exactly `(kimun.surface.semantic/dag store)`:
     `{:nodes {tag #{parent}} :equivalences {tag #{tag}}}`, usable by
     `semantic/in-scope?`, `query-scope`, `list-tags`, `glob-tags`;
   - `:equivalences {tag [tag …]}` — the declared equivalences read in both
     directions, sorted;
   - `:sections {model {:sections [segments/sections of the template]
     :owners {field section-path}}}`;
   - `:links {}` and `:anchors {}` — reserved, filled by another surface."
  [store]
  (let [host (:host store)
        entries (mstore/models store)
        bases (into {} (map (juxt :name :descriptor)) entries)
        models (into (sorted-map) (map (fn [m] [(:name m) (model-entry bases m)])) entries)
        docs (mapv #(doc-entry host bases models %) (tdocs/documents store))
        dag (sem/dag-of (mapcat :tags docs) (sem/declared store))]
    {:version version
     :head (:head store)
     :models models
     :docs (into (sorted-map) (map (juxt :tree identity)) docs)
     :by-model (grouped :model (filter :model docs))
     :by-name (into (sorted-map) (map (juxt :name :tree)) (reverse docs))
     :tags (grouped :tags docs)
     :tag-dag dag
     :equivalences (symmetric (:equivalences dag))
     :sections (into (sorted-map) (map (fn [m] [(:name m) (model-sections (:descriptor m))])) entries)
     :links {}
     :anchors {}}))

(defn skipped
  "Sorted names of the documents of index `idx` that carry `:error` (indexed
   by name, model and source only: no payload, no content hash)."
  [idx]
  (vec (sort (keep #(when (:error %) (:name %)) (vals (:docs idx))))))

(defn fingerprint
  "The store as one value (v1 `store_fingerprint`), read from index `idx`:
   `{:store head :models {name descriptor-node-id} :documents {\"Model:name\"
   {:hash-d source-hash :hash-c content-hash-or-nil}}}`."
  [idx]
  {:store (:head idx)
   :models (into (sorted-map) (map (fn [[n m]] [n (:id m)])) (:models idx))
   :documents (into (sorted-map) (map (fn [d] [(str (:model d) ":" (:name d)) {:hash-d (:hash-d d) :hash-c (:hash-c d)}]))
                    (vals (:docs idx)))})
