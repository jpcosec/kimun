(ns kimun.surface.markdown.anchors
  "Structural anchors of a document as EVIDENCE, with no identity (S2a of the
   parser plan, docs/v2/12 §1 S2, docs/v2/14 §13): a locator inside the
   document plus the reading contract it was computed under, and a hash of what
   is there. Nothing here mints an id, writes a store or touches a cache: it is
   a pure function over the neutral AST (`kimun.surface.markdown.ast/parse`),
   which both parsers share (docs/v2/21, rule 6: the anchoring is a locator and
   a contract; the identity of a unit comes from `K1e` when it is marked).

   - A SECTION is a heading and everything up to the next heading of the same
     or a higher level; sections nest by level. What comes before the first
     heading (frontmatter included) belongs to the root section (empty path).
   - A BLOCK is each direct child of the document (`:heading :paragraph :list
     :code :quote :opaque`). Nothing inside a block is anchored here.
   - A section locator is its path of titles with the ordinal among sisters
     that share the title: `[{:title \"A\" :n 0} {:title \"B\" :n 0}]`.
   - A block locator is `{:section <path> :type <type> :n <ordinal among the
     direct-content blocks of that type in that section>}`; the heading of a
     section is the block `{:section <its own path> :type :heading :n 0}`.
   - Ordinals SHIFT when something is inserted: a locator is stable evidence of
     where a block was read, never an identity.
   - The hash is `canon/digest` of the sub-AST (the block, or the vector of the
     section's blocks with its heading and its subsections).

   Line/byte ranges are not in the neutral AST: they are taken from an
   already-aligned list of line spans (`:spans`, one per top-level AST child,
   in order) that the caller gets from the block layer
   (`kimun.surface.models.blocks/parse`) after checking it matches the AST one
   to one. Without aligned spans the result simply carries no ranges (the ring
   of this namespace does not let it ask that layer itself). The reading
   contract is likewise supplied by the caller (`:parse-contract`): it is not
   computed here, so nothing about the environment is assumed."
  (:require [kimun.kernel.canon :as canon]
            [kimun.surface.markdown.ast :as ast]))

(defn- heading? [b] (= :heading (:type b)))
(defn- level-of [b] (or (get-in b [:attrs :level]) 0))

(defn- title-of
  "The heading's text as it is (the AST already holds the inline text)."
  [b]
  (or (:text b) ""))

(defn- offsets
  "Byte offset of the start of each line of `text` (0-based), so a line span
   becomes a byte span."
  [^String text]
  (let [n (count text)]
    (loop [i 0 acc [0]]
      (if (>= i n)
        acc
        (recur (inc i) (if (= \newline (.charAt text i)) (conj acc (inc i)) acc))))))

(defn- byte-span
  "`[start end)` bytes of lines `[ls le)` of `text`, or nil when the text has
   fewer lines that that."
  [starts text [ls le]]
  (let [n (count starts)]
    (when (and (<= 0 ls) (<= ls le) (<= le n))
      [(nth starts ls)
       (if (< le n) (nth starts le) (count text))])))

(defn- section-tree
  "Walks the top-level `blocks` and returns
   `{:sections [{:locator :level :from :to}] :block-sections [path …]}`: the
   sections in document order with the index range of their blocks (heading
   included) and, per block index, the path of the section that holds it. The
   root section (path `[]`) is implicit and not listed."
  [blocks]
  (let [n (count blocks)
        root {:path [] :level 0 :counts {} :from 0}
        emit (fn [top to] {:locator (:path top) :level (:level top) :from (:from top) :to to})]
    (loop [i 0
           stack [root]
           sections []
           block-sections []]
      (if (>= i n)
        {:sections (vec (sort-by :from
                                 (concat sections (map #(emit % n) (rest stack)))))
         :block-sections (vec block-sections)}
        (let [b (nth blocks i)]
          (if (heading? b)
            (let [lvl (level-of b)
                  [closed stack] (loop [stack stack closed []]
                                   (if-let [top (peek stack)]
                                     (if (>= (:level top) lvl)
                                       (recur (pop stack) (conj closed (emit top i)))
                                       [closed stack])
                                     [closed stack]))
                  parent (peek stack)
                  t (title-of b)
                  sib (get (:counts parent) t 0)
                  path (conj (:path parent) {:title t :n sib})
                  parent (update-in parent [:counts t] (fnil inc 0))
                  stack (conj (pop stack) parent)]
              (recur (inc i)
                     (conj stack {:path path :level lvl :from i :counts {}})
                     (into sections closed)
                     (conj (vec block-sections) path)))
            (recur (inc i) stack sections
                   (conj (vec block-sections) (:path (peek stack))))))))))

(defn- block-locators
  "The locator of every top-level block, from its section path and its ordinal
   among the blocks of the same type that are direct content of that section."
  [blocks block-sections]
  (let [seen (atom {})]
    (mapv (fn [b path]
            (let [k [path (:type b)]
                  n (get @seen k 0)]
              (swap! seen assoc k (inc n))
              {:section path :type (:type b) :n n}))
          blocks block-sections)))

(defn anchors
  "Structural anchors of Markdown `text` under `host` (see the namespace
   docstring): `{:sections [{:locator :level :hash :lines :bytes :blocks}]
   :blocks [{:locator :hash :lines :bytes}] :parse-contract c}` in document
   order, plus the `:parse-contract` the caller supplied.

   `opts`:
   - `:spans` `[[start-line end-line) …]`, one per top-level AST child, in
     order: line ranges taken from an aligned block layer. When absent (or when
     its count does not match the AST) no `:lines`/`:bytes` are emitted.
   - `:parse-contract` the reading contract the caller recorded; copied as is.

   Section hashes are the digest of their blocks (heading and subsections
   included); a block hash is the digest of the block. Neither is an identity."
  ([host text] (anchors host text {}))
  ([host text {:keys [spans parse-contract]}]
   (let [blocks (:children (ast/parse host text))
         {:keys [sections block-sections]} (section-tree blocks)
         locators (block-locators blocks block-sections)
         starts (offsets (str text))
         aligned? (and spans (= (count spans) (count blocks)))
         block-range (fn [i] (when aligned?
                               (let [[ls le] (nth spans i)
                                     b (byte-span starts text [ls le])]
                                 {:lines [ls le] :bytes b})))
         block-anchors (mapv (fn [b loc i]
                               (cond-> {:locator loc :hash (canon/digest host b)}
                                 (block-range i) (merge (block-range i))))
                             blocks locators (range))
         section-anchors (mapv (fn [{:keys [locator level from to]}]
                                 (let [bs (subvec blocks from to)
                                       range (when aligned? (block-range from))
                                       end (when aligned? (block-range (dec to)))]
                                   (cond-> {:locator locator :level level
                                            :hash (canon/digest host (vec bs))
                                            :blocks (count bs)}
                                     range (assoc :lines [(first (:lines range))
                                                          (second (:lines end))])
                                     range (assoc :bytes [(first (:bytes range))
                                                          (second (:bytes end))]))))
                               sections)]
     (cond-> {:sections section-anchors :blocks block-anchors}
       parse-contract (assoc :parse-contract parse-contract)))))

(defn resolve-anchor
  "The anchor (a section or a block) of `anchors` whose `:locator` equals
   `locator`, or nil. Exact equality: a locator is read under one contract and
   one state of the document, not resolved by approximation."
  [anchors locator]
  (or (some #(when (= locator (:locator %)) %) (:sections anchors))
      (some #(when (= locator (:locator %)) %) (:blocks anchors))))
