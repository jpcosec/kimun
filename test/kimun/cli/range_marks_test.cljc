(ns kimun.cli.range-marks-test
  "S2b-r from the CLI: a RANGE of text inside a block is a unit when it is
   DECLARED (`docs mark NAME --locator <block> --range S:E`), counted in
   graphemes of the NFC plain text of the block (docs/v2/02 §4.1, decision 32),
   and it reads, is re-anchored, moved and closed with the states and candidates
   of K1e–K1h. kimun never decides that a span moved; nothing here is a hash
   (docs/v2/14 §7, §20–§21; the rules are the team's inferences)."
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

(def family "👨‍👩‍👧")
;; plain text of the first paragraph: "Hola mundo é <family> fin" (18 graphemes);
;; `mundo` is bold in the source and `é` is e + a combining mark
(defn- plain [para] (str "# Nota\n\n" para "\n\nOtro texto.\n"))
(def para0 (str "Hola **mundo** é " family " fin"))
(def sec [{:title "Nota" :n 0}])
(defn- blk [type n] (pr-str {:section sec :type type :n n}))
(def p0 (blk :paragraph 0))
(def p1 (blk :paragraph 1))

(def model
  (pr-str {:model/name "BagDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "items" :type [:list :string] :required false :description "Items."}]
           :template "# ⸢rev•title⸥\n\n* ⸢optrev,list•items⸥\n"}))

(def tagged
  (pr-str {:model/name "Tagged" :model/version 1 :base [] :document-role :source
           :substrate {:parser :sldb-md/blocks :grammar "markdown"}
           :generation {:projections {}}
           :anchoring {:fields [{:name "title" :type :string :required true :description "Title."}
                                {:name "tags" :type [:list :string] :required true :description "Tags."}]
                       :selector [{:outer-index 0 :outer-type "heading" :outer-tag "h1" :handler :text
                                   :props ["title"] :regex "^(.*?)$"}
                                  {:outer-index 1 :outer-type "bullet_list" :outer-tag "ul" :handler :list :name "tags"}]}}))

(defn- setup! [p]
  (fs/create-dirs (str p "/notes"))
  (spit (str p "/notes/plain.md") (plain para0))
  (spit (str p "/notes/other.md") "# Otra\n\nSolo esto.\n")
  (spit (str p "/bag.edn") model)
  (spit (str p "/Tagged.edn") tagged)
  (spit (str p "/notes/bag.md") "# Bolsa\n\n* uno\n* dos\n")
  (spit (str p "/notes/tag.md") "# Título largo\n\n* uno\n* dos\n")
  (is (true? (:ok (run-in p "stores" "init" "--name" "p"))))
  (is (true? (:ok (run-in p "docs" "ingest" "notes/plain.md"))))
  (is (true? (:ok (run-in p "docs" "ingest" "notes/other.md"))))
  (is (true? (:ok (run-in p "models" "define" "-f" "bag.edn"))))
  (is (true? (:ok (run-in p "models" "define" "-f" "Tagged.edn"))))
  (is (true? (:ok (run-in p "docs" "track" "BagDoc" "notes/bag.md" "--name" "bag"))))
  (is (true? (:ok (run-in p "docs" "track" "Tagged" "notes/tag.md" "--name" "tag")))))

(use-fixtures :each
  (fn [f]
    (let [p (str (ktu/temp-dir "kimun-range-marks-"))]
      (try (binding [*p* p] (setup! p) (f)) (finally (fs/delete-tree p))))))

(defn- head [] (:head (:store (cli-store/open! (file ".kimun")))))
(defn- marks [name] (get-in (run "docs" "marks" name) [:data :marks]))
(defn- mark-of [name id] (first (filter #(= id (:id %)) (marks name))))
(defn- id-of [r] (get-in r [:data :id]))
(defn- span! [name block range & more] (apply run "docs" "mark" name "--locator" block "--range" range more))
(defn- write! [rel text] (spit (file "notes" rel) text))
(defn- blk-in [title] (pr-str {:section [{:title title :n 0}] :type :paragraph :n 0}))
(defn- update! [rel name] (run "docs" "update" (str "notes/" rel) "--name" name))

(deftest a-range-is-marked-by-declaration-and-read-over-the-plain-text
  (let [r (span! "plain" p0 "5:10" "--label" "la palabra")
        id (id-of r)
        b (run "docs" "mark" "plain" "--locator" p0)
        bid (id-of b)]
    (is (true? (:ok r)) (pr-str r))
    (is (true? (:ok b)) "the block itself is another unit, with its own id")
    (is (and (string? id) (string? bid) (not= id bid)))
    (is (= [{:id id :kind :span :label "la palabra" :status :anchored :candidates []
             :locator {:block {:section sec :type :paragraph :n 0} :range [5 10]}}]
           (filter #(= id (:id %)) (marks "plain")))
        "the unit is the block locator plus the range in plain graphemes: `mundo`, not the source offsets")
    (testing "an edit after the range leaves the span anchored while the block unit sees a mutation"
      (write! "plain.md" (plain (str/replace para0 " fin" " final")))
      (is (= :anchored (:status (mark-of "plain" id))))
      (is (= :mutated (:status (mark-of "plain" bid)))))
    (testing "an edit inside the range is a mutation of the same unit; a write records it"
      (write! "plain.md" (plain (str/replace (str/replace para0 " fin" " final") "**mundo**" "**MUNDO**")))
      (is (= :mutated (:status (mark-of "plain" id))))
      (is (= [] (:candidates (mark-of "plain" id))))
      (is (true? (:ok (update! "plain.md" "plain"))))
      (is (= :anchored (:status (mark-of "plain" id))))
      (is (= id (:id (mark-of "plain" id))) "same id"))
    (testing "the plain text counts a combining mark and a ZWJ family as one grapheme each"
      (let [e (id-of (span! "plain" p0 "11:12"))
            f (id-of (span! "plain" p0 "13:14"))]
        (is (and (string? e) (string? f)))
        (write! "plain.md" (plain (str/replace (str/replace para0 " fin" " final") "é" "É")))
        (is (= :mutated (:status (mark-of "plain" e))) "only the é moved: the family is untouched")
        (is (= :anchored (:status (mark-of "plain" f))))))))

(deftest a-shifted-span-is-proposed-never-chosen-and-moves-by-declaration
  (let [id (id-of (span! "plain" p0 "5:10"))
        before (str "# Nota\n\nNuevo párrafo largo.\n\n" para0 "\n\nOtro texto.\n")
        loc (fn [n] {:block {:section sec :type :paragraph :n n} :range [5 10]})]
    (write! "plain.md" before)
    (testing "a paragraph inserted above shifts the ordinal: the span reads another thing"
      (let [s (mark-of "plain" id)]
        (is (= :ambiguous (:status s)))
        (is (= [{:locator (loc 0) :reason :locator} {:locator (loc 1) :reason :hash}] (:candidates s))
            "the place now and the place with the same sub-text; none is chosen")
        (is (true? (:ok (update! "plain.md" "plain"))))
        (is (= s (mark-of "plain" id)) "a normal write does not move it")))
    (testing "the actor declares the new anchor, with a reason"
      (let [h (head)
            bare (span! "plain" p1 "5:10" "--unit" id)]
        (is (= "cli/usage" (get-in bare [:error :type])))
        (is (= h (head)))
        (is (true? (:ok (span! "plain" p1 "5:10" "--unit" id "--reason" "a paragraph went in above"))))
        (is (= :anchored (:status (mark-of "plain" id))))
        (is (= (loc 1) (:locator (mark-of "plain" id))))
        (is (= [5 10] (get-in (mark-of "plain" id) [:locator :range])))))
    (testing "it moves to another document with the same id, in one declared revision"
      (let [o (blk-in "Otra")
            r (run "docs" "mark" "other" "--unit" id "--from" "plain" "--locator" o "--range" "0:4"
                   "--reason" "the text lives there now")]
        (is (true? (:ok r)) (pr-str r))
        (is (nil? (mark-of "plain" id)) "it left the first document")
        (is (= [id] (map :id (marks "other"))))
        (is (= [0 4] (get-in (mark-of "other" id) [:locator :range])))))
    (testing "deleting the text does not end the unit; closing is a declaration"
      (write! "other.md" "# Otra\n\n```\nx\n```\n")
      (is (= :missing (:status (mark-of "other" id))))
      (is (true? (:ok (update! "other.md" "other"))))
      (is (= id (:id (mark-of "other" id))) "the id survives the write")
      (is (true? (:ok (run "docs" "unmark" "other" id "--reason" "gone"))))
      (is (nil? (mark-of "other" id))))))

(deftest what-has-no-plain-text-or-no-valid-range-is-rejected-and-writes-nothing
  (write! "plain.md" (str "# Nota\n\nHola mundo.\n\n```clj\n(+ 1 2)\n```\n\n* uno\n* dos\n\nEsto * no entra.\n"))
  (is (true? (:ok (update! "plain.md" "plain"))))
  (let [h (head)
        err (fn [r] (get-in r [:error :type]))
        msg (fn [r] (str (pr-str (:error r))))]
    (is (true? (:ok (span! "plain" p0 "5:10"))) "a good range on a paragraph is accepted")
    (let [h1 (head)]
      (testing "ranges"
        (is (= "cli/usage" (err (span! "plain" p0 "3:3"))) "an empty range is not a mark")
        (is (= "cli/usage" (err (span! "plain" p0 "5:2"))))
        (is (= "cli/usage" (err (span! "plain" p0 "abc"))))
        (is (= "docs/anchor-unresolved" (err (span! "plain" p0 "5:99"))) "past the text")
        (is (= "docs/exists" (err (span! "plain" p0 "5:10"))) "that span already is a unit"))
      (testing "blocks without plain text: nothing is invented for them"
        (doseq [b [(blk :code 0) (blk :list 0) (blk :opaque 0)]]
          (let [r (span! "plain" b "0:2")]
            (is (= "docs/anchor-unresolved" (err r)) b)
            (is (str/includes? (msg r) "no plain text") (str b ": " (msg r)))))
        (is (= "docs/anchor-unresolved" (err (span! "plain" (blk :paragraph 9) "0:2")))))
      (testing "usage"
        (is (= "cli/usage" (err (run "docs" "mark" "plain" "--range" "0:2"))) "a range needs a block")
        (is (= "cli/usage" (err (run "docs" "mark" "plain" "--locator" (pr-str sec) "--range" "0:2"))) "a section is not a block")
        (is (= "cli/usage" (err (run "docs" "mark" "plain" "--field" "x" "--index" "0" "--range" "0:2")))))
      (is (= h1 (head)) "no rejection wrote")
      (is (not= h h1) "while the accepted one did"))))

(deftest span-marks-in-typed-documents-are-not-a-hash-and-survive-a-reanchor
  (let [route-of (fn [] (into {} (map (fn [[p e]] [p (select-keys e [:projection-hash :content-hash :fingerprint])]))
                              (:routes (:store (cli-store/open! (file ".kimun"))))))
        root-of (fn [n] (:merkle-root (first (filter #(= n (:name %)) (:documents (:data (run "docs" "list")))))))
        bag-h (pr-str {:section [{:title "Bolsa" :n 0}] :type :heading :n 0})
        tag-h (pr-str {:section [{:title "Título largo" :n 0}] :type :heading :n 0})
        before (route-of)
        roots [(root-of "bag") (root-of "tag")]]
    (is (pos? (count before)))
    (is (every? #(every? (comp not str/blank? str) (vals %)) (vals before)) "there are hashes to compare")
    (let [b (id-of (span! "bag" bag-h "0:3"))
          t (id-of (span! "tag" tag-h "0:6"))]
      (is (and (string? b) (string? t)))
      (is (= before (route-of)) "marking moves no route hash")
      (is (= roots [(root-of "bag") (root-of "tag")]) "nor a Merkle root")
      (is (= [{:status :anchored :kind :span}] (map #(select-keys % [:status :kind]) (marks "tag"))) "a spec 16 class too")
      (is (= #{:span} (set (map :kind (get-in (run "docs" "show" "bag") [:data :marks])))) "`docs show` lists it")
      (testing "reading writes nothing"
        (let [h (head)] (run "docs" "marks" "bag") (run "docs" "show" "tag") (is (= h (head)))))
      (testing "an edit of the list leaves the heading span anchored; a new title is a new section locator: missing"
        (write! "bag.md" "# Bolsa\n\n* uno\n* dos\n* tres\n")
        (is (true? (:ok (run "docs" "update" "bag"))))
        (is (= [:anchored] (map :status (marks "bag"))))
        (write! "tag.md" "# Tátulo largo\n\n* uno\n* dos\n")
        (is (= [:missing] (map :status (marks "tag"))) "the block locator carries the title of its section")
        (is (= [[]] (map :candidates (marks "tag")))))
      (testing "a reanchor of the file keeps the unit"
        (fs/create-dirs (file "moved"))
        (fs/move (file "notes" "bag.md") (file "moved" "bag.md"))
        (is (true? (:ok (run "docs" "reanchor" "bag" "moved/bag.md" "--reason" "moved"))))
        (is (= [b] (map :id (marks "bag"))))))))
