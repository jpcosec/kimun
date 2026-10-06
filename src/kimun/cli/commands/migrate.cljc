(ns kimun.cli.commands.migrate
  "The `migrate` command: brings an sldb v1 store (`.sldb/`) into a kimun
   store in one process and one open — every model is defined through the
   code path of `models define --from-python`, every document is tracked on
   the store value the previous commit returned. Re-running is idempotent and
   every item ends in exactly one bucket (`kimun.surface.migrate/report`)."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [kimun.cli.commands.docs :as docs]
            [kimun.cli.commands.models :as models]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.migrate :as migrate]
            [kimun.surface.models.descriptor :as descriptor]
            [kimun.surface.models.edit :as edit]
            [kimun.surface.models.extract :as mextract]
            [kimun.surface.models.render :as mrender]
            [kimun.surface.models.store :as mstore]
            [sldb.host.default :as host]
            [sldb.kernel.err :as err]
            [sldb.kernel.ports :as ports]
            [sldb.kernel.store :as store]))

(def usage "Usage line of the command." "kimun migrate --from-v1 [PATH] [--pythonpath P] [--dry-run] [--only MODEL] [--limit N] [--force]")

(defn- fail [item reason e] (assoc item :bucket :failed :reason reason :message (ex-message e)))
(defn- ulid [] (ports/ulid (:ids host/host)))
(defn- rel [root p] (str (fs/relativize (fs/path root) (fs/path p))))

