(ns kimun.surface.models.render
  "Payload → Markdown (docs/v2/07 §6): walks the blocks of the model's
   template and injects each marker — scalars in place, lists by repeating the
   item template, tables by repeating the row template, mappings as YAML —
   omitting the line of an absent `optrev`. Byte-compatible with sldb v1's
   `SLDBRenderer` for templates without `py•` or Jinja expressions."
  (:require [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [kimun.surface.models.blocks :as blocks]
            [kimun.surface.models.descriptor :as descriptor]
            [kimun.surface.models.marker :as marker]))

(defn- fold-plain
  "PyYAML folds a long plain scalar at width 80: a single space reached past
   column 80 becomes a line break plus the continuation indent."
  [line]
  (let [[_ indent head value] (re-matches #"^(\s*)((?:- )?[^\s:'\"]+: |- )?(.*)$" line)]
    (if (or (<= (count line) 80) (nil? head) (re-find #"^[\"|>\[{]" value) (not (str/includes? value " ")))
      line
      (let [pad (apply str (repeat (+ 2 (count indent)) " "))]
        (loop [toks (re-seq #"\S+| +" value) col (+ (count indent) (count head)) out [indent head]]
          (if-let [t (first toks)]
            (if (and (= t " ") (> col 80) (next toks))
              (recur (rest toks) (count pad) (conj out "\n" pad))
              (recur (rest toks) (+ col (count t)) (conj out t)))
            (apply str out)))))))

(defn- dump [v]
  (->> (str/split-lines (str/trim (yaml/generate-string v :dumper-options {:flow-style :block})))
       (map fold-plain)
       (str/join "\n")))

(defn- pystr [v] (cond (true? v) "True" (false? v) "False" :else (str v)))

(defn- table-md [value cols]
  (if (empty? cols)
    "| |\n| --- |"
    (str/join "\n" (concat [(str "| " (str/join " | " cols) " |") (str "| " (str/join " | " (repeat (count cols) "---")) " |")]
                           (for [row (if (sequential? value) value [])]
                             (str "| " (str/join " | " (map #(let [c (get (if (map? row) row {}) % "")]
                                                                 (if (nil? c) "" (-> (pystr c) (str/replace "\n" " ") (str/replace "|" "\\|"))))
                                                              cols)) " |"))))))

(defn- resolve-marker [whole m data]
  (let [v (get data (:name m))]
    (cond
      (nil? v) (if (contains? #{"optrev" "render"} (:kind m)) "" whole)
      (marker/table-columns m) (table-md v (or (seq (marker/table-columns m))
                                               (when (and (sequential? v) (map? (first v))) (map str (keys (first v)))) []))
      (or (marker/trait? m "dict") (map? v) (sequential? v))
      (if (and (marker/trait? m "list") (not (coll? v))) (pystr v) (dump v))
      :else (pystr v))))

(defn- absent-optional-line? [line data]
  (when-let [[_ inner] (re-matches #"^\s*(?:[A-Za-z0-9_-]+\s*:\s*)?⸢([^⸥]+)⸥\s*$" line)]
    (let [m (marker/parse inner)] (and (marker/optional? m) (nil? (get data (:name m)))))))

(defn replace-markers
  "`text` with every marker replaced by its value in `data`; a line that is
   only an absent `optrev` (optionally behind `key:`) is dropped. One trailing
   newline of the result is dropped, as v1's template pass did."
  [text data]
  (->> (str/split text #"\n" -1)
       (remove #(absent-optional-line? % data))
       (map (fn [line] (str/replace line marker/pattern
                                    (fn [[whole inner]] (resolve-marker whole (marker/parse inner) data)))))
       (str/join "\n")
       (#(if (str/ends-with? % "\n") (subs % 0 (dec (count %))) %))))

(defn- render-list [b lines data]
  (let [[s e] (:map (first (:children b)))
        item-text (str/join "\n" (subvec lines s e))
        block-text (str/join "\n" (apply subvec lines (:map b)))]
    (if-let [[_ inner] (and (seq (:children b)) (re-find marker/pattern item-text))]
      (let [root (:name (marker/parse inner)) items (get data root [])]
        (cond (not (sequential? items)) ""
              (empty? items) (replace-markers item-text {root ""})
              :else (str/join "\n" (map #(replace-markers item-text (if (map? %) % {root %})) items))))
      (replace-markers block-text data))))

(defn- render-table [block-text data]
  (let [ls (str/split-lines block-text)
        [header sep row-tpl] ls
        inner (when (>= (count ls) 3) (second (re-find marker/pattern row-tpl)))]
    (if-not inner
      (replace-markers block-text data)
      (let [v (get data (:name (marker/parse inner)) {})
            items (cond (sequential? v) v
                        (map? v) (map v (sort-by #(if (re-matches #"\d+" (str %)) [0 (parse-long (str %))] [1 (str %)]) (keys v)))
                        :else [])
            rows (map #(replace-markers row-tpl %) items)]
        (if (seq rows) (str/join "\n" (concat [header sep] rows)) (replace-markers block-text data))))))

(defn- render-front-matter [block-text data]
  (let [body (-> (str/trim block-text) (str/replace #"^---\n?" "") (str/replace #"\n?---$" ""))
        line (fn [l]
               (if-let [[_ k inner] (re-matches #"^\s*([A-Za-z0-9_-]+)\s*:\s*⸢([^⸥]+)⸥\s*$" l)]
                 (let [m (marker/parse inner) v (get data (:name m))]
                   (cond (and (nil? v) (marker/optional? m)) nil
                         (and (nil? v) (not= "render" (:kind m))) (replace-markers l data)
                         :else (dump {k v})))
                 (replace-markers l data)))]
    (str "---\n" (str/join "\n" (keep line (str/split-lines body))) "\n---")))

(def ^:private ordered-empty
  "An insertion-ordered empty map (the one clj-yaml parses mappings into)."
  (yaml/parse-string "{}" :keywords false))

(defn- with-carriers
  "Adds to `data` the value of every `:exclude` carrier field: the mapping of
   the declared fields that have no marker of their own in the template."
  [descriptor data]
  (let [own (set (map (comp :name marker/parse second) (re-seq marker/pattern (:template descriptor))))]
    (reduce (fn [d {:keys [name]}]
              (assoc d name (into ordered-empty
                                  (for [f (:fields descriptor) :let [n (:name f)]
                                        :when (and (not (:exclude f)) (not (own n)) (some? (get data n)))]
                                    [n (get data n)]))))
            data (filter :exclude (:fields descriptor)))))

(defn render-template
  "Markdown of template text `template` filled with `data` (string keys)."
  [template data]
  (let [lines (vec (str/split-lines template))]
    (->> (blocks/parse template)
         (map (fn [b]
                (let [block-text (str/join "\n" (apply subvec lines (:map b)))]
                  (case (:type b)
                    ("bullet_list" "ordered_list") (render-list b lines data)
                    "table" (render-table block-text data)
                    "front_matter" (render-front-matter block-text data)
                    (replace-markers block-text data)))))
         (str/join "\n\n")
         str/trim)))

(defn render
  "Markdown of `payload` under `descriptor`: defaults and coercion applied
   (`descriptor/finalize`), then the template filled."
  [descriptor payload]
  (render-template (:template descriptor) (with-carriers descriptor (descriptor/finalize descriptor payload))))
