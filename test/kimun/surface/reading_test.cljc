(ns kimun.surface.reading-test
  "Reading the sources by class (docs/v2/drafts/diseno-leer.md, cut 1): the vocabulary
   `(reading (files p) reader)` of the evaluator and the pure running of the readers of
   `kimun.surface.reading`, over files that are strings."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.host.default :as host]
            [kimun.surface.declare :as declare]
            [kimun.surface.propositions :as props]
            [kimun.surface.reading :as reading]))

(defn- project [& files]
  (declare/read-project host/host (mapv (fn [[f t]] {:file f :text t}) (partition 2 files))))

(defn- failure [& files]
  (try (apply project files) nil
       (catch clojure.lang.ExceptionInfo e (assoc (ex-data e) :message (ex-message e)))))

(defn- run-readers
  "`reading/run` of the readers of `results` over the files `fs` (path → text)."
  ([results fs] (run-readers results fs {}))
  ([results fs opts]
   (reading/run (declare/readers results) (keys fs) (merge {:read-now fs} opts))))

(defn- plain [p] (select-keys p [:r :a :b :context :source]))

(def base
  "Relations and the reader of §3 of the design."
  "(ns desk.tareas)

(relation isa \"A es de la clase B.\")
(relation depends-on \"A no puede cerrarse antes que B.\")
(relation uses \"A se apoya en el átomo B.\")
(relation tagged \"A lleva la etiqueta B.\")

(reading (files \"desk/tasks/task-*.md\")
  (fn [doc]
    (let [task (symbol (get (:front doc) \"id\"))]
      (isa task Task)
      (doseq [d (get (:front doc) \"depends_on\")] (depends-on task (symbol d)))
      (doseq [a (get (:front doc) \"atoms\")]      (uses task (symbol a)))
      (doseq [t (get (:front doc) \"tags\")]       (tagged task (symbol t))))))
")

(def files
  {"desk/tasks/task-1.md" "---\nid: task-1\ndepends_on: [task-0]\natoms: [atom-a, atom-b]\ntags: [topic:x]\n---\n# One\n"
   "desk/tasks/task-0.md" "---\nid: task-0\n---\n# Zero\n"
   "desk/tasks/task-bad.md" "no frontmatter\n"
   "README.md" "---\nid: nope\n---\n"})

(defn- t [s] {:term s})

(deftest the-example-of-the-design-gives-the-hand-written-propositions
  (let [rs (project "kimun/tareas.clj" base)
        out (run-readers rs files)
        src (fn [p] {:source {:path p}})
        row (fn [r a b p] (merge {:r (str "desk.tareas/" r) :a (t a) :b (t b) :context "desk.tareas"} (src p)))]
    (testing "evaluating declares the readings and emits nothing"
      (is (= [] (:propositions (first rs))))
      (is (= [{:pattern "desk/tasks/task-*.md" :at {:file "kimun/tareas.clj" :line 8} :context "desk.tareas"}]
             (:readings (first rs)))))
    (testing "the propositions, in order of path (task-bad.md has no `id`: its reader fails, the others go on)"
      (is (= [(row "isa" "task-0" "Task" "desk/tasks/task-0.md")
              (row "isa" "task-1" "Task" "desk/tasks/task-1.md")
              (row "depends-on" "task-1" "task-0" "desk/tasks/task-1.md")
              (row "uses" "task-1" "atom-a" "desk/tasks/task-1.md")
              (row "uses" "task-1" "atom-b" "desk/tasks/task-1.md")
              (row "tagged" "task-1" "topic:x" "desk/tasks/task-1.md")]
             (map plain (:emitted out))))
      (is (= {:file "kimun/tareas.clj" :line 11} (:at (first (:emitted out)))) "the position is the one of the declaration file")
      (is (= [{:path "desk/tasks/task-bad.md" :type :declare/reading}]
             (map #(select-keys % [:path :type]) (:errors out)))))
    (testing "the matched count per reader, and README.md never matched"
      (is (= [{:matched 3 :propositions 6 :errors 1}] (map #(select-keys % [:matched :propositions :errors]) (:readings out)))))))

(deftest the-id-is-the-one-of-a-proposition-written-by-hand
  (let [rs (project "kimun/tareas.clj" (str base "\n(isa task-0 Task)\n"))
        out (run-readers rs (select-keys files ["desk/tasks/task-0.md"]))
        declared (:propositions (first rs))
        merged (reading/combine declared (:emitted out))]
    (is (= (:id (first declared)) (:id (first (:emitted out)))))
    (is (= 1 (count merged)) "declared and read are ONE proposition")
    (is (= (:at (first declared)) (:at (first merged))) "keeps the position of the declaration by hand")
    (is (= [{:path "desk/tasks/task-0.md"}] (:sources (first merged))))
    (is (= {:path "desk/tasks/task-0.md"} (:source (first merged))))
    (is (some #{{:file "kimun/tareas.clj" :line 11}} (map #(select-keys % [:file :line]) (:also-at (first merged))))
        "the position of the reader goes to :also-at")
    (is (nil? (:source (first declared))) "the declared one alone has no source")))

(deftest two-files-saying-the-same-are-one-proposition-with-two-sources
  (let [rs (project "kimun/c.clj"
                    "(ns c) (relation r \"d\") (reading (files \"d/*.md\") (fn [doc] (r (symbol \"x\") y)))")
        out (run-readers rs {"d/b.md" "b" "d/a.md" "a"})
        merged (reading/combine [] (:emitted out))]
    (is (= 2 (count (:emitted out))))
    (is (= 1 (count merged)))
    (is (= [{:path "d/a.md"} {:path "d/b.md"}] (:sources (first merged))))
    (is (= {:path "d/a.md"} (:source (first merged))))
    (is (nil? (:also-at (first merged))) "the same line of the reader is not a second position")))

(deftest several-readers-on-one-file-each-adds-its-own
  (let [rs (project "kimun/c.clj"
                    "(ns c) (relation isa \"d\")
(reading (files \"d/*.md\") (fn [doc] (isa (symbol (:path doc)) Doc)))
(reading (files \"d/a.md\") (fn [doc] (isa (symbol (:path doc)) Special)))
(reading (files \"**/a.md\") (fn [doc] nil))")
        out (run-readers rs {"d/a.md" "a" "d/b.md" "b"})]
    (is (= [["d/a.md" "Doc"] ["d/b.md" "Doc"] ["d/a.md" "Special"]]
           (map (fn [p] [(get-in p [:a :term]) (get-in p [:b :term])]) (:emitted out))))
    (is (= [2 1 1] (map :matched (:readings out))))
    (is (= [0 0 0] (map :errors (:readings out))))))

(deftest a-failing-reader-leaves-only-that-pair-out
  (testing "throws, endless loop, invalid emission, unreadable file; the rest is read"
    (let [rs (project "kimun/c.clj"
                      "(ns c) (relation isa \"d\")
(reading (files \"t/*.md\")
  (fn [doc]
    (isa (symbol (:path doc)) Ok)
    (case (:path doc)
      \"t/boom.md\" (throw (ex-info \"boom\" {}))
      \"t/loop.md\" (loop [] (recur))
      \"t/bad.md\" (isa [1 2] X)
      \"t/missing.md\" nil
      nil)))")
          out (run-readers rs {"t/ok.md" "x" "t/boom.md" "x" "t/loop.md" "x" "t/bad.md" "x"}
                           {:timeout-ms 300
                            :read-now (fn [p] (get {"t/ok.md" "x" "t/boom.md" "x" "t/loop.md" "x" "t/bad.md" "x"} p))})
          out (reading/run (declare/readers rs) ["t/ok.md" "t/boom.md" "t/loop.md" "t/bad.md" "t/missing.md"]
                           {:timeout-ms 300 :read-now #(get {"t/ok.md" "x" "t/boom.md" "x" "t/loop.md" "x" "t/bad.md" "x"} %)})
          errs (into {} (map (juxt :path identity)) (:errors out))]
      (is (= ["t/ok.md"] (map #(get-in % [:a :term]) (:emitted out)))
          "what the failing readers emitted before failing is dropped too")
      (is (= {"t/boom.md" :declare/reading "t/loop.md" :declare/timeout "t/bad.md" :declare/bad-node "t/missing.md" :declare/unreadable}
             (update-vals errs :type)))
      (is (str/includes? (:message (get errs "t/boom.md")) "boom"))
      (is (= {:file "kimun/c.clj" :line 2} (:at (get errs "t/boom.md"))))
      (is (= "t/*.md" (:pattern (get errs "t/loop.md")))))))

(deftest the-return-value-of-a-reader-is-ignored
  (let [rs (project "kimun/c.clj"
                    "(ns c) (relation isa \"d\")
(reading (files \"*.md\") (fn [doc] (isa a B) {:garbage 1}))
(reading (files \"*.md\") (fn [doc] 42))
(reading (files \"*.md\") (fn [doc] (isa a B) (isa (symbol \"c\") D) nil))")
        out (run-readers rs {"x.md" "x"})]
    (is (empty? (:errors out)))
    (is (= [["a" "B"] ["a" "B"] ["c" "D"]] (map (fn [p] [(get-in p [:a :term]) (get-in p [:b :term])]) (:emitted out))))
    (is (= 2 (count (reading/combine [] (:emitted out)))) "the same one twice is one")))

(deftest bad-readings-are-declared-errors-with-file-and-line
  (doseq [[body why] [["(reading \"x/*.md\" (fn [d] nil))" "not a (files …)"]
                      ["(reading (files \"x\") \"nope\")" "not a function"]
                      ["(reading (files \"x\") nil)" "nil"]
                      ["(reading (files \"x\"))" "one argument"]
                      ["(reading {:files \"x\"} (fn [d] nil))" "a map that is not a node"]]]
    (let [f (failure "kimun/c.clj" (str "(ns c)\n\n" body))]
      (is (= :declare/bad-reading (:type f)) why)
      (is (= {:file "kimun/c.clj" :line 3} (select-keys f [:file :line])) why)))
  (is (= :declare/unresolved (:type (failure "kimun/c.clj" "(ns c)\n(reading (files \"x\") (fn [d] (slurp \"/etc/passwd\")))")))
      "the reader sees nothing of the host"))

(deftest evaluating-opens-nothing-and-running-opens-only-what-matches
  (let [opened (atom [])
        rs (project "kimun/c.clj" (str "(ns c) (relation isa \"d\")\n(reading (files \"a/*.md\") (fn [doc] (throw (ex-info \"never at evaluation\" {}))))"))
        after-eval @opened
        out (reading/run (declare/readers rs) ["a/1.md" "b/2.md" "a/3.txt"] {:read-now (fn [p] (swap! opened conj p) "x")})]
    (is (= [] after-eval) "evaluating the declarations opened no file (`read-now` does not even exist there)")
    (is (= ["a/1.md"] @opened) "running opens only the file the pattern reaches")
    (is (= 1 (count (:errors out))))))

(deftest generated-files-and-the-declarations-are-never-read
  (let [rs (project "kimun/c.clj" "(ns c) (relation isa \"d\") (reading (files \"**/*.md\") (fn [doc] (isa (symbol (:path doc)) Doc)))")
        all {"docs/a.md" "x" "docs/gen.md" "x" "kimun/notes.md" "x" "b.md" "x"}
        out (run-readers rs all {:exclude #{"docs/gen.md"}})]
    (is (= ["b.md" "docs/a.md"] (map #(get-in % [:a :term]) (:emitted out))))))

(deftest what-the-reader-gets-is-plain-data
  (let [rs (project "kimun/c.clj" "(ns c) (relation seen \"d\")
(reading (files \"*\") (fn [doc] (seen (:path doc) (pr-str (assoc doc :text (count (:text doc)))))))")
        out (run-readers rs {"a.md" "---\nid: a\nwhen: 2026-11-15\n---\n# T\n\nbody\n" "n.txt" "# no\n"})
        docs (into {} (map (fn [p] [(:a p) (read-string (:b p))])) (:emitted out))]
    (is (= {:path "a.md" :front {"id" "a" "when" "2026-11-15"} :text (count "# T\n\nbody")
            :sections [{:title "T" :level 1 :path ["T"] :text "body"}]}
           (get docs "a.md")))
    (is (= {:path "n.txt" :front nil :sections [] :text (count "# no\n")} (get docs "n.txt")))))

(deftest doc-of-front
  (is (= {"id" "x" "n" 3 "l" ["a" "b"] "m" {"k" 1} "b" true "z" nil "d" "2026-11-15" "ts" "2026-11-15T10:20:30Z" "1" "k"}
         (:front (reading/doc-of "a.md" "---\nid: x\nn: 3\nl: [a, b]\nm: {k: 1}\nb: true\nz: ~\nd: 2026-11-15\nts: 2026-11-15T10:20:30Z\n1: k\n---\nbody"))))
  (testing "no frontmatter, a thematic break, invalid YAML and a YAML that is not a map are nil, not an error"
    (doseq [text ["body only" "---\n\nnot front\n---\nx" "---\na: [\n---\nbody" "---\n- a\n- b\n---\nbody" "---\njust a string\n---\nbody"]]
      (is (nil? (:front (reading/doc-of "a.md" text))) (pr-str text))))
  (is (every? #(or (string? %) (number? %) (boolean? %) (nil? %) (vector? %) (map? %))
              (tree-seq coll? seq (:front (reading/doc-of "a.md" "---\nd: 2026-11-15\nt: 2026-11-15T10:00:00Z\n---\n"))))
      "no Java objects"))

(deftest doc-of-sections
  (let [d (reading/doc-of "a.md" "intro\n\n# A\n\na1\n\n## B\n\nb1\n\n```\n# not a heading\n```\n\n### C\n\nc1\n\n## D ##\n\nd1\n\n# E\n\ne1\n")]
    (is (= [["A" 1 ["A"] "a1"]
            ["B" 2 ["A" "B"] "b1\n\n```\n# not a heading\n```"]
            ["C" 3 ["A" "B" "C"] "c1"]
            ["D" 2 ["A" "D"] "d1"]
            ["E" 1 ["E"] "e1"]]
           (map (juxt :title :level :path :text) (:sections d))))
    (is (str/starts-with? (:text d) "intro")))
  (testing "CRLF reads as LF; a non Markdown file is untouched"
    (is (= "t" (:text (first (:sections (reading/doc-of "a.md" "# H\r\nt\r\n"))))))
    (is (= "# H\r\nt\r\n" (:text (reading/doc-of "a.txt" "# H\r\nt\r\n"))))))

(deftest about-path-says-what-a-file-says
  (let [rs (project "kimun/c.clj" "(ns c) (relation isa \"d\") (reading (files \"d/*.md\") (fn [doc] (isa (symbol \"x\") Y)))")
        out (run-readers rs {"d/a.md" "a" "d/b.md" "b"})
        ps (reading/combine (:propositions (first rs)) (:emitted out))
        rows (:results (props/about-path ps "d/b.md"))]
    (is (= [:source] (map :via rows)))
    (is (= "x" (get-in (first rows) [:proposition :a :term])))
    (is (= [] (:results (props/about-path ps "d/zzz.md"))))))
