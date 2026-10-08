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
