(ns kimun.surface.models.segments
  "Explains a document as segments (sldb v1 `sldb.core.segments` /
   `sldb.api.segments`): which characters of the canonical render each field
   wrote, the literal template text in between, where those fields sit in a
   raw Markdown that is not canonical, and which section owns which field
   (v1 `extract_sections` / `_map_fields_to_sections`). Pure: the spans come
   from `render/render-marked`, whose sentinels are decoded here. Offsets are
   code points, as v1's Python `str` indices. Result maps keep v1's field
   names as keywords (`:canonical_text`, `:line_start`…); payloads keep string
   keys; `:type` is the string \"field\" or \"literal\"."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [kimun.surface.models.blocks :as blocks]
            [kimun.surface.models.extract :as extract]
            [kimun.surface.models.marker :as marker]
            [kimun.surface.models.render :as render]))

;; ── code-point addressing ────────────────────────────────────────────────

(defn- cp-count [^String s] (.codePointCount s 0 (count s)))

(defn- cp-index
  "Code-point offset of UTF-16 index `i` in `s`."
  [^String s i]
  (.codePointCount s 0 (int i)))

(defn- cp-sub
  "Substring of `s` between code-point offsets `a` and `b` (clamped)."
  [^String s a b]
  (let [n (cp-count s) a (max 0 (min n a)) b (max a (min n b))]
    (if (= n (count s))
      (subs s a b)
      (subs s (.offsetByCodePoints s 0 (int a)) (.offsetByCodePoints s 0 (int b))))))

(defn- cps
  "Vector of the code points of `s`."
  [^String s]
  (loop [i 0 out (transient [])]
    (if (< i (count s))
      (let [c (.codePointAt s (int i))] (recur (+ i (Character/charCount c)) (conj! out c)))
      (persistent! out))))

;; ── sentinel decoding (v1 sentinel_decoder) ──────────────────────────────