(defn- source-fns [dir]
  {:read-file (fn [r] (let [p (fs/path dir r)] (when (fs/regular-file? p) (slurp (str p)))))
   :list-dir (fn [r] (let [p (fs/path dir r)] (if (fs/directory? p) (map #(str (fs/file-name %)) (fs/list-dir p)) [])))})

(defn- define-model
  "One model: `[state' item]`. Exports the descriptor, validates it as
   `models define` does and commits the define plan on `store`."
  [{:keys [backend store descs] :as state} {:keys [name model-ref] :as m} {:keys [export dry-run actor]}]
  (let [item {:name name :model-ref model-ref}
        d (try (export model-ref) (catch Exception e (fail item :model-not-importable e)))]
    (if (:bucket d)
      [state d]
      (try
        (edit/check-template! (descriptor/validate! d))
        (let [r (if backend (mstore/define-plan host/host store d {:actor actor :timestamp (docs/timestamp) :tree-id (ulid)}) {:version 1})
              applied (when (and (:plan r) (not dry-run)) (store/commit! backend store (:plan r)))
              stored (:model/name d)]
          [(cond-> (assoc-in state [:descs name] {:descriptor d :stored stored}) applied (assoc :store (:store applied)))
           (assoc item :bucket (if (:unchanged r) :skipped :migrated) :model stored :version (:version r))])
        (catch Exception e
          [state (fail item (if (= "models" (some-> (:type (ex-data e)) namespace)) :descriptor-rejected :commit-rejected) e)])))))

(defn- migrate-document
  "One document: `[state' item]`. `known` maps the names tracked at open to
   their document maps (idempotency without rescanning the store)."
  [{:keys [backend store descs known] :as state} {:keys [name model path] :as doc} {:keys [v1-root root dry-run force actor]}]
  (let [item (cond-> {:name name :model model} path (assoc :path (rel root (fs/path v1-root path))))
        {:keys [descriptor stored]} (get descs model)
        file (when path (fs/path v1-root path))
        old (get known name)]
    (cond
      (:unreadable? doc) [state (assoc item :bucket :failed :reason :entry-unreadable :message (str "unreadable entry " (:entry doc)))]
      (nil? descriptor) [state (assoc item :bucket :failed :reason :model-unavailable :message (str "model " model " was not migrated"))]
      (not (fs/regular-file? file)) [state (assoc item :bucket :failed :reason :file-missing :message (str "no such file: " path))]
      :else
      (let [text (slurp (str file)) h (tdocs/text-hash host/host text)]
        (cond
          (and old (not= stored (:model old)))
          [state (assoc item :bucket :failed :reason :name-collision :message (str "`" name "` is already a document of " (:model old)))]
          (and old (= h (tdocs/text-hash host/host (:source old)))) [state (assoc item :bucket :skipped :source-hash h)]
          :else
          (let [payload (try (mextract/extract descriptor text) (catch Exception e (fail item :no-extract e)))]
            (cond
              (:bucket payload) [state payload]
              (and (not force) (not= (str/trim (try (mrender/render descriptor payload) (catch Exception _ ""))) (str/trim text)))
              [state (assoc item :bucket :failed :reason :no-round-trip :message "rendering what was extracted gives a different text; pass --force to migrate it anyway")]
              dry-run [state (assoc item :bucket :migrated :source-hash h :change (if old :updated :tracked))]
              :else
              (try
                (let [o {:actor actor :timestamp (docs/timestamp) :operation "migrate"}
                      plan (:plan (if old
                                    (tdocs/update-plan host/host store name text o)
                                    (tdocs/track-plan host/host store stored name text (assoc o :doc (ulid) :tree-id (ulid) :path (:path item)))))
                      r (when plan (store/commit! backend store plan))]
                  [(cond-> (assoc-in state [:known name] {:model stored :source text}) r (assoc :store (:store r)))
                   (assoc item :bucket :migrated :source-hash h :change (if old :updated :tracked)
                          :content-hash (tdocs/content-hash host/host payload))])
                (catch Exception e [state (fail item :commit-rejected e)])))))))))

(defn- link-store
  "One v1 `stores` entry: linked when its target holds a kimun store."
  [dir v1-root {:keys [name path] :as l} dry-run]
  (let [item {:name name :v1-path path}
        target (when path (cli-store/normalize-store-path (str (fs/path v1-root path))))]
    (cond (or (nil? name) (nil? path)) (assoc item :bucket :failed :reason :unreadable-link :message (str "unreadable stores entry " (pr-str l)))
          (not (cli-store/store? target)) (assoc item :bucket :failed :reason :no-kimun-store :message (str "no kimun store at " path ": migrate it, then `stores link`"))
          (some #(= name (:name %)) (:links (store/read-descriptor (cli-store/backend dir)))) (assoc item :bucket :skipped)
          dry-run (assoc item :bucket :migrated)
          :else (try (assoc item :bucket :migrated :path (:path (cli-store/link! dir name target (str v1-root))))
                     (catch Exception e (fail item :link-rejected e))))))

(defn- fold [f state items ctx]
  (reduce (fn [[st out] i] (let [[st' item] (f st i ctx)] [st' (conj out item)])) [state []] items))

(defn run!
  "Migrates the v1 store at `v1-dir` into the kimun store at `dir` (created
   when absent, except on `:dry-run`). `opts`: `:export` (`(fn [model-ref])
   descriptor`), `:only`, `:limit`, `:force`, `:dry-run`, `:actor`, `:name`.
   Returns `{:path :descriptor :head :report :elapsed-ms}`; raises
   `:migrate/not-v1-store`."
  [dir v1-dir {:keys [dry-run actor] :as opts}]
  (let [plan (migrate/select (migrate/read-v1 (source-fns v1-dir)) opts)
        t0 (System/currentTimeMillis)
        dir (str (fs/absolutize dir))
        _ (when-not (or dry-run (cli-store/store? dir)) (cli-store/init! dir {:name (:name opts) :actor actor}))
        {:keys [backend store] d :descriptor} (when (cli-store/store? dir) (cli-store/open! dir))
        ctx (assoc opts :v1-root (str (fs/parent (fs/absolutize v1-dir))) :root (str (fs/parent dir)))
        known (into {} (map (juxt :name #(select-keys % [:model :source]))) (when store (tdocs/documents store)))
        store (or store {:head nil})
        [s1 ms] (fold define-model {:backend backend :store store :descs {} :known known} (:models plan) ctx)
        [s2 ds] (fold migrate-document s1 (:documents plan) ctx)
        ls (if (cli-store/store? dir) (mapv #(link-store dir (:v1-root ctx) % dry-run) (:links plan)) [])]
    {:path dir :descriptor d :head (:head (:store s2))
     :report (migrate/report {:models ms :documents ds :links ls})
     :elapsed-ms (- (System/currentTimeMillis) t0)}))

(defn migrate
  "`migrate --from-v1 [PATH]`: PATH (default `.sldb` beside the kimun store)
   is a v1 store directory or the project that holds it. Data: the report of
   `kimun.surface.migrate/report` plus `:from`, `:dry-run?`, `:elapsed-ms`.
   Fails `migrate/not-v1-store` (exit 5) when PATH is not one, `cli/usage`
   without `--from-v1`, and `migrate/nothing-migrated` (exit 5, the report in
   its data) when there was work and no item was migrated or already there."
  [args opts {:keys [env cwd] :as ctx}]
  (when-not (:from-v1 opts)
    (err/raise :cli/usage (str "`migrate` needs a source: " usage) {:group "migrate"}))
  (let [dir (or (cli-store/resolve-store opts env cwd) (str (fs/path (fs/absolutize cwd) cli-store/dirname)))
        given (or (when (string? (:from-v1 opts)) (:from-v1 opts)) (first args))
        p (if given (fs/absolutize (fs/path cwd given)) (fs/path (fs/parent (fs/absolutize dir)) ".sldb"))
        v1-dir (str (if (fs/exists? (fs/path p migrate/index-path)) p (fs/path p ".sldb")))
        v1-root (str (fs/parent v1-dir))
        pp (str/join ":" (remove str/blank? [v1-root (str (fs/path v1-root "src")) (:pythonpath opts)]))
        export (or (:exporter ctx) #(models/from-python % {:pythonpath pp :env env :cwd v1-root}))
        limit (some-> (:limit opts) str parse-long)
        r (run! dir v1-dir {:export export :only (:only opts) :limit limit :force (:force opts) :dry-run (:dry-run opts)
                            :actor (or (:actor opts) (get env "USER") "kimun") :name (:name opts)})
        rep (:report r)
        data (assoc rep :from (rel (fs/parent (:path r)) v1-dir) :dry-run? (boolean (:dry-run opts)) :elapsed-ms (:elapsed-ms r))
        work (+ (get-in rep [:models :total]) (get-in rep [:documents :total]))
        landed (reduce + (for [k [:models :documents] b [:migrated :skipped]] (get-in rep [k b])))]
    (if (and (pos? work) (zero? landed))
      (out/failure :migrate/nothing-migrated "no model or document of the v1 store could be migrated" data 5)
      (out/ok "migrate" {:store (when (:descriptor r) {:name (:name (:descriptor r)) :path (:path r)})
                         :revision (:head r) :data data}))))

(def spec
  "Options of the `migrate` group (babashka.cli spec, merged by `kimun.cli.main`)."
  {:from-v1 {:ref "[PATH]" :desc "Migrate the sldb v1 store at PATH (default: .sldb beside the kimun store)"}
   :pythonpath {:ref "P" :desc "Extra PYTHONPATH for the model export (the v1 project root and its src/ are always on it)"}
   :dry-run {:desc "Classify every item without writing" :coerce :boolean}
   :only {:ref "MODEL" :desc "Only this v1 model and its documents"}
   :limit {:ref "N" :desc "At most N documents"}
   :force {:desc "Migrate documents that do not round-trip under their model" :coerce :boolean}})

(def commands
  "Subcommand name → command fn; `migrate` has none, the group runs `migrate`."
  {})

(defn dispatch
  "Runs the `migrate` group: it has no subcommand, so `sub` (nil or the first
   positional token) is taken as the PATH of `--from-v1`."
  [sub args opts ctx]
  (migrate (vec (remove nil? (cons sub args))) opts ctx))
