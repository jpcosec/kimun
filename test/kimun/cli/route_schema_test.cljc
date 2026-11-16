(ns kimun.cli.route-schema-test
  "K2a C5 from the CLI: a new store is of route schema 2 (the Merkle tree by
   path) and writes through it; a store without that schema is refused with
   the declared `store/unsupported-format` error."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.host.default :as host]
            [kimun.kernel.route :as route]
            [kimun.kernel.route-tree :as route-tree]
            [kimun.surface.markdown.ast :as ast]
            [kimun.test-util :as ktu]))

(def env {"USER" "tester"})
(def h host/host)

(defn- run [cwd & argv] (binding [ast/*parser* "native"] (:envelope (main/run argv env cwd))))
(defn- file [p & parts] (str (apply fs/path p parts)))
(defn- opened [p] (:store (cli-store/open! (file p ".kimun"))))
(defn- entries [p] (vals (:routes (opened p))))
(defn- check [p] (:data (run p "stores" "check")))

(def ticket
  (pr-str {:model/name "TicketDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "status" :type :string :required true :description "Status."}]
           :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"}))

(defn- write-documents!
  "Registers a typed and two generic documents in the store of `p`."
  [p]
  (spit (file p "ticket.edn") ticket)
  (is (true? (:ok (run p "models" "define" "-f" "ticket.edn"))))
  (fs/create-dirs (file p "tickets")) (fs/create-dirs (file p "notes" "deep"))
  (spit (file p "tickets" "login.md") "# T\n\nStatus: open\n")
  (spit (file p "notes" "plain.md") "# Note\n\nPara one.\n")
  (spit (file p "notes" "deep" "other.md") "# Other\n\nPara.\n")
  (is (true? (:ok (run p "docs" "track" "TicketDoc" "tickets/login.md"))))
  (is (true? (:ok (run p "docs" "ingest" "notes/plain.md"))))
  (is (true? (:ok (run p "docs" "ingest" "notes/deep/other.md")))))

(defn- new-project []
  (let [p (str (ktu/temp-dir "kimun-route-schema-"))]
    (is (true? (:ok (run p "stores" "init" "--name" "p"))))
    (write-documents! p)
    p))

(def paths ["notes/deep/other.md" "notes/plain.md" "tickets/login.md"])

(deftest a-new-store-is-of-schema-2-and-says-so
  (let [p (new-project)
        c (check p)]
    (is (= 2 (:route-schema (edn/read-string (slurp (file p ".kimun" "store.edn"))))))
    (is (= 3 (:route-count c)))
    (is (true? (:routes-complete? c)))
    (is (= (:hash (route-tree/from-entries h (entries p))) (:routes-root c)))
    (is (= paths (sort (map :path (entries p)))))))

(deftest writes-go-through-the-tree
  (let [p (new-project)]
    (spit (file p "notes" "plain.md") "# Note\n\nPara two.\n")
    (is (true? (:ok (run p "docs" "update" "notes/plain.md" "--name" "plain"))))
    (is (true? (:ok (run p "docs" "untrack" "other"))))
    (let [c (check p)]
      (is (= [2 true] ((juxt :route-count :routes-complete?) c)))
      (is (= (:hash (route-tree/from-entries h (entries p))) (:routes-root c))))
    (is (true? (:ok (run p "stores" "verify"))))))

(deftest a-store-without-a-route-schema-is-refused-with-the-declared-error
  (let [p (new-project)
        descriptor (file p ".kimun" "store.edn")
        _ (spit descriptor (pr-str (dissoc (edn/read-string (slurp descriptor)) :route-schema)))
        r (run p "stores" "check")]
    (is (false? (:ok r)))
    (is (= "store/unsupported-format" (get-in r [:error :type])))
    (is (= 5 (:exit r)))
    (is (re-find #"no longer supported: rebuild with `include build --rebuild`" (get-in r [:error :message])))))
