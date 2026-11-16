#!/usr/bin/env bb
;; Builds distributable bundles of `kimun` (docs/v2/08 §3–§5):
;;   bb release [--platforms linux-amd64,macos-aarch64] [--out dist] [--jar target/kimun.jar]
;;              [--local-bb] [--pin] [--without-pod]
;; Per platform: <out>/kimun-<version>-<platform>/{bin/kimun, bin/kimun.cmd,
;; lib/bb, lib/kimun.jar, lib/pod-kimun-treesitter, MANIFEST.edn, VERSION, LICENSE}, an
;; archive (.tar.gz, .zip on windows) and <out>/SHA256SUMS. `--local-bb` copies the
;; babashka found on PATH instead of downloading the pinned asset (tests, offline builds)
;; and is only valid for the platform of this machine. `--pin` downloads every asset and
;; rewrites the sha256 map of release.edn. Prints one EDN summary on stdout; exit 2 on a
;; usage or consistency error, 3 on a digest mismatch.
;;
;; One rule for the tree-sitter pod: a bundle carries the pod compiled FOR ITS PLATFORM
;; or the build fails (exit 2, naming what is missing). The only sources are
;; `target/pods/<platform>/<bin>` (CI staging) and, when <platform> is the platform of
;; this machine, the development build `pods/treesitter/target/release/<bin>`. There is
;; no other fallback. `--without-pod` builds a bundle with no pod on purpose: its
;; MANIFEST.edn says `:pod nil` and its launcher refuses `KIMUN_PARSER=treesitter`.
;; MANIFEST.edn always records the sha256 of what was packed; release.edn :pods
;; :sha256 is verified only when it is not nil.
(ns release
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [babashka.http-client :as http]
            [babashka.process :refer [shell]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def root (str (fs/parent (fs/parent (fs/absolutize *file*)))))

(defn- fail [code & msg]
  (binding [*out* *err*] (println "release:" (str/join " " msg)))
  (System/exit code))

(defn- sha256-file [f]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")]
    (with-open [in (io/input-stream (fs/file f))]
      (let [buf (byte-array 65536)]
        (loop [] (let [n (.read in buf)] (when (pos? n) (.update md buf 0 n) (recur))))))
    (str/join (map #(format "%02x" (bit-and % 0xff)) (.digest md)))))

(def assets
  "platform → babashka release asset name for version v."
  {:linux-amd64   #(str "babashka-" % "-linux-amd64-static.tar.gz")
   :linux-aarch64 #(str "babashka-" % "-linux-aarch64-static.tar.gz")
   :macos-amd64   #(str "babashka-" % "-macos-amd64.tar.gz")
   :macos-aarch64 #(str "babashka-" % "-macos-aarch64.tar.gz")
   :windows-amd64 #(str "babashka-" % "-windows-amd64.zip")})

;; Reproducible archives (docs/v2/12 §1 "CI/release reproducible"): every timestamp, the
;; entry order, the owner and the gzip header are fixed, so the same inputs give the same
;; bytes. The timestamp is $SOURCE_DATE_EPOCH or 1980-01-01 (the zip minimum).
(def default-epoch 315532800)

(defn- source-epoch
  "Seconds since the epoch stamped on every archived file."
  []
  (if-let [s (not-empty (System/getenv "SOURCE_DATE_EPOCH"))]
    (or (parse-long s) (fail 2 "SOURCE_DATE_EPOCH is not an integer:" s))
    default-epoch))

(defn- normalize-jar!
  "Rewrites jar `src` to `dest` with entries sorted by name and a fixed timestamp
   (an uberjar stamps the build time of every entry, so two builds never match)."
  [src dest epoch]
  (let [t (java.time.LocalDateTime/ofEpochSecond epoch 0 java.time.ZoneOffset/UTC)]
    (with-open [zf (java.util.zip.ZipFile. (fs/file src))
                out (java.util.zip.ZipOutputStream. (io/output-stream (fs/file dest)))]
      (doseq [e (sort-by #(.getName ^java.util.zip.ZipEntry %) (enumeration-seq (.entries zf)))]
        (let [ne (java.util.zip.ZipEntry. (.getName ^java.util.zip.ZipEntry e))]
          (.setTimeLocal ne t)
          (.putNextEntry out ne)
          (when-not (.isDirectory ^java.util.zip.ZipEntry e)
            (with-open [in (.getInputStream zf e)] (io/copy in out)))
          (.closeEntry out))))))

(defn- normalize-tree!
  "Fixes mtime and permissions of every file under `dir` (independent of umask)."
  [dir epoch]
  (let [ft (java.nio.file.attribute.FileTime/fromMillis (* 1000 epoch))]
    (doseq [f (cons (fs/path dir) (fs/glob dir "**" {:hidden true}))]
      (try (fs/set-posix-file-permissions
            f (cond (fs/directory? f) "rwxr-xr-x"
                    (fs/executable? f) "rwxr-xr-x"
                    :else "rw-r--r--"))
           (catch Exception _ nil))
      (fs/set-last-modified-time f ft))))

(defn- windows? [platform] (str/starts-with? (name platform) "windows"))

(def ^:private no-pod-message
  "kimun: this bundle was built with --without-pod, so it has no tree-sitter pod and KIMUN_PARSER=treesitter cannot work (see MANIFEST.edn). Unset KIMUN_PARSER to use the in-process parser.")

(defn- posix-launcher
  "bin/kimun of a bundle: with a pod it points KIMUN_POD at lib/; without one it
   refuses (exit 2) to run when the tree-sitter parser is requested."
  [pod?]
  (str "#!/bin/sh
# kimun launcher: bundled babashka + uberjar, located relative to this file.
# Follows symlinks (e.g. ~/.local/bin/kimun -> <bundle>/bin/kimun).
# `--` keeps every argument for kimun (bb would otherwise take --version/--help/version).
SELF=\"$0\"
while [ -h \"$SELF\" ]; do
  LINKDIR=\"$(cd \"$(dirname \"$SELF\")\" && pwd)\"
  SELF=\"$(readlink \"$SELF\")\"
  case \"$SELF\" in /*) ;; *) SELF=\"$LINKDIR/$SELF\" ;; esac
done
DIR=\"$(cd \"$(dirname \"$SELF\")/..\" && pwd)\"
"
       (if pod?
         "KIMUN_POD=\"$DIR/lib/pod-kimun-treesitter\"; export KIMUN_POD\n"
         (str "unset KIMUN_POD\n"
              "if [ \"${KIMUN_PARSER:-}\" = treesitter ]; then\n"
              "  echo \"" no-pod-message "\" >&2\n"
              "  exit 2\n"
              "fi\n"))
       "exec \"$DIR/lib/bb\" --jar \"$DIR/lib/kimun.jar\" -- \"$@\"\n"))

(defn- cmd-launcher
  "bin/kimun.cmd of a bundle (same contract as `posix-launcher`). Windows is not
   built by CI nor tested: see README Install."
  [pod?]
  (str "@echo off\r\nrem kimun launcher (Windows): bundled bb.exe + uberjar, relative to this file.\r\n"
       "rem Not tested: no windows bundle is published (README Install).\r\n"
       "set \"DIR=%~dp0..\"\r\n"
       (if pod?
         "set \"KIMUN_POD=%DIR%\\lib\\pod-kimun-treesitter.exe\"\r\n"
         (str "set \"KIMUN_POD=\"\r\n"
              "if /i \"%KIMUN_PARSER%\"==\"treesitter\" (\r\n"
              "  echo " no-pod-message " 1>&2\r\n"
              "  exit /b 2\r\n"
              ")\r\n"))
       "\"%DIR%\\lib\\bb.exe\" --jar \"%DIR%\\lib\\kimun.jar\" -- %*\r\n"))

(defn host-platform
  "Keyword of the platform this machine runs (:linux-amd64 …), or nil when it is not
   one of release.edn's."
  []
  (let [os (str/lower-case (System/getProperty "os.name"))
        arch (str/lower-case (System/getProperty "os.arch"))
        os' (cond (str/includes? os "linux") "linux"
                  (str/includes? os "mac") "macos"
                  (str/includes? os "windows") "windows")
        arch' (cond (#{"amd64" "x86_64"} arch) "amd64"
                    (#{"aarch64" "arm64"} arch) "aarch64")]
    (when (and os' arch') (keyword (str os' "-" arch')))))

(defn- pod-bin
  "Bundle name of the tree-sitter pod for `platform` (release.edn :pods)."
  [cfg platform]
  (get-in cfg [:pods platform :bin]
          (if (windows? platform) "pod-kimun-treesitter.exe" "pod-kimun-treesitter")))

(defn- pod-source
  "`{:path :origin}` of the pod binary to bundle for `platform`, or nil. The only
   sources: `target/pods/<platform>/<bin>` (CI staging, origin :ci-staging) and, only
   when `platform` is the platform of this machine, the development build
   `pods/treesitter/target/release/<bin>` (origin :local-build). A pod of another
   platform is never returned."
  [cfg platform]
  (let [bin (pod-bin cfg platform)
        staged (fs/path root "target" "pods" (clojure.core/name platform) bin)
        local (fs/path root "pods" "treesitter" "target" "release" bin)]
    (cond
      (fs/exists? staged) {:path staged :origin :ci-staging}
      (and (= platform (host-platform)) (fs/exists? local)) {:path local :origin :local-build})))

(defn- pod-missing-message
  "What is missing for `platform`'s pod, and the ways to provide it."
  [cfg platform]
  (let [bin (pod-bin cfg platform)]
    (str "no tree-sitter pod compiled for " (clojure.core/name platform)
         ": expected target/pods/" (clojure.core/name platform) "/" bin " (CI staging)"
         (if (= platform (host-platform))
           (str " or pods/treesitter/target/release/" bin " (run `bb pod`)")
           (str "; this machine is " (some-> (host-platform) clojure.core/name) ", so its development build does not count"))
         ". Pass --without-pod to build a bundle with no pod on purpose.")))

(defn- smoke-pod!
  "Runs the bundled pod through `kimun.host.treesitter` (KIMUN_POD pointed at it)
   and checks that it answers `version`; exits 3 when it does not."
  [pod]
  (let [r (shell {:dir root :continue true :out :string :err :string :env {"KIMUN_POD" (str pod)}}
                 "bb" "-e"
                 "(require '[kimun.host.treesitter :as ts]) (prn (ts/version))")
        out (str/trim (:out r))]
    (when-not (and (zero? (:exit r)) (str/includes? out "pod-kimun-treesitter"))
      (fail 3 "pod smoke test failed for" (str pod) "—" (or (not-empty (str/trim (:err r))) (not-empty out) (str "exit " (:exit r)))))
    out))

(defn- download! [url dest]
  (fs/create-dirs (fs/parent dest))
  (let [resp (http/get url {:as :stream :throw false})]
    (when-not (= 200 (:status resp)) (fail 2 "download failed" (str (:status resp)) url))
    (io/copy (:body resp) (fs/file dest))
    dest))

(defn- extract-bb!
  "Extracts the babashka binary from `archive` into `workdir`; returns its path."
  [archive workdir platform]
  (fs/create-dirs workdir)
  (if (windows? platform)
    (fs/unzip archive workdir {:replace-existing true})
    (shell "tar" "-xzf" (str archive) "-C" (str workdir)))
  (let [want (if (windows? platform) "bb.exe" "bb")
        found (first (fs/glob workdir (str "**" want)))]
    (or found (fs/path workdir want))))

(defn- pinned-bb!
  "Downloads (cached under <out>/.cache) and digest-checks the bb asset of `platform`;
   returns {:archive :sha256}."
  [{:keys [bb sha256]} platform out pin?]
  (let [asset ((assets platform) bb)
        url (str "https://github.com/babashka/babashka/releases/download/v" bb "/" asset)
        cache (fs/path out ".cache" asset)]
    (when-not (fs/exists? cache) (download! url cache))
    (let [digest (sha256-file cache)
          expected (get sha256 platform)]
      (cond
        pin? nil
        (nil? expected) (fail 2 "no sha256 pinned for" (name platform) "— run bb release --pin")
        (not= expected digest) (fail 3 "sha256 mismatch for" asset "expected" expected "got" digest))
      {:archive cache :sha256 digest})))

(defn- manifest
  "Text of MANIFEST.edn, one key per line, in a fixed order (the archive stays
   reproducible): what was packed, with the sha256 of the binaries."
  [{:keys [version platform bb-version bb-source bb-sha256 pod]}]
  (str "{:version " (pr-str version) "\n"
       " :platform " platform "\n"
       " :bb {:version " (pr-str bb-version) " :source " bb-source " :sha256 " (pr-str bb-sha256) "}\n"
       " :pod " (if pod
                  (str "{:bin " (pr-str (:bin pod)) " :sha256 " (pr-str (:sha256 pod))
                       " :origin " (:origin pod) " :pinned " (:pinned pod) " :smoked " (:smoked pod) "}")
                  "nil")
       "}\n"))

(defn- bundle!
  [{:keys [version] :as cfg} platform {:keys [out jar local-bb pin without-pod]}]
  (let [epoch (source-epoch)
        name (str "kimun-" version "-" (clojure.core/name platform))
        dir (fs/path out name)
        bin (fs/path dir "bin") lib (fs/path dir "lib")
        bb-name (if (windows? platform) "bb.exe" "bb")
        pod-name (pod-bin cfg platform)
        {pod :path pod-origin :origin} (when-not without-pod (pod-source cfg platform))
        _ (when-not (or pod without-pod) (fail 2 (pod-missing-message cfg platform)))
        pod-sha (when pod (sha256-file pod))
        pod-pin (get-in cfg [:pods platform :sha256])
        smoke? (and pod (= platform (host-platform)))]
    (fs/delete-tree dir)
    (fs/create-dirs bin) (fs/create-dirs lib)
    (let [bb-src (if local-bb
                   (or (fs/which "bb") (fail 2 "--local-bb: no bb on PATH"))
                   (let [{:keys [archive]} (pinned-bb! cfg platform out pin)]
                     (extract-bb! archive (fs/path out ".cache" (str "bb-" (clojure.core/name platform))) platform)))]
      (fs/copy bb-src (fs/path lib bb-name) {:replace-existing true}))
    (normalize-jar! jar (fs/path lib "kimun.jar") epoch)
    (when pod
      (when (and pod-pin (not= pod-pin pod-sha))
        (fail 3 "sha256 mismatch for pod" (str pod-name) "expected" pod-pin "got" pod-sha))
      (fs/copy pod (fs/path lib pod-name) {:replace-existing true}))
    (spit (str (fs/path bin "kimun")) (posix-launcher (boolean pod)))
    (spit (str (fs/path bin "kimun.cmd")) (cmd-launcher (boolean pod)))
    (spit (str (fs/path dir "VERSION")) (str version "\n"))
    (spit (str (fs/path dir "MANIFEST.edn"))
          (manifest {:version version :platform platform :bb-version (if local-bb
                                                  (str/trim (:out (shell {:out :string} (str (fs/which "bb")) "--version")))
                                                  (:bb cfg))
                     :bb-source (if local-bb :local-path :pinned)
                     :bb-sha256 (sha256-file (fs/path lib bb-name))
                     :pod (when pod {:bin pod-name :sha256 pod-sha :origin pod-origin
                                     :pinned (some? pod-pin) :smoked (boolean smoke?)})}))
    (fs/copy (fs/path root "LICENSE") (fs/path dir "LICENSE") {:replace-existing true})
    (doseq [f (cond-> [(fs/path bin "kimun") (fs/path lib bb-name)]
                pod (conj (fs/path lib pod-name)))]
      (try (fs/set-posix-file-permissions f "rwxr-xr-x") (catch Exception _ nil)))
    (when smoke? (smoke-pod! (fs/path lib pod-name)))
    (when (and pod (not smoke?))
      (binding [*out* *err*]
        (println "release: pod for" (clojure.core/name platform) "packed but not smoke-tested (not this machine's platform)")))
    (when-not pod
      (binding [*out* *err*]
        (println "release: --without-pod: bundle for" (clojure.core/name platform) "has no tree-sitter pod (MANIFEST.edn :pod nil)")))
    (normalize-tree! dir epoch)
    (let [archive (if (windows? platform)
                    (let [z (fs/path out (str name ".zip"))]
                      (fs/delete-if-exists z)
                      (fs/zip z [(str dir)] {:root (str out)})
                      z)
                    (let [t (fs/path out (str name ".tar.gz"))]
                      (shell "tar" "--sort=name" "--owner=0" "--group=0" "--numeric-owner"
                             (str "--mtime=@" epoch) "--use-compress-program=gzip -n -9"
                             "-cf" (str t) "-C" (str out) name)
                      t))]
      {:platform platform :dir (str dir) :archive (str archive) :sha256 (sha256-file archive)})))

(def ^:private release-header
  ";; Release matrix for `bb release` (docs/v2/08 §5). `:version` must equal
;; resources/VERSION. `:sha256` digests of the babashka assets are pinned with
;; `bb release --pin`; a nil digest aborts a non-local build. `:pods` names the
;; tree-sitter pod per platform: a bundle carries the pod compiled for ITS platform
;; (target/pods/<platform>/, CI staging; or the development build on that same
;; platform) or the build fails; `--without-pod` omits it explicitly. A pod `:sha256`
;; is nil until a pod built in CI has been published and pinned (`--pin` pins only
;; CI-staged pods): a locally compiled binary is not reproducible across machines, so
;; nothing is claimed about it here; MANIFEST.edn of each bundle records the sha256 of
;; what was actually packed.
")

(defn- pin! [cfg platforms out]
  (let [digests (into {} (for [p platforms] [p (:sha256 (pinned-bb! cfg p out true))]))
        ;; only CI-staged pods are pinned: a local build is not what anyone publishes
        pod-digests (into {} (for [p platforms
                                   :let [src (pod-source cfg p)]
                                   :when (= :ci-staging (:origin src))]
                               [p (sha256-file (:path src))]))
        cfg' (-> cfg
                 (update :sha256 merge digests)
                 (update :pods (fn [pods] (reduce-kv (fn [m p d] (assoc-in m [p :sha256] d)) pods pod-digests))))]
    (spit (str (fs/path root "release.edn"))
          (str release-header
               (with-out-str (clojure.pprint/pprint cfg'))))
    cfg'))

(defn -main [& args]
  (let [opts (cli/parse-opts args {:coerce {:local-bb :boolean :pin :boolean :without-pod :boolean}
                                   :alias {:p :platforms :o :out}})
        cfg (edn/read-string (slurp (str (fs/path root "release.edn"))))
        version (str/trim (slurp (str (fs/path root "resources" "VERSION"))))
        _ (when-not (= version (:version cfg))
            (fail 2 "resources/VERSION" version "≠ release.edn :version" (:version cfg)))
        all (set (:platforms cfg))
        platforms (if-let [p (:platforms opts)]
                    (mapv keyword (str/split (str p) #","))
                    (:platforms cfg))
        _ (doseq [p platforms] (when-not (all p) (fail 2 "unknown platform" (name p) "(known:" (str/join "," (map name (:platforms cfg))) ")")))
        out (str (fs/absolutize (or (:out opts) (fs/path root "dist"))))
        jar (str (fs/absolutize (or (:jar opts) (fs/path root "target" "kimun.jar"))))]
    (when-not (:without-pod opts)
      (when-let [missing (seq (remove #(pod-source cfg %) platforms))]
        (fail 2 (str/join "\n         " (map #(pod-missing-message cfg %) missing)))))
    (when (and (:local-bb opts) (seq (remove #{(host-platform)} platforms)))
      (fail 2 "--local-bb copies this machine's bb (" (some-> (host-platform) name) ") and only fits that platform; got"
            (str/join "," (map name (remove #{(host-platform)} platforms)))))
    (fs/create-dirs out)
    ;; Without --jar the jar is always rebuilt from the working tree: a stale
    ;; target/kimun.jar would silently ship old code. --jar means "use this one".
    (if (:jar opts)
      (when-not (fs/exists? jar) (fail 2 "jar not found:" jar))
      (shell {:dir root :out :string} "bb" "jar"))
    (let [cfg (if (:pin opts) (pin! cfg platforms out) cfg)
          bundles (mapv #(bundle! cfg % {:out out :jar jar 
                                  :local-bb (:local-bb opts) :pin (:pin opts)
                                  :without-pod (:without-pod opts)}) platforms)]
      (spit (str (fs/path out "SHA256SUMS"))
            (str/join "" (for [{:keys [archive sha256]} bundles] (str sha256 "  " (fs/file-name archive) "\n"))))
      (prn {:version version :out out :local-bb (boolean (:local-bb opts))
            :bundles (mapv #(update % :platform name) bundles)}))))

(apply -main *command-line-args*)
