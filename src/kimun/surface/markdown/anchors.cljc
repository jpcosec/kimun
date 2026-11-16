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
            [kimun.kernel.ports :as ports]
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

(defn- text-leaves
  "The leaves of the document: the paragraphs and headings that have plain
   text, in document order, as `{:locator <block locator> :graphemes [g …]}`.
   The plain text is the `:text` of the neutral AST (inline markup removed,
   NFC), and graphemes are the kernel's (`ports/graphemes`), docs/v2/02 §4.1.
   Code, lists, quotes and opaque blocks (what is outside the SLDB-MD profile
   included) have no leaf: nothing is invented for them."
  [host blocks locators]
  (let [seg (ports/segmenter host)]
    (vec (for [[b loc] (map vector blocks locators)
               :when (and (#{:paragraph :heading} (:type b)) (seq (:text b)))]
           {:locator loc :graphemes (vec (ports/graphemes seg (:text b)))}))))

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
   - `:leaves` when true, also `:leaves` (the plain text of each paragraph and
     heading, as graphemes of its NFC text, with the block locator) and a
     `:digest-fn`, what `span-anchor` and the marks of kind `:span` (S2b-r) need.
     Off by default: the result is then exactly what it always was.

   Section hashes are the digest of their blocks (heading and subsections
   included); a block hash is the digest of the block. Neither is an identity."
  ([host text] (anchors host text {}))
  ([host text {:keys [spans parse-contract leaves]}]
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
       parse-contract (assoc :parse-contract parse-contract)
       leaves (assoc :leaves (text-leaves host blocks locators)
                     :digest-fn (fn [v] (canon/digest host v)))))))

