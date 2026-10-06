(ns kimun.cli.main
  "Entry point of the `kimun` CLI (docs/v2/06 §B): parses the command
   line, routes the first token to a command group, renders one envelope on
   stdout and exits with its code. `run` is the pure-of-process core used by
   tests; `-main` adds printing and `System/exit`."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [kimun.cli.args :as args]
            [kimun.cli.commands.docs :as docs]
            [kimun.cli.commands.journal :as journal]
            [kimun.cli.commands.serve :as serve]
            [kimun.cli.commands.stores :as stores]
            [kimun.cli.errors :as errors]
            [kimun.cli.out :as out]))

(def groups
  "First token → group dispatch `(fn [subcommand args opts ctx] envelope)`,
   where `args` are the positional tokens after the subcommand."
  {"stores" stores/dispatch
   "docs" docs/dispatch
   "journal" journal/dispatch
   "serve" serve/dispatch})

(def group-specs
  "The option spec of every group, merged: a group declares its own options
   in its `spec` var, so adding a group never edits `kimun.cli.args`. Two
   groups may share an option name only with the same meaning."
  (merge stores/spec docs/spec journal/spec serve/spec))

(def usage
  "Usage text shown by `kimun`, `kimun help` and `--help`."
  (str/join "\n"
            ["kimun — content-addressed knowledge store (SLDB v2)"
             ""
             "usage: kimun <group> <command> [options]"
             "       kimun --version | --help"
             ""
             "groups:"
             "  stores init [--name NAME]   create .kimun/ in the working directory (or --store PATH)"
             "  stores check                open the store (replay) and report revisions, trees, objects"
             "  stores verify               recompute the hash of every reachable object"
             "  stores show                 descriptor and heads without replay"
             "  stores link NAME PATH       link another store (a .kimun or its project dir); path stored relative to this store"
             "  stores unlink NAME          remove a link"
             "  stores links                list links with status (ok | missing | not-a-store)"
             "  docs ingest FILE [--name N] parse a Markdown file and commit it as a new document tree"
             "  docs update FILE --name N   re-ingest FILE into the existing document N (an outside edit)"
             "  docs render NAME|TREE       canonical Markdown of a document (--format text prints it raw)"
             "  docs list                   every document: name, tree id, root node"
             "  journal show [--limit N] [--by ACTOR] [--tree NAME|ID]   the write history, newest first (a view of the log)"
             "  journal verify              check the revision chain, the log replay and every reachable object"
             "  serve [--port N] [--host H] [--token T] [--cors]   HTTP API over the commands (default 127.0.0.1:8787)"
             "                              --token (or $KIMUN_SERVE_TOKEN) requires `Authorization: Bearer TOKEN`"
             ""
             "options:"
             "  --store PATH    store directory (.kimun or its project dir); else $KIMUN_STORE, else walk-up"
             "  --format FMT    json (default) | edn | text"
             "  --actor NAME    actor recorded in transactions (default human/$USER)"
             "  --debug         print the stacktrace of an unexpected error to stderr"
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
            envelope (errors/wrap #(route parsed ctx argv))
            help? (= "help" (:command envelope))]
        {:envelope envelope
         :fmt (if help? "text" (get-in parsed [:opts :format]))
         :debug (boolean (get-in parsed [:opts :debug]))}))))

(defn -main
  "Prints the envelope of `run` to stdout and exits with its code; with
   `--debug` the stacktrace of a failure goes to stderr."
  [& argv]
  (let [{:keys [envelope fmt debug]} (run argv (System/getenv) (System/getProperty "user.dir"))]
    (println (out/emit envelope fmt))
    (when-let [t (and debug (:throwable (meta envelope)))]
      (binding [*out* *err*] (.printStackTrace ^Throwable t (java.io.PrintWriter. *out* true))))
    (flush)
    (System/exit (out/exit-code envelope))))
