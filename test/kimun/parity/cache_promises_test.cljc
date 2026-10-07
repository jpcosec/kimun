(ns kimun.parity.cache-promises-test
  "v1 runtime caches (`test_runtime_cache.py`, `store/test_cache_identity.py`,
   `store/test_runtime_cache_outside_store.py`) as the promises that survive
   in kimun, whose only cache is the derived index `.kimun/index/<head>.edn`:
   an answer belongs to its own store and model, a read writes nothing, a
   cached read does not extract again, losing the cache changes no answer, a
   change of contract or a hand edit is seen by the next command, and a write
   that changes nothing touches nothing. Each test names its v1 function."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.surface.index.build :as build]
            [kimun.surface.index.cache :as cache]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- opened [p] (cli-store/open! (cli-store/resolve-store {} env p)))

(defn- model [nm semantics fields]
  {:model/name nm :model/version 1 :base [] :semantics semantics
   :fields (mapv (fn [f] {:name f :type :string :required true :description f}) fields)
   :template (str "# ⸢rev•" (first fields) "⸥\n" (when (second fields) (str "\n⸢rev•" (second fields) "⸥\n")))})

(defn- store-with [m]
  (let [p (str (ktu/temp-dir "kimun-cachep-"))]
    (run p "stores" "init" "--name" "world")
    (spit (str (fs/path p "model.edn")) (pr-str m))
    (is (true? (:ok (run p "models" "define" "-f" "model.edn"))))
    p))

(defn- spec-world
  "A store with one `Spec` document `d` whose text is `# D` — the same in every world."
  [semantics field]
  (let [p (store-with (model "Spec" semantics [field]))]
    (is (true? (:ok (run p "docs" "create" "Spec" "d" "--set" (str field "=D")))))
    (is (= "# D\n" (slurp (str (fs/path p "d.md")))))
    p))

(defn- notes
  "A store with NoteDoc and the tracked files one.md and two.md."
  []
  (let [p (store-with (model "NoteDoc" {"type" ["note"]} ["title" "body"]))]
    (doseq [n ["one" "two"]]
      (spit (str (fs/path p (str n ".md"))) (str "# " n "\n\nbody of " n "\n"))
      (is (true? (:ok (run p "docs" "track" "NoteDoc" (str n ".md"))))))
    p))

(defn- tagged [p tag] (mapv :name (:data (run p "find" (str "se." tag)))))
(defn- payload [p n] (get-in (run p "docs" "show" n) [:data :payload]))
(defn- snapshot
  "Every file under `dir` with its bytes and modification time."
  [dir]
  (into (sorted-map)
        (map (fn [f] [(str f) [(vec (fs/read-all-bytes f)) (str (fs/last-modified-time f))]]))
        (filter fs/regular-file? (fs/glob dir "**" {:hidden true}))))
(defn- reads [p]
  [(:data (run p "docs" "list")) (:data (run p "docs" "show" "one")) (:data (run p "find" "body"))
   (:data (run p "find" "se.type.note")) (:data (run p "fields" "query" "title")) (:data (run p "docs" "status"))])

;; v1: store/test_cache_identity.py::test_two_stores_same_path_same_text_same_class_name_keep_their_own_tags
(deftest two-stores-with-the-same-path-text-and-model-name-keep-their-own-tags
  (let [a (spec-world {"type" ["alpha"]} "title") b (spec-world {"type" ["beta"]} "title")]
    (doseq [p [a b]] (is (true? (:ok (run p "rebuild-indexes")))))
    (is (= [["d"] []] [(tagged a "type.alpha") (tagged a "type.beta")]))
    (is (= [["d"] []] [(tagged b "type.beta") (tagged b "type.alpha")]))))

;; v1: store/test_cache_identity.py::test_a_payload_is_what_this_class_extracted_and_hash_d_is_computed_from_it
(deftest a-payload-is-what-its-own-model-extracted-and-its-content-hash-follows
  (let [a (spec-world {"type" ["t"]} "title") b (spec-world {"type" ["n"]} "name")
        show (fn [p] (:data (run p "docs" "show" "d")))]
    (is (= (:source-hash (show a)) (:source-hash (show b))) "the premise: the two texts are identical")
    (is (= {"title" "D"} (:payload (show a))))
    (is (= {"name" "D"} (:payload (show b))))
    (is (not= (:content-hash (show a)) (:content-hash (show b))) "the content hash is of the payload, not of the text")
    (testing "and the cached index of each says the same"
      (doseq [p [a b]] (is (true? (:ok (run p "rebuild-indexes")))))
      (is (= {"title" "D"} (:payload (show a))))
      (is (= {"name" "D"} (:payload (show b))))
      (is (= (:content-hash (show b)) (get-in (build/fingerprint (cache/load! (opened b))) [:documents "Spec:d" :hash-c]))))))

