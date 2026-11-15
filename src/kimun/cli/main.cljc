(ns kimun.cli.main
  "Entry point of the `kimun` CLI (docs/v2/06 §1): parses the command
   line, routes the first token to a command group, renders one envelope on
   stdout and exits with its code. `run` is the pure-of-process core used by
   tests; `-main` adds printing and `System/exit`."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [kimun.cli.args :as args]
            [kimun.cli.commands.direct :as direct]
            [kimun.cli.commands.adapters :as adapters]
            [kimun.cli.commands.corpus :as corpus]
            [kimun.cli.commands.docs :as docs]
            [kimun.cli.commands.edges :as edges]
            [kimun.cli.commands.fields :as fields]
            [kimun.cli.commands.find :as find]
            [kimun.cli.commands.graph :as graph]
            [kimun.cli.commands.include :as include]
            [kimun.cli.commands.index :as index]
            [kimun.cli.commands.migrate :as migrate]
            [kimun.cli.commands.models :as models]
            [kimun.cli.commands.predicates :as predicates]
            [kimun.cli.commands.sections :as sections]
            [kimun.cli.commands.semantic :as semantic]
            [kimun.cli.commands.journal :as journal]
            [kimun.cli.commands.links :as links]
            [kimun.cli.commands.serve :as serve]
            [kimun.cli.commands.stores :as stores]
            [kimun.cli.errors :as errors]
            [kimun.cli.store :as cli-store]
            [kimun.cli.out :as out]))

(def groups
  "First token → group dispatch `(fn [subcommand args opts ctx] envelope)`,
   where `args` are the positional tokens after the subcommand."
  {"stores" stores/dispatch
   "docs" (fn [sub args opts ctx]
            ;; `docs recover|compose` live in kimun.cli.commands.links
            (if-let [f (get links/commands sub)]
              (f args opts ctx)
              (docs/dispatch sub args opts ctx)))
   "adapters" adapters/dispatch
   "migrate" migrate/dispatch
   "edges" edges/dispatch
   "graph" graph/dispatch
   "predicates" predicates/dispatch
   "find" find/dispatch
   "corpus" corpus/dispatch
   "rebuild-indexes" index/dispatch
   "fields" fields/dispatch
   "sections" sections/dispatch
   "journal" journal/dispatch
   "semantic" semantic/dispatch
   "serve" serve/dispatch
   "models" models/dispatch
   "include" include/dispatch
   "extract" (direct/dispatcher "extract")
   "render" (direct/dispatcher "render")
   "validate" (direct/dispatcher "validate")})

(def group-specs
  "The option spec of every group, merged: a group declares its own options
   in its `spec` var, so adding a group never edits `kimun.cli.args`. Two
   groups may share an option name only with the same meaning."
  (merge stores/spec docs/spec journal/spec serve/spec models/spec include/spec direct/spec links/spec semantic/spec fields/spec sections/spec find/spec corpus/spec index/spec predicates/spec edges/spec graph/spec adapters/spec migrate/spec))

