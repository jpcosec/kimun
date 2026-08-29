(ns sldb.kernel.test-util
  "Minimal `defspec` over test.check's quick-check, because Babashka bundles
   clojure.test.check.{generators,properties} but not clojure-test."
  (:require [clojure.test :as t]
            [clojure.test.check :as tc]))

(defmacro defspec
  "Defines a clojure.test test that runs `prop` with `num-tests` trials and
   fails with the shrunk counter-example when it does not pass."
  [name num-tests prop]
  `(t/deftest ~name
     (let [r# (tc/quick-check ~num-tests ~prop)]
       (t/is (:pass? r#) (pr-str (select-keys r# [:num-tests :seed :fail :shrunk]))))))
