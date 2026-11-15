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
            [kimun.cli.compose :as compose]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.host.default :as host]
            [kimun.kernel.plan :as plan]
            [kimun.kernel.ports :as ports]
            [kimun.kernel.revision :as revision]
            [kimun.kernel.store :as store]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.include :as inc]
            [kimun.surface.models.extract :as mextract]
            [kimun.surface.models.noise :as noise]
            [kimun.surface.models.render :as mrender]
            [kimun.surface.models.store :as mstore]
            [kimun.kernel.err :as err]))

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
   is not versioned, the store's derived cache (never versioned, docs/v2/14
   §19) and a `!` re-include per declared glob so no declared file is ignored
   by a broader rule."
  [cfg]
  (let [store-path (get-in cfg [:store :path])]
    (concat
     [managed-start]
     (when (= :ignore (get-in cfg [:store :git]))
       [(str store-path "/")])
     [(str store-path "/cache/")]
     (map #(str "!" %) (inc/managed-globs cfg))
     [managed-end])))

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

(defn- descriptor-at
  "The descriptor EDN at `path` (relative to `root`); `:include/invalid` when
   it cannot be read."
  [root path]
  (let [f (fs/path root path)]
    (try (edn/read-string (slurp (str f)))
         (catch Exception e
           (err/raise :include/invalid (str "cannot read model descriptor " path ": " (ex-message e)) {:path path})))))

(defn- relative-to [root p] (str (fs/relativize (fs/absolutize root) (fs/absolutize p))))

(defn- now-ts
  "The current UTC instant in the plan's shape, `yyyy-MM-ddTHH:mm:ss.SSSZ`."
  []
  #?(:clj (.format (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                   (java.time.ZonedDateTime/now java.time.ZoneOffset/UTC))
     :cljs (.toISOString (js/Date.))))

(defn- identity-registry
  "Identities a rebuild can carry, read from the store at `store-dir` BEFORE it
   is moved: `{:available bool :by-path {qualified-path {:doc id :tree id}}
   :ambiguous [qualified-path] :without-file n}`. Every document with a file is
   in it, typed or of a generic class: the ones the declaration does not build
   again must show up as not carried, not vanish. A path the old store holds
   twice (a legacy duplicate) is ambiguous and is not carried; a document
   without a file has no path to be found by and is only counted; anything that
   prevents reading the store is `:available false` (the rebuild continues with
   fresh ids and says so)."
  [store-dir]
  (if-not (and store-dir (fs/exists? store-dir))
    {:available true :by-path {} :ambiguous [] :without-file 0
     :models {:tree nil :by-name {}}}
    (try
      (let [store (:store (cli-store/open! store-dir))
            docs (concat (tdocs/documents store) (tdocs/generic-documents store))
            by (group-by :qualified (filter :qualified docs))]
        {:available true
         :models {:tree (first (mstore/models-tree store))
                  :by-name (into {} (map (fn [m] [(:name m) {:version (:version m) :descriptor (:descriptor m)}])
                                         (mstore/models store)))}
         :by-path (into {} (keep (fn [[q ds]]
                                   (when (= 1 (count ds))
                                     (let [d (first ds)]
                                       [q (cond-> (select-keys d [:doc :tree])
                                            (seq (:units d)) (assoc :units (:units d)))])))
                                 by))
         :ambiguous (vec (sort (keep (fn [[q ds]] (when (< 1 (count ds)) q)) by)))
         :without-file (count (remove :qualified docs))})
      (catch Exception _ {:available false :by-path {} :ambiguous [] :without-file 0
                          :models {:tree nil :by-name {}}}))))

(defn- carried-ids
  "`{:doc id :tree id}` for the declared document at `path` (already relative to
   the project) when the registry carries it, else nil."
  [registry path]
  (get-in registry [:by-path path]))

