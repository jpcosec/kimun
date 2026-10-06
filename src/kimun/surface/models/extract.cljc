(ns kimun.surface.models.extract
  "Markdown → payload (docs/v2/07 §6): aligns the blocks of a document with
   the recipe of the model's template and captures each field. Pure, no store.
   The alignment reproduces sldb v1's `DataExtractor`."
  (:require [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [kimun.surface.models.blocks :as blocks]
            [kimun.surface.models.descriptor :as descriptor]
            [kimun.surface.models.marker :as marker]
            [kimun.surface.models.recipe :as recipe]
            [sldb.kernel.err :as err]))

(defn- plain [v]
  (walk/postwalk (fn [x] (cond (map? x) (into {} (map (fn [[k v]] [(if (keyword? k) (name k) k) v])) x)
                               (instance? java.util.Date x) (let [t (subs (.toString (.toInstant ^java.util.Date x)) 0 19)]
                                                              (if (str/ends-with? t "T00:00:00") (subs t 0 10) t))
                               (sequential? x) (vec x) :else x)) v))

(defn- yaml-data [b r]
  (when (contains? #{"fence" "front_matter"} (:type b))
    (let [content (str/trim (:content b))
          clean (let [c (str/trim (str/replace content marker/pattern ""))] (if (seq c) c content))
          data (try (plain (yaml/parse-string clean :keywords false))
                    (catch Exception e
                      (err/raise :models/bad-yaml (str "Failed to parse YAML content for " (or (:name r) "<unknown>") ": " (ex-message e)) {})))]
      (if (:markers r)
        (when (map? data) (into {} (map (fn [{:keys [key marker]}] [(:name marker) (get data key)])) (:markers r)))
        {(:name r) data}))))

(defn- cell-text [c] (str/trim (blocks/text-of c)))

(defn- table-data [b r]
  (let [rows (blocks/rows b)]
    (if (:marker r)
      (let [headers (mapv cell-text (:children (first rows)))
            cols (or (seq (:table-columns r)) headers)
            at (zipmap headers (range))]
        {(:name r) (if (empty? rows) []
                       (mapv (fn [row] (into {} (map (fn [c] [c (if-let [i (at c)] (or (some-> (get (:children row) i) cell-text) "") "")])) cols))
                             (rest rows)))})
      (let [records (keep (fn [row]
                            (let [rec (into {} (keep (fn [[i {:keys [marker cell-template]}]]
                                                       (when-let [cell (get (:children row) i)]
                                                         (when-let [m (re-matches (re-pattern (:regex (marker/text-pattern cell-template))) (cell-text cell))]
                                                           [(:name marker) (if (vector? m) (second m) m)]))))
                                            (:col-markers r))]
                              (when (seq rec) rec)))
                          (rest rows))]
        (when (seq records) {(:name r) (vec records)})))))

(defn- text-data [text r]
  (when-let [m (and text (re-matches (re-pattern (:regex r)) (str/trim text)))]
    (zipmap (:props r) (if (vector? m) (rest m) []))))

(defn- data-of [b r]
  (case (:handler r)
    :text (text-data (blocks/leaf-text b) r)
    :list {(:name r) (vec (remove str/blank? (map #(str/trim (blocks/text-of %)) (:children b))))}
    :yaml (yaml-data b r)
    :table (table-data b r)))

(defn- match-at [b r]
  (when (and (= (:type b) (:outer-type r)) (= (:tag b) (:outer-tag r)))
    (if (:anchor r) {} (let [v (data-of b r)] (when (seq v) v)))))

(defn- matches-position? [b r]
  (and (= (:type b) (:outer-type r)) (= (:tag b) (:outer-tag r))
       (if (= :text (:handler r))
         (some? (re-matches (re-pattern (:regex r)) (str/trim (blocks/text-of b))))
         (some? (match-at b r)))))

(defn- section-body [bs rs i lines {:keys [search] :as st}]
  (let [r (nth rs i)
        boundary (or (some (fn [fr] (first (filter #(match-at (nth bs %) fr) (range search (count bs))))) (subvec rs (inc i)))
                     (count bs))
        value (if (< search boundary)
                (str/trim (str/join "\n" (subvec lines (first (:map (nth bs search))) (min (count lines) (second (:map (nth bs (dec boundary))))))))
                "")]
    (cond-> (assoc st :curr (dec boundary))
      (or (seq value) (not (marker/optional? (first (:props-info r))))) (assoc-in [:out (first (:props r))] value))))

(defn- regular [bs rs i {:keys [search] :as st}]
  (let [r (nth rs i)
        end (if (:optional-block r) (min (inc search) (count bs)) (count bs))]
    (loop [b search st st]
      (if (or (>= b end) (and (not= -1 (:curr st)) (not= b (:curr st))))
        st
        (let [blk (nth bs b) v (when (= (:type blk) (:outer-type r)) (match-at blk r))]
          (cond
            (nil? v) (recur (inc b) st)
            (and (:optional-block r) (some #(and (:anchor %) (matches-position? blk %)) (subvec rs (inc i)))) st
            (:anchor r) (assoc st :curr b)
            :else (-> st (update :out merge v) (assoc :curr b))))))))

(defn extract-raw
  "The untyped values (`{field value}`, string keys) that the template text
   `template` captures from Markdown `text`."
  [template text]
  (let [bs (blocks/parse text) rs (recipe/recipes template) lines (vec (str/split-lines text))]
    (:out (reduce (fn [st i]
                    (let [r (nth rs i)
                          st (if (not= (:outer-index r) (:last st))
                               (assoc st :search (max (:search st) (inc (:curr st))) :last (:outer-index r) :curr -1)
                               st)]
                      (if (= :section-body (:capture r)) (section-body bs rs i lines st) (regular bs rs i st))))
                  {:out {} :search 0 :last -1 :curr -1} (range (count rs))))))

(defn extract
  "The typed payload of `descriptor` extracted from Markdown `text`: declared
   fields with defaults and coercion applied (`descriptor/finalize`). Raises
   `:validate/missing` when a required field is absent."
  [descriptor text]
  (descriptor/finalize descriptor (extract-raw (:template descriptor) text)))
