(ns kimun.surface.models.selector
  "The selector engine (docs/v2/16 §3): interprets a vector of EDN steps over
   the SLDB-MD block view of a Markdown text and returns the captured values.
   A step is the plain-data entry of a template's recipe
   (`kimun.surface.models.recipe/recipes`): `{:outer-index :outer-type
   :outer-tag :handler …}`; a vector of steps survives `pr-str` and
   `edn/read-string` (§3.1). Two modes, named as the descriptor's `:compat`
   (docs/v2/16 §3.5): `:legacy-v1-template` (default) keeps the quirks of
   sldb v1's `DataExtractor` on purpose (R3 §7); `:strict` corrects the two
   the spec names. Pure."
  (:require [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [kimun.surface.models.blocks :as blocks]
            [kimun.surface.models.marker :as marker]
            [kimun.surface.models.selector-cst :as cst]
            [kimun.kernel.err :as err]))

;; SnakeYAML resolves a date (`2026-06-14`) and a datetime to the same
;; java.util.Date, so a midnight datetime cannot be told from a date after
;; parsing. v1 (PyYAML) keeps them apart: a date prints "2026-06-14", a
;; datetime "2026-06-14T00:00:00". Quoting date-only plain scalars before
;; parsing makes them strings (v1's output); every Date left is a datetime.
(def ^:private date-line
  "A block line whose value is a bare date: optional dash/key prefix, the date,
   optional trailing comment (PyYAML still reads a date there)."
  #"^(\s*(?:(?:-\s+)+(?:[^\s:#'\"\[\]{},|>-][^:#\n]*:\s+)?|[^\s:#'\"\[\]{},|>-][^:#\n]*:\s+))(\d{4}-\d{2}-\d{2})(\s*(?:#.*)?)$")

(def ^:private flow-line
  "A block line whose value opens a flow collection: the dash/key prefix and
   the flow text. A `[` or `{` anywhere else in a line is ordinary text."
  #"^(\s*(?:(?:-\s+)+(?:[^\s:#'\"\[\]{},|>-][^:#\n]*:\s+)?|[^\s:#'\"\[\]{},|>-][^:#\n]*:\s+))([\[{].*)$")

(def ^:private flow-date #"^(\d{4}-\d{2}-\d{2})\s*[,\]}]")

(defn- quote-flow
  "Quotes the bare dates of one line of flow text: a date token right after
   `[`, `{`, `,` or `key: ` and right before `,`, `]`, `}`. Quoted strings and
   a trailing comment are copied verbatim, so no text is ever rewritten."
  [flow]
  (let [n (count flow)]
    (loop [i 0 quoted nil prev nil out (transient [])]
      (if (>= i n)
        (apply str (persistent! out))
        (let [c (nth flow i)
              spaced? (and (pos? i) (#{\space \tab} (nth flow (dec i))))]
          (cond
            quoted (recur (inc i)
                          (when-not (and (= c quoted) (not (and (= quoted \") (pos? i) (= \\ (nth flow (dec i)))))) quoted)
                          c (conj! out c))
            (#{\' \"} c) (recur (inc i) c c (conj! out c))
            (and (= c \#) spaced?) (apply str (persistent! (conj! out (subs flow i))))
            (#{\space \tab} c) (recur (inc i) nil prev (conj! out c))
            :else
            (let [m (when (or (#{\[ \{ \,} prev) (and (= prev \:) spaced?))
                      (re-find flow-date (subs flow i)))]
              (if m
                (recur (+ i 10) nil \' (conj! out (str "'" (second m) "'")))
                (recur (inc i) nil c (conj! out c))))))))))

(def ^:private block-indicator #"(?:^|[\s:-])[|>][-+0-9]*\s*(?:#.*)?$")

(defn- indent-of [line] (count (take-while #(= \space %) line)))

(defn- quote-line [line]
  (cond (re-find date-line line) (str/replace line date-line "$1'$2'$3")
        (re-find flow-line line) (let [[_ prefix flow] (re-find flow-line line)] (str prefix (quote-flow flow)))
        :else line))

(defn- quote-dates
  "Quotes bare dates line by line, leaving the body of block scalars (`|`,
   `>`, any chomping/indent indicator) untouched: every following line that
   is blank or indented deeper than the indicator line."
  [yaml-text]
  (loop [lines (str/split yaml-text #"\n" -1) in-block nil out []]
    (if-let [[line & more] (seq lines)]
      (if (and in-block (or (str/blank? line) (> (indent-of line) in-block)))
        (recur more in-block (conj out line))
        (recur more (when (re-find block-indicator line) (indent-of line)) (conj out (quote-line line))))
      (str/join "\n" out))))

(defn- plain [v]
  (walk/postwalk (fn [x] (cond (map? x) (into {} (map (fn [[k v]] [(if (keyword? k) (name k) k) v])) x)
                               (instance? java.util.Date x) (subs (.toString (.toInstant ^java.util.Date x)) 0 19)
                               (sequential? x) (vec x) :else x)) v))

(defn- yaml-data [b r]
  (when (contains? #{"fence" "front_matter"} (:type b))
    (let [content (str/trim (:content b))
          clean (let [c (str/trim (str/replace content marker/pattern ""))] (if (seq c) c content))
          data (try (plain (yaml/parse-string (quote-dates clean) :keywords false))
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

(defn- section-boundary
  "Index of the first block at or after `search` that a recipe later than `i`
   claims, or the block count. A recipe that captures a section body only
   counts when the anchor that introduces its section is present: an absent
   optional heading must be absent, not re-bind the block that belongs to the
   previous section (docs/v2/07 §4, optrev)."
  [bs rs i search]
  (or (some (fn [j]
              (let [fr (nth rs j)
                    prev (nth rs (dec j))]
                (when-not (and (= :section-body (:capture fr))
                               (:anchor prev)
                               (not (some #(match-at (nth bs %) prev) (range search (count bs)))))
                  (first (filter #(match-at (nth bs %) fr) (range search (count bs)))))))
            (range (inc i) (count rs)))
      (count bs)))

(defn- section-anchor
  "Index of the step whose match tells that the section of step `i` is present:
   the nearest heading among the anchors right before `i` (the placeholder
   paragraph a template puts under a heading is an anchor too, and a document
   may drop it), else the step right before `i`; nil when that is no anchor."
  [rs i]
  (let [chain (take-while #(:anchor (nth rs %)) (range (dec i) -1 -1))]
    (or (first (filter #(= "heading" (:outer-type (nth rs %))) chain))
        (first chain))))

(defn- section-body [mode bs rs i lines {:keys [search] :as st}]
  (let [r (nth rs i)
        anchor (section-anchor rs i)
        strict? (= :strict mode)]
    (if (and strict? anchor (not (contains? (:hits st) anchor)))
      ;; docs/v2/16 §3.5 line 1: a field whose section is absent is `:missing`.
      st
      (let [boundary (section-boundary bs rs i search)
            value (if (< search boundary)
                    (str/trim (str/join "\n" (subvec lines (first (:map (nth bs search))) (min (count lines) (second (:map (nth bs (dec boundary))))))))
                    "")
            ;; docs/v2/16 §3.5 line 2: a present, empty section is "".
            keep? (or (seq value) (not (marker/optional? (first (:props-info r)))) (and strict? anchor))]
        (cond-> (assoc st :curr (dec boundary))
          keep? (assoc-in [:out (first (:props r))] value)
          ;; the blocks the value was read from, for `extract-located`
          (and keep? (< search boundary))
          (assoc-in [:src (first (:props r))]
                    [(first (:map (nth bs search))) (second (:map (nth bs (dec boundary))))]))))))

(defn- regular [bs rs i {:keys [search] :as st}]
  ;; `:hits` records the anchors that matched; legacy never reads it.
  (let [r (nth rs i)
        end (if (:optional-block r) (min (inc search) (count bs)) (count bs))]
    (loop [b search st st]
      (if (or (>= b end) (and (not= -1 (:curr st)) (not= b (:curr st))))
        st
        (let [blk (nth bs b) v (when (= (:type blk) (:outer-type r)) (match-at blk r))]
          (cond
            (nil? v) (recur (inc b) st)
            (and (:optional-block r) (some #(and (:anchor %) (matches-position? blk %)) (subvec rs (inc i)))) st
            (:anchor r) (-> st (assoc :curr b) (update :hits (fnil conj #{}) i))
            :else (-> st (update :out merge v)
                      (update :src merge (zipmap (keys v) (repeat (:map blk))))
                      (assoc :curr b))))))))

(defn- check-steps!
  "Raises `:models/invalid` naming the first step that lacks `:outer-type`:
   such a step could never match a block and would silently capture nothing."
  [steps]
  (doseq [[i s] (map-indexed vector steps)
          :when (not (and (map? s) (string? (:outer-type s))))]
    (err/raise :models/invalid
               (str "Selector step " i " has no :outer-type: " (pr-str s))
               {:step i :selector-step s})))

(def modes
  "The extraction modes, named as the descriptor's `:compat` (docs/v2/16 §3.5)."
  #{:legacy-v1-template :strict})

(defn- extract-blocks
  "The final state of the block-view `steps` over `text` in `mode`: `:out` holds
   the captured values and `:src` the line range of the blocks each came from."
  [steps text mode]
  (check-steps! steps)
  (let [bs (blocks/parse text) rs (vec steps) lines (vec (str/split-lines text))]
    (select-keys (reduce (fn [st i]
                    (let [r (nth rs i)
                          st (if (not= (:outer-index r) (:last st))
                               (assoc st :search (max (:search st) (inc (:curr st))) :last (:outer-index r) :curr -1)
                               st)]
                      (if (= :section-body (:capture r)) (section-body mode bs rs i lines st) (regular bs rs i st))))
                  {:out {} :src {} :search 0 :last -1 :curr -1} (range (count rs)))
                 [:out :src])))

(defn- check-mode!
  "Raises `:models/invalid` naming `mode` when it is not one of `modes`."
  [mode]
  (when-not (contains? modes mode)
    (err/raise :models/invalid
               (str "Unknown selector mode " (pr-str mode) "; expected one of " (pr-str (sort modes)))
               {:mode mode :modes modes})))

(defn- extract-located*
  "`{:out {field value} :src {field [start end)}}`: the values `extract-selector`
   returns and the line range of the source of each one."
  [steps text mode]
  (if (some cst/cst-step? steps)
    (let [b (extract-blocks (vec (remove cst/cst-step? steps)) text mode)
          c (cst/extract-located (filterv cst/cst-step? steps) text)]
      {:out (merge (:out b) (update-vals c :value))
       :src (merge (:src b) (update-vals c :lines))})
    (extract-blocks steps text mode)))

(defn extract-selector
  "The untyped values (`{field value}`, string keys) that the selector `steps`
   capture from Markdown `text`. `steps` is the vector that `recipe/recipes`
   returns, or its EDN round trip. `mode` is `:legacy-v1-template` (default:
   v1's behaviour, quirks included) or `:strict`; any other value raises
   `:models/invalid` naming it. The substrate follows the step: a step with
   `:want :find`/`:table` or `:preserve-nesting` (C3-C5, docs/v2/16 §3.3) is
   served by the tree-sitter CST (`selector-cst`) and raises
   `:docs/parser-unavailable` without the pod; every other step runs on the
   block view, as always. `extract-located` gives the same values with the
   line range each was read from."
  ([steps text] (extract-selector steps text :legacy-v1-template))
  ([steps text mode]
   (check-mode! mode)
   (:out (extract-located* steps text mode))))

(defn extract-located
  "Like `extract-selector`, but `{field {:value v :lines [start end)}}`: the
   0-based line range (end exclusive) of the text each value was read from. A
   field with no source range (an empty section body) carries no `:lines`.
   The values are exactly those of `extract-selector`."
  ([steps text] (extract-located steps text :legacy-v1-template))
  ([steps text mode]
   (check-mode! mode)
   (let [{:keys [out src]} (extract-located* steps text mode)]
     (into {} (map (fn [[k v]] [k (cond-> {:value v} (get src k) (assoc :lines (get src k)))])) out))))
