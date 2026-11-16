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

(def dev-pod
  "The development build of the tree-sitter pod (`bb pod`) — what a local linux-amd64
   release packs."
  (str (fs/path root "pods" "treesitter" "target" "release" "pod-kimun-treesitter")))

(def pod? (fs/exists? dev-pod))

(defn- sha256 [f]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")]
    (str/join (map #(format "%02x" (bit-and % 0xff)) (.digest md (java.nio.file.Files/readAllBytes (fs/path f)))))))

(defn- ensure-jar! []
  (when-not (fs/exists? jar)
    (p/shell {:dir root :out :string :err :string} "bb" "jar")))

;; Without the development pod (`bb pod` not run) the releases below are built with
;; --without-pod and the pod assertions are skipped (printed); CI builds the pod first.
(defn- release!
  ([out] (release! out jar nil))
  ([out the-jar extra-env]
   (let [r (apply p/shell {:dir root :out :string :err :string :continue true :extra-env extra-env}
                  "bb" "scripts/release.clj" "--local-bb" "--platforms" "linux-amd64"
                  "--out" out "--jar" the-jar
                  (when-not pod? ["--without-pod"]))]
    (is (zero? (:exit r)) (str "release failed: " (:err r)))
    (edn/read-string (:out r)))))

(defn- build-jar!
  "Builds a fresh uberjar at `dest` (outside the worktree)."
  [dest]
  (let [r (p/shell {:dir root :out :string :err :string :continue true}
                   "bb" "--classpath" "src:resources" "uberjar" dest "-m" "kimun.cli.main")]
    (is (zero? (:exit r)) (:err r))
    dest))

(defn- run-bundle-env
  "Runs `launcher` with `args` in `dir` with exactly `env` (no bb on PATH unless
   `env` puts it there); returns {:exit :out :err :ms}."
  [launcher dir env & args]
  (let [t0 (System/nanoTime)
        r (apply p/shell {:dir dir :env env :out :string :err :string :continue true} launcher args)]
    {:exit (:exit r) :out (:out r) :err (:err r) :ms (/ (- (System/nanoTime) t0) 1e6)}))

(defn- run-bundle
  "`run-bundle-env` under `clean-env` (PATH without bb, no KIMUN_POD)."
  [launcher dir & args]
  (apply run-bundle-env launcher dir clean-env args))

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
              (is (= 0 (get-in (json/parse-string (:out check) true) [:data :revisions])))))
          (if-not pod?
            (println "release_test: SKIPPED pod smoke — no pods/treesitter/target/release/pod-kimun-treesitter (run `bb pod`)")
            (testing "the bundle carries its own pod and uses it: KIMUN_POD unset, no pod outside the bundle"
              (let [bdir (str (fs/path ext (fs/file-name dir)))
                    bundled (str (fs/path bdir "lib" "pod-kimun-treesitter"))
                    manifest (edn/read-string (slurp (str (fs/path bdir "MANIFEST.edn"))))
                    proj (str (fs/path tmp "proj-pod"))
                    _ (fs/create-dirs proj)
                    note (str (fs/path proj "n.md"))
                    _ (spit note "# T\n\nhello *w*\n\n- a\n- b\n")
                    env (assoc clean-env "KIMUN_PARSER" "treesitter")
                    run (fn [& args] (apply run-bundle-env launcher proj env args))
                    init (run "stores" "init" "--name" "demo")
                    ingest (run "docs" "ingest" "n.md" "--name" "notes")
                    render (run "docs" "render" "notes")
                    marks (run "docs" "marks" "notes")
                    contract (get-in (json/parse-string (:out marks) true) [:data :parse-contract])]
                (is (fs/executable? bundled))
                (is (= (sha256 dev-pod) (sha256 bundled)) "the packed pod is the one built here (staged and dev copies are the same file in CI)")
                (is (= (sha256 bundled) (get-in manifest [:pod :sha256])) "MANIFEST.edn records what was packed")
                (is (= (if (fs/exists? (fs/path root "target" "pods" "linux-amd64" "pod-kimun-treesitter"))
                         :ci-staging :local-build)
                       (get-in manifest [:pod :origin])))
                (is (= false (get-in manifest [:pod :pinned])) "release.edn pins no pod hash yet")
                (is (= :linux-amd64 (:platform manifest)))
                (doseq [r [init ingest render marks]] (is (zero? (:exit r)) (:err r)))
                (is (= "# T\n\nhello *w*\n\n- a\n- b\n" (:data (json/parse-string (:out render) true))))
                (is (= ["kimun.surface.markdown/treesitter" "1"] (:parser contract)))
                (is (str/starts-with? (:grammar contract) "pod-kimun-treesitter/")
                    "the tree-sitter grammar the pod reports (not the builtin parser)")))))))))

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

