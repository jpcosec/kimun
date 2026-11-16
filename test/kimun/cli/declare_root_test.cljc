(ns kimun.cli.declare-root-test
  "The root of the project of `declare` (docs/v2/drafts/diseno-evaluar.md §11): the six commands
   work from the nearest directory, going up from the working directory, that holds `kimun/`;
   else from the top of the git work tree; else from the working directory. Terms and `about`
   without FILE read the project (two contexts that require each other)."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.test-util :as ktu]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run (vec argv) env (str cwd))))
(defn- put! [dir rel text]
  (fs/create-dirs (fs/parent (fs/path dir rel)))
  (spit (str (fs/path dir rel)) text))
(defn- git! [d & args] (apply process/shell {:out :string :err :string :dir (str d)} "git" args))
(defn- init-git! [d] (git! d "init" "-q") (git! d "config" "user.email" "t@e") (git! d "config" "user.name" "t"))

(defn- project []
  (let [d (ktu/temp-dir "kimun-root-")]
    (put! d "kimun/a.clj" "(ns a)\n(relation isa \"d\")\n(isa (files \"src/**/*.py\") UI.view.dashboard)\n(isa (at \"docs/s.md\") Spec)\n")
    (put! d "kimun/sub/b.clj" "(ns b (:require [a :refer [isa]]))\n(isa card UI.view)\n")
    (put! d "src/x/m.py" "x\n") (put! d "docs/s.md" "# S\n\ntext\n") (put! d "deep/er/.keep" "")
    d))

(defn- data [e] (dissoc (:data e) :root))

(deftest six-commands-from-a-subdirectory-equal-those-from-the-root
  (let [d (project) sub (fs/path d "deep" "er")
        up "../../"
        same? (fn [name f-root f-sub]
                (let [a (apply run d f-root) b (apply run sub f-sub)]
                  (is (true? (:ok a)) (pr-str a)) (is (true? (:ok b)) (pr-str b))
                  (is (= (data a) (data b)) name)
                  (is (= (str (fs/real-path d)) (get-in b [:data :root])) (str name " says its root"))))]
    (same? "read" ["declare" "read"] ["declare" "read"])
    (same? "anchors" ["declare" "anchors"] ["declare" "anchors"])
    (same? "terms" ["declare" "terms"] ["declare" "terms"])
    (same? "about" ["declare" "about" "UI.view"] ["declare" "about" "UI.view"])
    (same? "about --path" ["declare" "about" "--path" "src/x/m.py"] ["declare" "about" "--path" (str up "src/x/m.py")])
    (same? "members" ["declare" "members" "src/**/*.py"] ["declare" "members" "src/**/*.py"])
    (testing "files given are relative to the cwd and named relative to the root"
      (is (= ["kimun/a.clj"] (mapv :file (get-in (run sub "declare" "read" (str up "kimun/a.clj")) [:data :files])))))
    (testing "add --to is relative to the cwd; the answer names the file from the root"
      (let [e (run sub "declare" "add" "--to" (str up "kimun/a.clj") "(isa newbie UI.view)")]
        (is (true? (:ok e)) (pr-str e))
        (is (= "kimun/a.clj" (get-in e [:data :file])))
        (is (re-find #"newbie" (slurp (str (fs/path d "kimun/a.clj")))))))))

(deftest terms-and-about-without-file-read-the-project
  (let [d (project)
        t (run d "declare" "terms")
        by (into {} (map (juxt :term identity)) (get-in t [:data :terms]))
        rows (get-in (run d "declare" "about" "UI.view") [:data :results])]
    (is (= 1 (:count (by "UI.view"))) "UI.view is written in b")
    (is (true? (:implicit (by "UI"))))
    (is (= ["a/isa" "a/isa"] (map (comp :r :proposition) rows)) "both contexts, the relation of a used from b")
    (is (= ["kimun/a.clj" "kimun/sub/b.clj"] (map (comp :file :at :proposition) rows)))
    (is (= ["kimun/a.clj"] (get-in (run d "declare" "terms" "kimun/a.clj") [:data :files])) "with FILE, only those")))

(deftest root-without-kimun-is-the-git-top-and-without-git-the-cwd
  (testing "git repository, no kimun/ anywhere: the top of the work tree"
    (let [d (ktu/temp-dir "kimun-root-git-")]
      (init-git! d)
      (put! d "decl.clj" "(ns g)\n(relation r \"d\")\n(r (files \"src/*.py\") C)\n")
      (put! d "src/a.py" "x") (put! d "src/deep/b.py" "x")
      (let [e (run (fs/path d "src") "declare" "members" "src/*.py")]
        (is (= (str (fs/real-path d)) (get-in e [:data :root])))
        (is (= ["src/a.py"] (get-in e [:data :members])))
        (is (= :git (get-in e [:data :files-from]))))
      (is (= 0 (count (get-in (run (fs/path d "src" "deep") "declare" "about" "--path" "b.py" "../../decl.clj") [:data :results])))
          "src/deep/b.py is not reached by src/*.py")
      (is (= 1 (count (get-in (run (fs/path d "src") "declare" "about" "--path" "a.py" "../decl.clj") [:data :results]))))))
  (testing "no git, no kimun/: the cwd"
    (let [d (ktu/temp-dir "kimun-root-none-")]
      (put! d "src/a.py" "x") (put! d ".kimun/z.py" "x") (put! d "w.clj" "(ns w)\n(relation r \"d\")\n(r q S)\n")
      (let [e (run d "declare" "members" "**/*.py")]
        (is (= (str (fs/real-path d)) (get-in e [:data :root])))
        (is (= [:walk ["src/a.py"]] [(get-in e [:data :files-from]) (get-in e [:data :members])])))
      (is (= {:ok true :n 0} (let [e (run d "declare" "terms")] {:ok (:ok e) :n (count (get-in e [:data :terms]))}))
          "no kimun/ folder: an empty project, nothing fails")
      (is (= 2 (count (get-in (run d "declare" "terms" "w.clj") [:data :terms]))) "q and S, from the file given"))))
