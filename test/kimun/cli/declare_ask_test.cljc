(ns kimun.cli.declare-ask-test
  "`declare terms|about|members` (docs/v2/drafts/diseno-evaluar.md §9, cut 3b): read-only and
   storeless, over a throwaway git repository and over a directory without git."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [kimun.cli.main :as main]
            [kimun.host.hash :as hash]
            [kimun.test-util :as ktu]))

(def env {"USER" "tester"})
(def ^:dynamic *p* nil)
(defn- run-in [p & argv] (:envelope (main/run (vec argv) env p)))
(defn- run [& argv] (apply run-in *p* argv))
(defn- git! [d & args]
  (let [r (apply process/shell {:out :string :err :string :continue true :dir (str d)} "git" args)]
    (when-not (zero? (:exit r)) (throw (ex-info (str "git failed: " (:err r)) {:args args})))
    (str/trim (:out r))))

(def decl
  "(ns p.d)
(relation zork \"A zork B.\")
(relation wug \"A wugs B.\")
(zork (files \"src/ui/**/*.py\") UI.component)
(zork (files \"src/**/*.py\") Python)
(zork dash UI.view.dashboard)
(zork card UI.view)
(zork (at \"docs/spec.md\" [\"Spec\"]) decision)
(wug (zork dash UI.view.dashboard) owner)
")

(defn- populate! [p]
  (doseq [f ["src/ui/a.py" "src/ui/sub/b.py" "src/c.py" "docs/spec.md" "ignored/z.py"]]
    (fs/create-dirs (fs/parent (fs/path p f)))
    (spit (str (fs/path p f)) "x\n"))
  (spit (str (fs/path p "decl.clj")) decl))

(use-fixtures :each
  (fn [f]
    (let [p (str (ktu/temp-dir "kimun-ask-"))]
      (try (binding [*p* p] (populate! p) (f)) (finally (fs/delete-tree p))))))

(defn- init-git! [p]
  (git! p "init" "-q") (git! p "config" "user.email" "t@e") (git! p "config" "user.name" "t")
  (spit (str (fs/path p ".gitignore")) "ignored/\n")
  (git! p "add" "-A") (git! p "commit" "-qm" "base")
  (spit (str (fs/path p "src/untracked.py")) "u\n")
  (spit (str (fs/path p "ignored/new.py")) "i\n"))

(defn- tree-state [p]
  (into (sorted-map) (for [f (fs/glob p "**" {:hidden true}) :when (and (fs/regular-file? f) (not (re-find #"/\.git/" (str f))))]
                       [(str (fs/relativize p f)) (hash/sha256-hex (fs/read-all-bytes (str f)))])))

(deftest terms-lists-implicit-ancestors
  (let [r (run "declare" "terms" "decl.clj")
        by (into {} (map (juxt :term identity)) (get-in r [:data :terms]))]
    (is (true? (:ok r)) (pr-str r))
    (is (nil? (:store r)))
    (is (= {:count 1 :implicit false :ancestors ["UI.view" "UI"]} (select-keys (by "UI.view.dashboard") [:count :implicit :ancestors])))
    (is (= {:count 0 :implicit true} (select-keys (by "UI") [:count :implicit])))
    (is (= 1 (get-in r [:data :summary :implicit])))))

(deftest about-term-and-exact
  (let [rows (fn [& more] (get-in (apply run "declare" "about" "UI.view" "decl.clj" more) [:data :results]))]
    (is (= [[:term :b "dash" "UI.view.dashboard" "p.d/zork"] [:term :b "card" "UI.view" "p.d/zork"] [:proposition :a "owner" nil "p.d/wug"]]
           (map (fn [x] [(:via x) (:side x) (some-> x :other :term) (:matched x) (-> x :proposition :r)]) (rows)))
        "the side, the opposite end and the matched term; then the wug whose term is the zork (via proposition)")
    (is (= ["card"] (map #(-> % :other :term) (rows "--exact"))))))

(deftest about-path-by-anchor-and-patterns-in-git-and-outside
  (init-git! *p*)
  (let [r (run "declare" "about" "--path" "src/ui/a.py" "decl.clj")
        rows (get-in r [:data :results])]
    (is (true? (:ok r)) (pr-str r))
    (is (= [["src/ui/**/*.py" "UI.component"] ["src/**/*.py" "Python"]]
           (map (juxt :pattern (comp :term :other)) rows))
        "two classes by two patterns, nothing declared per file")
    (is (= ["decision"] (map (comp :term :other) (get-in (run "declare" "about" "--path" "docs/spec.md" "decl.clj") [:data :results]))))
    (is (= "src/ui/a.py" (get-in r [:data :path]))))
  (testing "RUTA is relative to the working directory and answered relative to the root (the git top, here)"
    (let [src (str (fs/path *p* "src"))
          r (run-in src "declare" "about" "--path" "ui/a.py" "../decl.clj")]
      (is (= 2 (count (get-in r [:data :results]))) "from src/, ui/a.py is src/ui/a.py of the project")
      (is (= "src/ui/a.py" (get-in r [:data :path])))
      (is (= (str (fs/real-path *p*)) (get-in r [:data :root])))
      (is (= ["decision"] (map (comp :term :other) (get-in (run-in src "declare" "about" "--path" "../docs/spec.md" "../decl.clj") [:data :results]))))
      (is (= "cli/usage" (str (get-in (run-in src "declare" "about" "--path" "../../x.py" "../decl.clj") [:error :type]))) "outside the root")))
  (testing "without git it is the same, the file is never opened"
    (let [q (str (ktu/temp-dir "kimun-ask-nogit-"))]
      (try (spit (str (fs/path q "decl.clj")) decl)
           (is (= 2 (count (get-in (run-in q "declare" "about" "--path" "src/ui/a.py" "decl.clj") [:data :results]))))
           (finally (fs/delete-tree q))))))

(deftest members-from-git-and-from-a-walk
  (init-git! *p*)
  (let [r (run "declare" "members" "src/**/*.py")]
    (is (true? (:ok r)) (pr-str r))
    (is (= :git (get-in r [:data :files-from])))
    (is (= ["src/c.py" "src/ui/a.py" "src/ui/sub/b.py" "src/untracked.py"] (get-in r [:data :members]))
        "tracked and untracked, not the ignored ones")
    (is (= ["src/ui/a.py"] (get-in (run "declare" "members" "src/ui/*.py") [:data :members]))))
  (testing "deleted tracked files are not members"
    (fs/delete (fs/path *p* "src/c.py"))
    (is (not-any? #{"src/c.py"} (get-in (run "declare" "members" "src/**/*.py") [:data :members]))))
  (testing "outside git the directory is walked, without .git and .kimun"
    (let [q (str (ktu/temp-dir "kimun-ask-walk-"))]
      (try (doseq [f ["src/a.py" ".kimun/x.py" ".git/y.py" "src/.hid/h.py"]]
             (fs/create-dirs (fs/parent (fs/path q f))) (spit (str (fs/path q f)) "x"))
           (let [r (run-in q "declare" "members" "**/*.py")]
             (is (= :walk (get-in r [:data :files-from])))
             (is (= ["src/.hid/h.py" "src/a.py"] (get-in r [:data :members]))))
           (finally (fs/delete-tree q))))))

(deftest usage-and-no-writes
  (init-git! *p*)
  (let [before (tree-state *p*)]
    (doseq [argv [["declare" "terms" "decl.clj"] ["declare" "about" "dash" "decl.clj"]
                  ["declare" "about" "--path" "src/c.py" "decl.clj"] ["declare" "members" "**"]]]
      (is (true? (:ok (apply run argv))) (pr-str argv)))
    (is (= before (tree-state *p*)) "nothing is written, no store is created")
    (is (not (fs/exists? (fs/path *p* ".kimun")))))
  (testing "usage errors"
    (doseq [argv [["declare" "about"] ["declare" "members"] ["declare" "members" "a" "b"]
                  ["declare" "about" "--path" "src/c.py" "--exact" "decl.clj"]]]
      (let [r (apply run argv)]
        (is (false? (:ok r)) (pr-str argv))
        (is (= "cli/usage" (str (get-in r [:error :type]))) (pr-str r))))))
