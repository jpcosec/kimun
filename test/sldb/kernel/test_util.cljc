(ns sldb.kernel.test-util
  "Minimal `defspec` over test.check's quick-check, because Babashka bundles
   clojure.test.check.{generators,properties} but not clojure-test."
  (:require [clojure.walk]
            [clojure.test :as t]
            [clojure.test.check :as tc]))

(defn- env-long [k] #?(:clj (some-> (System/getenv k) parse-long) :default nil))

(defn run-property
  "quick-check of `prop` with `num-tests` trials. Two environment variables make
   a reported failure reproducible and a suite stressable without editing tests:
   SLDB_PROP_SEED (run with that seed: the :seed a failing defspec prints) and
   SLDB_PROP_SCALE (multiply the number of trials). Trial sizes cycle, so a seed
   reproduces a failure only with at least the trials of the reported run."
  [num-tests prop]
  (let [n (* num-tests (or (env-long "SLDB_PROP_SCALE") 1))]
    (if-some [seed (env-long "SLDB_PROP_SEED")]
      (tc/quick-check n prop :seed seed)
      (tc/quick-check n prop))))

(defmacro defspec
  "Defines a clojure.test test that runs `prop` with `num-tests` trials and
   fails with the shrunk counter-example when it does not pass (see
   `run-property` for re-running a reported seed)."
  [name num-tests prop]
  `(t/deftest ~name
     (let [r# (run-property ~num-tests ~prop)]
       (t/is (:pass? r#) (pr-str (select-keys r# [:num-tests :seed :fail :shrunk]))))))

(def tx-001-revision-ids
  "Revision id of test/fixtures/tx-001.edn under each store format. The fixtures
   pin the format-1 id (as :expected and as the :base of later plans)."
  {1 "93fd0bca29b4f94b68464a6952b42b1464055a26dd3dec4c3d37173c8a985053"
   2 "8ee693939d457251112165da68534a4d4508d1cbe17a7efe4b46f4483ed05992"})

(defn format-2-fixture
  "A fixture with every reference to the format-1 revision id of tx-001
   rewritten to its format-2 id (plans keep everything else)."
  [fx]
  (clojure.walk/postwalk-replace {(tx-001-revision-ids 1) (tx-001-revision-ids 2)} fx))
