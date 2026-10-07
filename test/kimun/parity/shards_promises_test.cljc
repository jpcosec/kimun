(ns kimun.parity.shards-promises-test
  "Promises of sldb v1's per-document shards (tests/store/test_documents_shards.py,
   test_merkle_shards.py) that survive kimun's design. v1 kept one YAML shard
   per document for three aggregates (documents, semantic, sections) and proved
   that composing the shards equals extracting every document directly. kimun
   has no shards: documents are trees of content-addressed objects and the
   aggregates are derived views of the head. The promise ported here is the
   observable one: what the store answers (documents, sections, semantic tags,
   index fingerprint) equals what reading each Markdown file directly gives,
   after creating, updating and untracking, whatever the order of the writes."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.surface.index.build :as build]
            [kimun.surface.index.cache :as cache]))

(def env {"USER" "tester"})

(defn- run [cwd & argv] (:envelope (main/run (vec argv) env cwd)))

(def note
  (pr-str {:model/name "Note" :model/version 1 :base [] :semantics {"domain" ["notes"]}
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "body" :type :string :required true :description "Body."}
                    {:name "semantic_tags" :type [:list :string] :required true :description "Tags."}]
           :template "# ⸢rev•title⸥\n\n## Body\n\n⸢rev•body⸥\n\n## Semantic Tags\n\n- ⸢rev,list•semantic_tags⸥\n"}))

(defn- tag-of [i] (if (even? i) "type.even" "type.odd"))

(defn- create! [p i]
  (is (true? (:ok (run p "docs" "create" "Note" (str "note-" i)
                       "--set" (str "title=Note " i) "--set" (str "body=Body of note " i)
                       "--set" (str "semantic_tags=[" (tag-of i) "]"))))))

(defn- project
  "A project with the Note model and the documents note-<i> for `is`, created in that order."
  [is]
  (let [p (str (ktu/temp-dir "kimun-shards-"))]
    (run p "stores" "init" "--name" "p")
    (spit (str (fs/path p "note.edn")) note)
    (assert (:ok (run p "models" "define" "-f" "note.edn")))
    (doseq [i is] (create! p i))
    p))

(defn- opened [p] (cli-store/open! (cli-store/resolve-store {} env p)))
(defn- k [m key] (if (contains? m key) (get m key) (get m (name key))))
(defn- listed [p] (:data (run p "docs" "list" "--model" "Note")))

