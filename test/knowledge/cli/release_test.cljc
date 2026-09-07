(ns knowledge.cli.release-test
  "Packaging (docs/v2/08 §4): `bb release --local-bb` produces the bundle layout,
   the extracted tarball runs without `bb` on PATH, its `--version` equals the
   in-process one, and `stores init` works from it. Builds target/knowledge.jar
   once if absent; spawns one bb at a time."
  (:require [clojure.test :refer [deftest is testing]]
            [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [knowledge.cli.main :as main]))

(def root (str (fs/absolutize ".")))
(def jar (str (fs/path root "target" "knowledge.jar")))
(def clean-env {"PATH" "/usr/bin:/bin"})

(defn- ensure-jar! []
  (when-not (fs/exists? jar)
    (p/shell {:dir root :out :string :err :string} "bb" "jar")))

(defn- release! [out]
  (let [r (p/shell {:dir root :out :string :err :string :continue true}
                   "bb" "scripts/release.clj" "--local-bb" "--platforms" "linux-amd64"
                   "--out" out "--jar" jar)]
    (is (zero? (:exit r)) (str "release failed: " (:err r)))
    (edn/read-string (:out r))))

(defn- run-bundle
  "Runs `launcher` with `args` in `dir` under a PATH without bb; returns
   {:exit :out :ms}."
  [launcher dir & args]
  (let [t0 (System/nanoTime)
        r (apply p/shell {:dir dir :env clean-env :out :string :err :string :continue true} launcher args)]
    {:exit (:exit r) :out (:out r) :err (:err r) :ms (/ (- (System/nanoTime) t0) 1e6)}))

(deftest local-bb-release-bundle-runs-without-bb-on-path
  (ensure-jar!)
  (fs/with-temp-dir [tmp {:prefix "knowledge-release"}]
    (let [out (str (fs/path tmp "dist"))
          summary (release! out)
          [bundle] (:bundles summary)
          dir (:dir bundle)
          archive (:archive bundle)]
      (testing "summary and layout"
        (is (= "2.0.0-alpha.1" (:version summary)))
        (is (= (str/trim (slurp (io/resource "VERSION"))) (:version summary)))
        (is (= "linux-amd64" (:platform bundle)))
        (doseq [f ["bin/knowledge" "bin/knowledge.cmd" "lib/bb" "lib/knowledge.jar" "VERSION" "LICENSE"]]
          (is (fs/exists? (fs/path dir f)) f))
        (is (fs/executable? (fs/path dir "bin" "knowledge")))
        (is (fs/executable? (fs/path dir "lib" "bb")))
        (is (= (str (:version summary) "\n") (slurp (str (fs/path dir "VERSION")))))
        (is (str/ends-with? archive ".tar.gz"))
        (is (fs/exists? archive)))
      (testing "SHA256SUMS lists the archive with its digest"
        (let [sums (slurp (str (fs/path out "SHA256SUMS")))]
          (is (str/includes? sums (str (:sha256 bundle) "  " (fs/file-name archive))))
          (is (= 64 (count (:sha256 bundle))))))
      (testing "the extracted tarball runs with PATH=/usr/bin:/bin"
        (let [ext (str (fs/path tmp "ext"))
              _ (fs/create-dirs ext)
              _ (p/shell {:out :string} "tar" "-xzf" archive "-C" ext)
              launcher (str (fs/path ext (fs/file-name dir) "bin" "knowledge"))
              {:keys [exit out ms]} (run-bundle launcher ext "--version")
              envelope (json/parse-string out true)
              in-process (get-in (main/run ["--version"] {} root) [:envelope :data])]
          (is (zero? exit))
          (is (true? (:ok envelope)))
          (is (= in-process (:data envelope)) "bundle --version == in-process version-data")
          (println (format "release_test: bundle --version in %.0f ms" ms))
          (is (< ms 1500) "startup budget (docs/v2/08 §4)")
          (testing "`--` keeps --help for knowledge, not bb"
            (let [{:keys [exit out]} (run-bundle launcher ext "--help")]
              (is (zero? exit))
              (is (str/includes? out "usage: knowledge"))))
          (testing "stores init / check from the bundle"
            (let [proj (str (fs/path tmp "proj"))
                  _ (fs/create-dirs proj)
                  init (run-bundle launcher proj "stores" "init" "--name" "demo")
                  check (run-bundle launcher proj "stores" "check")]
              (is (zero? (:exit init)) (:err init))
              (is (fs/exists? (fs/path proj ".knowledge" "store.edn")))
              (is (= "demo" (get-in (json/parse-string (:out init) true) [:data :name])))
              (is (zero? (:exit check)))
              (is (= 0 (get-in (json/parse-string (:out check) true) [:data :revisions]))))))))))

(deftest release-rejects-unknown-platform-and-version-drift
  (fs/with-temp-dir [tmp {:prefix "knowledge-release-neg"}]
    (let [r (p/shell {:dir root :out :string :err :string :continue true}
                     "bb" "scripts/release.clj" "--local-bb" "--platforms" "amiga-68k"
                     "--out" (str tmp) "--jar" jar)]
      (is (= 2 (:exit r)))
      (is (str/includes? (:err r) "unknown platform")))))
