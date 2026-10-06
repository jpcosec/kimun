(ns kimun.parity.engine-misc-test
  "Ports of sldb v1 tests/test_validation_semantics.py: the two validation
   modes (`render-validity` by default, `strict-roundtrip`) over a model that
   mirrors a `rev` field with an `optrev` marker. The recorded corpus does not
   carry validation results, so these are real ports, not oracles."
  (:require [clojure.test :refer [deftest is]]
            [kimun.surface.models.validate :as validate]))

(def multi-marker-doc
  "v1's MultiMarkerDoc."
  {:model/name "MultiMarkerDoc" :model/version 1 :base []
   :fields [{:name "title" :type :string :required true :description "Title"}
            {:name "body" :type :string :required true :description "Body"}]
   :template "# ⸢rev•title⸥\nMirror: ⸢optrev•title⸥\nBody: ⸢rev•body⸥"})

(def payload {"title" "Hello" "body" "World"})

;; v1: test_validation_semantics.py::test_render_validity_allows_optrev_identity_mismatch
(deftest render-validity-is-the-default-mode
  (let [r (validate/validate multi-marker-doc {:data payload})]
    (is (true? (:ok r)) (pr-str (:errors r)))
    (is (= :render-validity (:mode r)))
    (is (= "Mirror: Hello" (re-find #"Mirror: \w+" (:rendered r))))))

;; v1: test_validation_semantics.py::test_strict_roundtrip_enforces_all_markers
(deftest strict-roundtrip-enforces-all-markers
  (let [r (validate/validate multi-marker-doc {:data payload} :strict-roundtrip)]
    (is (true? (:ok r)) (pr-str (:errors r)))
    (is (= :strict-roundtrip (:mode r)))
    (is (= payload (:extracted r)))))
