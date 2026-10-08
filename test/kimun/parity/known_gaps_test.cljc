(ns kimun.parity.known-gaps-test
  "Known parity gaps: promises sldb v1 held that kimun does not hold today, found
   while closing the parity books (docs/v2/tests/parity-gaps.edn lists each with
   the smallest fix seen). Every deftest asserts kimun's CURRENT behaviour under a
   `;; GAP:` comment, so the suite stays green and the gap stays visible: the day
   a gap is fixed its deftest turns red and must be rewritten as the v1 promise
   and moved next to the ports of its v1 file."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.surface.models.descriptor :as descriptor]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run (vec argv) env cwd)))

;; ---------------------------------------------------------------- field-add of an optional field
(defn- file [p n] (str (fs/path p n)))

;; v1: store/test_optional_field_hash_neutral.py::test_model_extension_is_neutral_for_document_that_does_not_use_field
;; (the `models field-add` form of the promise; the `models define` form is ported in
;; kimun.parity.optional-field-hash-test)
;; GAP: v1 promised adding an optional field leaves a document that does not use it unchanged;
;; `models field-add NAME --field F --type string` appends a trailing `## F` section with an
;; optrev slot to the template, and an existing document whose body is the last slot then
;; extracts with its body moved into the new field (`body ""`, `summary_en "World body"`).
(deftest field-add-of-an-optional-field-rebinds-the-last-slot-of-existing-documents
  (let [p (str (ktu/temp-dir "kimun-gap-fa-"))]
    (run p "stores" "init" "--name" "p")
    (spit (file p "n.edn")
          (pr-str {:model/name "Note" :model/version 1 :base []
                   :fields [{:name "title" :type :string :required true :description "t"}
                            {:name "body" :type :string :required true :description "b"}]
                   :template "---\ntitle: ⸢rev•title⸥\n---\n\n# ⸢render•title⸥\n\n⸢rev•body⸥"}))
    (is (true? (:ok (run p "models" "define" "-f" "n.edn"))))
    (is (true? (:ok (run p "docs" "create" "Note" "hello" "--set" "title=Hello" "--set" "body=World body"))))
    (is (true? (:ok (run p "models" "field-add" "Note" "--field" "summary_en" "--type" "string"))))
    (is (= {"title" "Hello" "body" "" "summary_en" "World body"}
           (get-in (run p "docs" "show" "hello") [:data :payload])))))

;; ---------------------------------------------------------------- CLI: docs update of an untracked document
(def ticket (str (fs/absolutize "test/fixtures/models/ticket.edn")))


;; ---------------------------------------------------------------- engine: field descriptions
;; v1: test_standalone.py::test_structured_nl_doc_requires_field_descriptions
;; GAP: v1 promised a model whose field has no (or an empty) description is rejected ("fields must define a non-empty description: title"); kimun accepts it
(deftest descriptor-accepts-a-field-without-description
  (let [base {:model/name "MissingDescriptionDoc" :model/version 1 :base []
              :template "# ⸢rev•title⸥"}
        no-description (assoc base :fields [{:name "title" :type :string :required true}])
        empty-description (assoc base :fields [{:name "title" :type :string :required true :description ""}])]
    (is (= no-description (descriptor/validate! no-description)))
    (is (= empty-description (descriptor/validate! empty-description)))))
