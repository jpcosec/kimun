(ns kimun.surface.index.corpus
  "The field corpus and its ranking (v1 `sldb.api.corpus` field half and
   `sldb.api.matching`), pure over the index of `kimun.surface.index.build`.

   A corpus is one entry per document field, projected to text; `rank` orders
   entries by similarity to a query with an injected `:score-fn` (the kimun
   form of v1's `Embedder`) or, without one, a deterministic lexical score
   (v1 degraded to difflib; kimun uses the kernel's Dice, see `lexical-score`).
   Nothing here does I/O: vectors, if a caller wants them, are its business.

   Input is an index map (`{:docs {tree-id doc} …}`) or a federated view
   `[{:store \"name\" :index idx} …]` whose first element is the current store
   (its entries carry no store prefix, as v1's `local`; linked stores prefix
   their ids with the store name).

   Field entry: `{:id \"docs/Note/uno/body\" :document-id \"Note:uno\" :field
   \"body\" :model :name :store nil|\"s\" :text :hash :payload :tags}`.
   Document entry: `{:id \"Note:uno\" :model :name :store :text :hash :payload
   :tags}` (text = the document's non-blank string fields, one per line, as
   v1's HTTP projection)."
  (:require [clojure.string :as str]
            [kimun.surface.docs :as tdocs]
            [kimun.host.default :as host]
            [kimun.kernel.reconcile :as reconcile])
  #?(:clj (:import (java.text Normalizer Normalizer$Form))))

;; ---------------------------------------------------------------- projection

(def default-excluded-fields
  "Identity, bookkeeping and vocabulary fields that are not prose (v1
   `DEFAULT_EXCLUDED_FIELDS`)."
  #{"id" "name" "slug" "path" "tags" "semantic_tags" "hash" "source_hash" "content_hash"})

(def default-min-chars
  "Fields whose text is shorter are not indexed (v1 `min_chars`): similarity on one word is noise."
  12)

(defn field-text
  "The indexable text of one field value, or \"\" when there is nothing to rank
   (v1 `default_field_text`): a string is trimmed; a sequence keeps its
   non-blank strings, trimmed and joined by newlines; anything else (numbers,
   maps, nil) is \"\"."
  [value]
  (cond
    (string? value) (str/trim value)
    (sequential? value) (str/join "\n" (keep #(when (string? %) (not-empty (str/trim %))) value))
    :else ""))

(defn field-id
  "`docs/<Model>/<doc>/<field>`, prefixed with `<store>/` for a linked store (v1 `field_id`)."
  ([model doc field] (field-id model doc field nil))
  ([model doc field store]
   (let [address (str "docs/" model "/" doc "/" field)]
     (if store (str store "/" address) address))))

(defn split-field-id
  "A field id back into `{:store :model :doc :field}`, or nil if it is not one (v1 `split_field_id`)."
  [value]
  (let [i (str/last-index-of value "/docs/")
        store (when i (subs value 0 i))
        body (cond i (subs value (+ i 6))
                   (str/starts-with? value "docs/") (subs value 5))
        parts (when body (str/split body #"/" 3))]
    (when (= 3 (count parts))
      {:store (not-empty store) :model (first parts) :doc (second parts) :field (nth parts 2)})))

(defn document-id
  "`Model:name`, or `store:Model:name` for a linked store (v1 `export_id`)."
  [store model name]
  (if store (str store ":" model ":" name) (str model ":" name)))

;; ---------------------------------------------------------------- reading the index

(defn- view-of
  "Normalises an index map or a federated view to `[{:store nil|name :index idx}]`
   (the first element is the current store, unprefixed)."
  [x]
  (if (map? x)
    [{:store nil :index x}]
    (into [] (map-indexed (fn [i {:keys [store index]}] {:store (when (pos? i) store) :index index})) x)))

(defn- admitted?
  "v1 `IndexProjection.admits` plus the store filter. `:models` (allowed set, nil = all),
   `:exclude-models`, `:stores` (allowed store names; the current store is \"local\")."
  [{:keys [models exclude-models stores]} model store]
  (and (not (contains? (set exclude-models) model))
       (or (nil? models) (contains? (set models) model))
       (or (nil? stores) (contains? (set stores) (or store "local")))))

(defn- live-docs
  "Documents of the view that extracted (have a payload), in deterministic order
   `[{:store :doc}]` (view order, then model, name, tree)."
  [x opts]
  (for [{:keys [store index]} (view-of x)
        d (sort-by (juxt :model :name :tree) (vals (:docs index)))
        :when (and (map? (:payload d)) (not (:error d))
                   (admitted? opts (or (:model d) "") store))]
    {:store store :doc d}))

(defn- field-hash
  "The field's own hash from the index, recomputed from the payload when the
   document carries none (v1 `_field_hashes`: an empty map means 'not recorded')."
  [d]
  (let [recorded (:field-hashes d)
        m (if (seq recorded) recorded (tdocs/field-hashes host/host (:payload d)))]
    (fn [k] (or (get m k) (get m (name k)) (get m (keyword k))))))

(defn entries
  "The field corpus of `index-or-view`: one entry per document field the
   projection admits and whose text has at least `:min-chars` characters.
   `opts`: `:include` (allowed field names, nil = all), `:exclude` (default
   `default-excluded-fields`), `:min-chars` (default 12), `:models`,
   `:exclude-models`, `:stores`. Ordered by document then payload field order."
  ([x] (entries x {}))
  ([x {:keys [include exclude min-chars] :or {exclude default-excluded-fields min-chars default-min-chars} :as opts}]
   (let [include (some-> include set) exclude (set exclude)]
     (vec
      (for [{:keys [store doc]} (live-docs x opts)
            :let [{:keys [model name payload tags]} doc
                  hash-of (field-hash doc)]
            [k v] payload
            :let [f (clojure.core/name k) text (field-text v)]
            :when (and (not (contains? exclude f))
                       (or (nil? include) (contains? include f))
                       (>= (count text) min-chars))]
        {:id (field-id model name f store) :document-id (document-id store model name)
         :field f :model model :name name :store store
         :text text :hash (hash-of k) :payload payload :tags (vec tags)})))))

(defn document-entries
  "The document corpus (v1 `corpus_entries` with the HTTP projection): one entry
   per extracted document whose text (its non-blank string fields, one per line)
   is not empty. Same `opts` filters as `entries` for models and stores."
  ([x] (document-entries x {}))
  ([x opts]
   (vec
    (for [{:keys [store doc]} (live-docs x opts)
          :let [{:keys [model name payload tags content-hash hash-c]} doc
                text (str/join "\n" (keep #(when (string? %) (not-empty (str/trim %))) (vals payload)))]
          :when (seq text)]
      {:id (document-id store model name) :model model :name name :store store
       :text text :hash (or content-hash hash-c) :payload payload :tags (vec tags)}))))

;; ---------------------------------------------------------------- scoring

(defn normalize
  "Lower-cased, accent-stripped, whitespace-collapsed text (v1 `normalize`)."
  [text]
  (let [nfd #?(:clj (Normalizer/normalize ^CharSequence text Normalizer$Form/NFD)
               :cljs (.normalize text "NFD"))
        stripped (str/replace nfd #"\p{Mn}" "")]
    (str/join " " (remove str/blank? (str/split (str/lower-case stripped) #"\s+")))))

(defn lexical-score
  "The deterministic default score of `query` against `text`, in [0, 1]: the best
   Dice coefficient (`kimun.kernel.reconcile/dice`, trigram multiset) between the
   normalised query and the normalised text or any of its words, 0.0 when either
   is blank. The structure is v1's `DifflibMatcher.similarity`; the metric is the
   kernel's, not `difflib.ratio()`."
  [query text]
  (let [q (normalize query) c (normalize text)]
    (if (or (str/blank? q) (str/blank? c))
      0.0
      (reduce max (reconcile/dice host/host q c)
              (map #(reconcile/dice host/host q %) (str/split c #" "))))))

(defn cosine
  "Cosine similarity of two numeric vectors (0.0 when either has no magnitude);
   for building a `:score-fn` out of an embedder."
  [a b]
  (let [dot (reduce + 0.0 (map * a b))
        na (Math/sqrt (reduce + 0.0 (map #(* % %) a)))
        nb (Math/sqrt (reduce + 0.0 (map #(* % %) b)))]
    (if (and (pos? na) (pos? nb)) (/ dot (* na nb)) 0.0)))

;; ---------------------------------------------------------------- ranking

(defn- round4 [x] (/ (Math/round (* 10000.0 (double x))) 10000.0))

(defn- allowed? [among {:keys [id document-id]}]
  (or (nil? among) (contains? among id) (and document-id (contains? among document-id))))

(defn rank
  "The entries of `corpus` (a vector of `entries` / `document-entries`) closest to
   `query`, best first: `[{:id … :score s …entry minus :hash}]`, `:score` rounded
   to 4 decimals. `opts`: `:score-fn` `(fn [query text] number)` (default
   `lexical-score`), `:k` (at most k hits, nil = all), `:threshold` (keep
   `score >= threshold`, default 0.0), `:among` (ids to keep; for field entries a
   document id admits all its fields, as v1). Ties break by id ascending, so the
   order is total and deterministic. An empty corpus ranks to `[]`: the 'not
   indexed' state is the caller's to report, see `kimun.cli.commands.corpus`."
  [corpus query {:keys [score-fn k threshold among] :or {score-fn lexical-score threshold 0.0}}]
  (let [among (when (seq among) (set among))
        hits (->> corpus
                  (map (fn [e] [e (double (score-fn query (:text e)))]))
                  (filter (fn [[_ s]] (>= s threshold)))
                  (sort-by (fn [[e s]] [(- s) (:id e)]))
                  (filter (fn [[e _]] (allowed? among e)))
                  (map (fn [[e s]] (assoc (dissoc e :hash) :score (round4 s)))))]
    (vec (if k (take k hits) hits))))
