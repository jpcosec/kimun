(ns kimun.cli.journal-test
  "The `journal` group (`kimun.cli.commands.journal`) end to end on temporary
   stores with real history, called through `dispatch` (the group is wired into
   `kimun.cli.main` at merge): `show` and its filters, `verify`, tampering of an
   object and of the log, and the usage failures."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [kimun.cli.commands.journal :as j]
            [kimun.cli.main :as main]
            [kimun.cli.out :as out]))

(def env {"USER" "tester"})

(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))

(defn- jr
  "Dispatches `journal sub` in `dir` with option map `opts`."
  [dir sub opts & args]
  (j/dispatch sub (vec args) (merge {:format "json" :actor "human/tester"} opts) {:env env :cwd dir}))

(defn- project []
  (let [proj (str (fs/create-temp-dir {:prefix "kimun-cli-journal-"}))]
    (run proj "stores" "init" "--name" "p")
    (spit (str (fs/path proj "doc.md")) "# T\n\npara one\n")
    (run proj "docs" "ingest" "doc.md")
    (spit (str (fs/path proj "doc.md")) "# T\n\npara two\n")
    (run proj "docs" "update" "doc.md" "--name" "doc" "--actor" "kimun/derive")
    (spit (str (fs/path proj "doc.md")) "# T\n\npara three\n")
    (run proj "docs" "update" "doc.md" "--name" "doc")
    proj))

(deftest show-lists-newest-first
  (let [proj (project)
        e (jr proj "show" {})]
    (is (true? (:ok e)) (pr-str e))
    (is (= "journal show" (:command e)))
    (is (= 3 (count (:data e))))
    (is (= (:revision e) (get-in e [:data 0 :revision])) "the newest entry is the head")
    (is (= {:before ["para two"] :after ["para three"]} (get-in e [:data 0 :changes 0 :text])))))

(deftest show-filters
  (let [proj (project)]
    (is (= 2 (count (:data (jr proj "show" {:limit "2"})))))
    (is (= 2 (count (:data (jr proj "show" {:limit 2})))))
    (is (= 1 (count (:data (jr proj "show" {:by "kimun/derive"})))))
    (is (= 3 (count (:data (jr proj "show" {:tree "doc"})))))
    (is (= [] (:data (jr proj "show" {:tree "nope"}))))
    (is (= 1 (count (:data (jr proj "show" {:tree "doc" :by "kimun/derive" :limit "5"})))))))

(deftest bad-limit-is-usage
  (let [proj (project)]
    (doseq [v ["0" "-1" "abc" "1.5"]]
      (is (= :cli/usage (:type (ex-data (try (jr proj "show" {:limit v}) (catch Exception e e)))))
          (str "limit " v)))))

(deftest verify-passes
  (let [proj (project)
        e (jr proj "verify" {})]
    (is (true? (:ok e)) (pr-str e))
    (is (= "journal verify" (:command e)))
    (is (= 3 (get-in e [:data :revisions])))
    (is (= [] (get-in e [:data :bad])))))

(deftest verify-on-empty-store
  (let [proj (str (fs/create-temp-dir {:prefix "kimun-cli-journal-"}))]
    (run proj "stores" "init" "--name" "p")
    (is (= [] (:data (jr proj "show" {}))))
    (is (true? (:ok (jr proj "verify" {}))))))

;; Tampering: a corrupted object or log line is the typed failure store/corrupt, exit 5.
(deftest verify-detects-corrupt-object
  (let [proj (project)
        objs (fs/list-dir (fs/path proj ".kimun" "objects"))
        victim (first (sort-by str objs))]
    (spit (str victim) (str (slurp (str victim)) " tampered"))
    (let [e (jr proj "verify" {})]
      (is (false? (:ok e)))
      (is (= "store/corrupt" (get-in e [:error :type])))
      (is (= 5 (:exit e)))
      (is (seq (get-in e [:error :data :bad]))))))

(deftest verify-detects-corrupt-log
  (let [proj (project)
        log (str (fs/path proj ".kimun" "log.edn"))
        lines (str/split-lines (slurp log))]
    (testing "a log line altered"
      (spit log (str/join "\n" (cons (str/replace (first lines) "human/tester" "human/mallory") (rest lines))))
      (let [e (jr proj "verify" {})]
        (is (= "store/corrupt" (get-in e [:error :type])) (pr-str e))
        (is (= 5 (:exit e)))))
    (testing "a log line unreadable"
      (spit log (str/join "\n" (cons "{not edn" (rest lines))))
      (let [e (jr proj "verify" {})]
        (is (= "store/corrupt" (get-in e [:error :type])) (pr-str e))
        (is (= 5 (:exit e)))))))

(deftest group-surface
  (is (= #{"show" "verify"} (set (keys j/commands))))
  (is (= #{:limit :by :tree} (set (keys j/spec))))
  (let [e (j/dispatch "nope" [] {} {:env env :cwd "."})]
    (is (= "cli/usage" (get-in e [:error :type])))
    (is (= 6 (:exit e))))
  (is (= 6 (:exit (j/dispatch nil [] {} {:env env :cwd "."})))))

(deftest no-store-is-typed
  (let [dir (str (fs/create-temp-dir {:prefix "kimun-cli-journal-"}))
        ex (try (jr dir "show" {}) (catch Exception e e))]
    (is (= :cli/no-store (:type (ex-data ex))))))
