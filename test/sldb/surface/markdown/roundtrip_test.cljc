(ns sldb.surface.markdown.roundtrip-test
  (:require [clojure.test :refer [deftest is testing]]
            [sldb.kernel.test-util :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.edn :as edn]
            [sldb.host.default :as host]
            [sldb.surface.markdown.ast :as ast]
            [sldb.surface.markdown.render :as render]
            [sldb.kernel.generators :as g]
            #?(:clj [clojure.java.io :as io])))

(def h host/host)
(defn- parse [s] (ast/parse h s))
(defn- render [a] (render/render h a))
(defn- read-fixture [f] #?(:clj (edn/read-string (slurp (io/file f)))))
(defn- slurp-fixture [f] #?(:clj (slurp (io/file f))))

(defspec parse-render-identity 150
  (prop/for-all [a g/gen-ast]
    (= a (parse (render a)))))

(defspec render-is-idempotent 100
  (prop/for-all [a g/gen-ast]
    (let [r (render a)] (= r (render (parse r))))))

(deftest escapes-round-trip
  (doseq [text ["a*b" "a\\b" "[x]" "`t`" "a<b" "a|b" "a_b" "_x" "#" "x #" "- x" "1. x" "---" "***" "> q" "```" "~~~" "<x" "+ x" "10. y"]]
    (testing text
      (let [a {:type :document :attrs {} :children [{:type :paragraph :attrs {:marks []} :text text}]}]
        (is (= a (parse (render a))) (pr-str (render a))))
      (let [a {:type :document :attrs {} :children [{:type :heading :attrs {:level 2 :marks []} :text text}]}]
        (is (= a (parse (render a))) (pr-str (render a)))))))

(deftest canonical-spellings
  (is (= "# T\n\npara\n" (render (parse "#   T   #\n\n\npara\n\n"))))
  (is (= "- a\n- b\n" (render (parse "* a\n* b"))))
  (is (= "3. a\n4. b\n" (render (parse "3. a\n7. b"))))
  (is (= "a b\n" (render (parse "a\nb"))) "soft breaks become spaces")
  (is (= "> a\n>\n> b\n" (render (parse ">a\n>\n>b"))))
  (is (= "```\n``\n```\n" (render (parse "````\n``\n````"))))
  (is (= "" (render (parse ""))))
  (is (= "- a\n  \n  b\n- c\n" (render (parse "- a\n\n  b\n- c"))) "blank lines inside an item are indented"))

(deftest golden-profile
  (let [md (slurp-fixture "test/fixtures/markdown/profile.md")
        expected (read-fixture "test/fixtures/markdown/profile.ast.edn")
        a (parse md)]
    (is (= expected a) "profile.md parses to the frozen AST")
    (is (= md (render a)) "and renders back byte-for-byte (profile.md is canonical)")))
