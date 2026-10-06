(ns kimun.parity.marker-invariants-test
  "Ports of sldb v1 tests/test_marker_invariants.py: the recipe compiler's
   invariants on `rev`/`optrev` markers (one canonical `rev` per field, an
   `optrev` may stand alone or repeat)."
  (:require [clojure.test :refer [deftest is]]
            [kimun.surface.models.recipe :as recipe]))

(defn- names-of [rs] (mapv (comp (partial mapv :name) recipe/markers-of) rs))

;; v1: test_marker_invariants.py::test_duplicate_rev_markers_fail
(deftest duplicate-rev-markers-fail
  (let [e (try (recipe/recipes "⸢rev•field1⸥ and ⸢rev•field1⸥") nil
               (catch clojure.lang.ExceptionInfo e e))]
    (is (some? e))
    (is (= :models/invalid (:type (ex-data e))))
    (is (re-find #"Multiple canonical 'rev' markers found for field 'field1'" (ex-message e)))))

;; v1: test_marker_invariants.py::test_standalone_optrev_passes
(deftest standalone-optrev-passes
  (is (= [["field1"]] (names-of (recipe/recipes "⸢optrev•field1⸥")))))

;; v1: test_marker_invariants.py::test_valid_rev_and_optrev_pass
(deftest valid-rev-and-optrev-pass
  (let [rs (recipe/recipes "⸢rev•field1⸥ and ⸢optrev•field1⸥")]
    (is (= 1 (count rs)) "combined in one text block")
    (is (= 2 (count (recipe/markers-of (first rs)))))))

;; v1: test_marker_invariants.py::test_multiple_optrev_with_one_rev_pass
(deftest multiple-optrev-with-one-rev-pass
  (is (= 1 (count (recipe/recipes "⸢rev•field1⸥ then ⸢optrev•field1⸥ then ⸢optrev•field1⸥")))))