(defn- reference
  "What reading every tracked file directly gives, with no store in the way:
   payloads (`extract` of the model file against the Markdown), heading titles
   and the documents under each own tag."
  [p]
  (let [docs (for [d (listed p) :let [file (str (fs/path p (k d :path)))]]
               {:name (k d :name)
                :payload (get-in (run p "extract" "note.edn" "--input" (k d :path)) [:data :payload])
                :titles (vec (keep #(second (re-matches #"#+\s+(.*)" %)) (str/split-lines (slurp file))))})]
    {:payloads (into {} (map (juxt :name :payload)) docs)
     :titles (into {} (map (juxt :name :titles)) docs)
     :tags (reduce (fn [m d] (reduce #(update %1 %2 (fnil conj #{}) (str "Note:" (:name d)))
                                     m (get (:payload d) "semantic_tags")))
                   {"type.even" #{} "type.odd" #{}} docs)}))

(defn- composed
  "The same three aggregates as the store answers them."
  [p]
  (let [names (map #(k % :name) (listed p))]
    {:payloads (into {} (for [n names] [n (get-in (run p "docs" "show" n) [:data :payload])]))
     :titles (into {} (for [n names]
                        [n (mapv #(k % :title) (get-in (run p "sections" "list" n) [:data :sections]))]))
     :tags (into {} (for [t ["type.even" "type.odd"]]
                      [t (set (get-in (run p "semantic" "show" t) [:data :documents]))]))}))

;; v1: store/test_documents_shards.py::test_shard_aggregate_equals_direct_extraction
;; v1: store/test_merkle_shards.py::test_shard_aggregate_equals_direct_extraction
(deftest the-store-answers-what-direct-extraction-gives
  (let [p (project (range 8)) r (reference p)]
    (is (= 8 (count (:payloads r))))
    (is (= "Body of note 3" (get-in r [:payloads "note-3" "body"])) "the reference really extracted")
    (is (= ["Note 3" "Body" "Semantic Tags"] (get-in r [:titles "note-3"])))
    (is (= #{"Note:note-1" "Note:note-3" "Note:note-5" "Note:note-7"} (get-in r [:tags "type.odd"])))
    (is (= r (composed p)))))

;; v1: store/test_documents_shards.py::test_shard_aggregate_equals_direct_extraction_after_writes
;; v1: store/test_merkle_shards.py::test_shard_aggregate_equals_direct_extraction_after_writes
(deftest the-store-answers-what-direct-extraction-gives-after-writes
  (let [p (project (range 5))]
    (create! p 5)
    (is (true? (:ok (run p "docs" "update" "note-1" "--set" "title=Shard Note 1"
                         "--set" "body=changed shard content" "--set" "semantic_tags=[type.even]"))))
    (is (true? (:ok (run p "docs" "untrack" "note-2"))))
    (let [r (reference p) c (composed p)]
      (is (= r c))
      (is (= "changed shard content" (get-in c [:payloads "note-1" "body"])))
      (is (= "Shard Note 1" (first (get-in c [:titles "note-1"]))))
      (is (contains? (get-in c [:tags "type.even"]) "Note:note-1") "the changed tag moved the document")
      (is (not (contains? (get-in c [:tags "type.odd"]) "Note:note-1")))
      (is (contains? (:payloads c) "note-5"))
      (is (not (contains? (:payloads c) "note-2")))
      (is (not-any? #(contains? % "Note:note-2") (vals (:tags c)))))))

;; v1: store/test_documents_shards.py::test_untrack_deletes_the_shard_not_just_the_aggregate_entry
;; v1: store/test_merkle_shards.py::test_untrack_deletes_the_shards_not_just_the_aggregate_entry
(deftest untrack-leaves-no-derived-record-behind
  ;; v1 deleted the document's shard files; kimun has none, so the residue to
  ;; rule out is a record in any derived view, including the index written on disk.
  (let [p (project (range 3))]
    (is (true? (:ok (run p "rebuild-indexes"))))
    (let [o (opened p) before (cache/cache-path (:path o) (:head (:store o)))]
      (is (contains? (:by-name (edn/read-string (slurp before))) "note-1"))
      (is (true? (:ok (run p "docs" "untrack" "note-1"))))
      (is (= ["note-0" "note-2"] (mapv #(k % :name) (listed p))))
      (is (= "docs/missing" (get-in (run p "docs" "show" "note-1") [:error :type])))
      (is (false? (:ok (run p "sections" "list" "note-1"))))
      (is (= #{} (set (get-in (run p "semantic" "show" "type.odd") [:data :documents]))))
      (is (true? (:ok (run p "rebuild-indexes"))))
      (let [o2 (opened p) after (cache/cache-path (:path o2) (:head (:store o2)))
            index (edn/read-string (slurp after))]
        (is (not (fs/exists? before)) "the index of the previous head is pruned")
        (is (= #{"note-0" "note-2"} (set (keys (:by-name index)))))
        (is (not (str/includes? (slurp after) "note-1")) "no trace of the untracked document")
        (is (= #{"Note:note-0" "Note:note-2"} (set (keys (:documents (build/fingerprint index)))))))
      (is (true? (:ok (run p "stores" "check")))))))

;; v1: store/test_documents_shards.py::test_hash_b_matches_a_full_scan_of_the_same_content
(deftest the-fingerprint-kept-through-writes-matches-a-full-scan
  ;; v1: the roster hash kept incrementally on the write path equals hashing
  ;; every child directly. kimun: the fingerprint of the index of a store built
  ;; write by write equals the hashes of every document read one by one.
  (let [p (project (range 6))
        _ (run p "docs" "update" "note-4" "--set" "title=Note 4" "--set" "body=moved" "--set" "semantic_tags=[type.even]")
        fp (build/fingerprint (cache/load! (opened p)))
        scan (into {} (for [d (listed p) :let [full (:data (run p "docs" "show" (k d :name)))]]
                        [(str "Note:" (k d :name)) {:hash-c (k full :content-hash) :hash-d (k full :source-hash)}]))]
    (is (= 6 (count scan) (count (:documents fp))))
    (is (every? string? (mapcat vals (vals scan))))
    (is (= scan (:documents fp)))))

;; v1: store/test_documents_shards.py::test_hash_b_is_order_independent
(deftest identity-does-not-depend-on-the-order-of-the-writes
  ;; v1's hash_b covered (name, hash_c, hash_d) of every document, sorted. Its
  ;; kimun counterpart is the `:documents` part of the index fingerprint. Tree
  ;; ids and tree roots are left out on purpose: a tree is nominal (a ULID per
  ;; tree), so two stores never share them, whatever the order.
  (let [identity-of (fn [p] (:documents (build/fingerprint (cache/load! (opened p)))))
        p (project [0 1 2 3]) before (identity-of p)]
    (is (= #{"Note:note-0" "Note:note-1" "Note:note-2" "Note:note-3"} (set (keys before))))
    (is (= 4 (count (set (map :hash-c (vals before))))) "four distinct contents")
    (testing "untracking everything and tracking it again in another order"
      (doseq [i [0 1 2 3]] (is (true? (:ok (run p "docs" "untrack" (str "note-" i))))))
      (is (= {} (identity-of p)))
      (doseq [i [3 1 0 2]]
        (is (true? (:ok (run p "docs" "track" "Note" (str "note-" i ".md") "--name" (str "note-" i))))))
      (is (= before (identity-of p)))
      (is (= (pr-str (into (sorted-map) before)) (pr-str (into (sorted-map) (identity-of p))))))
    (testing "another store that created the same documents in another order"
      (is (= before (identity-of (project [2 0 3 1])))))))
