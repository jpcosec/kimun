(ns kimun.cli.reanchor-test
  "K1b: re-anchoring and closure are DECLARATIONS (docs/v2/14 §21, rules 3 and
   6). The path is the vigente reference, not the identity: a document keeps
   its document id and tree id when it is declared anchored elsewhere, the
   declaration says what happened (`--reason`) and it stays in the journal
   (`:declared`). The sensor never decides continuity and kimun moves no file."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.surface.docs :as tdocs]
            [kimun.test-util :as ktu]))

(def ^:dynamic *p* nil)
(def env {"USER" "tester"})

(defn- run-in [p & argv]
  (binding [kimun.surface.markdown.ast/*parser* "native"]
    (:envelope (main/run (vec argv) env p))))
(defn- run [& argv] (apply run-in *p* argv))
(defn- data [& argv] (:data (apply run argv)))
(defn- file [& parts] (str (apply fs/path *p* parts)))

(def ticket
  (pr-str {:model/name "TicketDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "status" :type :string :required true :description "Status."}]
           :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"}))

(defn- project
  "A store with the typed document `docs/a.md` (TicketDoc) and the generic
   document `notes/plain.md` (ingested)."
  []
  (let [p *p*]
    (is (true? (:ok (run-in p "stores" "init" "--name" "p"))))
    (fs/create-dirs (file "docs")) (fs/create-dirs (file "notes"))
    (spit (file "ticket.edn") ticket)
    (is (true? (:ok (run "models" "define" "-f" "ticket.edn"))))
    (spit (file "docs" "a.md") "# T\n\nStatus: open\n")
    (spit (file "notes" "plain.md") "# Note\n\nPara one.\n")
    (is (true? (:ok (run-in p "docs" "track" "TicketDoc" "docs/a.md"))))
    (is (true? (:ok (run-in p "docs" "ingest" "notes/plain.md"))))
    p))

(use-fixtures :each
  (fn [f]
    (let [p (str (ktu/temp-dir "kimun-reanchor-"))]
      (try (binding [*p* p] (project) (f)) (finally (fs/delete-tree p))))))

(defn- opened [] (:store (cli-store/open! (file ".kimun"))))
(defn- doc [nm] (first (filter #(= nm (:name %)) (tdocs/documents (opened)))))
(defn- gdoc [nm] (first (filter #(= nm (:name %)) (tdocs/generic-documents (opened)))))
(defn- head [] (:head (opened)))

(deftest a-typed-document-is-re-anchored-keeping-its-identity
  (let [t0 (:tree (doc "a")) d0 (:doc (doc "a"))]
    (fs/create-dirs (file "b"))
    (fs/move (file "docs" "a.md") (file "b" "c.md"))
    (let [r (run "docs" "reanchor" "a" "b/c.md" "--reason" "moved by hand")
          d (:data r)]
      (is (true? (:ok r)) (pr-str r))
      (is (= t0 (:tree d)) "the tree id is kept")
      (is (= d0 (:doc (doc "a"))) "the document id is kept")
      (is (= {:from "docs/a.md" :to "b/c.md" :previous-file "missing" :reason "moved by hand"}
             (:reanchored d)))
      (is (= "b/c.md" (:path (doc "a"))) "the new anchor is recorded")
      (is (= ["b/c.md"] (mapv :path (data "docs" "status"))) "status sees the new path")
      (is (= [:ok] (mapv :status (data "docs" "status")))))
    (testing "the old path is free and a new document there gets fresh ids"
      (spit (file "docs" "a.md") "# T\n\nStatus: closed\n")
      (is (true? (:ok (run "docs" "track" "TicketDoc" "docs/a.md" "--name" "a2"))))
      (let [a2 (doc "a2")]
        (is (not= t0 (:tree a2)))
        (is (not= d0 (:doc a2)))))))

(deftest a-generic-document-is-re-anchored-keeping-its-identity
  (let [t0 (:tree (gdoc "plain"))]
    (fs/create-dirs (file "b"))
    (fs/move (file "notes" "plain.md") (file "b" "plain.md"))
    (let [r (run "docs" "reanchor" "plain" "b/plain.md" "--reason" "moved")
          sense (data "stores" "sense")]
      (is (true? (:ok r)) (pr-str r))
      (is (= t0 (get-in r [:data :tree])))
      (is (= "b/plain.md" (:path (gdoc "plain"))))
      (is (not-any? #(= "docs/file-missing" (:type %)) (:signals sense))
          "the sensor no longer reports the old anchor missing"))))

(deftest refusals-do-not-write-a-revision
  (let [h0 (head)
        a (doc "a")]
    (testing "no --reason"
      (is (= ["cli/usage" 6] (let [e (run "docs" "reanchor" "a" "notes/plain.md")]
                               [(get-in e [:error :type]) (:exit e)]))))
    (testing "a file that does not exist"
      (is (= "docs/no-file" (get-in (run "docs" "reanchor" "a" "nope.md" "--reason" "x") [:error :type]))))
    (testing "a path another document already holds"
      (is (= "docs/exists" (get-in (run "docs" "reanchor" "a" "notes/plain.md" "--reason" "x") [:error :type]))))
    (testing "the path it already has"
      (is (= "cli/usage" (get-in (run "docs" "reanchor" "a" (:path a) "--reason" "x") [:error :type]))))
    (is (= h0 (head)) "no revision was written")))

(deftest the-declaration-is-in-the-journal
  (fs/create-dirs (file "b"))
  (fs/move (file "docs" "a.md") (file "b" "c.md"))
  (is (true? (:ok (run "docs" "reanchor" "a" "b/c.md" "--reason" "moved by hand"))))
  (let [e (first (data "journal" "show"))]
    (is (= "docs reanchor" (:operation e)))
    (is (= {:kind "reanchor" :from "docs/a.md" :to "b/c.md"
            :previous-file "missing" :reason "moved by hand"} (:declared e))))
  (is (true? (:ok (run "docs" "untrack" "a" "--reason" "no longer tracked"))))
  (let [e (first (data "journal" "show"))]
    (is (= "docs untrack" (:operation e)))
    (is (= {:kind "closure" :file "present" :reason "no longer tracked" :file-deleted? false}
           (:declared e)))))

(deftest a-reanchor-composes-with-a-rebuild-that-carries-identities
  (let [p (str (ktu/temp-dir "kimun-reanchor-inc-"))
        git! (fn [& args] (apply process/shell {:dir p :out :string :err :string :continue true} "git" args))]
    (try
      (git! "init" "-q") (git! "config" "user.email" "t@e") (git! "config" "user.name" "t")
      (fs/create-dirs (file p "models")) (fs/create-dirs (file p "docs"))
      (spit (file p "models" "TicketDoc.edn") ticket)
      (spit (file p "docs" "a.md") "# T\n\nStatus: open\n")
      (spit (file p "kimun.include.edn")
            (str "{:include/version 1 :store {:path \".kimun\" :git :ignore}\n"
                 " :models [\"models/*.edn\"]\n"
                 " :documents [{:glob \"docs/**/*.md\" :model \"TicketDoc\"}]}\n"))
      (git! "add" "-A") (git! "commit" "-qm" "init")
      (letfn [(inc-build [& argv]
                (:envelope (binding [kimun.surface.markdown.ast/*parser* "native"]
                             (main/run (into ["include" "build"] argv) env p))))]
        (let [b (inc-build)] (is (true? (:ok b)) (pr-str b)))
        (let [t0 (:tree (first (tdocs/documents (:store (cli-store/open! (file p ".kimun"))))))]
          (fs/move (file p "docs" "a.md") (file p "b.md"))
          (is (true? (:ok (:envelope (binding [kimun.surface.markdown.ast/*parser* "native"]
                                        (main/run ["docs" "reanchor" "a" "b.md" "--reason" "moved"] env p))))))
          (is (:ok (:envelope (binding [kimun.surface.markdown.ast/*parser* "native"]
                                (main/run ["docs" "show" "a"] env p)))))
          (spit (file p "kimun.include.edn")
                (str "{:include/version 1 :store {:path \".kimun\" :git :ignore}\n"
                     " :models [\"models/*.edn\"]\n"
                     " :documents [{:glob \"b.md\" :model \"TicketDoc\"}]}\n"))
          (git! "add" "-A") (git! "commit" "-qm" "moved")
          (let [b (inc-build "--rebuild")
                ids (get-in b [:data :identities])]
            (is (true? (:ok b)) (pr-str b))
            (is (= 1 (:carried ids)))
            (is (= [] (:not-carried ids)))
            (is (= t0 (:tree (first (tdocs/documents (:store (cli-store/open! (file p ".kimun")))))))))))
      (finally (fs/delete-tree p)))))
