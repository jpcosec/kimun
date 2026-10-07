(ns kimun.parity.durability-test
  "v1 atomic index writes and store-name-relative paths, as the promises that
   survive in kimun: a write leaves no temporary file and creates the
   directories it needs, and nothing in the store depends on where the store
   directory is or what it is called. Each test names its v1 function."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))

(def note
  (pr-str {:model/name "Note" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "body" :type :string :required true :description "Body."}]
           :template "# ⸢rev•title⸥\n\n## Body\n\n⸢rev•body⸥\n"}))

(defn- project []
  (let [p (str (ktu/temp-dir "kimun-durability-"))]
    (run p "stores" "init" "--name" "kb")
    (spit (str (fs/path p "note.edn")) note)
    (is (true? (:ok (run p "models" "define" "-f" "note.edn"))))
    p))

(defn- files [dir] (filter fs/regular-file? (fs/glob dir "**" {:hidden true})))

;; v1: store/test_update_fault_tolerance.py::test_atomic_write_cleans_up_temp_file
(deftest a-write-leaves-no-temporary-file-behind
  (let [p (project)]
    (is (true? (:ok (run p "docs" "create" "Note" "x" "--set" "title=Hola" "--set" "body=Mundo"))))
    (is (true? (:ok (run p "docs" "update" "x" "--set" "body=Otro"))))
    (is (true? (:ok (run p "rebuild-indexes"))))
    (is (= [] (filter #(str/includes? (str (fs/file-name %)) ".tmp") (files p))))
    ;; `lock`: the empty file of the commit lock (docs/v2/10-escala.md §7), created by the first commit
    (is (= #{"store.edn" "heads.edn" "log.edn" "objects" "index" "lock"}
           (set (map #(str (fs/file-name %)) (fs/list-dir (fs/path p ".kimun"))))))
    (is (every? #(re-matches #"[0-9a-f]{64}" (str (fs/file-name %))) (fs/list-dir (fs/path p ".kimun" "objects"))))
    (is (= "# Hola\n\n## Body\n\nOtro\n" (slurp (str (fs/path p "x.md")))) "the file holds the last write, whole")
    (is (true? (:ok (run p "stores" "verify"))))))

;; v1: store/test_update_fault_tolerance.py::test_atomic_write_creates_parent_dirs
(deftest a-write-creates-the-directories-it-needs
  (let [base (str (ktu/temp-dir "kimun-durability-"))
        store (str (fs/path base "a" "b" "c"))]
    (is (true? (:ok (run base "stores" "init" "--name" "deep" "--store" store))))
    (is (fs/regular-file? (fs/path store "store.edn")))
    (is (true? (:ok (run base "stores" "check" "--store" store))))
    (let [p (project)]
      (is (true? (:ok (run p "docs" "create" "Note" "x" "--set" "title=T" "--set" "body=B" "--path" "d/e/f/x.md"))))
      (is (= "# T\n\n## Body\n\nB\n" (slurp (str (fs/path p "d" "e" "f" "x.md")))))
      (is (not (fs/exists? (fs/path p ".kimun" "index"))))
      (is (true? (:ok (run p "rebuild-indexes"))))
      (is (fs/directory? (fs/path p ".kimun" "index"))))))

;; v1: store/test_store_name_relpaths.py::test_renamed_store_stays_fully_readable
(deftest a-renamed-store-stays-fully-readable
  (let [p (str (ktu/temp-dir "kimun-durability-"))
        store (str (fs/path p ".kimun_custom"))
        at (fn [& argv] (apply run p (concat argv ["--store" store])))]
    (run p "stores" "init" "--name" "kb")
    (fs/move (fs/path p ".kimun") store)
    (spit (str (fs/path p "note.edn")) note)
    (is (true? (:ok (at "models" "define" "-f" "note.edn"))))
    (is (true? (:ok (at "docs" "create" "Note" "x2" "--set" "title=Hola" "--set" "body=Mundo"))))
    (is (= [["x2" "Note" "x2.md" :intact]] (mapv (juxt :name :model :path :state) (:data (at "docs" "list")))))
    (is (= {"title" "Hola" "body" "Mundo"} (get-in (at "docs" "show" "x2") [:data :payload])))
    (is (true? (:ok (at "stores" "check"))))
    (is (seq (fs/list-dir (fs/path store "objects"))) "the objects went under the real store directory")
    (is (not (fs/exists? (fs/path p ".kimun"))) "no phantom default-named store was created")
    (testing "and moving the whole project keeps every document resolvable"
      (is (true? (:ok (at "rebuild-indexes"))))
      (let [listed (:data (at "docs" "list"))
            found (:data (at "find" "Hola"))
            q (str (fs/path (ktu/temp-dir "kimun-durability-") "elsewhere" "kb"))
            _ (fs/create-dirs (fs/parent q))
            _ (fs/move p q)
            at2 (fn [& argv] (apply run q (concat argv ["--store" (str (fs/path q ".kimun_custom"))])))]
        (is (= listed (:data (at2 "docs" "list"))))
        (is (= [{:name "x2" :path "x2.md" :status :ok}] (:data (at2 "docs" "status"))))
        (is (true? (:ok (at2 "stores" "check"))))
        (is (some #(= "x2" (:name %)) found))
        (is (= found (:data (at2 "find" "Hola"))) "served from the index cache that moved with it")
        (is (not-any? #(str/includes? (slurp (str %)) (str (fs/file-name (fs/parent p))))
                      (files (fs/path q ".kimun_custom")))
            "no file of the store names the directory it was created in")))))
