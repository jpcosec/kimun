(ns kimun.cli.beat-git-test
  "`stores sense|health|heartbeat` ask git which files changed since the pointer of the store
   and open only those documents. Every project is a throwaway git repository. What must hold:
   the signals, findings and verdict are the ones `--full` gives, the files git does not report
   are not read, and nothing is written."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [kimun.cli.commands.stores :as stores]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.host.hash :as hash]
            [kimun.surface.diagnostics :as diag]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.markdown.ast :as ast]
            [kimun.test-util :as ktu]))

(def ^:dynamic *p* nil)
(def env {"USER" "tester"})

(defn- run-in [p & argv] (binding [ast/*parser* "native"] (:envelope (main/run (vec argv) env p))))
(defn- run [& argv] (apply run-in *p* argv))
(defn- file [& parts] (str (apply fs/path *p* parts)))
(defn- git! [d & args]
  (let [r (apply process/shell {:out :string :err :string :continue true :dir (str d)} "git" args)]
    (when-not (zero? (:exit r)) (throw (ex-info (str "git failed: " (:err r)) {:args args})))
    (str/trim (:out r))))
(defn- commit! [msg] (git! *p* "add" "-A") (git! *p* "commit" "-qm" msg) (git! *p* "rev-parse" "HEAD"))

(def ticket
  (pr-str {:model/name "TicketDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "status" :type :string :required true :description "Status."}]
           :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"}))

(defn- tree-digest [dir]
  (into (sorted-map)
        (for [f (file-seq (fs/file dir)) :when (.isFile f)]
          [(str (fs/relativize dir f)) (hash/sha256-hex (fs/read-all-bytes f))])))

(defn- setup!
  "Two generic notes, one typed document (login.md), a commit and the pointer at it."
  [p]
  (fs/create-dirs (str p "/notes"))
  (git! p "init" "-q") (git! p "config" "user.email" "t@e") (git! p "config" "user.name" "t")
  (spit (str p "/.gitignore") ".kimun/\n")
  (spit (str p "/notes/a.md") "# A\n\nUno.\n")
  (spit (str p "/notes/b.md") "# B\n\nDos.\n")
  (spit (str p "/ticket.edn") ticket)
  (is (true? (:ok (run-in p "stores" "init" "--name" "p"))))
  (is (true? (:ok (run-in p "models" "define" "-f" "ticket.edn"))))
  (is (true? (:ok (run-in p "docs" "create" "TicketDoc" "login" "--set" "title=T" "--set" "status=open"))))
  (is (true? (:ok (run-in p "docs" "ingest" "notes/a.md"))))
  (is (true? (:ok (run-in p "docs" "ingest" "notes/b.md"))))
  (commit! "base")
  (is (true? (:ok (run-in p "stores" "seen" "--set" "HEAD")))))

(use-fixtures :each
  (fn [f]
    (let [p (str (ktu/temp-dir "kimun-beatgit-"))]
      (try (binding [*p* p] (setup! p) (f)) (finally (fs/delete-tree p))))))

(defn- reading
  "`[result rels]`: runs `thunk` with the project file reader recording every path it reads."
  [thunk]
  (let [orig @#'stores/file-reader reads (atom [])]
    [(with-redefs [stores/file-reader (fn [path] (let [r (orig path)] (fn [rel] (swap! reads conj rel) (r rel))))]
       (thunk))
     @reads]))

(defn- sense [& argv] (apply run "stores" "sense" argv))
(defn- health [& argv] (apply run "stores" "health" argv))
(defn- same-answer
  "The part of a response that must not depend on the path taken."
  [r] (-> r (update :data dissoc :scope :observed-at)))

(defn- both
  "Runs `cmd` through git and with `--full` over the same state; checks the answers are the same
   and returns `{:git r :full r :reads rels-read-by-git}`."
  [cmd]
  (let [[g reads] (reading #(run "stores" cmd))
        [f full-reads] (reading #(run "stores" cmd "--full"))]
    (is (true? (:ok g)) (pr-str g))
    (is (= (same-answer f) (same-answer g)) (str cmd ": git and --full answer the same"))
    (is (= :git (get-in g [:data :scope :mode])))
    (is (= :full (get-in f [:data :scope :mode])))
    (is (= (count reads) (get-in g [:data :scope :opened])) "opened counts the files read")
    (is (= 3 (count full-reads)) "--full opens every tracked document")
    {:git g :full f :reads (set reads)}))

(deftest nothing-changed-opens-nothing
  (let [head (git! *p* "rev-parse" "HEAD")]
    (doseq [cmd ["sense" "health"]]
      (let [{:keys [git reads]} (both cmd)]
        (is (= #{} reads) "no file was read")
        (is (= {:mode :git :since head :head head :changed 0 :opened 0} (get-in git [:data :scope])))))
    (is (= [3 true []] ((juxt :tracked :valid :signals) (:data (sense)))) "it still counts every document")))

(deftest a-changed-document-is-the-only-one-opened
  (testing "edited: uncommitted, then committed (the pointer stays behind)"
    (spit (file "notes" "a.md") "# A\n\nOtra cosa.\n")
    (doseq [commit? [false true]]
      (when commit? (commit! "edit a"))
      (doseq [cmd ["sense" "health"]]
        (let [{:keys [git reads]} (both cmd)]
          (is (= #{"notes/a.md"} reads))
          (is (= 1 (get-in git [:data :scope :opened])))
          (is (= 1 (get-in git [:data :scope :changed])))))
      (is (= [:source/mutated] (mapv :type (:signals (:data (sense))))) "and the edit is seen")))
  (testing "a typed document, edited"
    (spit (file "login.md") "# T\n\nStatus: closed\n")
    (is (= #{"login.md" "notes/a.md"} (:reads (both "sense")))))
  (testing "reformatted"
    (git! *p* "checkout" (git! *p* "rev-list" "--max-parents=0" "HEAD") "--" "login.md" "notes/a.md")
    (spit (file "notes" "b.md") "# B\n\n\nDos.\n")
    (let [{:keys [git reads]} (both "sense")]
      (is (= #{"notes/b.md"} reads))
      (is (seq (:signals (:data git))) "the full path sees the reformatting, and so does this one"))))

(deftest deleted-and-moved-documents
  (testing "deleted"
    (fs/delete (file "notes" "a.md"))
    (let [{:keys [git reads]} (both "sense")]
      (is (= #{"notes/a.md"} reads))
      (is (= [:source/missing] (mapv :type (:signals (:data git)))))))
  (testing "moved: git reports the old and the new path"
    (fs/create-dirs (file "moved"))
    (git! *p* "checkout" "--" "notes/a.md")
    (git! *p* "mv" "notes/a.md" "moved/a.md")
    (let [{:keys [git reads]} (both "health")]
      (is (= #{"notes/a.md"} reads) "the tracked path is the old one")
      (is (= 1 (get-in git [:data :scope :changed])) "git sees one rename")
      (is (= :needs-human (get-in git [:data :status])))
      (is (= ["docs/file-missing"] (mapv :type (:warnings git)))))))

(deftest a-rename-counts-at-both-names
  (let [change-scope @#'stores/change-scope]
    (git! *p* "mv" "notes/a.md" "notes/z.md")
    (let [sc (change-scope (file ".kimun") false)]
      (is (= :git (:mode sc)))
      (is (= #{"notes/a.md" "notes/z.md"} (:paths sc))))))

(deftest contract-moved-is-seen-without-any-file-changing
  (is (true? (:ok (run "models" "field-add" "TicketDoc" "--field" "priority" "--type" "int"
                       "--description" "Field int." "--default" "5"))))
  (let [{:keys [git reads]} (both "sense")]
    (is (= #{} reads) "no file changed")
    (is (= [:contract/changed] (mapv :type (:signals (:data git)))))
    (is (= :degraded (get-in (both "health") [:git :data :status])))))

(deftest orphan-is-seen-without-opening-the-file
  (let [{:keys [store]} (cli-store/open! (file ".kimun"))
        orig tdocs/documents
        report (fn [changed reads]
                 (with-redefs [tdocs/documents (fn [s] (mapv #(assoc % :state :orphan) (orig s)))]
                   (diag/report store (fn [rel] (swap! reads conj rel) (slurp (file rel))) {:changed changed})))
        git-reads (atom []) full-reads (atom [])
        g (report #{} git-reads)
        f (report nil full-reads)]
    (is (= {:orphan 1 :ok 2} (:counts f)) "the full path sees the orphan")
    (is (= (:counts f) (:counts g)))
    (is (= [] @git-reads))
    (is (= 3 (count @full-reads)))))

(deftest without-pointer-or-git-it-is-the-full-path
  (testing "no pointer"
    (fs/delete (file ".kimun" "seen.edn"))
    (spit (file "notes" "a.md") "# A\n\nOtra.\n")
    (let [r (sense)]
      (is (= {:mode :full :since nil :head nil :changed nil :opened 3} (get-in r [:data :scope])))
      (is (= [:source/mutated] (mapv :type (:signals (:data r)))))))
  (testing "git without commits"
    (let [q (str (ktu/temp-dir "kimun-beatgit-nocommit-"))]
      (try (git! q "init" "-q")
           (spit (str q "/a.md") "# A\n")
           (is (true? (:ok (run-in q "stores" "init" "--name" "q"))))
           (is (true? (:ok (run-in q "docs" "ingest" "a.md"))))
           (is (= {:mode :full :since nil :head nil :changed nil :opened 1}
                  (get-in (run-in q "stores" "sense") [:data :scope])))
           (finally (fs/delete-tree q)))))
  (testing "not a repository"
    (let [q (str (ktu/temp-dir "kimun-beatgit-nogit-"))]
      (try (spit (str q "/a.md") "# A\n")
           (is (true? (:ok (run-in q "stores" "init" "--name" "q"))))
           (is (true? (:ok (run-in q "docs" "ingest" "a.md"))))
           (let [r (run-in q "stores" "health")]
             (is (true? (:ok r)))
             (is (= {:mode :full :opened 1} (select-keys (get-in r [:data :scope]) [:mode :opened])))
             (is (= :ok (get-in r [:data :status]))))
           (finally (fs/delete-tree q))))))

(deftest full-forces-the-whole-path-even-with-a-pointer
  (let [r (sense "--full")]
    (is (= :full (get-in r [:data :scope :mode])))
    (is (= 3 (get-in r [:data :scope :opened])))
    (is (= :full (get-in (health "--full") [:data :scope :mode])))))

(deftest a-damaged-pointer-is-the-declared-error
  (spit (file ".kimun" "seen.edn") "{:commit \"0000000000000000000000000000000000000001\"}")
  (let [r (sense)]
    (is (false? (:ok r)))
    (is (= "git/bad-pointer" (get-in r [:error :type])))
    (is (true? (:ok (sense "--full"))) "--full does not consult the pointer")))

(defn- beats [& argv]
  (let [out (with-out-str (apply run "stores" "heartbeat" "--interval" "0.1" "--beats" "2" argv))]
    (->> (str/split-lines out) (mapv #(json/parse-string % true))
         (mapv (fn [r] (-> r (dissoc :at :elapsed-ms)
                           (update :observation #(when % (dissoc % :scope :observed-at)))))))))

(deftest the-heartbeat-in-git-mode-emits-the-same-records
  (doseq [mutate [(fn []) #(spit (file "notes" "a.md") "# A\n\nOtra.\n") #(fs/delete (file "notes" "b.md"))]]
    (mutate)
    (let [g (beats) f (beats "--full")]
      (is (= 4 (count g)))
      (is (= f g)))
    (let [obs (->> (with-out-str (run "stores" "heartbeat" "--interval" "0.1" "--beats" "1"))
                   str/split-lines (map #(json/parse-string % true)) (filter #(= "beat" (:kind %))) first :observation)]
      (is (= "git" (get-in obs [:scope :mode]))))))

(deftest nothing-is-written
  (spit (file "notes" "a.md") "# A\n\nOtra.\n")
  (let [before (tree-digest (file ".kimun"))]
    (doseq [argv [["sense"] ["sense" "--full"] ["health"] ["health" "--full"]
                  ["heartbeat" "--interval" "0.1" "--beats" "2"]]]
      (with-out-str (apply run "stores" argv))
      (is (= before (tree-digest (file ".kimun"))) (str argv)))
    (is (contains? before "seen.edn") "the pointer is part of what was compared")))
