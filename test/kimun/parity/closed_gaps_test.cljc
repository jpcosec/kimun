;; ports of v1 promises that were parity gaps and are now held
(ns kimun.parity.closed-gaps-test
  "Promises of sldb v1 that the closing audit found broken in kimun
   (docs/v2/tests/parity-gaps.edn) and that were fixed afterwards: each deftest
   is the v1 promise itself, moved here from `known_gaps_test.cljc` when its
   gap closed."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [kimun.cli.main :as main]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run (vec argv) env cwd)))

(defn- project []
  (let [p (str (fs/create-temp-dir {:prefix "kimun-closed-gap-"}))]
    (run p "stores" "init" "--name" "p")
    (spit (str (fs/path p "note.edn"))
          (pr-str {:model/name "Note" :model/version 1 :base []
                   :fields [{:name "title" :type :string :required true :description "Title."}]
                   :template "# ⸢rev•title⸥\n"}))
    (is (true? (:ok (run p "models" "define" "-f" "note.edn"))))
    p))

;; v1: api/test_api_documents.py::test_cli_save_payload_still_exits_with_the_message
(deftest update-payload-of-an-untracked-document-names-it
  (let [p (project)]
    (doseq [argv [["docs" "update" "ghost" "--set" "title=x"]
                  ["docs" "update" "--name" "ghost" "--set" "title=x"]]]
      (let [e (apply run p argv)]
        (is (false? (:ok e)) (pr-str argv))
        (is (= [1 "docs/missing"] [(:exit e) (get-in e [:error :type])]) (pr-str argv))
        (is (str/includes? (get-in e [:error :message]) "ghost") "the message names the document")
        (is (= "ghost" (get-in e [:error :data :document])))))))
