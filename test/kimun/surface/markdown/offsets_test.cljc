(ns kimun.surface.markdown.offsets-test
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.host.text :as text]
            [kimun.surface.markdown.offsets :as off]))

(def h host/host)
(defn- fwd [s r] (off/cp-range->grapheme-range h s r))
(defn- back [s r] (off/grapheme-range->cp-range h s r))

(def family "👨‍👩‍👧") ; 5 code points, 1 grapheme
(def e-acute-decomposed "é")

(deftest ascii-and-astral
  (testing "ASCII is the identity"
    (is (= {:range [2 5] :text "hello"} (fwd "hello" [2 5]))))
  (testing "an astral char is 1 code point, and 1 grapheme (not 2 UTF-16 units)"
    (is (= [1 2] (:range (fwd "a😀b" [1 2]))))
    (is (= [1 2] (:range (back "a😀b" [1 2]))))))

(deftest nfc-changes-code-point-count
  (testing "e + U+0301 (2 cp) and precomposed U+00E9 (1 cp) give the same NFC range"
    (is (= [1 2] (:range (fwd (str "x" e-acute-decomposed "y") [1 3]))))
    (is (= [1 2] (:range (fwd "xéy" [1 2])))))
  (testing "the reported text is the NFC one, and the inverse counts the original text"
    (is (= "xéy" (:text (fwd (str "x" e-acute-decomposed "y") [0 1]))))
    (is (= {:range [1 3]} (back (str "x" e-acute-decomposed "y") [1 2]))))
  (testing "a Hangul jamo triple composes to one syllable: 3 cp in, 1 grapheme out"
    (is (= [1 2] (:range (fwd "a각b" [1 4]))))))

(deftest cuts-through-a-cluster-are-rejected-not-adjusted
  (testing "between base and combining mark"
    (is (= {:error :splits-grapheme :offset 2}
           (select-keys (fwd (str "x" e-acute-decomposed "y") [0 2]) [:error :offset]))))
  (testing "inside a ZWJ emoji sequence, at either end"
    (is (= :splits-grapheme (:error (fwd (str "a" family "b") [1 3]))))
    (is (= :splits-grapheme (:error (fwd (str "a" family "b") [3 6]))))
    (is (= [1 2] (:range (fwd (str "a" family "b") [1 6])))))
  (testing "inside CRLF (end of line is one grapheme)"
    (is (= :splits-grapheme (:error (fwd "a\r\nb" [1 2]))))
    (is (= [1 2] (:range (fwd "a\r\nb" [1 3]))))
    (is (= [2 3] (:range (fwd "a\r\nb" [3 4])))))
  (testing "an empty range inside a cluster is also a cut"
    (is (= :splits-grapheme (:error (fwd (str "x" e-acute-decomposed) [2 2]))))))

(deftest empty-out-of-range-and-malformed
  (is (= [3 3] (:range (fwd "hello" [3 3]))))
  (is (= [0 0] (:range (fwd "" [0 0]))))
  (is (= [5 5] (:range (fwd "hello" [5 5]))) "end of text is a boundary")
  (is (= {:error :out-of-range :length 5} (select-keys (fwd "hello" [2 6]) [:error :length])))
  (is (= :out-of-range (:error (back "hello" [0 9]))))
  (is (= :invalid-range (:error (fwd "hello" [3 2]))))
  (is (= :invalid-range (:error (fwd "hello" [-1 2]))))
  (is (= :invalid-range (:error (fwd "hello" [0 nil])))))

(deftest inverse-bounds-and-malformed-input
  (testing "a cluster of 2 code points is 1 grapheme: the inverse never returns a bound inside it"
    (let [lone-mark "á"]       ; one cluster; NFC is 1 grapheme, so no such bound exists
      (is (= [0 2] (:range (back lone-mark [0 1]))))
      (is (= :out-of-range (:error (back lone-mark [0 2]))))))
  (testing "negative offsets are malformed"
    (is (= :invalid-range (:error (back "abc" [-1 1]))))))

(deftest round-trip-on-grapheme-boundaries
  (let [s (str "ab" e-acute-decomposed family "\r\n" "🇪🇸" "éz각")
        n (count (text/graphemes (text/nfc s)))
        cps (mapv first (map vector (reductions + 0 (map #(.codePointCount ^String % 0 (count %)) (text/graphemes s)))))]
    (is (pos? n))
    (doseq [a (range (inc n)) b (range a (inc n))]
      (let [cp (:range (back s [a b]))]
        (is (some? cp) (str "graphemes " [a b] " map back"))
        (is (= [a b] (:range (fwd s cp))) (str "round trip " [a b]))
        (is (every? (set cps) cp) "inverse lands on cluster boundaries of the original")))))
