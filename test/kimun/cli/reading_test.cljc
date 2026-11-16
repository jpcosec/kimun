(ns kimun.cli.reading-test
  "`declare read|terms|about|anchors|generate` with readers (docs/v2/drafts/diseno-leer.md, cut 1),
   over throwaway projects with and without git: the commands that ask include what the readers
   read; `--declared-only` runs none; nothing is written."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.test-util :as ktu]))

(def env {"USER" "tester"})
(defn- run [p & argv] (:envelope (main/run (vec argv) env (str p))))
(defn- git! [d & args]
  (let [r (apply process/shell {:out :string :err :string :continue true :dir (str d)} "git" args)]
    (when-not (zero? (:exit r)) (throw (ex-info (str "git failed: " (:err r)) {:args args})))))

(defn- put! [p rel text]
  (fs/create-dirs (fs/parent (fs/path p rel)))
  (spit (str (fs/path p rel)) text))

(def decl
  "(ns proy.tareas)
(relation isa \"A es de la clase B.\")
(relation uses \"A usa B.\")
(relation anchors \"A esta en B.\")
(defn leer [doc]
  (let [task (symbol (get (:front doc) \"id\"))]
    (isa task Task)
    (doseq [a (get (:front doc) \"atoms\")] (uses task (symbol a)))
    (anchors task (at (:path doc) [\"Meta\"]))))
(reading (files \"tasks/*.md\") leer)
(reading (files \"tasks/*.md\") (fn [doc] (isa (symbol (get (:front doc) \"id\")) Item)))
(isa (generated \"out/lista.md\"
       (fn [ask] (str \"# Tareas\\n\" (apply str (for [r ((:about ask) 'Task)] (str \"- \" (:term (:a (:proposition r))) \"\\n\"))))))
     Vista)
")

(defn- project
  "Tasks t1 and t2, a bad one, a file in kimun/ and one in out/ that the patterns would catch."
  [git?]
  (let [p (fs/real-path (ktu/temp-dir "kimun-read-"))]
    (put! p "kimun/tareas.clj" decl)
    (put! p "tasks/t1.md" "---\nid: t1\natoms: [a1, a2]\n---\n# Meta\n\nuno\n")
    (put! p "tasks/t2.md" "---\nid: t2\natoms: [a1]\n---\n# Meta\n\ndos\n")
    (put! p "tasks/bad.md" "sin frontmatter\n")
    (put! p "README.md" "---\nid: nope\n---\n")
    (when git?
      (git! p "init" "-q") (git! p "config" "user.email" "t@e") (git! p "config" "user.name" "t")
      (git! p "add" "-A") (git! p "commit" "-qm" "base"))
    p))

(defn- listing [p]
  (into (sorted-map)
        (for [f (fs/glob p "**" {:hidden true})
              :when (and (fs/regular-file? f) (not (re-find #"(^|/)\.git/" (str (fs/relativize p f)))))]
          [(str (fs/relativize p f)) (slurp (str f))])))

(defn- term-pairs [e] (set (map (fn [p] [(get-in p [:a :term]) (:r p) (get-in p [:b :term])]) (get-in e [:data :read-propositions]))))

(deftest read-shows-what-the-readers-read
  (doseq [git? [true false]]
    (let [p (project git?)
          before (listing p)
          e (run p "declare" "read")
          d (:data e)]
      (is (:ok e) (pr-str e))
      (is (= #{["t1" "proy.tareas/isa" "Task"] ["t2" "proy.tareas/isa" "Task"]
               ["t1" "proy.tareas/uses" "a1"] ["t1" "proy.tareas/uses" "a2"] ["t2" "proy.tareas/uses" "a1"]
               ["t1" "proy.tareas/isa" "Item"] ["t2" "proy.tareas/isa" "Item"]}
             (into #{} (remove #(= "proy.tareas/anchors" (second %))) (term-pairs e)))
          "two readers on the same files, each class")
      (is (= {"t1" "tasks/t1.md" "t2" "tasks/t2.md"}
             (into {} (for [x (:read-propositions d) :when (= "proy.tareas/anchors" (:r x))]
                        [(get-in x [:a :term]) (:path (:source x))])))
          "an (at …) emitted by a reader is a node of a proposition, with its source")
      (is (= [{:matched 3 :propositions 7 :errors 1} {:matched 3 :propositions 2 :errors 1}]
             (map #(select-keys % [:matched :propositions :errors]) (:readings d))))
      (is (= [{:path "tasks/bad.md" :type (keyword "declare" "reading")} {:path "tasks/bad.md" :type (keyword "declare" "reading")}]
             (map #(select-keys % [:path :type]) (:reading-errors d))))
      (is (= {:files 1 :relations 3 :propositions 1 :readings 2 :read-propositions 9 :reading-errors 2} (:summary d)))
      (is (= before (listing p)) "nothing written"))))

(deftest declared-only-runs-no-reader
  (let [p (project true)
        d (:data (run p "declare" "read" "--declared-only"))]
    (is (= [] (:read-propositions d)))
    (is (= [] (:reading-errors d)))
    (is (= [{:pattern "tasks/*.md" :at {:file "kimun/tareas.clj" :line 10} :context "proy.tareas"}
            {:pattern "tasks/*.md" :at {:file "kimun/tareas.clj" :line 11} :context "proy.tareas"}]
           (:readings d)) "the readings, with no counts")
    (is (= 0 (get-in d [:summary :read-propositions])))))

(deftest terms-and-about-include-what-was-read
  (let [p (project false)
        terms (get-in (run p "declare" "terms") [:data :terms])
        by (into {} (map (juxt :term :count)) terms)]
    (is (= {"t1" 5 "t2" 4 "a1" 2 "Task" 2} (select-keys by ["t1" "t2" "a1" "Task"])) "t1: isa, 2 uses, anchors, isa Item = 5")
    (let [e (run p "declare" "about" "a1")]
      (is (= #{"t1" "t2"} (set (map #(get-in % [:proposition :a :term]) (get-in e [:data :results]))))))
    (testing "the reading errors are not silent"
      (is (= 2 (count (get-in (run p "declare" "terms") [:data :reading-errors])))))))

(deftest about-path-lists-what-a-file-says
  (let [p (project true)
        e (run p "declare" "about" "--path" "tasks/t1.md")
        rows (get-in e [:data :results])]
    (is (:ok e) (pr-str e))
    (is (= #{:source :anchor} (set (map :via rows))))
    (is (= #{"isa" "uses" "anchors"} (into #{} (comp (filter #(= :source (:via %))) (map #(last (str/split (get-in % [:proposition :r]) #"/")))) rows)))
    (is (= 5 (count (filter #(= :source (:via %)) rows))))
    (testing "the (at …) a reader emitted is anchored on the file"
      (is (= ["t1"] (map #(get-in % [:proposition :a :term]) (filter #(= :anchor (:via %)) rows)))))
    (is (= [] (get-in (run p "declare" "about" "--path" "tasks/bad.md") [:data :results])))))

(deftest anchors-count-the-ones-readers-emit
  (let [p (project true)
        e (run p "declare" "anchors")
        rows (get-in e [:data :anchors])]
    (is (:ok e) (pr-str e))
    (is (= 2 (count rows)))
    (is (= #{:resolved} (set (map :status rows))) "# Meta exists in both")
    (is (= #{"tasks/t1.md" "tasks/t2.md"} (set (map #(get-in % [:anchor :path]) rows))))))

(deftest generate-recipes-see-what-was-read
  (let [p (project true)
        e (run p "declare" "generate")]
    (is (:ok e) (pr-str e))
    (is (= "# Tareas\n- t1\n- t2\n" (slurp (str (fs/path p "out/lista.md")))))
    (testing "the generated file is not read as a source, even when a pattern reaches it, and neither is kimun/"
      (put! p "out/x.md" "---\nid: x\n---\n")
      (let [r (run p "declare" "read")]
        (is (= #{"t1" "t2"} (into #{} (keep #(when (= "proy.tareas/isa" (:r %)) (get-in % [:a :term]))) (get-in r [:data :read-propositions]))))
        (is (= #{"tasks/t1.md" "tasks/t2.md"} (into #{} (map #(get-in % [:source :path])) (get-in r [:data :read-propositions]))))))))

(deftest a-pattern-reaching-generated-and-declarations-reads-neither
  (let [p (fs/real-path (ktu/temp-dir "kimun-read-"))]
    (put! p "kimun/a.clj" "(ns a)
(relation saw \"d\")
(reading (files \"**/*.md\") (fn [doc] (saw (symbol (:path doc)) File)))
(saw (generated \"out/g.md\" (fn [ask] \"hola\\n\")) File)")
    (put! p "kimun/notes.md" "x")
    (put! p "docs/a.md" "x")
    (run p "declare" "generate")
    (is (= "hola\n" (slurp (str (fs/path p "out/g.md")))))
    (is (= #{"docs/a.md"} (into #{} (map #(get-in % [:source :path])) (get-in (run p "declare" "read") [:data :read-propositions]))))))

(deftest a-reader-that-never-ends-does-not-stop-the-others
  (let [p (fs/real-path (ktu/temp-dir "kimun-read-"))]
    (put! p "kimun/a.clj" "(ns a)
(relation saw \"d\")
(reading (files \"d/*.md\") (fn [doc] (when (= \"d/b.md\" (:path doc)) (loop [] (recur))) (saw (symbol (:path doc)) File)))")
    (put! p "d/a.md" "x") (put! p "d/b.md" "x") (put! p "d/c.md" "x")
    (let [e (main/run ["declare" "read"] {"USER" "t" "KIMUN_DECLARE_TIMEOUT_MS" "400"} (str p))
          d (get-in e [:envelope :data])]
      (is (:ok (:envelope e)))
      (is (= ["d/a.md" "d/c.md"] (map #(get-in % [:a :term]) (:read-propositions d))))
      (is (= [{:path "d/b.md" :type (keyword "declare" "timeout")}] (map #(select-keys % [:path :type]) (:reading-errors d)))))))

(deftest called-from-another-namespace
  (let [p (project true)
        r (binding [*ns* (create-ns 'some.other.ns)] (run p "declare" "read"))]
    (is (= 9 (get-in r [:data :summary :read-propositions])))))
