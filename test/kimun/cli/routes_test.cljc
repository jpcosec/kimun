(ns kimun.cli.routes-test
  "C2b: the document surface registers a route per tracked file (K2a) and
   keeps the route map true on every write — track, ingest, update, a move to
   another path, untrack — for typed and generic documents. The route root is
   the same when the same files are registered again under new ULIDs, and
   moves when a tracked file really changes. A route is a locator: nothing
   here is an identity."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.host.default :as host]
            [kimun.host.store-cache :as store-cache]
            [kimun.kernel.ports :as ports]
            [kimun.kernel.route-tree :as route-tree]
            [kimun.kernel.store :as kstore]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.markdown.ast :as ast]
            [kimun.test-util :as ktu]))

(def env {"USER" "tester"})
(def h host/host)

(defn- root-of
  "The route root a store of route schema 2 (every new store) has for `entries`."
  [entries]
  (:hash (route-tree/from-entries h entries)))

(defn- run [cwd & argv] (binding [ast/*parser* "native"] (:envelope (main/run argv env cwd))))
(defn- file [p & parts] (str (apply fs/path p parts)))

(def ticket
  (pr-str {:model/name "TicketDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "status" :type :string :required true :description "Status."}]
           :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"}))

(def ticket-text "# T\n\nStatus: open\n")
(def note-text "# Note\n\nPara one.\n\n- a\n- b\n")
(def note-reformatted "# Note\n\n\nPara one.\n\n- a\n- b\n")
(def note-edited "# Note\n\nPara two.\n\n- a\n- b\n")

(defn- project
  "A store with the typed document `tickets/login.md` (TicketDoc, tracked) and
   the generic document `notes/plain.md` (ingested)."
  []
  (let [p (str (ktu/temp-dir "kimun-routes-"))]
    (is (true? (:ok (run p "stores" "init" "--name" "p"))))
    (spit (file p "ticket.edn") ticket)
    (is (true? (:ok (run p "models" "define" "-f" "ticket.edn"))))
    (fs/create-dirs (file p "tickets")) (fs/create-dirs (file p "notes"))
    (spit (file p "tickets" "login.md") ticket-text)
    (spit (file p "notes" "plain.md") note-text)
    (is (true? (:ok (run p "docs" "track" "TicketDoc" "tickets/login.md"))))
    (is (true? (:ok (run p "docs" "ingest" "notes/plain.md"))))
    p))

(defn- opened [p] (:store (cli-store/open! (file p ".kimun"))))
(defn- routes [p] (:routes (opened p)))
(defn- root [p] (:routes-root (opened p)))
(defn- hashed [e] (select-keys e [:path :fingerprint :class :projection-hash :content-hash]))

(deftest every-tracked-file-has-a-route
  (let [p (project)
        rs (routes p)
        typed (tdocs/find-document (opened p) "login")
        generic (tdocs/generic-document (opened p) "plain")]
    (is (= ["notes/plain.md" "tickets/login.md"] (sort (keys rs))))
    (is (= (root-of (vals rs)) (root p)) "the root is the route root of exactly these entries")
    (testing "a typed document: the class is its model, the content hash is its payload's"
      (let [e (rs "tickets/login.md")]
        (is (= {:path "tickets/login.md" :fingerprint (tdocs/fingerprint h ticket-text)
                :class (:model-id typed) :projection-hash (tdocs/projection-hash h ticket-text)
                :content-hash (tdocs/content-hash h {"title" "T" "status" "open"})}
               (hashed e)))
        (is (not= (:projection-hash e) (:content-hash e)) "structure and selected payload are two things")
        (is (= (:tree typed) (:tree-id e)) "the tree id is the locator, outside the root")))
    (testing "a generic document: the class selects the whole structure, so both hashes are the same"
      (let [e (rs "notes/plain.md")]
        (is (= {:path "notes/plain.md" :fingerprint (tdocs/fingerprint h note-text)
                :class "generic/md" :projection-hash (tdocs/projection-hash h note-text)
                :content-hash (tdocs/projection-hash h note-text)}
               (hashed e)))
        (is (= (:tree generic) (:tree-id e)))))
    (is (= [(root p) 2 true [] [] []]
           ((juxt :routes-root :route-count :routes-complete? :routes-missing :routes-shared :routes-outside)
            (:data (run p "stores" "check")))))
    (is (true? (:ok (run p "stores" "verify"))))))

(deftest the-root-is-the-same-when-the-same-files-are-registered-under-new-ulids
  (let [a (project) b (project)]
    (is (not= (set (map :tree-id (vals (routes a)))) (set (map :tree-id (vals (routes b)))))
        "two builds, different tree ids")
    (is (= (mapv hashed (sort-by :path (vals (routes a)))) (mapv hashed (sort-by :path (vals (routes b))))))
    (is (= (root a) (root b)))
    (testing "and after untracking and registering again in the same store"
      (let [before (root a)]
        (is (true? (:ok (run a "docs" "untrack" "notes/plain.md"))))
        (is (not= before (root a)))
        (is (true? (:ok (run a "docs" "ingest" "notes/plain.md"))))
        (is (= before (root a)))))
    (testing "the projection hash does not depend on the document id"
      (is (not= (:merkle-root (tdocs/generic-document (opened a) "plain"))
                (:merkle-root (tdocs/generic-document (opened b) "plain")))
          "the registered tree root does: it is not what the route hashes")
      (is (= (get-in (routes a) ["notes/plain.md" :projection-hash])
             (get-in (routes b) ["notes/plain.md" :projection-hash]))))))

(deftest a-real-change-moves-the-root-and-only-its-route
  (let [p (project)
        before (routes p)
        root-before (root p)]
    (testing "an edit: fingerprint and projection change"
      (spit (file p "notes" "plain.md") note-edited)
      (is (= root-before (root p)) "the store does not move until the file is registered again")
      (is (true? (:ok (run p "docs" "update" "notes/plain.md" "--name" "plain"))))
      (let [after (routes p)
            stable #(dissoc % :tree-id)
            changed (vec (sort (keep (fn [[path e]] (when (not= (stable e) (stable (get before path))) path)) after)))]
        (is (= [(set (keys before)) ["notes/plain.md"]] [(set (keys after)) changed]) "one route changed, none came or went")
        (is (not= root-before (root p)))
        (is (not= (get-in before ["notes/plain.md" :projection-hash])
                  (get-in (routes p) ["notes/plain.md" :projection-hash])))))
    (testing "a reformatting: the fingerprint changes, the projection does not"
      (spit (file p "notes" "plain.md") note-text)
      (is (true? (:ok (run p "docs" "update" "notes/plain.md" "--name" "plain"))))
      (is (= root-before (root p)) "back to the first text, back to the first root")
      (spit (file p "notes" "plain.md") note-reformatted)
      (is (= ["plain"] (get-in (run p "stores" "update") [:data :updated])))
      (let [e (get-in (routes p) ["notes/plain.md"])]
        (is (= (tdocs/fingerprint h note-reformatted) (:fingerprint e)))
        (is (= (get-in before ["notes/plain.md" :projection-hash]) (:projection-hash e)))
        (is (not= root-before (root p)) "the fingerprint is part of the root")))
    (testing "a typed document: a field edit changes fingerprint, projection and content hash"
      (let [r (root p)]
        (spit (file p "tickets" "login.md") "# T\n\nStatus: closed\n")
        (is (true? (:ok (run p "docs" "update" "login"))))
        (let [e (get-in (routes p) ["tickets/login.md"])]
          (is (not= r (root p)))
          (is (= (tdocs/content-hash h {"title" "T" "status" "closed"}) (:content-hash e)))
          (is (not= (get-in before ["tickets/login.md" :projection-hash]) (:projection-hash e))))))))

(deftest a-contract-change-is-sensed-and-leaves-the-root-alone
  (let [p (project)
        root-before (root p)
        recorded #(:parse-contract (tdocs/generic-document (opened p) "plain"))
        before (recorded)]
    (with-redefs [store-cache/parser-version "2"]
      (is (= [:contract/changed :contract/changed]
             (mapv :type (get-in (run p "stores" "sense") [:data :signals]))))
      (is (= ["login" "plain"] (sort (get-in (run p "stores" "update") [:data :updated]))))
      (is (not= (:digest before) (:digest (recorded))) "the parse contract recorded in the anchor moved")
      (is (= root-before (root p))
          "same source, same structure, same class: the route root says what is tracked, not how it is read"))))

(deftest a-moved-document-leaves-its-old-route-in-the-same-commit
  (let [p (project)
        log #(count (kstore/read-log (:backend (cli-store/open! (file p ".kimun")))))]
    (testing "a generic document re-anchored on another file"
      (fs/create-dirs (file p "archive"))
      (spit (file p "archive" "plain.md") note-text)
      (let [tree (:tree (tdocs/generic-document (opened p) "plain"))
            n (log)
            u (run p "docs" "update" "archive/plain.md" "--name" "plain")]
        (is (true? (:ok u)) (pr-str u))
        (is (= (inc n) (log)) "one commit")
        (is (= ["archive/plain.md" "tickets/login.md"] (sort (keys (routes p)))) "the old route is gone")
        (is (= tree (get-in (routes p) ["archive/plain.md" :tree-id])) "the same document, another locator")
        (is (= (root-of (vals (routes p))) (root p)))))
    (testing "a typed document moved through the surface"
      (fs/create-dirs (file p "done"))
      (spit (file p "done" "login.md") ticket-text)
      (let [{:keys [backend store]} (cli-store/open! (file p ".kimun"))
            doc (tdocs/find-document store "login")
            {:keys [plan]} (tdocs/update-plan h store (:tree doc) ticket-text
                                              {:path "done/login.md" :actor "human/tester"
                                               :timestamp "2026-01-01T00:00:00.000Z"})]
        (is (= [{:op :remove-route :tree (:tree doc) :path "tickets/login.md"}]
               (filterv #(= :remove-route (:op %)) (:ops plan))))
        (is (= "done/login.md" (:path (:route (first (filter #(= :register-tree (:op %)) (:ops plan)))))))
        (kstore/commit! backend store plan)
        (is (= ["archive/plain.md" "done/login.md"] (sort (keys (routes p)))))
        (is (= (:tree doc) (get-in (routes p) ["done/login.md" :tree-id])))))
    (is (true? (:ok (run p "stores" "verify"))))))

(deftest untrack-removes-the-route-and-frees-the-path
  (let [p (project)]
    (testing "typed"
      (is (true? (:ok (run p "docs" "untrack" "login"))))
      (is (= ["notes/plain.md"] (keys (routes p))))
      (is (= (root-of (vals (routes p))) (root p)))
      (is (true? (:ok (run p "docs" "track" "TicketDoc" "tickets/login.md")))
          "the path can be tracked again: no route was left behind"))
    (testing "generic"
      (is (true? (:ok (run p "docs" "untrack" "plain"))))
      (is (= ["tickets/login.md"] (keys (routes p))))
      (is (true? (:ok (run p "docs" "ingest" "notes/plain.md")))))
    (testing "the last route leaving still gives a root"
      (is (true? (:ok (run p "docs" "untrack" "login"))))
      (is (true? (:ok (run p "docs" "untrack" "plain"))))
      (is (= {} (routes p)))
      (is (= (root-of []) (root p))))
    (is (true? (:ok (run p "stores" "verify"))))))

(deftest one-route-one-document-a-new-track-on-a-held-file-is-refused
  (let [p (project)
        head #(:head (opened p))]
    (testing "a typed document under another name"
      (let [before (head)
            e (run p "docs" "track" "TicketDoc" "tickets/login.md" "--name" "again")]
        (is (= ["docs/exists" 3 "tickets/login.md" "login"]
               [(get-in e [:error :type]) (:exit e) (get-in e [:error :data :path]) (get-in e [:error :data :document])]))
        (is (= before (head)) "nothing was written")))
    (testing "a typed document on a file a generic one holds"
      (spit (file p "notes" "plain.md") ticket-text)
      (let [e (run p "docs" "track" "TicketDoc" "notes/plain.md" "--name" "other")]
        (is (= ["docs/exists" "plain"] [(get-in e [:error :type]) (get-in e [:error :data :document])]))))
    (testing "a generic document on a file a typed one holds"
      (let [e (run p "docs" "ingest" "tickets/login.md" "--name" "raw")]
        (is (= ["docs/exists" "login"] [(get-in e [:error :type]) (get-in e [:error :data :document])]))))))

(deftest a-legacy-second-document-on-one-file-is-kept-without-a-route-and-declared
  ;; a v1 store holds them (two documents of one model on desk/tasks/Board.md). They enter only through
  ;; the explicit legacy mode a migration uses; the route map has one entry per path, so the second is
  ;; not under the root, and `stores check` says so instead of reporting a complete root
  (let [p (project)
        first-tree (get-in (routes p) ["tickets/login.md" :tree-id])
        root-before (root p)
        {:keys [backend store]} (cli-store/open! (file p ".kimun"))
        {:keys [plan tree]} (binding [ast/*parser* "native"]
                              (tdocs/track-plan h store "TicketDoc" "again" ticket-text
                                                {;; the legacy document gets its field units when it is
                                                 ;; written, exactly as the CLI does (K1c)
                                                 :mint (fn [] (ports/ulid (:ids h)))
                                                 :doc "01EEEEEEEEEEEEEEEEEEEEEEEE" :tree-id "01FFFFFFFFFFFFFFFFFFFFFFFF"
                                                 :path "tickets/login.md" :actor "human/tester"
                                                 :timestamp "2026-01-01T00:00:00.000Z" :shared-path :allow}))]
    (is (nil? (:route (first (filter #(= :register-tree (:op %)) (:ops plan))))) "the legacy plan carries no route")
    (kstore/commit! backend store plan)
    (is (= first-tree (get-in (routes p) ["tickets/login.md" :tree-id])) "the route stays with its first holder")
    (is (= root-before (root p)) "the root does not move for a document it does not cover")
    (is (= :shared (tdocs/route-state (opened p) tree "tickets/login.md")))
    (let [c (run p "stores" "check")]
      (is (true? (:ok c)))
      (is (= [2 false [] [{:name "again" :qualified "tickets/login.md"}] []]
             ((juxt :route-count :routes-complete? :routes-missing :routes-shared :routes-outside) (:data c))))
      (is (= 1 (count (filter #(= "routes/shared-path" (:type %)) (:warnings c))))))
    (testing "it is still sensed, and an unchanged update writes nothing and takes no route"
      (is (true? (get-in (run p "docs" "update" tree) [:data :unchanged?])))
      (spit (file p "tickets" "login.md") "# T\n\nStatus: closed\n")
      (is (= 2 (count (get-in (run p "stores" "sense") [:data :signals]))))
      (spit (file p "tickets" "login.md") ticket-text))
    (testing "untracking it removes no route and the coverage is complete again"
      (is (true? (:ok (run p "docs" "untrack" tree))))
      (is (= first-tree (get-in (routes p) ["tickets/login.md" :tree-id])))
      (is (= [true []] ((juxt :routes-complete? :routes-shared) (:data (run p "stores" "check"))))))))

(deftest a-document-outside-the-project-keeps-working-and-is-reported-as-not-covered
  ;; the API admits `--store` from another directory and records a relative path that leaves the
  ;; project (v1: test_open_store_resolves_store_and_project_root). The route map refuses such a path,
  ;; so the document has no route: it is declared, not silently left out of a "complete" root
  (let [p (project)
        elsewhere (str (ktu/temp-dir "kimun-routes-outside-"))
        e (run elsewhere "docs" "create" "TicketDoc" "far" "--set" "title=F" "--set" "status=open"
               "--store" (file p ".kimun"))
        path (get-in e [:data :path])]
    (is (true? (:ok e)) (pr-str e))
    (is (str/starts-with? path "..") "its recorded path leaves the project")
    (is (= ["notes/plain.md" "tickets/login.md"] (sort (keys (routes p)))) "no route for it")
    (let [c (run p "stores" "check")]
      (is (true? (:ok c)) "the store is valid")
      (is (= [2 false [] [] [{:name "far" :qualified path}]]
             ((juxt :route-count :routes-complete? :routes-missing :routes-shared :routes-outside) (:data c))))
      (is (= 1 (count (filter #(= "routes/outside-project" (:type %)) (:warnings c))))))
    (is (true? (get-in (run p "docs" "update" "far") [:data :unchanged?])) "an unchanged update writes nothing")
    (is (= "F" (get-in (run p "docs" "show" "far") [:data :payload "title"])))
    (is (true? (:ok (run p "docs" "untrack" "far"))))
    (is (true? (:routes-complete? (:data (run p "stores" "check")))))
    (is (true? (:ok (run p "stores" "verify"))))))

(defn- strip-routes
  "`plan` without any route: what a writer before C2 produced."
  [plan]
  (update plan :ops (fn [ops] (->> ops (remove #(= :remove-route (:op %))) (mapv #(dissoc % :route))))))

(defn- legacy-ingest!
  "Commits generic document `name` for `path` without its route: what a
   writer before C2 produced."
  [p name path text doc tree]
  (let [{:keys [backend store]} (cli-store/open! (file p ".kimun"))
        {:keys [plan]} (binding [ast/*parser* "native"]
                         (tdocs/generic-plan h store name text path
                                             {:doc doc :tree-id tree :actor "human/tester"
                                              :timestamp "2026-01-01T00:00:00.000Z"}))]
    (kstore/commit! backend store (strip-routes plan))))

(deftest a-store-written-before-routes-is-reported-as-partial-never-as-complete
  (let [p (str (ktu/temp-dir "kimun-routes-legacy-"))
        check #(run p "stores" "check")
        coverage #(select-keys (:data (check)) [:routes-root :route-count :routes-complete? :routes-missing])]
    (is (true? (:ok (run p "stores" "init" "--name" "p"))))
    (spit (file p "old.md") note-text)
    (spit (file p "older.md") note-text)
    (legacy-ingest! p "old" "old.md" note-text "01AAAAAAAAAAAAAAAAAAAAAAAA" "01BBBBBBBBBBBBBBBBBBBBBBBB")
    (legacy-ingest! p "older" "older.md" note-text "01CCCCCCCCCCCCCCCCCCCCCCCC" "01DDDDDDDDDDDDDDDDDDDDDDDD")
    (testing "no route was ever registered: the documents work and the gap is visible"
      (is (nil? (root p)))
      (is (= [2 []] (let [d (:data (run p "stores" "sense"))] [(:tracked d) (:signals d)])))
      (is (= {:routes-root nil :route-count 0 :routes-complete? false :routes-missing ["old.md" "older.md"]}
             (coverage)))
      (let [w (first (filter #(= "routes/partial" (:type %)) (:warnings (check))))]
        (is (= ["old.md" "older.md"] (:missing w)))
        (is (re-find #"covers 0 of 2" (:message w))))
      (is (true? (:ok (check))) "partial coverage is a warning, not an invalid store"))
    (testing "writing a new document gives a root that covers only that one, and says so"
      (spit (file p "new.md") note-edited)
      (is (true? (:ok (run p "docs" "ingest" "new.md"))))
      (is (= [1 false ["old.md" "older.md"]] ((juxt :route-count :routes-complete? :routes-missing) (coverage))))
      (is (= (root-of (vals (routes p))) (root p))))
    (testing "registering an old document again, unchanged, gives it its route"
      (let [u (run p "docs" "update" "old.md" "--name" "old")]
        (is (false? (get-in u [:data :unchanged?])) "without its route it is not unchanged")
        (is (= ["new.md" "old.md"] (sort (keys (routes p)))))
        (is (= [2 false ["older.md"]] ((juxt :route-count :routes-complete? :routes-missing) (coverage))))
        (is (true? (get-in (run p "docs" "update" "old.md" "--name" "old") [:data :unchanged?]))
            "once routed, an unchanged file writes nothing")))
    (testing "untracking one that never had a route removes none and is not rejected"
      (is (true? (:ok (run p "docs" "untrack" "older"))))
      (is (= [2 true []] ((juxt :route-count :routes-complete? :routes-missing) (coverage))))
      (is (empty? (filter #(= "routes/partial" (:type %)) (:warnings (check))))))
    (is (true? (:ok (run p "stores" "verify"))))))

(deftest a-typed-document-registered-before-routes-gains-its-route-on-docs-update
  (let [p (project)
        {:keys [backend store]} (cli-store/open! (file p ".kimun"))
        doc (tdocs/find-document store "login")]
    ;; drop the route as if the document had been tracked before routes existed
    (kstore/commit! backend store {:plan/version 1 :base (:head store) :actor "human/tester" :engines {}
                                   :timestamp "2026-01-01T00:00:00.000Z"
                                   :ops [{:op :remove-route :tree (:tree doc) :path "tickets/login.md"}]})
    (is (= ["tickets/login.md"] (:routes-missing (:data (run p "stores" "check")))))
    (let [u (run p "docs" "update" "login")]
      (is (true? (:ok u)) (pr-str u))
      (is (false? (get-in u [:data :unchanged?])))
      (is (= (:tree doc) (get-in (routes p) ["tickets/login.md" :tree-id])))
      (is (true? (:routes-complete? (:data (run p "stores" "check"))))))))
