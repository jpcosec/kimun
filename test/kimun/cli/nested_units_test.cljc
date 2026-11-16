(ns kimun.cli.nested-units-test
  "K1h from the CLI: an item of a list field and a nested key of a structured
   field are units only when they are DECLARED (`docs mark --field F --index N`,
   `--path a.b`); nothing below the first level is minted on its own. They read
   with the states and candidates of K1e (`docs marks`), are re-anchored and
   closed by declaration, and are not part of any hash (docs/v2/14 §20–§21; the
   rules are the team's inferences)."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.host.default]
            [kimun.surface.markdown.ast :as ast]
            [kimun.test-util :as ktu]))

(def ^:dynamic *p* nil)
(def env {"USER" "tester"})

(defn- run-in [p & argv]
  (binding [ast/*parser* "native"]
    (:envelope (main/run (vec argv) env p))))
(defn- run [& argv] (apply run-in *p* argv))
(defn- file [& parts] (str (apply fs/path *p* parts)))

(def model
  (pr-str {:model/name "BagDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "meta" :type :yaml :required true :description "Metadata."}
                    {:name "items" :type :yaml :required false :description "Items."}]
           :template "# ⸢rev•title⸥\n\n## Metadata\n```yaml\n⸢rev,dict•meta⸥\n```\n\n* ⸢optrev,list•items⸥\n"}))

(defn- bag [items]
  (str "# Bag\n\n## Metadata\n\n```yaml\nowner:\n  name: ana\nversion: 1\n```\n\n"
       (str/join "\n" (map #(str "* " %) items)) "\n"))

(defn- write! [items] (spit (file "docs" "a.md") (bag items)))
(defn- update! [] (run "docs" "update" "a"))

(defn- setup! [p]
  (fs/create-dirs (str p "/docs"))
  (spit (str p "/bag.edn") model)
  (spit (str p "/docs/a.md") (bag ["uno" "dos" "tres"]))
  (spit (str p "/docs/g.md") "# G\n\nTexto.\n")
  (is (true? (:ok (run-in p "stores" "init" "--name" "p"))))
  (is (true? (:ok (run-in p "models" "define" "-f" "bag.edn"))))
  (is (true? (:ok (run-in p "docs" "track" "BagDoc" "docs/a.md" "--name" "a"))))
  (is (true? (:ok (run-in p "docs" "ingest" "docs/g.md")))))

(use-fixtures :each
  (fn [f]
    (let [p (str (ktu/temp-dir "kimun-nested-"))]
      (try (binding [*p* p] (setup! p) (f)) (finally (fs/delete-tree p))))))

(defn- head [] (:head (:store (cli-store/open! (file ".kimun")))))
(defn- marks [] (get-in (run "docs" "marks" "a") [:data :marks]))
(defn- mark-of [id] (first (filter #(= id (:id %)) (marks))))
(defn- item! [i & more] (apply run "docs" "mark" "a" "--field" "items" "--index" (str i) more))
(defn- id-of [r] (get-in r [:data :id]))
(defn- last-entry [] (first (get-in (run "journal" "show" "--limit" "1") [:data])))

(deftest an-item-and-a-nested-key-are-marked-by-declaration-only
  (let [units0 (into {} (map (juxt :field :id)) (:units (:data (run "docs" "show" "a"))))
        _ (is (= #{"title" "meta" "items"} (set (keys units0))) "the first-level units are minted as before")
        _ (is (empty? (marks)) "nothing below the first level is minted on its own")
        i1 (item! 1 "--label" "dos")
        n1 (run "docs" "mark" "a" "--field" "meta" "--path" "owner.name")
        id-i (id-of i1) id-n (id-of n1)]
    (is (true? (:ok i1)) (pr-str i1))
    (is (true? (:ok n1)) (pr-str n1))
    (is (and (string? id-i) (string? id-n) (not= id-i id-n)))
    (is (not-any? (set (vals units0)) [id-i id-n]) "a new id, not the field's")
    (is (= [:anchored :anchored] (map :status (marks))))
    (is (= #{:item :nested} (set (map :kind (marks)))))
    (testing "an edit elsewhere leaves them anchored; an edit of the item is a mutation of the same unit"
      (write! ["uno" "dos" "TRES"])
      (is (true? (:ok (update!))))
      (is (= [:anchored :anchored] (map :status (marks))))
      (write! ["uno" "DOS" "TRES"])
      (is (= :mutated (:status (mark-of id-i))))
      (is (= [] (:candidates (mark-of id-i))))
      (is (true? (:ok (update!))))
      (is (= [:anchored :anchored] (map :status (marks))) "the write recorded the new hash")
      (is (= #{id-i id-n} (set (map :id (marks)))) "same ids")
      (is (= units0 (into {} (map (juxt :field :id)) (:units (:data (run "docs" "show" "a")))))))))

(deftest inserting-shifts-deleting-is-missing-and-closing-is-declared
  (let [id (id-of (item! 1))]
    (is (string? id))
    (write! ["uno" "nuevo" "dos" "tres"])
    (let [before (mark-of id)]
      (is (= :ambiguous (:status before)))
      (is (= [{:field "items" :index 1} {:field "items" :index 2}] (mapv :locator (:candidates before)))
          "the place now and the place with the same text: both proposed, none chosen")
      (is (true? (:ok (update!))))
      (is (= before (mark-of id)) "a normal write does not move it")
      (testing "the actor declares the new anchor, with a reason"
        (let [h (head)
              no-reason (item! 2 "--unit" id)]
          (is (= "cli/usage" (get-in no-reason [:error :type])))
          (is (= h (head)))
          (is (true? (:ok (item! 2 "--unit" id "--reason" "a new item went in above"))))
          (is (= :anchored (:status (mark-of id))))
          (is (= {:field "items" :index 2} (:locator (mark-of id))))
          (is (= "unit-reanchor" (get-in (last-entry) [:declared :kind])))
          (is (= "a new item went in above" (get-in (last-entry) [:declared :reason])))))
      (testing "deleting it does not end the unit"
        (write! ["uno" "nuevo"])
        (is (= :missing (:status (mark-of id))))
        (is (true? (:ok (update!))))
        (is (= :missing (:status (mark-of id))))
        (is (= id (:id (mark-of id))) "the id survives the write"))
      (testing "closing is a declaration"
        (is (true? (:ok (run "docs" "unmark" "a" id "--reason" "gone"))))
        (is (nil? (mark-of id)))
        (is (= "unit-closure" (get-in (last-entry) [:declared :kind])))
        (is (= :missing (get-in (last-entry) [:declared :status])))))))

(deftest identical-items-are-two-units-and-one-to-many-lists-every-candidate
  (write! ["igual" "igual" "otro" "igual"])
  (is (true? (:ok (update!))))
  (let [a (id-of (item! 0)) b (id-of (item! 1))]
    (is (and (string? a) (string? b) (not= a b)) "two identical items are two units")
    (is (= [:anchored :anchored] (map :status (marks))))
    (is (= [[] []] (map :candidates (marks))) "anchored: nothing is in doubt")
    (write! ["cambiado" "igual" "otro" "igual"])
    (let [s (mark-of a)]
      (is (= :ambiguous (:status s)))
      (is (= [0 1 3] (mapv #(get-in % [:locator :index]) (:candidates s)))
          "every candidate is listed, none picked")
      (is (= [:locator :hash :hash] (mapv :reason (:candidates s)))))
    (is (true? (:ok (update!))))
    (is (= :ambiguous (:status (mark-of a))) "a write does not choose among them")))

(deftest field-marks-are-not-a-hash-survive-rebuilds-and-the-parent-may-vanish
  (let [route-of (fn [] (let [s (:store (cli-store/open! (file ".kimun")))]
                          (into {} (map (fn [[p e]] [p (select-keys e [:projection-hash :content-hash :fingerprint])]))
                                (:routes s))))
        before (route-of)
        root-of (fn [] (:merkle-root (first (filter #(= "a" (:name %)) (:documents (:data (run "docs" "list")))))))]
    (is (pos? (count before)))
    (is (every? #(every? (comp not str/blank? str) (vals %)) (vals before)) "there are hashes to compare")
    (let [id-i (id-of (item! 0)) id-n (id-of (run "docs" "mark" "a" "--field" "meta" "--path" "version"))]
      (is (= before (route-of)) "marking moves no route hash")
      (testing "reading writes nothing"
        (let [h (head)]
          (run "docs" "marks" "a") (run "docs" "show" "a")
          (is (= h (head)))))
      (testing "a reanchor of the file keeps them"
        (fs/create-dirs (file "moved"))
        (fs/move (file "docs" "a.md") (file "moved" "a.md"))
        (is (true? (:ok (run "docs" "reanchor" "a" "moved/a.md" "--reason" "moved"))))
        (is (= #{id-i id-n} (set (map :id (marks))))))
      (testing "the parent field stops being extracted: the marks stay, missing (absence does not end a unit)"
        (spit (file "moved" "a.md") "# Bag\n\n## Metadata\n\n```yaml\n{}\n```\n")
        (let [u (run "docs" "update" "a" "--force")] (is (true? (:ok u)) (pr-str u)))
        (is (= #{id-i id-n} (set (map :id (marks)))))
        (is (= [:missing :missing] (map :status (marks))))
        (is (= [[] []] (map :candidates (marks))))))))

(deftest rejections-and-the-other-document-classes
  (let [h (head)
        err (fn [r] (get-in r [:error :type]))]
    (testing "a generic document has no fields"
      (is (= "docs/not-anchored" (err (run "docs" "mark" "g" "--field" "items" "--index" "0"))))
      (is (= "docs/not-anchored" (err (run "docs" "mark" "g" "--locator" "{\"field\":\"items\",\"index\":0}")))))
    (testing "nothing there"
      (is (= "docs/anchor-unresolved" (err (item! 9))))
      (is (= "docs/anchor-unresolved" (err (run "docs" "mark" "a" "--field" "meta" "--path" "no.such")))))
    (testing "usage"
      (is (= "cli/usage" (err (run "docs" "mark" "a" "--field" "items" "--index" "0" "--path" "x"))))
      (is (= "cli/usage" (err (run "docs" "mark" "a" "--index" "0")))))
    (is (= h (head)) "no rejection wrote")
    (let [id (id-of (item! 0))]
      (testing "an item already marked"
        (is (= "docs/exists" (err (item! 0)))))
      (testing "a closed field unit is not an item unit: unknown unit"
        (is (= "docs/missing" (err (item! 1 "--unit" "NOPE" "--reason" "x")))))
      (testing "an item mark moves to another typed document with the same id (K1f)"
        (spit (file "docs" "b.md") (bag ["x" "y"]))
        (is (true? (:ok (run "docs" "track" "BagDoc" "docs/b.md" "--name" "b"))))
        (let [r (run "docs" "mark" "b" "--unit" id "--from" "a" "--field" "items" "--index" "1" "--reason" "to b")]
          (is (true? (:ok r)) (pr-str r))
          (is (nil? (mark-of id)) "it left a")
          (is (= [id] (map :id (get-in (run "docs" "marks" "b") [:data :marks]))))
          (is (= "unit-move" (get-in (last-entry) [:declared :kind])))))))
    (testing "a document of a spec 16 class marks its list items the same way"
      (spit (file "Tagged.edn")
            (pr-str {:model/name "Tagged" :model/version 1 :base [] :document-role :source
                     :substrate {:parser :sldb-md/blocks :grammar "markdown"}
                     :generation {:projections {}}
                     :anchoring {:fields [{:name "title" :type :string :required true :description "Title."}
                                          {:name "tags" :type [:list :string] :required true :description "Tags."}]
                                 :selector [{:outer-index 0 :outer-type "heading" :outer-tag "h1" :handler :text
                                             :props ["title"] :regex "^(.*?)$"}
                                            {:outer-index 1 :outer-type "bullet_list" :outer-tag "ul" :handler :list :name "tags"}]}}))
      (is (true? (:ok (run "models" "define" "-f" "Tagged.edn"))))
      (spit (file "docs" "t.md") "# T\n\n* uno\n* dos\n")
      (is (true? (:ok (run "docs" "track" "Tagged" "docs/t.md" "--name" "t"))))
      (let [r (run "docs" "mark" "t" "--field" "tags" "--index" "1")]
        (is (true? (:ok r)) (pr-str r))
        (is (= [:anchored] (map :status (get-in (run "docs" "marks" "t") [:data :marks]))))
        (spit (file "docs" "t.md") "# T\n\n* nuevo\n* uno\n* dos\n")
        (is (= [:ambiguous] (map :status (get-in (run "docs" "marks" "t") [:data :marks])))))))
