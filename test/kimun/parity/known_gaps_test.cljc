(ns kimun.parity.known-gaps-test
  "Known parity gaps: promises sldb v1 held that kimun does not hold today, found
   while closing the parity books (docs/v2/tests/parity-gaps.edn lists each with
   the smallest fix seen). Every deftest asserts kimun's CURRENT behaviour under a
   `;; GAP:` comment, so the suite stays green and the gap stays visible: the day
   a gap is fixed its deftest turns red and must be rewritten as the v1 promise
   and moved next to the ports of its v1 file."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run (vec argv) env cwd)))

