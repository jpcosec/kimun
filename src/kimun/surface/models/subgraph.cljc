(ns kimun.surface.models.subgraph
  "Spec 16 model descriptors (docs/v2/16 §3.4, §4): shape validation, the
   `:base` hierarchy (`derive`/`isa?`) and the composition of anchoring
   (selector, fields) and generation (projections) along it. Pure: no store,
   no files, no extraction."
  (:require [kimun.kernel.err :as err]
            [kimun.surface.models.descriptor :as descriptor]
            [kimun.surface.models.selector-cst :as cst]))

(def ^:private roles #{:source :generated})
(def ^:private parsers #{:treesitter :sldb-md/blocks})
(def ^:private compats #{:strict :legacy-v1-template})
(def ^:private formats #{:markdown :edn :json})

(defn- bad
  "Raises `:models/invalid` for `model` (a name or nil) with reason `why`."
  [model why]
  (err/raise :models/invalid (str "model " (pr-str model) ": " why)
             {:model model :problem why}))

(defn- check-field!
  "Checks one `:anchoring :fields` entry of model `nm`."
  [nm f]
  (when-not (map? f) (bad nm (str ":anchoring :fields entry is not a map: " (pr-str f))))
  (when-not (and (string? (:name f)) (seq (:name f)))
    (bad nm (str ":anchoring :fields entry without a :name string: " (pr-str f))))
  (when-not (and (string? (:description f)) (seq (:description f)))
    (bad nm (str "field `" (:name f) "` needs a non-empty :description")))
  (when-not (descriptor/type-valid? (:type f))
    (bad nm (str "field `" (:name f) "` has an invalid :type " (pr-str (:type f)))))
  (when (and (contains? f :required) (not (boolean? (:required f))))
    (bad nm (str "field `" (:name f) "` has a non-boolean :required"))))

(def ^:private cst-wants #{:find :table})

(defn- check-cst-step!
  "Checks one CST step (C3-C5, docs/v2/16 §3.3) of model `nm`: a non-empty
   string `:key`, the shape `selector-cst` reads. It has no `:outer-index` or
   `:handler`."
  [nm s]
  (when-not (and (string? (:key s)) (seq (:key s)))
    (bad nm (str "CST selector step without a :key string: " (pr-str s))))
  (when (and (contains? s :want) (not (contains? cst-wants (:want s))))
    (bad nm (str "CST selector step with an unknown :want " (pr-str (:want s)) ": " (pr-str s)))))

(defn- check-step!
  "Checks one `:anchoring :selector` step of model `nm`: a CST step
   (`selector-cst/cst-step?`) against `check-cst-step!`, any other as a block
   step (`:outer-index`, `:outer-type`, `:handler`)."
  [nm s]
  (when-not (map? s) (bad nm (str ":anchoring :selector step is not a map: " (pr-str s))))
  (if (cst/cst-step? s)
    (check-cst-step! nm s)
    (do
      (when (and (some? (:want s)) (not (integer? (:outer-index s))))
        (bad nm (str "selector step with an unknown :want " (pr-str (:want s)) ": " (pr-str s))))
      (when-not (integer? (:outer-index s))
        (bad nm (str "selector step without an integer :outer-index: " (pr-str s))))
      (when-not (and (string? (:outer-type s)) (seq (:outer-type s)))
        (bad nm (str "selector step without an :outer-type string: " (pr-str s))))
      (when-not (keyword? (:handler s))
        (bad nm (str "selector step without a keyword :handler: " (pr-str s)))))))

(defn validate!
  "Returns spec 16 descriptor `d` (docs/v2/16 §3.4) if its shape is valid;
   otherwise raises `:models/invalid` naming the first problem. `:template` and
   `:contract/digest` are not required (a digest, if present, is accepted and
   ignored: K6 computes it). Unknown keys are accepted."
  [d]
  (when-not (map? d) (bad nil "descriptor is not a map"))
  (let [nm (:model/name d)]
    (when-not (and (string? nm) (re-matches #"[A-Z_][A-Za-z0-9_]*" nm))
      (bad nm ":model/name must be a capitalized identifier"))
    (when-not (and (integer? (:model/version d)) (>= (:model/version d) 1))
      (bad nm ":model/version must be an integer >= 1"))
    (when-not (and (vector? (:base d)) (every? string? (:base d)))
      (bad nm ":base must be a vector of strings"))
    (when-not (contains? roles (:document-role d))
      (bad nm (str ":document-role must be one of " (pr-str roles))))
    (let [s (:substrate d)]
      (when-not (map? s) (bad nm ":substrate must be a map"))
      (when-not (contains? parsers (:parser s))
        (bad nm (str ":substrate :parser must be one of " (pr-str parsers))))
      (when-not (contains? compats (:compat s))
        (bad nm (str ":substrate :compat must be one of " (pr-str compats)))))
    (let [a (:anchoring d)]
      (when-not (map? a) (bad nm ":anchoring must be a map"))
      (when-not (vector? (:fields a)) (bad nm ":anchoring :fields must be a vector"))
      (run! #(check-field! nm %) (:fields a))
      (when-not (apply distinct? "" (map :name (:fields a)))
        (bad nm ":anchoring :fields has duplicate names"))
      (when-not (vector? (:selector a)) (bad nm ":anchoring :selector must be a vector"))
      (run! #(check-step! nm %) (:selector a)))
    (let [p (get-in d [:generation :projections])]
      (when-not (map? p) (bad nm ":generation :projections must be a map"))
      (doseq [[k v] p]
        (when-not (keyword? k) (bad nm (str "projection id " (pr-str k) " is not a keyword")))
        (when-not (and (map? v) (contains? formats (:format v)))
          (bad nm (str "projection " k " needs a :format in " (pr-str formats))))))
    d))

(defn blocked-by
  "Open decisions that block using descriptor `d`: #{:F10} for a `:generated`
   document role (docs/v2/16 §2.3), #{} for `:source`."
  [d]
  (if (= :generated (:document-role d)) #{:F10} #{}))

(defn index-models
  "Index of `models` by `:model/name`, the `index` the composition functions take."
  [models]
  (into {} (map (juxt :model/name identity)) models))

(defn- model-tag
  "Hierarchy keyword of model `nm`."
  [nm]
  (keyword "kimun.model" nm))

(defn- lineage
  "Ancestors of `nm` in `index`, ancestor-first, each one once (the first time
   it is reached). Raises `:models/invalid` on a cycle or a missing base."
  [index nm]
  (letfn [(visit [[acc path] n]
            (when (some #{n} path)
              (bad n (str "cycle in :base: " (pr-str (conj path n)))))
            (when-not (contains? index n)
              (bad n (str "base `" n "` is not among the models")))
            (if (some #{n} acc)
              [acc path]
              (let [[acc' _] (reduce visit [acc (conj path n)] (:base (index n)))]
                [(conj acc' n) path])))]
    (vec (butlast (first (visit [[] []] nm))))))

(defn hierarchy
  "A `make-hierarchy` with one `derive` per `:base` edge of `models`, keyed by
   `(keyword \"kimun.model\" name)`. Raises `:models/invalid` naming the node
   on a cycle or a base that is not among `models`."
  [models]
  (let [idx (index-models models)]
    (run! #(lineage idx (:model/name %)) models)
    (reduce (fn [h m]
              (reduce #(derive %1 (model-tag (:model/name m)) (model-tag %2)) h (:base m)))
            (make-hierarchy)
            models)))

(defn isa-model?
  "True when model name `child` is `parent` or inherits from it in `h`."
  [h child parent]
  (isa? h (model-tag child) (model-tag parent)))

(defn ancestors-of
  "Ancestor names of `nm`, ancestor-first, each ancestor once even in a
   diamond (team decision: the spec does not fix it). `index` maps name to
   descriptor. Raises `:models/invalid` on a cycle or missing base."
  [index nm]
  (lineage index nm))

(defn- chain
  "Descriptors of the ancestors of `nm` followed by `nm` itself."
  [index nm]
  (mapv index (conj (lineage index nm) nm)))

(defn compose-selector
  "Selector of `nm`: the steps of its ancestors then its own (docs/v2/16 §4.2),
   with the `:outer-index` of the block steps renumbered 0..n-1. CST steps
   (C3-C5) have no index: they keep their key and their declaration order and
   are not counted (the spec fixes no place for them: §4.2 says only \"union by
   key when it uses `:want :find`\")."
  [index nm]
  (let [n (volatile! -1)]
    (vec (map #(if (cst/cst-step? %) % (assoc % :outer-index (vswap! n inc)))
              (mapcat #(get-in % [:anchoring :selector]) (chain index nm))))))

(defn compose-fields
  "Fields of `nm` by name, ancestors first; a redeclaration keeps the position
   of the first declaration and takes the latest definition."
  [index nm]
  (let [fs (mapcat #(get-in % [:anchoring :fields]) (chain index nm))
        pos (reduce (fn [m f] (if (contains? m (:name f)) m (assoc m (:name f) (count m)))) {} fs)
        last-def (reduce (fn [m f] (assoc m (:name f) f)) {} fs)]
    (vec (sort-by #(pos (:name %)) (vals last-def)))))

(defn compose-projections
  "Named generation projections of `nm`: ancestors first, `merge`d, so the
   subclass adds and overrides one by one. Never touches the selector."
  [index nm]
  (apply merge {} (map #(get-in % [:generation :projections]) (chain index nm))))

(defn check-composition!
  "Raises `:models/invalid` if `nm` redeclares an inherited field with another
   `:type` or a weaker `:required` (docs/v2/16 §4.2: a subclass cannot drop
   inherited constraints). A model with `:base []` inherits nothing. Returns
   the composed fields."
  [index nm]
  (let [inherited (into {} (map (juxt :name identity))
                        (mapcat #(get-in % [:anchoring :fields])
                                (mapv index (lineage index nm))))]
    (doseq [f (get-in (index nm) [:anchoring :fields])
            :let [p (inherited (:name f))]
            :when p]
      (when (not= (:type p) (:type f))
        (bad nm (str "field `" (:name f) "` changes the inherited type "
                     (pr-str (:type p)) " to " (pr-str (:type f)))))
      (when (and (:required p) (not (:required f)))
        (bad nm (str "field `" (:name f) "` drops the inherited :required"))))
    (compose-fields index nm)))
