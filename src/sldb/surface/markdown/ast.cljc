(ns sldb.surface.markdown.ast
  "CST → neutral structural AST of the SLDB-MD profile (docs/v2/04 §3.2, §4,
   §7). Total: anything outside the profile becomes an :opaque block."
  (:require [clojure.string :as str]
            [sldb.surface.markdown.cst :as cst]
            [sldb.surface.markdown.inline :as inline]))

(declare blocks)

(defn- opaque [format blob] {:type :opaque :format format :blob blob})

(defn- heading-block [host b]
  (let [line (cst/strip-cr (first (:lines b)))
        [_ hashes rest] (re-matches #"^ {0,3}(#{1,6})(?: (.*)|)$" line)
        rest (-> (or rest "") (str/replace #"(?<!\\)\s+#+\s*$" "") (str/replace #"^#+$" "") str/trim)
        r (inline/parse host rest)]
    (if (= :unparsed r)
      (opaque "markdown/unparsed" (cst/block-text b))
      {:type :heading :attrs {:level (count hashes) :marks (:marks r)} :text (:text r)})))

(defn- paragraph-block [host b]
  (let [logical (str/join " " (map (comp str/trim cst/strip-cr) (:lines b)))
        r (inline/parse host logical)]
    (if (= :unparsed r)
      (opaque "markdown/unparsed" (cst/block-text b))
      {:type :paragraph :attrs {:marks (:marks r)} :text (:text r)})))

(defn- code-block [b]
  (let [{:keys [info]} (cst/fence-open (first (:lines b)))
        body (rest (:lines b))
        closed? (and (seq body)
                     (re-matches (re-pattern (str "^ {0,3}" (if (= (:char b) \`) "`" "~") "{" (:n b) ",} *$"))
                                 (cst/strip-cr (last body))))
        content (if closed? (butlast body) body)]
    {:type :code
     :attrs (if (str/blank? info) {} {:lang info})
     :text (str/join "\n" (map cst/strip-cr content))}))

(defn- quote-block [host b]
  (let [inner (str/join "\n" (map #(str/replace (cst/strip-cr %) #"^ {0,3}> ?" "") (:lines b)))]
    {:type :quote :attrs {} :children (blocks host (cst/parse inner))}))

(defn- dedent [line w]
  (let [k (min w (count (take-while #(= \space %) line)))] (subs line k)))

(defn- list-block [host b]
  (let [lines (mapv cst/strip-cr (:lines b))
        first-m (cst/list-marker (first lines))
        w (:width first-m)
        marker-type (fn [m] (if (:ordered? m) :ordered (:marker m)))
        ;; split into items at lines carrying a marker of the same type at indent < w
        items (reduce (fn [items line]
                        (let [m (cst/list-marker line)]
                          (if (and m (< (:indent m) w))
                            (conj items {:marker m :lines [line]})
                            (update items (dec (count items)) update :lines conj line))))
                      [] lines)
        mixed? (some #(not= (marker-type (:marker %)) (marker-type first-m)) items)
        item-ast (fn [{:keys [marker lines]}]
                   (let [first-line (str (apply str (repeat w \space))
                                         (subs (first lines) (+ (:indent marker) (:width marker))))
                         inner (str/join "\n" (map #(dedent % w) (cons first-line (rest lines))))
                         kids (blocks host (cst/parse inner))]
                     (when (and (seq kids) (contains? #{:paragraph :heading :code :opaque} (:type (first kids))))
                       {:type :item :attrs {} :children kids})))
        asts (map item-ast items)]
    (if (or mixed? (some nil? asts))
      (opaque "markdown/unparsed" (str/join "\n" lines))
      {:type :list
       :attrs (if (:ordered? first-m) {:ordered true :start (:number first-m)} {:ordered false})
       :children (vec asts)})))

(defn- block [host b]
  (case (:kind b)
    :blank          nil
    :heading        (heading-block host b)
    :paragraph      (paragraph-block host b)
    :code           (code-block b)
    :thematic-break {:type :thematic-break :attrs {}}
    :quote          (quote-block host b)
    :list           (list-block host b)
    :html           (opaque "markdown/html" (cst/block-text b))
    :table          (opaque "markdown/table" (cst/block-text b))))

(defn- blocks [host cst]
  (vec (keep #(block host %) (:blocks cst))))

(defn parse
  "Markdown text → neutral AST `{:type :document :attrs {} :children [...]}`."
  [host s]
  {:type :document :attrs {} :children (blocks host (cst/parse s))})
