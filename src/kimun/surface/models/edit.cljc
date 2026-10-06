(ns kimun.surface.models.edit
  "Editing a model is transforming its descriptor (docs/v2/07 §1): the pure
   counterparts of v1's draft edits — add a field with its template section,
   remove a field, replace the template — and the check that a template only
   names declared fields."
  (:require [clojure.string :as str]
            [sldb.kernel.err :as err]))

(defn- title-of [field]
  (str/join " " (map str/capitalize (str/split field #"_+"))))

(defn section-for-field
  "The template section v1 appended for a new field: a level-2 heading and
   the marker that fits `type` — a list item for `[:list scalar]`, the `table`
   trait for a list of maps, `dict` for a map, a lone marker otherwise.
   `optional?` selects `optrev` over `rev`."
  [field type optional?]
  (let [kind (if optional? "optrev" "rev")
        marker (cond
                 (and (vector? type) (= :list (first type)) (vector? (second type)) (= :map (first (second type))))
                 (str "⸢" kind ",table•" field "⸥")
                 (and (vector? type) (= :list (first type))) (str "- ⸢" kind ",list•" field "⸥")
                 (or (= :yaml type) (and (vector? type) (= :map (first type)))) (str "⸢" kind ",dict•" field "⸥")
                 :else (str "⸢" kind "•" field "⸥"))]
    (str "## " (title-of field) "\n\n" marker)))

(defn add-field
  "`descriptor` with `field` (`{:name :type :required …}`) appended and its
   section added to the template: `optrev` when the field has a default or is
   not required, `rev` otherwise. Raises `:models/field-exists` for a
   duplicate name."
  [descriptor field]
  (when (some #(= (:name field) (:name %)) (:fields descriptor))
    (err/raise :models/field-exists (str "model `" (:model/name descriptor) "` already has a field `" (:name field) "`")
               {:model (:model/name descriptor) :field (:name field)}))
  (let [optional? (or (contains? field :default) (not (:required field)))]
    (-> descriptor
        (update :fields conj field)
        (update :template #(str (str/trimr %) "\n\n" (section-for-field (:name field) (:type field) optional?) "\n")))))

(defn remove-field
  "`descriptor` without field `name`; its declaration goes, and so does every
   template line that carries one of its markers (with the level-2 heading
   v1 generated for it, when present). Raises `:models/missing` when the
   model has no such field."
  [descriptor name]
  (when-not (some #(= name (:name %)) (:fields descriptor))
    (err/raise :models/missing (str "model `" (:model/name descriptor) "` has no field `" name "`") {:model (:model/name descriptor) :field name}))
  (let [mark? #(boolean (re-find (re-pattern (str "⸢[^⸥•]*•" (java.util.regex.Pattern/quote name) "(\\|[^⸥]*)?⸥")) %))
        heading (str "## " (title-of name))
        lines (remove #(or (mark? %) (= heading (str/trim %))) (str/split-lines (:template descriptor)))
        text (str/replace (str/join "\n" lines) #"\n{3,}" "\n\n")]
    (-> descriptor
        (update :fields (fn [fs] (vec (remove #(= name (:name %)) fs))))
        (assoc :template (str (str/trimr text) "\n")))))

(defn set-template
  "`descriptor` with `template` as its template text."
  [descriptor template]
  (assoc descriptor :template template))

(defn- marked
  "Field names used by markers of `kinds` (a regex alternation) in the
   template, skipping table rows and inline code spans."
  [descriptor kinds]
  (distinct (for [line (str/split-lines (:template descriptor))
                  :when (not (str/starts-with? (str/triml line) "|"))
                  [_ field] (re-seq (re-pattern (str "⸢(?:" kinds ")(?:,[^•⸥]*)?•([^⸥|]+)")) (str/replace line #"`[^`]*`" ""))]
              (str/trim field))))

(defn rev-fields
  "Declared fields that the template captures with a non-optional `rev`
   marker of their own: the fields a document must give a value for."
  [descriptor]
  (let [declared (set (map :name (:fields descriptor)))]
    (vec (filter declared (marked descriptor "rev")))))

(defn undeclared-fields
  "Names that the `rev`/`optrev` markers of the template use and the
   descriptor does not declare, sorted. Markers inside a table row are
   skipped: they name the columns of the row's table field, not fields; so
   are markers quoted in an inline code span, which are prose. A
   descriptor with an `:exclude` carrier field is exempt (its keys are
   lifted at extraction): returns []."
  [descriptor]
  (if (some :exclude (:fields descriptor))
    []
    (let [declared (set (map :name (:fields descriptor)))]
      (vec (sort (remove declared (marked descriptor "rev|optrev")))))))

(defn check-template!
  "Returns `descriptor`, or raises `:models/invalid` when its template names
   undeclared fields (v1: a draft whose template references unknown fields)."
  [descriptor]
  (let [missing (undeclared-fields descriptor)]
    (when (seq missing)
      (err/raise :models/invalid (str "template of `" (:model/name descriptor) "` references undeclared field(s): " (str/join ", " missing))
                 {:model (:model/name descriptor) :fields missing}))
    descriptor))
