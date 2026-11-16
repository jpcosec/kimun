(ns kimun.cli.commands.stores
  "The `kimun stores` group (docs/v2/06 §3): `init`, `check`, `health`,
   `sense`, `changed`, `heartbeat`, `update`, `reconcile`, `verify`, `unreachable`, `pack`,
   `unpack`, `show`, and the federation commands `link`, `unlink` and
   `links`. Every
   command is `(fn [args opts ctx] envelope)` where `args` are the positional
   tokens after the subcommand and `ctx` is `{:env env :cwd cwd}`; `dispatch`
   routes a subcommand name."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [kimun.cli.commands.docs :as docs]
            [kimun.cli.compose :as compose]
            [kimun.cli.errors :as errors]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.host.git :as git]
            [kimun.host.seen :as seen]
            [kimun.surface.changes :as changes]
            [kimun.surface.diagnostics :as diag]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.heartbeat :as heartbeat]
            [kimun.surface.signals :as signals]
            [kimun.host.default :as host]
            [kimun.host.fs-store :as fs-store]
            [kimun.kernel.err :as err]
            [kimun.kernel.ports :as ports]
            [kimun.kernel.plan :as plan]
            [kimun.kernel.revision :as revision]
            [kimun.kernel.store :as store]))

(defn- store-ref [descriptor path] {:name (:name descriptor) :path path})

(defn- link-warning [{:keys [name status resolved]}]
  {:type (str "link/" (clojure.core/name status))
   :message (str "link " name " -> " resolved
                 (if (= :missing status) ": target does not exist" ": target is not a kimun store"))})

(defn init
  "`stores init`: creates the store at `--store` (if given) or `<cwd>/.kimun`
   with `--name` and `--actor`. Data: `{:path :name :actor :capabilities}`."
  [_args opts {:keys [cwd]}]
  (let [dir (if (:store opts)
              (cli-store/normalize-store-path (:store opts))
              (str (fs/path (fs/absolutize cwd) cli-store/dirname)))
        r (cli-store/init! dir {:name (:name opts) :actor (:actor opts)
                                :content (case (:content opts) "inline" :inline :by-reference)})]
    (out/ok "stores init" {:store (store-ref (:descriptor r) (:path r))
                           :revision nil
                           :data (select-keys r [:path :name :actor :capabilities])})))

(defn- file-reader
  "`(fn [relative-path] text-or-nil)` over the project that owns store `path`."
  [path]
  (let [root (fs/parent (fs/absolutize path))]
    (fn [rel] (let [f (fs/path root rel)] (when (fs/regular-file? f) (slurp (str f)))))))

(defn- diagnose
  "The `kimun.surface.diagnostics/report` of an opened store (`open!` result),
   with the kernel's hash verification handed in."
  [{:keys [backend store path]}]
  (diag/report store (file-reader path) {:verify (store/verify backend store)}))

(defn- project-of
  "`{:root :ex}` for the open store at `path`: the project directory and the store's path
   relative to it (excluded from what git reports)."
  [path]
  (let [root (str (fs/parent (fs/absolutize path)))]
    {:root root :ex [(str (fs/relativize root (fs/absolutize path)))]}))

