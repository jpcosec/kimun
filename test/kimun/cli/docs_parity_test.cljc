(ns kimun.cli.docs-parity-test
  "Differential check on real data: documents of sldb v1's own store, with the
   payload v1 extracts from each (test/fixtures/parity/docs, recorded by
   scripts/parity-docs/record_docs.py), tracked into a kimun store through the
   CLI. Every payload must equal v1's; a case may differ only through an entry
   in EXPECTED-DIFFS.edn with a written reason."
  (:require [kimun.test-util :as ktu] [clojure.test :refer [deftest is]]
            [clojure.edn :as edn]
            [babashka.fs :as fs]
            [cheshire.core :as json]
            [kimun.cli.main :as main]))

(def root "test/fixtures/parity/docs")
(defn- run [cwd & argv] (:envelope (main/run argv {"USER" "parity"} cwd)))

(deftest real-documents-extract-as-in-v1
  (let [p (str (ktu/temp-dir "kimun-docs-parity-"))
        expected-diffs (let [f (fs/path root "EXPECTED-DIFFS.edn")] (if (fs/exists? f) (edn/read-string (slurp (str f))) {}))
        cases (sort (map #(str (fs/file-name %)) (filter fs/directory? (fs/list-dir root))))
        _ (run p "stores" "init" "--name" "parity")
        results (doall
                 (for [c cases
                       :let [dir (str (fs/absolutize (fs/path root c)))
                             model (:model/name (edn/read-string (slurp (str (fs/path dir "descriptor.edn")))))
                             d (run p "models" "define" "-f" (str (fs/path dir "descriptor.edn")))
                             target (str (fs/path p (str c ".md")))
                             _ (fs/copy (fs/path dir "doc.md") target)
                             e (when (:ok d) (run p "docs" "track" model target "--name" c "--force"))
                             want (json/parse-string (slurp (str (fs/path dir "payload.json"))))
                             got (some-> (get-in e [:data :payload]) json/generate-string json/parse-string)]]
                   {:case c :ok? (= want got)
                    :why (cond (not (:ok d)) (get-in d [:error :message]) (not (:ok e)) (get-in e [:error :message])
                               :else (str "differs in " (vec (filter #(not= (get want %) (get got %)) (distinct (concat (keys want) (keys got)))))))}))
        bad (remove :ok? results)
        unexpected (remove #(contains? expected-diffs (:case %)) bad)
        stale (filter #(contains? expected-diffs (:case %)) (filter :ok? results))]
    (println "docs parity:" {:cases (count cases) :equal (count (filter :ok? results)) :expected-diffs (count bad)})
    (is (pos? (count cases)))
    (is (empty? unexpected) (pr-str (mapv (juxt :case :why) unexpected)))
    (is (empty? stale) (str "listed in EXPECTED-DIFFS.edn but equal now: " (mapv :case stale)))
    (is (true? (:ok (run p "stores" "verify"))))))
