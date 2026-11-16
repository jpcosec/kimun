(ns kimun.cli.declare-fixes-test
  "What showed up using the `declare` commands for real (docs/v2/drafts/diseno-evaluar.md §12):
   options after `--to F FORM`, exactly one form, `--context` against an existing file,
   dependencies outside the FILEs named, and the time limit from the environment."
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.args :as args]
            [kimun.cli.main :as main]
            [kimun.test-util :as ktu]))

(defn- run* [env cwd & argv] (main/run argv env (str cwd)))

(defn- run [cwd & argv] (:envelope (apply run* {"USER" "tester"} cwd argv)))

(defn- listing [dir]
  (into (sorted-map) (map (fn [p] [(str (fs/relativize dir p)) (when (fs/regular-file? p) (slurp (str p)))])) (fs/glob dir "**")))

(defn- put! [dir rel text]
  (fs/create-dirs (fs/parent (fs/path dir rel)))
  (spit (str (fs/path dir rel)) text))

(defn- project []
  (let [dir (ktu/temp-dir "kimun-fix-")]
    (put! dir "kimun/base.clj" "(ns base)\n(relation isa \"d\")\n(isa x Y)\n")
    (put! dir "kimun/decisiones.clj" "(ns decisiones (:require [base :refer [isa]]))\n(relation own \"d\")\n(isa z Y)\n")
    (put! dir "kimun/sub/mid.clj" "(ns mid (:require [decisiones]))\n(relation m \"d\")\n")
    (put! dir "kimun/sub/top.clj" "(ns top (:require [mid] [base :as b]))\n(b/isa t Y)\n")
    (put! dir "outside/lonely.clj" "(ns lonely (:require [base]))\n")
    dir))

(deftest options-after-a-positional-that-follows-an-option
  (testing "the parser"
    (is (= {:args ["declare" "add" "(isa a \"b c\")"] :opts {:to "f.clj" :format "edn" :actor "x"}}
           (-> (args/parse ["declare" "add" "--to" "f.clj" "(isa a \"b c\")" "--format" "edn" "--actor" "x"] {} {:to {:ref "ID"}})
               (update :opts select-keys [:to :format :actor]))))
    (is (= ["declare" "add" "A" "B"] (:args (args/parse ["declare" "add" "--to" "f" "A" "B" "--format" "edn"] {} {:to {:ref "ID"}}))) "two positionals")
    (is (= "json" (:format (:opts (args/parse ["declare" "add" "--to" "f" "A"] {} {:to {:ref "ID"}})))) "the default still applies")
    (is (= ["a" "--not-an-option"] (:args (args/parse ["a" "--" "--not-an-option"]))) "after `--` all is positional"))
  (testing "the command, with the option last and the output in EDN"
    (let [dir (project)
          r (run* {"USER" "t"} dir "declare" "add" "--to" "kimun/decisiones.clj" "(isa w \"b c\")" "--format" "edn")]
      (is (true? (:ok (:envelope r))) (pr-str (:error (:envelope r))))
      (is (= "edn" (:fmt r)))
      (is (= "(isa w \"b c\")\n" (subs (slurp (str (fs/path dir "kimun/decisiones.clj"))) (count "(ns decisiones (:require [base :refer [isa]]))\n(relation own \"d\")\n(isa z Y)\n")))))
    (let [dir (project)
          r (run* {"USER" "t"} dir "declare" "about" "Y" "--format" "edn")
          m (run* {"USER" "t"} dir "declare" "members" "kimun/*.clj" "--format" "edn")]
      (is (= ["edn" "edn"] [(:fmt r) (:fmt m)]))
      (is (true? (:ok (:envelope m)))))))

(deftest add-takes-exactly-one-form
  (let [dir (project)
        before (listing dir)]
    (doseq [form ["(isa a b) (isa c d)" "(isa a b)\n(isa c d)" "; nothing" "(isa a"]]
      (let [e (run dir "declare" "add" "--to" "kimun/decisiones.clj" form)]
        (is (false? (:ok e)) form)
        (is (= (if (= form "(isa a") ["declare/syntax" 3] ["cli/usage" 6]) [(get-in e [:error :type]) (:exit e)]) form)
        (is (= before (listing dir)) "the file is the same")))))

