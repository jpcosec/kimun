(ns kimun.cli.release-test
  "Packaging (docs/v2/08 §4): `bb release --local-bb` produces the bundle layout,
   the extracted tarball runs without `bb` on PATH, its `--version` equals the
   in-process one, and `stores init` works from it. Builds target/kimun.jar
   once if absent; spawns one bb at a time."
  (:require [clojure.test :refer [deftest is testing]]
            [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [kimun.cli.main :as main]))

(def root (str (fs/absolutize ".")))
(def jar (str (fs/path root "target" "kimun.jar")))
(def clean-env {"PATH" "/usr/bin:/bin"})

(defn- ensure-jar! []
  (when-not (fs/exists? jar)
    (p/shell {:dir root :out :string :err :string} "bb" "jar")))

(defn- release!
  ([out] (release! out jar nil))
  ([out the-jar extra-env]
   (let [r (p/shell {:dir root :out :string :err :string :continue true :extra-env extra-env}
                   "bb" "scripts/release.clj" "--local-bb" "--platforms" "linux-amd64"
                   "--out" out "--jar" the-jar)]
    (is (zero? (:exit r)) (str "release failed: " (:err r)))
    (edn/read-string (:out r)))))

(defn- build-jar!
  "Builds a fresh uberjar at `dest` (outside the worktree)."
  [dest]
  (let [r (p/shell {:dir root :out :string :err :string :continue true}
                   "bb" "--classpath" "src:resources" "uberjar" dest "-m" "kimun.cli.main")]
    (is (zero? (:exit r)) (:err r))
    dest))

(defn- run-bundle
  "Runs `launcher` with `args` in `dir` under a PATH without bb; returns
   {:exit :out :ms}."
  [launcher dir & args]
  (let [t0 (System/nanoTime)
        r (apply p/shell {:dir dir :env clean-env :out :string :err :string :continue true} launcher args)]
    {:exit (:exit r) :out (:out r) :err (:err r) :ms (/ (- (System/nanoTime) t0) 1e6)}))

(deftest local-bb-release-bundle-runs-without-bb-on-path
  (ensure-jar!)
  (fs/with-temp-dir [tmp {:prefix "kimun-release"}]
    (let [out (str (fs/path tmp "dist"))
          summary (release! out)
          [bundle] (:bundles summary)
          dir (:dir bundle)
          archive (:archive bundle)]
      (testing "summary and layout"
        (is (= "2.0.0-alpha.1" (:version summary)))
        (is (= (str/trim (slurp (io/resource "VERSION"))) (:version summary)))
        (is (= "linux-amd64" (:platform bundle)))
        (doseq [f ["bin/kimun" "bin/kimun.cmd" "lib/bb" "lib/kimun.jar" "VERSION" "LICENSE"]]
          (is (fs/exists? (fs/path dir f)) f))
        (is (fs/executable? (fs/path dir "bin" "kimun")))
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
              launcher (str (fs/path ext (fs/file-name dir) "bin" "kimun"))
              {:keys [exit out ms]} (run-bundle launcher ext "--version")
              envelope (json/parse-string out true)
              in-process (get-in (main/run ["--version"] {} root) [:envelope :data])]
          (is (zero? exit))
          (is (true? (:ok envelope)))
          (is (= in-process (:data envelope)) "bundle --version == in-process version-data")
          (println (format "release_test: bundle --version in %.0f ms" ms))
          (is (< ms 1500) "startup budget (docs/v2/08 §4)")
          (testing "a symlink to the launcher (~/.local/bin style) still finds the bundle"
            (let [bindir (str (fs/path tmp "home-bin"))
                  _ (fs/create-dirs bindir)
                  link (str (fs/path bindir "kimun"))
                  _ (fs/create-sym-link link launcher)
                  {:keys [exit out]} (run-bundle link ext "--version")]
              (is (zero? exit))
              (is (= in-process (:data (json/parse-string out true))))))
          (testing "`--` keeps --help for kimun, not bb"
            (let [{:keys [exit out]} (run-bundle launcher ext "--help")]
              (is (zero? exit))
              (is (str/includes? out "usage: kimun"))))
          (testing "stores init / check from the bundle"
            (let [proj (str (fs/path tmp "proj"))
                  _ (fs/create-dirs proj)
                  init (run-bundle launcher proj "stores" "init" "--name" "demo")
                  check (run-bundle launcher proj "stores" "check")]
              (is (zero? (:exit init)) (:err init))
              (is (fs/exists? (fs/path proj ".kimun" "store.edn")))
              (is (= "demo" (get-in (json/parse-string (:out init) true) [:data :name])))
              (is (zero? (:exit check)))
              (is (= 0 (get-in (json/parse-string (:out check) true) [:data :revisions]))))))))))

