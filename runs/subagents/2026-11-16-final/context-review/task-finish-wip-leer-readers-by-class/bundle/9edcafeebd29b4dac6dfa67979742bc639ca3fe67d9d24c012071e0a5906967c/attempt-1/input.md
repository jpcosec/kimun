FILE:desk/tasks/task-finish-wip-leer-readers-by-class.md
---
sldb:
  by: agent
  at: '2026-11-16T19:08:06Z'
  authority: generated
  via: save_document_payload
id: task-finish-wip-leer-readers-by-class
status: draft
summary: ''
tags:
- workspace:desk
- artifact:task
current_node: ritual-execution
history: []
references:
- docs/v2/drafts/diseno-leer.md
- docs/v2/drafts/diseno-evaluar.md
- docs/v2/drafts/diseno-generar.md
- docs/v2/drafts/traspaso-2026-11-17.md
- docs/coordinacion/reglas-ejecutor.md
- docs/coordinacion/validate.sh
- AGENTS.md
depends_on:
- task-toolchain-on-robocop2-and-suite-reproduced
pills: []
files:
- src/kimun/surface/reading.cljc
- test/kimun/surface/reading_test.cljc
- test/kimun/cli/reading_test.cljc
- test/kimun/cli/declare_test.cljc
- test/kimun/cli/declare_project_test.cljc
- test/kimun/cli/mcp_test.cljc
task_type: task.code
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# Finish wip-leer readers by class

## Rationale

_Explain why this task exists or the business driver behind it._

The owner's criterion of done, 2026-11-16: kimun must ingest an advanced project such as deskops. The piece that opens it is 'readers by class': a declaration (reading (files "glob") reader-fn) whose function receives a view of each matching Markdown file (:path :front :sections :text) and declares propositions from it. Design: docs/v2/drafts/diseno-leer.md §3-§5 (this task is its 'cut 1'). The code is written on branch wip/leer (one commit, 83379739, on top of main's 1f933ab0) but its executor was cut off: three older tests fail and the trial on the real desk was never run.

## Goal

_Describe the concrete result this task must produce._

Branch wip/leer is rebased on origin/main, its tests and the whole suite pass, the readers have been run over a copy of the real deskops desk with the numbers recorded, and main is fast-forwarded to it and pushed.

## Scope

_State what is in scope and what is out of scope._

In scope: src/kimun/surface/reading.cljc, test/kimun/surface/reading_test.cljc, test/kimun/cli/reading_test.cljc (all already on the branch); the three tests that fail; src/kimun/cli/main.cljc only to resolve a rebase conflict in its help text; the reader drafts runs/subagents/2026-11-16-leer/deskops-readers/{base,atoms,tasks,materializations}.clj (on the branch, never run); README.md and docs/v2/drafts/diseno-leer.md (add a section 'Decidido al implementar'). Out of scope: any write under ~/proyectos/SLDB_1 (read-only: copy the desk, never run sldb or deskops inside it); readers for the other desk classes; any cache.

## Implementation Path

_Outline the expected implementation route or affected surface._

1. git fetch; git worktree add /tmp/kimun-leer-wt origin/wip/leer; git rebase origin/main (on conflict keep both sides' help lines in src/kimun/cli/main.cljc). 2. Fix the three tests: on this branch the :summary of 'declare read' gains the keys :readings :read-propositions :reading-errors only when the project declares readers (docstring of src/kimun/cli/commands/declare.cljc); test/kimun/cli/declare_test.cljc line 25 and test/kimun/cli/declare_project_test.cljc line 36 compare the whole map (= {:files 2 :relations 2 :propositions N} ...) and test/kimun/cli/mcp_test.cljc does the same: first run them to see the actual map; if the extra keys appear for a project WITHOUT readers that is a bug in the code (fix the code, not the test); otherwise update the expected map. 3. Reality trial: D=$(mktemp -d); cp -r ~/proyectos/SLDB_1/deskops/desk $D/desk; mkdir $D/kimun; copy the four reader drafts, with their names, to $D/kimun/base.clj, atoms.clj, tasks.clj, materializations.clj; (cd $D && git init -q && git add -A && git commit -qm copy); then from $D run 'time <repo>/bin/kimun declare read --format edn > read.edn': its :data :summary gives the counts and :data :reading-errors the failures. As a sanity check that asking works, 'bin/kimun declare terms' must list task ids and 'bin/kimun declare about <any id listed>' must return rows. Reading errors need not reach zero: each distinct cause is either fixed in the reader or named in the report with its count. 4. Write runs/subagents/YYYY-MM-DD-leer/REPORT.md (the day of the run): what was done, the numbers, every point where the design was ambiguous or wrong, declared limits. 5. Validate with docs/coordinacion/validate.sh (replace its /home/jp paths by this machine's), commit ('feat(declare): readers by class' + a separate 'chore(evidence)' commit, trailer 'Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>'), git checkout main, git merge --ff-only, push, remove the worktree and delete the branch locally and on origin.

