(ns kimun.surface.models.selector-cst
  "The selector steps that only the tree-sitter CST can serve (docs/v2/16 §3.3):
   C3 `:want :find` (a section by heading and regex, wherever it sits), C4 a
   nested list that keeps its hierarchy, C5 `:want :table` (a structured
   `pipe_table`). The SLDB-MD block view (`kimun.surface.models.blocks`)
   flattens nested lists and has no section tree, so these steps read the
   pod's block CST (`kimun.host.treesitter/parse-block`) directly. A step is
   plain EDN (§3.1); the regex is a string (`:regex`), never `#\"…\"`, which is
   not portable EDN. With no pod the step raises `:docs/parser-unavailable`:
   there is no silent fallback to the flat view. Called by
   `kimun.surface.models.selector`; the steps of a selector that are not CST
   steps never reach this namespace."
  (:require [clojure.string :as str]
            [kimun.host.treesitter :as ts]
            [kimun.kernel.err :as err]))

(defn cst-step?
  "True when selector step `s` needs the CST: `:want :find` (C3), `:want :table`
   (C5), or a list step with `:preserve-nesting` (C4, the shape of
   `spec16_examples.edn`)."
  [s]
  (and (map? s)
       (or (contains? #{:find :table} (:want s))
           (true? (:preserve-nesting s)))))

(defn- rows-end
  "Exclusive end row of a CST node: a node that ends at column 0 ends on the
   previous row."
  [node]
  (let [[er ec] (:end_point node)] (if (zero? ec) er (inc er))))

(defn- row-of [node] (first (:start_point node)))

(defn- children-of [node type] (filter #(= type (:type %)) (:children node)))

(defn- heading-info
  "`{:level :title :end}` of a section's `atx_heading`, or nil."
  [section]
  (when-let [h (first (children-of section "atx_heading"))]
    (let [marker (some #(when-let [m (re-matches #"atx_h([1-6])_marker" (:type %))] (parse-long (second m))) (:children h))
          inline (first (children-of h "inline"))]
      (when marker
        {:level marker :title (str/trim (or (:text inline) "")) :end (rows-end h)}))))

(defn- sections
  "Every `section` node of the CST, document order, with its heading info and
   the line range of its body: from the end of the heading to the end of the
   section (which includes its sub-sections)."
  [node]
  (mapcat (fn [c]
            (if (= "section" (:type c))
              (let [h (heading-info c)]
                (cons (assoc h :node c :from (:end h) :to (rows-end c)) (sections c)))
              (sections c)))
          (:children node)))

(defn- where-pred
  "Predicate over a section for `:where {:outer-tag \"h2\" :regex \"…\"}`: the
   level named by the tag (any when absent) and the regex, which matches the
   whole trimmed title (when absent, any title)."
  [{:keys [outer-tag regex]}]
  (let [level (some->> outer-tag (re-matches #"h([1-6])") second parse-long)
        re (some-> regex re-pattern)]
    (fn [s] (and (or (nil? level) (= level (:level s)))
                 (or (nil? re) (some? (re-matches re (:title s))))))))

(defn- find-section [root where]
  (first (filter (where-pred where) (sections root))))

(defn- descend
  "First node of `type` in preorder under `node`, or nil."
  [node type]
  (some (fn [c] (if (= type (:type c)) c (descend c type))) (:children node)))

(defn- scope
  "The CST node a list or table step reads: the section `:where` finds, or the
   whole document when the step has no `:where`. `::absent` when there is no
   such section."
  [root {:keys [where]}]
  (if where
    (if-let [s (find-section root where)] (:node s) ::absent)
    root))

(defn- item-text [item]
  (->> (children-of item "paragraph")
       first
       :children
       (filter #(= "inline" (:type %)))
       (map :text)
       (str/join "\n")
       str/trim))

(defn- list-items
  "Preorder `[{:text :depth}]` of the items of CST `list` node `l`: a sub-list's
   items follow their parent with `:depth` one deeper, so the nesting is not
   flattened away (C4)."
  [l depth]
  (vec (mapcat (fn [item]
                 (cons {:text (item-text item) :depth depth}
                       (mapcat #(list-items % (inc depth)) (children-of item "list"))))
               (children-of l "list_item"))))

(defn- cell-text [c] (str/trim (str/replace (or (:text c) "") "\\|" "|")))

(defn- table-rows
  "`[[cell …] …]` of a `pipe_table` node: the header row first, then the body
   rows, cells trimmed; the delimiter row is dropped."
  [t]
  (vec (keep (fn [r]
               (when (#{"pipe_table_header" "pipe_table_row"} (:type r))
                 (mapv cell-text (children-of r "pipe_table_cell"))))
             (:children t))))

(defn- section-body [lines {:keys [from to]}]
  (str/trim (str/join "\n" (subvec lines (min from (count lines)) (min to (count lines))))))

(defn- capture [root lines step]
  (cond
    (= :find (:want step))
    (when-let [s (find-section root (:where step))] (section-body lines s))

    (= :table (:want step))
    (let [sc (scope root step)]
      (when-not (= ::absent sc) (some-> (descend sc "pipe_table") table-rows)))

    :else
    (let [sc (scope root step)]
      (when-not (= ::absent sc) (some-> (descend sc "list") (list-items 0))))))

(defn extract
  "`{field value}` (string keys, the step's `:key`) captured by the CST `steps`
   from Markdown `text`. A step whose section, list or table is not found
   leaves its field out. Raises `:docs/parser-unavailable` when the
   tree-sitter pod is not available, `:models/invalid` for a step with no `:key`."
  [steps text]
  (doseq [s steps]
    (when-not (string? (:key s))
      (err/raise :models/invalid (str "CST selector step without a string :key: " (pr-str s)) {:selector-step s})))
  (when-not (ts/available?)
    (err/raise :docs/parser-unavailable
               "A CST selector step (:find, :table, :preserve-nesting) needs the tree-sitter pod; it is not available"
               {:steps (mapv :key steps)}))
  (let [text (if (str/ends-with? text "\n") text (str text "\n"))
        root (first (ts/parse-block text))
        lines (vec (str/split text #"\n" -1))]
    (into {} (keep (fn [s] (when-some [v (capture root lines s)] [(:key s) v]))) steps)))
