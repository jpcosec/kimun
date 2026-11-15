(ns kimun.cli.route-schema-test
  "K2a C5 from the CLI: a new store is of route schema 2 (the Merkle tree by
   path) and says so; a store written before the tree is of schema 1, keeps
   answering and writing as it did, and moves to schema 2 only through
   `stores upgrade` (or a rebuild), with the same documents, routes and
   history and a route root that changes and is reported."
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

(defn- old-project
  "A project whose store is what a store created before the route tree is:
   its store.edn carries no `:route-schema`, and every write went through the
   flat map of schema 1."
  []
  (let [p (str (ktu/temp-dir "kimun-route-schema-"))
        descriptor (file p ".kimun" "store.edn")]
    (is (true? (:ok (run p "stores" "init" "--name" "p"))))
    (spit descriptor (pr-str (dissoc (edn/read-string (slurp descriptor)) :route-schema)))
    (write-documents! p)
    p))

(def paths ["notes/deep/other.md" "notes/plain.md" "tickets/login.md"])

(deftest a-new-store-is-of-schema-2-and-says-so
  (let [p (new-project)
        c (check p)]
    (is (= 2 (:route-schema (edn/read-string (slurp (file p ".kimun" "store.edn"))))))
    (is (= 2 (:routes-schema c)))
    (is (= 3 (:route-count c)))
    (is (true? (:routes-complete? c)))
    (is (= (:hash (route-tree/from-entries h (entries p))) (:routes-root c)))
    (is (= paths (sort (map :path (entries p)))))
    (testing "there is nothing to upgrade"
      (let [u (run p "stores" "upgrade")]
        (is (true? (:ok u)))
        (is (= {:upgraded false :format-version 4 :route-schema 2}
               (select-keys (:data u) [:upgraded :format-version :route-schema])))))))

(deftest a-store-written-before-the-tree-keeps-working-as-schema-1
  (let [p (old-project)
        c (check p)]
    (is (= 1 (:routes-schema c)) "the schema is visible, not guessed")
    (is (= 3 (:route-count c)))
    (is (true? (:routes-complete? c)))
    (is (= (route/root h (entries p)) (:routes-root c)) "the root is still the flat root it had")
    (testing "a write stays in schema 1 and store.edn is not touched"
      (spit (file p "notes" "plain.md") "# Note\n\nPara two.\n")
      (is (true? (:ok (run p "docs" "update" "notes/plain.md" "--name" "plain"))))
      (is (= 1 (:routes-schema (check p))))
      (is (= (route/root h (entries p)) (:routes-root (check p))))
      (is (nil? (:route-schema (edn/read-string (slurp (file p ".kimun" "store.edn")))))))
    (is (true? (:ok (run p "stores" "verify"))))))

(deftest stores-upgrade-moves-the-routes-to-the-tree-and-nothing-else
  (let [p (old-project)
        before (check p)
        before-entries (set (entries p))
        listed (:data (run p "docs" "list"))
        shown (:data (run p "docs" "show" "login"))
        journal (:data (run p "journal" "show"))
        u (run p "stores" "upgrade")
        after (check p)]
    (is (true? (:ok u)) (pr-str u))
    (is (= {:upgraded true :format-version 4 :from-route-schema 1 :route-schema 2 :routes 3}
           (select-keys (:data u) [:upgraded :format-version :from-route-schema :route-schema :routes])))
    (is (= (:routes-root before) (get-in u [:data :routes-root-before])))
    (is (= (:routes-root after) (get-in u [:data :routes-root])))
    (is (not= (:routes-root before) (:routes-root after)) "the root changes, and the command says both")
    (is (= 2 (:routes-schema after)))
    (is (= (:hash (route-tree/from-entries h before-entries)) (:routes-root after)))
    (testing "what the store holds did not move"
      (is (= before-entries (set (entries p))) "every route, tree ids included")
      (is (= (:head before) (:head after)) "no revision was added")
      (let [same [:revisions :trees :head :route-count :routes-complete? :routes-missing :routes-shared :routes-outside]]
        (is (= (select-keys before same) (select-keys after same))))
      (is (= listed (:data (run p "docs" "list"))))
      (is (= shown (:data (run p "docs" "show" "login"))))
      (is (= journal (:data (run p "journal" "show")))))
    (is (true? (:ok (run p "stores" "verify"))))
    (is (true? (:ok (run p "journal" "verify"))))
    (testing "a second upgrade has nothing to do"
      (is (false? (get-in (run p "stores" "upgrade") [:data :upgraded]))))
    (testing "writes go on, through the tree"
      (spit (file p "notes" "plain.md") "# Note\n\nPara two.\n")
      (is (true? (:ok (run p "docs" "update" "notes/plain.md" "--name" "plain"))))
      (is (true? (:ok (run p "docs" "untrack" "other"))))
      (let [c (check p)]
        (is (= [2 2 true] ((juxt :routes-schema :route-count :routes-complete?) c)))
        (is (= (:hash (route-tree/from-entries h (entries p))) (:routes-root c))))
      (is (true? (:ok (run p "stores" "verify")))))))

(deftest an-upgraded-store-and-a-new-one-agree-on-the-root-of-the-same-files
  ;; the tree ids differ between the two stores and do not enter the root
  (let [old (old-project)
        _ (is (true? (:ok (run old "stores" "upgrade"))))
        fresh (new-project)]
    (is (= (:routes-root (check fresh)) (:routes-root (check old))))))
