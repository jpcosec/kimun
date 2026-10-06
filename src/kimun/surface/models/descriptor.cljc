(ns kimun.surface.models.descriptor
  "Model descriptors (docs/v2/07 §2): validation of the EDN shape and the
   typing of an extracted payload — defaults, required fields, coercion."
  (:require [clojure.string :as str]
            [sldb.kernel.err :as err]))

(defn validate!
  "Returns `descriptor` when it has the shape of 07 §2; raises `:models/invalid`
   naming the first problem otherwise. `py•` markers are rejected (07 §8)."
  [descriptor]
  (let [bad (fn [why] (err/raise :models/invalid (str "descriptor: " why) {:model (:model/name descriptor)}))]
    (when-not (and (string? (:model/name descriptor)) (re-matches #"[A-Z_][A-Za-z0-9_]*" (:model/name descriptor))) (bad "`:model/name` must be a capitalised identifier"))
    (when-not (string? (:template descriptor)) (bad "`:template` must be a string"))
    (when-not (and (vector? (:fields descriptor)) (every? #(string? (:name %)) (:fields descriptor))) (bad "`:fields` must be a vector of maps with a string `:name`"))
    (when (apply distinct? (cons ::none (map :name (:fields descriptor)))) nil)
    (when (str/includes? (:template descriptor) "⸢py•") (bad "`py•` markers do not migrate (docs/v2/07 §8): convert them to derived values"))
    descriptor))

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
