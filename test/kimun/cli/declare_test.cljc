(ns kimun.cli.declare-test
  "`kimun declare read` (docs/v2/06): the envelope, two files, declared
   failures with their exit codes, and that nothing is written to disk."
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.test-util :as ktu]))

(def env {"USER" "tester"})

(defn- run [cwd & argv] (:envelope (main/run argv env (str cwd))))

(defn- listing [dir]
  (into (sorted-map) (map (fn [p] [(str (fs/relativize dir p)) (when (fs/regular-file? p) (slurp (str p)))])) (fs/glob dir "**")))

(deftest read-two-files-without-touching-the-disk
  (let [dir (ktu/temp-dir "kimun-declare-")]
    (spit (str (fs/path dir "a.clj")) "(ns p.a)\n(relation isa \"d\")\n(isa x Y)\n")
    (spit (str (fs/path dir "b.clj")) "(ns p.b)\n(relation isa \"d\")\n(isa x Y)\n(isa z Y)\n")
    (let [before (listing dir)
          e (run dir "declare" "read" "a.clj" "b.clj")]
      (is (true? (:ok e)))
      (is (= "declare read" (:command e)))
      (is (nil? (:store e)))
      (is (= {:files 2 :relations 2 :propositions 3} (get-in e [:data :summary])))
      (is (= [["a.clj" "p.a" 1] ["b.clj" "p.b" 2]]
             (mapv (juxt :file :context (comp count :propositions)) (get-in e [:data :files]))))
      (is (= {:file "a.clj" :line 3} (get-in e [:data :files 0 :propositions 0 :at])))
      (is (= before (listing dir)) "no file created, none changed"))))

(deftest failures-are-declared-envelopes
  (let [dir (ktu/temp-dir "kimun-declare-")]
    (spit (str (fs/path dir "bad.clj")) "(ns p.a)\n(nope x y)\n")
    (spit (str (fs/path dir "ok.clj")) "(ns p.ok)\n")
    (testing "a file that does not evaluate: type, file and line, exit 3; one bad file fails the command"
      (let [e (run dir "declare" "read" "ok.clj" "bad.clj")]
        (is (false? (:ok e)))
        (is (= 3 (:exit e)))
        (is (= "declare/unresolved" (get-in e [:error :type])))
        (is (= "bad.clj" (get-in e [:error :data :file])))
        (is (= 2 (get-in e [:error :data :line])))))
    (testing "a missing file is exit 1"
      (let [e (run dir "declare" "read" "nope.clj")]
        (is (= ["declare/missing" 1] [(get-in e [:error :type]) (:exit e)]))))
    (testing "no FILE, or an unknown subcommand, is usage (6)"
      (is (= 6 (:exit (run dir "declare" "read"))))
      (is (= 6 (:exit (run dir "declare" "write" "x")))))))
