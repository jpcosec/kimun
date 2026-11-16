(ns kimun.surface.markdown.offsets
  "Pure boundary mapping between code-point offsets and grapheme offsets over
   the NFC form of a text (docs/v2/14 §7 and Q9, decision 32: an anchor is
   leaf + [start end) in NFC graphemes; ranges counted in code points by an
   external producer are translated on entry; docs/v2/02 §4.1).

   Offsets are measured over the text passed in, never over a whole document:
   which text that is belongs to the caller. A range that does not fall on
   grapheme boundaries is rejected with a typed result, never adjusted.

   Results are plain maps: `{:range [start end]}` on success, otherwise
   `{:error kind :why string ...}` with kind one of `:invalid-range`,
   `:out-of-range`, `:splits-grapheme`, `:splits-nfc-grapheme`,
   `:segmentation-diverges`."
  (:require [kimun.kernel.ports :as ports]))

(defn- cp-count
  "Number of Unicode code points of `s`."
  [s]
  #?(:clj  (.codePointCount ^String s 0 (count s))
     :cljs (count (js/Array.from s))))

(defn- prefix-sums
  "[0 n1 n1+n2 …] of the numbers `ns`."
  [ns]
  (reduce (fn [acc n] (conj acc (+ (peek acc) n))) [0] ns))

(defn- table
  "Boundary table of `text`: `{:nfc string :cps [..] :gs [..]}` where `:cps`
   holds the code-point offset of every grapheme boundary of the original text
   and `:gs` the offset of the same boundary in NFC graphemes (both start at 0
   and end at the totals). Nil `:error` fields mean consistent; otherwise the
   result is `{:error :segmentation-diverges}`: NFC of the clusters does not
   segment like NFC of the whole text, so no per-cluster mapping exists."
  [host text]
  (let [seg (ports/segmenter host)
        nrm (ports/text host)
        clusters (ports/graphemes seg text)
        nfc-clusters (mapv #(ports/nfc nrm %) clusters)
        per (mapv #(ports/graphemes seg %) nfc-clusters)
        nfc-text (ports/nfc nrm text)]
    (if (and (= nfc-text (apply str nfc-clusters))
             (= (vec (mapcat identity per)) (ports/graphemes seg nfc-text)))
      {:nfc nfc-text
       :cps (prefix-sums (map cp-count clusters))
       :gs (prefix-sums (map count per))}
      {:error :segmentation-diverges
       :why "NFC of the grapheme clusters of the text does not segment like NFC of the whole text"})))

(defn- check-range
  "Nil when `[s e]` is a well-formed range of integers with 0 <= s <= e <= `n`,
   else the typed error."
  [[s e :as r] n unit]
  (cond (not (and (vector? r) (= 2 (count r)) (integer? s) (integer? e) (<= 0 s) (<= s e)))
        {:error :invalid-range :range r
         :why "a range is [start end) of integers with 0 <= start <= end"}
        (> e n)
        {:error :out-of-range :range r :length n
         :why (str "range ends past the text (" n " " unit ")")}))

(defn- translate
  "Maps both ends of `r` through `from`→`to` boundary vectors; a typed error
   with `split-kind` when an end is not a boundary."
  [from to r split-kind unit]
  (let [idx (zipmap from (range))
        ends (mapv idx r)]
    (if-let [bad (first (remove idx r))]
      {:error split-kind :range r :offset bad
       :why (str "offset " bad " (" unit ") falls inside a grapheme cluster; not adjusted")}
      {:range (mapv #(nth to %) ends)})))

(defn cp-range->grapheme-range
  "`[start end)` in code points over `text` → `{:range [start end)}` in
   graphemes over the NFC form of `text`, plus `:text` (that NFC form). Empty
   ranges are kept (`[k k]`). A bound inside a grapheme cluster (combining
   mark, ZWJ emoji, CRLF, regional-indicator pair) yields `:splits-grapheme`;
   outside the text, `:out-of-range`; malformed, `:invalid-range`."
  [host text range*]
  (let [t (table host text)]
    (or (when (:error t) t)
        (check-range range* (peek (:cps t)) "code points")
        (assoc (translate (:cps t) (:gs t) range* :splits-grapheme "code points")
               :text (:nfc t)))))

(defn grapheme-range->cp-range
  "Inverse: `[start end)` in graphemes over the NFC form of `text` →
   `{:range [start end)}` in code points over `text` as given (not NFC).
   `:splits-nfc-grapheme` when a bound falls between NFC graphemes that come
   from one original cluster; `:out-of-range` / `:invalid-range` as above."
  [host text range*]
  (let [t (table host text)]
    (if (:error t)
      t
      (or (check-range range* (peek (:gs t)) "graphemes")
          (translate (:gs t) (:cps t) range* :splits-nfc-grapheme "graphemes")))))
