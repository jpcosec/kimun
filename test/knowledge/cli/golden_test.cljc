(ns knowledge.cli.golden-test
  "Frozen CLI envelopes (test/fixtures/cli/s0/*.json): the JSON rendering of
   `version`, `stores init` and `stores check`, with volatile fields
   normalized, must match the fixtures generated once from the implementation
   (docs/v2/03 §5)."
  (:require [clojure.test :refer [deftest is]]
            [clojure.walk :as walk]
            [babashka.fs :as fs]
            [cheshire.core :as json]
            [knowledge.cli.main :as main]
            [knowledge.cli.out :as out]))

(def env {"USER" "golden"})

(def volatile-keys
  "Keys whose values vary per machine, checkout or run."
  #{:path :bb :git :actor :capabilities :head :revision})

(defn normalize
  "Replaces every value under a volatile key with \"<norm>\", recursively."
  [v]
  (walk/prewalk (fn [x]
                  (if (map? x)
                    (into {} (map (fn [[k v]] [k (if (contains? volatile-keys k) "<norm>" v)])) x)
                    x))
                v))

(defn- rendered [argv cwd]
  (let [{:keys [envelope]} (main/run argv env cwd)]
    (normalize (json/parse-string (out/emit envelope "json") true))))

(defn- fixture [name]
  (json/parse-string (slurp (str "test/fixtures/cli/s0/" name ".json")) true))

(deftest golden-version
  (is (= (fixture "version") (rendered ["--version"] (System/getProperty "user.dir")))))

(deftest golden-init-and-check
  (let [proj (str (fs/create-temp-dir {:prefix "knowledge-golden-"}))
        init (rendered ["stores" "init" "--name" "golden"] proj)
        check (rendered ["stores" "check"] proj)]
    (is (= (fixture "init") init))
    (is (= (fixture "check") check))))
