(ns kimun.cli.include-test
  "Tests of the `kimun include` group (docs/v2/13) on a throwaway git repo:
   build then clean check, staged store and undeclared documents rejected, the
   idempotent .gitignore block, the chained pre-commit hook and a rebuild that
   reproduces the store."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [kimun.cli.main :as main]))

(def ^:dynamic *repo* nil)

(def ^:private model-text
  (str "{:base []\n"
       " :fields\n"
       " [{:description \"Name.\" :name \"name\" :py/annotation \"<class 'str'>\" :required true :type :string}\n"
       "  {:description \"Body.\" :name \"body\" :py/annotation \"<class 'str'>\" :required true :type :string}]\n"
       " :model/name \"TestDoc\"\n"
       " :model/version 1\n"
       " :semantics {\"type\" [\"test\"] \"workspace\" [\"test\"]}\n"
       " :template \"---\\nid: \u2e22rev\u2022name\u2e25\\n---\\n\\n# Title\\n\\n## Body\\n\\n\u2e22rev\u2022body\u2e25\\n\"}\n"))

(def ^:private doc-a
  (str "---\nid: N\n---\n\n# Title\n\n## Body\n\nB\n"))

(def ^:private include-text
  (str "{:include/version 1\n"
       " :store {:path \".kimun\" :git :ignore}\n"
       " :models [\"models/*.edn\"]\n"
       " :documents [{:glob \"docs/**/*.md\" :model \"TestDoc\"}]\n"
       " :exclude [\"docs/skip.md\"]}\n"))

(defn- git!
  "Runs `git ARGS…` in `dir`, raising on failure."
  [dir & args]
  (let [r (apply process/shell {:out :string :err :string :continue true :dir (str dir)} "git" args)]
    (when-not (zero? (:exit r))
      (throw (ex-info (str "git " (str/join " " args) " failed: " (:err r)) {})))
    r))

(defn- write! [dir rel text]
  (let [f (fs/path dir rel)]
    (some-> (fs/parent f) fs/create-dirs)
    (spit (str f) text)))

(defn- run-include [& argv]
  (:envelope (main/run (vec argv) {"USER" "tester"} (str *repo*))))

(defn- setup! [d]
  (git! d "init" "-q")
  (git! d "config" "user.email" "tester@example.com")
  (git! d "config" "user.name" "tester")
  (write! d "models/TestDoc.edn" model-text)
  (write! d "docs/a.md" doc-a)
  (write! d "kimun.include.edn" include-text)
  (git! d "add" "models/TestDoc.edn" "docs/a.md" "kimun.include.edn")
  (git! d "commit" "-qm" "init")
  ;; tracked later, in the tests that need it: an excluded document is a file the
  ;; declaration does not manage, so committing it would (correctly) be drift
  (write! d "docs/skip.md" "# skipped\n"))

(use-fixtures :each
  (fn [f]
    (let [d (fs/create-temp-dir {:prefix "kimun-include-"})]
      (try (setup! d) (binding [*repo* d] (f)) (finally (fs/delete-tree d))))))

(deftest build-then-check-is-clean
  (let [b (run-include "include" "build")]
    (is (:ok b) (pr-str b))
    (is (= 1 (get-in b [:data :models])))
    (is (= 1 (get-in b [:data :documents])))
    (testing "one declared document, one model, nothing to report"
      (let [c (run-include "include" "check")]
        (is (:ok c) (pr-str c))
        (is (every? zero? (vals (get-in c [:data :findings])))))
      (let [s (run-include "include" "status")]
        (is (= 1 (get-in s [:data :models])))
        (is (= 1 (get-in s [:data :documents])))))))

(deftest staged-store-file-is-rejected
  (run-include "include" "build")
  (git! *repo* "add" "-f" ".kimun/store.edn")
  (let [c (run-include "include" "check" "--staged")]
    (is (false? (:ok c)))
    (is (= 3 (:exit c)))
    (is (= :include/drift (keyword (get-in c [:error :type]))))
    (is (= 1 (get-in c [:error :data :findings :store-tracked])))
    (is (= 0 (get-in c [:error :data :findings :tracked-undeclared])))))

(deftest staged-excluded-document-is-not-a-finding
  (run-include "include" "build")
  (git! *repo* "add" "docs/skip.md")
  (let [c (run-include "include" "check" "--staged")]
    (testing "an excluded path is explicitly not a kimun document, tracked or staged"
      (is (:ok c) (pr-str c)))))

(deftest tracked-file-under-a-managed-glob-is-a-finding
  (testing "a tracked path under a managed glob, neither declared nor excluded, drifts"
    (write! *repo* "docs/ghost.md" "# ghost\n")
    (git! *repo* "add" "-f" "docs/ghost.md")
    (git! *repo* "commit" "-qm" "ghost")
    (fs/delete (fs/path *repo* "docs/ghost.md"))
    (let [c (run-include "include" "check")]
      (is (false? (:ok c)))
      (is (= ["docs/ghost.md"] (get-in c [:error :data :paths :tracked-undeclared]))))))

(deftest gitignore-is-idempotent-and-preserves-foreign-lines
  (write! *repo* ".gitignore" "foreign/\n*.tmp\n")
  (let [r1 (run-include "include" "gitignore" "--write")
        c1 (slurp (str (fs/path *repo* ".gitignore")))
        r2 (run-include "include" "gitignore" "--write")
        c2 (slurp (str (fs/path *repo* ".gitignore")))]
    (is (:ok r1) (pr-str r1))
    (is (:ok r2) (pr-str r2))
    (testing "idempotent, foreign lines kept, exactly one managed block"
      (is (= c1 c2))
      (is (str/includes? c1 "foreign/"))
      (is (str/includes? c1 "*.tmp"))
      (is (str/includes? c1 ".kimun/"))
      (is (= 1 (count (re-seq #"# >>> kimun include \(managed\) >>>" c1)))))))

(deftest hook-install-chains-an-existing-hook
  (let [hp (fs/path *repo* ".git/hooks/pre-commit")]
    (write! *repo* ".git/hooks/pre-commit" "#!/bin/sh\necho foreign\n")
    (fs/set-posix-file-permissions hp "rwxr-xr-x")
    (let [r (run-include "include" "hook" "--install")
          prev (fs/path (str hp ".kimun-prev"))]
      (is (:ok r) (pr-str r))
      (is (fs/exists? prev))
      (is (str/includes? (slurp (str prev)) "echo foreign"))
      (is (str/includes? (slurp (str hp)) "# kimun include hook (managed)"))
      (is (str/includes? (slurp (str hp)) "include check --staged"))
      (is (fs/executable? hp))
      (testing "installing again is idempotent; uninstall restores the chained hook"
        (is (:ok (run-include "include" "hook" "--install")))
        (is (:ok (run-include "include" "hook" "--uninstall")))
        (is (str/includes? (slurp (str hp)) "echo foreign"))
        (is (not (fs/exists? prev)))))))

(deftest build-reproduces-the-store
  (is (:ok (run-include "include" "build")))
  (let [before (run-include "docs" "show" "a")]
    (is (:ok before) (pr-str before))
    (is (:ok (run-include "include" "build" "--rebuild")))
    (let [after (run-include "docs" "show" "a")]
      (is (:ok after))
      (is (= (get-in before [:data :payload]) (get-in after [:data :payload]))))))
