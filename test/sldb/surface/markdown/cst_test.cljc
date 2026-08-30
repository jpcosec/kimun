(ns sldb.surface.markdown.cst-test
  (:require [clojure.test :refer [deftest is testing]]
            [sldb.kernel.test-util :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [sldb.surface.markdown.cst :as cst]))

(defn- kinds [s] (mapv :kind (:blocks (cst/parse s))))

(defspec lossless-for-any-string 300
  (prop/for-all [s (gen/one-of [gen/string
                                (gen/fmap #(clojure.string/join %) (gen/vector (gen/elements ["a\n" "\r\n" "\n" "\t" " " "- x\n" "```\n" "> q\n" "# h\n" "\n\n" "|t|\n" "<p>\n"]) 0 12))])]
    (= s (cst/text (cst/parse s)))))

(deftest lossless-edge-cases
  (doseq [s ["" "\n" "\n\n" "a" "a\n" "a\r\nb\r\n" "a\r\nb" "\t\n \n" "```\nno close" "- a\n\n  b\n\nc"]]
    (is (= s (cst/text (cst/parse s))) (pr-str s))))

(deftest segmentation-rules
  (testing "block starts (rules 1–9)"
    (is (= [:blank] (kinds "   ")))
    (is (= [:code] (kinds "```\nx\n```")))
    (is (= [:code] (kinds "~~~py\nx\n~~~")))
    (is (= [:heading] (kinds "## t")))
    (is (= [:heading] (kinds "#")))
    (is (= [:thematic-break] (kinds "* * *")))
    (is (= [:thematic-break] (kinds "---")))
    (is (= [:quote] (kinds "> q")))
    (is (= [:list] (kinds "- a")))
    (is (= [:list] (kinds "12. a")))
    (is (= [:html] (kinds "<div>")))
    (is (= [:paragraph] (kinds "< 3")))
    (is (= [:table] (kinds "| a |")))
    (is (= [:paragraph] (kinds "plain"))))
  (testing "fences close only with the same char and enough length"
    (is (= [:code] (kinds "```\n~~~\nx\n```")))
    (is (= [:code :paragraph] (kinds "````\nx\n```\n````\ny")))
    (is (= [:code] (kinds "```\nx"))))
  (testing "quotes are cut by blank lines; paragraphs too"
    (is (= [:quote :blank :quote] (kinds "> a\n\n> b")))
    (is (= [:paragraph :blank :paragraph] (kinds "a\n\nb"))))
  (testing "lists: same marker continues, other marker family opens a new list, blank + indented continues, blank + other closes"
    (is (= [:list] (kinds "- a\n- b")))
    (is (= [:list :list] (kinds "- a\n* b")))
    (is (= [:list :list] (kinds "- a\n1. b")))
    (is (= [:list] (kinds "- a\n\n  b")))
    (is (= [:list :blank :paragraph] (kinds "- a\n\nb")))
    (is (= [:list] (kinds "- a\n  lazy? no, indented\nlazy yes")))
    (is (= [:list :heading] (kinds "- a\n# h")))
    (is (= 2 (:width (first (:blocks (cst/parse "- a")))))))
  (testing "html and table run until a blank line"
    (is (= [:html :blank :paragraph] (kinds "<div>\nx\n\ny")))
    (is (= [:table] (kinds "| a |\n| - |\n")))))