(deftest release-rejects-unknown-platform-and-version-drift
  (fs/with-temp-dir [tmp {:prefix "kimun-release-neg"}]
    (let [r (p/shell {:dir root :out :string :err :string :continue true}
                     "bb" "scripts/release.clj" "--local-bb" "--platforms" "amiga-68k"
                     "--out" (str tmp) "--jar" jar)]
      (is (= 2 (:exit r)))
      (is (str/includes? (:err r) "unknown platform")))))

(deftest release-archives-are-byte-identical-across-builds
  (fs/with-temp-dir [tmp {:prefix "kimun-release-det"}]
    (let [jar-a (build-jar! (str (fs/path tmp "a.jar")))
          _ (Thread/sleep 2500)             ; zip entries carry 2 s timestamps
          jar-b (build-jar! (str (fs/path tmp "b.jar")))
          a (first (:bundles (release! (str (fs/path tmp "out-a")) jar-a nil)))
          _ (Thread/sleep 1100)
          _ (doseq [f (fs/glob (:dir a) "**")] (fs/set-last-modified-time f (java.nio.file.attribute.FileTime/fromMillis 1e12)))
          b (first (:bundles (release! (str (fs/path tmp "out-b")) jar-b nil)))]
      (is (= (:sha256 a) (:sha256 b)) "same tree, two builds (and two uberjars) -> same tar.gz")
      (is (= (slurp (str (fs/path tmp "out-a" "SHA256SUMS")))
             (slurp (str (fs/path tmp "out-b" "SHA256SUMS")))))
      (is (= (slurp (str (fs/path tmp "out-a" "SHA256SUMS")))
             (str (:sha256 a) "  " (fs/file-name (:archive a)) "\n"))))))

(deftest release-archive-entries-are-sorted-owned-by-root-and-stamped-with-the-epoch
  (fs/with-temp-dir [tmp {:prefix "kimun-release-meta"}]
    (ensure-jar!)
    (let [listing (fn [env]
                    (let [b (first (:bundles (release! (str (fs/path tmp (str "o" (hash env)))) jar env)))]
                      {:sha (:sha256 b)
                       :lines (str/split-lines
                               (:out (p/shell {:out :string :extra-env {"TZ" "UTC"}} "tar" "--numeric-owner" "-tvzf" (:archive b))))}))
          dflt (listing nil)
          fixed (listing {"SOURCE_DATE_EPOCH" "1700000000"})
          names (fn [{:keys [lines]}] (map #(last (str/split % #" ")) lines))]
      (is (= (sort (names dflt)) (names dflt)) "entries in lexicographic order")
      (is (every? #(str/includes? % " 0/0 ") (:lines dflt)) "uid/gid 0")
      (is (= #{"1980-01-01"} (set (map #(nth (str/split % #"\s+") 3) (:lines dflt)))) "default epoch 1980-01-01")
      (is (= #{"2023-11-14"} (set (map #(nth (str/split % #"\s+") 3) (:lines fixed)))) "SOURCE_DATE_EPOCH honored")
      (is (not= (:sha dflt) (:sha fixed))))))
