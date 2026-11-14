(ns kimun.cli.sense-test
  "`kimun stores sense [--once]` end to end: one read-only observation over
   tracked documents, structured signals from the existing diagnostics, and no
   writes to the store. The first cut does not promise a heartbeat daemon."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.test-util :as ktu]))

(def env {"USER" "tester"})

(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- file [p & parts] (str (apply fs/path p parts)))
(defn- store-dir [p] (file p ".kimun"))

(def ticket
  (pr-str {:model/name "TicketDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "status" :type :string :required true :description "Status."}]
           :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"}))

(defn- project
  "A project with TicketDoc and the tracked document `login` (login.md)."
  []
  (let [p (str (ktu/temp-dir "kimun-sense-"))]
    (is (true? (:ok (run p "stores" "init" "--name" "p"))))
    (spit (file p "ticket.edn") ticket)
    (is (true? (:ok (run p "models" "define" "-f" "ticket.edn"))))
    (is (true? (:ok (run p "docs" "create" "TicketDoc" "login"
                          "--set" "title=T" "--set" "status=open"))))
    p))

(defn- head [p]
  (:head (:store (cli-store/open! (store-dir p)))))

(defn- sense [p & argv]
  (:data (apply run p "stores" "sense" argv)))

(deftest one-observation-is-ok-and-writes-nothing
  (let [p (project)
        before-head (head p)
        before-log (slurp (file p ".kimun" "log.edn"))
        data (sense p "--once")]
    (is (= :once (:mode data)))
    (is (string? (:observed-at data)))
    (is (= [1 true [] "no signals"]
           [(:tracked data) (:valid data) (:signals data) (:summary data)]))
    (is (= {:total 0 :by-type {} :by-severity {}} (:counts data)))
    (is (= before-head (head p)) "the observation does not move the head")
    (is (= before-log (slurp (file p ".kimun" "log.edn"))) "the observation does not append to the log")))

(deftest signals-carry-the-diagnostic-category-and-the-repair
  (let [p (project)]
    (spit (file p "login.md") "# T\n\nStatus: closed\n")
    (let [data (sense p "--once")
          s (first (:signals data))]
      (is (false? (:valid data)))
      (is (= 1 (:tracked data)))
      (is (= [{:type :source/mutated :category :edited :severity :warning
               :document "login" :model "TicketDoc" :path "login.md"
               :needs-human true :action "kimun docs update login"}]
             (mapv #(select-keys % [:type :category :severity :document :model :path
                                    :needs-human :action])
                   (:signals data))))
      (is (= ["status"] (get-in s [:detail :fields])))
      (is (= 1 (get-in data [:counts :by-type :source/mutated])))
      (is (str/includes? (:summary data) "1 warning")))))

(deftest missing-source-is-a-signal-not-a-crash
  (let [p (project)]
    (fs/delete (file p "login.md"))
    (let [data (sense p "--once")]
      (is (= [:source/missing :missing true]
             (let [s (first (:signals data))]
               [(:type s) (:category s) (:needs-human s)])))
      (is (= 1 (get-in data [:counts :by-type :source/missing]))))))

(deftest json-output-is-machine-readable
  (let [p (project)
        e (run p "stores" "sense" "--once")
        j (json/parse-string (out/emit e "json") true)]
    (is (true? (:ok j)))
    (is (= "stores sense" (:command j)))
    (is (= "once" (get-in j [:data :mode])))
    (is (vector? (get-in j [:data :signals])))
    (is (string? (get-in j [:data :summary])))))
