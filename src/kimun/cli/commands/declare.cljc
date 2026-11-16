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
            [kimun.surface.declared-anchors :as declared-anchors]
            [kimun.surface.propositions :as props]))

(def spec
  "Options of the `declare` group (merged by `kimun.cli.main`): `--context` (of `add`) and
   `--exact` (of `about`); `--to` is the shared option of `edges` and `--path` that of
   `docs create`."
  {:context {:ref "NAME" :desc "declare add: create the file given by --to with (ns NAME) when it does not exist"}
   :exact {:desc "declare about: only the term itself, not the more specific ones (UI.view.dashboard under UI.view)" :coerce :boolean}})

(def project-dir
  "The folder, under the working directory, whose `*.clj` files are the declarations
   of the project (a convention: without it there are none, and nothing fails)."
  "kimun")

(defn- project-files
  "The `*.clj` files under the project folder of `root`, as paths relative to `root`
   (always with `/`), sorted by path; [] when the folder does not exist."
  [root]
  (let [dir (fs/path root project-dir)]
    (if (fs/directory? dir)
      (->> (fs/glob dir "**.clj")
           (filter fs/regular-file?)
           (map #(str/replace (str (fs/relativize root %)) "\\" "/"))
           sort vec)
      [])))

(defn- project-root
  "The root of the project the commands of the group work from: the nearest directory, going
   up from `cwd`, that holds the project folder; else the top of the git work tree `cwd` is in;
   else `cwd` itself. Returns `{:cwd :root}` as absolute real paths (cwd symlinks resolved)."
  [cwd]
  (let [cwd (fs/real-path (fs/absolutize cwd))
        with-folder (->> (iterate fs/parent cwd) (take-while some?)
                         (filter #(fs/directory? (fs/path % project-dir))) first)
        top (when-not with-folder (some-> (git/repo-root cwd) fs/real-path))]
    {:cwd cwd :root (or with-folder top cwd)}))

(defn- named
  "The path the user gave (`p`, relative to `cwd`, or absolute) as a name relative to `root`
   with `/`; an absolute path outside `root` stays absolute. `{:abs :rel :inside?}`."
  [{:keys [cwd root]} p]
  (let [abs (fs/normalize (if (fs/absolute? p) (fs/path p) (fs/path cwd p)))
        inside? (fs/starts-with? abs root)]
    {:abs abs :inside? inside?
     :rel (str/replace (str (if inside? (fs/relativize root abs) abs)) "\\" "/")}))

(defn- under-project?
  "True when `path` (absolute) is a `*.clj` file under the project folder of `root`."
  [root path]
  (and (str/ends-with? (str path) ".clj")
       (fs/starts-with? (fs/normalize path) (fs/path root project-dir))))

(defn- timeout-opts
  "`{:timeout-ms n}` for the evaluator from `$KIMUN_DECLARE_TIMEOUT_MS` in the environment of
   `ctx` (the limit for each file; unset: the evaluator's default). Not a positive integer is
   a usage error."
  [{:keys [env]}]
  (if-let [v (get env "KIMUN_DECLARE_TIMEOUT_MS")]
    (let [n (try (Long/parseLong (str/trim (str v))) (catch NumberFormatException _ nil))]
      (if (and n (pos? n))
        {:timeout-ms n}
        (args/usage! "declare" (str "KIMUN_DECLARE_TIMEOUT_MS must be a positive number of milliseconds, got " (pr-str v)) {:value v})))
    {}))

(defn- header
  "`{:context :requires}` of a declaration text, or nil when it does not read (the evaluator
   reports that error when it evaluates the set)."
  [file text]
  (try (declare/header-of text file) (catch clojure.lang.ExceptionInfo _ nil)))

(defn- dependencies
  "Of the project files not in `named` (`[{:file :text}]`), the ones that define, directly or
   through each other, a context the `named` ones require and no named one defines; `[{:file
   :text}]` in path order. Contexts nobody defines are left for the evaluator to report."
  [root named]
  (let [named-files (into #{} (map :file) named)
        candidates (delay (into [] (keep (fn [f] (let [text (slurp (str (fs/path root f)))]
                                                   (when-let [h (header f text)] (assoc h :file f :text text)))))
                                (remove named-files (project-files root))))
        headers (keep #(header (:file %) (:text %)) named)]
    (loop [defined (set (map :context headers))
           needed (set (mapcat :requires headers))
           taken []]
      (let [missing (remove defined needed)]
        (if-let [dep (and (seq missing) (first (filter #(and (not (some #{(:file %)} (map :file taken))) ((set missing) (:context %))) @candidates)))]
          (recur (conj defined (:context dep)) (into needed (:requires dep)) (conj taken dep))
          (mapv #(select-keys % [:file :text]) (sort-by :file taken)))))))

(defn- read-set
  "The declaration set as one project: `files` (as given, relative to the working directory)
   or, when none, the files of the project folder of the root. Returns `{:results [{:file
   :context :relations :propositions}] :required [file…]}` with the files named relative to
   the root; a missing file is `declare/missing`. When every FILE is under the project folder
   the contexts they require that no FILE defines are looked for in the rest of the project
   and loaded as dependencies: they take part in the evaluation, are listed in `:required`
   and are not in `:results`."
  [rt files ctx]
  (let [items (if (seq files)
                (mapv #(assoc (named rt %) :given %) files)
                (mapv (fn [f] {:abs (fs/path (:root rt) f) :rel f :given f :inside? true}) (project-files (:root rt))))
        texts (mapv (fn [{:keys [abs rel given]}]
                      (when-not (fs/regular-file? abs)
                        (err/raise :declare/missing (str "declaration file not found: " given) {:file given}))
                      {:file rel :text (slurp (str abs))})
                    items)
        deps (if (and (seq files) (every? #(and (:inside? %) (under-project? (:root rt) (:abs %))) items))
               (dependencies (:root rt) texts)
               [])
        results (declare/read-project host/host (into texts deps) (timeout-opts ctx))]
    {:results (vec (take (count texts) results)) :required (mapv :file deps)}))

(defn read-files
  "`declare read [FILE…]`: data `{:root :files [{:file :context :relations :propositions}]
   :summary {:files :relations :propositions}}`, plus `:required [path…]` when FILEs under the
   project folder needed contexts of other project files, loaded as dependencies and not listed
   in `:files`. A file that does not exist is `declare/missing`
   (1); one that does not evaluate is the `declare/*` error of the evaluator (3), naming file
   and line."
  [files _opts {:keys [cwd] :as ctx}]
  (let [rt (project-root cwd)
        {:keys [results required]} (read-set rt files ctx)]
    (out/ok "declare read"
            {:data (cond-> {:root (str (:root rt))
                            :files results
                            :summary {:files (count results)
                                      :relations (transduce (map (comp count :relations)) + results)
                                      :propositions (transduce (map (comp count :propositions)) + results)}}
                     (seq required) (assoc :required required))})))

(defn- project-reader
  "`(fn [relative-path] text-or-nil)` over the project directory `root`."
  [root]
  (fn [rel] (let [f (fs/path root rel)] (when (fs/regular-file? f) (slurp (str f))))))

(defn- comparison
  "`{:since sha :pointer sha-or-nil}` for `anchors`, or nil when there is nothing to compare
   with: `--since` (any commit git knows) or, without it, the pointer of the store found
   in `root` (`.kimun/seen.edn`; a damaged one is `git/bad-pointer`, never ignored)."
  [root since]
  (let [store-dir (str (fs/path root ".kimun"))
        has-pointer? (and (nil? since) (cli-store/store? store-dir) (fs/regular-file? (fs/path store-dir "seen.edn")))
        head (when (or since has-pointer?) (git/repo-head root))
        pointer (when has-pointer? (seen/read-pointer store-dir root))]
    (when (or since pointer)
      {:since (git/resolve-commit root (or since pointer)) :pointer pointer :head head})))

(defn anchors-of
  "`declare anchors [FILE…] [--since COMMIT]`: read-only and storeless. Evaluates the
   declaration files and, for every `(at …)` used as a term of a proposition, resolves it
   against the files of the project (its root) with
   `kimun.surface.declared-anchors/states`. With a commit (`--since`, or the valid pointer of
   a store in the root) git says which files changed and only those are opened,
   comparing the text git keeps at the commit with the text now; without one the anchors are
   only resolved. Data `{:root :files [{:file :context :propositions}] :anchors [row…] :summary
   {:files :anchors :opened :by-status} :since :pointer :head}` (`:since`/`:pointer`/`:head`
   nil without comparison). Errors: `declare/*` of the evaluator, `declare/bad-locator`,
   `git/not-a-repository`, `git/unknown-commit`, `git/bad-pointer`, `git/error`."
  [files opts {:keys [cwd] :as ctx}]
  (let [since (:since opts)
        _ (when-not (or (nil? since) (and (string? since) (seq since)))
            (args/usage! "declare" "anchors" "`declare anchors --since` needs a COMMIT" {}))
        rt (project-root cwd)
        root (:root rt)
        {:keys [results required]} (read-set rt files ctx)
        props (vec (mapcat :propositions results))
        cmp (comparison root since)
        changed (when cmp
                  (into #{} (mapcat (juxt :path :from)) (remove nil? (git/changed-since root (:since cmp) [".kimun"]))))
        changed (when changed (disj changed nil))
        rows (declared-anchors/states host/host props
                                      {:read-now (project-reader root)
                                       :read-then (when cmp #(git/show root (:since cmp) %))
                                       :changed changed})]
    (out/ok "declare anchors"
            {:data (cond-> {:root (str root)
                            :files (mapv #(select-keys % [:file :context :propositions]) results)
                            :anchors rows
                            :summary {:files (count results) :anchors (count rows)
                                      :opened (count (filter :opened rows))
                                      :by-status (frequencies (map :status rows))}
                            :since (:since cmp) :pointer (:pointer cmp) :head (:head cmp)}
                     (seq required) (assoc :required required))})))

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
   given, on a new last line) to the declaration file FILE (relative to the working
   directory; named in the answer relative to the root). FILE must exist; with
   `--context NAME` a missing FILE is created with `(ns NAME)`. Before writing, the set is
   evaluated in memory with the form added (the project when FILE is a `*.clj` under the
   project folder, else FILE alone): if it does not evaluate the `declare/*` error is the
   answer and nothing is written; if the form adds no proposition and no relation the answer
   is `:added false` with a `:reason` and nothing is written. The write is atomic and is the
   only one of the group. Data `{:root :file :added :propositions [new] :relations [new names]}`."
  [args opts {:keys [cwd] :as ctx}]
  (let [[form & more] args
        to (:to opts)
        context (:context opts)]
    (when (or (str/blank? form) (seq more))
      (args/usage! "declare" "add" "`declare add --to FILE 'FORM'` needs exactly one FORM" {}))
    ;; a form that does not even read is reported by the evaluation below, with the file and line
    (let [n (try (declare/count-forms form "FORM") (catch clojure.lang.ExceptionInfo _ 1))]
      (when-not (= 1 n)
        (args/usage! "declare" "add" (str "`declare add` takes exactly one form, the text has " n) {:forms n})))
    (when-not (and (string? to) (seq to))
      (args/usage! "declare" "add" "`declare add` needs --to FILE" {}))
    (let [rt (project-root cwd)
          root (:root rt)
          {:keys [abs rel]} (named rt to)
          exists? (fs/regular-file? abs)
          _ (when-not (or exists? (and (string? context) (seq context)))
              (args/usage! "declare" "add" (str "`" to "` does not exist: create it with --context NAME") {:file to}))
          current (if exists? (slurp (str abs)) (str "(ns " context ")\n"))
          _ (when (and exists? (string? context) (seq context))
              (let [actual (:context (declare/header-of current rel))]
                (when (not= context actual)
                  (args/usage! "declare" "add" (str "--context " context " does not match the `ns` of " rel " (" actual ")") {:file rel :context context :actual actual}))))
          appended (str current (if (or (str/blank? current) (str/ends-with? current "\n")) "" "\n") (str/trim form) "\n")
          others (when (under-project? root abs) (remove #(= rel %) (project-files root)))
          other-texts (mapv (fn [f] {:file f :text (slurp (str (fs/path root f)))}) others)
          after (first (declare/read-project host/host (into [{:file rel :text appended}] other-texts) (timeout-opts ctx)))
          before (when exists? (first (declare/read-project host/host (into [{:file rel :text current}] other-texts) (timeout-opts ctx))))
          news (new-keys (or before {}) after)
          added? (boolean (or (seq (:propositions news)) (seq (:relations news))))]
      (when added?
        (fs/create-dirs (fs/parent abs))
        (write-atomic! abs appended))
      (out/ok "declare add"
              {:data (cond-> {:root (str root) :file rel :added added? :propositions (:propositions news) :relations (:relations news)}
                       (not added?) (assoc :reason "the form adds no proposition and no relation: it was already declared"))}))))

;; --- cut 3b: asking (docs/v2/drafts/diseno-evaluar.md §9, §11) ----------------------------

(defn- propositions-of-set
  "`{:propositions [p…] :required [file…]}` of the declaration set (the FILEs given or the project's)."
  [rt files ctx]
  (let [{:keys [results required]} (read-set rt files ctx)]
    {:propositions (vec (mapcat :propositions results)) :required required}))

(defn- walk-files
  "The files under `root` relative to it, without `.git` and `.kimun`."
  [root]
  (->> (fs/glob root "**" {:hidden true})
       (remove #(or (fs/directory? %) (re-find #"(^|/)(\.git|\.kimun)(/|$)" (str (fs/relativize root %)))))
       (map #(str/replace (str (fs/relativize root %)) "\\" "/"))
       sort vec))

(defn- repo-files
  "`{:files [path…] :from :git|:walk}`: git's list (tracked and not ignored untracked,
   those that exist) when `root` is in a repository, the directory walk otherwise."
  [root]
  (if-let [l (git/list-files root [".kimun"])]
    {:files (vec (filter #(fs/regular-file? (fs/path root %)) l)) :from :git}
    {:files (walk-files root) :from :walk}))

(defn terms-of
  "`declare terms [FILE…]`: data `{:root :terms [{:term :count :ancestors :implicit}] :files
   [file…] :summary {:terms :implicit}}`; without FILE the project's declarations. See
   `kimun.surface.propositions/terms`."
  [files _opts {:keys [cwd] :as ctx}]
  (let [rt (project-root cwd)
        {:keys [propositions required]} (propositions-of-set rt files ctx)
        ts (props/terms propositions)]
    (out/ok "declare terms"
            {:data (cond-> {:root (str (:root rt)) :terms ts :files (vec files)
                            :summary {:terms (count ts) :implicit (count (filter :implicit ts))}}
                     (seq required) (assoc :required required))})))

(defn- relative-to-root
  "`p` (relative to the working directory or absolute) as a path relative to the root; a
   path outside the root is a usage error."
  [rt p]
  (let [{:keys [rel inside?]} (named rt p)]
    (when-not inside?
      (args/usage! "declare" "about" (str "--path is outside the project root " (:root rt) ": " p) {:path p}))
    rel))

(defn about-of
  "`declare about TERM [FILE…] [--exact]` or `declare about --path PATH [FILE…]`: read-only,
   storeless. Data of the term form `{:root :term :exact :levels 1 :results [row…] :files}`,
   of the path form `{:root :path :results [row…] :files}`; rows in
   `kimun.surface.propositions`. PATH is relative to the working directory and is answered
   relative to the root; the file is never opened; `--exact` with `--path` is a usage error."
  [argv opts {:keys [cwd] :as ctx}]
  (let [p (:path opts)
        _ (when (and (contains? opts :path) (not (and (string? p) (seq p))))
            (args/usage! "declare" "about" "`declare about --path` needs a PATH" {}))
        _ (when (and p (:exact opts))
            (args/usage! "declare" "about" "`--exact` has no effect with `--path`" {}))
        term (when-not p (args/require-arg! "declare" argv 0 "a TERM (or --path PATH)" "about"))
        files (vec (if p argv (rest argv)))
        rt (project-root cwd)
        {ps :propositions required :required} (propositions-of-set rt files ctx)
        data (if p
               (props/about-path ps (relative-to-root rt p))
               (props/about ps term {:exact (boolean (:exact opts))}))]
    (out/ok "declare about"
            {:data (cond-> (assoc data :root (str (:root rt)) :files files :summary {:results (count (:results data))})
                     (seq required) (assoc :required required))})))

(defn members-of
  "`declare members 'PATTERN'`: the project files a `(files PATTERN)` node reaches, expanded
   now and never stored. PATTERN is relative to the root. Data `{:root :pattern :members
   [path…] :files-from :git|:walk :summary {:members}}`: `:files-from` says where the list of
   project files came from. More than one argument is a usage error."
  [argv _opts {:keys [cwd]}]
  (let [pattern (args/require-arg! "declare" argv 0 "a PATTERN" "members")
        _ (when (> (count argv) 1)
            (args/usage! "declare" "members" "`declare members` takes one PATTERN and no FILE" {:extra (vec (rest argv))}))
        root (:root (project-root cwd))
        {:keys [files from]} (repo-files root)
        ms (props/members pattern files)]
    (out/ok "declare members"
            {:data {:root (str root) :pattern pattern :members ms :files-from from :summary {:members (count ms)}}})))

(def commands
  "Subcommand name → command fn `(fn [args opts ctx] envelope)`."
  {"read" read-files "anchors" anchors-of "add" add-form "terms" terms-of "about" about-of "members" members-of})

(defn dispatch
  "Runs subcommand `sub` of the `declare` group. `args` are the tokens after
   the subcommand; an unknown or missing subcommand is the failure `cli/usage`."
  [sub args opts ctx]
  (if-let [f (get commands sub)]
    (f (vec args) opts ctx)
    (args/usage! "declare" sub (str "unknown `declare` command: " (or sub "(none)") "; known: read, anchors, add, terms, about, members") {})))
