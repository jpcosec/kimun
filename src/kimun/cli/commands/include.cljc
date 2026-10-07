(ns kimun.cli.commands.include
  "The `kimun include` group (docs/v2/13): a repo's kimun.include.edn declares
   what of the kimun world it versions in git, and this group makes the repo and
   git agree with it. `check` diffs the declaration against git and the store,
   `gitignore` maintains the managed block of `.gitignore`, `hook` installs the
   pre-commit check, `build` builds the store from the declaration and `status`
   summarises both. Every command is `(fn [args opts ctx] envelope)`; all I/O
   (running git, reading the tree, opening the store) happens here, over the
   pure core kimun.surface.include."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.include :as inc]
            [sldb.kernel.err :as err]))

(def spec
  "Options of the `include` group (babashka.cli spec, merged by `kimun.cli.main`)."
  {:staged {:desc "Check the staged set instead of the whole index (pre-commit)" :coerce :boolean}
   :write {:desc "Write the managed block into .gitignore (default: print it)" :coerce :boolean}
   :install {:desc "Install the pre-commit hook" :coerce :boolean}
   :uninstall {:desc "Remove the pre-commit hook" :coerce :boolean}
   :rebuild {:desc "Move an existing store aside and build it again" :coerce :boolean}})

(def ^:private managed-start "# >>> kimun include (managed) >>>")
(def ^:private managed-end "# <<< kimun include <<<")
(def ^:private hook-marker "# kimun include hook (managed)")
(def ^:private hook-prev "pre-commit.kimun-prev")
(def ^:private include-file "kimun.include.edn")

(defn- git
  "Runs `git ARGS…` in `root`; returns `{:exit :out :err}` (`:out`/`:err` strings)."
  [root args]
  (apply process/shell {:out :string :err :string :continue true :dir (str root)} "git" args))

(defn- git-out
  "The stdout of `git ARGS…` in `root`; raises `:include/invalid` (exit 3) when
   git fails (for instance outside a repository)."
  [root args]
  (let [r (git root args)]
    (when-not (zero? (:exit r))
      (err/raise :include/invalid (str "git " (str/join " " args) " failed in " root ": " (str/trim (str (:err r)))) {}))
    (:out r)))

(defn- repo-root
  "The git top level for `ctx`; raises `:include/invalid` when there is none."
  [{:keys [cwd]}]
  (let [r (git cwd ["rev-parse" "--show-toplevel"])]
    (when-not (zero? (:exit r))
      (err/raise :include/invalid (str "not inside a git repository: " cwd) {:cwd (str cwd)}))
    (str/trim (str (:out r)))))

(defn- load-config
  "The parsed kimun.include.edn at `root`; raises `:include/missing` (exit 1)
   when the file is absent."
  [root]
  (let [f (fs/path root include-file)]
    (when-not (fs/regular-file? f)
      (err/raise :include/missing (str "no " include-file " at " root
                                       "; a repo declares what it versions with one") {:path (str f)}))
    (inc/parse (slurp (str f)))))