(defn- build-plan!
  "The whole declaration as ONE revision (docs/v2/14 §10): every model
   descriptor applied in memory first (so the documents can bind to them), then
   every declared document, all ops composed into one TransactionPlan committed
   once. A document that fails its checks is collected in `:failed`; nothing is
   committed when there is any failure. Returns
   `{:revision-id id|nil :models n :models-failed n :documents n
     :documents-failed n :failed [..]}`."
  ([root store-dir plan opts] (build-plan! root store-dir plan opts nil))
  ([root store-dir {:keys [models documents]} opts identities]
  (let [{:keys [backend store]} (cli-store/open! store-dir)
        h (:host store)
        actor (:actor opts)
        ts (now-ts)
        ctx (compose/context store store-dir)
        registry (or identities {:available true :by-path {} :ambiguous [] :without-file 0
                                 :models {:tree nil :by-name {}}})
        ;; models are CLASSES: a rebuild keeps the version of a model whose
        ;; contract did not move (so its content-addressed id does not move),
        ;; advances it when the contract moved, and never versions it back.
        model-carry (fn [desc]
                      (let [name (:model/name desc)
                            prior (get-in registry [:models :by-name name])]
                        (cond
                          (nil? prior) {:version 1 :kind :new}
                          (= (dissoc (:descriptor prior) :model/version)
                             (dissoc desc :model/version))
                          {:version (:version prior) :kind :kept}
                          :else {:version (inc (or (:version prior) 1)) :kind :advanced})))
        mres (reduce (fn [{:keys [store plans names failed models kept advanced new tree-carried]
                           :as acc} path]
                       (try
                         (let [desc (descriptor-at root path)
                               {:keys [version kind]} (model-carry desc)
                               old-tree (get-in registry [:models :tree])
                               r (mstore/define-plan h store desc
                                                     {:actor actor :timestamp ts
                                                      ;; the first define creates the tree: it keeps the
                                                      ;; old id when the old store had one
                                                      :version version
                                                      :tree-id (or old-tree (ports/ulid (:ids h)))})]
                           (if (:unchanged r)
                             (-> acc (update :names conj (:name r))
                                 (update :models conj [(:name r) [(:version r) (:id r)]])
                                 (update :kept inc)
                                 (update :tree-carried #(or % (some? old-tree))))
                             (-> acc (assoc :store (:store (revision/apply-plan store (:plan r))))
                                 (update :plans conj (:plan r))
                                 (update :names conj (:name r))
                                 (update :models conj [(:name r) [(:version r) (:id r)]])
                                 (update kind inc)
                                 (update :tree-carried #(or % (some? old-tree))))))
                         (catch Exception e
                           (update acc :failed conj {:path path :error (or (:type (ex-data e)) "include/invalid")
                                                     :message (ex-message e)}))))
                     {:store store :plans [] :names [] :failed []
                      :models [] :kept 0 :advanced 0 :new 0 :tree-carried false}
                     models)
        mstore* (:store mres)
        dres (reduce (fn [{:keys [plans names failed carried minted paths] :as acc}
                          {:keys [path model name force inline]}]
                       (let [rel (relative-to root (str (fs/path root path)))
                             carried-ids (carried-ids registry rel)
                             text (try (slurp (str (fs/path root path))) (catch Exception _ nil))]
                         (cond
                           (nil? text)
                           (-> acc (update :failed conj {:path path :name name :model model :error "docs/no-file"
                                                         :message (str "no such file: " path)}))
                           (contains? names name)
                           (update acc :failed conj {:path path :name name :model model :error "docs/exists"
                                                     :message (str "a document named `" name "` is declared twice")})
                           :else
                           (try
                             (let [m (or (mstore/find-model mstore* model)
                                         (err/raise :models/missing (str "no model `" model "` in the store") {:model model}))]
                               (when-not force
                                 (let [n (noise/structural-noise (:descriptor m) text)]
                                   (when (noise/noisy? n)
                                     (err/raise :docs/structural-noise
                                                (str "structural noise in `" name "`: " model " does not declare " (noise/describe n))
                                                {:document name :model model})))
                                 (let [again (mrender/render (:descriptor m) (mextract/extract (:descriptor m) text) ctx)]
                                   (when (not= (str/trim again) (str/trim text))
                                     (err/raise :docs/not-idempotent
                                                (str "`" path "` does not round-trip under " model)
                                                {:document name :model model}))))
                               (let [{:keys [plan]} (tdocs/track-plan h mstore* model name text
                                                                      {:mint (fn [] (ports/ulid (:ids h)))
                                                                       :units (:units carried-ids)
                                                                       :doc (or (:doc carried-ids) (ports/ulid (:ids h)))
                                                                       :tree-id (or (:tree carried-ids) (ports/ulid (:ids h)))
                                                                       :path rel
                                                                       :actor actor :timestamp ts :operation "include build"
                                                                       :inline inline})]
                                 (-> acc (update :plans conj plan) (update :names conj name)
                                     (update :paths conj rel)
                                     (update (if carried-ids :carried :minted) inc)
                                     (update :units-carried + (count (:units carried-ids))))))
                             (catch Exception e
                               (-> acc (update :failed conj {:path path :name name :model model
                                                             :error (or (:type (ex-data e)) "docs/failed")
                                                             :message (ex-message e)})
                                       (update :names conj name)))))))
                     {:plans [] :names [] :failed [] :carried 0 :minted 0 :paths [] :units-carried 0}
                     documents)
        all (concat (:plans mres) (:plans dres))
        carried (:carried dres 0)
        declared (set (:paths dres))
        declared-models (set (map first (:models mres)))
        models-not-carried (vec (sort (remove declared-models (keys (get-in registry [:models :by-name])))))
        not-carried (vec (sort (remove declared (keys (:by-path registry)))))
        operation (if (pos? carried)
                    (str "include build (identities carried from " (:source registry) ")")
                    "include build")
        merged (when (seq all)
                 (plan/compose-plans all {:base (:head store) :actor actor :timestamp ts
                                          :engines {"kimun.surface.docs" "1"
                                                    tdocs/meta-key (pr-str {:operation operation
                                                                            :address (str (count (:names dres)) " documents")
                                                                            :fields {}})}}))
        r (when merged (store/commit! backend store merged))]
    {:revision-id (:revision-id r)
     :models (- (count models) (count (:failed mres)))
     :models-failed (count (:failed mres))
     :documents (- (count documents) (count (:failed dres)))
     :documents-failed (count (:failed dres))
     :failed (vec (concat (:failed mres) (:failed dres)))
     :identities {:available (:available registry)
                  :carried carried :minted (:minted dres 0)
                  :units-carried (:units-carried dres 0)
                  :models {:tree-carried (boolean (:tree-carried mres))
                           :kept (:kept mres 0) :advanced (:advanced mres 0) :new (:new mres 0)}
                  :models-not-carried models-not-carried
                  :not-carried not-carried
                  :ambiguous (:ambiguous registry)
                  :without-file (:without-file registry 0)}})))

(defn- backup-stamp
  "The local time a backup is named with, to the second (`yyyyMMdd-HHmmss`)."
  []
  (-> (java.time.LocalDateTime/now)
      (.format (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd-HHmmss"))))

(defn build
  "`include build [--rebuild]`: builds the store from the declaration in ONE
   revision (docs/v2/14 §10): `stores init`, then every model descriptor and
   every declared document composed into one TransactionPlan and committed
   once. Refuses to touch an existing store unless `--rebuild`, which moves it
   to `<store>.bak-<timestamp>-<ulid>` (a unique name: an existing backup is
   never overwritten). A failure is `:include/drift` (exit 3)."
  [args opts {:keys [env cwd] :as ctx}]
  (no-args! args "build")
  (let [{:keys [root cfg plan]} (config-plan ctx)
        store-dir (str (fs/path root (get-in cfg [:store :path])))
        ;; the old store is read BEFORE it is moved: a rebuild that cannot read it
        ;; still runs (it is the recovery path), with fresh ids and a warning
        ;; the name the old store is moved to, decided once: the identities are
        ;; reported, and the transaction is labelled, with where they came from
        ;; unique: the timestamp has second resolution, so a fresh ULID follows it
        ;; and two rebuilds in the same second (or two processes) never choose
        ;; the same name. The move below does not replace: an existing backup is
        ;; never overwritten. No lock is taken here, so two CONCURRENT rebuilds
        ;; of one store are still not coordinated; only the backups are safe.
        bak (str store-dir ".bak-" (backup-stamp) "-" (ports/ulid (:ids host/host)))
        old-registry (when (and (:rebuild opts) (fs/exists? store-dir))
                       (assoc (identity-registry store-dir) :source (str (fs/file-name bak))))]
    (when (fs/exists? store-dir)
      (if (:rebuild opts)
        (do (fs/move store-dir bak)
            (binding [*out* *err*] (println (str "moved existing store to " bak))))
        (err/raise :include/exists
                   (str store-dir " already exists; pass --rebuild to move it aside and build it again")
                   {:path store-dir})))
    (let [init (:envelope (run-cli root env (cond-> ["stores" "init" "--store" store-dir]
                                              (= :inline (get-in cfg [:store :content]))
                                              (conj "--content" "inline"))))
          _ (when-not (:ok init)
              (err/raise :include/drift (str "stores init failed: " (get-in init [:error :type]))
                         {:phase "stores init" :error (get-in init [:error :type]) :message (get-in init [:error :message])}))
          registry (if (and old-registry (:available old-registry))
                     (assoc old-registry :source (:source old-registry))
                     old-registry)
          identities (when-not (:rebuild opts) {:available true :by-path {} :ambiguous [] :without-file 0})
          r (build-plan! root store-dir plan opts
                         (or registry identities))
          ids (:identities r)
          identities (cond-> ids
                       (and old-registry (not (:available old-registry)))
                       ;; no identity was read: nothing carried, nothing known about
                       ;; the old store; the documents minted are still counted
                       (assoc :available false :carried 0 :not-carried [] :ambiguous []
                              :units-carried 0 :without-file 0
                              :models {:tree-carried false :kept 0 :advanced 0
                                       ;; every declared model is new when nothing was read
                                       :new (get-in ids [:models :new] 0)}
                              :models-not-carried []))
          data {:store store-dir
                :models (:models r) :models-failed (:models-failed r)
                :documents (:documents r) :documents-failed (:documents-failed r)
                :failed (:failed r)
                :identities (assoc identities :source (get old-registry :source))}
          warnings (cond-> []
                     (and old-registry (not (:available old-registry)))
                     (conj {:type "include/identities-unavailable"
                            :message (str "the previous store could not be read, so no document identity was carried: the documents were registered with fresh ids")
                            :store (get old-registry :source)})
                     (and (:available identities)
                          (or (seq (:not-carried identities)) (seq (:ambiguous identities))
                              (seq (:models-not-carried identities))))
                     (conj {:type "include/identities-not-carried"
                            :message (str (count (:not-carried identities)) " document identit(ies) of the previous store were not carried"
                                          (when (seq (:ambiguous identities))
                                            (str " and " (count (:ambiguous identities)) " path(s) had two documents"))
                                          (when (seq (:models-not-carried identities))
                                            (str " and " (count (:models-not-carried identities))
                                                 " model(s) the declaration no longer defines: "
                                                 (str/join ", " (:models-not-carried identities))))
                                          ": see :identities")
                            :not-carried (:not-carried identities)
                            :ambiguous (:ambiguous identities)
                            :models-not-carried (:models-not-carried identities)}))]
      (if (seq (:failed r))
        (out/failure :include/drift (str (count (:failed r)) " declaration(s) failed to build")
                     (assoc data :warnings warnings) 3)
        (out/ok "include build" {:store {:path store-dir} :revision (:revision-id r)
                                 :data data :warnings warnings})))))

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
