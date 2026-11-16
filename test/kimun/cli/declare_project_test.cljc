(ns kimun.cli.declare-project-test
  "`declare read|anchors` over the project folder and `declare add`
   (docs/v2/06, docs/v2/drafts/diseno-evaluar.md §9, cut 3a), in temporary
   directories."
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.test-util :as ktu]))

(def env {"USER" "tester"})

(defn- run [cwd & argv] (:envelope (main/run argv env (str cwd))))

(defn- listing [dir]
  (into (sorted-map) (map (fn [p] [(str (fs/relativize dir p)) (when (fs/regular-file? p) (slurp (str p)))])) (fs/glob dir "**")))

(defn- put! [dir rel text]
  (fs/create-dirs (fs/parent (fs/path dir rel)))
  (spit (str (fs/path dir rel)) text))

(defn- project []
  (let [dir (ktu/temp-dir "kimun-proj-")]
    (put! dir "kimun/a.clj" "(ns a)\n(relation isa \"d\")\n(relation anchors \"d\")\n(isa x Y)\n")
    (put! dir "kimun/sub/b.clj" "(ns b (:require [a :refer [isa]]))\n(isa z Y)\n")
    (put! dir "other.clj" "(ns other)\n(relation r \"d\")\n(r p q)\n")
    (put! dir "kimun/notes.txt" "not a declaration")
    dir))

(deftest read-without-files-reads-the-project-folder
  (let [dir (project)
        before (listing dir)
        e (run dir "declare" "read")]
    (is (true? (:ok e)))
    (is (= [["kimun/a.clj" "a"] ["kimun/sub/b.clj" "b"]] (mapv (juxt :file :context) (get-in e [:data :files]))) "recursive, sorted by path, relative to the cwd, only *.clj")
    (is (= ["a/isa"] (mapv :r (get-in e [:data :files 1 :propositions]))) "b uses the relation of a, which comes from the same folder")
    (is (= {:files 2 :relations 2 :propositions 2} (get-in e [:data :summary])))
    (is (= before (listing dir)) "nothing written")))

(deftest explicit-files-are-the-whole-set
  (let [dir (project)]
    (testing "other.clj alone: not the project"
      (let [e (run dir "declare" "read" "other.clj")]
        (is (= ["other.clj"] (mapv :file (get-in e [:data :files]))))))
    (testing "b alone, under kimun/: the context it requires is loaded as a dependency, and listed apart"
      (let [e (run dir "declare" "read" "kimun/sub/b.clj")]
        (is (= [["kimun/sub/b.clj"] ["kimun/a.clj"]] [(mapv :file (get-in e [:data :files])) (get-in e [:data :required])]))))
    (testing "outside kimun/ the set is only the files named"
      (put! dir "other2.clj" "(ns other2 (:require [a :refer [isa]]))\n")
      (let [e (run dir "declare" "read" "other2.clj")]
        (is (= ["declare/unknown-context" 3 "other2.clj" 1] [(get-in e [:error :type]) (:exit e) (get-in e [:error :data :file]) (get-in e [:error :data :line])]))))
    (testing "both named: the set is complete"
      (is (true? (:ok (run dir "declare" "read" "kimun/sub/b.clj" "kimun/a.clj")))))))

(deftest no-project-folder-or-an-empty-one-is-an-empty-answer
  (let [dir (ktu/temp-dir "kimun-proj-")]
    (doseq [step [identity (fn [d] (fs/create-dirs (fs/path d "kimun")))]]
      (step dir)
      (doseq [cmd ["read" "anchors"]]
        (let [e (run dir "declare" cmd)]
          (is (true? (:ok e)) cmd)
          (is (= [] (get-in e [:data :files])) cmd)
          (is (= 0 (get-in e [:data :summary :files])) cmd))))))