## Validation

_List the checks required before this task can close._

- bb lint exits 0
- One directed run has 0 failures over: kimun.surface.reading-test kimun.cli.reading-test kimun.cli.declare-test kimun.cli.declare-project-test kimun.cli.mcp-test plus every other namespace under test/kimun/cli/ whose name starts with declare or stores (a sci evaluator inside Babashka can pass alone and fail in company)
- bb test: line 2 is tree-sitter pod: available and the last line has :fail 0 :error 0
- bb oracle exits 0 and bin/kimun include check is clean
- REPORT.md has, measured on the copy of the desk: Markdown files matched by each reader, propositions read (:read-propositions), reading errors (:reading-errors) with each distinct cause and its count, and wall-clock seconds of declare read

## Done When

_Name the observable condition that makes the task complete._

origin/main contains the feature commit and the evidence commit, REPORT.md is at runs/subagents/YYYY-MM-DD-leer/REPORT.md with the measured numbers that Validation lists, and the outputs of validate.sh are in the subfolder coordinator/ next to it. Start only when task-toolchain-on-robocop2-and-suite-reproduced is closed (bb and the pod must exist on the machine)./ If declare read on the desk takes more than 10 s, say so in REPORT.md: that number decides the task task-cache-of-readings-by-git-blob-hash.

FILE:test/kimun/cli/declare_test.cljc
(ns kimun.cli.declare-test
  "`kimun declare read` (docs/v2/06): the envelope, two files, declared
   failures with their exit codes, and that nothing is written to disk."
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.test-util :as ktu]))

(def env {"USER" "tester"})

(defn- run [cwd & argv] (:envelope (main/run argv env (str cwd))))

(defn- listing [dir]
  (into (sorted-map) (map (fn [p] [(str (fs/relativize dir p)) (when (fs/regular-file? p) (slurp (str p)))])) (fs/glob dir "**")))

(deftest read-two-files-without-touching-the-disk
  (let [dir (ktu/temp-dir "kimun-declare-")]
    (spit (str (fs/path dir "a.clj")) "(ns p.a)\n(relation isa \"d\")\n(isa x Y)\n")
    (spit (str (fs/path dir "b.clj")) "(ns p.b)\n(relation isa \"d\")\n(isa x Y)\n(isa z Y)\n")
    (let [before (listing dir)
          e (run dir "declare" "read" "a.clj" "b.clj")]
      (is (true? (:ok e)))
      (is (= "declare read" (:command e)))
      (is (nil? (:store e)))
      (is (= {:files 2 :relations 2 :propositions 3} (get-in e [:data :summary])))
      (is (= [["a.clj" "p.a" 1] ["b.clj" "p.b" 2]]
             (mapv (juxt :file :context (comp count :propositions)) (get-in e [:data :files]))))
      (is (= {:file "a.clj" :line 3} (get-in e [:data :files 0 :propositions 0 :at])))
      (is (= before (listing dir)) "no file created, none changed"))))

(deftest failures-are-declared-envelopes
  (let [dir (ktu/temp-dir "kimun-declare-")]
    (spit (str (fs/path dir "bad.clj")) "(ns p.a)\n(nope x y)\n")
    (spit (str (fs/path dir "ok.clj")) "(ns p.ok)\n")
    (testing "a file that does not evaluate: type, file and line, exit 3; one bad file fails the command"
      (let [e (run dir "declare" "read" "ok.clj" "bad.clj")]
        (is (false? (:ok e)))
        (is (= 3 (:exit e)))
        (is (= "declare/unresolved" (get-in e [:error :type])))
        (is (= "bad.clj" (get-in e [:error :data :file])))
        (is (= 2 (get-in e [:error :data :line])))))
    (testing "a missing file is exit 1"
      (let [e (run dir "declare" "read" "nope.clj")]
        (is (= ["declare/missing" 1] [(get-in e [:error :type]) (:exit e)]))))
    (testing "an unknown subcommand is usage (6)"
      (is (= 6 (:exit (run dir "declare" "write" "x")))))))

