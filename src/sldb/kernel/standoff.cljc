(ns sldb.kernel.standoff
  "Stand-off below the paragraph (docs/v2/02 §4, §4.1). The canonical leaf is a
   paragraph's plain NFC text with grapheme offsets; over it the deterministic
   UAX #29 layers — graphemes, words and sentences — are derived on demand and,
   when the caller supplies a memo (an atom map keyed by leaf id, composed by
   the host: `sldb.host.default/layer-cache`), cached there; they are never
   materialized as graph nodes and the kernel holds no global mutable state
   (docs/v2/03 §1). A stand-off address is
   `[leaf-id start end]` in grapheme offsets and resolves to the addressed text
   without any node existing: the address is virtual until an edge references a
   `:span` (`sldb.kernel.node`, `sldb.kernel.anchor`), which this namespace does
   not create."
  (:require [sldb.kernel.ports :as ports]
            [sldb.kernel.revision :as revision]
            [sldb.kernel.err :as err]))

(defn- leaf-text
  "The plain NFC text of the `:sign/:text` leaf `leaf-id` in `store`, or raises
   :standoff/unresolved-leaf when the id is absent or is not a text leaf (§4.1:
   a stand-off address inherits the ground of its leaf)."
  [store leaf-id]
  (let [n (revision/get-object store leaf-id)]
    (if (and (map? n) (= :sign (:class n)) (= :text (:kind n)) (string? (get-in n [:content :text])))
      (get-in n [:content :text])
      (err/raise :standoff/unresolved-leaf
                 (str "leaf does not resolve to a text leaf: " leaf-id)
                 {:leaf leaf-id}))))

(defn- derive-layers [store leaf-id]
  (let [seg (ports/segmenter (:host store))
        text (leaf-text store leaf-id)]
    {:graphemes (ports/graphemes seg text)
     :words (ports/words seg text)
     :sentences (ports/sentences seg text)}))

(defn layers
  "The deterministic UAX #29 layers of leaf `leaf-id` in `store`:
   {:graphemes [grapheme-string …] :words [[start end] …] :sentences [[start end] …]}
   with word and sentence ranges in grapheme offsets. Derived on demand
   (docs/v2/02 §4); with a `cache` (an atom holding a map keyed by leaf id)
   the derivation is memoized there — a node id is its content hash, so a leaf
   id fixes its text and the layers are a pure function of it — and with nil
   (the default) it is recomputed on every call. Raises
   :standoff/unresolved-leaf when the leaf is absent or not a text leaf.
   Deriving the layers never adds a node to the store."
  ([store leaf-id] (layers store leaf-id nil))
  ([store leaf-id cache]
   (if (nil? cache)
     (derive-layers store leaf-id)
     (or (get @cache leaf-id)
         (let [ls (derive-layers store leaf-id)]
           (swap! cache assoc leaf-id ls)
           (get @cache leaf-id))))))

(defn cached?
  "Whether the layers of `leaf-id` are currently memoized in `cache` (an atom
   map as accepted by `layers`); false for a nil cache."
  [cache leaf-id]
  (boolean (and cache (contains? @cache leaf-id))))

(defn grapheme-count
  "Number of UAX #29 graphemes of leaf `leaf-id` in `store` (docs/v2/02 §6.2:
   the ceiling every stand-off range is checked against). Optional trailing
   `cache` as in `layers`."
  ([store leaf-id] (grapheme-count store leaf-id nil))
  ([store leaf-id cache]
   (count (:graphemes (layers store leaf-id cache)))))

(defn resolve-address
  "The text addressed by the virtual stand-off address `[leaf-id start end]` in
   grapheme offsets over the leaf's NFC text (docs/v2/02 §4.1). Resolves without
   materializing any node. Raises :standoff/invalid-range when `start`/`end` are
   not offsets with 0 ≤ start ≤ end, :standoff/unresolved-leaf when the leaf is
   absent or not a text leaf, and :standoff/range-out-of-bounds when `end`
   exceeds the leaf grapheme count (mirroring the :span check of §6.2).
   Optional trailing `cache` as in `layers`."
  ([store leaf-id start end] (resolve-address store leaf-id start end nil))
  ([store leaf-id start end cache]
   (when-not (and (integer? start) (integer? end) (<= 0 start end))
     (err/raise :standoff/invalid-range
                (str "range must be grapheme offsets with 0 <= start <= end, got [" start " " end "]")
                {:leaf leaf-id :range [start end]}))
   (let [gs (:graphemes (layers store leaf-id cache))]
     (when (> end (count gs))
       (err/raise :standoff/range-out-of-bounds
                  (str "range end " end " exceeds leaf grapheme count " (count gs))
                  {:leaf leaf-id :range [start end] :grapheme-count (count gs)}))
     (apply str (subvec gs start end)))))