(deftest anchors-without-files-resolve-over-the-project
  (let [dir (project)]
    (put! dir "kimun/sub/c.clj" "(ns c (:require [a :refer [anchors]]))\n(anchors thing (at \"docs/s.md\" [\"S\"]))\n")
    (put! dir "docs/s.md" "# S\n\ntext\n")
    (let [e (run dir "declare" "anchors")]
      (is (true? (:ok e)) (pr-str (:error e)))
      (is (= ["kimun/a.clj" "kimun/sub/b.clj" "kimun/sub/c.clj"] (mapv :file (get-in e [:data :files]))))
      (is (= [["a/anchors" :resolved]] (mapv (juxt :r (comp keyword :status)) (get-in e [:data :anchors])))))))

(deftest add-appends-a-form-and-reports-what-is-new
  (let [dir (project)
        before (listing dir)
        e (run dir "declare" "add" "--to" "kimun/sub/b.clj" "(isa new Thing)")]
    (is (true? (:ok e)) (pr-str (:error e)))
    (is (= {:file "kimun/sub/b.clj" :added true :relations []} (select-keys (:data e) [:file :added :relations])))
    (is (= ["a/isa"] (mapv :r (get-in e [:data :propositions]))))
    (is (= {:file "kimun/sub/b.clj" :line 3} (get-in e [:data :propositions 0 :at])))
    (is (= (str (get before "kimun/sub/b.clj") "(isa new Thing)\n") (slurp (str (fs/path dir "kimun/sub/b.clj")))) "one form on a new last line")
    (is (= (dissoc before "kimun/sub/b.clj") (dissoc (listing dir) "kimun/sub/b.clj")) "no other file touched, no temporary left")
    (testing "a new relation counts as new"
      (let [e2 (run dir "declare" "add" "--to" "kimun/a.clj" "(relation summarizes \"d\")")]
        (is (= [true ["summarizes"] []] [(get-in e2 [:data :added]) (get-in e2 [:data :relations]) (get-in e2 [:data :propositions])]))))))

(deftest add-that-does-not-evaluate-writes-nothing
  (let [dir (project)
        before (listing dir)]
    (doseq [[form type] [["(nope x y)" "declare/unresolved"] ["(isa only-one)" "declare/arity"] ["(isa x" "declare/syntax"]]]
      (let [e (run dir "declare" "add" "--to" "kimun/sub/b.clj" form)]
        (is (false? (:ok e)) form)
        (is (= [type 3 "kimun/sub/b.clj"] [(get-in e [:error :type]) (:exit e) (get-in e [:error :data :file])]) form)
        (is (= before (listing dir)) "byte for byte the same")))
    (testing "evaluated with the project: a form that breaks ANOTHER file of the set is refused too"
      (let [e (run dir "declare" "add" "--to" "kimun/a.clj" "(relation isa \"another doc\")")]
        (is (= "declare/relation-redefined" (get-in e [:error :type])))
        (is (= before (listing dir)))))))

(deftest add-of-what-is-already-declared-writes-nothing
  (let [dir (project)
        before (listing dir)
        e (run dir "declare" "add" "--to" "kimun/a.clj" "(isa x Y)")]
    (is (true? (:ok e)))
    (is (= {:added false :propositions [] :relations []} (select-keys (:data e) [:added :propositions :relations])))
    (is (string? (get-in e [:data :reason])))
    (is (= before (listing dir)))))

(deftest add-with-context-creates-the-file
  (let [dir (project)]
    (testing "without the file and without --context it is usage"
      (is (= 6 (:exit (run dir "declare" "add" "--to" "kimun/new.clj" "(isa q r)"))))
      (is (not (fs/exists? (fs/path dir "kimun/new.clj")))))
    (testing "--context creates it with its ns, requiring what it needs"
      (let [e (run dir "declare" "add" "--to" "kimun/deep/new.clj" "--context" "n" "(relation r \"d\")")]
        (is (true? (:ok e)) (pr-str (:error e)))
        (is (= "(ns n)\n(relation r \"d\")\n" (slurp (str (fs/path dir "kimun/deep/new.clj")))))
        (is (= ["r"] (get-in e [:data :relations])))))
    (testing "no --to is usage"
      (is (= 6 (:exit (run dir "declare" "add" "(isa q r)")))))))