FILE:test/kimun/cli/declare_project_test.cljc
(ns kimun.cli.declare-project-test
  "`declare read|anchors` over the project folder and `declare add`
   (docs/v2/06, docs/v2/drafts/diseno-evaluar.md §9, cut 3a), in temporary
   directories."
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.test-util :as ktu]))

(def env {"USER" "tester"})

(defn- run [cwd & argv] (:envelope (main/run argv env (str cwd))))

(defn- listing [dir]
  (into (sorted-map) (map (fn [p] [(str (fs/relativize dir p)) (when (fs/regular-file? p) (slurp (str p)))])) (fs/glob dir "**")))

(defn- put! [dir rel text]
  (fs/create-dirs (fs/parent (fs/path dir rel)))
  (spit (str (fs/path dir rel)) text))

(defn- project []
  (let [dir (ktu/temp-dir "kimun-proj-")]
    (put! dir "kimun/a.clj" "(ns a)\n(relation isa \"d\")\n(relation anchors \"d\")\n(isa x Y)\n")
    (put! dir "kimun/sub/b.clj" "(ns b (:require [a :refer [isa]]))\n(isa z Y)\n")
    (put! dir "other.clj" "(ns other)\n(relation r \"d\")\n(r p q)\n")
    (put! dir "kimun/notes.txt" "not a declaration")
    dir))

(deftest read-without-files-reads-the-project-folder
  (let [dir (project)
        before (listing dir)
        e (run dir "declare" "read")]
    (is (true? (:ok e)))
    (is (= [["kimun/a.clj" "a"] ["kimun/sub/b.clj" "b"]] (mapv (juxt :file :context) (get-in e [:data :files]))) "recursive, sorted by path, relative to the cwd, only *.clj")
    (is (= ["a/isa"] (mapv :r (get-in e [:data :files 1 :propositions]))) "b uses the relation of a, which comes from the same folder")
    (is (= {:files 2 :relations 2 :propositions 2} (get-in e [:data :summary])))
    (is (= before (listing dir)) "nothing written")))

(deftest explicit-files-are-the-whole-set
  (let [dir (project)]
    (testing "other.clj alone: not the project"
      (let [e (run dir "declare" "read" "other.clj")]
        (is (= ["other.clj"] (mapv :file (get-in e [:data :files]))))))
    (testing "b alone, under kimun/: the context it requires is loaded as a dependency, and listed apart"
      (let [e (run dir "declare" "read" "kimun/sub/b.clj")]
        (is (= [["kimun/sub/b.clj"] ["kimun/a.clj"]] [(mapv :file (get-in e [:data :files])) (get-in e [:data :required])]))))
    (testing "outside kimun/ the set is only the files named"
      (put! dir "other2.clj" "(ns other2 (:require [a :refer [isa]]))\n")
      (let [e (run dir "declare" "read" "other2.clj")]
        (is (= ["declare/unknown-context" 3 "other2.clj" 1] [(get-in e [:error :type]) (:exit e) (get-in e [:error :data :file]) (get-in e [:error :data :line])]))))
    (testing "both named: the set is complete"
      (is (true? (:ok (run dir "declare" "read" "kimun/sub/b.clj" "kimun/a.clj")))))))

(deftest no-project-folder-or-an-empty-one-is-an-empty-answer
  (let [dir (ktu/temp-dir "kimun-proj-")]
    (doseq [step [identity (fn [d] (fs/create-dirs (fs/path d "kimun")))]]
      (step dir)
      (doseq [cmd ["read" "anchors"]]
        (let [e (run dir "declare" cmd)]
          (is (true? (:ok e)) cmd)
          (is (= [] (get-in e [:data :files])) cmd)
          (is (= 0 (get-in e [:data :summary :files])) cmd))))))

