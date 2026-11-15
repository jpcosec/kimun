(ns kimun.surface.models.capture
  "C6 of docs/v2/16 §3.3: every capture of a selector carries the coordinates
   of where it was read, in the vocabulary of `kimun.surface.markdown.anchors`
   (S2a): block locators, line/byte ranges and block hashes. This is evidence
   of where the value came from under one reading of the document, not an
   identity: no capture has an `:id` and nothing is minted (docs/v2/14 §21;
   the identity of a unit is K1e's, docs/v2/16 §6).

   `capture-selector` returns, per field, `{:value :path :spans :leaf-hashes}`:
   - `:value` exactly what `selector/extract-selector` returns for the field;
   - `:path` the block locators (`{:section :type :n}`) of the leaf blocks the
     value was read from, in document order;
   - `:spans` one `{:lines [start end) :bytes [start end)}` per leaf block,
     parallel to `:path`;
   - `:leaf-hashes` the anchor hash of each leaf block, parallel to `:path`
     (spec 16 §3.3 C6 says `:leaf-hashes`; §6 says `:content-hash` for the same
     idea: this namespace follows C6, the capability definition).
   Ranges exist only when the block layer matches the neutral AST one to one
   (S2a): otherwise `:aligned?` is false and the captures carry the value alone.
   A field whose source is empty (an empty section) carries the value alone
   too. The `extract-selector` result is unchanged."
  (:require [clojure.string :as str]
            [kimun.host.default :as host]
            [kimun.kernel.canon :as canon]
            [kimun.surface.markdown.anchors :as anchors]
            [kimun.surface.markdown.ast :as ast]
            [kimun.surface.models.blocks :as blocks]
            [kimun.surface.models.selector :as selector]))

(defn aligned-spans
  "Line spans `[[start end) …]` of the top-level blocks of `text` when the block
   layer matches the neutral AST one to one (same count, each slice parsed
   alone yields one block with the digest of its AST child); nil otherwise."
  [text]
  (let [children (:children (ast/parse host/host text))]
    (try
      (let [bs (blocks/parse text)
            spans (mapv :map bs)
            lines (vec (str/split-lines text))
            slice (fn [[ls le]] (str/join "\n" (subvec lines (long ls) (long (min le (count lines))))))]
        (when (and (every? some? spans) (= (count bs) (count children))
                   (every? true? (map (fn [blk b]
                                        (let [kids (:children (ast/parse host/host (slice (:map blk))))]
                                          (and (= 1 (count kids))
                                               (= (canon/digest host/host (first kids))
                                                  (canon/digest host/host b)))))
                                      bs children)))
          spans))
      (catch Exception _ nil))))

(defn- overlaps? [[ls le] {[bs be] :lines}]
  (and (< ls be) (< bs le)))

(defn- coordinates
  "The `:path :spans :leaf-hashes` of the leaf blocks of `anchored` that the
   line range `lines` overlaps, or nil when none does."
  [anchored lines]
  (when-let [leaves (seq (filter #(overlaps? lines %) (:blocks anchored)))]
    {:path (mapv :locator leaves)
     :spans (mapv #(select-keys % [:lines :bytes]) leaves)
     :leaf-hashes (mapv :hash leaves)}))

(defn capture-selector
  "The captures of selector `steps` over Markdown `text` (same `mode` as
   `selector/extract-selector`): `{:captures {field {:value … :path … :spans …
   :leaf-hashes …}} :aligned? bool}` (see the namespace docstring). The values
   are those of `extract-selector`; the coordinates come from
   `kimun.surface.markdown.anchors` over spans the block layer shares with the
   neutral AST. Needs the tree-sitter pod exactly when a step does."
  ([steps text] (capture-selector steps text :legacy-v1-template))
  ([steps text mode]
   (let [text (str text)
         located (selector/extract-located steps text mode)
         spans (aligned-spans text)
         anchored (when spans (anchors/anchors host/host text {:spans spans}))]
     {:aligned? (boolean spans)
      :captures (into {}
                      (map (fn [[k {:keys [value lines]}]]
                             [k (merge {:value value}
                                       (when (and anchored lines) (coordinates anchored lines)))]))
                      located)})))