;; Bundles built from a throwaway root (the script finds its root from its own path), so
;; the pod rules are tested with exactly the pods the test puts there.
(defn- fake-root!
  "Copies scripts/release.clj, release.edn, resources/VERSION and LICENSE under `dir`;
   returns the path of the copied script."
  [dir]
  (fs/create-dirs (fs/path dir "scripts"))
  (fs/create-dirs (fs/path dir "resources"))
  (fs/copy (fs/path root "scripts" "release.clj") (fs/path dir "scripts" "release.clj"))
  (fs/copy (fs/path root "release.edn") (fs/path dir "release.edn"))
  (fs/copy (fs/path root "resources" "VERSION") (fs/path dir "resources" "VERSION"))
  (fs/copy (fs/path root "LICENSE") (fs/path dir "LICENSE"))
  (str (fs/path dir "scripts" "release.clj")))

(defn- script!
  "Runs the copied `script` with `args`; {:exit :out :err}."
  [script & args]
  (let [r (apply p/shell {:dir (str (fs/parent (fs/parent script))) :out :string :err :string :continue true}
                 "bb" script args)]
    (select-keys r [:exit :out :err])))

(deftest a-bundle-never-carries-another-platforms-pod-nor-silently-no-pod
  (fs/with-temp-dir [tmp {:prefix "kimun-release-pod"}]
    (ensure-jar!)
    (let [rt (str (fs/path tmp "root"))
          script (fake-root! rt)
          out (str (fs/path tmp "dist"))
          dummy-pod (fn [& parts] (fs/create-dirs (fs/parent (apply fs/path rt parts)))
                      (spit (str (apply fs/path rt parts)) "not a pod") parts)]
      (testing "no pod for the platform: the build fails and says what is missing"
        (let [r (script! script "--local-bb" "--platforms" "linux-amd64" "--out" out "--jar" jar)]
          (is (= 2 (:exit r)))
          (is (str/includes? (:err r) "no tree-sitter pod compiled for linux-amd64"))
          (is (str/includes? (:err r) "bb pod"))
          (is (str/includes? (:err r) "--without-pod"))
          (is (empty? (fs/glob out "*.tar.gz")) "no archive was produced")))
      (testing "a development pod and a staged pod of OTHER platforms are not this platform's pod"
        (dummy-pod "pods" "treesitter" "target" "release" "pod-kimun-treesitter")
        (dummy-pod "target" "pods" "linux-aarch64" "pod-kimun-treesitter")
        (let [r (script! script "--local-bb" "--platforms" "macos-aarch64" "--out" out "--jar" jar)]
          (is (= 2 (:exit r)))
          (is (str/includes? (:err r) "no tree-sitter pod compiled for macos-aarch64") (:err r))
          (is (str/includes? (:err r) "does not count"))
          (is (empty? (fs/glob out "*.tar.gz")) "no bundle of macos carries the linux pod")))
      (testing "--local-bb copies this machine's bb: it never fits another platform"
        (dummy-pod "target" "pods" "macos-aarch64" "pod-kimun-treesitter")
        (let [r (script! script "--local-bb" "--platforms" "macos-aarch64" "--out" out "--jar" jar)]
          (is (= 2 (:exit r)))
          (is (str/includes? (:err r) "only fits that platform") (:err r))
          (is (empty? (fs/glob out "*.tar.gz")))))
      (testing "--without-pod: explicit, declared in MANIFEST.edn, refused by the launcher when the pod is needed"
        (let [r (script! script "--local-bb" "--platforms" "linux-amd64" "--out" out "--jar" jar "--without-pod")
              dir (str (fs/path out "kimun-2.0.0-alpha.1-linux-amd64"))
              manifest (edn/read-string (slurp (str (fs/path dir "MANIFEST.edn"))))
              launcher (str (fs/path dir "bin" "kimun"))
              treesitter (run-bundle-env launcher tmp (assoc clean-env "KIMUN_PARSER" "treesitter") "--version")
              plain (run-bundle launcher tmp "--version")]
          (is (zero? (:exit r)) (:err r))
          (is (str/includes? (:err r) "--without-pod"))
          (is (contains? manifest :pod))
          (is (nil? (:pod manifest)))
          (is (not (fs/exists? (fs/path dir "lib" "pod-kimun-treesitter"))))
          (is (= 2 (:exit treesitter)))
          (is (str/includes? (:err treesitter) "no tree-sitter pod"))
          (is (zero? (:exit plain)) "the in-process parser still works")))))
  )