(def usage
  "Usage text shown by `kimun`, `kimun help` and `--help`."
  (str/join "\n"
            ["kimun — content-addressed knowledge store (SLDB v2)"
             ""
             "usage: kimun <group> <command> [options]"
             "       kimun --version | --help"
             ""
             "groups:"
             "  stores init [--name NAME] [--content by-reference|inline]   create .kimun/ in the working directory (or --store PATH)"
             "  stores check                open the store (replay) and report revisions, trees, objects"
             "  stores verify               recompute the hash of every reachable object"
             "  stores upgrade              upgrade a store to the current format (1→2, 2→3 in place; 3→4 asks for include build --rebuild; format 4: route map to the Merkle tree by path)"
             "  stores show                 descriptor and heads without replay"
             "  stores health               verdict: ok | degraded (run: …) | needs-human (inspect: …)"
             "  stores unreachable [--ids]  read-only inventory of stored objects the heads do not reach (removes nothing)"
             "  stores sense [--once]       one read-only observation: structured signals from tracked documents"
             "  stores heartbeat [--interval SECONDS] [--beats N]   repeat that observation; NDJSON stream on stdout, writes nothing"
             "  stores update               apply the repairs that need no decision (reformatted files, moved contracts)"
             "  stores reconcile [ROOT] [--apply]  compare stores found under ROOT with the links"
             "  stores link NAME PATH       link another store (a .kimun or its project dir); path stored relative to this store"
             "  stores unlink NAME          remove a link"
             "  stores links                list links with status (ok | missing | not-a-store)"
             "  docs ingest FILE [--name N] parse a Markdown file and commit it as a new document tree"
             "  docs update FILE --name N   re-ingest FILE into the existing document N (an outside edit)"
             "  docs render NAME|TREE       canonical Markdown of a document (--format text prints it raw)"
             "  docs list [--model M]       every document: name, tree id, and for typed ones model, state, path"
             "  docs create MODEL NAME --data FILE | --set k=v … [--path P] [--inline]   render a payload, track it, write the file"
             "  docs track MODEL FILE [--name N] [--force] [--inline]   track an existing Markdown file as a document of MODEL"
             "  docs update NAME [FILE] [--data F | --set k=v]   re-read the file or apply a payload"
             "  docs show NAME [--format md] [--ast] · docs status · docs untrack NAME · docs delete NAME [--yes]"
             "  include check [--staged] · gitignore [--write] · hook --install|--uninstall · build [--rebuild] · status"
             "                              what this repo versions in git, from kimun.include.edn"
             "  models define|field-add … [--dry-run | --backfill]   documents of a redefined model: list or rewrite them"
             "  models define -f X.edn | --from-python MOD:CLASS [--pythonpath P]   define a model or its next version"
             "  models list | show NAME [--format md] | history NAME | check [NAME] [-f DRAFT.edn]"
             "  models field-add NAME --field F --type T --description TEXT [--default D] [--required] | field-remove NAME --field F"
             "  models template NAME [-f FILE]   show the template, or replace it (new version)"
             "  extract [MODEL] --input DOC.md [-o FILE] [--allow-empty]   typed payload of a document (no store needed)"
             "  render [MODEL] --data PAYLOAD.json|yaml [-o FILE]   Markdown of a payload"
             "  validate [MODEL] --input DOC.md | --data FILE [--mode render-validity|strict-roundtrip]"
             "                              MODEL (or --model): X.edn | model NAME in the store | module:Class"
             "  docs recover DOC [--depth N] [--links-only] [--include-transclusions]   the [[links]] of a document and where they resolve"
             "  docs compose DOC [-o FILE|-]                  expand ![[transclusions]] into composed Markdown"
             "  find TERM [--in physical|semantic|all] [--type KIND,…] [--global] [--regex] [--fuzzy] [--where EXPR] [--select K,…] [--limit N]"
             "                              TERM may be an address: st.{Model}.doc.field · st.{Base+} · se.<tag> · gse.<tag>"
             "  corpus rank QUERY [--k N] [--threshold X] [--among ID,…] [--fields] [--refresh] · corpus refresh"
             "  rebuild-indexes             write the derived index of the head under .kimun/index/"
             "  edges show [ID | --from ID | --to ID] [--relation R] · check · node ID · nodes [--type T] · types · init · rebuild"
             "  edges relate|unrelate FROM TO --relation R · type-define NAME [--source-types a,b --target-types a,b …]"
             "  predicates add NAME --axis A [--description D] · list · show NAME · validate [NAME] · remove NAME"
             "  graph get ID · list [--type T] · query -f FILE.json · neighborhood ID [--depth N] [--direction D]"
             "  graph path FROM TO · cycles · order · components · central [--metric K] · isolated · similar ID"
             "  graph snapshot save|load FILE · export [-o FILE] · ingest-sldb FILE [-o OUT]"
             "  adapters list · scan|sync|check ADAPTER PATH --system S [--factory M:A] [--language L] [-- adapter options]"
             "                              code → documents through an out-of-process adapter: python-symbols, argparse-cli,"
             "                              click-cli, treesitter-symbols, or the path of any executable (docs/v2/11)"
             "  migrate --from-v1 [PATH] [--pythonpath P] [--dry-run] [--only MODEL] [--limit N] [--force]"
             "                              bring a sldb v1 store (.sldb) into this kimun store; idempotent"
             "  fields get DOC PATH · schema MODEL [FIELD] · query FIELD [--where EXPR] [--model M]"
             "  fields set|append DOC PATH VALUE · unset|clear DOC PATH · clean DOC PATH [--dedupe] [--drop-empty]"
             "  sections list DOC · fields DOC SECTION · find [TERM] [--in physical|semantic|both] [--regex] [--fuzzy] [--where EXPR]"
             "  semantic show [TAG] | tags [PREFIX]            a tag (parents, children, equivalents, documents) or the DAG; list tags"
             "  semantic parent-add|parent-remove CHILD PARENT · equivalent-add A B · export [-o FILE]"
             "  journal show [--limit N] [--by ACTOR] [--tree NAME|ID]   the write history, newest first (a view of the log)"
             "  journal verify              check the revision chain, the log replay and every reachable object"
             "  serve [--port N] [--host H] [--token T] [--cors]   HTTP API over the commands (default 127.0.0.1:8787)"
             "                              --token (or $KIMUN_SERVE_TOKEN) requires `Authorization: Bearer TOKEN`"
             ""
             "options:"
             "  --store PATH    store directory (.kimun or its project dir); else $KIMUN_STORE, else walk-up"
             "  --format FMT    json (default) | edn | text | md | yaml | ast"
             "  --actor NAME    actor recorded in transactions (default human/$USER)"
             "  --debug         print the stacktrace of an unexpected error to stderr"
             "  --wait          if another process is writing the store, wait for it (default: fail with store/busy)"
             ""
             "exit codes: 0 ok · 1 missing · 2 ambiguous · 3 rejected · 4 conflict · 5 store · 6 usage · 7 capability · 70 unexpected"]))

