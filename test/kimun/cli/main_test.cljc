(ns kimun.cli.main-test
  "The CLI entry point: version, usage, formats and the not-yet-available
   evaluator surface (docs/v2/06 §B)."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [kimun.cli.main :as main]
            [kimun.cli.out :as out]))

(def env {"USER" "tester"})
(def cwd (System/getProperty "user.dir"))

(defn- run [& argv] (main/run argv env cwd))

(deftest version-matches-resources-VERSION
  (let [{:keys [envelope]} (run "--version")
        expected (str/trim (slurp (io/resource "VERSION")))]
    (is (true? (:ok envelope)))
    (is (= "version" (:command envelope)))
    (is (= expected (get-in envelope [:data :version])))
    (is (= "sldb-v2" (get-in envelope [:data :kernel])))
    (is (string? (get-in envelope [:data :bb])))
    (is (string? (get-in envelope [:data :git])))
    (is (= envelope (:envelope (run "version"))) "the bare token is an alias of --version")))

(deftest unknown-first-token-is-the-evaluator-stub
  (let [{:keys [envelope]} (run "find" "foo")]
    (is (false? (:ok envelope)))
    (is (= 6 (:exit envelope)))
    (is (= 6 (out/exit-code envelope)))
    (is (= "eval/not-available" (get-in envelope [:error :type])))
    (is (str/includes? (get-in envelope [:error :message]) "kimun find foo"))
    (is (= ["find" "foo"] (get-in envelope [:error :data :tokens])))))

(deftest edn-format-round-trips-the-envelope
  (let [{:keys [envelope fmt]} (run "--version" "--format" "edn")]
    (is (= "edn" fmt))
    (is (= envelope (edn/read-string (out/emit envelope fmt))))))

(deftest text-format-is-human-readable
  (let [{:keys [envelope fmt]} (run "--version" "--format" "text")
        s (out/emit envelope fmt)]
    (is (= "text" fmt))
    (is (not (str/blank? s)))
    (is (str/includes? s "version"))
    (testing "a failure renders its type and message"
      (let [{:keys [envelope fmt]} (run "find" "x" "--format" "text")
            s (out/emit envelope fmt)]
        (is (str/starts-with? s "✗ eval/not-available:"))))))

(deftest unknown-option-is-a-usage-error
  (let [{:keys [envelope fmt]} (run "stores" "check" "--bogus")]
    (is (false? (:ok envelope)))
    (is (= 6 (:exit envelope)))
    (is (contains? #{"org.babashka/cli" "cli/usage"} (get-in envelope [:error :type])))
    (is (str/includes? (get-in envelope [:error :message]) "--bogus"))
    (is (= "json" fmt))
    (testing "the requested format survives a parse error"
      (is (= "text" (:fmt (run "--bogus" "--format" "text")))))
    (testing "an invalid --format value is a usage error"
      (is (= 6 (:exit (:envelope (run "--version" "--format" "xml"))))))
    (testing "a value-taking option without its value is a usage error"
      (let [e (:envelope (run "stores" "check" "--store"))]
        (is (= "cli/usage" (get-in e [:error :type])))
        (is (= 6 (:exit e)))))))

(deftest no-args-and-help-print-usage-as-text
  (doseq [argv [[] ["--help"] ["help"]]]
    (let [{:keys [envelope fmt]} (main/run argv env cwd)]
      (is (true? (:ok envelope)))
      (is (= "text" fmt))
      (is (str/includes? (get-in envelope [:data :usage]) "stores init"))
      (is (str/includes? (out/emit envelope fmt) "usage: kimun")))))

(deftest json-format-renders-namespaced-types-as-strings
  (let [{:keys [envelope]} (run "find" "x")
        s (out/emit envelope "json")]
    (is (str/includes? s "\"eval/not-available\""))
    (is (not (str/includes? s "Exception")))))
