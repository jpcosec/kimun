(ns kimun.adapters.python-unittest-test
  "The Python half of the adapters: `python3 -m unittest discover python/tests`
   as a process from the repository root (located from the classpath's
   resources/ directory, like `python-dir` in kimun.cli.commands.models). The
   unittest summary line is printed so skipped tests (no click, no tree_sitter)
   stay visible in the bb output."
  (:require [clojure.test :refer [deftest is]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [babashka.process :as p]))

(defn- repo-root
  "The worktree root: the parent of the classpath's `resources/` directory, else the cwd."
  []
  (or (when-let [r (io/resource "VERSION")]
        (when (= "file" (.getProtocol r))
          (str (fs/parent (fs/parent (fs/path (.getPath r)))))))
      (System/getProperty "user.dir")))

(defn- summary-line [err]
  (->> (str/split-lines err)
       (remove str/blank?)
       (filter #(or (str/starts-with? % "OK") (str/starts-with? % "FAILED") (str/starts-with? % "Ran ")))
       (str/join " | ")))

(deftest python-adapter-unittests-pass
  (let [root (repo-root)
        env (assoc (into {} (System/getenv)) "PYTHONDONTWRITEBYTECODE" "1")
        r (p/shell {:out :string :err :string :continue true :dir root :env env}
                   "python3" "-m" "unittest" "discover" "python/tests")]
    (println "python unittest:" (summary-line (:err r)))
    (when-not (zero? (:exit r)) (println (:err r)))
    (is (zero? (:exit r)))))
