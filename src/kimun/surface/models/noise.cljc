(ns kimun.surface.models.noise
  "Structural noise of a document (v1 `structural_noise`, `heading_scan`,
   `frontmatter_keys`): the headings its model's template never declares and
   the frontmatter keys the model never declares. A heading inside a field is
   a field the model is missing, and an undeclared key is lost on the first
   re-render; both are silent losses, so the write paths refuse them.

   Pure: descriptor and Markdown in, a plain map out. Headings are read with
   the block segmentation of `kimun.surface.models.blocks`, so a `#` inside a
   code fence, a list or a quote is not a heading."
  (:require [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [kimun.surface.models.blocks :as blocks]
            [kimun.surface.models.marker :as marker]))

(defn- heading-of
  "`{:level :text :line :raw}` of a heading block, nil for any other block or a heading with no title."
  [{:keys [tag map] :as block}]
  (when (re-matches #"h[1-6]" (or tag ""))
    (let [text (str/trim (or (blocks/leaf-text block) (:content block) ""))
          level (parse-long (subs tag 1))]
      (when (seq text)
        {:level level :text text :line (inc (first map)) :raw (str (apply str (repeat level "#")) " " text)}))))

(defn- scan-headings [markdown]
  (into [] (keep heading-of) (blocks/parse markdown)))

(defn- wildcard? [title] (boolean (re-find marker/pattern title)))

(defn- declared
  "`[literals wildcard-levels]` of a template: a heading whose title holds a marker opens its whole level."
  [template]
  (reduce (fn [[lits levels] {:keys [level text]}]
            (if (wildcard? text) [lits (conj levels level)] [(conj lits [level text]) levels]))
          [#{} #{}]
          (scan-headings (or template ""))))

(defn- front-matter [markdown]
  (some #(when (= "front_matter" (:type %)) (:content %)) (blocks/parse markdown)))

(defn- document-keys [markdown]
  (if-let [content (front-matter markdown)]
    (let [data (try (yaml/parse-string content :keywords false) (catch #?(:clj Exception :cljs :default) _ nil))]
      (if (map? data) (into #{} (map str) (keys data)) #{}))
    #{}))

(defn- template-keys [template]
  (into #{} (keep #(second (re-matches #"^([A-Za-z0-9_-]+)\s*:.*" %)))
        (some-> (front-matter (or template "")) str/split-lines)))

(defn structural-noise
  "The noise of `markdown` under `descriptor`:
   `{:headings [{:text :level :line :raw}…] :frontmatter [\"key\"…]}`, both
   vectors, empty when clean. `:headings` are the document's headings, in
   order, that the template neither lists literally (same level and title) nor
   opens with a marker heading of that level (`# ⸢rev•title⸥` declares every
   `#`); `:line` is 1-based and `:raw` the heading as written (`## Title`).
   `:frontmatter` are the sorted top-level keys of the document's frontmatter
   that the template's frontmatter does not name. A template with no
   frontmatter declares no key. `opts` `:related` (descriptors of related
   models) is accepted for the contract but v1 consults none: a child model
   carries the whole template, so only `descriptor` decides."
  ([descriptor markdown] (structural-noise descriptor markdown {}))
  ([descriptor markdown _opts]
   (let [template (:template descriptor)
         [literals levels] (declared template)]
     {:headings (into [] (remove #(or (contains? levels (:level %)) (contains? literals [(:level %) (:text %)])))
                      (scan-headings markdown))
      :frontmatter (vec (sort (remove (template-keys template) (document-keys markdown))))})))

(defn noisy?
  "True when `result` (of `structural-noise`) names any heading or key."
  [result]
  (boolean (or (seq (:headings result)) (seq (:frontmatter result)))))

(defn describe
  "One line naming every noisy heading and key of `result`, or \"clean\"."
  [result]
  (let [parts (cond-> []
                (seq (:headings result)) (conj (str "headings the model does not declare: "
                                                    (str/join ", " (map #(str "`" (:raw %) "` (line " (:line %) ")") (:headings result)))))
                (seq (:frontmatter result)) (conj (str "frontmatter keys the model does not declare: "
                                                       (str/join ", " (map #(str "`" % "`") (:frontmatter result))))))]
    (if (seq parts) (str/join "; " parts) "clean")))
