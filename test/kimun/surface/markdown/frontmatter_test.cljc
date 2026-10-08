(ns kimun.surface.markdown.frontmatter-test
  "Frontmatter as an opaque extension of the profile (docs/v2/07 §5) and pipe
   tables as opaques with exact bytes: invariants 8 and 9 hold."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.surface.markdown.ast :as ast]
            [kimun.surface.markdown.cst :as cst]
            [kimun.surface.markdown.render :as render]))

(def h host/host)
(def fm "---\nid: t1\ntags:\n- a\n- b\n---")
(def table "| a | b |\n| --- | --- |\n| 1 | x\\|y |")

(deftest frontmatter-is-one-opaque-block
  (let [s (str fm "\n\n# T\n\npara\n") a (ast/parse h s)]
    (is (= {:type :opaque :format "markdown/frontmatter" :blob fm} (first (:children a))))
    (is (= :heading (:type (second (:children a)))))
    (is (= s (cst/text (cst/parse-document s))) "invariant 8: the CST concatenates to the text")
    (is (= a (ast/parse h (render/render h a))) "invariant 9: parse(render(A)) == A")
    (is (= s (render/render h a)))))

(deftest frontmatter-only-and-non-frontmatter
  (testing "a document that is only frontmatter"
    (doseq [s [fm (str fm "\n")]]
      (is (= s (cst/text (cst/parse-document s))))
      (is (= [{:type :opaque :format "markdown/frontmatter" :blob fm}] (:children (ast/parse h s))))))
  (testing "a leading thematic break is not frontmatter"
    (let [a (ast/parse h "---\n\npara\n\n---\n")]
      (is (= [:thematic-break :paragraph :thematic-break] (mapv :type (:children a))))
      (is (= a (ast/parse h (render/render h a))))))
  (testing "an unclosed fence is not frontmatter"
    (is (not= :opaque (:type (first (:children (ast/parse h "---\nid: x\n"))))))))

(deftest tables-are-opaque-with-exact-bytes
  (let [s (str "# T\n\n" table "\n") a (ast/parse h s)]
    (is (= {:type :opaque :format "markdown/table" :blob table} (second (:children a))))
    (is (= a (ast/parse h (render/render h a))))))
