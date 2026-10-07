(ns kimun.cli.links-test
  "`docs recover` and `docs compose` end to end on temporary stores and files.
   The command fns are called directly (`kimun.cli.commands.links/commands`);
   stores are set up through `kimun.cli.main/run`. Each test names in a comment
   the sldb v1 test function it ports."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.commands.links :as lc]
            [kimun.cli.main :as main]
            [kimun.cli.out :as out]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))

(def note
  (pr-str {:model/name "NoteDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "body" :type :string :required true :description "Body."}]
           :template "# ⸢rev•title⸥\n\n⸢rev•body⸥\n"}))

(defn- project
  "A project directory with a store and the NoteDoc model."
  []
  (let [p (str (ktu/temp-dir "kimun-links-"))]
    (run p "stores" "init" "--name" "p")
    (spit (str (fs/path p "note.edn")) note)
    (is (true? (:ok (run p "models" "define" "-f" "note.edn"))))
    p))

(defn- write! [p name text]
  (let [f (fs/path p name)] (some-> (fs/parent f) fs/create-dirs) (spit (str f) text)))

(defn- track! [p & files]
  (doseq [f files] (is (true? (:ok (run p "docs" "track" "NoteDoc" f))) f)))

(defn- cmd [sub p args opts]
  ((get lc/commands sub) args (merge {:format "json" :actor "human/tester"} opts) {:env env :cwd p}))

(defn- fixture
  "concept, constraints and root (links to one, transcludes the other), tracked."
  []
  (let [p (project)]
    (write! p "concept.md" "# Concept\n\nBackground info.\n")
    (write! p "constraints.md" "# Constraints\n\nAlways validate first.\n")
    (write! p "root.md" "# Root\n\nSee [[concept]] for background.\n\n![[constraints]]\n")
    (track! p "concept.md" "constraints.md" "root.md")
    p))

(deftest recover-and-compose-links
  ;; v1: test_recover_and_compose_links (CLI part)
  (let [p (fixture)
        r (cmd "recover" p ["root.md"] {:include-transclusions true})
        links (get-in r [:data :links])]
    (is (true? (:ok r)) (pr-str r))
    (is (= "root" (get-in r [:data :root])))
    (is (= ["concept" :link true] ((juxt :target :kind :resolved) (links 0))))
    (is (= ["constraints" :transclusion true] ((juxt :target :kind :resolved) (links 1))))
    (is (= [] (get-in r [:data :unresolved])))
    (let [c (cmd "compose" p ["root.md"] {:format "text"})]
      (is (true? (:ok c)))
      (is (string? (:data c)) "text prints the Markdown itself")
      (is (str/includes? (:data c) "See [[concept]] for background."))
      (is (str/includes? (:data c) "# Constraints"))
      (is (str/includes? (:data c) "Always validate first.")))))

