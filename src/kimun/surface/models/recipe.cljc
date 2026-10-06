(ns kimun.surface.models.recipe
  "The recipe of a template (docs/v2/07 §4): a derived view, never persisted —
   one entry per marker-bearing or literal block of the template, in document
   order, saying how the matching block of a document yields its fields.
   Semantics are those of sldb v1's `TemplateExtractor`."
  (:require [clojure.string :as str]
            [kimun.surface.models.blocks :as blocks]
            [kimun.surface.models.marker :as marker]
            [sldb.kernel.err :as err]))

(defn- list-recipes [b]
  (for [item (:children b)
        :let [text (str/trim (blocks/text-of item))
              m (some-> (re-find marker/pattern text) second marker/parse)]
        :when (and m (marker/data? m))]
    {:handler :list :name (:name m) :marker m :item-template text}))

(defn- table-recipes [b]
  (let [rows (blocks/rows b)
        cols (when (>= (count rows) 2)
               (into (sorted-map)
                     (keep-indexed (fn [i cell]
                                     (let [text (str/trim (blocks/text-of cell))
                                           m (some-> (re-find marker/pattern text) second marker/parse)]
                                       (when (and m (marker/data? m)) [i {:marker m :cell-template text}])))
                                   (:children (last rows)))))]
    (when (seq cols)
      [{:handler :table :name (:name (:marker (val (first cols)))) :col-markers cols
        :props (mapv (comp :name :marker) (vals cols))}])))

(defn- yaml-key [line m-start m-text lines idx]
  (or (second (re-matches #"^\s*([A-Za-z0-9_-]+)\s*:\s*$" (subs line 0 m-start)))
      (when (= (str/trim line) m-text)
        (when-let [prev (first (remove str/blank? (map str/trim (reverse (take idx lines)))))]
          (second (re-matches #"^([A-Za-z0-9_-]+)\s*:\s*$" prev))))))

(defn- yaml-recipes [b]
  (let [content (str/trim (:content b))
        found (re-seq marker/pattern content)
        single (when (= 1 (count found)) (marker/parse (second (first found))))]
    (if (and single (marker/data? single) (marker/trait? single "dict"))
      [{:handler :yaml :name (:name single) :marker single}]
      (let [lines (vec (str/split-lines content))
            ms (vec (keep-indexed
                     (fn [idx line]
                       (let [mt (re-matcher marker/pattern line)]
                         (when (.find mt)
                           (let [m (marker/parse (.group mt 1))]
                             (when (marker/data? m)
                               (when-let [k (yaml-key line (.start mt) (.group mt) lines idx)]
                                 {:key k :marker m}))))))
                     lines))]
        (when (seq ms) [{:handler :yaml :markers ms}])))))

(defn- lone? [text props regex]
  (and (= 1 (count props)) (str/starts-with? text "⸢") (str/ends-with? text "⸥") (= regex "^(.*?)$")))

(defn- text-recipes [b]
  (let [content (str/trim (blocks/text-of b))]
    (when (or (str/includes? content "⸢") (str/includes? content "{{"))
      (let [{:keys [props regex dynamic?]} (marker/text-pattern content)
            one (when (= 1 (count props)) (first props))]
        (when dynamic?
          (if-let [cols (and one (str/starts-with? content "⸢") (str/ends-with? content "⸥") (marker/table-columns one))]
            [{:handler :table :name (:name one) :marker one :table-columns cols :props [(:name one)]
              :outer-type "table" :outer-tag "table"}]
            (let [lone (lone? content props regex)]
              (when (and lone (not (contains? #{"paragraph" "heading"} (:type b))))
                (err/raise :models/invalid
                           (str "Field '" (:name one) "' is a lone marker in a '" (:type b) "' block; move it to its own paragraph.")
                           {:field (:name one) :block (:type b)}))
              [(cond-> {:handler :text :props (mapv :name props) :props-info props :regex regex}
                 (and one (marker/optional? one) (str/starts-with? content "⸢") (str/ends-with? content "⸥")) (assoc :optional-block true)
                 (empty? props) (assoc :anchor true)
                 (and lone (= "paragraph" (:type b))) (assoc :capture :section-body))])))))))

(defn- anchor [b]
  (let [content (str/trim (blocks/text-of b))]
    {:handler :text :anchor true :props []
     :regex (if (seq content) (str "^" (java.util.regex.Pattern/quote content) "$") "^$")}))

(defn markers-of
  "Every marker a recipe entry carries."
  [r]
  (cond (:props-info r) (:props-info r)
        (:marker r) [(:marker r)]
        (:markers r) (mapv :marker (:markers r))
        (:col-markers r) (mapv :marker (vals (:col-markers r)))
        :else []))

(defn recipes
  "The recipe of template text `template`: a vector of entries
   `{:outer-index :outer-type :outer-tag :handler …}`. Raises `:models/invalid`
   when a field has more than one `rev` marker or a lone marker sits in a
   block that cannot own a section body."
  [template]
  (let [rs (vec (mapcat
                 (fn [i b]
                   (let [own (case (:type b)
                               ("bullet_list" "ordered_list") (list-recipes b)
                               "table" (table-recipes b)
                               ("fence" "front_matter") (or (yaml-recipes b) (text-recipes b))
                               (text-recipes b))]
                     (map #(merge {:outer-index i :outer-type (:type b) :outer-tag (:tag b)} %)
                          (if (seq own) own [(anchor b)]))))
                 (range) (blocks/parse template)))
        dup (->> rs (mapcat #(if (:col-markers %) [] (markers-of %))) (filter #(= "rev" (:kind %)))
                 (map :name) frequencies (filter #(> (val %) 1)) first)]
    (when dup
      (err/raise :models/invalid (str "Multiple canonical 'rev' markers found for field '" (key dup) "'.") {:field (key dup)}))
    rs))

(defn data-fields
  "Names of the fields a template extracts (`rev` and `optrev` markers)."
  [template]
  (set (map :name (filter marker/data? (mapcat markers-of (recipes template))))))