(defn- resource-text [name]
  (some-> (io/resource name) slurp str/trim))

(defn version-data
  "`{:version :kernel :bb :git}` from resources/VERSION, the running Babashka
   and resources/git-sha (\"dev\" when absent)."
  []
  {:version (or (resource-text "VERSION") "unknown")
   :kernel "sldb-v2"
   :bb (or (System/getProperty "babashka.version") "n/a")
   :git (or (resource-text "git-sha") "dev")})

(defn- format-of
  "The `--format` value present in raw `argv`, for rendering parse errors."
  [argv]
  (let [v (second (drop-while #(not= "--format" %) argv))]
    (if (contains? args/formats v) v "json")))

(defn- route [{:keys [args opts]} ctx argv]
  (let [[group sub] args]
    (cond
      (or (empty? argv) (:help opts) (= "help" group))
      (out/ok "help" {:data {:usage usage}})

      (or (:version opts) (= "version" group))
      (out/ok "version" {:data (version-data)})

      (contains? groups group)
      ((groups group) sub (vec (drop 2 args)) opts ctx)

      :else
      (out/failure :eval/not-available
                   (str "the evaluator surface (kimun " (str/join " " args) ") arrives in milestone S4; known groups: " (str/join ", " (sort (keys groups))))
                   {:tokens (vec args)}
                   6))))

(defn run
  "Runs `argv` with environment map `env` and working directory `cwd`; returns
   `{:envelope e :fmt fmt}` without printing or exiting. Every error becomes a
   failure envelope through `kimun.cli.errors/wrap`."
  [argv env cwd]
  (let [argv (vec argv)
        parsed (errors/wrap #(args/parse argv env group-specs))]
    (if (false? (:ok parsed))
      {:envelope parsed :fmt (format-of argv)}
      (let [ctx {:env env :cwd cwd}
            envelope (binding [cli-store/*wait* (boolean (get-in parsed [:opts :wait]))]
                       (errors/wrap #(route parsed ctx argv)))
            help? (= "help" (:command envelope))]
        {:envelope envelope
         :fmt (if help? "text" (get-in parsed [:opts :format]))
         :debug (boolean (get-in parsed [:opts :debug]))}))))

(defn -main
  "Prints the envelope of `run` to stdout and exits with its code; with
   `--debug` the stacktrace of a failure goes to stderr. An envelope whose
   metadata says `:streamed` already wrote its output and is not printed."
  [& argv]
  (let [{:keys [envelope fmt debug]} (run argv (System/getenv) (System/getProperty "user.dir"))]
    (when-not (:streamed (meta envelope))
      (println (out/emit envelope fmt)))
    (when-let [t (and debug (:throwable (meta envelope)))]
      (binding [*out* *err*] (.printStackTrace ^Throwable t (java.io.PrintWriter. *out* true))))
    (flush)
    (System/exit (out/exit-code envelope))))
