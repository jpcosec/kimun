(ns sldb.surface.markdown.render
  "Canonical Markdown renderer (docs/v2/04 §6): one spelling per construct, so
   that parse(render(A)) == A for every canonical A (invariant 9)."
  (:require [clojure.string :as str]
            [sldb.kernel.ports :as ports]
            [sldb.surface.markdown.cst :as cst]
            [sldb.surface.markdown.inline :as inline]))

(def ^:private escapable #{"\\" "*" "_" "[" "]" "`" "<" "|"})
(def ^:private escapable-heading (conj escapable "#"))

(defn- escape-grapheme [g esc] (if (contains? esc g) (str "\\" g) g))

(defn- code-fence [s]
  (let [runs (map count (re-seq #"`+" s))]
    (apply str (repeat (inc (apply max 0 runs)) "`"))))

(defn- open-delim [[kind _ _ _]]
  (case kind :emphasis "*" :strong "**" :link "[" :code nil))

(defn- close-delim [[kind _ _ url] content]
  (case kind
    :emphasis "*" :strong "**" :link (str "](" url ")")
    :code (let [f (code-fence content)] f)))

(defn render-inline
  "Text with its marks re-inserted (§6); `heading?` also escapes `#`."
  [host text marks & {:keys [heading?]}]
  (let [esc (if heading? escapable-heading escapable)
        gs (ports/graphemes (ports/segmenter host) text)
        n (count gs)
        marks (sort-by inline/mark-key marks)
        opens (group-by second marks)
        closes (group-by #(nth % 2) marks)
        in-code? (fn [p] (some (fn [[k s e]] (and (= k :code) (<= s p) (< p e))) marks))
        content-of (fn [[_ s e]] (apply str (subvec gs s e)))]
    (apply str
           (for [p (range (inc n))
                 piece (concat
                        ;; closes: innermost first = reverse canonical order among marks ending here
                        (map #(close-delim % (content-of %)) (reverse (sort-by inline/mark-key (get closes p))))
                        (map #(or (open-delim %) (code-fence (content-of %))) (get opens p))
                        (when (< p n)
                          [(let [g (nth gs p)]
                             (cond (in-code? p) g
                                   (and (= g "!") (some #(= :link (first %)) (get opens (inc p)))) "\\!"
                                   :else (escape-grapheme g esc)))]))]
             piece))))

(defn- escape-line-start
  "If the line would start a block (rules 2–8), escape its first character (or the
   dot of an ordered marker)."
  [line]
  (let [kind (cst/classify line)]
    (cond
      (= kind :paragraph) line
      (= kind :blank) line
      (re-matches #"^\d{1,9}\..*" line) (str/replace-first line "." "\\.")
      :else (str "\\" line))))

(declare render-block)

(defn- indent
  "Prefixes every line — blank ones included, so blank lines inside a list item
   count as indented continuation (§3.1 rule c) — with `w` spaces."
  [lines w]
  (mapv #(str (apply str (repeat w \space)) %) lines))

(defn- join-blocks
  "Lines of blocks separated by one blank line."
  [blocks-lines]
  (vec (apply concat (interpose [""] blocks-lines))))

(defn- render-item [host item w marker]
  (let [kids (map #(render-block host %) (:children item))
        first-lines (first kids)
        rest-lines (rest kids)
        head (into [(str marker " " (first first-lines))] (indent (vec (rest first-lines)) w))]
    (into head (indent (join-blocks (cons [] rest-lines)) w))))

(defn- render-list
  "Markers `- ` or `n. ` from :start; continuation lines are indented by the width of
   the FIRST marker (w), which is also the width the parser dedents by (§3.2)."
  [host {:keys [attrs children]}]
  (let [ordered? (:ordered attrs)
        numbers (map #(+ (:start attrs) %) (range (count children)))
        markers (if ordered? (map #(str % ".") numbers) (repeat "-"))
        w (inc (count (first markers)))]
    (vec (mapcat (fn [item marker] (render-item host item w marker)) children markers))))

(defn- render-block [host {:keys [type attrs text children blob] :as b}]
  (case type
    :heading   [(let [inl (render-inline host text (:marks attrs) :heading? true)]
                  (str (apply str (repeat (:level attrs) "#")) (when (seq inl) (str " " inl))))]
    :paragraph [(escape-line-start (render-inline host text (:marks attrs)))]
    :code      (let [fence (let [runs (map count (re-seq #"(?m)^`+" text))]
                             (apply str (repeat (max 3 (inc (apply max 0 runs))) "`")))]
                 (-> [(str fence (:lang attrs))]
                     (into (if (= text "") [] (str/split text #"\n" -1)))
                     (conj fence)))
    ;; an empty line is ">"; an indented blank line (list continuation) keeps its spaces
    :quote     (mapv #(if (= "" %) ">" (str "> " %)) (join-blocks (map #(render-block host %) children)))
    :list      (render-list host b)
    :thematic-break ["---"]
    :opaque    (str/split blob #"\n" -1)))

(defn render
  "Canonical Markdown of an AST, ending with a newline."
  [host ast]
  (let [lines (join-blocks (map #(render-block host %) (:children ast)))]
    (if (empty? lines) "" (str (str/join "\n" lines) "\n"))))