(defn- change-scope
  "Which documents the diagnosis has to open. `{:mode :full}` (everything, as always) when
   `full?` is set, the project is not in a git repository with commits, or the store has no
   pointer (`stores seen`); a damaged pointer is `git/bad-pointer`, never ignored. Otherwise
   `{:mode :git :since :head :changed n :paths #{…}}`: what git says moved between the pointer
   and the working tree (committed or not, untracked included); a renamed path counts at the
   old and at the new name. Read-only: nobody moves the pointer here, so a pointer moved while
   the store lagged is not seen by this path (`--full` sees it)."
  [path full?]
  (if full?
    {:mode :full}
    (let [{:keys [root ex]} (project-of path)
          head (try (git/repo-head root)
                    (catch clojure.lang.ExceptionInfo e
                      (if (#{:git/not-a-repository :git/no-commits} (:type (ex-data e))) nil (throw e))))
          since (when head (seen/read-pointer path root))]
      (if-not since
        {:mode :full}
        (let [changes (git/changed-since root since ex)]
          {:mode :git :since since :head head :changed (count changes)
           :paths (into #{} (comp (mapcat (juxt :path :from)) (remove nil?)) changes)})))))

(defn- scoped-report
  "`[report scope]`: the diagnosis of an opened store, opening only the documents `change-scope`
   says may have changed. `scope` is the `:scope` of the response:
   `{:mode :git|:full :since :head :changed :opened}` (`:opened`: documents whose file was read)."
  [{:keys [backend store path]} full?]
  (let [sc (change-scope path full?)
        reader (file-reader path)
        opened (atom 0)
        report (diag/report store (fn [rel] (swap! opened inc) (reader rel))
                            {:verify (store/verify backend store) :changed (:paths sc)})]
    [report {:mode (:mode sc) :since (:since sc) :head (:head sc) :changed (:changed sc) :opened @opened}]))

(def ^:private warning-type
  {:reformatted "docs/file-modified" :edited "docs/file-modified" :missing "docs/file-missing"
   :contract-moved "docs/superseded" :parser-unavailable "docs/parser-unavailable"
   :contract-inconsistent "docs/contract-inconsistent" :rebuild-mismatch "docs/rebuild-mismatch"
   :unextractable "docs/unextractable" :noise "docs/structural-noise"
   :orphan "docs/orphan" :corrupt "store/corrupt"})

(defn- finding-warning [f]
  (assoc (select-keys f [:category :model :document :path :action :undeclared])
         :type (warning-type (:category f)) :message (:message f)))

(defn check
  "`stores check`: opens the store (replaying the log) and reports
   `{:name :path :hash-alg :revisions :trees :objects :head :links}` (plus
   `:routes-root`, `:route-count`, `:routes-complete?`, `:routes-missing`,
   `:routes-shared` and `:routes-outside` once a document with a file is
   registered: the root of the route Merkle of K2a, the routes under it and
   what it does not cover — documents registered before routes existed
   (`routes/partial` warning), legacy second documents on a file another one
   holds (`routes/shared-path`) and documents whose file is outside the
   project (`routes/outside-project`); a root that leaves something out is
   never reported as complete) where
   `:links` is the `kimun.cli.store/links` report; a link whose target is
   missing or not a store is a warning `{:type \"link/missing\" :message …}`
   (the command stays `:ok`). When the store tracks typed documents (or is
   corrupt), `:documents` is the `kimun.surface.diagnostics/report` plus
   `:tracked`: `{:tracked :valid :summary :checked :counts :models :documents
   :findings}`; every finding is also a warning `{:type :message :category
   :model :document :path :action}` naming the command that repairs it. A
   store that is not valid (anything beyond `:ok` and `:reformatted`) is the
   failure `store/invalid`, exit 5, carrying the same data plus `:warnings`."
  [_args opts {:keys [env cwd]}]
  (let [{:keys [descriptor store path backend] :as opened} (cli-store/open! (cli-store/resolve-store opts env cwd))
        link-report (cli-store/links path)
        report (diagnose opened)
        ;; what the route root does not cover; unreadable when the store is damaged (reported below)
        coverage (err/rescue-any (fn [_] {:missing [] :shared [] :outside []}) (tdocs/route-coverage store))]
    (let [data (cond-> {:name (:name descriptor)
                        :path path
                        :hash-alg (:hash-alg descriptor)
                        ;; nil when damage (reported in :documents) makes the view unreadable
                        :revisions (err/rescue-any (fn [_] nil) (count (:revisions store)))
                        :trees (err/rescue-any (fn [_] nil) (count (:trees store)))
                        :objects (err/rescue-any (fn [_] nil) (count (:objects store)))
                        :head (:head store)
                        :links link-report}
                 ;; the route Merkle (K2a) covers the registered files that hold a route, not everything
                 ;; tracked: what it leaves out (unrouted, shared or outside the project) is listed with it
                 (or (:routes-root store) (some #(seq (get coverage %)) [:missing :shared :outside]))
                 (assoc :routes-root (:routes-root store)
                        :route-count (err/rescue-any (fn [_] nil) (count (:routes store)))
                        :routes-complete? (every? #(empty? (get coverage %)) [:missing :shared :outside])
                        :routes-missing (mapv :qualified (:missing coverage))
                        :routes-shared (mapv #(select-keys % [:name :qualified]) (:shared coverage))
                        :routes-outside (mapv #(select-keys % [:name :qualified]) (:outside coverage)))
                 (or (seq (:documents report)) (seq (:findings report)))
                 (assoc :documents (assoc report :tracked (count (:documents report)))))
          partial (when (seq (:missing coverage))
                    [{:type "routes/partial"
                      :message (str "the route root covers " (:routed coverage) " of " (:registered coverage)
                                    " registered documents: " (count (:missing coverage))
                                    " were registered before routes existed and are not under it — `kimun docs update NAME`"
                                    " (typed) or `kimun docs update FILE --name NAME` registers each one's route")
                      :missing (mapv :qualified (:missing coverage))}])
          shared (when (seq (:shared coverage))
                   [{:type "routes/shared-path"
                     :message (str (count (:shared coverage)) " document(s) track a file whose route another document holds: "
                                   (str/join ", " (map #(str (:name %) " (" (:qualified %) ")") (:shared coverage)))
                                   " — the route map has one entry per path, so the route root does not cover them;"
                                   " they are still tracked and sensed")
                     :documents (mapv #(select-keys % [:name :qualified]) (:shared coverage))}])
          outside (when (seq (:outside coverage))
                    [{:type "routes/outside-project"
                      :message (str (count (:outside coverage)) " document(s) track a file outside the project: "
                                    (str/join ", " (map #(str (:name %) " (" (:qualified %) ")") (:outside coverage)))
                                    " — a path that leaves the project cannot be a route, so the route root does not cover them;"
                                    " they are still tracked and sensed")
                      :documents (mapv #(select-keys % [:name :qualified]) (:outside coverage))}])
          ;; a cache pack this reader ignored: the cache is not the store, so it is
          ;; a warning, never a failure (docs/v2/10 §11.5)
          bad-cache-packs (when backend (fs-store/cache-bad-packs backend))
          cache-warnings (when (seq bad-cache-packs)
                           [{:type "cache/corrupt-pack"
                             :message (str (count bad-cache-packs) " cache pack(s) cannot be read and are ignored (cold cache): "
                                           (str/join ", " bad-cache-packs) " — `kimun stores pack --cache` drops them")
                             :packs bad-cache-packs}])
          warnings (-> (mapv link-warning (remove #(= :ok (:status %)) link-report))
                       (into cache-warnings)
                       (into partial)
                       (into shared)
                       (into outside)
                       (into (map finding-warning) (:findings report)))]
      (if (false? (:valid report))
        ;; an invalid store is a failure a script can see (v1 exited non-zero);
        ;; the whole report travels in the error data, warnings included
        (out/failure :store/invalid (:summary report) (assoc data :warnings warnings) 5)
        (out/ok "stores check" {:store (store-ref descriptor path)
                                :revision (:head store)
                                :data data
                                :warnings warnings})))))

(defn- observe
  "One read-only observation of the store at `dir`: opens it from scratch,
   runs the diagnostics and returns the success envelope whose `:data` is
   `{:observed-at :tracked :valid :signals :counts :summary}`. Shared by
   `stores sense` and every beat of `stores heartbeat`, so both report the
   same signals."
  [command dir full?]
  (let [{:keys [descriptor store path] :as opened} (cli-store/open! dir)
        [report scope] (scoped-report opened full?)
        signals (signals/from-report report)]
    (out/ok command {:store (store-ref descriptor path)
                     :revision (:head store)
                     :data {:observed-at (docs/timestamp)
                            :tracked (:checked report)
                            :valid (:valid report)
                            :signals signals
                            :counts (signals/counts signals)
                            :summary (signals/summary signals)
                            :scope scope}})))

(defn sense
  "`stores sense [--once]`: one read-only observation of tracked documents.
   Reuses the existing diagnostics report and emits structured signals
   (`kimun.surface.signals`); it writes nothing. `--once` is accepted to make
   the single observation explicit; this command never loops (the loop is
   `stores heartbeat`). Data: `{:mode :once :observed-at :tracked :valid :signals
   :counts :summary}`. `:observed-at` is the wall-clock of this response only;
   it is never written to the store as an event.

   With a git repository that has commits and a pointer (`stores seen`), only the documents
   whose path git reports changed since the pointer are opened (`change-scope`); `--full`, no
   git, no commits or no pointer open them all. `:scope` says which path was taken
   (`{:mode :git|:full :since :head :changed :opened}`). Nobody moves the pointer here: a pointer
   moved while the store lagged is invisible to the git path; `--full` sees it."
  [_args opts {:keys [env cwd]}]
  (update (observe "stores sense" (cli-store/resolve-store opts env cwd) (:full opts))
          :data #(merge (array-map :mode :once) %)))

(defn changed
  "`stores changed [--since COMMIT]`: read-only, asks git what changed between COMMIT and the
   working tree, and for the changed files that have a mark in the store, the state of each
   mark (`kimun.surface.changes/since`). Without `--since` COMMIT is the pointer of
   `stores seen` (`:cli/usage` telling how to create it when there is none; `git/bad-pointer`
   when it is damaged). `--since` never moves the pointer. Next to `stores sense`, which it
   leaves untouched: it does not hash the tracked documents, opens only changed files with
   marks and writes nothing. A directory that is not a git repository is
   `git/not-a-repository`. Data: `{:since :pointer :head :dirty :changed :marks :summary}`;
   `:pointer` is the stored sha or nil, so it shows where `:since` came from."
  [_args opts {:keys [env cwd]}]
  (let [since (:since opts)
        _ (when-not (or (nil? since) (and (string? since) (seq since)))
            (err/raise :cli/usage "`stores changed --since` needs a COMMIT" {:group "stores" :subcommand "changed"}))
        {:keys [descriptor store path]} (cli-store/open! (cli-store/resolve-store opts env cwd))
        {:keys [root ex]} (project-of path)
        head (git/repo-head root)
        pointer (if since (seen/raw path) (seen/read-pointer path root))
        _ (when-not (or since pointer)
            (err/raise :cli/usage "no pointer yet: pass --since COMMIT, or create the pointer with `stores seen --set HEAD`"
                       {:group "stores" :subcommand "changed"}))
        sha (git/resolve-commit root (or since pointer))
        data (changes/since host/host store
                            {:since sha :pointer pointer :head head
                             :dirty (boolean (seq (git/changed-since root head ex)))
                             :changes (git/changed-since root sha ex)}
                            {:read-then #(git/show root sha %) :read-now (file-reader path)})]
    (out/ok "stores changed" {:store (store-ref descriptor path) :revision (:head store) :data (assoc data :pointer pointer)})))

(defn seen
  "`stores seen [--set COMMIT]`: the pointer \"I looked up to this commit\" kept in
   `<store>/seen.edn` (`kimun.host.seen`). Without `--set` it only reads:
   `{:commit sha-or-nil :head sha :behind bool}` (`:behind` = the pointer is not HEAD). With
   `--set COMMIT` (`HEAD` is a commit; resolved to a full sha by git) it moves the pointer, the
   only thing it writes, and answers `{:previous :commit :head :behind}`. Uncommitted changes
   are not hidden by moving it: `stores changed` always diffs against the working tree."
  [args opts {:keys [env cwd]}]
  (let [{:keys [descriptor store path]} (cli-store/open! (cli-store/resolve-store opts env cwd))
        {:keys [root]} (project-of path)
        head (git/repo-head root)
        ;; `--set` is the repeatable option of `docs update` (one global spec, kimun.cli.args):
        ;; it arrives as a vector of strings, so the commit is required and `HEAD` names HEAD
        setv (:set opts)
        target (first setv)
        ref (store-ref descriptor path)]
    (when (or (seq args) (> (count setv) 1))
      (err/raise :cli/usage "`stores seen` takes no positional argument and one COMMIT at most: --set COMMIT" {:group "stores" :subcommand "seen"}))
    (if setv
      (let [previous (seen/raw path)
            sha (git/resolve-commit root (or target head))]
        (seen/write-pointer! path sha)
        (out/ok "stores seen" {:store ref :revision (:head store)
                               :data {:previous previous :commit sha :head head :behind (not= sha head)}}))
      (let [c (seen/read-pointer path root)]
        (out/ok "stores seen" {:store ref :revision (:head store)
                               :data {:commit c :head head :behind (and (some? c) (not= c head))}})))))

(defn- emit-line!
  "Writes `record` as one compact JSON line on stdout and flushes; false when
   stdout no longer accepts output (the consumer closed the pipe). The line is
   written through `*out*` and the closed pipe is detected on the process's
   `System/out`: the two are the same stream in the CLI, the only supported
   host. Where `*out*` is rebound (an in-process test, an embedding) the line
   goes to the rebound writer and a closed consumer is not detected."
  [record]
  (try (println (json/generate-string record))
       (flush)
       (not (.checkError System/out))
       (catch java.io.IOException _ false)))

(defn heartbeat
  "`stores heartbeat [--interval SECONDS] [--beats N]`: the heartbeat daemon.
   Every interval it repeats the observation of `stores sense` and writes it to
   stdout; that is all it does (no action, no LLM, no embedding, no write to
   the store or the log). It is the explicit exception to \"one envelope on
   stdout\" (docs/v2/06 §1): the output is NDJSON, one compact JSON record per
   line — `started`, then `beat` or `error` per attempt, then `stopped`
   (`kimun.surface.heartbeat`); `--format` does not apply to the stream.

   `--interval` is a fixed delay in seconds after each observation (default
   30, minimum 0.1); `--beats` ends after N attempts (default: until stopped).
   A failed observation is an `error` record and the next beat tries again
   from scratch. SIGINT/SIGTERM stop between observations with a `stopped`
   record (the hook waits up to 2 s for it); a closed stdout ends the loop.
   A bad option or a missing store fails before any record, as the usual
   failure envelope. A failure of the loop itself ends the stream with a
   `stopped` record of reason `fatal` and exit 70. The returned envelope
   carries `{:streamed true}` in its metadata so `kimun.cli.main` does not
   print it after the stream."
  [_args opts {:keys [env cwd]}]
  (let [interval-ms (or (heartbeat/parse-interval (:interval opts))
                        (err/raise :cli/usage (str "--interval needs seconds between 0.1 and 86400, got `" (:interval opts) "`")
                                   {:option "interval" :value (:interval opts)}))
        beats (let [b (heartbeat/parse-beats (:beats opts))]
                (if (= :invalid b)
                  (err/raise :cli/usage (str "--beats needs a positive integer, got `" (:beats opts) "`")
                             {:option "beats" :value (:beats opts)})
                  b))
        dir (cli-store/resolve-store opts env cwd)
        _ (when (or (nil? dir) (not (cli-store/store? dir))) (cli-store/open! dir))
        descriptor (store/read-descriptor (cli-store/backend dir))
        ref (store-ref descriptor (str dir))
        stop (promise)
        done (promise)
        hook (Thread. (fn [] (deliver stop true) (deref done 2000 nil)))
        runtime (Runtime/getRuntime)]
    (.addShutdownHook runtime hook)
    (try
      (let [r (heartbeat/run {:observe #(errors/wrap (fn [] (observe "stores heartbeat" dir (:full opts))))
                              :emit emit-line!
                              :wait #(true? (deref stop % false))
                              :stop? #(realized? stop)
                              :clock docs/timestamp
                              :now-ms #(System/currentTimeMillis)
                              :store ref
                              :interval-ms interval-ms
                              :beats beats})]
        ;; nothing but NDJSON may follow the first record: either way the envelope is not printed
        (with-meta (if (= :fatal (:reason r))
                     (out/failure :heartbeat/fatal (get-in r [:error :message]) (select-keys r [:beats :errors]) 70)
                     (out/ok "stores heartbeat" {:store ref :data r}))
          {:streamed true}))
      (finally
        (deliver done true)
        (try (.removeShutdownHook runtime hook) (catch IllegalStateException _ nil))))))

(defn health
  "`stores health`: the verdict of the store (`kimun.surface.diagnostics/health`):
   `{:status :summary :findings :actions :next}` with `:status` `:ok`,
   `:degraded` (a command repairs it: `:next` is `run: …`) or `:needs-human`
   (`:next` is `inspect: …`). Each finding is also a warning. `:data` adds `:scope`, and
   the git path of `stores sense` applies (`--full` opens every document)."
  [_args opts {:keys [env cwd]}]
  (let [{:keys [descriptor store path] :as opened} (cli-store/open! (cli-store/resolve-store opts env cwd))
        [report scope] (scoped-report opened (:full opts))]
    (out/ok "stores health" {:store (store-ref descriptor path)
                             :revision (:head store)
                             :data (assoc (diag/health report) :summary (:summary report) :scope scope)
                             :warnings (mapv finding-warning (:findings report))})))

(defn- update-meta
  "The `kimun/meta` of a composed `stores update` revision: one entry per
   repaired document (`:address` and `:fields` changed), so the journal keeps
   naming what moved even though the whole repair is one transaction."
  [metas]
  {:operation "stores update"
   :address (str (count metas) " documents")
   :fields {}
   :documents (mapv #(select-keys % [:address :fields]) metas)})

(defn update-store
  "`stores update`: applies every repair that needs no decision — re-reads the
   documents whose file was only reformatted and moves those whose model's
   contract moved to the current contract by re-rendering their recorded
   payload under it (v1 `stores update` recomputed hashes; here a hash is never
   stored apart from its content, so updating means committing the new source).
   Every repaired document's `update-plan` is computed in memory on the state
   the previous one leaves and all of them compose into ONE revision
   (`kimun.kernel.plan/compose-plans`, S1b step 2) committed once; the journal
   entry carries `:documents` with the fields each one changed. Findings that
   need a human are left alone and listed. Data:
   `{:updated [name] :skipped [{:document :category :action}] :status}`."
  [_args opts {:keys [env cwd]}]
  (let [{:keys [backend descriptor path] :as opened} (cli-store/open! (cli-store/resolve-store opts env cwd))
        base (:store opened)
        read-file (file-reader path)
        root (str (fs/parent (fs/absolutize path)))
        findings (:findings (diagnose opened))
        safe (filter (comp diag/safe :category) findings)
        ts (docs/timestamp)
        {:keys [final plans metas warnings]}
        (reduce (fn [{:keys [final plans metas] :as acc} f]
                  (if (:generic f)
                    ;; a document without a model (K3): its file is registered again as it is
                    (let [doc (tdocs/generic-document final (:tree f))
                          {:keys [plan]} (tdocs/generic-update-plan host/host final doc (read-file (:path doc))
                                                                    {:actor (:actor opts) :timestamp ts :operation "stores update"})
                          meta (when plan (edn/read-string (get-in plan [:engines tdocs/meta-key])))]
                      (-> acc
                          (assoc :final (if plan (:store (revision/apply-plan final plan)) final))
                          (cond-> plan (update :plans conj plan))
                          (cond-> meta (update :metas conj meta))))
                  (let [doc (tdocs/document final (or (:tree f) (:document f)))
                        ;; only a redefined model asks for the text to be re-rendered; when the
                        ;; parser moved (K6) the file is already right and is only registered again
                        moved? (and (= :contract-moved (:category f)) (= :superseded (:state f)))
                        text (if moved?
                               (tdocs/move-text final doc nil (compose/context final path))
                               (or (some-> (:path doc) read-file) (:source doc)))
                        {:keys [plan]} (tdocs/update-plan host/host final (or (:tree f) (:document f)) text
                                                          {:mint (fn [] (ports/ulid (:ids host/host)))
                                                           :actor (:actor opts) :timestamp ts :operation "stores update"})
                        st (if plan (:store (revision/apply-plan final plan)) final)
                        meta (when plan (edn/read-string (get-in plan [:engines tdocs/meta-key])))
                        w (if (and plan moved? (:path doc))
                            (try (spit (str (fs/path root (:path doc))) text) nil
                                 (catch Exception e [{:type "docs/file-not-written"
                                                      :message (str "could not write " (:path doc) ": " (ex-message e))}]))
                            [])]
                    (-> acc
                        (assoc :final st)
                        (cond-> plan (update :plans conj plan))
                        (cond-> meta (update :metas conj meta))
                        (update :warnings into w)))))
                {:final base :plans [] :metas [] :warnings []}
                safe)
        composed (when (seq plans)
                   (plan/compose-plans plans
                                       {:base (:head base) :actor (:actor opts) :timestamp ts
                                        :engines {"kimun.surface.docs" "1"
                                                  tdocs/meta-key (pr-str (update-meta metas))}}))
        final (if composed (:store (store/commit! backend base composed)) final)
        after (diag/report final read-file)]
    (out/ok "stores update" {:store (store-ref descriptor path)
                             :revision (:head final)
                             :data {:updated (mapv :document safe)
                                    :skipped (mapv #(select-keys % [:document :category :action]) (remove (comp diag/safe :category) findings))
                                    :status (:status (diag/health after))}
                             :warnings (into (vec warnings) (mapv finding-warning (:findings after)))})))

(defn- canonical [p] (str (fs/canonicalize p)))

(defn reconcile
  "`stores reconcile [ROOT] [--apply]`: compares the stores found under ROOT
   (default: this store's project directory) with this store's links (v1
   `reconcile_catalog`). Data: `{:root :discovered [path] :missing [path]
   :stale [{:name :resolved}] :applied}` — `:missing` are stores found but
   not linked, `:stale` links whose target is gone. Nothing is written
   without `--apply`, which links each missing store under its project
   directory's name and unlinks each stale link."
  [args opts {:keys [env cwd]}]
  (let [{:keys [descriptor store path]} (cli-store/open! (cli-store/resolve-store opts env cwd))
        root (if-let [r (first args)] (str (fs/absolutize (fs/path cwd r))) (str (fs/parent (fs/absolutize path))))
        found (->> (fs/glob root "**store.edn" {:hidden true})
                   (map fs/parent)
                   (filter #(= cli-store/dirname (str (fs/file-name %))))
                   (map canonical) (remove #{(canonical path)}) sort vec)
        linked (cli-store/links path)
        registered (set (map (comp canonical :resolved) (filter #(= :ok (:status %)) linked)))
        missing (vec (remove registered found))
        stale (mapv #(select-keys % [:name :resolved]) (remove #(= :ok (:status %)) linked))]
    (when (:apply opts)
      (doseq [s stale] (cli-store/unlink! path (:name s)))
      (doseq [m missing] (cli-store/link! path (str (fs/file-name (fs/parent m))) m cwd)))
    (out/ok "stores reconcile" {:store (store-ref descriptor path)
                                :revision (:head store)
                                :data {:root root :discovered found :missing missing :stale stale :applied (boolean (:apply opts))}})))

(defn verify
  "`stores verify`: the full verification (`kimun.kernel.store/verify-full`):
   replays the whole log (every transaction and revision id, final heads
   against heads.edn) and recomputes the hash of every reachable object; a log
   that does not replay is `:bad [{:reason :replay-failed}]`. (`stores check`
   runs only the hash check, on the store as opened from its heads.) Data
   `{:checked :bad :routes}`, plus `:uncommitted-tail`
   when the log ends with a line the heads do not record; a mismatch is the
   failure `store/corrupt` (exit 5) carrying `:bad`."
  [_args opts {:keys [env cwd]}]
  (let [{:keys [backend descriptor store path]} (cli-store/open! (cli-store/resolve-store opts env cwd))
        {:keys [ok? checked bad uncommitted-tail]} (store/verify-full backend (:host store))]
    (if ok?
      (out/ok "stores verify" {:store (store-ref descriptor path)
                               :revision (:head store)
                               :data (cond-> {:checked checked :bad bad
                                              :routes (err/rescue-any (fn [_] nil) (store/route-coverage store))}
                                       ;; a writer died mid-commit: reported, not a failure; the next commit discards it
                                       uncommitted-tail (assoc :uncommitted-tail (select-keys uncommitted-tail [:id :revision :torn-line])))})
      (out/failure :store/corrupt
                   (if-let [f (first (filter #(= :replay-failed (:reason %)) bad))]
                     (str "the log does not replay in " path ": " (:message f))
                     (str (count bad) " problem(s) in " path " (objects that do not hash to their name, or a log that does not end at the heads)"))
                   {:checked checked :bad bad :path path}
                   5))))

(defn- tally
  "`{:count n :bytes n :by-class {class {:count n :bytes n}}}` of `items`
   (`{:id :class}`), with the stored size of each object (`fs-store/stored-size`:
   a loose file or its length inside a pack)."
  [backend items]
  (let [size (fn [i] (long (or (fs-store/stored-size backend (:id i)) 0)))
        sized (map #(assoc % :bytes (size %)) items)
        total (fn [xs] {:count (count xs) :bytes (reduce + 0 (map :bytes xs))})]
    (assoc (total sized)
           :by-class (into (sorted-map)
                           (map (fn [[c xs]] [(name c) (total xs)]))
                           (group-by :class sized)))))

(def unreachable-note
  "What every `stores unreachable` answer says of itself, so that the report
   is not read as a list of garbage."
  "inventory only: nothing was written or removed. A candidate is an object the heads did not reach when the inventory was taken; it is not known to be safe to remove (a writer stores its objects before it moves the heads).")

(defn unreachable
  "`stores unreachable [--ids]`: the inventory of the objects under objects/
   that the heads do not reach (`kimun.kernel.store/unreachable`; docs/v2/10
   §11.3). Read-only: it writes nothing and removes nothing; there is no
   command that removes them. Data:

   - `:note`: `unreachable-note`, the same sentence in every answer.
   - `:stored`, `:reached`, `:unreachable`: object counts (`:stored` is what
     `stores check` reports as `:objects`).
   - `:candidates` `{:count :bytes :by-class}`: unreachable objects nothing is
     known to need, by shape; `:roots` the same for those no other unreachable
     object mentions (roots of the heads map and of the route map that a later
     commit replaced or that a losing writer wrote).
   - `:lost-revisions` `[id]`: candidates that are revisions, i.e. commits that
     are not in the history of the head (a writer that lost the race, or the
     uncommitted tail).
   - `:damaged` `[{:id :reason}]`: unreachable objects that do not hash to
     their name or do not read; only reported.
   - `:stable?`: false when the heads moved while the inventory was taken.
   - `:uncommitted-tail` when the log ends with a line the heads do not record.
   - with `--ids`, `:ids {:candidates [{:id :class :root? :bytes}]}`.

   Bytes are file sizes, not disk blocks. A candidate is not known to be
   garbage: a writer stores its objects before it moves the heads, so an
   inventory taken during a commit lists objects about to be reached. Warning
   `unreachable/unstable` when `:stable?` is false."
  [_args opts {:keys [env cwd]}]
  (let [{:keys [backend descriptor store path]} (cli-store/open! (cli-store/resolve-store opts env cwd))
        r (store/unreachable backend store)
        size (fn [i] (assoc i :bytes (long (or (fs-store/stored-size backend (:id i)) 0))))
        tail (:uncommitted-tail r)]
    (out/ok "stores unreachable"
            {:store (store-ref descriptor path)
             :revision (:head store)
             :data (cond-> {:path path
                            :note unreachable-note
                            :stored (:stored r) :reached (:reached r) :unreachable (:unreachable r)
                            :candidates (tally backend (:candidates r))
                            :roots (tally backend (filter :root? (:candidates r)))
                            :lost-revisions (mapv :id (filter #(= :revision (:class %)) (:candidates r)))
                            :damaged (:damaged r)
                            :stable? (:stable? r)}
                     tail (assoc :uncommitted-tail (select-keys tail [:id :revision :torn-line]))
                     (:ids opts) (assoc :ids {:candidates (mapv size (:candidates r))}))
             :warnings (if (:stable? r)
                         []
                         [{:type "unreachable/unstable"
                           :message "the heads moved while the inventory was taken; run it again"}])})))

(def ^:private max-pack-bytes
  "The largest `stores pack --max-bytes` accepts: a pack record is held in
   memory while it is written, so the option has a ceiling."
  16777216)

(defn- checked-max-bytes
  "`--max-bytes` as a long in [1, max-pack-bytes], or nil when it was not given;
   anything else is `:cli/usage`."
  [opts]
  (when-let [v (:max-bytes opts)]
    (let [n (try (parse-long (str v)) (catch Exception _ nil))
          bad (fn [why]
                (err/raise :cli/usage
                           (str "`stores pack --max-bytes` " why ", got `" v "`")
                           {:group "stores" :subcommand "pack" :option "max-bytes" :value v}))]
      (cond (nil? n) (bad "needs an integer")
            (< n 1) (bad "needs a positive integer")
            (> n max-pack-bytes) (bad (str "is at most " max-pack-bytes " (16 MiB)"))
            :else n))))

(defn pack
  "`stores pack [--max-bytes N] [--cache]`: moves the loose objects smaller than
   N bytes (default 4096) into a new pack and removes the loose copies
   (`kimun.host.fs-store/pack!`; docs/v2/10 §11.4 for the store, §11.5 for the
   cache). Explicit and never part of a commit. `--cache`
   packs the DERIVED cache (`cache/objects` → `cache/packs`), which is not the
   store: it also drops the cache packs that cannot be loaded, deletes a loose
   cache object that does not hash to its name and deletes a loose object a
   cache pack already holds. Both scopes repair a missing or stale `.idx`
   (readers never write), so `:indexes-repaired` can be positive with `:packed`
   0. The store is verified after packing a store pack: a failure is
   `store/corrupt` (exit 5) and the loose files are already gone (`stores unpack`
   writes them back). Data: `{:path :scope :packed :pack :bytes :kept-loose
   :skipped-damaged :dropped-damaged :deduplicated :dropped-packs :packs
   :checked :indexes-repaired}`."
  [_args opts {:keys [env cwd]}]
  (let [path (cli-store/resolve-store opts env cwd)
        {:keys [backend descriptor]} (cli-store/open! path)
        max-bytes (checked-max-bytes opts)
        cache? (:cache opts)
        r (if cache?
            (fs-store/pack-cache! backend (cond-> {} max-bytes (assoc :max-bytes max-bytes)))
            (fs-store/pack! backend (cond-> {} max-bytes (assoc :max-bytes max-bytes))))
        reopened (cli-store/open! path)
        v (when-not cache? (store/verify (:backend reopened) (:store reopened)))]
    (if (or cache? (:ok? v))
      (out/ok "stores pack"
              {:store (store-ref descriptor path)
               :revision (:head (:store reopened))
               :data (cond-> {:path path :scope (:scope r) :packed (:packed r) :pack (:pack r)
                              :bytes (:bytes r) :kept-loose (:kept-loose r)
                              :skipped-damaged (:skipped-damaged r)
                              :dropped-damaged (:dropped-damaged r)
                              :deduplicated (:deduplicated r)
                              :dropped-packs (:dropped-packs r)
                              :packs (if cache?
                                       (:packs r)
                                       (:packs (fs-store/pack-stats (:backend reopened))))
                              :indexes-repaired (:indexes-repaired r)}
                       (not cache?) (assoc :checked (:checked v)))})
      (out/failure :store/corrupt
                   (str (count (:bad v)) " problem(s) after packing " path
                        "; the loose copies are already removed: run `stores unpack` to write them back")
                   {:path path :pack (:pack r) :checked (:checked v) :bad (:bad v)}
                   5))))

(defn unpack
  "`stores unpack`: writes every packed object back to `objects/` and removes the
   pack (`kimun.host.fs-store/unpack!`; docs/v2/10 §11.4).
   The store is verified after unpacking: a failure is `store/corrupt` (exit 5).
   Data: `{:path :packs :unpacked :checked}`."
  [_args opts {:keys [env cwd]}]
  (let [path (cli-store/resolve-store opts env cwd)
        {:keys [backend descriptor]} (cli-store/open! path)
        r (fs-store/unpack! backend)
        reopened (cli-store/open! path)
        v (store/verify (:backend reopened) (:store reopened))]
    (if (:ok? v)
      (out/ok "stores unpack"
              {:store (store-ref descriptor path)
               :revision (:head (:store reopened))
               :data {:path path :packs (:packs r) :unpacked (:unpacked r) :checked (:checked v)}})
      (out/failure :store/corrupt
                   (str (count (:bad v)) " problem(s) after unpacking " path)
                   {:path path :checked (:checked v) :bad (:bad v)}
                   5))))

(defn show
  "`stores show`: descriptor and heads without replaying the log:
   `{:name :path :hash-alg :links :head :capabilities}`."
  [_args opts {:keys [env cwd]}]
  (let [dir (cli-store/resolve-store opts env cwd)]
    (when (or (nil? dir) (not (cli-store/store? dir)))
      (cli-store/open! dir))
    (let [b (cli-store/backend dir)
          descriptor (store/read-descriptor b)
          heads (store/read-heads b)]
      (out/ok "stores show" {:store (store-ref descriptor (str dir))
                             :revision (:head heads)
                             :data {:name (:name descriptor)
                                    :path (str dir)
                                    :hash-alg (:hash-alg descriptor)
                                    :links (vec (or (:links descriptor) []))
                                    :head (:head heads)
                                    :capabilities (:capabilities descriptor)}}))))

(defn- need-args
  "Raises the usage failure when `args` has not exactly `n` tokens."
  [command usage args n]
  (when (not= n (count args))
    (err/raise :cli/usage (str "usage: kimun stores " usage) {:command command})))

(defn link
  "`stores link NAME PATH`: links the current store to the store at PATH (a
   `.kimun` or a project directory) by NAME, persisting a path relative to
   this store. Data: `{:link {:name :path} :resolved}`."
  [args opts {:keys [env cwd]}]
  (need-args "stores link" "link NAME PATH" args 2)
  (let [dir (cli-store/resolve-store opts env cwd)
        {:keys [path descriptor store]} (cli-store/open! dir)
        l (cli-store/link! path (first args) (second args) cwd)]
    (out/ok "stores link" {:store (store-ref descriptor path)
                           :revision (:head store)
                           :data {:link l
                                  :resolved (:resolved (first (filter #(= (:name l) (:name %)) (cli-store/links path))))}})))

(defn unlink
  "`stores unlink NAME`: removes the link NAME. Data: `{:unlinked {:name :path}}`."
  [args opts {:keys [env cwd]}]
  (need-args "stores unlink" "unlink NAME" args 1)
  (let [{:keys [path descriptor store]} (cli-store/open! (cli-store/resolve-store opts env cwd))]
    (out/ok "stores unlink" {:store (store-ref descriptor path)
                             :revision (:head store)
                             :data {:unlinked (cli-store/unlink! path (first args))}})))

(defn links
  "`stores links`: lists the links with their status. Data:
   `{:links [{:name :path :resolved :status}]}`; broken links are warnings."
  [_args opts {:keys [env cwd]}]
  (let [{:keys [path descriptor store]} (cli-store/open! (cli-store/resolve-store opts env cwd))
        report (cli-store/links path)]
    (out/ok "stores links" {:store (store-ref descriptor path)
                            :revision (:head store)
                            :data {:links report}
                            :warnings (mapv link-warning (remove #(= :ok (:status %)) report))})))

(def spec
  "Options of the `stores` group (babashka.cli spec, merged by `kimun.cli.main`)."
  {:name {:ref "NAME" :desc "Store name (stores init)"}
   :content {:ref "MODE" :desc "Content mode of the store: by-reference (default) or inline (stores init)"}
   :apply {:desc "Write what the comparison found (stores reconcile)" :coerce :boolean}
   :since {:ref "COMMIT" :desc "Commit to compare the working tree with (stores changed; default: the pointer). `stores seen --set COMMIT` reuses the shared --set option of docs"}
   :full {:desc "Open every tracked document instead of only what git says changed since the pointer (stores sense, health, heartbeat)" :coerce :boolean}
   :once {:desc "Make the single observation explicit (stores sense; no daemon)" :coerce :boolean}
   :ids {:desc "List the id, class and bytes of every object (stores unreachable)" :coerce :boolean}
   :max-bytes {:ref "N" :desc "Largest loose object, in bytes, that `stores pack` moves into a pack (default 4096, at most 16777216)"}
   :cache {:desc "Pack the derived cache (cache/objects into cache/packs), not the store (stores pack)" :coerce :boolean}
   :interval {:ref "SECONDS" :desc "Seconds between observations, decimals allowed (stores heartbeat; default 30, minimum 0.1)"}
   :beats {:ref "N" :desc "Stop after N observations (stores heartbeat; default: until interrupted)"}})

(def commands
  "Subcommand name → command fn."
  {"init" init "check" check "verify" verify "show" show
   "link" link "unlink" unlink "links" links
   "unreachable" unreachable "pack" pack "unpack" unpack "health" health "sense" sense "changed" changed "seen" seen "heartbeat" heartbeat "update" update-store "reconcile" reconcile})

(defn dispatch
  "Runs subcommand `sub` of the `stores` group (`args` are the positional tokens);
   an unknown or missing subcommand is the failure `cli/usage` listing the
   known ones."
  [sub args opts ctx]
  (if-let [f (get commands sub)]
    (f (vec args) opts ctx)
    (out/failure :cli/usage
                 (str (if sub (str "unknown subcommand `stores " sub "`") "missing subcommand for `stores`")
                      "; expected one of: " (str/join ", " (sort (keys commands))))
                 {:group "stores" :subcommand sub :known (vec (sort (keys commands)))}
                 6)))
