(ns kimun.surface.models.descriptor
  "Model descriptors (docs/v2/07 §2): validation of the EDN shape and the
   typing of an extracted payload — defaults, required fields, coercion."
  (:require [clojure.string :as str]
            [kimun.kernel.err :as err]))

(def scalar-types
  "The scalar field types of 07 §2."
  #{:string :int :bool :text :yaml})

(defn type-valid?
  "True when `t` is a field type of 07 §2: a scalar, `[:list T]`, `[:map T]`,
   `[:enum v…]` with at least one string, or `[:ref Model]`."
  [t]
  (cond
    (keyword? t) (contains? scalar-types t)
    (and (vector? t) (contains? #{:list :map} (first t))) (and (= 2 (count t)) (type-valid? (second t)))
    (and (vector? t) (= :enum (first t))) (and (next t) (every? string? (rest t)))
    (and (vector? t) (= :ref (first t))) (and (= 2 (count t)) (string? (second t)))
    :else false))

(defn default-of-type?
  "True when default value `v` belongs to field type `t`. `:yaml` and the
   element types of collections accept any value."
  [t v]
  (cond
    (nil? v) true
    (contains? #{:string :text} t) (string? v)
    (= :int t) (integer? v)
    (= :bool t) (boolean? v)
    (= :yaml t) true
    (= :list (first t)) (sequential? v)
    (= :map (first t)) (map? v)
    (= :enum (first t)) (boolean (some #{v} (rest t)))
    (= :ref (first t)) (string? v)
    :else false))

(defn- state-problem [{:keys [fields state]}]
  (when state
    (let [f (first (filter #(= (:field state) (:name %)) fields))]
      (cond
        (nil? f) (str "`:state :field` names no field: " (:field state))
        (not (and (vector? (:type f)) (= :enum (first (:type f))))) "`:state :field` must be an `[:enum …]` field"
        (not= (vec (rest (:type f))) (vec (:order state))) "`:state :order` must list exactly the enum values of its field"
        (not (every? (set (:order state)) (:terminal state))) "`:state :terminal` must be a subset of `:order`"))))

(defn validate!
  "Returns `descriptor` when it has the shape of 07 §2; raises `:models/invalid`
   naming the first problem otherwise: name, version, template, fields (unique
   string names, a valid type, a default of that type, a non-empty
   `:description`), `:base` as a vector of names, `:state` coherent with its
   enum field. `py•` markers are rejected (07 §8). With `{:known-models #{…}}`
   every `:base` must be one of them."
  ([descriptor] (validate! descriptor {}))
  ([descriptor {:keys [known-models]}]
   (let [bad (fn [why] (err/raise :models/invalid (str "descriptor: " why) {:model (:model/name descriptor) :problem why}))
         fields (:fields descriptor)]
     (when-not (map? descriptor) (bad "must be a map"))
     (when-not (and (string? (:model/name descriptor)) (re-matches #"[A-Z_][A-Za-z0-9_]*" (:model/name descriptor))) (bad "`:model/name` must be a capitalised identifier"))
     (when (and (contains? descriptor :model/version) (not (and (integer? (:model/version descriptor)) (pos? (:model/version descriptor))))) (bad "`:model/version` must be an integer ≥ 1"))
     (when-not (string? (:template descriptor)) (bad "`:template` must be a string"))
     (when-not (and (vector? fields) (every? #(and (map? %) (string? (:name %))) fields)) (bad "`:fields` must be a vector of maps with a string `:name`"))
     (when-let [dup (first (keep (fn [[n c]] (when (> c 1) n)) (frequencies (map :name fields))))] (bad (str "field `" dup "` is declared more than once")))
     (doseq [f fields]
       (when (and (contains? f :type) (not (type-valid? (:type f)))) (bad (str "field `" (:name f) "` has an unknown type " (pr-str (:type f)))))
       (when (and (contains? f :type) (contains? f :default) (not (default-of-type? (:type f) (:default f))))
         (bad (str "the default of field `" (:name f) "` is not of its type " (pr-str (:type f))))))
     (let [undescribed (->> fields (remove #(and (string? (:description %)) (not (str/blank? (:description %))))) (mapv :name))]
       (when (seq undescribed)
         (bad (str "fields must define a non-empty description: " (str/join ", " undescribed)))))
     (when (and (contains? descriptor :base) (not (and (sequential? (:base descriptor)) (every? string? (:base descriptor))))) (bad "`:base` must be a vector of model names"))
     (when known-models
       (when-let [missing (first (remove (set known-models) (:base descriptor)))] (bad (str "base model `" missing "` is not defined"))))
     (when-let [p (state-problem descriptor)] (bad p))
     (when (str/includes? (:template descriptor) "⸢py•") (bad "`py•` markers do not migrate (docs/v2/07 §8): convert them to derived values"))
     descriptor)))

(defn- coerce [{:keys [name type]} v]
  (cond
    (and (= :int type) (string? v) (re-matches #"\s*-?\d+\s*" v)) (parse-long (str/trim v))
    (and (= :bool type) (string? v) (contains? #{"true" "false"} (str/lower-case (str/trim v)))) (= "true" (str/lower-case (str/trim v)))
    (and (vector? type) (= :enum (first type)) (not (some #{v} (rest type))))
    (err/raise :validate/invalid (str "field '" name "' must be one of " (vec (rest type))) {:field name :value v})
    (and (contains? #{:string :text} type) (not (string? v)))
    (err/raise :validate/invalid (str "field '" name "' must be a string") {:field name :value v})
    (and (vector? type) (= :list (first type)) (not (sequential? v)))
    (err/raise :validate/invalid (str "field '" name "' must be a list") {:field name :value v})
    :else v))

(defn finalize
  "The typed payload of `descriptor` from the raw `values` an extraction or a
   caller produced (string keys): declared fields only, defaults applied,
   values coerced, nil dropped. Raises `:validate/missing` for a required
   field with no value — never a silent empty. A field declared `:exclude true`
   is a carrier (v1's excluded frontmatter dict): the keys of its map value
   are lifted into the declared fields that have no value of their own, and
   the carrier itself never appears in the payload."
  [descriptor values]
  (let [carriers (filter :exclude (:fields descriptor))
        values (reduce (fn [vs {:keys [name]}]
                         (let [m (get vs name)] (if (map? m) (merge m (dissoc vs name)) (dissoc vs name))))
                       values carriers)
        descriptor (update descriptor :fields #(vec (remove :exclude %)))]
   (reduce (fn [out {:keys [name required] :as f}]
            (let [v (get values name ::absent)
                  v (if (or (= ::absent v) (nil? v)) (get f :default ::absent) v)]
              (cond (and (= ::absent v) required)
                    (err/raise :validate/missing (str "required field '" name "' is missing") {:field name :model (:model/name descriptor)})
                    (= ::absent v) out
                    :else (assoc out name (coerce f v)))))
          {} (:fields descriptor))))
