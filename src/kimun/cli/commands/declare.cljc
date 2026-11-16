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
            [kimun.cli.store :as cli-store]
            [kimun.host.default :as host]
            [kimun.host.git :as git]
            [kimun.host.seen :as seen]
            [kimun.kernel.err :as err]
            [kimun.surface.declare :as declare]
            [kimun.surface.declared-anchors :as declared-anchors]))

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

(defn- project-reader
  "`(fn [relative-path] text-or-nil)` over the project directory `cwd`."
  [cwd]
  (fn [rel] (let [f (fs/path cwd rel)] (when (fs/regular-file? f) (slurp (str f))))))

(defn- comparison
  "`{:since sha :pointer sha-or-nil}` for `anchors`, or nil when there is nothing to compare
   with: `--since` (any commit git knows) or, without it, the pointer of the store found
   in `cwd` (`.kimun/seen.edn`; a damaged one is `git/bad-pointer`, never ignored)."
  [cwd since]
  (let [store-dir (str (fs/path cwd ".kimun"))
        has-pointer? (and (nil? since) (cli-store/store? store-dir) (fs/regular-file? (fs/path store-dir "seen.edn")))
        head (when (or since has-pointer?) (git/repo-head cwd))
        pointer (when has-pointer? (seen/read-pointer store-dir cwd))]
    (when (or since pointer)
      {:since (git/resolve-commit cwd (or since pointer)) :pointer pointer :head head})))

(defn anchors-of
  "`declare anchors FILE… [--since COMMIT]`: read-only and storeless. Evaluates the
   declaration files and, for every `(at …)` used as a term of a proposition, resolves it
   against the files of the project (the working directory) with
   `kimun.surface.declared-anchors/states`. With a commit (`--since`, or the valid pointer of
   a store in the working directory) git says which files changed and only those are opened,
   comparing the text git keeps at the commit with the text now; without one the anchors are
   only resolved. Data `{:files [{:file :context :propositions}] :anchors [row…] :summary
   {:files :anchors :opened :by-status} :since :pointer :head}` (`:since`/`:pointer`/`:head`
   nil without comparison). Errors: `declare/*` of the evaluator, `declare/bad-locator`,
   `git/not-a-repository`, `git/unknown-commit`, `git/bad-pointer`, `git/error`."
  [files opts {:keys [cwd]}]
  (when (empty? files)
    (args/usage! "declare" "anchors" "`declare anchors` needs at least one FILE" {}))
  (let [since (:since opts)
        _ (when-not (or (nil? since) (and (string? since) (seq since)))
            (args/usage! "declare" "anchors" "`declare anchors --since` needs a COMMIT" {}))
        results (mapv #(read-file cwd %) files)
        props (vec (mapcat :propositions results))
        cmp (comparison cwd since)
        changed (when cmp
                  (into #{} (mapcat (juxt :path :from)) (remove nil? (git/changed-since cwd (:since cmp) [".kimun"]))))
        changed (when changed (disj changed nil))
        rows (declared-anchors/states host/host props
                                      {:read-now (project-reader cwd)
                                       :read-then (when cmp #(git/show cwd (:since cmp) %))
                                       :changed changed})]
    (out/ok "declare anchors"
            {:data {:files (mapv #(select-keys % [:file :context :propositions]) results)
                    :anchors rows
                    :summary {:files (count results) :anchors (count rows)
                              :opened (count (filter :opened rows))
                              :by-status (frequencies (map :status rows))}
                    :since (:since cmp) :pointer (:pointer cmp) :head (:head cmp)}})))

(def commands
  "Subcommand name → command fn `(fn [args opts ctx] envelope)`."
  {"read" read-files "anchors" anchors-of})

(defn dispatch
  "Runs subcommand `sub` of the `declare` group. `args` are the tokens after
   the subcommand; an unknown or missing subcommand is the failure `cli/usage`."
  [sub args opts ctx]
  (if-let [f (get commands sub)]
    (f (vec args) opts ctx)
    (args/usage! "declare" sub (str "unknown `declare` command: " (or sub "(none)") "; known: read, anchors") {})))
