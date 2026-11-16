(ns kimun.surface.models.blocks
  "Top-level block view of a Markdown text for the models surface: the same
   segmentation sldb v1 took from markdown-it (`front_matter`, `heading`,
   `paragraph`, `fence`, `bullet_list`, `ordered_list`, `table`,
   `blockquote`, `hr`), each block as
   `{:type :tag :content :map [start end) :children}` with line numbers into
   the original text, so a field can capture the exact source lines.

   `parse` uses the tree-sitter pod (`kimun.host.treesitter`, decision 29) as
   the segmenter when it is available and falls back to the in-process
   `parse-native` otherwise. `parse-native` is the differential oracle (R2
   §5 fase 1) and stays until the pod's spans match it byte for byte."
  (:require [clojure.string :as str]
            [kimun.host.treesitter :as ts]))

(def ^:private heading-re #"^ {0,3}(#{1,6})(?:[ \t]+(.*?))?(?:[ \t]+#+)?[ \t]*$")
(def ^:private fence-re #"^ {0,3}(`{3,}|~{3,})(.*)$")
(def ^:private hr-re #"^ {0,3}([-*_])(?:[ \t]*\1){2,}[ \t]*$")
(def ^:private bullet-re #"^( {0,3})([-*+])([ \t]+|$)(.*)$")
(def ^:private ordered-re #"^( {0,3})(\d{1,9})([.)])([ \t]+|$)(.*)$")
(def ^:private delim-re #"^ {0,3}\|?[ \t]*:?-+:?[ \t]*(\|[ \t]*:?-+:?[ \t]*)*\|?[ \t]*$")

(defn- blank? [l] (str/blank? l))
(defn- item-start [l] (or (re-matches bullet-re l) (re-matches ordered-re l)))
(defn- list-type [l] (cond (re-matches bullet-re l) "bullet_list" (re-matches ordered-re l) "ordered_list"))

(defn- table-at? [lines i]
  (and (< (inc i) (count lines)) (str/includes? (nth lines i) "|")
       (str/includes? (nth lines (inc i)) "-") (re-matches delim-re (nth lines (inc i)))))

(defn- starts-block? [lines i]
  (let [l (nth lines i)]
    (or (re-matches heading-re l) (re-matches fence-re l) (re-matches hr-re l)
        (str/starts-with? (str/triml l) ">") (re-matches bullet-re l)
        (when-let [[_ _ n] (re-matches ordered-re l)] (= "1" n))
        (table-at? lines i))))

(defn- inline [content] {:type "inline" :tag "" :content content :children []})

(defn- cells [line]
  (let [t (str/trim line)
        t (if (str/starts-with? t "|") (subs t 1) t)
        t (if (and (str/ends-with? t "|") (not (str/ends-with? t "\\|"))) (subs t 0 (dec (count t))) t)]
    (mapv #(str/trim (str/replace % "\\|" "|")) (str/split t #"(?<!\\)\|" -1))))

(defn- row [tag line]
  {:type "tr" :tag "tr" :content "" :children (mapv (fn [c] {:type tag :tag tag :content "" :children [(inline c)]}) (cells line))})

(defn- para-end [lines i]
  (loop [j (inc i)]
    (if (or (>= j (count lines)) (blank? (nth lines j)) (starts-block? lines j)) j (recur (inc j)))))

(defn- list-end
  "Index after the last line of the list starting at `i`: items of the same
   kind, their indented continuations and lazy lines; a blank line ends it
   unless an item or an indented line follows."
  [lines i kind]
  (loop [j (inc i)]
    (cond
      (>= j (count lines)) j
      (blank? (nth lines j))
      (let [k (loop [k j] (if (and (< k (count lines)) (blank? (nth lines k))) (recur (inc k)) k))]
        (if (and (< k (count lines))
                 (or (= kind (list-type (nth lines k))) (re-find #"^(?: {2,}|\t)" (nth lines k))))
          (recur k) j))
      (= kind (list-type (nth lines j))) (recur (inc j))
      (re-find #"^(?: {2,}|\t)" (nth lines j)) (recur (inc j))
      (starts-block? lines j) j
      :else (recur (inc j)))))

(defn- list-items [lines i end]
  (let [starts (filterv #(and (item-start (nth lines %)) (not (re-find #"^(?: {2,}|\t)" (nth lines %)))) (range i end))]
    (mapv (fn [s e]
            (let [first-line (last (item-start (nth lines s)))
                  more (->> (subvec lines (inc s) e) (remove blank?) (map str/trim))
                  text (str/join "\n" (cons first-line more))]
              {:type "list_item" :tag "li" :content "" :map [s e]
               :children [{:type "paragraph" :tag "p" :content "" :children [(inline text)]}]}))
          starts (concat (rest starts) [end]))))

(defn- quote-block [lines i]
  (let [end (loop [j (inc i)]
              (if (and (< j (count lines)) (not (blank? (nth lines j)))
                       (or (str/starts-with? (str/triml (nth lines j)) ">") (not (starts-block? lines j))))
                (recur (inc j)) j))
        inner (map #(str/replace % #"^ {0,3}> ?" "") (subvec lines i end))
        paras (->> (partition-by blank? inner) (remove #(blank? (first %)))
                   (mapv (fn [ls] {:type "paragraph" :tag "p" :content "" :children [(inline (str/join "\n" (map str/trim ls)))]})))]
    [{:type "blockquote" :tag "blockquote" :content "" :map [i end] :children paras} end]))

(defn- block-at [lines i]
  (let [l (nth lines i) n (count lines)]
    (cond
      (re-matches fence-re l)
      (let [[_ f] (re-matches fence-re l)
            close (re-pattern (str "^ {0,3}" (java.util.regex.Pattern/quote (str (first f))) "{" (count f) ",}[ \\t]*$"))
            j (loop [j (inc i)] (if (or (>= j n) (re-matches close (nth lines j))) j (recur (inc j))))
            body (subvec lines (inc i) (min j n))]
        [{:type "fence" :tag "code" :content (if (seq body) (str (str/join "\n" body) "\n") "") :map [i (min (inc j) n)] :children []}
         (min (inc j) n)])
      (re-matches heading-re l)
      (let [[_ hs txt] (re-matches heading-re l)]
        [{:type "heading" :tag (str "h" (count hs)) :content "" :map [i (inc i)] :children [(inline (str/trim (or txt "")))]} (inc i)])
      (re-matches hr-re l) [{:type "hr" :tag "hr" :content "" :map [i (inc i)] :children []} (inc i)]
      (str/starts-with? (str/triml l) ">") (quote-block lines i)
      (table-at? lines i)
      (let [end (loop [j (+ i 2)] (if (or (>= j n) (blank? (nth lines j)) (not (str/includes? (nth lines j) "|"))) j (recur (inc j))))]
        [{:type "table" :tag "table" :content "" :map [i end]
          :children [{:type "thead" :tag "thead" :content "" :children [(row "th" l)]}
                     {:type "tbody" :tag "tbody" :content "" :children (mapv #(row "td" (nth lines %)) (range (+ i 2) end))}]}
         end])
      (item-start l)
      (let [kind (list-type l) end (list-end lines i kind)]
        [{:type kind :tag (if (= kind "bullet_list") "ul" "ol") :content "" :map [i end] :children (list-items lines i end)} end])
      :else
      (let [end (para-end lines i)
            setext (when (< end n) (cond (re-matches #"^ {0,3}=+[ \t]*$" (nth lines end)) "h1"
                                         (re-matches #"^ {0,3}-+[ \t]*$" (nth lines end)) "h2"))
            text (str/join "\n" (map str/trim (subvec lines i end)))]
        (if setext
          [{:type "heading" :tag setext :content "" :map [i (inc end)] :children [(inline text)]} (inc end)]
          [{:type "paragraph" :tag "p" :content "" :map [i end] :children [(inline text)]} end])))))

(defn parse-native
  "The top-level blocks of Markdown `text`, in order, by the in-process
   segmentation (the differential oracle of R2 §5 fase 1)."
  [text]
  (let [lines (vec (str/split-lines text)) n (count lines)
        fm-end (when (and (pos? n) (re-matches #"^---[ \t]*$" (first lines)))
                 (first (filter #(re-matches #"^---[ \t]*$" (nth lines %)) (range 1 n))))
        fm (when fm-end {:type "front_matter" :tag "" :content (str/join "\n" (subvec lines 1 fm-end)) :map [0 (inc fm-end)] :children []})]
    (loop [i (if fm (inc fm-end) 0) out (if fm [fm] [])]
      (cond (>= i n) out
            (blank? (nth lines i)) (recur (inc i) out)
            :else (let [[b j] (block-at lines i)] (recur (long j) (conj out b)))))))

;; ------------------------------------------------------------ pod (M layer)

(defn- pod-type
  "Native `:type` for a pod block kind, `::list` for lists (the marker decides
   bullet vs ordered), or nil when the kind has no counterpart here."
  [t]
  (case t
    ("minus_metadata" "plus_metadata") "front_matter"
    ("atx_heading" "setext_heading") "heading"
    "paragraph" "paragraph"
    ("fenced_code_block" "indented_code_block") "fence"
    "block_quote" "blockquote"
    "list" ::list
    "thematic_break" "hr"
    "pipe_table" "table"
    ("html_block" "link_reference_definition") "paragraph"
    nil))

(defn- heading-range [lines i j]
  (let [line (nth lines i)]
    (if-let [[_ hs txt] (re-matches heading-re line)]
      {:type "heading" :tag (str "h" (count hs)) :content "" :map [i j]
       :children [(inline (str/trim (or txt "")))]}
      (let [under (nth lines (dec j))
            tag (if (re-matches #"^ {0,3}=+[ \t]*$" under) "h1" "h2")]
        {:type "heading" :tag tag :content "" :map [i j]
         :children [(inline (str/join "\n" (map str/trim (subvec lines i (dec j)))))]}))))

(defn- fence-range [lines i j]
  (let [[_ f] (re-matches fence-re (nth lines i))
        close (re-pattern (str "^ {0,3}" (java.util.regex.Pattern/quote (str (first f))) "{" (count f) ",}[ \\t]*$"))
        closed? (and (< i (dec j)) (re-matches close (nth lines (dec j))))
        body (subvec lines (inc i) (if closed? (dec j) j))]
    {:type "fence" :tag "code"
     :content (if (seq body) (str (str/join "\n" body) "\n") "")
     :map [i j] :children []}))

(defn- paragraph-range [lines i j]
  (let [text (str/join "\n" (map str/trim (subvec lines i j)))]
    {:type "paragraph" :tag "p" :content "" :map [i j] :children [(inline text)]}))

(defn- quote-range [lines i j]
  (let [inner (map #(str/replace % #"^ {0,3}> ?" "") (subvec lines i j))
        paras (->> (partition-by blank? inner) (remove #(blank? (first %)))
                   (mapv (fn [ls] {:type "paragraph" :tag "p" :content ""
                                   :children [(inline (str/join "\n" (map str/trim ls)))]})))]
    {:type "blockquote" :tag "blockquote" :content "" :map [i j] :children paras}))

(defn- table-range [lines i j]
  {:type "table" :tag "table" :content "" :map [i j]
   :children [{:type "thead" :tag "thead" :content "" :children [(row "th" (nth lines i))]}
              {:type "tbody" :tag "tbody" :content ""
               :children (mapv #(row "td" (nth lines %)) (range (+ i 2) j))}]})

(defn- list-range [lines i j type]
  (let [end (loop [e j] (if (and (> e i) (blank? (nth lines (dec e)))) (recur (dec e)) e))]
    {:type type :tag (if (= type "bullet_list") "ul" "ol") :content ""
     :map [i end] :children (list-items lines i end)}))

(defn- pod-block [lines node]
  (let [[i j] (ts/block-span node)
        t (pod-type (:type node))]
    (cond
      (nil? t) nil
      (= t "front_matter") {:type "front_matter" :tag ""
                             :content (str/join "\n" (subvec lines (inc i) (max (inc i) (dec j))))
                             :map [i j] :children []}
      (= t "heading") (heading-range lines i j)
      (= t "paragraph") (paragraph-range lines i j)
      (= t "fence") (fence-range lines i j)
      (= t "blockquote") (quote-range lines i j)
      (= t "hr") {:type "hr" :tag "hr" :content "" :map [i j] :children []}
      (= t "table") (table-range lines i j)
      (= t ::list) (list-range lines i j (if (re-matches ordered-re (nth lines i)) "ordered_list" "bullet_list")))))

(defn parse-pod
  "Blocks of `text` from the pod's `blocks` segmentation (R2 §5 fase 1), or nil
   when the pod is unavailable or returns a kind with no counterpart here."
  [text]
  (when (ts/available?)
    (let [lines (ts/source-lines text)
          blocks (mapv #(pod-block lines %) (ts/blocks (ts/with-final-newline text)))]
      (when (every? some? blocks) blocks))))

(defn parse
  "The top-level blocks of Markdown `text`, in order: the tree-sitter pod when
   available (decision 29), else `parse-native`."
  [text]
  (or (try (parse-pod text) (catch Throwable _ nil))
      (parse-native text)))

(defn text-of
  "v1's `get_text`: a node's own content when it has one, else its children's
   texts concatenated."
  [node]
  (if (seq (:content node)) (:content node) (apply str (map text-of (:children node)))))

(defn leaf-text
  "Text of the first descendant that carries content (the block's inline), or nil."
  [node]
  (if (seq (:content node)) (:content node) (some leaf-text (:children node))))

(defn rows
  "The `tr` rows of a table block, header first."
  [table]
  (vec (mapcat (fn [c] (if (= "tr" (:type c)) [c] (filter #(= "tr" (:type %)) (:children c)))) (:children table))))
