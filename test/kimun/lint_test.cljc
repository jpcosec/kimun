(ns kimun.lint-test
  "scripts/check_rings.clj as a process: a global mutable def in ring 0 is a
   violation (docs/v2/03 §1), and the real src/ tree is clean."
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [babashka.process :as p]))

(def repo (System/getProperty "user.dir"))
(def script (str (fs/path repo "scripts" "check_rings.clj")))

(def bad-ns
  "(ns sldb.kernel.bad
  \"A kernel namespace with a global atom, which docs/v2/03 §1 forbids.\")

(def bad-cache \"Memo that should be an argument.\" (atom {}))

(defn fine
  \"A documented function.\"
  [x] x)
")

(defn- lint [& roots]
  (apply p/shell {:out :string :err :string :continue true :dir repo} "bb" script roots))

(deftest a-global-atom-in-the-kernel-fails-lint
  (let [root (str (fs/create-temp-dir {:prefix "kimun-lint-"}))
        file (fs/path root "src" "sldb" "kernel" "bad.cljc")]
    (fs/create-dirs (fs/parent file))
    (spit (str file) bad-ns)
    (let [{:keys [exit out]} (lint root)]
      (is (= 1 exit))
      (is (str/includes? out "global mutable"))
      (is (str/includes? out "sldb.kernel.bad"))
      (is (str/includes? out "bad-cache")))))

(deftest the-real-src-tree-is-clean
  (let [{:keys [exit out]} (lint (str (fs/path repo "src")))]
    (is (= 0 exit) out)
    (is (str/includes? out "rings and docstrings ok"))))
