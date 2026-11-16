(ns kimun.cli.model-carry-test
  "K1d: a rebuild keeps the `models` tree and the version (and contract) of
   every model whose contract did not move. The hash of a descriptor is the
   ADDRESS of that value, not the identity of a unit; K1d does not decide
   whether a model is a unit of the graph (open for `M1`). No id is minted for
   a model and its version never goes back (docs/v2/14 §21, 12 §1 K1)."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [babashka.process :as process]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.models.store :as mstore]
            [kimun.test-util :as ktu]))

(def ^:dynamic *repo* nil)
(def env {"USER" "tester"})

(defn- model-text
  ([fields] (model-text fields "---\nid: ⸢rev•name⸥\n---\n\n# Title\n\n## Body\n\n⸢rev•body⸥\n"))
  ([fields template]
   (pr-str {:base [] :model/name "TestDoc" :model/version 1
            :fields (vec (for [f fields]
                           (merge {:type :string :required true :description (str f ".")} f)))
            :semantics {"type" ["test"] "workspace" ["test"]}
            :template template})))

(def base-fields [{:name "name"} {:name "body"}])

(def include-text
  (str "{:include/version 1\n"
       " :store {:path \".kimun\" :git :ignore}\n"
       " :models [\"models/*.edn\"]\n"
       " :documents [{:glob \"docs/**/*.md\" :model \"TestDoc\"}]\n"
       "}\n"))

(defn- git! [d & args]
  (let [r (apply process/shell {:out :string :err :string :continue true :dir (str d)} "git" args)]
    (when-not (zero? (:exit r))
      (throw (ex-info (str "git " (str/join " " args) " failed: " (:err r)) {})))
    r))

(defn- write! [d rel text]
  (let [f (fs/path d rel)] (some-> (fs/parent f) fs/create-dirs) (spit (str f) text)))

(defn- run-include [& argv] (:envelope (main/run (vec argv) env (str *repo*))))
(defn- run-cmd [& argv] (:envelope (main/run (vec argv) env (str *repo*))))
(defn- store [] (:store (cli-store/open! (str *repo* "/.kimun"))))
(defn- models-tree-id [] (first (mstore/models-tree (store))))
(defn- model-map []
  (into {} (map (fn [m] [(:name m) [(:version m) (:id m)]]) (mstore/models (store)))))
(defn- contract-of [doc-name]
  (let [d (tdocs/find-document (store) doc-name)]
    (select-keys d [:contract-id :model-id :model-version])))
(defn- models-identities [] (get-in (run-include "include" "build" "--rebuild") [:data :identities :models]))

(use-fixtures :each
  (fn [f]
    (let [d (str (ktu/temp-dir "kimun-model-carry-"))]
      (try
        (git! d "init" "-q") (git! d "config" "user.email" "t@e") (git! d "config" "user.name" "t")
        (write! d "models/TestDoc.edn" (model-text base-fields))
        (write! d "docs/a.md" "---\nid: N\n---\n\n# Title\n\n## Body\n\nB\n")
        (write! d "kimun.include.edn" include-text)
        (git! d "add" "-A") (git! d "commit" "-qm" "init")
        (binding [*repo* d] (f))
        (finally (fs/delete-tree d))))))

(deftest one-model-declared-twice-counts-once
  ;; the same descriptor in two files is ONE model: the second define comes
  ;; back :unchanged inside the same build, and it must not inflate a counter
  (let [dup (fs/path *repo* "models" "TestDoc-copia.edn")]
    (fs/copy (fs/path *repo* "models" "TestDoc.edn") dup)
    (is (fs/exists? dup) "there are two files")
    (is (= 2 (count (fs/glob (fs/path *repo* "models") "*.edn"))) "two descriptors")
    (is (= 1 (count (distinct (map #(-> (slurp (str %)) edn/read-string :model/name)
                                   (fs/glob (fs/path *repo* "models") "*.edn")))))
        "and one model name")
    (let [b (run-include "include" "build")
          ids (get-in b [:data :identities :models])]
      (is (true? (:ok b)) (pr-str b))
      (is (= {:tree-carried false :kept 0 :advanced 0 :new 1} ids)
          "the new model is counted once, as new")
      (is (= 1 (+ (:kept ids) (:advanced ids) (:new ids))) "one name, one count")
      (is (= 1 (count (mstore/models (store)))) "the store holds one model"))
    (let [b (run-include "include" "build" "--rebuild")
          ids (get-in b [:data :identities :models])]
      (is (true? (:ok b)) (pr-str b))
      (is (= {:tree-carried true :kept 1 :advanced 0 :new 0} ids)
          "the conserved model is counted once, as kept")
      (is (= 1 (+ (:kept ids) (:advanced ids) (:new ids))) "one name, one count"))))

