(ns kimun.surface.fields
  "The `fields` surface of sldb v1 over typed documents (docs/v2/06 §5), pure:
   field paths (`a.b`, `items.0.name`; a numeric segment indexes a list), the
   coercion of a command-line value to the declared type of the field, the
   mutations of one field of a payload (`:set`, `:unset`, `:append`, `:clean`,
   `:clear`), the field schema of a model and the query of a field path
   across the tracked documents with the section that owns each field. No
   I/O: `kimun.cli.commands.fields` reads, renders, commits and writes."
  (:require [clojure.string :as str]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.index.where :as where]
            [kimun.surface.models.descriptor :as descriptor]
            [kimun.surface.models.extract :as extract]
            [kimun.surface.models.render :as render]
            [kimun.surface.models.segments :as segments]
            [kimun.surface.models.store :as mstore]
            [kimun.kernel.err :as err]))

(def operations
  "The mutations `mutate` knows; the journal names one as `fields <name>`."
  #{:set :unset :append :clean :clear})

;; ---------------------------------------------------------------- paths

(defn split-path
  "The segments of field path `path`: split on `.` (v1's `/` is accepted
   too), empty segments dropped. Raises `:fields/bad-path` for an empty path."
  [path]
  (let [parts (vec (remove str/blank? (str/split (str path) #"[./]")))]
    (when (empty? parts) (err/raise :fields/bad-path "a field path is required, e.g. `status` or `items.0.name`" {:path path}))
    parts))

(defn- missing! [path] (err/raise :fields/missing (str "no value at field path `" path "`") {:path path}))

(defn- index-of
  "The list index segment `part` names in `coll`; a non-numeric segment is
   `:fields/bad-path`, an index out of range `:fields/missing`."
  [coll part path]
  (let [i (if (re-matches #"\d+" part) (parse-long part)
              (err/raise :fields/bad-path (str "`" part "` in `" path "` must be a list index (0, 1, …)") {:path path :segment part}))]
    (if (< i (count coll)) i (missing! path))))

(defn- step [v part path]
  (cond (map? v) (if (contains? v part) (get v part) (missing! path))
        (sequential? v) (nth v (index-of v part path))
        :else (missing! path)))

(defn value-at
  "The value of `payload` at `path` (v1 `deep_get`). Raises `:fields/missing`
   (exit 1) when a key is absent, a scalar is stepped into or a list index is
   out of range (v1 let `IndexError` escape), `:fields/bad-path` for a
   non-numeric list index."
  [payload path]
  (reduce #(step %1 %2 path) payload (split-path path)))

(defn present?
  "True when `payload` has a value (nil included) at `path`."
  [payload path]
  (try (value-at payload path) true
       (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) e
         (if (= :fields/missing (:type (ex-data e))) false (throw e)))))

(defn- put [v parts new create? path]
  (let [[part & more] parts]
    (cond
      (map? v) (cond (and (not (contains? v part)) (not create?)) (missing! path)
                     more (assoc v part (put (get v part {}) more new create? path))
                     :else (assoc v part new))
      (sequential? v) (let [i (index-of v part path)]
                        (assoc (vec v) i (if more (put (nth v i) more new create? path) new)))
      :else (missing! path))))

(defn assoc-at
  "`payload` with `value` at `path` (v1 `deep_set`). With `create?` absent map
   keys are created (intermediate ones as maps); without it an absent key is
   `:fields/missing`. A list index must exist either way."
  [payload path value create?]
  (put payload (split-path path) value create? path))

(defn- cut [v parts path]
  (let [[part & more] parts]
    (cond
      (map? v) (cond (not (contains? v part)) (if more (missing! path) v)
                     more (assoc v part (cut (get v part) more path))
                     :else (dissoc v part))
      (sequential? v) (let [i (index-of v part path) v (vec v)]
                        (if more (assoc v i (cut (nth v i) more path))
                            (into (subvec v 0 i) (subvec v (inc i)))))
      :else (missing! path))))

(defn dissoc-at
  "`payload` without the value at `path` (v1 `deep_delete`): a map key is
   removed (an absent last key is ignored, as v1 did), a list element is
   dropped. An absent intermediate key is `:fields/missing`."
  [payload path]
  (cut payload (split-path path) path))

(defn- list-at [payload path]
  (let [v (value-at payload path)]
    (if (sequential? v) (vec v)
        (err/raise :fields/not-a-list (str "field path `" path "` is not a list field") {:path path :value v}))))

;; ---------------------------------------------------------------- schema and coercion

(defn- field-decl [d field-name]
  (first (filter #(= field-name (:name %)) (:fields d))))

(defn- type-name [t]
  (cond (keyword? t) (name t) (vector? t) (str (name (first t)) "[" (str/join ", " (map type-name (rest t))) "]") (nil? t) "any" :else (str t)))

(defn- field-row [f]
  (cond-> {:name (:name f) :description (or (:description f) "") :type (type-name (:type f)) :required (boolean (:required f))}
    (contains? f :default) (assoc :default (:default f))))

(defn schema
  "The field schema of model descriptor `d` (v1 `fields show models/M[/F]`):
   `{:model :fields [{:name :description :type :required (:default)}]}`, or
   with `field-name` that one row plus `:model` and `:field`. `:type` is the
   declared type as text (`string`, `list[string]`, `enum[a, b]`). An
   undeclared field is `:fields/missing` (v1 raised `KeyError`)."
  ([d] {:model (:model/name d) :fields (mapv field-row (:fields d))})
  ([d field-name]
   (if-let [f (field-decl d field-name)]
     (assoc (field-row f) :model (:model/name d) :field field-name)
     (err/raise :fields/missing (str "model `" (:model/name d) "` declares no field `" field-name "`")
                {:model (:model/name d) :field field-name :known (mapv :name (:fields d))}))))

(defn type-at
  "The declared type of `path` under descriptor `d`, or nil when the model does
   not say: the field's type for a one-segment path, the element type under a
   `[:list T]` or `[:map T]` for each further segment."
  [d path]
  (let [[top & more] (split-path path)]
    (reduce (fn [t _] (when (and (vector? t) (contains? #{:list :map} (first t))) (second t)))
            (:type (field-decl d top)) more)))

(defn- wrong-type! [path t v]
  (err/raise :fields/wrong-type (str "field path `" path "` is declared " (type-name t) "; got " (pr-str v))
             {:path path :expected (type-name t) :value v}))

(defn coerce
  "`value` (what the command line parsed, with `raw` its text) as a value of
   declared type `t`, for field path `path`. A scalar given for a `:string`,
   `:text` or enum field is its text (`2024` stays \"2024\"); a string of digits
   is an `:int`, `true`/`false` a `:bool`; list and map elements are coerced
   to their element type. An untyped path (`nil`, `:yaml`) takes the value as
   parsed. Anything else is `:fields/wrong-type` (exit 3)."
  [t path raw value]
  (let [kind (if (vector? t) (first t) t)
        text #(cond (string? %) %
                    ;; YAML reads `# note` as a comment: for a string field the text given is the value
                    (and (nil? %) (string? raw) (not (contains? #{"" "~" "null" "Null" "NULL"} (str/trim raw)))) (str/trim raw)
                    (or (number? %) (boolean? %)) (if (and (string? raw) (not (str/blank? raw))) (str/trim raw) (str %))
                    :else (wrong-type! path t %))]
    (case kind
      (:string :text) (text value)
      :enum (let [s (text value)] (if (some #{s} (rest t)) s (wrong-type! path t value)))
      :int (cond (integer? value) value
                 (and (string? value) (re-matches #"\s*-?\d+\s*" value)) (parse-long (str/trim value))
                 :else (wrong-type! path t value))
      :bool (cond (boolean? value) value
                  (and (string? value) (#{"true" "false"} (str/lower-case (str/trim value)))) (= "true" (str/lower-case (str/trim value)))
                  :else (wrong-type! path t value))
      :list (if (sequential? value) (mapv #(coerce (second t) path (str %) %) value) (wrong-type! path t value))
      :map (if (map? value) (into {} (map (fn [[k v]] [k (coerce (second t) path (str v) v)])) value) (wrong-type! path t value))
      value)))

;; ---------------------------------------------------------------- mutation

(defn- empty-item? [v] (or (nil? v) (= "" v) (= [] v) (= {} v)))

(defn- clean-list
  "v1 `_clean_list`: drops empty items and/or later duplicates, keeping order."
  [items {:keys [drop-empty dedupe]}]
  (:out (reduce (fn [{:keys [seen] :as st} item]
                  (cond (and drop-empty (empty-item? item)) st
                        (and dedupe (contains? seen item)) st
                        :else (-> st (update :out conj item) (update :seen conj item))))
                {:out [] :seen #{}} items)))

(defn mutate
  "Applies mutation `op` to `payload` (a document payload under descriptor
   `d`) at field path `path` and returns `{:payload :operation :field :path
   :before :after :existed? :unchanged?}`: `:field` is the top-level field,
   `:before`/`:after` the value at `path` (nil when absent). Nothing is
   validated against the model here beyond the type of the given value — see
   `render-checked`. `opts`:

   - `:set` — `:value` (parsed) and `:raw` (its text), coerced to the declared
     type; `:create?` (default true) creates an absent key, false makes it
     `:fields/missing` (v1 `update` vs `create`);
   - `:unset` — removes the key or list element (an absent last key is a no-op);
   - `:append` — `:value`/`:raw` coerced to the list's element type; the path
     must hold a list (`:fields/missing`, `:fields/not-a-list`);
   - `:clean` — v1 `fields clean`: `:drop-empty` and/or `:dedupe` over a list;
   - `:clear` — the list at `path` becomes `[]`."
  [d payload op path {:keys [value raw create?] :or {create? true} :as opts}]
  (when-not (operations op) (err/raise :fields/bad-operation (str "unknown field operation " (pr-str op)) {:operation op}))
  (let [parts (split-path path) path (str/join "." parts)
        existed? (present? payload path)
        before (when existed? (value-at payload path))
        t (type-at d path)
        after-payload (case op
                        :set (assoc-at payload path (coerce t path raw value) create?)
                        :unset (dissoc-at payload path)
                        :append (assoc-at payload path (conj (list-at payload path) (coerce (when (vector? t) (second t)) path raw value)) false)
                        :clean (assoc-at payload path (clean-list (list-at payload path) opts) false)
                        :clear (do (list-at payload path) (assoc-at payload path [] false)))
        after (when (and (not= :unset op) (present? after-payload path)) (value-at after-payload path))]
    {:payload after-payload :operation (str "fields " (name op)) :field (first parts) :path path
     :before before :after after :existed? existed? :unchanged? (= payload after-payload)}))

(defn- norm [v] (if (string? v) (str/trim v) v))

(defn render-checked
  "The Markdown of `payload` under descriptor `d` (rendered with composition
   context `ctx`, see `render/render`) once it is known to be a valid document
   of the model: `{:text :payload}` with the finalized payload. Raises what
   `descriptor/finalize` raises for a payload the model rejects
   (`:validate/missing`, `:validate/invalid`) and `:fields/not-roundtrip`
   (exit 3) when `field` — a top-level field the template carries — does not
   read back from the rendered text as it was written."
  [d payload ctx field]
  (let [typed (descriptor/finalize d payload)
        text (str (render/render d payload ctx) "\n")
        back (extract/extract d text)]
    (when (and (contains? back field) (not= (norm (get typed field)) (norm (get back field))))
      (err/raise :fields/not-roundtrip
                 (str "field '" field "' does not survive render → extract under " (:model/name d) ": the value would be lost or altered in the file")
                 {:field field :written (get typed field) :read (get back field)}))
    {:text text :payload typed}))

;; ---------------------------------------------------------------- owning sections and query

(defn owning-sections
  "`{field section-path}` for a document of descriptor `d` whose Markdown is
   `markdown`: `segments/field-owners` over the document's own sections
   (v1 `_get_owning_section_map`). `{}` without a template or a text."
  [d markdown]
  (if (and (:template d) markdown)
    (segments/field-owners d (segments/sections markdown))
    {}))

(defn owning-section
  "The path of the section that owns field path `path` in a document, given
   `owners` (what `owning-sections` returns): the owner of the exact path,
   else of its top-level field; nil when the field sits above the first
   heading or the ownership cannot be told."
  [owners path]
  (or (get owners path) (get owners (first (split-path path)))))

(defn- flatten-payload [v prefix]
  (if (map? v)
    (mapcat (fn [[k x]] (flatten-payload x (if (str/blank? prefix) (str k) (str prefix "." k)))) v)
    [[prefix v]]))

(defn rows
  "The rows of v1 `query_field_records` for one document: every leaf of
   `payload` (maps flattened with `.`) whose path is `field-ref` or ends in
   `.field-ref`, as `{:model :doc :path :field :value :owning_section}`.
   `doc` is `{:name :model :path :payload :source}` and `d` its model's
   descriptor. This is the function a `find --type field` hit is built from."
  [d doc field-ref]
  (let [hits (filter (fn [[p _]] (or (= p field-ref) (str/ends-with? p (str "." field-ref)))) (flatten-payload (:payload doc) ""))
        owners (when (seq hits) (owning-sections d (:source doc)))]
    (mapv (fn [[p v]] {:model (:model doc) :doc (:name doc) :path (:path doc) :field p :value v
                       :owning_section (owning-section owners p)})
          hits)))

(defn query
  "`rows` for `field-ref` across the tracked documents of `store`, by document
   name (v1 `fields query`). `opts`: `:model` keeps the documents of that
   model; `:where` is a parsed `--where` expression (`where/parse`) matched
   against `{:name :model :bases :payload :tags}` of each document. Documents
   whose source no longer extracts under their model are skipped."
  ([store field-ref] (query store field-ref {}))
  ([store field-ref {:keys [model] ast :where}]
   (vec (for [doc (tdocs/documents store)
              :when (or (nil? model) (= model (:model doc)))
              :let [d (:descriptor (mstore/find-model store (:model doc)))
                    payload (:payload (tdocs/payload-of store doc))]
              :when (and d payload
                         (or (nil? ast)
                             (where/matches? ast {:name (:name doc) :model (:model doc) :bases (vec (:base d))
                                                  :payload payload :tags (vec (get payload "semantic_tags"))})))
              row (rows d (assoc doc :payload payload) field-ref)]
          row))))