(deftest anchors-without-files-resolve-over-the-project
  (let [dir (project)]
    (put! dir "kimun/sub/c.clj" "(ns c (:require [a :refer [anchors]]))\n(anchors thing (at \"docs/s.md\" [\"S\"]))\n")
    (put! dir "docs/s.md" "# S\n\ntext\n")
    (let [e (run dir "declare" "anchors")]
      (is (true? (:ok e)) (pr-str (:error e)))
      (is (= ["kimun/a.clj" "kimun/sub/b.clj" "kimun/sub/c.clj"] (mapv :file (get-in e [:data :files]))))
      (is (= [["a/anchors" :resolved]] (mapv (juxt :r (comp keyword :status)) (get-in e [:data :anchors])))))))

(deftest add-appends-a-form-and-reports-what-is-new
  (let [dir (project)
        before (listing dir)
        e (run dir "declare" "add" "--to" "kimun/sub/b.clj" "(isa new Thing)")]
    (is (true? (:ok e)) (pr-str (:error e)))
    (is (= {:file "kimun/sub/b.clj" :added true :relations []} (select-keys (:data e) [:file :added :relations])))
    (is (= ["a/isa"] (mapv :r (get-in e [:data :propositions]))))
    (is (= {:file "kimun/sub/b.clj" :line 3} (get-in e [:data :propositions 0 :at])))
    (is (= (str (get before "kimun/sub/b.clj") "(isa new Thing)\n") (slurp (str (fs/path dir "kimun/sub/b.clj")))) "one form on a new last line")
    (is (= (dissoc before "kimun/sub/b.clj") (dissoc (listing dir) "kimun/sub/b.clj")) "no other file touched, no temporary left")
    (testing "a new relation counts as new"
      (let [e2 (run dir "declare" "add" "--to" "kimun/a.clj" "(relation summarizes \"d\")")]
        (is (= [true ["summarizes"] []] [(get-in e2 [:data :added]) (get-in e2 [:data :relations]) (get-in e2 [:data :propositions])]))))))

(deftest add-that-does-not-evaluate-writes-nothing
  (let [dir (project)
        before (listing dir)]
    (doseq [[form type] [["(nope x y)" "declare/unresolved"] ["(isa only-one)" "declare/arity"] ["(isa x" "declare/syntax"]]]
      (let [e (run dir "declare" "add" "--to" "kimun/sub/b.clj" form)]
        (is (false? (:ok e)) form)
        (is (= [type 3 "kimun/sub/b.clj"] [(get-in e [:error :type]) (:exit e) (get-in e [:error :data :file])]) form)
        (is (= before (listing dir)) "byte for byte the same")))
    (testing "evaluated with the project: a form that breaks ANOTHER file of the set is refused too"
      (let [e (run dir "declare" "add" "--to" "kimun/a.clj" "(relation isa \"another doc\")")]
        (is (= "declare/relation-redefined" (get-in e [:error :type])))
        (is (= before (listing dir)))))))

(deftest add-of-what-is-already-declared-writes-nothing
  (let [dir (project)
        before (listing dir)
        e (run dir "declare" "add" "--to" "kimun/a.clj" "(isa x Y)")]
    (is (true? (:ok e)))
    (is (= {:added false :propositions [] :relations []} (select-keys (:data e) [:added :propositions :relations])))
    (is (string? (get-in e [:data :reason])))
    (is (= before (listing dir)))))

(deftest add-with-context-creates-the-file
  (let [dir (project)]
    (testing "without the file and without --context it is usage"
      (is (= 6 (:exit (run dir "declare" "add" "--to" "kimun/new.clj" "(isa q r)"))))
      (is (not (fs/exists? (fs/path dir "kimun/new.clj")))))
    (testing "--context creates it with its ns, requiring what it needs"
      (let [e (run dir "declare" "add" "--to" "kimun/deep/new.clj" "--context" "n" "(relation r \"d\")")]
        (is (true? (:ok e)) (pr-str (:error e)))
        (is (= "(ns n)\n(relation r \"d\")\n" (slurp (str (fs/path dir "kimun/deep/new.clj")))))
        (is (= ["r"] (get-in e [:data :relations])))))
    (testing "no --to is usage"
      (is (= 6 (:exit (run dir "declare" "add" "(isa q r)")))))))

