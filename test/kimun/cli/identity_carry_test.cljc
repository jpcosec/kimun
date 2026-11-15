(ns kimun.cli.identity-carry-test
  "K1a: `include build --rebuild` carries the identity of a document (its
   document id and its tree id) from the store it moves aside, read before the
   move, by qualified path; what it cannot carry is declared in `:identities`
   (docs/v2/14 §21, docs/v2/12 §1 K1)."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.surface.docs :as tdocs]))

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
    (let [d (ktu/temp-dir "kimun-k1a-")]
      (try (setup! d) (binding [*repo* d] (f)) (finally (fs/delete-tree d))))))

(defn- doc [nm]
  (let [s (:store (cli-store/open! (str *repo* "/.kimun")))]
    (first (filter #(= nm (:name %)) (tdocs/documents s)))))
(defn- tree [nm] (:tree (doc nm)))
(defn- doc-id [nm] (:doc (doc nm)))
(defn- route-entries []
  (let [s (:store (cli-store/open! (str *repo* "/.kimun")))]
    ;; the route root with it: the same files give the same root, and the entries
    ;; must name the same trees for the identities to have been carried
    (assoc (into {} (map (fn [[p e]] [p (:tree-id e)]) (:routes s)))
           ::root (:routes-root s))))

(deftest a-rebuild-carries-the-same-identities-when-nothing-changed
  (is (:ok (run-include "include" "build")))
  (let [t0 (tree "a") d0 (doc-id "a") routes0 (route-entries)]
    (is (string? t0)) (is (string? d0))
    (let [b (run-include "include" "build" "--rebuild")
          ids (get-in b [:data :identities])]
      (is (:ok b) (pr-str b))
      (is (= {:available true :carried 1 :minted 0 :not-carried [] :ambiguous [] :without-file 0}
             (dissoc ids :source)))
      (is (= t0 (tree "a")) "the tree id is carried")
      (is (= d0 (doc-id "a")) "the document id is carried")
      (is (= t0 (get routes0 "docs/a.md")) "the route entry names the tree")
      (is (= routes0 (route-entries)) "the route root and the tree ids of its entries are the same")
      (is (= (get-in (run-include "include" "build" "--rebuild") [:data :identities :carried]) 1)))))

(deftest a-rebuild-carries-the-identity-of-an-edited-file
  (is (:ok (run-include "include" "build")))
  (let [t0 (tree "a") d0 (doc-id "a")]
    (write! *repo* "docs/a.md" (str "---\nid: N\n---\n\n# Title\n\n## Body\n\nB2\n"))
    (git! *repo* "add" "docs/a.md")
    (git! *repo* "commit" "-qm" "edit")
    (let [b (run-include "include" "build" "--rebuild")]
      (is (:ok b) (pr-str b))
      (is (= t0 (tree "a")))
      (is (= d0 (doc-id "a")))
      (is (= 1 (get-in b [:data :identities :carried]))))))

(deftest what-cannot-be-carried-is-declared
  (is (:ok (run-include "include" "build")))
  (let [old-ids (set (map (fn [n] (tree n)) ["a"]))]
    ;; a document of a generic class, ingested by hand: the declaration does not
    ;; build it again, so the rebuild must say it was not carried
    (write! *repo* "notes/free.md" "# Free\n\ntext\n")
    (is (:ok (:envelope (main/run ["docs" "ingest" "notes/free.md"] {"USER" "tester"} (str *repo*)))))
    (write! *repo* "docs/b.md" (str "---\nid: N\n---\n\n# Title\n\n## Body\n\nB\n"))
    (fs/delete (fs/path *repo* "docs/a.md"))
    (git! *repo* "add" "-A")
    (git! *repo* "commit" "-qm" "swap")
    (let [b (run-include "include" "build" "--rebuild")
          ids (get-in b [:data :identities])]
      (is (:ok b) (pr-str b))
      (is (= ["docs/a.md" "notes/free.md"] (:not-carried ids))
          "the path that is gone and the document the declaration does not build are declared")
      (is (= [0 1] [(:carried ids) (:minted ids)]))
      (is (re-find #"^\.kimun\.bak-\d{8}-\d{6}$" (:source ids)) "and it names the store they were read from")
      (is (fs/exists? (fs/path *repo* (:source ids))))
      (is (some #(= "include/identities-not-carried" (:type %)) (:warnings b)))
      (is (not (contains? old-ids (tree "b"))) "the new document gets a fresh id"))))

(deftest an-unreadable-old-store-keeps-the-rebuild-and-says-so
  (is (:ok (run-include "include" "build")))
  (spit (str *repo* "/.kimun/heads.edn") "{:not edn")
  (let [b (run-include "include" "build" "--rebuild")
        ids (get-in b [:data :identities])]
    (is (:ok b) (pr-str b))
    (is (false? (:available ids)))
    (is (zero? (:carried ids)))
    (is (= 1 (:minted ids)) "the one declared document was minted, and is counted")
    (is (= [] (:not-carried ids)))
    (is (some #(= "include/identities-unavailable" (:type %)) (:warnings b)))
    (is (= 1 (get-in b [:data :documents])) "the store is built with fresh ids")))
