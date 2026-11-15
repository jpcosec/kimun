(ns kimun.surface.models.selector-cst-test
  "C3-C5 of docs/v2/16 §3.3 on the tree-sitter CST (M1i). The tests that read
   the CST need the pod (`KIMUN_POD`); without it they print a `SKIPPED` line
   and test 4 (the typed error) still runs."
  (:require [clojure.test :refer [deftest is testing]]
            [kimun.host.treesitter :as ts]
            [kimun.surface.models.selector :as sel]))

(defmacro ^:private with-pod
  "Runs `body` only when the pod is available; otherwise says it skipped."
  [name & body]
  `(if (ts/available?)
     (do ~@body)
     (println (str "SKIPPED (no tree-sitter pod): " ~name))))

(def ^:private notes-step
  {:key "notes" :want :find :outer-type "heading" :where {:outer-tag "h2" :regex "^\\QNotes\\E$"}})
(def ^:private items-step
  {:key "items" :outer-type "list" :preserve-nesting true
   :where {:outer-tag "h2" :regex "^Items$"}})
(def ^:private matrix-step
  {:key "matrix" :want :table :outer-type "pipe_table"
   :where {:outer-tag "h2" :regex "^Data$"}})

(def ^:private doc-a
  (str "# T\n\n## Items\n\n- a\n  - a1\n    - a11\n- b\n\n## Data\n\n"
       "| k | v |\n|---|--:|\n| x | 1 |\n| y | 2 \\| 3 |\n\n## Notes\n\nfirst note\n\nsecond line\n"))

(def ^:private doc-b
  "The same sections, Notes first and Data last, with a section between."
  (str "# T\n\n## Notes\n\nfirst note\n\nsecond line\n\n## Items\n\n- a\n  - a1\n    - a11\n- b\n\n"
       "## Other\n\nunrelated\n\n## Data\n\n| k | v |\n|---|--:|\n| x | 1 |\n| y | 2 \\| 3 |\n"))

(deftest a-two-level-list-keeps-its-hierarchy
  (with-pod "a-two-level-list-keeps-its-hierarchy"
    (is (= [{:text "a" :depth 0} {:text "a1" :depth 1} {:text "a11" :depth 2} {:text "b" :depth 0}]
           (get (sel/extract-selector [items-step] doc-a) "items")))
    (testing "the block view the other steps use does not keep the nesting"
      (is (= ["a\n- a1\n- a11" "b"]
             (get (sel/extract-selector [{:outer-index 0 :outer-type "heading" :outer-tag "h2" :handler :text
                                          :anchor true :props [] :regex "^\\QItems\\E$"}
                                         {:outer-index 1 :outer-type "bullet_list" :outer-tag "ul" :handler :list :name "items"}]
                                        doc-a) "items"))))))

(deftest a-section-is-found-wherever-it-sits
  (with-pod "a-section-is-found-wherever-it-sits"
    (is (= "first note\n\nsecond line" (get (sel/extract-selector [notes-step] doc-a) "notes")))
    (is (= (get (sel/extract-selector [notes-step] doc-a) "notes")
           (get (sel/extract-selector [notes-step] doc-b) "notes")))
    (is (= {} (sel/extract-selector [notes-step] "# T\n\n## Items\n\n- a\n"))
        "an absent section leaves the field out")))

(deftest a-pipe-table-gives-rows-and-cells
  (with-pod "a-pipe-table-gives-rows-and-cells"
    (let [rows [["k" "v"] ["x" "1"] ["y" "2 | 3"]]]
      (is (= rows (get (sel/extract-selector [matrix-step] doc-a) "matrix")))
      (is (= rows (get (sel/extract-selector [matrix-step] doc-b) "matrix"))))))

(deftest a-cst-step-without-the-pod-fails-typed
  (with-redefs [ts/available? (constantly false)]
    (doseq [step [notes-step items-step matrix-step]]
      (is (= :docs/parser-unavailable
             (try (sel/extract-selector [step] doc-a) :returned
                  (catch clojure.lang.ExceptionInfo e (:type (ex-data e)))))
          (str (:key step))))))

(def ^:private c1-c2-steps
  [{:outer-index 0 :outer-type "heading" :outer-tag "h1" :handler :text :props ["title"]
    :props-info [{:kind "rev" :traits [] :name "title"}] :regex "^(.*?)$"}
   {:outer-index 1 :outer-type "heading" :outer-tag "h2" :handler :text :anchor true :props []
    :regex "^\\QNotes\\E$"}
   {:outer-index 2 :outer-type "paragraph" :outer-tag "p" :handler :text :props ["notes_first"]
    :props-info [{:kind "rev" :traits [] :name "notes_first"}] :regex "^(.*?)$"}])

(deftest the-same-steps-agree-on-both-substrates
  (with-pod "the-same-steps-agree-on-both-substrates"
    (let [plain (sel/extract-selector c1-c2-steps doc-b)
          mixed (sel/extract-selector (conj c1-c2-steps matrix-step) doc-b)
          no-pod (with-redefs [ts/available? (constantly false)] (sel/extract-selector c1-c2-steps doc-b))]
      (is (= {"title" "T" "notes_first" "first note"} plain))
      (is (= plain no-pod) "block view served by the pod or by the in-process parser")
      (is (= (assoc plain "matrix" [["k" "v"] ["x" "1"] ["y" "2 | 3"]]) mixed)
          "C3-C5 steps add their fields and leave the others unchanged"))))
