(ns sldb.surface.markdown.inline
  "Inline parser of the SLDB-MD profile (docs/v2/04 §4, §5): one pass with a
   delimiter stack over a logical line, producing {:text Text :marks Marks}
   with grapheme offsets, or :unparsed when the line is outside the profile."
  (:require [clojure.string :as str]
            [sldb.kernel.ports :as ports]))

(def ^:private ascii-punct "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~")

(defn- punct? [c] (and c (str/includes? ascii-punct (str c))))
(defn- ws? [c] (or (nil? c) (boolean (re-matches #"\s" (str c)))))
(defn- alnum? [c] (and c (boolean (re-matches #"[\p{L}\p{N}]" (str c)))))
(defn- letter? [c] (and c (boolean (re-matches #"\p{L}" (str c)))))

(defn- run-length [s i c]
  (loop [j i] (if (and (< j (count s)) (= c (nth s j))) (recur (inc j)) (- j i))))

(defn mark-key
  "Canonical sort key of a mark: [s (- e) (name kind)] (docs/v2/04 §4)."
  [[kind s e]] [s (- e) (name kind)])

(defn valid-marks?
  "§4 mark rules: non-empty ranges in bounds, every pair disjoint or nested, no
   same-kind nesting or equal ranges, :code contains nothing, :link contains no link."
  [marks n]
  (and (every? (fn [[_ s e]] (and (integer? s) (integer? e) (<= 0 s) (< s e) (<= e n))) marks)
       (every? (fn [[[k1 s1 e1] [k2 s2 e2]]]
                 (let [disjoint (or (<= e1 s2) (<= e2 s1))
                       nested (or (and (<= s1 s2) (<= e2 e1)) (and (<= s2 s1) (<= e1 e2)))
                       outer (if (and (<= s1 s2) (<= e2 e1)) k1 k2)
                       inner (if (= outer k1) k2 k1)]
                   (and (or disjoint nested)
                        (not (and nested (= k1 k2)))
                        (not (and nested (= outer :code)))
                        (not (and nested (= outer :link) (= inner :link))))))
               (for [a marks b marks :when (not= a b)] [a b]))))

(defn- char->grapheme-index
  "Map from char offsets of `text` to grapheme indices; nil for non-boundaries."
  [host text]
  (let [gs (ports/graphemes (ports/segmenter host) text)]
    (loop [i 0 pos 0 m {}]
      (if (>= i (count gs))
        (assoc m pos i)
        (recur (inc i) (+ pos (count (nth gs i))) (assoc m pos i))))))

(declare parse*)

(defn- link-close-index
  "Index of the `]` closing a link whose text starts at `from`, skipping escapes and
   code spans; nil when there is none."
  [s from]
  (loop [i from]
    (cond (>= i (count s)) nil
          (= \\ (nth s i)) (recur (+ i 2))
          (= \` (nth s i)) (let [k (run-length s i \`)
                                 close (loop [j (+ i k)]
                                         (cond (>= j (count s)) nil
                                               (= \` (nth s j)) (let [r (run-length s j \`)] (if (= r k) j (recur (+ j r))))
                                               :else (recur (inc j))))]
                             (if close (recur (+ close k)) nil))
          (= \] (nth s i)) i
          :else (recur (inc i)))))

(defn- parse-link-text
  "Link text is parsed with the same rules but may not open another link."
  [host inner]
  (parse* host inner true))

(defn- parse*
  "Returns {:text :marks} with marks in CHAR offsets, or :unparsed. `in-link?`
   forbids opening another link."
  [host s & [in-link?]]
  (let [n (count s)]
    (loop [i 0 out "" stack [] marks []]
      (if (>= i n)
        (if (seq stack) :unparsed {:text (str out) :marks marks})
        (let [c (nth s i) prev (when (pos? i) (nth s (dec i))) nxt (nth s (inc i) nil)]
          (cond
            ;; escapes
            (= c \\)
            (if (punct? nxt)
              (recur (+ i 2) (str out nxt) stack marks)
              (recur (inc i) (str out "\\") stack marks))
            ;; code spans
            (= c \`)
            (let [k (run-length s i \`)
                  close (loop [j (+ i k)]
                          (cond (>= j n) nil
                                (= \` (nth s j)) (let [r (run-length s j \`)] (if (= r k) j (recur (+ j r))))
                                :else (recur (inc j))))]
              (if (or (nil? close) (= close (+ i k)))
                :unparsed
                (let [content (subs s (+ i k) close) start (count out) out (str out content)]
                  (recur (+ close k) out stack (conj marks [:code start (count out)])))))
            ;; emphasis / strong
            (= c \*)
            (let [k (run-length s i \*)
                  after (nth s (+ i k) nil)
                  can-open (not (ws? after))
                  can-close (not (ws? prev))
                  kinds (case k 1 [:emphasis] 2 [:strong] 3 [:strong :emphasis] nil)]
              (cond
                (nil? kinds) :unparsed
                (and can-close (seq stack)
                     (= (map :kind (take (count kinds) (rseq stack))) (reverse kinds)))
                ;; close innermost-first
                (let [closing (take (count kinds) (rseq stack))
                      pos (count out)]
                  (recur (+ i k) out (vec (drop-last (count kinds) stack))
                         (into marks (map (fn [{:keys [kind start]}] [kind start pos]) closing))))
                can-open
                (recur (+ i k) out (into stack (map (fn [kind] {:kind kind :start (count out)}) kinds)) marks)
                :else :unparsed))
            ;; links
            (= c \[)
            (let [close (when-not in-link? (link-close-index s (inc i)))]
              (if (nil? close)
                :unparsed
                (let [inner (subs s (inc i) close)
                      rest (subs s (inc close))]
                  (if-let [[_ url] (re-find #"^\(([^\s()]+)\)" rest)]
                    (let [parsed (parse-link-text host inner)]
                      (if (or (= :unparsed parsed) (nil? parsed) (str/blank? (:text parsed)))
                        :unparsed
                        (let [start (count out) out (str out (:text parsed))]
                          (recur (+ close 2 (count url) 1) out stack
                                 (-> marks
                                     (into (map (fn [[k a b & more]] (into [k (+ start a) (+ start b)] more)) (:marks parsed)))
                                     (conj [:link start (count out) url]))))))
                    :unparsed))))
            (= c \]) :unparsed
            (and (= c \!) (= nxt \[)) :unparsed
            (and (= c \<) (or (letter? nxt) (contains? #{\/ \! \?} nxt))) :unparsed
            (and (= c \_) (not= (boolean (alnum? prev)) (boolean (alnum? nxt)))) :unparsed
            :else (recur (inc i) (str out c) stack marks)))))))

(defn parse
  "{:text Text :marks Marks} (grapheme offsets, canonical order, NFC) or :unparsed."
  [host s]
  (let [r (parse* host s)]
    (if (= :unparsed r)
      :unparsed
      (let [text (ports/nfc (ports/text host) (:text r))
            ;; offsets were computed on the un-normalized text; NFC may change char lengths,
            ;; so recompute char offsets by re-running on NFC text when they differ
            r (if (= text (:text r)) r (parse* host (ports/nfc (ports/text host) s)))]
        (if (= :unparsed r)
          :unparsed
          (let [text (:text r)
                idx (char->grapheme-index host text)
                gcount (count (ports/graphemes (ports/segmenter host) text))
                marks (mapv (fn [[k s e & more]] (into [k (idx s) (idx e)] more)) (:marks r))]
            (if (and (every? (fn [[_ s e]] (and s e)) marks) (valid-marks? marks gcount))
              {:text text :marks (vec (sort-by mark-key marks))}
              :unparsed)))))))
