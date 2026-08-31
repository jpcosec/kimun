(ns sldb.host.text
  "Host adapter implementing `sldb.kernel.ports/TextNormalizer` and
   `sldb.kernel.ports/TextSegmenter` (docs/v2/02 §2.1 step 1, §4, §8.1):
   Unicode NFC and the UAX #29 grapheme, word and sentence layers via
   java.text.BreakIterator on Babashka/JVM and Intl.Segmenter on ClojureScript."
  (:require [sldb.kernel.ports :as ports])
  #?(:clj (:import [java.text Normalizer Normalizer$Form BreakIterator])))

(defn nfc
  "Unicode NFC normalization of a string (UAX #15). Identity for non-strings."
  [s]
  (if (string? s)
    #?(:clj  (Normalizer/normalize ^String s Normalizer$Form/NFC)
       :cljs (.normalize s "NFC"))
    s))

(defrecord Nfc []
  ports/TextNormalizer
  (nfc [_ s] (nfc s)))

(def normalizer
  "The standard TextNormalizer."
  (->Nfc))

(defn graphemes
  "Vector of grapheme clusters (UAX #29) of `s`: java.text.BreakIterator on the
   JVM, Intl.Segmenter on Node."
  [s]
  #?(:clj  (let [bi (BreakIterator/getCharacterInstance)]
             (.setText bi ^String s)
             (loop [start (.first bi) end (.next bi) acc []]
               (if (= end BreakIterator/DONE)
                 acc
                 (recur end (.next bi) (conj acc (subs s start end))))))
     :cljs (mapv #(.-segment %) (js/Array.from (.segment (js/Intl.Segmenter. js/undefined #js {:granularity "grapheme"}) s)))))

(defn- grapheme-offset
  "A function code-unit-offset → grapheme-offset for `s`. Boundaries reported by
   a word/sentence BreakIterator always fall on grapheme boundaries, so every
   segment endpoint maps to an exact grapheme index."
  [s]
  (let [ends (reduce (fn [acc g] (conj acc (+ (peek acc) (count g)))) [0] (graphemes s))]
    (fn [code-offset]
      (dec (count (take-while #(<= % code-offset) ends))))))

#?(:clj
   (defn- break-ranges
     "Vector of [start end] grapheme-offset ranges of `s` under BreakIterator
      `bi`, in order and covering `s`."
     [^BreakIterator bi s]
     (let [->g (grapheme-offset s)]
       (.setText bi ^String s)
       (loop [start (.first bi) end (.next bi) acc []]
         (if (= end BreakIterator/DONE)
           acc
           (recur end (.next bi) (conj acc [(->g start) (->g end)])))))))

#?(:cljs
   (defn- intl-ranges
     "Vector of [start end] grapheme-offset ranges of `s` for Intl.Segmenter
      granularity `gran` (\"word\" or \"sentence\")."
     [gran s]
     (let [->g (grapheme-offset s)
           segs (js/Array.from (.segment (js/Intl.Segmenter. js/undefined #js {:granularity gran}) s))]
       (mapv (fn [seg] (let [i (.-index seg) t (.-segment seg)]
                         [(->g i) (->g (+ i (.-length t)))]))
             segs))))

(defn words
  "Vector of [start end] grapheme-offset ranges of the UAX #29 word segments of
   `s`, in order and covering it."
  [s]
  #?(:clj  (break-ranges (BreakIterator/getWordInstance) s)
     :cljs (intl-ranges "word" s)))

(defn sentences
  "Vector of [start end] grapheme-offset ranges of the UAX #29 sentence segments
   of `s`, in order and covering it."
  [s]
  #?(:clj  (break-ranges (BreakIterator/getSentenceInstance) s)
     :cljs (intl-ranges "sentence" s)))

(defrecord Segmenter []
  ports/TextSegmenter
  (graphemes [_ s] (graphemes s))
  (words [_ s] (words s))
  (sentences [_ s] (sentences s)))

(def segmenter
  "The standard TextSegmenter."
  (->Segmenter))
