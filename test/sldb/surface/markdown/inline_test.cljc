(ns sldb.surface.markdown.inline-test
  (:require [clojure.test :refer [deftest is testing]]
            [sldb.host.default :as host]
            [sldb.surface.markdown.inline :as inline]))

(def h host/host)
(defn- p [s] (inline/parse h s))

(deftest profile-rules
  (testing "plain, escapes, snake_case"
    (is (= {:text "a*b" :marks []} (p "a\\*b")))
    (is (= {:text "a\\b" :marks []} (p "a\\b")) "backslash before non-punctuation is literal")
    (is (= {:text "snake_case" :marks []} (p "snake_case")))
    (is (= :unparsed (p "_x_")) "flanking underscore is outside the profile")
    (is (= {:text "a _ b" :marks []} (p "a _ b"))))
  (testing "code spans"
    (is (= {:text "x" :marks [[:code 0 1]]} (p "`x`")))
    (is (= {:text "a`b" :marks [[:code 0 3]]} (p "``a`b``")))
    (is (= :unparsed (p "`x")))
    (is (= :unparsed (p "``")))
    (is (= {:text "*x*" :marks [[:code 0 3]]} (p "`*x*`")) "no marks inside code"))
  (testing "emphasis and strong"
    (is (= {:text "a b" :marks [[:emphasis 0 3]]} (p "*a b*")))
    (is (= {:text "a" :marks [[:strong 0 1]]} (p "**a**")))
    (is (= {:text "a" :marks [[:emphasis 0 1] [:strong 0 1]]} (p "***a***")))
    (is (= :unparsed (p "****a****")))
    (is (= :unparsed (p "*a **b* c**")) "crossed marks")
    (is (= :unparsed (p "*a")) "unclosed")
    (is (= :unparsed (p "* a*")) "delimiter next to a space cannot open")
    (is (= :unparsed (p "*a *b* c*")) "same kind nested"))
  (testing "links"
    (is (= {:text "x" :marks [[:link 0 1 "u"]]} (p "[x](u)")))
    (is (= {:text "a b" :marks [[:link 0 3 "http://x"] [:emphasis 2 3]]} (p "[a *b*](http://x)")))
    (is (= :unparsed (p "[x][r]")))
    (is (= :unparsed (p "[x]")))
    (is (= :unparsed (p "![i](u)")))
    (is (= :unparsed (p "[[wiki]]")))
    (is (= :unparsed (p "[^1] note")))
    (is (= :unparsed (p "x ] y"))))
  (testing "html inline and autolinks"
    (is (= :unparsed (p "<span>x</span>")))
    (is (= :unparsed (p "<https://x>")))
    (is (= {:text "a < b" :marks []} (p "a < b")))))

(deftest marks-canonical-order
  (is (= [[:strong 0 4] [:emphasis 0 2] [:code 3 4]]
         (:marks (p "***ab* `c`**"))))
  (is (inline/valid-marks? [[:strong 0 5] [:emphasis 0 2] [:code 3 4]] 5))
  (is (not (inline/valid-marks? [[:emphasis 0 3] [:strong 2 5]] 5)) "crossing")
  (is (not (inline/valid-marks? [[:emphasis 0 3] [:emphasis 1 2]] 5)) "same kind nested")
  (is (not (inline/valid-marks? [[:code 0 3] [:emphasis 1 2]] 5)) "code contains nothing")
  (is (not (inline/valid-marks? [[:link 0 3 "u"] [:link 1 2 "v"]] 5)) "link in link")
  (is (not (inline/valid-marks? [[:emphasis 2 2]] 5)) "empty range"))

(deftest grapheme-offsets
  (is (= {:text "é👨‍👩‍👧x" :marks [[:emphasis 1 2]]} (p "é*👨‍👩‍👧*x")) "offsets count grapheme clusters, not chars")
  (is (= (p "é") (p "é")) "NFC before offsets"))