;; v1: store/test_cache_identity.py::test_a_model_redefined_in_a_running_process_gets_its_new_tags
(deftest a-model-redefined-in-a-running-process-gets-its-new-tags
  (let [p (spec-world {"type" ["before"]} "title")]
    (is (true? (:ok (run p "rebuild-indexes"))))
    (is (= ["d"] (tagged p "type.before")))
    (spit (str (fs/path p "model.edn")) (pr-str (assoc (model "Spec" {"type" ["after"]} ["title"]) :model/version 2)))
    (is (true? (:ok (run p "models" "define" "-f" "model.edn"))))
    (is (= ["d"] (tagged p "type.after")))
    (is (= [] (tagged p "type.before")))))

;; v1: store/test_runtime_cache_outside_store.py::test_read_leaves_store_tree_identical
(deftest a-read-leaves-the-store-tree-identical
  (let [p (notes) before (snapshot (fs/path p ".kimun"))]
    (is (every? seq (reads p)))
    (is (true? (:ok (run p "stores" "check"))))
    (is (= before (snapshot (fs/path p ".kimun"))))
    (is (not (fs/exists? (fs/path p ".kimun" "index"))) "a read never creates the cache")))

;; v1: store/test_runtime_cache_outside_store.py::test_cache_hits_new_location_and_second_read_does_not_reextract
(deftest a-cached-read-does-not-extract-again-and-losing-the-cache-changes-no-answer
  (let [p (notes) uncached (reads p)
        report (:data (run p "rebuild-indexes"))
        file (fs/path p ".kimun" (:cache report))
        builds (atom 0) real build/build]
    (is (fs/regular-file? file) "the cache is one file under the store's own index directory")
    (is (= (str (fs/path "index" (str (:head report) ".edn"))) (:cache report)))
    (with-redefs [build/build (fn [& args] (swap! builds inc) (apply real args))]
      (is (= :cache (:source (cache/lookup! (opened p)))))
      (is (= (get uncached 3) (:data (run p "find" "se.type.note"))))
      (is (= 0 @builds) "nothing was extracted again")
      (fs/delete-tree (fs/path p ".kimun" "index"))
      (is (= (get uncached 3) (:data (run p "find" "se.type.note"))))
      (is (pos? @builds) "without the cache the index is built from the store"))
    (is (= uncached (reads p)) "deleting the cache changed no answer")))

;; v1: store/test_runtime_cache_outside_store.py::test_unwritable_cache_read_still_works
(deftest a-read-works-where-the-cache-cannot-be-written
  (let [p (notes) answers (reads p)]
    (spit (str (fs/path p ".kimun" "index")) "not a directory")
    (is (= :built (:source (cache/lookup! (opened p)))))
    (is (= answers (reads p)))
    (is (= "not a directory" (slurp (str (fs/path p ".kimun" "index")))))))

;; v1: test_runtime_cache.py::test_repeated_loads_share_documents
(deftest repeated-loads-give-the-same-documents
  (let [p (notes)]
    (is (= (cache/load! (opened p)) (cache/load! (opened p))) "built twice: equal")
    (is (true? (:ok (run p "rebuild-indexes"))))
    (let [before (snapshot (fs/path p ".kimun"))
          a (cache/lookup! (opened p)) b (cache/lookup! (opened p))]
      (is (= [:cache :cache] (mapv :source [a b])) "both loads are served by the one cached index")
      (is (= (:index a) (:index b) (build/build (:store (opened p)))))
      (is (= before (snapshot (fs/path p ".kimun")))))))

;; v1: test_runtime_cache.py::test_a_contract_change_invalidates_document_cache_entries_without_editing_the_markdown
(deftest a-contract-change-is-seen-without-editing-the-markdown
  (let [p (notes)]
    (is (true? (:ok (run p "rebuild-indexes"))))
    (is (not (contains? (payload p "one") "status")) "the old contract has no such field")
    (spit (str (fs/path p "model.edn"))
          (pr-str (-> (model "NoteDoc" {"type" ["note"]} ["title" "body"]) (assoc :model/version 2)
                      (update :fields conj {:name "status" :type :string :default "open" :description "status"}))))
    (is (true? (:ok (run p "models" "define" "-f" "model.edn"))))
    (is (= "# one\n\nbody of one\n" (slurp (str (fs/path p "one.md")))) "untouched")
    (let [r (cache/lookup! (opened p))]
      (is (= :built (:source r)) "the cache of the old head is not served")
      (is (= (build/build (:store (opened p))) (:index r))))
    (is (= {"title" "one" "body" "body of one" "status" "open"} (payload p "one")))
    (is (= [["one" "open"] ["two" "open"]]
           (mapv (juxt :doc :value) (get-in (run p "fields" "query" "status") [:data :results]))))
    (testing "with the contract unmoved, a rebuilt cache is a hit again"
      (is (true? (:ok (run p "rebuild-indexes"))))
      (is (= :cache (:source (cache/lookup! (opened p)))))
      (is (= "open" (get (payload p "two") "status"))))))