(defn- decode
  "`[text spans]` from a sentinel-marked render: the text without sentinels
   and `{:meta :start :end}` (UTF-16 offsets) in closing order, `:_order`
   being the order in which the spans were opened."
  [^String raw]
  (loop [i 0 out [] len 0 stack [] spans [] order 0]
    (let [m (re-matcher #"[\ue000\ue002]" raw)]
      (if-not (.find m (int i))
        [(apply str (conj out (subs raw i))) spans]
        (let [at (.start m) chunk (subs raw i at) len (+ len (count chunk)) out (conj out chunk)]
          (if (= "\ue000" (subs raw at (inc at)))
            (let [sep (.indexOf raw "\ue001" (int at))]
              (recur (inc sep) out len (conj stack [(assoc (edn/read-string (subs raw (inc at) sep)) :_order order) len]) spans (inc order)))
            (let [[meta start] (peek stack)]
              (recur (inc at) out len (pop stack) (conj spans {:meta meta :start start :end len}) order))))))))

(defn- strip-with-offsets
  "Trims `text` as the renderer does and rebases/clamps `spans` onto the
   result, converting their offsets to code points."
  [text spans]
  (let [lead (- (count text) (count (str/triml text)))
        stripped (str/trim text)
        at (fn [p] (cp-index stripped (max 0 (min (count stripped) (- p lead)))))]
    [stripped (mapv #(-> % (update :start at) (update :end at)) spans)]))

(defn- render-with-spans
  "v1 `render_with_spans`: `[canonical-text raw-spans]`."
  [descriptor payload ctx]
  (let [[text spans] (decode (render/render-marked descriptor payload ctx))]
    (strip-with-offsets text spans)))

;; ── segment nodes (v1 segment_nodes / segment_display / payload_values) ──

(defn- display-of [text start end]
  (let [n (cp-count text)
        own-lines? (and (or (zero? start) (= "\n" (cp-sub text (dec start) start)))
                        (or (= end n) (= "\n" (cp-sub text end (inc end)))))
        blank? (and (or (zero? start) (= "\n\n" (cp-sub text (max 0 (- start 2)) start)))
                    (or (= end n) (= "\n\n" (cp-sub text end (+ end 2)))))]
    (if (and own-lines? blank?) "block" "inline")))

(defn- field-kind
  "v1 `field_kind` over a descriptor field: its input kind. A field v1's
   exporter could only type as opaque but annotated as a Python `Enum`
   (`:py/annotation \"<enum 'X'>\"`) is an \"enum\", as v1 reports it."
  [{:keys [type] :as field}]
  (let [enum? #(and (vector? %) (= :enum (first %)))
        annotation (str/replace (str (:py/annotation field)) #"\s*\|\s*None$" "")]
    (cond (or (enum? type) (str/starts-with? annotation "<enum '")) "enum"
          (contains? #{"dict" "<class 'dict'>"} annotation) "object"
          (and (vector? type) (= :list (first type)))
          (cond (contains? #{:string :text} (second type)) "stringlist" (enum? (second type)) "enumlist" :else "list")
          :else (get {:string "string" :text "string" :bool "boolean" :int "integer" :float "number" :number "number" :map "object"}
                     type "unknown"))))

(defn- digits? [s] (boolean (and s (re-matches #"\d+" s))))

(defn- kind-at [descriptor path]
  (let [parts (str/split path #"/" -1)
        field (first (filter #(= (first parts) (:name %)) (:fields descriptor)))
        kind (when field (field-kind field))]
    (cond (nil? field) "unknown"
          (and (>= (count parts) 2) (digits? (second parts))) (get {"stringlist" "string" "enumlist" "enum"} kind "unknown")
          :else kind)))

(defn- value-at [payload path]
  (reduce (fn [cur part]
            (cond (and (sequential? cur) (digits? part) (< (parse-long part) (count cur))) (nth cur (parse-long part))
                  (and (map? cur) (contains? cur part)) (get cur part)
                  :else (reduced nil)))
          payload (remove str/blank? (str/split path #"/"))))

(defn- build-node [{:keys [meta start end]} text descriptor payload]
  {:type "field" :start start :end end :text (cp-sub text start end)
   :path (:path meta) :marker (:marker meta) :traits (vec (:traits meta))
   :editable (contains? #{"rev" "optrev"} (:marker meta))
   :display (display-of text start end)
   :kind (kind-at descriptor (:path meta)) :value (value-at payload (:path meta))
   :source nil :composition nil :children [] :_order (:_order meta)})

;; ── nesting and literals (v1 span_nesting / segment_tree) ────────────────

(defn- inside? [outer inner] (and (<= (:start outer) (:start inner)) (<= (:end inner) (:end outer))))

(defn- nest
  "Roots of the containment tree of `nodes`, already sorted by start, widest
   first, latest wrap first."
  [nodes]
  (loop [nodes nodes roots []]
    (if-let [n (first nodes)]
      (let [[in after] (split-with #(inside? n %) (rest nodes))]
        (recur after (conj roots (assoc n :children (nest in)))))
      roots)))

(defn- literal [text a b] {:type "literal" :start a :end b :text (cp-sub text a b)})

(defn- interleave-literals [nodes text lo hi]
  (let [[out cursor]
        (reduce (fn [[out cursor] n]
                  (let [out (if (> (:start n) cursor) (conj out (literal text cursor (:start n))) out)
                        kids (:children n)
                        n (-> (dissoc n :_order)
                              (assoc :children (if (seq kids) (interleave-literals kids text (:start n) (:end n)) [])))]
                    [(conj out n) (max cursor (:end n))]))
                [[] lo] nodes)]
    (if (< cursor hi) (conj out (literal text cursor hi)) out)))

(defn segment-tree
  "v1 `build_segment_tree`: flat `nodes` (`{:start :end :_order …}`, any other
   keys kept) nested by containment and interleaved with literals over
   `text[0, len)`. Nodes are ordered by start, widest first, and — at an
   identical range — latest `:_order` first, so a single piece covering exactly
   its field ends up inside it. `:_order` is dropped from the result; every
   node gets `:children` (possibly empty)."
  [nodes text]
  (interleave-literals (nest (sort-by (juxt :start (comp - :end) (comp - :_order)) nodes)) text 0 (cp-count text)))

(defn segments
  "The canonical render of `payload` under `descriptor`, explained:
   `{:text canonical :spans [{:meta {:path :marker :traits :_order} :start
   :end}] :segments [segment…]}`. `:spans` is v1's `render_with_spans` level
   (one per value a marker wrote, in writing order). `:segments` is v1's
   `DocumentSegments.segments`: literals `{:type \"literal\" :start :end
   :text}` and fields `{:type \"field\" :start :end :text :path :marker
   :traits :editable :display :kind :value :source :composition :children}`
   in reading order, a contained span nested in `:children` (again with its
   literals); the `:text` of the top-level segments concatenates to `:text`.
   Offsets are code points, `:end` exclusive. `ctx` is `render/render`'s."
  ([descriptor payload] (segments descriptor payload nil))
  ([descriptor payload ctx]
   (let [[text spans] (render-with-spans descriptor payload ctx)]
     {:text text :spans spans :segments (segment-tree (map #(build-node % text descriptor payload) spans) text)})))

;; ── alignment with a raw text (v1 canonical_alignment, difflib) ──────────

(defn- longest-match [a b2j alo ahi blo bhi]
  (loop [i alo j2len {} best [alo blo 0]]
    (if (< i ahi)
      (let [[nj best] (reduce (fn [[nj best] j]
                                (cond (< j blo) [nj best]
                                      (>= j bhi) (reduced [nj best])
                                      :else (let [k (inc (get j2len (dec j) 0))]
                                              [(assoc! nj j k) (if (> k (nth best 2)) [(inc (- i k)) (inc (- j k)) k] best)])))
                              [(transient {}) best] (get b2j (nth a i)))]
        (recur (inc i) (persistent! nj) best))
      best)))

(defn- matching-blocks [a b]
  (let [b2j (persistent! (reduce (fn [m j] (assoc! m (nth b j) (conj (get m (nth b j) []) j))) (transient {}) (range (count b))))
        found (loop [queue [[0 (count a) 0 (count b)]] out []]
                (if-let [[alo ahi blo bhi] (peek queue)]
                  (let [[i j k :as x] (longest-match a b2j alo ahi blo bhi)
                        queue (pop queue)]
                    (if (pos? k)
                      (recur (cond-> queue
                               (and (< alo i) (< blo j)) (conj [alo i blo j])
                               (and (< (+ i k) ahi) (< (+ j k) bhi)) (conj [(+ i k) ahi (+ j k) bhi]))
                             (conj out x))
                      (recur queue out)))
                  (sort out)))
        [out [i1 j1 k1]] (reduce (fn [[out [i1 j1 k1]] [i2 j2 k2]]
                                   (if (and (= (+ i1 k1) i2) (= (+ j1 k1) j2))
                                     [out [i1 j1 (+ k1 k2)]]
                                     [(if (pos? k1) (conj out [i1 j1 k1]) out) [i2 j2 k2]]))
                                 [[] [0 0 0]] found)]
    (conj (if (pos? k1) (conj out [i1 j1 k1]) out) [(count a) (count b) 0])))

(defn- opcodes
  "difflib's `SequenceMatcher(None, a, b, autojunk=False).get_opcodes()` over
   code-point vectors: `[tag i1 i2 j1 j2]` with tag :equal :replace :delete :insert."
  [a b]
  (first (reduce (fn [[out i j] [ai bj size]]
                   (let [tag (cond (and (< i ai) (< j bj)) :replace (< i ai) :delete (< j bj) :insert)
                         out (if tag (conj out [tag i ai j bj]) out)]
                     [(if (pos? size) (conj out [:equal ai (+ ai size) bj (+ bj size)]) out) (+ ai size) (+ bj size)]))
                 [[] 0 0] (matching-blocks a b))))

(defn- map-offset
  "`[raw-offset exact?]` for canonical offset `pos` (v1 `map_offset`)."
  [ops pos end?]
  (or (some (fn [[tag i1 i2 j1 j2]]
              (when (if end? (and (< i1 pos) (<= pos i2)) (and (<= i1 pos) (< pos i2)))
                (if (= :equal tag) [(+ j1 (- pos i1)) true] [(if end? j2 j1) false])))
            ops)
      [(if (seq ops) (nth (peek ops) 4) 0) false]))

(defn- unmatched-runs [ops raw]
  (->> ops
       (filter #(contains? #{:insert :replace} (first %)))
       (map (fn [[_ _ _ j1 j2]]
              (let [chunk (cp-sub raw j1 j2)
                    s (+ j1 (- (cp-count chunk) (cp-count (str/triml chunk))))
                    e (- j2 (- (cp-count chunk) (cp-count (str/trimr chunk))))]
                {:start s :end e :text (if (< s e) (cp-sub raw s e) "")})))
       (remove #(= "" (:text %)))
       vec))

(defn- py-round
  "Python's `round(x, n)` of a double: the decimal with `n` places nearest to
   the exact binary value of `x`, an exact tie going to the even digit (so
   `round(0.03125, 4)` is 0.0312, where `Math/round` on `x·10ⁿ` gives 0.0313
   and may also misplace values the multiplication pushes onto a tie)."
  [x n]
  #?(:clj (.doubleValue (.setScale (BigDecimal. (double x)) (int n) java.math.RoundingMode/HALF_EVEN))
     :cljs (let [scale (js/Math.pow 10 n) scaled (* x scale) low (js/Math.floor scaled)]
             ;; an exact tie survives the multiplication exactly (it is k/2ʲ); anything else rounds to nearest
             (/ (if (== 0.5 (- scaled low)) (if (even? low) low (inc low)) (js/Math.round scaled)) scale))))

(defn extract-segments
  "Where each field of `descriptor` sits in the raw `markdown` (v1
   `build_extraction_segments` → `ExtractionSegments`): the Markdown is
   extracted, its payload rendered canonically with spans, and each span
   mapped onto the raw text through a difflib alignment. Returns `{:model
   :text :payload :canonical_text :fields [{:type \"field\" :start :end :text
   :path :marker :traits :editable :display :kind :value :alignment}] :unmatched
   [{:start :end :text}] :coverage :canonical?}`: `:fields` in reading order
   over the raw text, `:alignment` \"exact\" when both borders fell on text
   identical to the canonical render (else \"approximate\"), `:unmatched` the
   trimmed raw runs the canonical render does not account for, `:coverage`
   the fraction (4 decimals) of the trimmed raw length that field spans cover.
   `:canonical?` is kimun's addition (v1 only lets callers compare the two
   texts): whether the trimmed raw text already is the canonical render.
   Raises what `extract/extract` raises when the Markdown does not extract."
  ([descriptor markdown] (extract-segments descriptor markdown nil))
  ([descriptor markdown ctx]
   (let [payload (extract/extract descriptor markdown)
         [canonical spans] (render-with-spans descriptor payload ctx)
         ops (if (and (= canonical markdown) (seq canonical))
               [[:equal 0 (cp-count canonical) 0 (cp-count canonical)]]
               (opcodes (cps canonical) (cps markdown)))
         fields (->> spans
                     (map (fn [span]
                            (let [node (build-node span canonical descriptor payload)
                                  [start exact-start] (map-offset ops (:start node) false)
                                  [end exact-end] (map-offset ops (:end node) true)
                                  end (max start end)]
                              (-> (select-keys node [:path :marker :traits :editable :display :kind :value])
                                  (assoc :type "field" :start start :end end :text (cp-sub markdown start end)
                                         :alignment (if (and exact-start exact-end) "exact" "approximate"))))))
                     (sort-by :start)
                     vec)
         covered (reduce + 0 (map #(max 0 (- (:end %) (:start %))) fields))
         total (cp-count (str/trim markdown))]
     {:model (:model/name descriptor) :text markdown :payload payload :canonical_text canonical
      :fields fields :unmatched (unmatched-runs ops markdown)
      :coverage (py-round (min 1.0 (/ (double covered) (if (zero? total) 1 total))) 4)
      :canonical? (= (str/trim markdown) canonical)})))

;; ── a whole document (v1 build_document_segments) ────────────────────────

(defn- py-strip
  "Python's `str.strip()`: trims every character `str.isspace()` accepts,
   which `clojure.string/trim` does not all cover (U+0085, U+00A0…)."
  [s]
  (let [ws "[\\s\\u001c-\\u001f\\u0085\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000]+"]
    (-> s (str/replace (re-pattern (str "^" ws)) "") (str/replace (re-pattern (str ws "$")) ""))))

(defn document-segments
  "v1 `build_document_segments` → `DocumentSegments`, with the store lookup
   replaced by its inputs: `markdown` is the text of the document file and
   `ctx` is `render/render`'s context, which may also carry the identity only
   a store knows — `:id` (the reference the caller asked for), `:doc` (the
   tracked name) and `:path` (the file path relative to the project root),
   each nil when absent. Returns `{:id :doc :model :path :text :canonical
   :payload :segments}`: `:payload` is what `markdown` extracts to (v1's
   runtime document), `:text` its canonical render and `:segments` the tree
   `segments` builds over it. `:canonical` is v1's `_is_canonical` without the
   file read: whether `markdown`, stripped as Python strips, already is
   `:text` — false when a composed source changed after the document was
   saved. v1's third answer, None for a document with no path or a file that
   cannot be read, has no counterpart here: the text is an argument, so
   `:canonical` is always a boolean. Raises what `extract/extract` raises
   when the Markdown does not extract."
  ([descriptor markdown] (document-segments descriptor markdown nil))
  ([descriptor markdown ctx]
   (let [payload (extract/extract descriptor markdown)
         {:keys [text] tree :segments} (segments descriptor payload ctx)]
     {:id (:id ctx) :doc (:doc ctx) :model (:model/name descriptor) :path (:path ctx)
      :text text :canonical (= (py-strip markdown) text)
      :payload payload :segments tree})))

;; ── sections and field ownership (v1 api/records) ────────────────────────

(defn- slugify [title]
  (let [slug (-> (str/lower-case title) (str/replace #"[^a-z0-9]+" "-") (str/replace #"^-+|-+$" ""))]
    (if (seq slug) slug "section")))

(defn- line-count [markdown]
  (if (= "" markdown)
    0
    (let [n (count (str/split markdown #"\r\n|\n|\r" -1))] (if (re-find #"[\n\r]$" markdown) (dec n) n))))

(defn sections
  "The sections of `markdown` (v1 `extract_sections`), one per non-empty
   heading in document order: `{:title :slug :level :path :line_start
   :line_end}`. `:path` joins the slugs of the enclosing headings with `/`
   (a repeated path gets `-2`, `-3`…); lines are 1-based and a section runs
   to the line before the next heading of its level or above, or to the end."
  [markdown]
  (let [found (:out (reduce (fn [{:keys [stack out] :as st} node]
                              (let [title (str/trim (or (blocks/leaf-text node) (:content node) ""))]
                                (if (or (not (re-matches #"h[1-6]" (or (:tag node) ""))) (= "" title))
                                  st
                                  (let [level (parse-long (subs (:tag node) 1))
                                        stack (loop [s stack] (if (and (seq s) (>= (:level (peek s)) level)) (recur (pop s)) s))
                                        parent (str/join "/" (map :slug stack))
                                        path-of #(if (seq parent) (str parent "/" %) %)
                                        taken (set (map :path out))
                                        base (slugify title)
                                        slug (first (remove #(taken (path-of %)) (cons base (map #(str base "-" %) (iterate inc 2)))))
                                        section {:title title :slug slug :level level :path (path-of slug)
                                                 :line_start (some-> (:map node) first inc) :line_end (some-> (:map node) second inc)}]
                                    {:stack (conj stack section) :out (conj out section)}))))
                            {:stack [] :out []} (blocks/parse markdown)))
        last-line (line-count markdown)]
    (vec (map-indexed (fn [i section]
                        (let [next-start (some #(when (and (<= (:level %) (:level section)) (:line_start %)) (:line_start %))
                                               (subvec found (inc i)))]
                          (assoc section :line_end (if next-start (dec next-start) last-line))))
                      found))))

(defn- template-of [template-or-descriptor]
  (if (string? template-or-descriptor) template-or-descriptor (:template template-or-descriptor)))

(defn- marker-lines
  "`{:order [field…] :at {field line}}` of the `rev`/`optrev` markers of
   `template`: first-appearance order and the 1-based line of the last marker."
  [template]
  (reduce (fn [acc [line-no line]]
            (reduce (fn [acc [_ inner]]
                      (let [m (marker/parse inner)]
                        (if (and (marker/data? m) (seq (:name m)))
                          (-> acc (update :order #(if (contains? (:at acc) (:name m)) % (conj % (:name m))))
                              (assoc-in [:at (:name m)] line-no))
                          acc)))
                    acc (re-seq marker/pattern line)))
          {:order [] :at {}} (map-indexed #(vector (inc %1) %2) (str/split template #"\n" -1))))

(defn field-lines
  "`{field line}` (v1 `_field_template_line_map`): the 1-based template line
   of each `rev`/`optrev` marker with a name — the last one when a field has
   several; `render` markers and nameless markers are ignored. `known-fields`
   is accepted for parity with v1, where a marker naming a field outside it
   only triggers a log warning: it never changes the result."
  ([template-or-descriptor] (field-lines template-or-descriptor nil))
  ([template-or-descriptor _known-fields]
   (:at (marker-lines (template-of template-or-descriptor)))))

(defn- owner-pairs
  "`[[field section-path]…]` in template order (first appearance of a field),
   empty when the rendered sections do not correspond to the template's."
  [template rendered]
  (let [lines (marker-lines template)
        own (sections template)
        rendered (vec rendered)
        correspond? (cond (< (count rendered) (count own)) false
                          (= 1 (count own)) (= (:level (first own)) (:level (first rendered)))
                          :else (= (count own) (count rendered)))]
    (if-not correspond?
      []
      (for [field (:order lines)
            :let [line-no (get (:at lines) field)
                  idx (last (keep-indexed (fn [i s] (when (and (:line_start s) (<= (:line_start s) line-no)) i)) own))]
            :when idx]
        [field (:path (nth rendered idx))]))))

(defn field-owners
  "`{field section-path}`: the section of the rendered document that owns
   each `rev`/`optrev` field of a template (v1 `_map_fields_to_sections`).
   `template-or-descriptor` is template text or a descriptor; `sections` is
   what `sections` returns for the rendered document. A field belongs to the
   last template heading at or above its (last) marker line, mapped by
   position onto the rendered sections; fields above the first heading have
   no owner, and the result is `{}` when the rendered headings do not
   correspond to the template's. `known-fields` is accepted for parity with
   v1, where it only triggers a log warning: it never changes the result."
  ([template-or-descriptor sections] (field-owners template-or-descriptor sections nil))
  ([template-or-descriptor sections _known-fields]
   (into {} (owner-pairs (template-of template-or-descriptor) sections))))

(defn- flatten-payload [payload prefix]
  (if (map? payload)
    (mapcat (fn [[k v]] (flatten-payload v (if (seq prefix) (str prefix "." k) (str k)))) payload)
    [[prefix payload]]))

(defn fields-of-section
  "The rows v1's `sldb sections fields` prints: for every leaf of `payload`
   (nested maps flattened with `.`), `{:field_path :value :owning_section}`,
   kept when `section-path` is blank or is the section that owns the field."
  [template-or-descriptor sections payload section-path]
  (let [owners (field-owners template-or-descriptor sections)]
    (vec (for [[path value] (flatten-payload payload "")
               :when (or (str/blank? section-path) (= section-path (get owners path)))]
           {:field_path path :value value :owning_section (get owners path)}))))
