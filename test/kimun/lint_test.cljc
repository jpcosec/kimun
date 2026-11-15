(ns kimun.lint-test
  "scripts/check_rings.clj as a process: a global mutable def in ring 0 is a
   violation (docs/v2/03 §1), the base may not require the graph package
   (`kimun.graph.*`, docs/v2/14 §19) beyond the crossings written down as
   pending, and the real src/ tree is clean."
  (:require [kimun.test-util :as ktu] [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [babashka.process :as p]))

(def repo (System/getProperty "user.dir"))
(def script (str (fs/path repo "scripts" "check_rings.clj")))

(def bad-ns
  "(ns kimun.kernel.bad
  \"A kernel namespace with a global atom, which docs/v2/03 §1 forbids.\")

(def bad-cache \"Memo that should be an argument.\" (atom {}))

(defn fine
  \"A documented function.\"
  [x] x)
")

(defn- ns-source
  "Source of a documented namespace `ns-name` requiring `lib`."
  [ns-name lib]
  (str "(ns " ns-name "\n  \"A documented namespace.\"\n  (:require [" lib " :as x]))\n"))

(defn- lint-of
  "Lint result over a temporary root holding `files` (`{relative-path source}`)."
  [files]
  (let [root (str (ktu/temp-dir "kimun-lint-"))]
    (doseq [[rel source] files
            :let [file (fs/path root rel)]]
      (fs/create-dirs (fs/parent file))
      (spit (str file) source))
    (p/shell {:out :string :err :string :continue true :dir repo} "bb" script root)))

(defn- lint [& roots]
  (apply p/shell {:out :string :err :string :continue true :dir repo} "bb" script roots))

(deftest a-global-atom-in-the-kernel-fails-lint
  (let [root (str (ktu/temp-dir "kimun-lint-"))
        file (fs/path root "src" "kimun" "kernel" "bad.cljc")]
    (fs/create-dirs (fs/parent file))
    (spit (str file) bad-ns)
    (let [{:keys [exit out]} (lint root)]
      (is (= 1 exit))
      (is (str/includes? out "global mutable"))
      (is (str/includes? out "kimun.kernel.bad"))
      (is (str/includes? out "bad-cache")))))

(deftest the-base-may-not-require-the-graph-package
  (doseq [base ["kimun.surface.bad" "kimun.surface.index.bad" "kimun.surface.markdown.bad"
                "kimun.host.bad" "kimun.kernel.bad"]]
    (let [{:keys [exit out]} (lint-of {(str (str/replace base "." "/") ".cljc") (ns-source base "kimun.graph.model")})]
      (is (= 1 exit) base)
      (is (str/includes? out (str base ": ")) out)
      (is (str/includes? out "kimun.graph.model") out))))

(deftest the-graph-package-sits-over-the-base-and-under-the-cli
  (is (= 0 (:exit (lint-of {"kimun/graph/fine.cljc" (ns-source "kimun.graph.fine" "kimun.surface.edges")}))))
  (is (= 0 (:exit (lint-of {"kimun/graph/fine.cljc" (ns-source "kimun.graph.fine" "kimun.graph.model")}))))
  (is (= 0 (:exit (lint-of {"kimun/cli/fine.cljc" (ns-source "kimun.cli.fine" "kimun.graph.model")}))))
  (let [{:keys [exit out]} (lint-of {"kimun/graph/bad.cljc" (ns-source "kimun.graph.bad" "kimun.cli.out")})]
    (is (= 1 exit))
    (is (str/includes? out "kimun.graph.bad: forbidden require kimun.cli.out") out)))

(deftest a-pending-graph-crossing-is-exact-and-may-only-shrink
  ;; the one written down passes
  (is (= 0 (:exit (lint-of {"kimun/surface/semantic.cljc" (ns-source "kimun.surface.semantic" "kimun.graph.io")}))))
  ;; another graph namespace from the same place does not
  (let [{:keys [exit out]} (lint-of {"kimun/surface/semantic.cljc"
                                     (str "(ns kimun.surface.semantic\n  \"A documented namespace.\"\n"
                                          "  (:require [kimun.graph.io :as a] [kimun.graph.model :as b]))\n")})]
    (is (= 1 exit))
    (is (str/includes? out "may not require the graph package: kimun.graph.model") out))
  ;; and once the crossing is gone the entry must go too
  (let [{:keys [exit out]} (lint-of {"kimun/surface/semantic.cljc" (ns-source "kimun.surface.semantic" "clojure.string")})]
    (is (= 1 exit))
    (is (str/includes? out "no longer needed") out)))

(deftest the-real-src-tree-is-clean
  (let [{:keys [exit out]} (lint (str (fs/path repo "src")))]
    (is (= 0 exit) out)
    (is (str/includes? out "rings and docstrings ok"))))