;; v1: test_runtime_cache.py::test_a_hand_edit_is_seen_at_the_start_of_the_next_operation
;; v1: test_runtime_cache.py::test_a_hand_edit_is_seen_by_the_next_sldb_cli_command
(deftest a-hand-edit-is-seen-by-the-next-command
  (let [p (notes)]
    (is (true? (:ok (run p "rebuild-indexes"))) "even with a cached index of the head")
    (is (= [:ok :ok] (mapv :status (:data (run p "docs" "status")))))
    (spit (str (fs/path p "one.md")) "# one\n\nbody of one, changed\n")
    (is (= [{:name "one" :path "one.md" :status :modified} {:name "two" :path "two.md" :status :ok}]
           (:data (run p "docs" "status"))))
    (is (false? (:ok (run p "stores" "check"))) "the store no longer matches its files")
    (is (true? (:ok (run p "docs" "update" "one"))))
    (is (= "body of one, changed" (get (payload p "one") "body")))
    (is (= [["one" "body of one, changed"] ["two" "body of two"]]
           (mapv (juxt :doc :value) (get-in (run p "fields" "query" "body") [:data :results]))))))

;; v1: test_runtime_cache.py::test_a_hand_edit_is_always_seen_by_stores_update
(deftest a-hand-edit-is-always-seen-by-stores-update
  (let [p (notes)]
    (is (true? (:ok (run p "rebuild-indexes"))))
    (reads p)
    (spit (str (fs/path p "one.md")) "# one\n\nbody of one, changed\n")
    (let [e (run p "stores" "update")]
      (is (= [{:document "one" :category :edited :action "kimun docs update one"}] (get-in e [:data :skipped]))
          "seen and named; adopting an edit is a decision, so update names the command instead of taking it")
      (is (= ["docs/file-modified"] (mapv :type (:warnings e)))))
    (is (true? (:ok (run p "docs" "update" "one"))))
    (is (= "body of one, changed" (get (payload p "one") "body")))
    (is (= [] (get-in (run p "stores" "update") [:data :skipped])))))

;; v1: test_runtime_cache.py::test_a_save_that_changes_nothing_leaves_the_file_alone
(deftest a-save-that-changes-nothing-leaves-every-file-alone
  (let [p (notes)]
    (is (true? (:ok (run p "rebuild-indexes"))))
    (let [before (snapshot p)
          moved (fn [] (let [after (snapshot p)] (filterv #(not= (before %) (after %)) (keys (merge before after)))))]
      (is (true? (get-in (run p "docs" "update" "one") [:data :unchanged?])))
      (is (true? (get-in (run p "models" "define" "-f" "model.edn") [:data :unchanged?])))
      (is (true? (:ok (run p "stores" "update"))))
      (is (= [] (moved)) "no revision, no object, no rewritten cache, no rewritten Markdown")
      (testing "a payload equal to the recorded one: the store is left alone and the Markdown keeps its bytes"
        (is (true? (get-in (run p "docs" "update" "one" "--set" "body=body of one") [:data :unchanged?])))
        (is (= [] (remove #{(str (fs/path p "one.md"))} (moved))))
        (is (= "# one\n\nbody of one\n" (slurp (str (fs/path p "one.md")))))
        (is (true? (:ok (run p "rebuild-indexes"))))
        (is (= (update-vals before first) (update-vals (snapshot p) first)) "a rebuild of the same head writes the same bytes")))
    (testing "and a write to one document leaves the other's objects and file alone"
      (let [before (snapshot p)]
        (is (true? (:ok (run p "docs" "update" "one" "--set" "body=new body"))))
        (let [after (snapshot p) same (fn [f] (= (before f) (after f)))]
          (is (every? same (filter #(re-find #"/objects/|two\.md$|store\.edn$" %) (keys before)))
              "objects are written once; two.md and the descriptor are untouched")
          (is (not (same (str (fs/path p "one.md"))))))))))
