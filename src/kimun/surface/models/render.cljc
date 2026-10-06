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
            [kimun.surface.models.extract :as extract]
            [kimun.surface.models.marker :as marker]))

(defn- fold-plain
  "PyYAML folds a long plain scalar at width 80: a single space reached past
   column 80 becomes a line break plus the continuation indent."
  [line]
  (let [[_ indent head value] (re-matches #"^(\s*)((?:- )*[^\s:'\"]+: |(?:- )+)?(.*)$" line)]
    (if (or (<= (count line) 80) (nil? head) (re-find #"^[\"|>\[{]" value) (not (str/includes? value " ")))
      line
      (let [pad (apply str (repeat (+ (count indent) (+ (* 2 (count (re-seq #"- " (str/replace head #": $" "")))) (if (str/ends-with? head ": ") 2 0))) " "))]
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

(defn- wrap
  "`text` as marker `m` wrote it, wrapped in v1's span sentinels (`U+E000 meta
   U+E001 text U+E002`, the meta as EDN) when a recording scope `rec`
   (`{:prefix item-path :scalar-root name}`) is active; `text` itself otherwise."
  [rec m text]
  (if (nil? rec)
    text
    (let [{:keys [prefix scalar-root]} rec n (:name m)
          path (cond (str/blank? prefix) n (= scalar-root n) prefix :else (str prefix "/" n))]
      (str "\ue000" (pr-str {:path path :marker (:kind m) :traits (:traits m)}) "\ue001" text "\ue002"))))

(defn- drop-trailing-newline
  "v1's template pass loses one trailing newline; when the text ends in span
   sentinels (only under a recording scope `rec`) the newline they shield is
   the one lost."
  [rec s]
  (cond (str/ends-with? s "\n") (subs s 0 (dec (count s)))
        (and rec (str/ends-with? s "\ue002"))
        (let [body (str/replace s #"\ue002+$" "")]
          (if (str/ends-with? body "\n") (str (subs body 0 (dec (count body))) (subs s (count body))) s))
        :else s))

(defn- absent-optional-line? [line data]
  (when-let [[_ inner] (re-matches #"^\s*(?:[A-Za-z0-9_-]+\s*:\s*)?⸢([^⸥]+)⸥\s*$" line)]
    (let [m (marker/parse inner)] (and (marker/optional? m) (nil? (get data (:name m)))))))

(defn replace-markers
  "`text` with every marker replaced by its value in `data`; a line that is
   only an absent `optrev` (optionally behind `key:`) is dropped. One trailing
   newline of the result is dropped, as v1's template pass did."
  ([text data] (replace-markers text data nil))
  ([text data rec]
   (->> (str/split text #"\n" -1)
        (remove #(absent-optional-line? % data))
        (map (fn [line] (str/replace line marker/pattern
                                     (fn [[whole inner]]
                                       (let [m (marker/parse inner) out (resolve-marker whole m data)]
                                         (if (= out whole) out (wrap rec m out)))))))
        (str/join "\n")
        (drop-trailing-newline rec))))

(defn- render-list [b lines data rec]
  (let [[s e] (:map (first (:children b)))
        item-text (str/join "\n" (subvec lines s e))
        block-text (str/join "\n" (apply subvec lines (:map b)))]
    (if-let [[_ inner] (and (seq (:children b)) (re-find marker/pattern item-text))]
      (let [root (:name (marker/parse inner)) items (get data root [])]
        (cond (not (sequential? items)) ""
              (empty? items) (replace-markers item-text {root ""} rec)
              :else (str/join "\n" (map-indexed
                                    (fn [i item]
                                      (replace-markers item-text (if (map? item) item {root item})
                                                       (when rec {:prefix (str root "/" i) :scalar-root (when-not (map? item) root)})))
                                    items))))
      (replace-markers block-text data rec))))

(defn- render-table [block-text data rec]
  (let [ls (str/split-lines block-text)
        [header sep row-tpl] ls
        inner (when (>= (count ls) 3) (second (re-find marker/pattern row-tpl)))]
    (if-not inner
      (replace-markers block-text data rec)
      (let [v (get data (:name (marker/parse inner)) {})
            items (cond (sequential? v) v
                        (map? v) (map v (sort-by #(if (re-matches #"\d+" (str %)) [0 (parse-long (str %))] [1 (str %)]) (keys v)))
                        :else [])
            rows (map #(replace-markers row-tpl % rec) items)]
        (if (seq rows) (str/join "\n" (concat [header sep] rows)) (replace-markers block-text data rec))))))

(defn- render-front-matter [block-text data rec]
  (let [body (-> (str/trim block-text) (str/replace #"^---\n?" "") (str/replace #"\n?---$" ""))
        line (fn [l]
               (if-let [[_ k inner] (re-matches #"^\s*([A-Za-z0-9_-]+)\s*:\s*⸢([^⸥]+)⸥\s*$" l)]
                 (let [m (marker/parse inner) v (get data (:name m))]
                   (cond (and (nil? v) (marker/optional? m)) nil
                         (and (nil? v) (not= "render" (:kind m))) (replace-markers l data rec)
                         (nil? rec) (dump {k v})
                         :else (let [dumped (dump {k v}) head (str k ":")]
                                 (if (str/starts-with? dumped head)
                                   (let [[_ lead value] (re-matches #"(?s)( *)(.*)" (subs dumped (count head)))]
                                     (str head lead (wrap rec m value)))
                                   dumped))))
                 (replace-markers l data rec)))]
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

(defn- render-raw
  "The rendered blocks of `template` joined by blank lines, not yet trimmed;
   with a recording scope `rec` every written value is sentinel-wrapped."
  [template data rec]
  (let [lines (vec (str/split-lines template))]
    (->> (blocks/parse template)
         (map (fn [b]
                (let [block-text (str/join "\n" (apply subvec lines (:map b)))]
                  (case (:type b)
                    ("bullet_list" "ordered_list") (render-list b lines data rec)
                    "table" (render-table block-text data rec)
                    "front_matter" (render-front-matter block-text data rec)
                    (replace-markers block-text data rec)))))
         (str/join "\n\n"))))

(defn render-template
  "Markdown of template text `template` filled with `data` (string keys)."
  [template data]
  (str/trim (render-raw template data nil)))

(defn- spec-get [spec k] (let [v (get spec k)] (if (some? v) v (get spec (keyword k)))))

(defn- fill-line
  "v1's `str.format(**payload)` for a composition line template: `{field}`
   placeholders (`{{`/`}}` escape a brace); nil when a placeholder names a
   field the payload does not have (v1's KeyError, which skips the ref)."
  [template payload]
  (let [names (keep second (re-seq #"\{\{|\}\}|\{([^{}]*)\}" template))]
    (when (every? #(contains? payload %) names)
      (str/replace template #"\{\{|\}\}|\{([^{}]*)\}"
                   (fn [[whole n]] (cond (= "{{" whole) "{" (= "}}" whole) "}"
                                         :else (let [v (get payload n)] (if (nil? v) "None" (pystr v)))))))))

(defn- compose-field
  "The text of one composed field (v1 `StructuredNLDoc.compose_field`): one
   line per ref of the source field, each the line template filled with the
   payload extracted from the referenced document. A ref with no text, an
   invalid referenced document or an unfillable line is skipped silently."
  [spec data {:keys [ref-text ref-model]}]
  (let [source (spec-get spec "source_field")
        refs (when source (get data source))
        model (when-let [m (spec-get spec "model")] (when ref-model (ref-model m)))
        line (fn [raw]
               (try
                 (when-let [text (when ref-text (ref-text (-> (str raw) str/trim (str/replace #"^`+|`+$" ""))))]
                   (let [p (extract/extract model text)]
                     (when (seq p) (fill-line (or (spec-get spec "template") "- {title}") p))))
                 (catch Exception _ nil)))]
    (if (or (str/blank? source) (not (sequential? refs)) (nil? model))
      ""
      (str/join (or (spec-get spec "separator") "\n") (remove str/blank? (map line refs))))))

(defn compositions
  "The compositions a descriptor declares, `{name {\"source_field\" \"model\"
   \"template\" [\"separator\"]}}` (keyword keys accepted too), read from
   `:compositions` or, as v1's exporter writes it, `:py/compositions`."
  [descriptor]
  (or (:compositions descriptor) (:py/compositions descriptor) {}))

(defn- with-compositions [descriptor data ctx]
  (reduce-kv (fn [d n spec] (assoc d (name n) (compose-field spec d ctx))) data (compositions descriptor)))

(defn render
  "Markdown of `payload` under `descriptor`: defaults and coercion applied
   (`descriptor/finalize`), then the template filled. Composed fields
   (`⸢render•name⸥` over `compositions`) are expanded through the optional
   context `ctx`, two caller-supplied functions so the engine does no I/O:
   `{:ref-text (fn [ref] markdown-or-nil) :ref-model (fn [model-ref]
   descriptor-or-nil)}`. `ref` is the ref as written in the payload (trimmed,
   backticks stripped): resolving it against a root is the caller's job.
   Without a context every composed field renders empty."
  ([descriptor payload] (render descriptor payload nil))
  ([descriptor payload ctx]
   (render-template (:template descriptor)
                    (with-compositions descriptor (with-carriers descriptor (descriptor/finalize descriptor payload)) ctx))))

(defn render-marked
  "The instrumented render behind `kimun.surface.models.segments`: what
   `render` produces before its final trim, with every value a marker wrote
   wrapped in v1's span sentinels — `U+E000 meta U+E001 text U+E002`, the meta
   being the EDN `{:path :marker :traits}` of the span (list items scoped as
   `root/i[/name]`). Decoding the sentinels away and trimming gives exactly
   `(render descriptor payload ctx)`. The plain render never takes this path."
  [descriptor payload ctx]
  (render-raw (:template descriptor)
              (with-compositions descriptor (with-carriers descriptor (descriptor/finalize descriptor payload)) ctx)
              {:prefix "" :scalar-root nil}))