FILE:test/kimun/cli/mcp_test.cljc
(ns kimun.cli.mcp-test
  "`kimun mcp` (docs/v2/06, section `mcp`): the pure handler of `kimun.surface.mcp`
   (methods, errors, the tool table and its argv) and the real process over stdio."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.surface.mcp :as mcp]
            [kimun.test-util :as ktu]))

(defn- ctx-with
  "A ctx whose `run` records its argv in the returned atom and answers `envelope`."
  [envelope]
  (let [calls (atom [])]
    [calls {:run (fn [argv env cwd] (swap! calls conj {:argv argv :env env :cwd cwd}) {:envelope envelope})
            :env {"E" "1"} :cwd "/work" :version "9.9"}]))

(defn- ask [ctx msg] (some-> (mcp/handle-line (json/generate-string msg) ctx) json/parse-string))

(def ok-envelope {:ok true :command "x" :data {:a 1}})

(deftest methods-and-ids
  (let [[_ ctx] (ctx-with ok-envelope)]
    (testing "initialize echoes a known protocol version, else gives the default; ids keep their type"
      (let [r (ask ctx {"jsonrpc" "2.0" "id" 7 "method" "initialize" "params" {"protocolVersion" "2025-03-26"}})]
        (is (= 7 (get r "id")))
        (is (= {"protocolVersion" "2025-03-26" "capabilities" {"tools" {}}
                "serverInfo" {"name" "kimun" "version" "9.9"}}
               (get r "result"))))
      (let [r (ask ctx {"jsonrpc" "2.0" "id" "s" "method" "initialize" "params" {"protocolVersion" "1999-01-01"}})]
        (is (= "s" (get r "id")))
        (is (= "2024-11-05" (get-in r ["result" "protocolVersion"])))))
    (testing "ping"
      (is (= {} (get (ask ctx {"jsonrpc" "2.0" "id" 1 "method" "ping"}) "result"))))
    (testing "a notification is never answered, known or unknown, with or without params"
      (is (nil? (mcp/handle-line "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}" ctx)))
      (is (nil? (mcp/handle-line "{\"jsonrpc\":\"2.0\",\"method\":\"nope\"}" ctx)))
      (is (nil? (mcp/handle-line "{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"zz\"}}" ctx))))
    (testing "errors"
      (is (= -32601 (get-in (ask ctx {"jsonrpc" "2.0" "id" 2 "method" "resources/list"}) ["error" "code"])))
      (let [r (json/parse-string (mcp/handle-line "{not json" ctx))]
        (is (= -32700 (get-in r ["error" "code"])))
        (is (contains? r "id") "id is null, present")
        (is (nil? (get r "id"))))
      (is (= -32602 (get-in (ask ctx {"jsonrpc" "2.0" "id" 3 "method" "tools/call"}) ["error" "code"]))))))

