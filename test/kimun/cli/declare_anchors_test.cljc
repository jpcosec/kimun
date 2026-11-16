(ns kimun.cli.declare-anchors-test
  "`kimun declare anchors FILE… [--since COMMIT]`: the anchors written in declarations are
   resolved against the files and git says whether what is anchored changed. No store is
   needed and nothing is written. Every project is a throwaway git repository."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [kimun.cli.main :as main]
            [kimun.host.hash :as hash]
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
(defn- anchors-since [& more] (apply run "declare" "anchors" "decl.clj" more))
(defn- by-name
  "Status of each anchor by the term on the other side (x0, x1…)."
  [r]
  (into {} (map (juxt (comp :term :other) identity)) (get-in r [:data :anchors])))
(defn- status [r] (into {} (map (fn [[k v]] [k (:status v)])) (by-name r)))

(def spec0 "# Spec\n\nUno.\n\nDos.\n\n## B\n\nTres.\n\n## C\n\nCuatro.\n")
(def decl
  "(ns p.d)
(relation anchors \"A is in B.\")
(anchors p1 (at \"docs/spec.md\" [\"Spec\"] {:type :paragraph :n 1}))
(anchors p2 (at \"docs/spec.md\" [\"Spec\"] {:type :paragraph :n 2}))
(anchors sb (at \"docs/spec.md\" [\"Spec\" \"B\"]))
(anchors sc (at \"docs/spec.md\" [\"Spec\" \"C\"]))
(anchors whole (at \"data.txt\"))
")

(use-fixtures :each
  (fn [f]
    (let [p (str (ktu/temp-dir "kimun-decl-anchors-"))]
      (try (binding [*p* p]
             (fs/create-dirs (str p "/docs"))
             (git! p "init" "-q") (git! p "config" "user.email" "t@e") (git! p "config" "user.name" "t")
             (spit (file "docs" "spec.md") spec0)
             (spit (file "data.txt") "datos\n")
             (spit (file "decl.clj") decl)
             (f))
           (finally (fs/delete-tree p))))))


(deftest without-store-and-without-commit-it-only-resolves
  (commit! "base")
  (let [r (anchors-since)]
    (is (true? (:ok r)) (pr-str r))
    (is (= "declare anchors" (:command r)))
    (is (nil? (:store r)))
    (is (= [nil nil nil] ((juxt #(get-in % [:data :since]) #(get-in % [:data :pointer]) #(get-in % [:data :head])) r)))
    (is (= {"p1" :resolved "p2" :resolved "sb" :resolved "sc" :resolved "whole" :resolved} (status r)))
    (is (= {:anchors 5 :files 1 :opened 5 :by-status {:resolved 5}} (get-in r [:data :summary])))
    (is (= {:file "decl.clj" :line 3} (:declared-at (get (by-name r) "p1"))))
    (testing "with no git at all the same holds"
      (let [q (str (ktu/temp-dir "kimun-decl-nogit-"))]
        (try (spit (str q "/decl.clj") "(ns q)\n(relation anchors \"d\")\n(anchors x (at \"a.txt\"))\n")
             (spit (str q "/a.txt") "hola\n")
             (is (= :resolved (get (status (run-in q "declare" "anchors" "decl.clj")) "x")))
             (finally (fs/delete-tree q)))))))

(deftest editing-the-anchored-paragraph-mutates-only-it
  (let [x (commit! "base")]
    (spit (file "docs" "spec.md") (str/replace spec0 "Dos." "Dos, editado."))
    (spit (file "data.txt") "datos\n")
    (testing "uncommitted"
      (let [r (anchors-since "--since" x)]
        (is (true? (:ok r)) (pr-str r))
        (is (= x (get-in r [:data :since])))
        (is (= x (get-in r [:data :head])))
        (is (= {"p1" :anchored "p2" :mutated "sb" :anchored "sc" :anchored "whole" :anchored} (status r)))
        (is (= [true true true true false] (mapv :opened (map (by-name r) ["p1" "p2" "sb" "sc" "whole"])))
            "the unchanged data.txt is not opened")
        (is (not= (:before (get (by-name r) "p2")) (:hash (get (by-name r) "p2"))))))
    (testing "committed gives the same states"
      (let [uncommitted (status (anchors-since "--since" x))
            y (commit! "edit")
            r (anchors-since "--since" x)]
        (is (= uncommitted (status r)))
        (is (= y (get-in r [:data :head])))
        (testing "and since the new commit nothing changed: nothing is opened"
          (let [r2 (anchors-since "--since" y)]
            (is (= #{:anchored} (set (vals (status r2)))))
            (is (zero? (get-in r2 [:data :summary :opened])))))))))

(deftest moving-and-deleting-a-section
  (let [x (commit! "base")]
    (testing "moved up a level: the path Spec/B no longer exists"
      (spit (file "docs" "spec.md") (str/replace spec0 "## B" "# B"))
      (is (= {"p1" :anchored "p2" :anchored "sb" :missing "sc" :missing "whole" :anchored}
             (status (anchors-since "--since" x)))
          "C now hangs from the new top-level B, so Spec/C is gone too"))
    (testing "deleted"
      (spit (file "docs" "spec.md") "# Spec\n\nUno.\n\nDos.\n\n## C\n\nCuatro.\n")
      (let [r (anchors-since "--since" x)]
        (is (= :missing (:status (get (by-name r) "sb"))))
        (is (= [] (:candidates (get (by-name r) "sb"))))
        (is (= :anchored (:status (get (by-name r) "sc"))))))
    (testing "the whole file deleted"
      (fs/delete (file "data.txt"))
      (is (= :missing (:status (get (by-name (anchors-since "--since" x)) "whole")))))))

(deftest the-pointer-of-the-store-is-the-default-commit
  (let [x (commit! "base")]
    (is (true? (:ok (run "stores" "init" "--name" "p"))))
    (is (true? (:ok (run "stores" "seen" "--set" "HEAD"))))
    (spit (file "docs" "spec.md") (str/replace spec0 "Uno." "Uno, editado."))
    (let [r (anchors-since)]
      (is (true? (:ok r)) (pr-str r))
      (is (= [x x] [(get-in r [:data :since]) (get-in r [:data :pointer])]))
      (is (= :mutated (get (status r) "p1"))))
    (testing "--since wins over the pointer and is not recorded in it"
      (let [r (anchors-since "--since" "HEAD")]
        (is (= [nil] [(get-in r [:data :pointer])]))
        (is (= :mutated (get (status r) "p1")))))
    (testing "a store without pointer: no comparison"
      (fs/delete (file ".kimun" "seen.edn"))
      (is (= :resolved (get (status (anchors-since)) "p1"))))
    (testing "a damaged pointer is declared, never ignored"
      (spit (file ".kimun" "seen.edn") "{:commit \"deadbeef\"}")
      (is (= "git/bad-pointer" (get-in (anchors-since) [:error :type])))
      (spit (file ".kimun" "seen.edn") "garbage {")
      (is (= "git/bad-pointer" (get-in (anchors-since) [:error :type]))))))

(deftest outside-git-and-bad-commit-are-git-errors
  (commit! "base")
  (is (= "git/unknown-commit" (get-in (anchors-since "--since" "nosuchcommit") [:error :type])))
  (let [q (str (ktu/temp-dir "kimun-decl-nogit-"))]
    (try (spit (str q "/decl.clj") "(ns q)\n(relation anchors \"d\")\n(anchors x (at \"a.txt\"))\n")
         (let [r (run-in q "declare" "anchors" "decl.clj" "--since" "HEAD")]
           (is (false? (:ok r)))
           (is (= "git/not-a-repository" (get-in r [:error :type])) (pr-str r)))
         (finally (fs/delete-tree q)))))

(deftest a-badly-formed-locator-is-declared-with-its-line
  (commit! "base")
  (spit (file "bad.clj") "(ns p.b)\n(relation anchors \"d\")\n\n(anchors x (at \"docs/spec.md\" [\"Spec\"] {:type :paragraph :n 0}))\n")
  (let [r (run "declare" "anchors" "bad.clj")]
    (is (false? (:ok r)))
    (is (= 3 (:exit r)))
    (is (= "declare/bad-locator" (get-in r [:error :type])))
    (is (= ["bad.clj" 4] [(get-in r [:error :data :file]) (get-in r [:error :data :line])]))
    (is (= 6 (:exit (run "declare" "anchors"))) "no FILE is usage")))

(defn- tree-digest [dir]
  (into (sorted-map)
        (for [f (file-seq (fs/file dir)) :when (and (.isFile f) (not (str/includes? (str f) "/.git/")))]
          [(str (fs/relativize dir f)) (hash/sha256-hex (fs/read-all-bytes f))])))

(defn- git-state []
  {:status (git! *p* "status" "--porcelain") :head (git! *p* "rev-parse" "HEAD")
   :stash (git! *p* "stash" "list") :refs (git! *p* "for-each-ref")
   :index (hash/sha256-hex (fs/read-all-bytes (str (fs/path *p* ".git" "index"))))})

(deftest read-only
  (let [x (commit! "base")]
    (is (true? (:ok (run "stores" "init" "--name" "p"))))
    (spit (file "docs" "spec.md") (str/replace spec0 "Dos." "Dos, editado."))
    (spit (file "nuevo.md") "# Nuevo\n")
    (let [t0 (tree-digest *p*) g0 (git-state)
          r1 (anchors-since "--since" x)
          r2 (anchors-since)]
      (is (true? (:ok r1)))
      (is (true? (:ok r2)))
      (is (= :mutated (get (status r1) "p2")))
      (is (seq (:status g0)) "there were uncommitted changes to disturb")
      (is (= t0 (tree-digest *p*)))
      (is (= g0 (git-state))))))