(deftest context-must-match-an-existing-file
  (let [dir (project)
        before (listing dir)
        e (run dir "declare" "add" "--to" "kimun/decisiones.clj" "--context" "otro" "(isa q r)")]
    (is (= ["cli/usage" 6] [(get-in e [:error :type]) (:exit e)]))
    (is (re-find #"otro" (get-in e [:error :message])))
    (is (= before (listing dir))))
  (let [dir (project)
        e (run dir "declare" "add" "--to" "kimun/decisiones.clj" "--context" "decisiones" "(isa q r)")]
    (is (true? (:ok e)) (pr-str (:error e)))
    (is (true? (get-in e [:data :added])))))

(deftest required-contexts-of-the-project-load-as-dependencies
  (let [dir (project)
        before (listing dir)]
    (testing "one FILE under kimun/ whose requires live in other project files"
      (let [e (run dir "declare" "read" "kimun/sub/top.clj")]
        (is (true? (:ok e)) (pr-str (:error e)))
        (is (= ["kimun/sub/top.clj"] (mapv :file (get-in e [:data :files]))) "only the FILE named is listed")
        (is (= ["kimun/base.clj" "kimun/decisiones.clj" "kimun/sub/mid.clj"] (get-in e [:data :required])) "transitive, sorted")
        (is (= ["base/isa"] (mapv :r (get-in e [:data :files 0 :propositions]))))))
    (testing "the same for anchors, terms and about"
      (doseq [argv [["declare" "anchors" "kimun/sub/top.clj"] ["declare" "terms" "kimun/sub/top.clj"] ["declare" "about" "Y" "kimun/sub/top.clj"]]]
        (is (= ["kimun/base.clj" "kimun/decisiones.clj" "kimun/sub/mid.clj"] (get-in (apply run dir argv) [:data :required])) (pr-str argv))))
    (testing "everything named: nothing is a dependency, and no :required"
      (let [e (run dir "declare" "read" "kimun/base.clj" "kimun/decisiones.clj")]
        (is (true? (:ok e)))
        (is (not (contains? (:data e) :required)))))
    (testing "outside kimun/ the error stays"
      (let [e (run dir "declare" "read" "outside/lonely.clj")]
        (is (= ["declare/unknown-context" "outside/lonely.clj"] [(get-in e [:error :type]) (get-in e [:error :data :file])]))))
    (testing "a context nobody defines stays an error"
      (put! dir "kimun/sub/bad.clj" "(ns bad (:require [nobody]))\n")
      (is (= "declare/unknown-context" (get-in (run dir "declare" "read" "kimun/sub/bad.clj") [:error :type]))))
    (is (= (dissoc (listing dir) "kimun/sub/bad.clj") before) "read-only")))

(deftest time-limit-comes-from-the-environment
  (let [dir (project)]
    (put! dir "kimun/spin.clj" "(ns spin)\n(loop [] (recur))\n")
    (let [r (run* {"USER" "t" "KIMUN_DECLARE_TIMEOUT_MS" "300"} dir "declare" "read" "kimun/spin.clj")
          e (:envelope r)]
      (is (= ["declare/timeout" 3 "kimun/spin.clj"] [(get-in e [:error :type]) (:exit e) (get-in e [:error :data :file])])))
    (doseq [bad ["abc" "0" "-5" ""]]
      (let [e (:envelope (run* {"USER" "t" "KIMUN_DECLARE_TIMEOUT_MS" bad} dir "declare" "read" "kimun/base.clj"))]
        (is (= ["cli/usage" 6] [(get-in e [:error :type]) (:exit e)]) (pr-str bad))))
    (is (true? (:ok (:envelope (run* {"USER" "t" "KIMUN_DECLARE_TIMEOUT_MS" "20000"} dir "declare" "read" "kimun/base.clj")))))))
