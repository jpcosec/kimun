(ns sldb.surface.markdown.cst
  "Lossless block segmentation of Markdown text (docs/v2/04 §3, §3.1): total,
   never interprets inlines, and `(text (parse s)) == s` for every string
   (invariant 8). Lines keep a trailing \\r if they had one, so CRLF input
   round-trips exactly."
  (:require [clojure.string :as str]))

(def ^:private re-blank   #"^\s*$")
(def ^:private re-fence   #"^ {0,3}(`{3,}|~{3,})([^`]*)$")
(def ^:private re-heading #"^ {0,3}#{1,6}( |$).*")
(def ^:private re-break   #"^ {0,3}([-*_])( *\1){2,} *$")
(def ^:private re-quote   #"^ {0,3}>.*")
(def ^:private re-list    #"^( {0,3})([-*+]|\d{1,9}\.)( |$).*")
(def ^:private re-html    #"^ {0,3}<[A-Za-z/!?].*")
(def ^:private re-table   #"^ {0,3}\|.*")

(defn strip-cr
  "The line without its trailing \\r."
  [line]
  (if (str/ends-with? line "\r") (subs line 0 (dec (count line))) line))

(defn fence-open
  "{:char \\` :n 3 :info \"lang\"} when the line opens a fence (rule 2), else nil."
  [line]
  (when-let [[_ fence info] (re-matches re-fence (strip-cr line))]
    {:char (first fence) :n (count fence) :info (str/trim info)}))

(defn- fence-close? [line ch n]
  (let [l (strip-cr line)]
    (boolean (re-matches (re-pattern (str "^ {0,3}" (if (= ch \`) "`" "~") "{" n ",} *$")) l))))

(defn list-marker
  "{:indent i :marker \"-\" :ordered? false :number nil :width 2} for a rule-6 line, else nil."
  [line]
  (when-let [[_ indent marker] (re-matches re-list (strip-cr line))]
    (let [ordered? (boolean (re-matches #"\d.*" marker))]
      {:indent (count indent) :marker marker :ordered? ordered?
       :number (when ordered? (parse-long (subs marker 0 (dec (count marker)))))
       :width (inc (count marker))})))

(defn classify
  "Block kind a line would start (rules 1–9 of §3.1)."
  [line]
  (let [l (strip-cr line)]
    (cond
      (re-matches re-blank l)   :blank
      (re-matches re-fence l)   :code
      (re-matches re-heading l) :heading
      (re-matches re-break l)   :thematic-break
      (re-matches re-quote l)   :quote
      (re-matches re-list l)    :list
      (re-matches re-html l)    :html
      (re-matches re-table l)   :table
      :else                     :paragraph)))

(defn- leading-spaces [line]
  (count (take-while #(= \space %) line)))

(defn marker-type
  "Marker family of a list line: :ordered or the bullet character string."
  [line]
  (when-let [m (list-marker line)] (if (:ordered? m) :ordered (:marker m))))

(defn- same-list-marker?
  "A rule-6 line at indent < w whose marker family equals the block's."
  [block line]
  (let [m (list-marker line)]
    (and m (< (:indent m) (:width block)) (= (marker-type line) (:marker-type block)))))

(defn- continues?
  "Does `line` belong to the open `block`? For lists, `next-line` is the lookahead."
  [block line next-line]
  (let [kind (classify line)]
    (case (:kind block)
      :blank     (= kind :blank)
      :code      true
      :quote     (= kind :quote)
      (:html :table) (not= kind :blank)
      :paragraph (= kind :paragraph)
      :list      (let [w (:width block)]
                   (or (same-list-marker? block line)
                       (and (= kind :blank) next-line
                            (or (same-list-marker? block next-line)
                                (>= (leading-spaces next-line) w)))
                       (and (not= kind :blank) (>= (leading-spaces line) w))
                       (= kind :paragraph)))
      false)))

(defn- open-block [kind line i]
  (cond-> {:kind kind :lines [line] :start i}
    (= kind :code) (merge (select-keys (fence-open line) [:char :n]))
    (= kind :list) (assoc :width (:width (list-marker line)) :marker-type (marker-type line))))

(defn- closes-after? [block line]
  (case (:kind block)
    (:heading :thematic-break) true
    :code (and (> (count (:lines block)) 1) (fence-close? line (:char block) (:n block)))
    false))

(defn parse
  "Total, lossless segmentation of `s` into blocks (docs/v2/04 §3)."
  [s]
  (let [trailing? (str/ends-with? s "\n")
        raw (str/split s #"\n" -1)
        lines (if trailing? (vec (butlast raw)) (vec raw))
        lines (if (= s "") [] lines)
        newline (if (some-> (first lines) (str/ends-with? "\r")) "\r\n" "\n")]
    (loop [i 0 open nil blocks []]
      (if (>= i (count lines))
        {:blocks (cond-> blocks open (conj open)) :newline newline :trailing-newline? trailing?}
        (let [line (nth lines i) next-line (nth lines (inc i) nil)]
          (if (and open (continues? open line next-line))
            (let [open' (update open :lines conj line)]
              (if (closes-after? open' line)
                (recur (inc i) nil (conj blocks open'))
                (recur (inc i) open' blocks)))
            (let [blocks (cond-> blocks open (conj open))
                  b (open-block (classify line) line i)]
              (if (closes-after? b line)
                (recur (inc i) nil (conj blocks b))
                (recur (inc i) b blocks)))))))))

(defn text
  "Reconstructs the original text from a CST (invariant 8)."
  [{:keys [blocks trailing-newline?]}]
  (let [lines (mapcat :lines blocks)]
    (str (str/join "\n" lines) (when trailing-newline? "\n"))))

(defn block-text
  "Lines of a block joined with \\n (without \\r), as used for opaque blobs."
  [block]
  (str/join "\n" (map strip-cr (:lines block))))
