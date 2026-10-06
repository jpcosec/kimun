(ns kimun.parity.merkle-promises-test
  "Promises of sldb v1's Merkle layers and operation cache
   (tests/store/test_merkle_cost.py, test_merkle_operation_cache.py,
   test_stale_shard_hashes.py) that survive kimun's design. v1 counted calls to
   its extractors and to its shard composer to prove that a write touches only
   its own document and that a repeated read does no work twice. kimun has no
   shards, dirty sets nor in-process caches to count: the cost of a write is
   the number of immutable objects it adds, the identity of an untouched
   document is its tree root, and the only cache is the derived index of the
   head (`rebuild-indexes`). The same promises are asserted on those."
  (:require [babashka.fs :as fs]
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

(defn- write!
  "`docs create` (verb \"create\") or `docs update` of the Note `n` with a whole payload."
  [p verb n title body tag]
  (let [target (if (= verb "create") ["Note" n] [n])
        e (apply run p "docs" verb
                 (concat target ["--set" (str "title=" title) "--set" (str "body=" body)
                                 "--set" (str "semantic_tags=[" tag "]")]))]
    (is (true? (:ok e)) (pr-str (:error e)))
    e))

(defn- project
  "A project with the Note model and `n` documents note-0 … note-(n-1)."
  [n]
  (let [p (str (fs/create-temp-dir {:prefix "kimun-merkle-"}))]
    (run p "stores" "init" "--name" "p")
    (spit (str (fs/path p "note.edn")) note)
    (assert (:ok (run p "models" "define" "-f" "note.edn")))
    (dotimes [i n] (write! p "create" (str "note-" i) (str "Note " i) (str "Body of note " i) "type.plain"))
    p))

(defn- opened [p] (cli-store/open! (cli-store/resolve-store {} env p)))
(defn- roots [p] (into {} (map (juxt :name :root)) (:data (run p "docs" "list"))))
(defn- objects [p] (set (map fs/file-name (fs/list-dir (fs/path p ".kimun" "objects")))))
(defn- source [o] (dissoc (cache/lookup! o) :index))
(defn- cache-file [p] (let [o (opened p)] (str (cache/cache-path (:path o) (:head (:store o))))))
(defn- snapshot [p]
  (into (sorted-map) (map (fn [f] [(str f) (vec (fs/read-all-bytes f))]))
        (filter fs/regular-file? (fs/glob (fs/path p ".kimun") "**" {:hidden true}))))

(defn- cost-of
  "Objects added by `write` to a store of `n` documents, and whether every
   document but `touched` kept its tree root."
  [n touched write]
  (let [p (project n) before-roots (roots p) before (objects p)]
    (write p)
    (let [after-roots (roots p)]
      {:added (count (remove before (objects p)))
       :kept? (every? (fn [[name root]] (= root (get after-roots name))) (dissoc before-roots touched))
       :others (count (dissoc before-roots touched))
       :p p})))

;; v1: store/test_merkle_cost.py::test_adding_one_document_extracts_only_that_document
(deftest adding-one-document-writes-only-that-document
  (let [add (fn [p] (write! p "create" "note-new" "Note new" "Body of the new note" "type.plain"))
        small (cost-of 3 nil add) large (cost-of 20 nil add)]
    (is (= 20 (:others large) (dec (count (roots (:p large))))) "the twenty earlier documents are still listed")
    (is (true? (:kept? large)) "none of the earlier documents got a new root")
    (is (pos? (:added large)))
    (is (= (:added small) (:added large)) "the cost does not grow with the documents left alone")
    (is (true? (:ok (run (:p large) "stores" "verify"))))))

;; v1: store/test_merkle_operation_cache.py::test_a_single_write_processes_only_its_own_document_not_every_document
(deftest a-single-update-writes-only-its-own-document
  (let [edit (fn [p] (write! p "update" "note-1" "Note 1" "changed body" "type.plain"))
        small (cost-of 3 "note-1" edit) large (cost-of 15 "note-1" edit)]
    (is (= 14 (:others large)))
    (is (true? (:kept? large)) "the fourteen other documents keep their roots")
    (is (not= (get (roots (project 3)) "note-1") (get (roots (:p large)) "note-1")))
    (is (= "changed body" (get-in (run (:p large) "docs" "show" "note-1") [:data :payload "body"])))
    (is (pos? (:added large)))
    (is (= (:added small) (:added large)) "the cost does not grow with the documents left alone")))

;; v1: store/test_merkle_operation_cache.py::test_documents_index_composed_at_most_once_per_operation
;; v1: store/test_merkle_cost.py::test_load_store_index_is_cached_by_file_signature
(deftest the-index-is-built-once-and-read-many-times
  ;; v1 keyed its in-process caches by the (mtime, size) of a file and by a
  ;; per-operation token; kimun keys the one cache it has by the head revision.
  (let [p (project 12)]
    (is (= {:source :built :reason :no-cache} (source (opened p))) "nothing written yet: built")
    (is (true? (:ok (run p "rebuild-indexes"))))
    (let [o (opened p) built (build/build (:store o)) before (snapshot p)]
      (dotimes [_ 20]
        (is (= {:source :cache} (source o)))
        (is (= built (cache/load! o))))
      (is (= before (snapshot p)) "twenty reads wrote nothing")
      (testing "the key is the head: a write is what makes the next read miss"
        (write! p "create" "note-late" "Late" "Late body" "type.plain")
        (is (= :built (:source (cache/lookup! (opened p)))))
        (is (contains? (:by-name (cache/load! (opened p))) "note-late"))))))

;; v1: store/test_merkle_operation_cache.py::test_documents_index_cache_reused_across_operations_when_nothing_moved
(deftest the-index-is-reused-by-a-later-operation-when-nothing-moved
  (let [p (project 12)]
    (run p "rebuild-indexes")
    (let [file (cache-file p) written (fs/last-modified-time file)]
      (is (= {:source :cache} (source (opened p))) "a store opened again is a fresh operation")
      (is (true? (:ok (run p "find" "Body" "--limit" "1"))))
      (is (true? (:ok (run p "docs" "list"))))
      (is (= {:source :cache} (source (opened p))))
      (is (= written (fs/last-modified-time file)) "reused verbatim, not rewritten"))))

;; v1: store/test_merkle_operation_cache.py::test_invalidate_forces_exactly_one_recompose_with_correct_content
(deftest an-invalid-index-is-rebuilt-once-with-the-right-content
  (let [p (project 6)]
    (run p "rebuild-indexes")
    (let [file (cache-file p) good (edn/read-string (slurp file))]
      (spit file (pr-str (assoc good :by-name {})))
      (let [o (opened p) r (cache/lookup! o)]
        (is (= :built (:source r)) "an index that does not match the store is not trusted")
        (is (= good (:index r) (build/build (:store o)))))
      (fs/delete file)
      (is (= {:source :built :reason :no-cache} (source (opened p))))
      (is (true? (:ok (run p "rebuild-indexes"))) "one rebuild")
      (is (= good (edn/read-string (slurp file))))
      (is (= {:source :cache} (source (opened p))) "and the next read is a hit again"))))

;; v1: store/test_merkle_operation_cache.py::test_a_hand_edit_through_stores_update_needs_no_recompose_next_operation
(deftest a-hand-edit-taken-in-is-what-the-next-operation-reads
  ;; v1 kept its in-process cache in step while `stores update` absorbed the
  ;; edit. In kimun an edit of the content is taken in by `docs update NAME`
  ;; (`stores update` only repairs what needs no decision), and the index is
  ;; keyed by the head: taking the edit in moves the head, so the old index can
  ;; never be served for the new content.
  (let [p (project 5)]
    (run p "rebuild-indexes")
    (let [old (cache-file p)]
      (spit (str (fs/path p "note-0.md"))
            (str/replace (slurp (str (fs/path p "note-0.md"))) "Body of note 0" "Hand-edited body"))
      (is (true? (:ok (run p "docs" "update" "note-0"))))
      (let [o (opened p) index (cache/load! o) full (:data (run p "docs" "show" "note-0"))]
        (is (not= old (cache-file p)) "the head moved")
        (is (= "Hand-edited body" (get-in full [:payload "body"])))
        (is (= (build/build (:store o)) index))
        (is (= {:hash-c (:content-hash full) :hash-d (:source-hash full)}
               (get-in (build/fingerprint index) [:documents "Note:note-0"])))
        (is (= ["note-0"] (distinct (map :doc (:data (run p "find" "Hand-edited"))))))
        (is (true? (:ok (run p "stores" "check"))))
        (run p "rebuild-indexes")
        (is (= {:source :cache} (source (opened p))))
        (is (= index (cache/load! (opened p))))))))

;; v1: store/test_merkle_operation_cache.py::test_dirty_namespaces_are_independent
(deftest one-write-reaches-the-sections-view-and-the-semantic-view
  ;; v1's bug: semantic and sections shared one dirty set, so the consumer that
  ;; ran first hid the write from the other. Both orders of reading are tried.
  (let [p (project 3)
        titles (fn [n] (mapv :title (get-in (run p "sections" "list" n) [:data :sections])))
        tagged (fn [t] (set (get-in (run p "semantic" "show" t) [:data :documents])))]
    (write! p "update" "note-0" "First retitle" "b" "type.first")
    (is (= "First retitle" (first (titles "note-0"))))
    (is (= #{"Note:note-0"} (tagged "type.first")))
    (write! p "update" "note-0" "Second retitle" "b" "type.second")
    (is (= #{"Note:note-0"} (tagged "type.second")))
    (is (= "Second retitle" (first (titles "note-0"))))
    (is (= #{} (tagged "type.first")))
    (is (= #{"Note:note-1" "Note:note-2"} (tagged "type.plain")) "the other documents did not move")))

;; v1: store/test_stale_shard_hashes.py::test_update_recomputes_a_content_hash_it_cannot_trust
;; v1: store/test_stale_shard_hashes.py::test_update_backfills_field_hashes_of_an_older_shard
(deftest a-hash-stored-by-an-older-format-is-not-trusted
  ;; v1: a shard written before per-field hashes existed carried a content hash
  ;; nothing had verified; it was believed for ever and `stores check` blamed a
  ;; file nobody had edited. kimun's hashes with authority are object names
  ;; (a tampered object is the business of `stores verify`); the only other
  ;; stored hashes are those of the derived index, whose format is versioned.
  (let [p (project 1)
        original (:content-hash (:data (run p "docs" "show" "note-0")))
        stale (apply str (repeat 64 "d"))
        older (fn [text] (pr-str (update (edn/read-string text) :version dec)))]
    (run p "rebuild-indexes")
    (let [file (cache-file p) current (slurp file)]
      (is (str/includes? current original))
      (testing "an index of the older format, its hash intact, is rebuilt in the current one"
        (spit file (older current))
        (let [o (opened p) r (cache/lookup! o)]
          (is (= {:source :built :reason :stale} (dissoc r :index)))
          (is (= build/version (:version (:index r)))))
        (is (true? (:ok (run p "rebuild-indexes"))))
        (is (= (edn/read-string current) (edn/read-string (slurp file))))
        (is (= {:source :cache} (source (opened p)))))
      (testing "and the wrong hash it carries is recomputed, never served"
        (spit file (older (str/replace current original stale)))
        (let [r (cache/lookup! (opened p))]
          (is (= :built (:source r)))
          (is (= original (get-in (build/fingerprint (:index r)) [:documents "Note:note-0" :hash-c]))))
        (is (true? (:ok (run p "stores" "check"))) "no finding against a file nobody edited")
        (is (= original (:content-hash (:data (run p "docs" "show" "note-0")))))
        (is (true? (:ok (run p "stores" "update"))))
        (is (true? (:ok (run p "rebuild-indexes"))))
        (is (= (edn/read-string current) (edn/read-string (slurp file))))
        (is (not (str/includes? (slurp file) stale)))
        (is (= {:source :cache} (source (opened p))))))))

;; v1: store/test_stale_shard_hashes.py::test_update_leaves_an_already_current_shard_untouched
(deftest update-leaves-a-current-store-untouched
  (let [p (project 2)]
    (run p "rebuild-indexes")
    (let [before (snapshot p) head (:revision (run p "stores" "check"))]
      (dotimes [_ 2]
        (let [e (run p "stores" "update")]
          (is (true? (:ok e)))
          (is (= [] (get-in e [:data :updated])))))
      (is (= head (:revision (run p "stores" "check"))) "no revision was written")
      (is (= before (snapshot p)) "not a byte under the store changed"))))
