(ns kimun.surface.propositions-test
  "Asking propositions (docs/v2/drafts/diseno-evaluar.md §9, cut 3b): dotted hierarchy of
   terms, `about` with and without `exact`, both sides, proposition on proposition at one
   level, `about-path` by anchor and by pattern (several classes per file) and `members`.
   Relation names are arbitrary on purpose: nothing may depend on them."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.surface.propositions :as p]))

(defn- prop [id r a b] {:id id :r r :a a :b b :context "c"})
(defn- t [s] {:term s})

(def props
  [(prop "p1" "zork" (t "dash") (t "UI.view.dashboard"))
   (prop "p2" "qux/blip" (t "UI.view") (t "dash"))
   (prop "p3" "zork" (t "x") (t "Lib"))
   (prop "p4" "wug" {:proposition "p1"} (t "owner"))
   (prop "p5" "wug" (t "y") {:proposition "p4"})
   (prop "p6" "zork" {:files "src/ui/**/*.py"} (t "UI.component"))
   (prop "p7" "frob" {:files "src/**/*.py"} (t "Python"))
   (prop "p8" "zork" (t "doc") {:anchor {:path "docs/spec.md" :locator [["Spec"]]}})
   (prop "p9" "zork" {:files "docs/*.md"} (t "Doc"))
   (prop "p10" "zork" (t "same") (t "same"))])

(deftest dotted-hierarchy-and-implicit-ancestors
  (is (= ["UI.view" "UI"] (p/ancestors-of "UI.view.dashboard")))
  (is (= [] (p/ancestors-of "dash")))
  (is (= [] (p/ancestors-of "a..b")))
  (let [by (into {} (map (juxt :term identity)) (p/terms props))]
    (is (= {:count 1 :implicit false :ancestors ["UI.view" "UI"]} (select-keys (by "UI.view.dashboard") [:count :implicit :ancestors])))
    (is (= {:count 1 :implicit false} (select-keys (by "UI.view") [:count :implicit]))
        "an ancestor somebody wrote is explicit and counts only its own propositions")
    (is (= {:count 0 :implicit true :ancestors []} (select-keys (by "UI") [:count :implicit :ancestors]))
        "UI was never written: it appears, marked implicit")
    (is (= 2 (:count (by "dash"))) "named in p1 and p2")
    (is (= 1 (:count (by "same"))) "two sides of one proposition are one naming")
    (is (nil? (by "p1")) "propositions and files are not terms")))

(deftest about-with-and-without-exact
  (let [rows (:results (p/about props "UI.view"))
        direct (filter #(= :term (:via %)) rows)]
    (is (= [["p1" :b "UI.view.dashboard" (t "dash")] ["p2" :a "UI.view" (t "dash")]]
           (map (juxt (comp :id :proposition) :side :matched :other) direct))
        "by default the more specific term is included; side and the other end are told")
    (is (= [["zork" "p1"] ["qux/blip" "p2"]] (map (juxt (comp :r :proposition) (comp :id :proposition)) direct))
        "relations come back as written, whatever their name"))
  (is (= ["p2"] (map (comp :id :proposition) (:results (p/about props "UI.view" {:exact true}))))
      "exact leaves out UI.view.dashboard")
  (is (= [] (:results (p/about props "UI.vi"))) "a prefix of the text is not an ancestor")
  (testing "a term on both sides gives a row per side"
    (is (= [:a :b] (map :side (:results (p/about props "same")))))))

(deftest proposition-on-proposition-one-level
  (let [r (p/about props "UI.view")
        via (filter #(= :proposition (:via %)) (:results r))]
    (is (= 1 (:levels r)))
    (is (= [["p4" :a "p1" (t "owner")]] (map (juxt (comp :id :proposition) :side :about :other) via))
        "p4 talks about p1, which is in the result")
    (is (not-any? #(= "p5" (get-in % [:proposition :id])) (:results r))
        "p5 talks about p4: a second level, not followed"))
  (testing "a proposition already in the result by its terms is not repeated"
    (let [ps (conj props (prop "p11" "wug" {:proposition "p2"} (t "dash")))
          ids (map (comp :id :proposition) (:results (p/about ps "dash")))]
      (is (= ids (distinct ids))))))

(deftest about-path-by-anchor-and-by-pattern
  (let [[r1 r2 :as rows] (:results (p/about-path props "docs/spec.md"))]
    (is (= 2 (count rows)))
    (is (= ["p8" :anchor :b (t "doc") [["Spec"]]] [(-> r1 :proposition :id) (:via r1) (:side r1) (:other r1) (:locator r1)])
        "the anchor on that path: the anchor sits on the b side, the other end is a")
    (is (= ["p9" :files :a (t "Doc") "docs/*.md"] [(-> r2 :proposition :id) (:via r2) (:side r2) (:other r2) (:pattern r2)])
        "the pattern docs/*.md covers it: the other end is b"))
  (testing "two classes arrive by two different patterns to one file"
    (let [rows (:results (p/about-path props "src/ui/a/b.py"))]
      (is (= [["p6" "src/ui/**/*.py" (t "UI.component")] ["p7" "src/**/*.py" (t "Python")]]
             (map (juxt (comp :id :proposition) :pattern :other) rows)))))
  (testing "* stays inside a segment, ** crosses them"
    (is (empty? (:results (p/about-path props "docs/sub/spec.md"))) "docs/*.md does not reach docs/sub/")
    (is (= ["p7"] (map (comp :id :proposition) (:results (p/about-path props "src/z.py")))) "src/**/*.py reaches src/z.py but src/ui/**/ does not")
    (is (= ["p8" "p9"] (map (comp :id :proposition) (:results (p/about-path props "./docs/spec.md"))))
        "a leading ./ is ignored")
    (is (empty? (:results (p/about-path props "docs/other.txt"))))))

(deftest members-expand-when-asked
  (let [paths ["src/ui/a.py" "src/ui/x/b.py" "src/c.py" "README.md" "src/ui/a.py" "src/ui/d.txt"]]
    (is (= ["src/ui/a.py" "src/ui/x/b.py"] (p/members "src/ui/**/*.py" paths)))
    (is (= ["src/ui/a.py"] (p/members "src/ui/*.py" paths)) "* does not cross a slash")
    (is (= [] (p/members "nothing/**" paths)))))
