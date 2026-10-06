(ns kimun.surface.models.validate
  "Roundtrip validation of a document against its model (docs/v2/07 §6):
   `render` then `extract` must give back the fields the template carries."
  (:require [clojure.string :as str]
            [kimun.surface.models.descriptor :as descriptor]
            [kimun.surface.models.extract :as extract]
            [kimun.surface.models.recipe :as recipe]
            [kimun.surface.models.render :as render]))

(defn- norm [v] (if (string? v) (str/trim v) v))

(defn validate
  "`{:ok bool :mode :input-data :rendered :extracted :errors [{:field …}]}` for
   `input`, either `{:markdown text}` or `{:data payload}`. `mode` is
   `:render-validity` (default: every `rev`/`optrev` field survives the
   roundtrip) or `:strict-roundtrip` (the whole payload does)."
  ([d input] (validate d input :render-validity))
  ([d {:keys [markdown data]} mode]
   (let [input-data (if (some? markdown) (extract/extract d markdown) (descriptor/finalize d data))
         rendered (render/render d input-data)
         extracted (extract/extract d rendered)
         fields (if (= :strict-roundtrip mode)
                  (distinct (concat (keys input-data) (keys extracted)))
                  (recipe/data-fields (:template d)))
         same? (if (= :strict-roundtrip mode) = #(= (norm %1) (norm %2)))
         errors (vec (for [f (sort fields) :when (not (same? (get input-data f) (get extracted f)))]
                       {:field f :type :validate/roundtrip :message (str "field '" f "' does not survive render → extract")}))]
     {:ok (empty? errors) :mode mode :input-data input-data :rendered rendered :extracted extracted :errors errors})))
