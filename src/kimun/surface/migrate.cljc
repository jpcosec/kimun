(ns kimun.surface.migrate
  "Migration from an sldb v1 store (`.sldb/`) to kimun, the pure half: reads
   the v1 layout through injected file functions into a plan of work (models
   to define, documents to track, links to other stores) and folds the
   per-item outcomes of a run into a report. No path in a plan is absolute:
   v1 paths are kept as written, relative to the v1 project root
   (docs/v2/tests/parity-v1.md, \"Desviaciones deliberadas\")."
  (:require [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [sldb.kernel.err :as err]))

(def index-path "The v1 store index, relative to the `.sldb` directory." "core/store_index.yaml")
(def documents-dir "Directory of v1 document entries, relative to the `.sldb` directory." "core/documents")

(def reasons
  "Typed failure reasons of a migration item → what each means."
  {:model-not-importable "the model's Python class could not be imported or exported"
   :descriptor-rejected "the exported descriptor is not a valid kimun model (e.g. retired `py•` markers)"
   :model-unavailable "the document's model is not in the index or failed to migrate"
   :file-missing "the Markdown file the v1 entry points at does not exist"
   :entry-unreadable "the v1 document entry is not readable YAML with a name and a path"
   :no-extract "the document does not extract under its model"
   :no-round-trip "rendering the extracted payload gives a text other than the file's"
   :name-collision "another document with this name is tracked under a different model"
   :commit-rejected "the kernel rejected the transaction"})

(defn- parse-yaml [text] (try (yaml/parse-string text :keywords false) (catch #?(:clj Exception :cljs :default) _ nil)))

(defn- link-of [s]
  (cond (string? s) {:name (last (remove str/blank? (str/split s #"/"))) :path s}
        (map? s) {:name (or (get s "name") (get s "alias") (some-> (get s "path") (str/split #"/") last)) :path (or (get s "path") (get s "root"))}
        :else {:name nil :path nil}))

(defn- document-of [model file text]
  (let [e (parse-yaml (or text "")) nm (when (map? e) (get e "name")) p (when (map? e) (get e "path"))]
    (if (and (string? nm) (string? p))
      {:name nm :model model :path p :entry file :source-hash (get e "source_hash")
       :content-hash (get e "content_hash") :semantic-tags (vec (get e "semantic_tags"))}
      {:name (str/replace file #"\.ya?ml$" "") :model model :entry file :unreadable? true})))

(defn read-v1
  "The plan of work of a v1 store. `read-file` is `(fn [rel] text-or-nil)` and
   `list-dir` is `(fn [rel] names)` (sorted or not; empty when absent), both
   relative to the `.sldb` directory. Returns
   `{:models [{:name :model-ref :path}] :documents [{:name :model :path :entry
   :source-hash :content-hash :semantic-tags}] :links [{:name :path}]}` with
   models in index order and documents sorted by model (index order, then
   unindexed directories) and entry file. A document entry that cannot be
   read carries `:unreadable? true`. Raises `:migrate/not-v1-store` when the
   index is missing or is not a YAML mapping."
  [{:keys [read-file list-dir]}]
  (let [index (some-> (read-file index-path) parse-yaml)]
    (when-not (map? index)
      (err/raise :migrate/not-v1-store (str "not an sldb v1 store: no readable " index-path) {:index index-path}))
    (let [models (mapv (fn [m] {:name (get m "name") :model-ref (get m "model_ref") :path (get m "path")}) (get index "models"))
          indexed (mapv :name models)
          dirs (concat indexed (sort (remove (set indexed) (list-dir documents-dir))))]
      {:models models
       :links (mapv link-of (get index "stores"))
       :documents (vec (for [m dirs
                             f (sort (filter #(re-find #"\.ya?ml$" %) (list-dir (str documents-dir "/" m))))]
                         (document-of m f (read-file (str documents-dir "/" m "/" f)))))})))

(defn select
  "`plan` narrowed by `only` (a model name, or nil) and `limit` (a maximum
   number of documents, or nil)."
  [plan {:keys [only limit]}]
  (cond-> plan
    only (update :models (partial filterv #(= only (:name %))))
    only (update :documents (partial filterv #(= only (:model %))))
    limit (update :documents #(vec (take limit %)))))

(defn- bucket-counts [items]
  (let [by (group-by :bucket items)]
    {:total (count items) :migrated (count (:migrated by)) :skipped (count (:skipped by)) :failed (count (:failed by))
     :failed-by-reason (into (sorted-map) (frequencies (map :reason (:failed by))))}))

(defn report
  "The report of a run. `outcomes` is `{:models [item] :documents [item]
   :links [item]}`; an item is `{:name … :bucket :migrated|:skipped|:failed}`
   plus `:reason` (a key of `reasons`) and `:message` when failed. Returns
   `{:models counts :documents counts :links counts :per-model {model counts}
   :items outcomes}` where counts is `{:total :migrated :skipped :failed
   :failed-by-reason}`. Raises `:migrate/unbucketed` when an item has no
   valid bucket or a failure has no known reason: nothing is lost silently."
  [outcomes]
  (doseq [k [:models :documents :links] i (get outcomes k)]
    (when-not (and (#{:migrated :skipped :failed} (:bucket i))
                   (or (not= :failed (:bucket i)) (contains? reasons (:reason i)) (= :links k)))
      (err/raise :migrate/unbucketed (str "migration item without a bucket or reason: " (:name i)) {:item i})))
  {:models (bucket-counts (:models outcomes))
   :documents (bucket-counts (:documents outcomes))
   :links (bucket-counts (:links outcomes))
   :per-model (into (sorted-map) (map (fn [[m is]] [m (bucket-counts is)])) (group-by :model (:documents outcomes)))
   :items (select-keys outcomes [:models :documents :links])})