(defn span-locator?
  "True when `locator` names a text range inside a block (S2b-r): `{:block <block
   locator> :range [start end]}`."
  [locator]
  (and (map? locator) (contains? locator :block) (contains? locator :range)))

(defn span-anchor
  "The anchor of the span `locator` (`{:block L :range [start end)}`) in
   `anchors` read with `:leaves`: `{:locator :hash :text}`, the hash being the
   digest of the sub-text. The range counts GRAPHEMES of the NFC plain text of
   the block (docs/v2/02 §4.1, decision 32), not source characters: the inline
   markup is not in it. Otherwise a typed rejection `{:error kind :why …}`, never
   an adjustment: `:invalid-range` (not two integers, negative, or empty:
   `start < end` is required), `:no-leaves` (anchors read without `:leaves`),
   `:unresolved` (no such block), `:no-plain-text` (the block is code, a list, a
   quote, or opaque, so outside the profile too) and `:out-of-range` (the range
   ends past the text). A range cannot cut a grapheme here: it is already
   counted in them (a producer counting code points maps through
   `kimun.surface.markdown.offsets`, which rejects that)."
  [anchors locator]
  (let [{:keys [block range]} locator
        [s e] range
        leaf (some #(when (= block (:locator %)) %) (:leaves anchors))
        known (some #(when (= block (:locator %)) %) (:blocks anchors))]
    (cond
      (not (and (sequential? range) (= 2 (count range)) (integer? s) (integer? e) (<= 0 s) (< s e)))
      {:error :invalid-range :range range
       :why "a span range is [start end) of whole numbers with 0 <= start < end (an empty range is not a mark)"}
      (nil? (:digest-fn anchors)) {:error :no-leaves :why "the anchors were read without :leaves"}
      leaf (let [g (:graphemes leaf) n (count g)]
             (if (> e n)
               {:error :out-of-range :range range :length n
                :why (str "the range ends past the plain text of the block (" n " graphemes)")}
               (let [t (apply str (subvec g s e))]
                 {:locator locator :hash ((:digest-fn anchors) t) :text t})))
      known {:error :no-plain-text :block block
             :why (str "a block of type `" (name (:type block)) "` has no plain text to mark a range in")}
      :else {:error :unresolved :block block :why "no block of this document has that locator"})))

(defn- span-twins
  "Every window of the plain text of the document with the length of `loc`'s
   range and the digest `h`, other than the range `a` resolves to, in document
   order, as `{:locator :hash}`. Windows may overlap. Used as CANDIDATES only."
  [anchors loc a h]
  (let [[s e] (:range loc)
        len (when (and (integer? s) (integer? e)) (- e s))
        dig (:digest-fn anchors)]
    (if-not (and dig len (pos? len))
      []
      (vec (for [{bl :locator g :graphemes} (:leaves anchors)
                 i (range 0 (inc (- (count g) len)))
                 :let [l {:block bl :range [i (+ i len)]}]
                 :when (and (not= l (:locator a))
                            (= h (dig (apply str (subvec g i (+ i len))))))]
             {:locator l :hash h})))))

(defn resolve-anchor
  "The anchor (a section, a block or, when `field-anchors` were merged in, an
   item or a nested key) of `anchors` whose `:locator` equals `locator`, or nil. Exact equality: a locator is read under one contract and
   one state of the document, not resolved by approximation."
  [anchors locator]
  (if (span-locator? locator)
    (let [r (span-anchor anchors locator)] (when-not (:error r) r))
    (some #(when (= locator (:locator %)) %)
          (concat (:sections anchors) (:blocks anchors) (:items anchors) (:nested anchors)))))

;; ---------------------------------------------------------------- structural marks (K1e)

(defn- nested-paths
  "Every key path under map `m`, with the value found there, as `[path value]`
   in sorted-key order (maps only: nothing inside a list)."
  [m prefix]
  (mapcat (fn [[k v]]
            (let [p (conj prefix k)]
              (cons [p v] (when (map? v) (nested-paths v p)))))
          (sort-by (comp str key) m)))

(defn field-anchors
  "Anchors BELOW a field, from the `payload` of a typed document (K1h): `{:items
   [{:locator {:field f :index i} :hash h}] :nested [{:locator {:field f :path
   [k …]} :hash h}]}`. An ITEM is each element of a field whose value is a list;
   a NESTED key is each key path under a field whose value is a map. The hash is
   `canon/digest` of the extracted value (canonical), never an identity: the
   index of an item shifts when something is inserted before it, exactly like
   a block ordinal. Merge the result into the text `anchors` to read marks of
   kind `:item` and `:nested` with `mark-states`/`advance-marks`."
  [host payload]
  (let [anchor (fn [loc v] {:locator loc :hash (canon/digest host v)})]
    (reduce-kv
     (fn [acc f v]
       (cond
         (sequential? v) (update acc :items into (map-indexed (fn [i x] (anchor {:field f :index i} x)) v))
         (map? v) (update acc :nested into (map (fn [[p x]] (anchor {:field f :path p} x)) (nested-paths v [])))
         :else acc))
     {:items [] :nested []}
     (into (sorted-map) payload))))

(defn- entries-of
  "The anchors of `anchors` of `kind` (`:section`, `:block`, or, when
   `field-anchors` were merged in, `:item` and `:nested`)."
  [anchors kind]
  (case kind
    :section (:sections anchors)
    :block (:blocks anchors)
    :item (:items anchors)
    :nested (:nested anchors)))

(defn- anchor-at
  "The anchor of `kind` whose locator is `locator`, or nil."
  [anchors kind locator]
  (if (= :span kind)
    (let [r (span-anchor anchors locator)] (when-not (:error r) r))
    (first (filter #(= locator (:locator %)) (entries-of anchors kind)))))

(defn- same-hash
  "Anchors of `kind` other than `a` whose hash is `h`. An item or a nested key
   is only compared inside its own field (`loc`'s `:field`): the same value in
   another field is another thing."
  [anchors kind a h loc]
  (if (= :span kind)
    (span-twins anchors loc a h)
    (vec (remove #(identical? % a)
               (filter #(and (= h (:hash %))
                             (or (not (#{:item :nested} kind))
                                 (= (:field loc) (:field (:locator %)))))
                       (entries-of anchors kind))))))

(defn- candidate
  "A candidate for a mark: its locator, why it is offered and, when the block
   layer aligned, its lines."
  [a reason]
  (cond-> {:locator (:locator a) :reason reason}
    (:lines a) (assoc :lines (:lines a))))

(defn mark-states
  "The state of every structural mark of `marks` (`{id {:kind :locator :hash
   :label}}`) against the `anchors` of the text read now, as
   `[{:id :kind :label :locator :status :candidates}]` sorted by id.

   - `:anchored` the locator resolves and the hash is the registered one;
   - `:mutated` it resolves to something else and the registered hash appears
     nowhere else (an edit in place is the default; a replacement is declared);
   - `:ambiguous` it resolves to something else AND the registered hash appears
     elsewhere: probably a shifted ordinal, never decided here;
   - `:missing` the locator does not resolve: no candidate when the hash is
     nowhere, the anchors with that hash when it is elsewhere.

   A mark of kind `:span` (S2b-r; `anchors` read with `:leaves`) reads with the
   same table: its locator is `{:block L :range [s e)}`, its hash that of the
   sub-text, and the anchors with that hash are the other windows of that length
   in the plain text of ANY block of the document (not only its own: an ordinal
   shift would otherwise look like an edit in place).

   Every candidate is a PROPOSAL `{:locator :reason :lines?}`, reason
   `:locator` or `:hash`; one-to-many lists all of them and never picks one."
  [anchors marks]
  (vec (sort-by :id
                (for [[id m] marks]
                  (let [kind (:kind m)
                        loc (:locator m)
                        h (:hash m)
                        a (anchor-at anchors kind loc)
                        others (same-hash anchors kind a h loc)
                        status (cond (and a (= h (:hash a))) :anchored
                                     (and a (empty? others)) :mutated
                                     a :ambiguous
                                     :else :missing)
                        cands (cond-> []
                                (= :ambiguous status) (conj (candidate a :locator))
                                ;; an anchored mark has no candidates, even when its
                                ;; hash has a twin elsewhere: nothing is in doubt
                                (and (not= :anchored status) (seq others))
                                (into (map #(candidate % :hash) others)))]
                    {:id id :kind kind :label (:label m) :locator loc
                     :status status :candidates cands})))))

(defn advance-marks
  "The marks after a normal write, read against the `anchors` of the text
   written: an `:anchored` mark stays as it is; a `:mutated` one records the new
   hash (same unit, same id, same locator: while the anchoring resolves a change
   is a mutation); `:ambiguous` and `:missing` are NOT touched; no id, locator or
   mark is ever removed here."
  [anchors marks]
  (let [states (into {} (map (juxt :id identity)) (mark-states anchors marks))]
    (into (sorted-map)
          (map (fn [[id m]]
                 (let [st (get states id)
                       new-hash (some-> (anchor-at anchors (:kind m) (:locator m)) :hash)]
                   [id (if (and (= :mutated (:status st)) new-hash)
                         (assoc m :hash new-hash)
                         m)])))
          marks)))
