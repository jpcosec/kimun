(ns kimun.cli.upgrade-test
  "`kimun stores upgrade` end to end: a format-1 store written by the CLI
   through the legacy engine answers `docs list`, `docs show`, `journal show`
   and `stores verify` the same after the upgrade (revision and transaction
   ids read through the map the upgrade writes), keeps a backup, and a second
   upgrade is a no-op."
  (:require [kimun.test-util :as ktu] [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [babashka.fs :as fs]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.host.default :as host]
            [kimun.kernel.store :as store]))

(def env {"USER" "tester"})

(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))

(defn- format-1-project
  "A project whose `.kimun` is a format-1 store (created by the kernel with
   {:format-version 1} and the descriptor `stores init` writes), then written
   by the CLI: two untyped documents and their edits, a model, a typed
   document and a field change."
  []
  (let [proj (str (ktu/temp-dir "kimun-cli-upgrade-"))
        dir (str (fs/path proj ".kimun"))
        b (cli-store/backend dir)]
    (fs/create-dirs dir)
    (store/init! b host/host {"human/tester" :all "kimun/derive" :all "kimun/materialize" :all} {:format-version 1})
    (store/write-descriptor! b (assoc (store/read-descriptor b) :name "p" :links []))
    (spit (str (fs/path proj "a.md")) "# A\n\npara one, see [[b]]\n")
    (spit (str (fs/path proj "b.md")) "# B\n\nother\n")
    (spit (str (fs/path proj "note.edn"))
          (pr-str {:model/name "NoteDoc" :model/version 1 :base []
                   :fields [{:name "title" :type :string :required true :description "Title."}]
                   :template "# ⸢rev•title⸥\n"}))
    (doseq [argv [["docs" "ingest" "a.md"] ["docs" "ingest" "b.md"] ["models" "define" "-f" "note.edn"]
                  ["docs" "create" "NoteDoc" "pan" "--set" "title=Cocina del pan"]
                  ["fields" "set" "pan" "title" "Pan de masa madre"]]]
      (is (:ok (apply run proj argv))))
    (spit (str (fs/path proj "a.md")) "# A\n\npara two, see [[b]]\n\n- item\n")
    (is (:ok (run proj "docs" "update" "a.md" "--name" "a")))
    (spit (str (fs/path proj "b.md")) "# B\n\nother, edited\n")
    (is (:ok (run proj "docs" "update" "b.md" "--name" "b" "--actor" "kimun/derive")))
    proj))

(defn- answers [proj]
  {:list (run proj "docs" "list")
   :show-a (run proj "docs" "show" "a" "--ast")
   :show-b (run proj "docs" "show" "b" "--ast")
   :show-pan (run proj "docs" "show" "pan")
   :fields (run proj "fields" "get" "pan" "title")
   :journal (run proj "journal" "show")
   :verify (run proj "stores" "verify")})

(defn- renamed
  "`x` with every string that is an old id replaced by its new id (`m`)."
  [m x]
  (walk/postwalk #(if (and (string? %) (contains? m %)) (m %) %) x))

(deftest a-format-1-store-answers-the-same-after-the-upgrade
  (let [proj (format-1-project)
        dir (str (fs/path proj ".kimun"))
        before (answers proj)
        _ (is (= 1 (:format-version (store/read-descriptor (cli-store/backend dir)))))
        e (run proj "stores" "upgrade")
        ids (edn/read-string (slurp (str (fs/path dir "upgrade-format-1.edn"))))
        m (merge (:revisions ids) (:transaction-ids ids))
        after (answers proj)]
    (is (true? (:ok e)) (pr-str e))
    (is (true? (get-in e [:data :upgraded])))
    (is (= (count (store/read-log (cli-store/backend (get-in e [:data :backup])))) (get-in e [:data :transactions])))
    (is (= 2 (:format-version (store/read-descriptor (cli-store/backend dir)))))
    (is (= (get-in e [:data :head-before]) (:head-before ids)))
    (is (= (:revision e) (:head ids) (m (:head-before ids))))
    (testing "the backup is the untouched format-1 store"
      (let [bk (get-in e [:data :backup])]
        (is (str/starts-with? (str (fs/file-name bk)) ".kimun.format-1-"))
        (is (= 1 (:format-version (store/read-descriptor (cli-store/backend bk)))))
        (is (:ok? (store/verify-full (cli-store/backend bk) host/host)))))
    (is (not-any? #(str/includes? (str (fs/file-name %)) ".upgrade-") (fs/list-dir proj)) "no build directory left")
    (doseq [k [:list :show-a :show-b :show-pan :fields :journal]]
      (is (true? (:ok (k before))) (pr-str (k before)))
      (is (= (renamed m (k before)) (k after)) (str k)))
    (is (true? (:ok (:verify after))) (pr-str (:verify after)))
    (is (= (dissoc (renamed m (:verify before)) :data) (dissoc (:verify after) :data)))
    (is (= (get-in before [:verify :data :bad]) (get-in after [:verify :data :bad])))
    (testing "a second upgrade moves format 2 to format 3 in place, and a third is a no-op"
      (spit (str (fs/path proj "a.md")) "# A\n\npara three\n")
      (is (true? (:ok (run proj "docs" "update" "a.md" "--name" "a"))))
      (let [log-before (mapv #(contains? % :plan) (store/read-log (cli-store/backend dir)))
            e2 (run proj "stores" "upgrade")]
        (is (true? (:ok e2)) (pr-str e2))
        (is (true? (get-in e2 [:data :upgraded])))
        (is (= 2 (get-in e2 [:data :from-format])))
        (is (= 3 (get-in e2 [:data :format-version])))
        (is (every? true? log-before) "the format-2 log carried the plans")
        (is (= 3 (:format-version (store/read-descriptor (cli-store/backend dir)))))
        (is (not-any? #(contains? % :plan) (store/read-log (cli-store/backend dir)))
            "format-3 lines carry {:id :revision} only"))
      (is (true? (:ok (run proj "stores" "verify"))))
      (let [e3 (run proj "stores" "upgrade")]
        (is (true? (:ok e3)) (pr-str e3))
        (is (= {:upgraded false :format-version 3 :path dir} (:data e3)))))))
