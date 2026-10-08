(ns kimun.cli.index-test
  "`kimun rebuild-indexes` end to end on temporary stores (v1 `stores
   semantic-map` and the refresh of `stores update`): the envelope, the file
   it writes, that it commits nothing, and its usage errors."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.commands.index :as cindex]
            [kimun.cli.errors :as errors]
            [kimun.cli.main :as main]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.index.build :as build]
            [kimun.surface.models.store :as mstore]
            [kimun.host.default :as host]
            [kimun.kernel.store :as store]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- opened [p] (cli-store/open! (cli-store/resolve-store {} env p)))
(defn- rebuild [cwd & tokens]
  (errors/wrap #(cindex/dispatch (first tokens) (vec (rest tokens)) {:format "json" :actor "human/tester"} {:env env :cwd cwd})))

(defn- ticket [fields template]
  {:model/name "TicketDoc" :model/version 1 :base [] :semantics {"type" ["ticket"]}
   :fields (into [{:name "title" :type :string :required true :description "Title."}] fields)
   :template template})

(defn- project []
  (let [p (str (ktu/temp-dir "kimun-index-cli-"))]
    (run p "stores" "init" "--name" "p")
    (spit (str (fs/path p "ticket.edn")) (pr-str (ticket [] "# ⸢rev•title⸥\n")))
    (is (true? (:ok (run p "models" "define" "-f" "ticket.edn"))))
    (is (true? (:ok (run p "docs" "create" "TicketDoc" "login" "--set" "title=Broken login"))))
    p))

;; v1: test_update_store_indexes_reports_a_complete_refresh (through the CLI)
(deftest rebuild-indexes-writes-the-cache-of-the-head
  (let [p (project) head (:head (:store (opened p)))
        journal (get-in (run p "journal" "show") [:data])
        e (rebuild p)
        path (fs/path p ".kimun" "index" (str head ".edn"))]
    (is (true? (:ok e)) (pr-str e))
    (is (= 0 (out/exit-code e)))
    (is (= "rebuild-indexes" (:command e)))
    (is (= "p" (get-in e [:store :name])))
    (is (= head (:revision e)))
    (is (= {:head head :cache (str (fs/path "index" (str head ".edn"))) :documents 1 :models 1
            :skipped [] :complete true :pruned 0}
           (dissoc (:data e) :bytes :tags)))
    (is (pos? (get-in e [:data :tags])))
    (is (= [] (:warnings e)))
    (is (fs/regular-file? path))
    (is (= (build/build (:store (opened p))) (edn/read-string (slurp (str path)))))
    (testing "it commits nothing: same head, same journal"
      (is (= head (:head (:store (opened p)))))
      (is (= journal (get-in (run p "journal" "show") [:data]))))
    (testing "running it again is the same answer"
      (is (= (:data e) (:data (rebuild p)))))
    (is (true? (:ok (run p "stores" "check"))))))

;; v1: test_update_store_indexes_skips_a_missing_document (CLI half)
(deftest rebuild-indexes-names-the-documents-it-could-not-read
  (let [p (project)
        {:keys [backend] s :store} (opened p)
        r (mstore/define-plan host/host s (ticket [{:name "owner" :type :string :required true :description "Owner."}]
                                                  "# ⸢rev•title⸥\n\nOwner: ⸢rev•owner⸥\n")
                              {:actor "human/tester" :timestamp "2026-01-01T00:00:00.000Z"})
        _ (store/commit! backend s (:plan r))
        e (rebuild p)]
    (is (true? (:ok e)) "the cache it wrote is exact; v1 exited 1 here")
    (is (= ["login"] (get-in e [:data :skipped])))
    (is (false? (get-in e [:data :complete])))
    (is (= [{:type "index/skipped-document" :document "login" :model "TicketDoc"}]
           (mapv #(dissoc % :message) (:warnings e))) "never silent")
    (is (string? (:message (first (:warnings e)))))))

(deftest rebuild-indexes-usage-and-missing-store
  (let [p (project)]
    (let [e (rebuild p "now")]
      (is (false? (:ok e)))
      (is (= "cli/usage" (get-in e [:error :type])))
      (is (= 6 (out/exit-code e))))
    (is (not (fs/exists? (fs/path p ".kimun" "index"))) "a refused command writes nothing"))
  (let [e (rebuild (str (ktu/temp-dir "kimun-nostore-")))]
    (is (= "cli/no-store" (get-in e [:error :type])))
    (is (= 5 (out/exit-code e)))))