(defn- split-z "Splits NUL-separated `s` into a vector of non-empty paths." [s]
  (if (str/blank? (str s)) [] (vec (remove str/blank? (str/split (str s) #"\u0000")))))

(defn- git-tracked "The paths git tracks, relative to `root`." [root]
  (split-z (git-out root ["ls-files" "-z"])))

(defn- git-staged "The staged added/copied/modified/renamed paths, relative to `root`." [root]
  (split-z (git-out root ["diff" "--cached" "--name-only" "--diff-filter=ACMR" "-z"])))

(defn- glob-dir
  "The directory part of `glob` before its first wildcard, or `.`."
  [glob]
  (let [idxs (keep (fn [c] (str/index-of glob c)) ["*" "?"])
        idx (when (seq idxs) (apply min idxs))
        cut (if (number? idx) (subs glob 0 idx) glob)
        slash (str/last-index-of cut "/")]
    (if slash (subs cut 0 slash) "")))

(defn- workspace-files
  "The working-tree paths of `root` that match a managed glob of `cfg` (the
   candidate set the plan reads)."
  [root cfg]
  (let [globs (inc/managed-globs cfg)
        managed-rx (map inc/compile-glob globs)
        dirs (distinct (map #(str (fs/path root (glob-dir %))) globs))
        all (for [d dirs :when (fs/exists? d)] (fs/glob d "**"))]
    (->> all
         (mapcat identity)
         (filter fs/regular-file?)
         (map #(str (fs/relativize root (fs/absolutize %))))
         (map inc/normalize-path)
         (filter (fn [p] (some #(re-matches % p) managed-rx)))
         distinct
         (sort-by identity)
         vec)))

(defn- model-bases
  "`{model-path #{base-name}}` over `files`, read from each descriptor `cfg`
   declares. Raises `:include/invalid` on an unreadable descriptor."
  [root cfg files]
  (let [model-rx (map inc/compile-glob (:models cfg))]
    (into {}
          (keep (fn [p]
                  (when (some #(re-matches % p) model-rx)
                    [p (try (set (:base (edn/read-string (slurp (str (fs/path root p))))))
                            (catch Exception e
                              (err/raise :include/invalid (str "cannot read model descriptor " p ": " (ex-message e)) {:path p})))])))
          files)))

(defn- config-plan
  "`{:root :cfg :plan :files}` for the repo of `ctx`: the parsed declaration and
   the plan over the working tree's managed files."
  [{:keys [cwd]}]
  (let [root (repo-root {:cwd cwd})
        cfg (load-config root)
        files (workspace-files root cfg)]
    {:root root :cfg cfg :files files
     :plan (inc/plan cfg files {:model-bases (model-bases root cfg files)})}))

(defn- store-documents
  "The paths of the documents tracked in the store of `ctx`, or nil when there
   is no store yet."
  [opts {:keys [env cwd]}]
  (let [dir (cli-store/resolve-store opts env cwd)]
    (when (and dir (cli-store/store? dir))
      (let [{:keys [store]} (cli-store/open! dir)]
        (map :path (tdocs/documents store))))))

(defn- findings-summary [findings]
  (into (sorted-map) (map (fn [[k v]] [k (count v)])) findings))

(defn- no-args! [args sub]
  (when (seq args)
    (err/raise :cli/usage (str "`include " sub "` takes no positional arguments") {:group "include" :subcommand sub})))

(defn check
  "`include check [--staged]`: diffs the declaration against git and the store.
   Without `--staged` every finding kind is checked against the tracked set, the
   working tree and the store; with `--staged` (the pre-commit hook) only the
   staged set is inspected, rejecting staged store files under `:git :ignore`
   and staged files under a managed glob that the declaration does not claim.
   A failure is `:include/drift` (exit 3) carrying the findings; otherwise an
   :ok envelope."
  [args opts ctx]
  (no-args! args "check")
  (let [{:keys [root cfg plan files]} (config-plan ctx)
        staged? (:staged opts)
        tracked (if staged? (git-staged root) (git-tracked root))
        kinds (if staged? [:tracked-undeclared :store-tracked] inc/finding-kinds)
        inputs {:tracked tracked
                :files (if staged? tracked files)
                :store-documents (store-documents opts ctx)}
        {:keys [ok? findings]} (inc/diff cfg plan inputs kinds)]
    (if ok?
      (out/ok "include check" {:data {:ok true :staged? (boolean staged?) :findings (findings-summary findings)}})
      (out/failure :include/drift
                   (str "the declaration and git/store disagree: "
                        (str/join ", " (map (fn [[k v]] (str (count v) " " (name k))) (remove (comp empty? val) findings))))
                   {:staged? (boolean staged?) :findings (findings-summary findings) :paths findings}
                   3))))

(defn- managed-block
  "The managed lines of `.gitignore` for `cfg`: the store path when the store
   is not versioned, and a `!` re-include per declared glob so no declared file
   is ignored by a broader rule."
  [cfg]
  (concat
   [managed-start]
   (when (= :ignore (get-in cfg [:store :git]))
     [(str (get-in cfg [:store :path]) "/")])
   (map #(str "!" %) (inc/managed-globs cfg))
   [managed-end]))

(defn- merge-block
  "`content` with the managed block replaced between the markers (or appended
   when they are absent); everything outside the block is untouched. Idempotent."
  [content block]
  (let [lines (vec (str/split-lines (str content)))
        start (first (keep-indexed #(when (= %2 managed-start) %1) lines))
        end (first (keep-indexed #(when (= %2 managed-end) %1) lines))
        body (vec block)]
    (if (and start end (<= start end))
      (str (str/join "\n" (concat (subvec lines 0 start) body (subvec lines (inc end)))) "\n")
      (let [prefix (str/replace (str content) #"\s*$" "")]
        (str (if (str/blank? prefix) "" (str prefix "\n"))
             (str/join "\n" body) "\n")))))

(defn gitignore
  "`include gitignore [--write]`: the managed block for `.gitignore` as data
   (`--format text` prints just the block), or with `--write` writes it into
   `.gitignore` between its markers, preserving every foreign line. Idempotent."
  [args opts ctx]
  (no-args! args "gitignore")
  (let [{:keys [root cfg]} (config-plan ctx)
        block (managed-block cfg)
        f (fs/path root ".gitignore")
        existing (if (fs/regular-file? f) (slurp (str f)) "")]
    (when (:write opts) (spit (str f) (merge-block existing block)))
    (out/ok "include gitignore" {:data {:path ".gitignore" :written? (boolean (:write opts))
                                        :block (str/join "\n" block) :lines (vec block)}})))

(defn- hook-path
  "The pre-commit hook path git uses for `root`."
  [root]
  (let [p (str/trim (git-out root ["rev-parse" "--git-path" "hooks/pre-commit"]))]
    (str (if (fs/absolute? p) p (fs/path root p)))))

(defn- prev-path [hp] (str hp "." "kimun-prev"))

(defn- hook-script []
  (str "#!/bin/sh\n"
       hook-marker "\n"
       "# Managed by `kimun include hook`: rejects staged changes that drift from " include-file ".\n"
       "set -e\n"
       "root=\"$(git rev-parse --show-toplevel)\"\n"
       "\"$root/bin/kimun\" include check --staged\n"
       "prev=\"$(git rev-parse --git-path hooks/" hook-prev ")\"\n"
       "if [ -x \"$prev\" ]; then exec \"$prev\" \"$@\"; fi\n"))

(defn- executable? [p] (and (fs/regular-file? p) (fs/executable? p)))

(defn- install-hook! [root]
  (let [hp (hook-path root)
        content (when (fs/regular-file? hp) (slurp hp))
        prev (prev-path hp)]
    (when (and content (not (str/includes? content hook-marker)))
      (when (fs/exists? prev)
        (err/raise :include/invalid (str "cannot chain the existing hook: " prev " already exists") {:path prev}))
      (when-not (executable? hp)
        (err/raise :include/invalid (str "cannot chain " hp ": it is not an executable regular file") {:path hp}))
      (fs/copy hp prev)
      (fs/set-posix-file-permissions prev "rwxr-xr-x"))
    (some-> (fs/parent hp) fs/create-dirs)
    (spit hp (hook-script))
    (fs/set-posix-file-permissions hp "rwxr-xr-x")
    (out/ok "include hook" {:data {:path hp :installed true :chained (fs/exists? prev)}})))

(defn- uninstall-hook! [root]
  (let [hp (hook-path root)
        prev (prev-path hp)
        ours? (and (fs/regular-file? hp) (str/includes? (slurp hp) hook-marker))]
    (cond
      (not ours?) (out/ok "include hook" {:data {:path hp :installed false :ours? false
                                                :message "the hook is not managed by kimun; left untouched"}})
      (fs/exists? prev) (do (fs/move prev hp {:replace-existing true})
                            (fs/set-posix-file-permissions hp "rwxr-xr-x")
                            (out/ok "include hook" {:data {:path hp :installed false :restored true}}))
      :else (do (fs/delete hp)
                (out/ok "include hook" {:data {:path hp :installed false :restored false}})))))

(defn hook
  "`include hook --install|--uninstall`: installs a pre-commit hook that runs
   `bin/kimun include check --staged`, chaining a foreign hook (moved to
   `pre-commit.kimun-prev`) instead of overwriting it, or removes our hook and
   restores the chained one."
  [args opts ctx]
  (no-args! args "hook")
  (let [root (repo-root ctx)]
    (cond
      (:install opts) (install-hook! root)
      (:uninstall opts) (uninstall-hook! root)
      :else (err/raise :cli/usage "`include hook` needs --install or --uninstall" {:group "include" :subcommand "hook"}))))

(defn- run-cli
  "Runs another `kimun` command in-process (cwd `root`) through
   `kimun.cli.main/run`, without printing or exiting."
  [root env argv]
  ((requiring-resolve 'kimun.cli.main/run) argv env root))

(defn build
  "`include build [--rebuild]`: builds the store from the plan in one process —
   `stores init`, every model descriptor in base-first order, then every
   declared document (`docs track`, with `--force` where declared). Refuses to
   touch an existing store unless `--rebuild`, which moves it to
   `<store>.bak-<timestamp>`. A failure is `:include/drift` (exit 3)."
  [args opts {:keys [env cwd] :as ctx}]
  (no-args! args "build")
  (let [{:keys [root cfg plan]} (config-plan ctx)
        store-dir (str (fs/path root (get-in cfg [:store :path])))]
    (when (fs/exists? store-dir)
      (if (:rebuild opts)
        (let [stamp (-> (java.time.LocalDateTime/now)
                        (.format (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd-HHmmss")))
              bak (str store-dir ".bak-" stamp)]
          (fs/move store-dir bak)
          (binding [*out* *err*] (println (str "moved existing store to " bak))))
        (err/raise :include/exists
                   (str store-dir " already exists; pass --rebuild to move it aside and build it again")
                   {:path store-dir})))
    (let [init (:envelope (run-cli root env ["stores" "init" "--store" store-dir]))
          _ (when-not (:ok init)
              (err/raise :include/drift (str "stores init failed: " (get-in init [:error :type]))
                         {:phase "stores init" :error (get-in init [:error :type]) :message (get-in init [:error :message])}))
          models (reduce (fn [acc path]
                           (let [e (:envelope (run-cli root env ["models" "define" "-f" path "--store" store-dir]))]
                             (if (:ok e)
                               (update acc :ok conj path)
                               (update acc :failed conj {:path path :error (get-in e [:error :type])
                                                         :message (get-in e [:error :message])}))))
                         {:ok [] :failed []} (:models plan))
          docs (reduce (fn [acc {:keys [path model name force]}]
                         (let [argv (cond-> ["docs" "track" model path "--name" name]
                                      force (conj "--force"))
                               e (:envelope (run-cli root env (conj argv "--store" store-dir)))]
                           (if (:ok e)
                             (update acc :ok conj path)
                             (update acc :failed conj {:path path :name name :model model
                                                       :error (get-in e [:error :type])
                                                       :message (get-in e [:error :message])}))))
                       {:ok [] :failed []} (:documents plan))
          failed (concat (:failed models) (:failed docs))
          data {:store store-dir
                :models (count (:ok models)) :models-failed (count (:failed models))
                :documents (count (:ok docs)) :documents-failed (count (:failed docs))
                :failed (vec failed)}]
      (if (seq failed)
        (out/failure :include/drift (str (count failed) " declaration(s) failed to build") data 3)
        (out/ok "include build" {:store {:path store-dir} :data data})))))

(defn status
  "`include status`: counts of the declaration and a summary of the findings —
   models, documents (total and by model), forced, tracked, and how many paths
   each finding kind reports."
  [args opts ctx]
  (no-args! args "status")
  (let [{:keys [root cfg plan files]} (config-plan ctx)
        tracked (set (git-tracked root))
        docs (:documents plan)
        inputs {:tracked tracked :files files :store-documents (store-documents opts ctx)}
        {:keys [findings]} (inc/diff cfg plan inputs)]
    (out/ok "include status"
            {:data {:models (count (:models plan))
                    :documents (count docs)
                    :by-model (into (sorted-map) (map (fn [[m ds]] [m (count ds)])) (group-by :model docs))
                    :forced (count (filter :force docs))
                    :tracked (count (filter #(contains? tracked (inc/normalize-path (:path %))) docs))
                    :findings (findings-summary findings)}})))

(def commands
  "Subcommand name → command fn `(fn [args opts ctx] envelope)`."
  {"check" check "gitignore" gitignore "hook" hook "build" build "status" status})

(defn dispatch
  "Runs subcommand `sub` of the `include` group; an unknown or missing
   subcommand is the failure `cli/usage` listing the known ones."
  [sub args opts ctx]
  (if-let [f (get commands sub)]
    (f (vec args) opts ctx)
    (out/failure :cli/usage
                 (str (if sub (str "unknown subcommand `include " sub "`") "missing subcommand for `include`")
                      "; expected one of: " (str/join ", " (sort (keys commands))))
                 {:group "include" :subcommand sub :known (vec (sort (keys commands)))}
                 6)))
