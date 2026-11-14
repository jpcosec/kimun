(ns kimun.cli.generic-docs-test
  "K3: a document without a model is registered under the generic class of the
   Markdown profile — external anchor, class `generic/md`, root hash — and its
   tree is cache, like a typed document with a file. It is then watched by
   `stores sense`. Stores that keep the text (`:inline`) and files outside the
   project keep the persisted tree."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.host.store-cache :as store-cache]
            [kimun.host.treesitter :as ts]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.markdown.ast :as ast]
            [kimun.test-util :as ktu]))

(def env {"USER" "tester"})

(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- file [p & parts] (str (apply fs/path p parts)))

(def text-1 "# Title\n\nPara one.\n\n- a\n- b\n")
(def text-reformatted "# Title\n\n\nPara one.\n\n- a\n- b\n")
(def text-edited "# Title\n\nPara two.\n\n- a\n- b\n")

(defn- project
  "A by-reference store with `notes/plain.md` ingested as `plain`:
   `[project ingest-envelope]`."
  [& init-args]
  (let [p (str (ktu/temp-dir "kimun-generic-"))]
    (is (true? (:ok (apply run p "stores" "init" "--name" "p" init-args))))
    (fs/create-dirs (file p "notes"))
    (spit (file p "notes" "plain.md") text-1)
    [p (binding [ast/*parser* "native"] (run p "docs" "ingest" "notes/plain.md"))]))

(defn- signals [p]
  (binding [ast/*parser* (or ast/*parser* "native")]
    (mapv (fn [s] [(:type s) (:document s) (:needs-human s) (:action s)])
          (get-in (run p "stores" "sense") [:data :signals]))))

(defn- opened [p] (:store (cli-store/open! (file p ".kimun"))))

(def update-cmd "kimun docs update notes/plain.md --name plain")

(deftest a-document-without-a-model-is-registered-under-the-generic-class
  (let [[p e] (project)
        tree (get-in e [:data :tree])]
    (is (true? (:ok e)) (pr-str e))
    (is (= {:name "plain" :blocks 3 :class "generic/md" :anchor "notes/plain.md"}
           (select-keys (:data e) [:name :blocks :class :anchor])))
    (testing "the store holds the registration, not the blocks"
      (let [store (opened p)
            doc (tdocs/generic-document store tree)]
        (is (= ["plain" "notes/plain.md" "notes/plain.md" "generic/md" true]
               ((juxt :name :path :qualified :class :generic) doc)))
        (is (re-matches #"sha-256:[0-9a-f]{64}" (:fingerprint doc)))
        (is (string? (:merkle-root doc)))
        (is (= "generic/md" (:class (:parse-contract doc))))
        (is (true? (tdocs/cached-tree? store tree)))
        (is (= [] (tdocs/documents store)) "it is not a typed document")))
    (is (= {:add-node 2 :register-tree 1 :add-edge 1}
           (:ops (first (:data (run p "journal" "show" "--limit" "1")))))
        "root and anchor, the registration and the anchor edge: no block or ownership op")
    (is (= [{:name "plain" :tree tree :class "generic/md"}]
           (mapv #(dissoc % :root) (:data (run p "docs" "list")))))
    (is (= text-1 (:data (run p "docs" "render" "plain"))))
    (is (true? (:ok (run p "stores" "verify"))))
    (is (true? (:ok (run p "stores" "check"))))))

(deftest the-tree-is-cache-and-is-rebuilt-from-the-file
  (let [[p] (project)]
    (fs/delete-tree (file p ".kimun" store-cache/dirname))
    (is (= text-1 (:data (run p "docs" "render" "plain"))))
    (is (= :document (get-in (run p "docs" "show" "plain" "--ast") [:data :ast :type])))
    (is (true? (:ok (run p "stores" "verify"))))))

(deftest a-generic-document-is-sensed
  (let [[p] (project)]
    (is (= [1 true []]
           (let [d (:data (run p "stores" "sense"))] [(:tracked d) (:valid d) (:signals d)])))
    (testing "text changed, structure identical"
      (spit (file p "notes" "plain.md") text-reformatted)
      (is (= [[:source/reformatted "plain" false update-cmd]] (signals p)))
      (is (true? (get-in (run p "stores" "sense") [:data :valid]))))
    (testing "structure changed"
      (spit (file p "notes" "plain.md") text-edited)
      (is (= [[:source/mutated "plain" true update-cmd]] (signals p)))
      (is (false? (get-in (run p "stores" "sense") [:data :valid]))))
    (testing "file gone"
      (fs/move (file p "notes" "plain.md") (file p "notes" "moved.md"))
      (is (= [[:source/missing "plain" true nil]] (signals p)))
      (fs/move (file p "notes" "moved.md") (file p "notes" "plain.md")))
    (testing "same file, another parser: the contract, not the source"
      (spit (file p "notes" "plain.md") text-1)
      (with-redefs [ts/available? (constantly true)
                    ts/version (constantly {:pod "pod-kimun-treesitter" :version "9.9.9" :tree_sitter "0.25"})]
        (binding [ast/*parser* "treesitter"]
          (let [s (first (get-in (run p "stores" "sense") [:data :signals]))]
            (is (= [:contract/changed true [:parser :grammar]]
                   [(:type s) (get-in s [:detail :content-ok]) (get-in s [:detail :contract :changed])]))))))
    (is (= [] (signals p)))))

(deftest stores-update-repairs-what-needs-no-decision
  (let [[p] (project)
        head #(:head (opened p))]
    (spit (file p "notes" "plain.md") text-reformatted)
    (let [before (head)
          u (run p "stores" "update")]
      (is (= ["plain"] (get-in u [:data :updated])))
      (is (not= before (head)))
      (is (= text-reformatted (slurp (file p "notes" "plain.md"))) "the file is not rewritten")
      (is (= [] (signals p))))
    (testing "an edit is left for a person"
      (spit (file p "notes" "plain.md") text-edited)
      (let [before (head)
            u (run p "stores" "update")]
        (is (= [[] [{:document "plain" :category :edited :action update-cmd}]]
               [(get-in u [:data :updated]) (get-in u [:data :skipped])]))
        (is (= before (head)))))))

(deftest docs-update-registers-the-file-again
  (let [[p e] (project)
        tree (get-in e [:data :tree])]
    (spit (file p "notes" "plain.md") text-edited)
    (let [u (run p "docs" "update" "notes/plain.md" "--name" "plain")]
      (is (= {:name "plain" :tree tree :class "generic/md" :anchor "notes/plain.md" :unchanged? false}
             (select-keys (:data u) [:name :tree :class :anchor :unchanged?])))
      (is (= text-edited (:data (run p "docs" "render" "plain"))))
      (is (= [] (signals p))))
    (testing "an unchanged file writes nothing"
      (let [head (:head (opened p))
            u (run p "docs" "update" "notes/plain.md" "--name" "plain")]
        (is (true? (get-in u [:data :unchanged?])))
        (is (= head (:head (opened p))))))
    (testing "another file re-anchors the document, explicitly"
      (spit (file p "notes" "other.md") text-1)
      (let [u (run p "docs" "update" "notes/other.md" "--name" "plain")]
        (is (= "notes/other.md" (get-in u [:data :anchor])))
        (is (= ["notes/other.md" tree] ((juxt :path :tree) (tdocs/generic-document (opened p) "plain"))))
        (is (= 1 (count (tdocs/generic-documents (opened p)))) "no second document, no stale anchor edge")))
    (is (true? (:ok (run p "stores" "verify"))))))

(deftest a-file-name-starting-with-two-dots-is-inside-the-project
  (let [[p] (project)]
    (spit (file p "..foo.md") text-1)
    (let [e (run p "docs" "ingest" "..foo.md")]
      (is (= ["generic/md" "..foo.md"] [(get-in e [:data :class]) (get-in e [:data :anchor])])))
    (fs/create-dirs (file p "sub"))
    (spit (file p "sub" "..bar.md") text-1)
    (is (= "sub/..bar.md" (get-in (run (file p "sub") "docs" "ingest" "..bar.md" "--store" (file p ".kimun")) [:data :anchor])))))

(deftest what-cannot-be-anchored-keeps-its-persisted-tree
  (testing "a file outside the project"
    (let [[p] (project)
          outside (file (str (ktu/temp-dir "kimun-outside-")) "out.md")]
      (spit outside text-1)
      (let [e (run p "docs" "ingest" outside)]
        (is (true? (:ok e)))
        (is (nil? (get-in e [:data :class])))
        (is (= text-1 (:data (run p "docs" "render" "out"))))
        (is (= ["plain"] (mapv :name (tdocs/generic-documents (opened p)))))
        (testing "and it is updated as before K3"
          (spit outside text-edited)
          (is (true? (:ok (run p "docs" "update" outside "--name" "out"))))
          (is (= text-edited (:data (run p "docs" "render" "out"))))))
      (testing "a registered document is not re-anchored outside the project"
        (let [u (run p "docs" "update" outside "--name" "plain")]
          (is (= "docs/outside-project" (get-in u [:error :type])))))))
  (testing "a store that keeps the text"
    (let [[p e] (project "--content" "inline")]
      (is (true? (:ok e)))
      (is (nil? (get-in e [:data :class])))
      (is (= [] (tdocs/generic-documents (opened p))))
      (is (= 0 (get-in (run p "stores" "sense") [:data :tracked])))
      (is (= text-1 (:data (run p "docs" "render" "plain")))))))

(deftest names-stay-unique-among-documents-without-a-model
  (let [[p] (project)]
    (fs/create-dirs (file p "other"))
    (spit (file p "other" "plain.md") text-1)
    (let [e (run p "docs" "ingest" "other/plain.md")]
      (is (= ["docs/exists" 3] [(get-in e [:error :type]) (:exit e)])))
    (is (true? (:ok (run p "docs" "ingest" "other/plain.md" "--name" "plain2"))))
    (is (= ["plain" "plain2"] (mapv :name (tdocs/generic-documents (opened p)))))
    (is (str/includes? (get-in (run p "docs" "ingest" "other/plain.md") [:error :message]) "already exists"))))

(def ticket
  (pr-str {:model/name "TicketDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "status" :type :string :required true :description "Status."}]
           :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"}))

(deftest one-sensed-path-one-document
  (let [[p e] (project)
        head #(:head (opened p))]
    (testing "the same file under another name"
      (let [before (head)
            dup (run p "docs" "ingest" "notes/plain.md" "--name" "again")]
        (is (= ["docs/exists" 3 "notes/plain.md" "plain" (get-in e [:data :tree])]
               [(get-in dup [:error :type]) (:exit dup) (get-in dup [:error :data :path])
                (get-in dup [:error :data :document]) (get-in dup [:error :data :tree])]))
        (is (= before (head)) "nothing was written")
        (is (= "docs/exists" (get-in (run p "docs" "ingest" "./notes/plain.md" "--name" "again") [:error :type]))
            "the path is compared normalised")))
    (testing "re-anchoring onto a file another document holds"
      (spit (file p "notes" "second.md") text-edited)
      (is (true? (:ok (run p "docs" "ingest" "notes/second.md"))))
      (let [u (run p "docs" "update" "notes/second.md" "--name" "plain")]
        (is (= ["docs/exists" "second"] [(get-in u [:error :type]) (get-in u [:error :data :document])]))))
    (testing "a file a typed document tracks"
      (spit (file p "ticket.edn") ticket)
      (is (true? (:ok (run p "models" "define" "-f" "ticket.edn"))))
      (is (true? (:ok (run p "docs" "create" "TicketDoc" "login" "--set" "title=T" "--set" "status=open"))))
      (let [dup (run p "docs" "ingest" "login.md" "--name" "raw")]
        (is (= ["docs/exists" "login.md" "login"]
               [(get-in dup [:error :type]) (get-in dup [:error :data :path]) (get-in dup [:error :data :document])]))))))
