(ns kimun.surface.markdown.cst-ast
  "SLDB-MD as a view over the tree-sitter CST (docs/v2/14 §13 etapa S.2, decision
   29): the pod's **block** CST (`parse-block`, no inline) is segmented into
   blocks and the closed-profile rules of `kimun.surface.markdown.profile` are
   applied *after* the parse — `_` never emphasis, out-of-profile → `:opaque`
   byte for byte, lazy continuation per docs/v2/04 §3.1. The canonical render
   stays a function of the neutral AST (`kimun.surface.markdown.render`), so
   the round trip is preserved."
  (:require [clojure.string :as str]
            [kimun.host.treesitter :as ts]
            [kimun.surface.markdown.profile :as profile]
            [kimun.surface.markdown.cst :as cst]))

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
  "Line spans `[start end)` of the pod nodes. `ts/block-span` is the rule of R2; it needs
   one correction: when a node ends mid-line (end column > 0) and the next node
   starts on that same line, the line belongs to the next node, so the current
   one ends before it (the pod splits a line between two nodes)."
  [nodes]
  (let [spans (mapv ts/block-span nodes)
        starts (mapv #(first (:start_point %)) nodes)]
    (mapv (fn [k [s e]]
            (let [[er ec] (:end_point (nth nodes k))
                  next-sr (get starts (inc k))]
              (if (and (pos? ec) (= next-sr er) (= e (inc er)))
                [s er]
                [s e])))
          (range) spans)))

(defn- fence-close? [line ch n]
  (let [l (cst/strip-cr line)]
    (boolean (re-matches (re-pattern (str "^ {0,3}" (if (= ch \`) "`" "~") "{" n ",} *$")) l))))

(defn- same-list-marker? [w mt line]
  (let [m (cst/list-marker line)]
    (and m (< (:indent m) w) (= (cst/marker-type line) mt))))

(defn- continues-block? [b line next-line]
  (let [k (cst/classify line)]
    (case (:kind b)
      :code true
      :quote (= k :quote)
      (:html :table) (not= k :blank)
      :paragraph (= k :paragraph)
      :list (let [w (:width b) mt (:marker-type b)]
              (or (same-list-marker? w mt line)
                  (and (= k :blank) next-line
                       (or (same-list-marker? w mt next-line)
                           (>= (cst/leading-spaces next-line) w)))
                  (and (not= k :blank) (>= (cst/leading-spaces line) w))
                  (= k :paragraph)))
      false)))

(defn- closes-after? [b line]
  (case (:kind b)
    (:heading :thematic-break) true
    :code (and (> (count (:lines b)) 1) (fence-close? line (:char b) (:n b)))
    false))

(defn- profile-block-at
  "Segments one top-level SLDB-MD block starting at non-blank line `i`
   (docs/v2/04 §3.1, docs/v2/07 §5), returning `[block end-line]`."
  [lines fm-end-idx i]
  (if (and (zero? i) fm-end-idx)
    (let [end (inc fm-end-idx)]
      [{:kind :frontmatter :lines (subvec lines 0 end)} end])
    (let [line0 (nth lines i)
          k0 (cst/classify line0)
          b0 (cond-> {:kind k0 :lines [line0]}
               (= k0 :code) (merge (select-keys (cst/fence-open line0) [:char :n]))
               (= k0 :list) (assoc :width (:width (cst/list-marker line0))
                                   :marker-type (cst/marker-type line0)))]
      (if (closes-after? b0 line0)
        [b0 (inc i)]
        (loop [j (inc i) b b0]
          (if (>= j (count lines))
            [b j]
            (let [line (nth lines j)
                  next-line (nth lines (inc j) nil)]
              (if (continues-block? b line next-line)
                (let [b' (update b :lines conj line)]
                  (if (closes-after? b' line)
                    [b' (inc j)]
                    (recur (inc j) b')))
                [b j]))))))))

(defn- next-non-blank [lines i]
  (loop [k i]
    (if (and (< k (count lines)) (= :blank (cst/classify (nth lines k))))
      (recur (inc k))
      k)))

(defn- trim-end-blanks
  "Drops trailing blank lines absorbed by a pod block, except in code/frontmatter."
  [lines i j k]
  (if (contains? #{:code :frontmatter} k)
    j
    (loop [e j]
      (if (and (> e i) (= :blank (cst/classify (nth lines (dec e)))))
        (recur (dec e))
        e))))

(defn- pod-node-block
  "Extracts a CST block from pod node over trimmed lines `[i j)` when mapped
   in the profile."
  [lines i j node]
  (let [t (:type node)
        k (kind t)
        j (trim-end-blanks lines i j k)]
    (when (and k (> j i))
      (if (= k :code)
        (when-let [fo (cst/fence-open (nth lines i))]
          {:kind :code :lines (subvec lines i j) :char (:char fo) :n (:n fo)})
        {:kind k :lines (subvec lines i j)}))))

(defn- reconcile-blocks
  "Walks `lines` using pod block `spans` where they match SLDB-MD boundaries
   (docs/v2/04 §3.1) and falling back to profile block extents wherever CommonMark
   diverges (ordered digit-width lists `9.->10.`, lazy quotes, fences with
   backtick info strings, single-line tables, or adjacent opaque/paragraph lines)."
  [lines nodes spans]
  (let [fm-idx (fm-end lines)
        n-lines (count lines)
        n-nodes (count nodes)]
    (loop [cur (next-non-blank lines 0)
           idx 0
           acc []]
      (if (>= cur n-lines)
        acc
        (let [idx (loop [m idx]
                    (if (and (< m n-nodes) (< (first (nth spans m)) cur))
                      (recur (inc m))
                      m))
              [prof-b prof-end] (profile-block-at lines fm-idx cur)
              pod-b (when (< idx n-nodes)
                      (let [[i j] (nth spans idx)
                            node (nth nodes idx)
                            j' (trim-end-blanks lines i j (kind (:type node)))]
                        (when (and (= i cur) (= j' prof-end))
                          (pod-node-block lines i j' node))))]
          (if (and pod-b (= (:kind pod-b) (:kind prof-b)))
            (recur (next-non-blank lines prof-end) (inc idx) (conj acc pod-b))
            (recur (next-non-blank lines prof-end) idx (conj acc (dissoc prof-b :width :marker-type)))))))))

(defn- unknown?
  "A pod block node the profile view cannot place: a type with no counterpart
   (`reconcile-blocks` handles setext and indented code) or an ERROR / `:has_error`
   subtree. Such a document falls back to the in-process parser."
  [node]
  (let [t (:type node)]
    (or (and (not (contains? #{"setext_heading" "indented_code_block"} t))
             (nil? (kind t)))
        (= "ERROR" t)
        (:error node)
        (:has_error node))))

(defn parse
  "Markdown text → neutral AST, segmented by the tree-sitter pod and converted
   with the SLDB-MD profile rules. Documents the pod parses to an ERROR (or to a
   kind with no counterpart) fall back to `profile/parse`, the oracle; any
   internal error does too, so the flag is always safe."
  [host s]
  (try
    (let [lines (ts/source-lines s)
          root (first (ts/parse-block (ts/with-final-newline s)))
          nodes (expand root)]
      (if (or (:has_error root) (some unknown? nodes))
        (profile/parse host s)
        (let [spans (line-spans nodes)
              cst-blocks (reconcile-blocks lines nodes spans)]
          {:type :document :attrs {} :children (profile/from-blocks host {:blocks cst-blocks})})))
    (catch Throwable _ (profile/parse host s))))
