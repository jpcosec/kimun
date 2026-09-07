#!/usr/bin/env bb
;; Builds distributable bundles of `kimun` (docs/v2/08 §3–§5):
;;   bb release [--platforms linux-amd64,macos-aarch64] [--out dist] [--jar target/kimun.jar]
;;              [--local-bb] [--pin]
;; Per platform: <out>/kimun-<version>-<platform>/{bin/kimun, bin/kimun.cmd,
;; lib/bb, lib/kimun.jar, VERSION, LICENSE}, an archive (.tar.gz, .zip on windows) and
;; <out>/SHA256SUMS. `--local-bb` copies the babashka found on PATH instead of downloading
;; the pinned asset (tests, offline builds). `--pin` downloads every asset and rewrites the
;; sha256 map of release.edn. Prints one EDN summary on stdout; exit 2 on a usage or
;; consistency error, 3 on a digest mismatch.
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

(defn- windows? [platform] (str/starts-with? (name platform) "windows"))

(def posix-launcher
  "#!/bin/sh
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
exec \"$DIR/lib/bb\" --jar \"$DIR/lib/kimun.jar\" -- \"$@\"
")

(def cmd-launcher
  "@echo off\r\nrem kimun launcher (Windows): bundled bb.exe + uberjar, relative to this file.\r\nset \"DIR=%~dp0..\"\r\n\"%DIR%\\lib\\bb.exe\" --jar \"%DIR%\\lib\\kimun.jar\" -- %*\r\n")

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

(defn- bundle!
  [{:keys [version] :as cfg} platform {:keys [out jar local-bb pin]}]
  (let [name (str "kimun-" version "-" (clojure.core/name platform))
        dir (fs/path out name)
        bin (fs/path dir "bin") lib (fs/path dir "lib")
        bb-name (if (windows? platform) "bb.exe" "bb")]
    (fs/delete-tree dir)
    (fs/create-dirs bin) (fs/create-dirs lib)
    (let [bb-src (if local-bb
                   (or (fs/which "bb") (fail 2 "--local-bb: no bb on PATH"))
                   (let [{:keys [archive]} (pinned-bb! cfg platform out pin)]
                     (extract-bb! archive (fs/path out ".cache" (str "bb-" (clojure.core/name platform))) platform)))]
      (fs/copy bb-src (fs/path lib bb-name) {:replace-existing true}))
    (fs/copy jar (fs/path lib "kimun.jar") {:replace-existing true})
    (spit (str (fs/path bin "kimun")) posix-launcher)
    (spit (str (fs/path bin "kimun.cmd")) cmd-launcher)
    (spit (str (fs/path dir "VERSION")) (str version "\n"))
    (fs/copy (fs/path root "LICENSE") (fs/path dir "LICENSE") {:replace-existing true})
    (doseq [f [(fs/path bin "kimun") (fs/path lib bb-name)]]
      (try (fs/set-posix-file-permissions f "rwxr-xr-x") (catch Exception _ nil)))
    (let [archive (if (windows? platform)
                    (let [z (fs/path out (str name ".zip"))]
                      (fs/delete-if-exists z)
                      (fs/zip z [(str dir)] {:root (str out)})
                      z)
                    (let [t (fs/path out (str name ".tar.gz"))]
                      (shell "tar" "-czf" (str t) "-C" (str out) name)
                      t))]
      {:platform platform :dir (str dir) :archive (str archive) :sha256 (sha256-file archive)})))

(defn- pin! [cfg platforms out]
  (let [digests (into {} (for [p platforms] [p (:sha256 (pinned-bb! cfg p out true))]))
        cfg' (update cfg :sha256 merge digests)]
    (spit (str (fs/path root "release.edn"))
          (str ";; Release matrix for `bb release` (docs/v2/08 §5). `:version` must equal\n"
               ";; resources/VERSION. `:sha256` digests of the babashka assets are pinned with\n"
               ";; `bb release --pin`; a nil digest aborts a non-local build.\n"
               (with-out-str (clojure.pprint/pprint cfg'))))
    cfg'))

(defn -main [& args]
  (let [opts (cli/parse-opts args {:coerce {:local-bb :boolean :pin :boolean}
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
    (fs/create-dirs out)
    ;; Without --jar the jar is always rebuilt from the working tree: a stale
    ;; target/kimun.jar would silently ship old code. --jar means "use this one".
    (if (:jar opts)
      (when-not (fs/exists? jar) (fail 2 "jar not found:" jar))
      (shell {:dir root :out :string} "bb" "jar"))
    (let [cfg (if (:pin opts) (pin! cfg platforms out) cfg)
          bundles (mapv #(bundle! cfg % {:out out :jar jar :local-bb (:local-bb opts) :pin (:pin opts)}) platforms)]
      (spit (str (fs/path out "SHA256SUMS"))
            (str/join "" (for [{:keys [archive sha256]} bundles] (str sha256 "  " (fs/file-name archive) "\n"))))
      (prn {:version version :out out :local-bb (boolean (:local-bb opts))
            :bundles (mapv #(update % :platform name) bundles)}))))

(apply -main *command-line-args*)