(deftest the-tool-table-is-listed-with-valid-schemas
  (let [[_ ctx] (ctx-with ok-envelope)
        listed (get-in (ask ctx {"jsonrpc" "2.0" "id" 1 "method" "tools/list"}) ["result" "tools"])]
    (is (= ["kimun_declare_read" "kimun_declare_anchors" "kimun_declare_terms" "kimun_declare_about"
            "kimun_declare_members" "kimun_changed" "kimun_seen" "kimun_health"
            "kimun_declare_generate_check" "kimun_declare_generate" "kimun_declare_add"]
           (mapv #(get % "name") listed)))
    (doseq [t listed]
      (is (string? (get t "description")))
      (is (= "object" (get-in t ["inputSchema" "type"])))
      (is (map? (get-in t ["inputSchema" "properties"])))
      (is (every? (set (keys (get-in t ["inputSchema" "properties"]))) (get-in t ["inputSchema" "required"])))
      (is (= (not (#{"kimun_declare_add" "kimun_declare_generate"} (get t "name"))) (get-in t ["annotations" "readOnlyHint"]))
          "only declare_add and declare_generate are not read-only"))))

(deftest each-tool-builds-its-argv
  (let [cases [["kimun_declare_read" {} ["declare" "read"]]
               ["kimun_declare_read" {"files" ["a.clj" "b.clj"]} ["declare" "read" "a.clj" "b.clj"]]
               ["kimun_declare_anchors" {"since" "abc"} ["declare" "anchors" "--since" "abc"]]
               ["kimun_declare_anchors" {} ["declare" "anchors"]]
               ["kimun_declare_terms" {} ["declare" "terms"]]
               ["kimun_declare_about" {"term" "UI.view" "exact" true} ["declare" "about" "UI.view" "--exact"]]
               ["kimun_declare_about" {"term" "UI" "exact" false "files" ["a.clj"]} ["declare" "about" "UI" "a.clj"]]
               ["kimun_declare_about" {"path" "src/x.clj"} ["declare" "about" "--path" "src/x.clj"]]
               ["kimun_declare_members" {"pattern" "src/**"} ["declare" "members" "src/**"]]
               ["kimun_changed" {"since" "HEAD~1"} ["stores" "changed" "--since" "HEAD~1"]]
               ["kimun_changed" {} ["stores" "changed"]]
               ["kimun_seen" {} ["stores" "seen"]]
               ["kimun_health" {} ["stores" "health"]]
               ["kimun_declare_generate_check" {} ["declare" "generate" "--check"]]
               ["kimun_declare_generate" {} ["declare" "generate"]]
               ["kimun_declare_add" {"to" "k/a.clj" "form" "(isa x Y)"} ["declare" "add" "--to" "k/a.clj" "(isa x Y)"]]
               ["kimun_declare_add" {"to" "k/a.clj" "form" "(isa x Y)" "context" "p.a"}
                ["declare" "add" "--to" "k/a.clj" "(isa x Y)" "--context" "p.a"]]]]
    (doseq [[tool args argv] cases]
      (let [[calls ctx] (ctx-with ok-envelope)
            r (ask ctx {"jsonrpc" "2.0" "id" 1 "method" "tools/call" "params" {"name" tool "arguments" args}})]
        (is (= [{:argv argv :env {"E" "1"} :cwd "/work"}] @calls) (str tool " " args))
        (is (false? (get-in r ["result" "isError"])))
        (is (= ok-envelope (-> r (get-in ["result" "content" 0 "text"]) (json/parse-string true)))
            "the envelope as it is, in a text content")
        (is (= "text" (get-in r ["result" "content" 0 "type"])))))))

(deftest a-failed-command-is-an-error-result-not-a-protocol-error
  (let [failed {:ok false :command "declare read" :error {:type "declare/missing" :message "nf"} :exit 3}
        [_ ctx] (ctx-with failed)
        r (ask ctx {"jsonrpc" "2.0" "id" 1 "method" "tools/call" "params" {"name" "kimun_declare_read" "arguments" {}}})]
    (is (nil? (get r "error")))
    (is (true? (get-in r ["result" "isError"])))
    (is (= "declare/missing" (get-in (json/parse-string (get-in r ["result" "content" 0 "text"])) ["error" "type"])))))

(deftest invalid-calls-never-reach-the-cli
  (let [[calls ctx] (ctx-with ok-envelope)
        code (fn [name args]
               (get-in (ask ctx {"jsonrpc" "2.0" "id" 1 "method" "tools/call" "params" {"name" name "arguments" args}})
                       ["error" "code"]))]
    (is (= -32602 (code "stores_check" {})) "a command outside the table")
    (is (= -32602 (code "stores check" {})) "a CLI command line is not a tool name")
    (is (= -32602 (code "declare read" {})) "nor is declare read")
    (is (= -32602 (code "kimun_health" {"argv" ["docs" "delete"]})) "an argument outside the schema")
    (is (= -32602 (code "kimun_declare_members" {})) "a missing required argument")
    (is (= -32602 (code "kimun_declare_read" {"files" "a.clj"})) "a wrong type")
    (is (= -32602 (code "kimun_declare_about" {})) "neither term nor path")
    (is (= -32602 (code "kimun_declare_about" {"term" "a" "path" "b"})) "both term and path")
    (is (= -32602 (code "kimun_declare_read" [])) "arguments not an object")
    (testing "a value that starts with - is never an option for the CLI"
      (doseq [[name args] [["kimun_declare_about" {"term" "--path"}]
                           ["kimun_declare_read" {"files" ["--since" "x"]}]
                           ["kimun_declare_add" {"to" "--context" "form" "(isa x Y)"}]
                           ["kimun_declare_add" {"to" "a.clj" "form" "-x"}]
                           ["kimun_declare_anchors" {"since" "--full"}]
                           ["kimun_declare_terms" {"files" ["-h"]}]
                           ["kimun_declare_about" {"path" "--exact"}]
                           ["kimun_declare_members" {"pattern" "--help"}]
                           ["kimun_changed" {"since" "-1"}]]]
        (let [r (ask ctx {"jsonrpc" "2.0" "id" 1 "method" "tools/call" "params" {"name" name "arguments" args}})]
          (is (= -32602 (get-in r ["error" "code"])) (str name args))
          (is (str/includes? (get-in r ["error" "message"]) "starts with `-`")))))
    (is (empty? @calls))))

;; ------------------------------------------------------------ the real process

(defn- exchange
  "Runs `bin/kimun mcp` in `dir`, writes `lines` to stdin, closes it; `{:out :err :exit}`."
  [dir lines]
  (let [bin (str (fs/absolutize "bin/kimun"))
        p (process/process {:dir dir :in (str (str/join "\n" lines) "\n") :out :string :err :string
                            :extra-env {"USER" "tester"}} bin "mcp")
        r @p]
    {:out (:out r) :err (:err r) :exit (:exit r)}))

(deftest the-process-speaks-json-rpc-over-stdio
  (let [dir (ktu/temp-dir "kimun-mcp-")
        file (str (fs/path dir "kimun" "a.clj"))
        _ (fs/create-dirs (fs/path dir "kimun"))
        _ (spit file "(ns p.a)\n(relation isa \"d\")\n(isa x Y)\n")
        call (fn [id name args] (json/generate-string {"jsonrpc" "2.0" "id" id "method" "tools/call" "params" {"name" name "arguments" args}}))
        before (slurp file)
        reads (exchange dir [(json/generate-string {"jsonrpc" "2.0" "id" 1 "method" "initialize" "params" {"protocolVersion" "2024-11-05"}})
                             "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"
                             "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"
                             (call 3 "kimun_declare_read" {})])
        lines (str/split-lines (:out reads))
        resp (mapv json/parse-string lines)]
    (is (= 0 (:exit reads)))
    (is (= [1 2 3] (mapv #(get % "id") resp)) "one line per request, none for the notification, nothing else")
    (is (= 11 (count (get-in resp [1 "result" "tools"]))))
    (is (= {"files" 1 "relations" 1 "propositions" 1}
           (-> (get-in resp [2 "result" "content" 0 "text"]) json/parse-string (get-in ["data" "summary"]))))
    (is (= before (slurp file)) "reading changed nothing")
    (let [added (exchange dir [(call 4 "kimun_declare_add" {"to" "kimun/a.clj" "form" "(isa z Y)"})])
          r (json/parse-string (first (str/split-lines (:out added))))]
      (is (= 0 (:exit added)))
      (is (= 1 (count (str/split-lines (:out added)))))
      (is (false? (get-in r ["result" "isError"])))
      (is (str/includes? (slurp file) "(isa z Y)") "add wrote"))))

FILE:instruccion
Cold-bundle de la tarea task-finish-wip-leer-readers-by-class: con el TaskDoc, los rituales enrutados por el Board, las pills ligadas, los átomos ligados y los archivos que el TaskDoc nombra, reformula el encargo, lista ambigüedades con cita textual del input y da el DRY-RUN sin ejecutar nada. No ejecutes nada.

Sección de validación del TaskDoc:
- bb lint exits 0
- One directed run has 0 failures over: kimun.surface.reading-test kimun.cli.reading-test kimun.cli.declare-test kimun.cli.declare-project-test kimun.cli.mcp-test plus every other namespace under test/kimun/cli/ whose name starts with declare or stores (a sci evaluator inside Babashka can pass alone and fail in company)
- bb test: line 2 is tree-sitter pod: available and the last line has :fail 0 :error 0
- bb oracle exits 0 and bin/kimun include check is clean
- REPORT.md has, measured on the copy of the desk: Markdown files matched by each reader, propositions read (:read-propositions), reading errors (:reading-errors) with each distinct cause and its count, and wall-clock seconds of declare read