(deftest a-rebuild-keeps-the-tree-and-the-versions-when-nothing-changed
  (is (true? (:ok (run-include "include" "build"))))
  (let [t0 (models-tree-id) m0 (model-map) c0 (contract-of "a")]
    (is (string? t0) "the models tree exists")
    (is (= ["TestDoc"] (keys m0)))
    (is (= 1 (first (get m0 "TestDoc"))))
    (is (every? some? (vals c0)) (pr-str c0))
    (let [b (run-include "include" "build" "--rebuild")
          ids (get-in b [:data :identities])]
      (is (true? (:ok b)) (pr-str b))
      (is (= t0 (models-tree-id)) "the models tree keeps its id")
      (is (= m0 (model-map)) "and every model keeps its version and id")
      (is (= {:tree-carried true :kept 1 :advanced 0 :new 0} (:models ids)))
      (is (= c0 (contract-of "a")) "the document is still under the same contract")
      (is (= [] (:models-not-carried ids)))
      (is (not-any? #(= "include/identities-not-carried" (:type %)) (:warnings b))
          "nothing to declare when everything was carried")))
  (testing "a model the declaration no longer defines is declared, not dropped silently"
    (write! *repo* "models/OtherDoc.edn"
            (pr-str {:base [] :model/name "OtherDoc" :model/version 1
                     :fields [{:type :string :required true :description "Name." :name "name"}]
                     :semantics {"type" ["test"]} :template "# ⸢rev•name⸥
"}))
    (is (true? (:ok (run-include "include" "build" "--rebuild"))) "the second model enters")
    (is (= #{"TestDoc" "OtherDoc"} (set (keys (model-map)))))
    (fs/delete (fs/path *repo* "models" "OtherDoc.edn"))
    (let [b (run-include "include" "build" "--rebuild")
          ids (get-in b [:data :identities])
          w (first (filter #(= "include/identities-not-carried" (:type %)) (:warnings b)))]
      (is (true? (:ok b)) (pr-str b))
      (is (= ["OtherDoc"] (:models-not-carried ids)) "it is named, not forgotten")
      (is (= ["OtherDoc"] (:models-not-carried w)) "and the existing warning carries it")
      (is (= 1 (get-in ids [:models :kept])) "the declared model still keeps its version"))))

(deftest a-version-does-not-go-back
  (is (true? (:ok (run-include "include" "build"))))
  ;; the model file moves and the STORE defines version 2 (in place)
  (write! *repo* "models/TestDoc.edn"
          (model-text (conj base-fields {:name "note" :required false})))
  (is (true? (:ok (run-cmd "models" "define" "-f" "models/TestDoc.edn"))))
  (let [v2 (get (model-map) "TestDoc")]
    (is (= 2 (first v2)) "the store holds version 2")
    (let [b (run-include "include" "build" "--rebuild")
          ids (get-in b [:data :identities])]
      (is (true? (:ok b)) (pr-str b))
      (is (= v2 (get (model-map) "TestDoc")) "the rebuild keeps version 2 and its id")
      (is (= {:tree-carried true :kept 1 :advanced 0 :new 0} (:models ids))))))

(deftest a-moved-contract-advances-the-version
  (is (true? (:ok (run-include "include" "build"))))
  (let [v1 (get (model-map) "TestDoc")]
    ;; the declaration's model file moves; the store is NOT redefined
    (write! *repo* "models/TestDoc.edn"
            (model-text (conj base-fields {:name "note" :required false})))
    (let [b (run-include "include" "build" "--rebuild")
          ids (get-in b [:data :identities])
          v2 (get (model-map) "TestDoc")]
      (is (true? (:ok b)) (pr-str b))
      (is (= (inc (first v1)) (first v2)) "the version advances, never goes back")
      (is (not= v1 v2) "and the content-addressed id moves")
      (is (= {:tree-carried true :kept 0 :advanced 1 :new 0} (:models ids))))))

(deftest an-unreadable-old-store-falls-back-to-version-one-and-says-so
  (is (true? (:ok (run-include "include" "build"))))
  (spit (str *repo* "/.kimun/heads.edn") "{:not edn")
  (let [b (run-include "include" "build" "--rebuild")
        ids (get-in b [:data :identities])]
    (is (true? (:ok b)) (pr-str b))
    (is (false? (:available ids)))
    (is (= {:tree-carried false :kept 0 :advanced 0 :new 1} (:models ids)))
    (is (= [1] (mapv first (vals (model-map)))) "every model starts at version 1")
    (is (some #(= "include/identities-unavailable" (:type %)) (:warnings b)))))
