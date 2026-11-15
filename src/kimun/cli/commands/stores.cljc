(ns kimun.cli.commands.stores
  "The `kimun stores` group (docs/v2/06 §3): `init`, `check`, `health`,
   `sense`, `heartbeat`, `update`, `reconcile`, `verify`, `unreachable`, `pack`,
   `unpack`, `upgrade`, `show`, and the federation commands `link`, `unlink` and
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
            [kimun.surface.diagnostics :as diag]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.heartbeat :as heartbeat]
            [kimun.surface.signals :as signals]
            [kimun.host.default :as host]
            [kimun.host.fs-store :as fs-store]
            [kimun.kernel.err :as err]
            [kimun.kernel.plan :as plan]
            [kimun.kernel.revision :as revision]
            [kimun.kernel.store :as store]
            [kimun.kernel.upgrade :as upgrade]))

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
   `:routes-root`, `:routes-schema` (2 = Merkle tree by path, 1 = the flat map
   of a store written before it, which `stores upgrade` moves to 2),
   `:route-count`, `:routes-complete?`, `:routes-missing`,
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
  (let [{:keys [descriptor store path] :as opened} (cli-store/open! (cli-store/resolve-store opts env cwd))
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
                        :routes-schema (revision/route-schema store)
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
          warnings (-> (mapv link-warning (remove #(= :ok (:status %)) link-report))
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
  [command dir]
  (let [{:keys [descriptor store path] :as opened} (cli-store/open! dir)
        report (diagnose opened)
        signals (signals/from-report report)]
    (out/ok command {:store (store-ref descriptor path)
                     :revision (:head store)
                     :data {:observed-at (docs/timestamp)
                            :tracked (:checked report)
                            :valid (:valid report)
                            :signals signals
                            :counts (signals/counts signals)
                            :summary (signals/summary signals)}})))

(defn sense
  "`stores sense [--once]`: one read-only observation of tracked documents.
   Reuses the existing diagnostics report and emits structured signals
   (`kimun.surface.signals`); it writes nothing. `--once` is accepted to make
   the single observation explicit; this command never loops (the loop is
   `stores heartbeat`). Data: `{:mode :once :observed-at :tracked :valid :signals
   :counts :summary}`. `:observed-at` is the wall-clock of this response only;
   it is never written to the store as an event."
  [_args opts {:keys [env cwd]}]
  (update (observe "stores sense" (cli-store/resolve-store opts env cwd))
          :data #(merge (array-map :mode :once) %)))

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
      (let [r (heartbeat/run {:observe #(errors/wrap (fn [] (observe "stores heartbeat" dir)))
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
   (`:next` is `inspect: …`). Each finding is also a warning."
  [_args opts {:keys [env cwd]}]
  (let [{:keys [descriptor store path] :as opened} (cli-store/open! (cli-store/resolve-store opts env cwd))
        report (diagnose opened)]
    (out/ok "stores health" {:store (store-ref descriptor path)
                             :revision (:head store)
                             :data (assoc (diag/health report) :summary (:summary report))
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
                                                          {:actor (:actor opts) :timestamp ts :operation "stores update"})
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
   - `:held` `{:count :bytes :by-class}`: resolved plans a committed log line
     names; without them the log cannot be replayed, they are not candidates.
   - `:damaged` `[{:id :reason}]`: unreachable objects that do not hash to
     their name or do not read; only reported.
   - `:stable?`: false when the heads moved while the inventory was taken.
   - `:uncommitted-tail` when the log ends with a line the heads do not record.
   - with `--ids`, `:ids {:candidates [{:id :class :root? :bytes}] :held […]}`.

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
                            :held (tally backend (:held r))
                            :damaged (:damaged r)
                            :stable? (:stable? r)}
                     tail (assoc :uncommitted-tail (select-keys tail [:id :revision :torn-line]))
                     (:ids opts) (assoc :ids {:candidates (mapv size (:candidates r))
                                              :held (mapv size (:held r))}))
             :warnings (if (:stable? r)
                         []
                         [{:type "unreachable/unstable"
                           :message "the heads moved while the inventory was taken; run it again"}])})))

(def ^:private max-pack-bytes
  "The largest `stores pack --max-bytes` accepts: a pack record is held in
   memory while it is written, so the option has a ceiling."
  16777216)

(defn- pack-only!
  "Raises :store/error unless `descriptor` is a format-4 store: packs are part of
   the format-4 layout (docs/v2/10 §11.4)."
  [descriptor path what]
  (when-not (= 4 (:format-version descriptor))
    (err/raise :store/error
               (str what " needs a format-4 store; " path " is format " (:format-version descriptor))
               {:path path :format-version (:format-version descriptor)})))

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
  "`stores pack [--max-bytes N]`: moves the loose objects smaller than N bytes
   (default 4096) into a new pack under `packs/` and removes the loose copies
   (`kimun.host.fs-store/pack!`; docs/v2/10 §11.4). Explicit and never part of a
   commit; only a format-4 store. It also repairs the `.idx` of an existing pack
   that is missing or stale (readers rebuild in memory and never write), so
   `:indexes-repaired` can be positive with `:packed` 0. The store is verified
   after packing: a failure is `store/corrupt` (exit 5) and the loose files are
   already gone (`stores unpack` writes them back). Data: `{:path :packed :pack
   :bytes :kept-loose :skipped-damaged :packs :checked :indexes-repaired}`."
  [_args opts {:keys [env cwd]}]
  (let [path (cli-store/resolve-store opts env cwd)
        {:keys [backend descriptor]} (cli-store/open! path)
        _ (pack-only! descriptor path "stores pack")
        max-bytes (checked-max-bytes opts)
        r (fs-store/pack! backend (cond-> {} max-bytes (assoc :max-bytes max-bytes)))
        reopened (cli-store/open! path)
        v (store/verify (:backend reopened) (:store reopened))]
    (if (:ok? v)
      (out/ok "stores pack"
              {:store (store-ref descriptor path)
               :revision (:head (:store reopened))
               :data {:path path :packed (:packed r) :pack (:pack r) :bytes (:bytes r)
                      :kept-loose (:kept-loose r) :skipped-damaged (:skipped-damaged r)
                      :packs (:packs (fs-store/pack-stats (:backend reopened)))
                      :checked (:checked v) :indexes-repaired (:indexes-repaired r)}})
      (out/failure :store/corrupt
                   (str (count (:bad v)) " problem(s) after packing " path
                        "; the loose copies are already removed: run `stores unpack` to write them back")
                   {:path path :pack (:pack r) :checked (:checked v) :bad (:bad v)}
                   5))))

(defn unpack
  "`stores unpack`: writes every packed object back to `objects/` and removes the
   pack (`kimun.host.fs-store/unpack!`; docs/v2/10 §11.4). Only a format-4 store.
   The store is verified after unpacking: a failure is `store/corrupt` (exit 5).
   Data: `{:path :packs :unpacked :checked}`."
  [_args opts {:keys [env cwd]}]
  (let [path (cli-store/resolve-store opts env cwd)
        {:keys [backend descriptor]} (cli-store/open! path)
        _ (pack-only! descriptor path "stores unpack")
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

(def ^:private layout
  "Entries of a store directory that the upgrade rebuilds, or drops: the
   derived index is keyed by revision ids that the upgrade replaces
   (`rebuild-indexes` writes it again)."
  #{"objects" "log.edn" "heads.edn" "store.edn" "lock" "index"})

(defn- stamp [] (str/replace (docs/timestamp) #"[^0-9]" ""))

(defn- upgrade-routes
  "The data of `stores upgrade` on the format-4 store at `dir` (backend `b`):
   moves its route map from schema 1 to schema 2
   (`kimun.kernel.store/upgrade-routes!`), then rehashes every reachable
   object. When that check fails the previous heads and store.edn are put
   back and the failure is `store/corrupt`."
  [b dir]
  (let [descriptor (store/read-descriptor b)
        heads (store/read-heads b)
        t0 (System/nanoTime)
        r (store/upgrade-routes! b host/host)]
    (if-not (:upgraded r)
      {:upgraded false :format-version 4 :route-schema (:route-schema r) :path dir}
      (let [v (try (store/verify b (store/open b host/host))
                   (catch Exception e {:ok? false :bad [{:reason :open-failed :message (ex-message e)}]}))]
        (when-not (:ok? v)
          (when (:routes-root r) (store/swap-heads! b (store/read-heads b) heads))
          (store/write-descriptor! b descriptor)
          (err/raise :store/corrupt "the store did not verify after the route upgrade; the previous heads were restored"
                     {:path dir :bad (:bad v)}))
        (cond-> {:upgraded true :format-version 4 :path dir
                 :from-route-schema 1 :route-schema 2 :routes (:routes r)
                 :checked (:checked v)
                 :seconds (/ (Math/round (/ (- (System/nanoTime) t0) 1e7)) 100.0)}
          (:routes-root r) (assoc :routes-root-before (:routes-root-before r)
                                  :routes-root (:routes-root r)
                                  :route-nodes (:nodes r)))))))

(defn upgrade-store
  "`stores upgrade`: moves the store to the current format. A format-1 store is
   rewritten as format 2 (`kimun.kernel.upgrade/upgrade!`: every transaction
   re-committed in log order through the format-2 engine, then checked and
   fully verified). The new store is built in the sibling directory
   `<store>.upgrade-<stamp>`; the original is untouched until the new one
   verifies, then it is renamed to `<store>.format-1-<stamp>` (a backup the user
   may delete) and the new one takes its place. Files of the store directory
   other than the layout are carried over (not `index/`, derived and keyed by
   the old revision ids). `upgrade-format-1.edn` in the new store maps every old
   revision and transaction id to its new id. A format-2 store is converted to
   format 3 in place: its log lines `{:id :plan :revision}` become
   `{:id :revision}` (the resolved plan is already in the CAS, invariant 17)
   and `store.edn` records `:format-version 3`; nothing else moves and the
   whole store is verified after the rewrite, restoring the previous files if
   it fails. For a `:git :ignore` store the stronger path is
   `include build --rebuild`, which reconstructs the store from the files and
   drops the verbatim source copies. A format-3 store is not rewritten in
   place: format 4 registers a by-reference document as cache (docs/v2/14
   §18.6), which needs the files, so the upgrade reports
   `:needs-rebuild true` and names `kimun include build --rebuild`. A format-4
   store keeps its format; when its route map is of schema 1 (one flat map
   object, every store written before the route tree) it is moved to schema 2,
   the Merkle tree by path (docs/v2/10 §11.2): same entries, same documents,
   same head revision, no transaction added, and a route root that changes
   because it is computed over a tree. The data then carries
   `{:from-route-schema 1 :route-schema 2 :routes :routes-root-before
   :routes-root :route-nodes :checked :seconds}`. A store already of schema 2
   is left as is (`:upgraded false :route-schema 2`). When a writer commits to the
   original meanwhile the upgrade is abandoned with `store/stale` (exit 4).
   Data: `{:upgraded
   :format-version :path}` plus, when upgraded, `{:transactions :head-before
   :backup :map :dropped-tail :checked :seconds}` (format 1) or
   `{:transactions :dropped-plans :checked :seconds}` (format 2→3)."
  [_args opts {:keys [env cwd]}]
  (let [dir0 (cli-store/resolve-store opts env cwd)
        _ (when (or (nil? dir0) (not (cli-store/store? dir0))) (cli-store/open! dir0))
        dir (str (fs/absolutize dir0))
        b (cli-store/backend dir)
        descriptor (store/read-descriptor b)
        heads-file (str (fs/path dir "heads.edn"))
        heads-text #(when (fs/exists? heads-file) (slurp heads-file))]
    (cond
      (= 2 (:format-version descriptor 1))
      (let [log-file (str (fs/path dir "log.edn"))
            desc-file (str (fs/path dir "store.edn"))
            log (vec (store/read-log b))
            bad (some #(when-not (store/transaction? %) %) log)
            _ (when bad (err/raise :store/corrupt-log "cannot upgrade: unreadable log line" {:line (or (:torn-line bad) bad)}))
            dropped (count (filter :plan log))
            log-text (if (seq log) (str (str/join "\n" (map #(pr-str (dissoc % :plan)) log)) "\n") "")
            desc-text (pr-str (assoc descriptor :format-version 3))
            old-log (when (fs/exists? log-file) (slurp log-file))
            old-desc (slurp desc-file)
            t0 (System/nanoTime)]
        (spit log-file log-text)
        (spit desc-file desc-text)
        (let [v (try (store/verify-full b host/host)
                     (catch Exception e {:ok? false :bad [{:reason :replay-failed :message (ex-message e)}]}))]
          (if (:ok? v)
            (out/ok "stores upgrade" {:store (store-ref (store/read-descriptor b) dir)
                                       :revision (:head (store/read-heads b))
                                       :data {:upgraded true :from-format 2 :format-version 3 :path dir
                                              :transactions (count log) :dropped-plans dropped
                                              :checked (:checked v)
                                              :seconds (/ (Math/round (/ (- (System/nanoTime) t0) 1e7)) 100.0)}})
            (do (when old-log (spit log-file old-log))
                (spit desc-file old-desc)
                (err/raise :store/error "the format-3 store does not verify; the log and descriptor were restored" {:bad (:bad v)})))))

      (= 1 (:format-version descriptor 1))
      (let [t (stamp)
            tmp (str dir ".upgrade-" t)
            backup (str dir ".format-1-" t)
            before (heads-text)
            head-before (:head (store/read-heads b))
            t0 (System/nanoTime)
            r (try (upgrade/upgrade! b (cli-store/backend tmp) host/host)
                   (catch Exception e (fs/delete-tree tmp) (throw e)))]
        (when (not= before (heads-text))
          (fs/delete-tree tmp)
          (err/raise :store/stale (str "the store at " dir " changed while it was being upgraded; run the upgrade again") {:path dir}))
        (doseq [f (fs/list-dir dir)
                :let [n (str (fs/file-name f))]
                :when (not (or (layout n) (str/includes? n ".tmp-")))]
          (if (fs/directory? f) (fs/copy-tree f (fs/path tmp n)) (fs/copy f (fs/path tmp n))))
        (spit (str (fs/path tmp "upgrade-format-1.edn"))
              (pr-str {:from-format 1 :to-format 2 :head-before head-before :head (:head r)
                       :revisions (into (sorted-map) (:revisions r)) :transaction-ids (into (sorted-map) (:transaction-ids r))}))
        (fs/move dir backup {:atomic-move true})
        (fs/move tmp dir {:atomic-move true})
        (out/ok "stores upgrade" {:store (store-ref descriptor dir)
                                  :revision (:head r)
                                  :data {:upgraded true :format-version 2 :path dir
                                         :transactions (:transactions r)
                                         :head-before head-before
                                         :backup backup
                                         :map (str (fs/path dir "upgrade-format-1.edn"))
                                         :dropped-tail (some-> (:dropped-tail r) (select-keys [:id :revision :torn-line]))
                                         :checked (get-in r [:verify :checked])
                                         :seconds (/ (Math/round (/ (- (System/nanoTime) t0) 1e7)) 100.0)}}))

      :else
      (out/ok "stores upgrade" {:store (store-ref descriptor dir)
                                :revision (:head (store/read-heads b))
                                :data (if (= 3 (:format-version descriptor 1))
                                        ;; format 3 → 4 registers a by-reference document as cache (no tree
                                        ;; object, no block/text node in the CAS): it requires the files, so
                                        ;; it is a rebuild, not an in-place rewrite (docs/v2/14 §18.6)
                                        {:upgraded false :format-version 3 :path dir
                                         :needs-rebuild true
                                         :action "kimun include build --rebuild"
                                         :message "format 3 → 4 registers documents with a file as cache; run `kimun include build --rebuild` to rebuild from the files"}
                                        (upgrade-routes b dir))}))))

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
   :once {:desc "Make the single observation explicit (stores sense; no daemon)" :coerce :boolean}
   :ids {:desc "List the id, class and bytes of every object (stores unreachable)" :coerce :boolean}
   :max-bytes {:ref "N" :desc "Largest loose object, in bytes, that `stores pack` moves into a pack (default 4096, at most 16777216)"}
   :interval {:ref "SECONDS" :desc "Seconds between observations, decimals allowed (stores heartbeat; default 30, minimum 0.1)"}
   :beats {:ref "N" :desc "Stop after N observations (stores heartbeat; default: until interrupted)"}})

(def commands
  "Subcommand name → command fn."
  {"init" init "check" check "verify" verify "upgrade" upgrade-store "show" show
   "link" link "unlink" unlink "links" links
   "unreachable" unreachable "pack" pack "unpack" unpack "health" health "sense" sense "heartbeat" heartbeat "update" update-store "reconcile" reconcile})

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
