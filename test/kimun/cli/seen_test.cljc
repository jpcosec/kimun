(ns kimun.cli.seen-test
  "The pointer \"I looked up to this commit\" (`stores seen`, `<store>/seen.edn`) and
   `stores changed` without `--since`. Every project is a throwaway git repository."
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
(defn- commit! [msg] (git! *p* "add" "notes") (git! *p* "commit" "-qm" msg) (git! *p* "rev-parse" "HEAD"))
(defn- paths [r] (mapv :path (get-in r [:data :changed])))
(defn- strip [r] (update r :data dissoc :pointer))

(defn- tree-digest [dir]
  (into (sorted-map)
        (for [f (file-seq (fs/file dir)) :when (.isFile f)]
          [(str (fs/relativize dir f)) (hash/sha256-hex (fs/read-all-bytes f))])))

(use-fixtures :each
  (fn [f]
    (let [p (str (ktu/temp-dir "kimun-seen-"))]
      (try (binding [*p* p]
             (fs/create-dirs (str p "/notes"))
             (git! p "init" "-q") (git! p "config" "user.email" "t@e") (git! p "config" "user.name" "t")
             (spit (str p "/notes/a.md") "# A\n\nUno.\n")
             (is (true? (:ok (run-in p "stores" "init" "--name" "p"))))
             (is (true? (:ok (run-in p "docs" "ingest" "notes/a.md"))))
             (f))
           (finally (fs/delete-tree p))))))

(deftest no-pointer-is-a-declared-usage-error
  (commit! "base")
  (let [r (run "stores" "changed")]
    (is (false? (:ok r)))
    (is (= "cli/usage" (get-in r [:error :type])) (pr-str r))
    (is (str/includes? (get-in r [:error :message]) "stores seen --set"))
    (is (= {:commit nil :behind false} (select-keys (:data (run "stores" "seen")) [:commit :behind]))))
  (is (not (fs/exists? (file ".kimun" "seen.edn")))))

(deftest pointer-drives-changed
  (let [x (commit! "base")
        s (run "stores" "seen" "--set" "HEAD")]
    (is (true? (:ok s)) (pr-str s))
    (is (= {:previous nil :commit x :head x :behind false} (:data s)))
    (is (= {:commit x} (read-string (slurp (file ".kimun" "seen.edn")))) "the file holds the sha and nothing else")
    (spit (file "notes" "a.md") "# A\n\nUno, editado.\n")
    (let [by-pointer (run "stores" "changed") by-since (run "stores" "changed" "--since" x)]
      (is (= ["notes/a.md"] (paths by-pointer)))
      (is (= x (get-in by-pointer [:data :pointer]) (get-in by-since [:data :pointer])))
      (is (= (strip by-pointer) (strip by-since))))
    (testing "a new commit leaves the pointer behind, and changed shows it"
      (let [y (commit! "second")
            sn (run "stores" "seen")]
        (is (= {:commit x :head y :behind true} (:data sn)))
        (is (= ["notes/a.md"] (paths (run "stores" "changed"))))
        (is (= x (get-in (run "stores" "changed") [:data :since])))
        (testing "--since does not move the pointer"
          (run "stores" "changed" "--since" y)
          (is (= x (:commit (:data (run "stores" "seen"))))))
        (testing "seen --set again: nothing left to see"
          (let [s2 (run "stores" "seen" "--set" "HEAD")]
            (is (= {:previous x :commit y :head y :behind false} (:data s2))))
          (is (= [] (paths (run "stores" "changed")))))))))

(deftest dirty-tree-stays-visible-with-pointer-at-head
  (commit! "base")
  (run "stores" "seen" "--set" "HEAD")
  (spit (file "notes" "a.md") "# A\n\nSin commit.\n")
  (spit (file "notes" "new.md") "# N\n")
  (let [r (run "stores" "changed")]
    (is (= ["notes/a.md" "notes/new.md"] (paths r)))
    (is (true? (get-in r [:data :dirty])))))

(deftest set-writes-only-the-pointer
  (let [x (commit! "base")]
    (spit (file "notes" "a.md") "# A\n\nSucio.\n")
    (let [k0 (tree-digest (file ".kimun"))
          g0 (git! *p* "status" "--porcelain")
          s (run "stores" "seen" "--set" x)
          k1 (tree-digest (file ".kimun"))]
      (is (true? (:ok s)) (pr-str s))
      (is (= (set (keys k1)) (conj (set (keys k0)) "seen.edn")))
      (is (= k0 (dissoc k1 "seen.edn")) "every other file of the store is identical")
      (is (= g0 (git! *p* "status" "--porcelain")))
      (is (seq g0)))
    (testing "a read changes nothing"
      (let [k (tree-digest (file ".kimun"))]
        (run "stores" "seen") (run "stores" "changed")
        (is (= k (tree-digest (file ".kimun"))))))))

(deftest damaged-pointer-is-declared
  (let [x (commit! "base")]
    (run "stores" "seen" "--set" "HEAD")
    (testing "unreadable"
      (spit (file ".kimun" "seen.edn") "{:commit")
      (doseq [argv [["stores" "seen"] ["stores" "changed"]]]
        (is (= "git/bad-pointer" (get-in (apply run argv) [:error :type])) (pr-str argv)))
      (is (true? (:ok (run "stores" "changed" "--since" x))) "an explicit --since does not need it"))
    (testing "a sha git does not know"
      (spit (file ".kimun" "seen.edn") (pr-str {:commit (apply str (repeat 40 "a"))}))
      (is (= "git/bad-pointer" (get-in (run "stores" "changed") [:error :type]))))
    (testing "seen --set repairs it"
      (is (true? (:ok (run "stores" "seen" "--set" "HEAD"))))
      (is (= x (:commit (:data (run "stores" "seen"))))))))

(deftest include-build-leaves-the-pointer-at-head
  (testing "in a git repository with commits"
    (let [p (str (ktu/temp-dir "kimun-seen-inc-"))]
      (try
        (git! p "init" "-q") (git! p "config" "user.email" "t@e") (git! p "config" "user.name" "t")
        (spit (str p "/kimun.include.edn") "{:include/version 1 :store {:path \".kimun\" :git :ignore} :models [] :documents []}")
        (git! p "add" "kimun.include.edn") (git! p "commit" "-qm" "decl")
        (let [h (git! p "rev-parse" "HEAD") r (run-in p "include" "build")]
          (is (true? (:ok r)) (pr-str r))
          (is (= {:commit h} (read-string (slurp (str p "/.kimun/seen.edn")))))
          (is (= h (:commit (:data (run-in p "stores" "seen")))))
          (testing "--rebuild too, after the repository moved"
            (spit (str p "/f.txt") "x") (git! p "add" "f.txt") (git! p "commit" "-qm" "more")
            (is (true? (:ok (run-in p "include" "build" "--rebuild"))))
            (is (= (git! p "rev-parse" "HEAD") (:commit (:data (run-in p "stores" "seen")))))))
        (finally (fs/delete-tree p)))))
  (testing "a repository without commits: no pointer and no failure (include build needs git anyway)"
    (let [p (str (ktu/temp-dir "kimun-seen-nocommit-"))]
      (try
        (git! p "init" "-q")
        (spit (str p "/kimun.include.edn") "{:include/version 1 :store {:path \".kimun\" :git :ignore} :models [] :documents []}")
        (let [r (run-in p "include" "build")]
          (is (true? (:ok r)) (pr-str r)))
        (is (not (fs/exists? (str p "/.kimun/seen.edn"))))
        (finally (fs/delete-tree p))))))
