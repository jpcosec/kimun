(ns kimun.cli.journal-test
  "The `journal` group (`kimun.cli.commands.journal`) end to end on temporary
   stores with real history, called through `dispatch` (the group is wired into
   `kimun.cli.main` at merge): `show` and its filters, `verify`, tampering of an
   object and of the log, and the usage failures."
  (:require [kimun.test-util :as ktu] [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [kimun.cli.commands.journal :as j]
            [kimun.cli.errors :as errors]
            [kimun.cli.store :as cstore]
            [kimun.kernel.store :as kstore]
            [kimun.cli.main :as main]
            [kimun.cli.out :as out]))

(def env {"USER" "tester"})

(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))

(defn- jr
  "Dispatches `journal sub` in `dir` with option map `opts`."
  [dir sub opts & args]
  (j/dispatch sub (vec args) (merge {:format "json" :actor "human/tester"} opts) {:env env :cwd dir}))

(defn- project []
  (let [proj (str (ktu/temp-dir "kimun-cli-journal-"))]
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
  (let [proj (str (ktu/temp-dir "kimun-cli-journal-"))]
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


(defn- history
  "A project whose store has three committed transactions; returns
   {:proj :dir :log (path) :lines (log lines) :revs [r1 r2 r3] :heads (path)
    :heads-at [heads.edn text after each commit]}."
  []
  (let [proj (str (ktu/temp-dir "kimun-cli-journal-"))
        dir (str (fs/path proj ".kimun"))
        log (str (fs/path dir "log.edn"))]
    (run proj "stores" "init" "--name" "p")
    (let [heads (str (fs/path dir "heads.edn"))
          heads-at (vec (for [text ["one" "two" "three"]]
                          (do (spit (str (fs/path proj "doc.md")) (str "# T\n\npara " text "\n"))
                              (if (= "one" text) (run proj "docs" "ingest" "doc.md") (run proj "docs" "update" "doc.md" "--name" "doc"))
                              (slurp heads))))
          lines (str/split-lines (slurp log))]
      {:proj proj :dir dir :log log :lines lines :heads heads :heads-at heads-at
       :revs (mapv #(:revision (edn/read-string %)) lines)})))

(defn- point-heads-at!
  "Puts back the heads.edn the store had when `rev` was its head: what a
   writer that died before replacing the heads (or a heads.edn restored from
   an older copy) leaves behind. (Format 2 records the per-tree heads as an
   mmap root, so an older head needs the older root with it.)"
  [{:keys [heads revs heads-at]} rev]
  (spit heads (heads-at (.indexOf revs rev))))

(defn- forge-head!
  "Rewrites only the :head of heads.edn to `rev`, leaving the per-tree heads of
   the last commit: a heads.edn no commit ever wrote."
  [{:keys [heads revs]} rev]
  (spit heads (str/replace (slurp heads) (peek revs) rev)))

;; F (kernel-scale): `journal verify` replays the log (store/replay-open), then checks the DAG and the hashes.
(deftest verify-detects-a-truncated-log-and-heads-that-disagree-with-it
  (testing "the last log line cut short"
    (let [{:keys [proj log lines]} (history)
          text (str (str/join "\n" lines) "\n")]
      (spit log (subs text 0 (- (count text) (quot (count (peek lines)) 2))))
      (let [e (jr proj "verify" {})]
        (is (= ["store/corrupt" 5] [(get-in e [:error :type]) (:exit e)]) (pr-str e))
        (is (= [:replay-failed] (mapv :reason (get-in e [:error :data :bad])))))))
  (testing "heads.edn two transactions behind the end of the log"
    (let [{:keys [proj revs] :as h} (history)]
      (point-heads-at! h (first revs))
      (let [e (jr proj "verify" {})]
        (is (= ["store/corrupt" 5] [(get-in e [:error :type]) (:exit e)]) (pr-str e))
        (is (= [:replay-failed] (mapv :reason (get-in e [:error :data :bad])))))))
  (testing "heads one transaction behind are a writer dead mid-commit: reported, not a failure"
    (let [{:keys [proj revs] :as h} (history)]
      (point-heads-at! h (second revs))
      (let [e (jr proj "verify" {})]
        (is (true? (:ok e)) (pr-str e))
        (is (= 2 (get-in e [:data :revisions])))
        (is (= (peek revs) (get-in e [:data :uncommitted-tail :revision]))))))
  (testing "the head one behind with the last commit's per-tree heads is no dead writer: it does not replay"
    (let [{:keys [proj revs] :as h} (history)]
      (forge-head! h (second revs))
      (let [e (jr proj "verify" {})]
        (is (= ["store/corrupt" 5] [(get-in e [:error :type]) (:exit e)]) (pr-str e))
        (is (= [:replay-failed] (mapv :reason (get-in e [:error :data :bad]))))))))

(deftest group-surface
  (is (= #{"show" "verify"} (set (keys j/commands))))
  (is (= #{:limit :by :tree} (set (keys j/spec))))
  (let [e (j/dispatch "nope" [] {} {:env env :cwd "."})]
    (is (= "cli/usage" (get-in e [:error :type])))
    (is (= 6 (:exit e))))
  (is (= 6 (:exit (j/dispatch nil [] {} {:env env :cwd "."})))))

(deftest no-store-is-typed
  (let [dir (str (ktu/temp-dir "kimun-cli-journal-"))
        ex (try (jr dir "show" {}) (catch Exception e e))]
    (is (= :cli/no-store (:type (ex-data ex))))))

;; Two writers that opened the same heads: the loser is store/stale (exit 4), wrote
;; no log line, and the store stays readable; a writer dead mid-commit is reported.
(deftest a-losing-writer-is-stale-and-leaves-the-store-readable
  (let [proj (project)
        dir (str (fs/path proj ".kimun"))
        a (cstore/open! dir)
        b (cstore/open! dir)
        actor (:actor (:plan (peek (kstore/read-log (:backend a)))))
        plan (fn [s i]
               {:plan/version 1 :engines {} :base (:head s) :actor actor :timestamp "2026-08-29T12:00:00.000Z"
                :ops [{:op :add-node :node {:class :sign :kind :block :content {:format :markdown :type :document :attrs {:name (str "w" i)}}} :as :r}
                      {:op :new-tree :tree {:kind :document :name (str "w" i)} :id (str "01ARZ3NDEKTSV4RRFFQ69G000" i) :root :r}]})
        _ (kstore/commit! (:backend a) (:store a) (plan (:store a) 1))
        log-before (slurp (str (fs/path dir "log.edn")))
        e (try (kstore/commit! (:backend b) (:store b) (plan (:store b) 2)) nil
               (catch clojure.lang.ExceptionInfo e (ex-data e)))]
    (is (= :store/stale (:type e)))
    (is (= 4 (errors/exit-for (:type e))))
    (is (= log-before (slurp (str (fs/path dir "log.edn")))) "no line from the loser")
    (is (true? (:ok (jr proj "verify" {}))))
    (is (true? (:ok (run proj "stores" "verify"))))
    (testing "the retry on a fresh open! succeeds, and the CLI keeps writing"
      (let [c (cstore/open! dir)]
        (kstore/commit! (:backend c) (:store c) (plan (:store c) 2)))
      (spit (str (fs/path proj "doc.md")) "# T\n\npara after the race\n")
      (is (true? (:ok (run proj "docs" "update" "doc.md" "--name" "doc"))))
      (is (true? (:ok (jr proj "verify" {})))))
    (testing "a line left by a writer that died before recording its heads is reported, not fatal"
      (let [lines (count (kstore/read-log (:backend a)))]
        (spit (str (fs/path dir "log.edn")) "{:id \"dead\" :plan" :append true)
        (let [v (jr proj "verify" {})]
          (is (true? (:ok v)) (pr-str v))
          (is (= {:torn-line (inc lines)} (get-in v [:data :uncommitted-tail]))))
        (is (= {:torn-line (inc lines)} (get-in (run proj "stores" "verify") [:data :uncommitted-tail])))
        (is (true? (:ok (jr proj "show" {}))))
        (spit (str (fs/path proj "doc.md")) "# T\n\npara after the crash\n")
        (is (true? (:ok (run proj "docs" "update" "doc.md" "--name" "doc"))) "the next commit discards it")
        (is (= (inc lines) (count (kstore/read-log (:backend a)))))
        (is (nil? (get-in (jr proj "verify" {}) [:data :uncommitted-tail])))))))
