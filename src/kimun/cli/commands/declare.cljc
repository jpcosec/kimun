(ns kimun.cli.commands.declare
  "The `kimun declare` group (docs/v2/drafts/diseno-evaluar.md, cut 1): `read`
   evaluates one or more declaration files and shows the propositions they
   produce. Storeless: it reads the files named (paths relative to the working
   directory) or, with none, the `*.clj` of the project folder, as one set of
   contexts; only `add` writes (one declaration file). The evaluation itself is the
   pure `kimun.surface.declare`; all I/O is here. Every command is
   `(fn [args opts ctx] envelope)`, `ctx` being `{:env env :cwd cwd}`."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
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
  "Options of the `declare` group (merged by `kimun.cli.main`): `--context` (of `add`);
   `--to` is the shared option of `edges`."
  {:context {:ref "NAME" :desc "declare add: create the file given by --to with (ns NAME) when it does not exist"}})

(def project-dir
  "The folder, under the working directory, whose `*.clj` files are the declarations
   of the project (a convention: without it there are none, and nothing fails)."
  "kimun")

(defn- project-files
  "The `*.clj` files under the project folder of `cwd`, as paths relative to `cwd`
   (always with `/`), sorted by path; [] when the folder does not exist."
  [cwd]
  (let [root (fs/path cwd project-dir)]
    (if (fs/directory? root)
      (->> (fs/glob root "**.clj")
           (filter fs/regular-file?)
           (map #(str/replace (str (fs/relativize cwd %)) "\\" "/"))
           sort vec)
      [])))

(defn- read-set
  "The declaration set as one project: `files` (as given, relative to `cwd`) or,
   when none, the files of the project folder. Returns `[{:file :context :relations
   :propositions}]` in that order; a missing file is `declare/missing`."
  [cwd files]
  (let [names (if (seq files) (vec files) (project-files cwd))
        texts (mapv (fn [file]
                      (let [path (if (fs/absolute? file) (fs/path file) (fs/path cwd file))]
                        (when-not (fs/regular-file? path)
                          (err/raise :declare/missing (str "declaration file not found: " file) {:file file}))
                        {:file file :text (slurp (str path))}))
                    names)]
    (declare/read-project texts)))

(defn read-files
  "`declare read FILE…`: data `{:files [{:file :context :relations
   :propositions}] :summary {:files :relations :propositions}}`. A file that
   does not exist is `declare/missing` (1); one that does not evaluate is the
   `declare/*` error of the evaluator (3), naming file and line."
  [files _opts {:keys [cwd]}]
  (let [results (read-set cwd files)]
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
  (let [since (:since opts)
        _ (when-not (or (nil? since) (and (string? since) (seq since)))
            (args/usage! "declare" "anchors" "`declare anchors --since` needs a COMMIT" {}))
        results (read-set cwd files)
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

(defn- declaration-path
  "The file `file` (as given, relative to `cwd`) as `{:path :rel}`."
  [cwd file]
  {:path (if (fs/absolute? file) (fs/path file) (fs/path cwd file)) :rel file})

(defn- under-project?
  "True when `rel` (relative to `cwd`) is a file under the project folder."
  [cwd path]
  (let [root (fs/absolutize (fs/path cwd project-dir))]
    (and (str/ends-with? (str path) ".clj") (fs/starts-with? (fs/normalize (fs/absolutize path)) (fs/normalize root)))))

(defn- write-atomic!
  "Writes `text` to `path` through a temporary file in the same folder and a rename."
  [path text]
  (let [tmp (fs/path (fs/parent path) (str "." (fs/file-name path) ".tmp-" (System/nanoTime)))]
    (try (spit (str tmp) text)
         (fs/move tmp path {:replace-existing true :atomic-move true})
         (finally (fs/delete-if-exists tmp)))))

(defn- new-keys
  "The propositions of `after` whose id is not in `before`, and the relations of `after`
   not in `before`."
  [before after]
  (let [ids (into #{} (map :id) (:propositions before))]
    {:propositions (vec (remove #(contains? ids (:id %)) (:propositions after)))
     :relations (vec (sort (remove #(contains? (:relations before) %) (keys (:relations after)))))}))

(defn add-form
  "`declare add --to FILE 'FORM' [--context NAME]`: appends the text of FORM (one form, as
   given, on a new last line) to the declaration file FILE. FILE must exist; with
   `--context NAME` a missing FILE is created with `(ns NAME)`. Before writing, the set is
   evaluated in memory with the form added (the project when FILE is a `*.clj` under the
   project folder, else FILE alone): if it does not evaluate the `declare/*` error is the
   answer and nothing is written; if the form adds no proposition and no relation the answer
   is `:added false` with a `:reason` and nothing is written. The write is atomic and is the
   only one of the group. Data `{:file :added :propositions [new] :relations [new names]}`."
  [args opts {:keys [cwd]}]
  (let [[form & more] args
        to (:to opts)
        context (:context opts)]
    (when (or (str/blank? form) (seq more))
      (args/usage! "declare" "add" "`declare add --to FILE 'FORM'` needs exactly one FORM" {}))
    (when-not (and (string? to) (seq to))
      (args/usage! "declare" "add" "`declare add` needs --to FILE" {}))
    (let [{:keys [path rel]} (declaration-path cwd to)
          exists? (fs/regular-file? path)
          _ (when-not (or exists? (and (string? context) (seq context)))
              (args/usage! "declare" "add" (str "`" to "` does not exist: create it with --context NAME") {:file to}))
          current (if exists? (slurp (str path)) (str "(ns " context ")\n"))
          appended (str current (if (or (str/blank? current) (str/ends-with? current "\n")) "" "\n") (str/trim form) "\n")
          in-project? (under-project? cwd path)
          others (when in-project? (remove #(= rel %) (project-files cwd)))
          texts (into [{:file rel :text appended}]
                      (map (fn [f] {:file f :text (slurp (str (fs/path cwd f)))}) others))
          after (first (declare/read-project texts))
          before (when exists?
                   (first (declare/read-project (into [{:file rel :text current}]
                                                      (map (fn [f] {:file f :text (slurp (str (fs/path cwd f)))}) others)))))
          news (new-keys (or before {}) after)
          added? (boolean (or (seq (:propositions news)) (seq (:relations news))))]
      (when added?
        (fs/create-dirs (fs/parent path))
        (write-atomic! path appended))
      (out/ok "declare add"
              {:data (cond-> {:file rel :added added? :propositions (:propositions news) :relations (:relations news)}
                       (not added?) (assoc :reason "the form adds no proposition and no relation: it was already declared"))}))))

(def commands
  "Subcommand name → command fn `(fn [args opts ctx] envelope)`."
  {"read" read-files "anchors" anchors-of "add" add-form})

(defn dispatch
  "Runs subcommand `sub` of the `declare` group. `args` are the tokens after
   the subcommand; an unknown or missing subcommand is the failure `cli/usage`."
  [sub args opts ctx]
  (if-let [f (get commands sub)]
    (f (vec args) opts ctx)
    (args/usage! "declare" sub (str "unknown `declare` command: " (or sub "(none)") "; known: read, anchors, add") {})))
