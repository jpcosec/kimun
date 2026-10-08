(ns kimun.surface.markdown.cst-ast
  "SLDB-MD as a view over the tree-sitter CST (docs/v2/14 §13 etapa S.2, decision
   29): the pod's **block** CST (`parse-block`, no inline) is segmented into
   blocks and the closed-profile rules of `kimun.surface.markdown.ast` are
   applied *after* the parse — `_` never emphasis, out-of-profile → `:opaque`
   byte for byte, lazy continuation per docs/v2/04 §3.1. The canonical render
   stays a function of the neutral AST (`kimun.surface.markdown.render`), so
   the round trip is preserved."
  (:require [clojure.string :as str]
            [kimun.host.treesitter :as ts]
            [kimun.surface.markdown.ast :as ast]
            [kimun.surface.markdown.cst :as cst]))

(defn- norm
  "The text the pod's block grammar needs: a final newline. Without it an
   ATX-only document parses to an ERROR root (R2 §6)."
  [text]
  (if (str/ends-with? text "\n") text (str text "\n")))

(defn- source-lines
  "Lines of the original text with the convention of `cst/parse`: internal blank
   lines kept, the empty line the trailing newline adds dropped."
  [text]
  (let [raw (str/split text #"\n" -1)
        raw (if (str/ends-with? text "\n") (vec (butlast raw)) (vec raw))]
    (if (= text "") [] raw)))

(defn- span
  "`[start-line end-line)` of a pod block: start/end point rows, end exclusive."
  [node]
  (let [[sr _] (:start_point node)
        [er ec] (:end_point node)]
    [(long sr) (long (if (zero? ec) er (inc er)))]))

(defn- expand
  "Top-level named block nodes of a block CST root, `section` wrappers flattened
   (the flattening the pod's `blocks` var also does)."
  [node]
  (mapcat (fn [c]
            (cond
              (not (:named c)) []
              (= "section" (:type c)) (expand c)
              :else [c]))
          (:children node)))

(defn- kind
  "SLDB-MD kind of a pod block type, or nil when the type has no counterpart.
   Indented code and link-reference definitions are out of the profile
   (docs/v2/04 §3.1, §11) and stay paragraphs; setext headings are handled
   separately (`blocks-of`) because the profile segments their lines apart."
  [t]
  (case t
    ("minus_metadata" "plus_metadata") :frontmatter
    "atx_heading" :heading
    "link_reference_definition" :paragraph
    "paragraph" :paragraph
    "fenced_code_block" :code
    "block_quote" :quote
    "list" :list
    "thematic_break" :thematic-break
    "html_block" :html
    "pipe_table" :table
    nil))

(defn- fm-end
  "Index of the line closing a `---` frontmatter block that opens on line 0, or
   nil: `---`, then non-blank lines, then `---`. A blank before the closing
   fence means line 0 was a thematic break, not frontmatter (docs/v2/04, the
   rule of `cst/frontmatter-end`)."
  [lines]
  (when (and (>= (count lines) 3) (re-matches #"---[ \t]*" (cst/strip-cr (first lines))))
    (loop [i 1]
      (when (< i (count lines))
        (let [l (cst/strip-cr (nth lines i))]
          (cond (re-matches #"---[ \t]*" l) (when (> i 1) i)
                (str/blank? l) nil
                :else (recur (inc i))))))))

(defn- line-spans
  "Line spans `[start end)` of the pod nodes. `span` is the rule of R2; it needs
   one correction: when a node ends mid-line (end column > 0) and the next node
   starts on that same line, the line belongs to the next node, so the current
   one ends before it (the pod splits a line between two nodes)."
  [nodes]
  (let [spans (mapv span nodes)
        starts (mapv #(first (:start_point %)) nodes)]
    (mapv (fn [k [s e]]
            (let [[er ec] (:end_point (nth nodes k))
                  next-sr (get starts (inc k))]
              (if (and (pos? ec) (= next-sr er) (= e (inc er)))
                [s er]
                [s e])))
          (range) spans)))

(defn- blocks-of
  "CST-like blocks (`[{:kind :lines}]`) for a pod node over lines `[i j)`: one
   block, none, or — for the constructions the closed profile segments
   differently — the node's lines re-segmented by the profile (docs/v2/04 §3.1,
   §11): setext headings are not in the profile, and frontmatter only when the
   `---` fence closes with no blank line in between. Trailing blank lines the
   pod block absorbed are dropped (native blocks never carry one) except inside
   code, where blanks are content."
  [lines i j node]
  (let [t (:type node)]
    (cond
      (contains? #{"minus_metadata" "plus_metadata"} t)
      (let [end (fm-end lines)]
        (if (and (zero? i) end (= j (inc end)))
          [{:kind :frontmatter :lines (subvec lines 0 (inc end))}]
          (:blocks (cst/parse (str/join "\n" (subvec lines i j))))))

      (= t "setext_heading")
      (:blocks (cst/parse (str/join "\n" (subvec lines i j))))

      ;; indented code is not in the profile and the pod groups it across blank
      ;; lines; let the profile's own rules segment those lines
      (= t "indented_code_block")
      (:blocks (cst/parse (str/join "\n" (subvec lines i j))))

      (= t "fenced_code_block")
      (let [fo (cst/fence-open (nth lines i))]
        (if fo
          [{:kind :code :lines (subvec lines i j) :char (:char fo) :n (:n fo)}]
          (:blocks (cst/parse (str/join "\n" (subvec lines i j))))))

      :else
      (let [k (kind t)
            j (if (and k (not (contains? #{:code :frontmatter} k)))
                (loop [e j] (if (and (> e i) (str/blank? (cst/strip-cr (nth lines (dec e))))) (recur (dec e)) e))
                j)]
        (if k [(cond-> {:kind k :lines (subvec lines i j)})] [])))))

(defn- lazy-continuation
  "The profile's lazy-continuation rule (docs/v2/04 §3.1): a quote block ends at
   the first line that is not `>`-prefixed; the remainder is re-segmented."
  [blocks]
  (mapcat
   (fn [b]
     (if (and (= :quote (:kind b))
              (some #(not (re-matches #"^ {0,3}>.*" (cst/strip-cr %))) (rest (:lines b))))
       (let [k (count (take-while #(re-matches #"^ {0,3}>.*" (cst/strip-cr %)) (:lines b)))
             head (subvec (:lines b) 0 k)
             tail (subvec (:lines b) k)]
         (into [{:kind :quote :lines head}]
               (:blocks (cst/parse (str/join "\n" tail)))))
       [b]))
   blocks))

(defn- unknown?
  "A pod block node the profile view cannot place: a type with no counterpart
   (`blocks-of` handles setext and indented code) or an ERROR node. Such a
   document falls back to the in-process parser."
  [node]
  (let [t (:type node)]
    (or (and (not (contains? #{"setext_heading" "indented_code_block"} t))
             (nil? (kind t)))
        (= "ERROR" t)
        (:error node))))

(defn parse
  "Markdown text → neutral AST, segmented by the tree-sitter pod and converted
   with the SLDB-MD profile rules. Documents the pod parses to an ERROR (or to a
   kind with no counterpart) fall back to `ast/parse-native`, the oracle; any
   internal error does too, so the flag is always safe."
  [host s]
  (try
    (let [lines (source-lines s)
          root (first (ts/parse-block (norm s)))
          nodes (expand root)]
      (if (some unknown? nodes)
        (ast/parse-native host s)
        (let [spans (line-spans nodes)
              cst-blocks (lazy-continuation (vec (mapcat (fn [node [i j]] (blocks-of lines i j node))
                                                         nodes spans)))]
          {:type :document :attrs {} :children (ast/from-blocks host {:blocks cst-blocks})})))
    (catch Throwable _ (ast/parse-native host s))))