(deftest recover-and-compose-accept-tracked-names-and-yaml
  ;; v1: test_docs_recover_and_compose_accept_tracked_doc_names_and_yaml
  (let [p (fixture)
        r (cmd "recover" p ["root"] {})]
    (is (true? (:ok r)) (pr-str r))
    (is (= #{"concept" "constraints"} (set (map :target (get-in r [:data :links])))))
    (testing "the name resolves from any directory of the project"
      (write! p "sub/x.txt" "")
      (is (true? (:ok ((get lc/commands "recover") ["root"] {:format "json"} {:env env :cwd (str (fs/path p "sub"))})))))
    (let [c (cmd "compose" p ["root"] {:format "yaml"})
          y (out/emit c "yaml")]
      (is (true? (:ok c)))
      (is (map? (:data c)))
      (is (str/includes? y "root: root") "kimun nests v1's top-level keys under data:")
      (is (str/includes? y "transclusions:"))
      (is (str/includes? y "data:")))))

(deftest recover-predicate-links
  ;; v1: test_recover_predicate_links_cli
  (let [p (project)]
    (write! p "spec.md" "# Spec\n\nS.\n")
    (write! p "research.md" "# Research\n\nR.\n")
    (write! p "task.md" "# Task\n\n[implements:: [[spec]]]\n[grounded_by:: [[research]]]\n")
    (track! p "spec.md" "research.md" "task.md")
    (let [r (cmd "recover" p ["task.md"] {})
          preds (filter #(= :predicate-link (:kind %)) (get-in r [:data :links]))
          by (into {} (map (juxt :predicate identity)) preds)]
      (is (true? (:ok r)) (pr-str r))
      (is (= 2 (count preds)))
      (is (= #{"implements" "grounded_by"} (set (keys by))))
      (is (= "HOW" (:w5h1-type (by "implements"))))
      (is (= "PROVENANCE" (:w5h1-type (by "grounded_by"))))
      (is (= ["predicate_link: [implements][HOW] spec [ok]" "predicate_link: [grounded_by][PROVENANCE] research [ok]"]
             (:data (cmd "recover" p ["task.md"] {:format "text"})))))))

(deftest recover-links-only
  ;; v1: test_recover_links_only (no store: a plain file path; unresolved -> exit 1)
  (let [d (str (ktu/temp-dir "kimun-links-"))]
    (write! d "test.md" "# Test\n[[target1]]\n[[target2]]")
    (let [e (cmd "recover" d ["test.md"] {:links-only true :format "text"})
          s (out/emit e "text")]
      (is (false? (:ok e)))
      (is (= 1 (out/exit-code e)))
      (is (= "links/missing" (get-in e [:error :type])))
      (is (= ["target1" "target2"] (get-in e [:error :data :targets])))
      (is (str/includes? s "target1"))
      (is (str/includes? s "target2"))
      (is (not (str/includes? s "link:"))))
    (testing "all resolved: the sorted targets are the data"
      (write! d "target1.md" "x") (write! d "target2.md" "y")
      (write! d "ok.md" "[[target2.md]] [[target1.md]] [[target2.md]]")
      (is (= ["target1.md" "target2.md"] (:data (cmd "recover" d ["ok.md"] {:links-only true})))))))

(deftest recover-depth
  ;; v1: test_recover_depth
  (let [d (str (ktu/temp-dir "kimun-links-"))]
    (write! d "doc1.md" "# Doc 1\n[[doc2.md]]")
    (write! d "doc2.md" "# Doc 2\n[[target3]]")
    (let [e1 (cmd "recover" d ["doc1.md"] {:format "text"})
          s1 (out/emit e1 "text")]
      (is (true? (:ok e1)))
      (is (str/includes? s1 "doc2.md"))
      (is (not (str/includes? s1 "target3"))))
    (let [e2 (cmd "recover" d ["doc1.md"] {:format "text" :depth "2"})
          s2 (out/emit e2 "text")]
      (is (= 1 (out/exit-code e2)) "target3 is unresolved")
      (is (str/includes? s2 "doc2.md"))
      (is (str/includes? s2 "target3"))
      (is (= ["target3"] (get-in e2 [:error :data :unresolved]))))
    (is (= 2 (count (get-in (cmd "recover" d ["doc1.md"] {:depth 2}) [:error :data :links])))
        "a numeric depth (as babashka.cli may deliver it) works too")))

(deftest bad-arguments
  ;; no v1 test: v1 argparse rejected these; here they are typed errors
  (let [d (str (ktu/temp-dir "kimun-links-"))
        raises (fn [f] (try (f) nil (catch clojure.lang.ExceptionInfo e (ex-data e))))]
    (write! d "a.md" "x")
    (is (= :cli/usage (:type (raises #(cmd "recover" d ["a.md"] {:depth "zero"})))))
    (is (= :cli/usage (:type (raises #(cmd "recover" d ["a.md"] {:depth "0"})))))
    (is (= :cli/usage (:type (raises #(cmd "compose" d [] {})))))
    (is (= :links/missing (:type (raises #(cmd "compose" d ["ghost"] {})))))))

(deftest lookup-precedence
  ;; v1: resolve_link_target order (tracked name > beside the document > relative to the root; the root replaces v1's cwd)
  (let [p (project)]
    (write! p "notes/a.md" "# A\n\nA body.\n")
    (write! p "notes/doc.md" "# Doc\n\nuses [[a.md]] and [[shared.md]] and [[a]]\n")
    (write! p "notes/shared.md" "beside")
    (write! p "shared.md" "at root")
    (write! p "a.md" "# A\n\nroot a.\n")
    (track! p "notes/a.md")
    (let [links (get-in (cmd "recover" p ["notes/doc.md"] {}) [:data :links])
          by (into {} (map (juxt :target identity)) links)
          rel #(str (fs/relativize p (:path %)))]
      (is (= ["notes/a.md" :path] [(rel (by "a.md")) (:source (by "a.md"))]) "beside the document beats the root")
      (is (= "notes/shared.md" (rel (by "shared.md"))))
      (is (= [:store "notes/a.md"] [(:source (by "a")) (rel (by "a"))]) "a tracked name is the store"))
    (testing "the root is not the cwd"
      (let [sub (str (fs/path p "elsewhere"))]
        (fs/create-dirs sub)
        (write! sub "only-here.md" "cwd only")
        (write! p "pr.md" "[[only-here.md]]")
        (let [r ((get lc/commands "recover") ["../pr.md"] {:format "json"} {:env env :cwd sub})]
          (is (false? (:ok r)) "only-here.md sits in the cwd, not beside the document nor under the root")
          (is (= ["only-here.md"] (get-in r [:error :data :unresolved])))))))
  (testing "a tracked document is read from the store, not the file"
    (let [p (project)]
      (write! p "t.md" "# T\n\nstored.\n")
      (track! p "t.md")
      (write! p "t.md" "# T\n\nCHANGED ON DISK.\n")
      (write! p "r.md" "![[t]]\n")
      (is (str/includes? (:data (cmd "compose" p ["r.md"] {:format "text"})) "stored.")))))

(deftest compose-output-and-unresolved
  ;; v1: _write_composed (-o FILE | -), exit 1 on unresolved transclusions
  (let [p (fixture)
        c (cmd "compose" p ["root"] {:output "out/composed.md"})]
    (is (true? (:ok c)))
    (is (= (str (fs/path p "out" "composed.md")) (get-in c [:data :output])))
    (is (nil? (get-in c [:data :markdown])))
    (is (str/includes? (slurp (get-in c [:data :output])) "Always validate first."))
    (is (str/ends-with? (slurp (get-in c [:data :output])) "\n"))
    (is (string? (:data (cmd "compose" p ["root"] {:output "-"}))) "-o - is the Markdown on stdout"))
  (let [p (project)]
    (write! p "u.md" "# U\n\n![[nothing]]\n")
    (let [c (cmd "compose" p ["u.md"] {:output "u-out.md"})]
      (is (= 1 (out/exit-code c)))
      (is (= ["nothing"] (get-in c [:error :data :unresolved])))
      (is (fs/exists? (fs/path p "u-out.md")) "the output is written even when links are unresolved"))))
