(ns kimun.cli.commands.declare
  "The `kimun declare` group (docs/v2/drafts/diseno-evaluar.md, cut 1): `read`
   evaluates one or more declaration files and shows the propositions they
   produce. Read-only and storeless: it reads the files named (paths relative
   to the working directory) and writes nothing. The evaluation itself is the
   pure `kimun.surface.declare`; all I/O is here. Every command is
   `(fn [args opts ctx] envelope)`, `ctx` being `{:env env :cwd cwd}`."
  (:require [babashka.fs :as fs]
            [kimun.cli.args :as args]
            [kimun.cli.out :as out]
            [kimun.kernel.err :as err]
            [kimun.surface.declare :as declare]))

(def spec
  "Options of the `declare` group (merged by `kimun.cli.main`): none of its own."
  {})

(defn- read-file
  "The evaluated declaration file `file` (as given, resolved against `cwd`)."
  [cwd file]
  (let [path (if (fs/absolute? file) (fs/path file) (fs/path cwd file))]
    (when-not (fs/regular-file? path)
      (err/raise :declare/missing (str "declaration file not found: " file) {:file file}))
    (assoc (declare/read-declarations (slurp (str path)) file) :file file)))

(defn read-files
  "`declare read FILE…`: data `{:files [{:file :context :relations
   :propositions}] :summary {:files :relations :propositions}}`. A file that
   does not exist is `declare/missing` (1); one that does not evaluate is the
   `declare/*` error of the evaluator (3), naming file and line."
  [files _opts {:keys [cwd]}]
  (when (empty? files)
    (args/usage! "declare" "read" "`declare read` needs at least one FILE" {}))
  (let [results (mapv #(read-file cwd %) files)]
    (out/ok "declare read"
            {:data {:files results
                    :summary {:files (count results)
                              :relations (transduce (map (comp count :relations)) + results)
                              :propositions (transduce (map (comp count :propositions)) + results)}}})))

(def commands
  "Subcommand name → command fn `(fn [args opts ctx] envelope)`."
  {"read" read-files})

(defn dispatch
  "Runs subcommand `sub` of the `declare` group. `args` are the tokens after
   the subcommand; an unknown or missing subcommand is the failure `cli/usage`."
  [sub args opts ctx]
  (if-let [f (get commands sub)]
    (f (vec args) opts ctx)
    (args/usage! "declare" sub (str "unknown `declare` command: " (or sub "(none)") "; known: read") {})))
