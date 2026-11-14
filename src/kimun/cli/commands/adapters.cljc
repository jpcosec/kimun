(ns kimun.cli.commands.adapters
  "The `kimun adapters` group (docs/v2/11; v1 `sldb selfdoc`): `scan`, `sync`,
   `check` and `list` over surface adapters. An adapter is an out-of-process
   tool (protocol 1: `<adapter> ROOT [--system NAME] [options]` prints facts as
   one JSON object) — a built-in under `python/kimun/adapters/` or any
   executable given by path. This namespace is the I/O side: it runs the
   adapter, hands its output to the pure planner (`kimun.surface.adapters`)
   and applies the plan through the commands of `models define` and `docs
   create|update|untrack` — every write is one of their normal commits.
   Tokens after `ADAPTER PATH` (given after `--` on the command line) are
   passed to the adapter verbatim. Every command is `(fn [args opts ctx]
   envelope)`."
  (:refer-clojure :exclude [sync])
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [kimun.cli.commands.docs :as docs]
            [kimun.cli.commands.models :as models]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.adapters :as planner]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.models.extract :as mextract]
            [kimun.surface.models.store :as mstore]
            [kimun.host.default :as host]
            [kimun.kernel.err :as err]))

(def usage
  "Usage lines of the group, for the CLI help."
  ["kimun adapters list                                   built-in adapters and whether their runtime is present"
   "kimun adapters scan ADAPTER PATH --system S [--factory M:A] [--language L] [--output DIR] [--architecture-spec FILE] [-- adapter options]   planned documents; writes nothing, needs no store"
   "kimun adapters sync ADAPTER PATH --system S [...]      define the models if absent, then create/update/untrack so the store matches the code"
   "kimun adapters check ADAPTER PATH --system S [...]     report drift without writing; drift is a failure (exit 1, as v1 `selfdoc check`)"
   "kimun adapters python-scan|python-sync|python-check [--source-root src] [--package P] [--system sldb] [...]   v1 `selfdoc python-*`: adapter python-symbols"
   "kimun adapters selfdoc-scan|selfdoc-sync|selfdoc-check --factory M:A [--pythonpath P] [--include-hidden] [--system sldb] [...]   v1 `selfdoc scan|sync|check`: adapter argparse-cli over the project root"])

(def spec
  "Options of the `adapters` group (babashka.cli spec, merged by
   `kimun.cli.main`). `--output DIR` (default `knowledge`, as v1) and
   `--pythonpath P` are read too but not declared here: `direct`/`graph`/
   `semantic` and `models` already declare those names as string options."
  {:system {:ref "NAME" :desc "Stable lowercase system identifier of the generated documents (adapters; v1 aliases default to sldb)"}
   :factory {:ref "MOD:ATTR" :desc "module:callable returning the CLI parser, passed to a cli adapter (adapters)"}
   :language {:ref "LANG" :desc "Override the language reported by the adapter (adapters)"}
   :architecture-spec {:ref "FILE" :desc "Architecture spec recorded in symbol documents, relative to the project root (adapters)"}
   :source-root {:ref "DIR" :desc "Python root relative to the project root, default src (adapters python-*)"}
   :package {:ref "NAME" :desc "Package prefix for stable ids, passed to the adapter (adapters python-*)"}
   :include-hidden {:desc "Include commands/options with suppressed help (adapters selfdoc-*)" :coerce :boolean}})

(def default-output "Default of `--output`, as v1 `selfdoc`: `knowledge`, relative to the project root." "knowledge")

(def drift-type
  "Error type of `adapters check` on drift. v1 `selfdoc check` exits 1 on drift
   (`return 0 if report.ok else 1`); under `kimun.cli.errors` only the names
   `missing`/`empty` give 1 without registering a type (`:adapters/drift`
   would be 70), so drift is `:adapters/missing` with `:reason \"drift\"`."
  :adapters/missing)

(defn- usage! [sub message]
  (err/raise :cli/usage (str "`adapters " sub "` " message) {:group "adapters" :subcommand sub}))

(defn- python-dir
  "The `python/` directory of kimun: `$KIMUN_PYTHON`, else beside `resources/`
   (as `kimun.cli.commands.models`)."
  [env]
  (or (get env "KIMUN_PYTHON")
      (when-let [r (io/resource "VERSION")]
        (when (= "file" (.getProtocol r))
          (str (fs/path (fs/parent (fs/parent (fs/path (.getPath r)))) "python"))))))

(defn builtins
  "`{name file}` of the built-in adapters: `python/kimun/adapters/*.py`, file
   name underscores → dashes, private (`_x.py`, `__init__.py`) files skipped."
  [env]
  (let [dir (some-> (python-dir env) (fs/path "kimun" "adapters"))]
    (into (sorted-map)
          (when (and dir (fs/directory? dir))
            (for [f (fs/glob dir "*.py") :let [n (fs/file-name f)] :when (not (str/starts-with? n "_"))]
              [(str/replace (str/replace n #"\.py$" "") "_" "-") (str f)])))))

(defn resolve-adapter
  "`{:name :command [argv…] :builtin?}` for token `adapter`: a built-in name
   (run with `$KIMUN_ADAPTER_PYTHON`, default `python3`), else a path to an
   executable (it contains `/` or names a file relative to `cwd`). Anything
   else is `:adapters/missing` (exit 1)."
  [adapter {:keys [env cwd]}]
  (let [known (builtins env) p (fs/absolutize (fs/path cwd adapter))]
    (cond (contains? known adapter)
          {:name adapter :builtin? true :command [(or (get env "KIMUN_ADAPTER_PYTHON") "python3") (known adapter)]}
          (and (or (str/includes? adapter "/") (fs/regular-file? p)) (fs/regular-file? p))
          {:name adapter :builtin? false :command [(str p)]}
          :else (err/raise :adapters/missing (str "unknown adapter `" adapter "`: not a built-in (" (str/join ", " (keys known))
                                                  ") nor an executable file")
                           {:reason "unknown-adapter" :adapter adapter :known (vec (keys known))}))))

(defn- execute
  "Runs `command` + `argv` in `cwd` with the process environment overlaid by
   the string entries of `env` and `PYTHONDONTWRITEBYTECODE=1`; `{:exit :out
   :err}`, exit 127 when the executable cannot be started."
  [command argv {:keys [env cwd]}]
  (try (apply process/shell {:out :string :err :string :continue true :dir (str cwd)
                             :extra-env (assoc (into {} (filter (fn [[k v]] (and (string? k) (string? v)))) env)
                                               "PYTHONDONTWRITEBYTECODE" "1")}
              (concat command argv))
       (catch Exception e {:exit 127 :out "" :err (str "error: " (ex-message e))})))

(defn- error-line [stderr]
  (or (last (filter #(str/starts-with? % "error:") (str/split-lines (str stderr))))
      (last (remove str/blank? (str/split-lines (str stderr)))) "no message on stderr"))

(defn run-adapter
  "Runs the resolved adapter `a` as `<adapter> ROOT [--system S] argv…` and
   returns its output parsed with string keys. Raises `:adapters/runtime`
   (adapter exit 3, or the executable cannot start), `:adapters/failed` (any
   other non-zero exit; stderr in the data, its `error:` line in the message)
   and `:adapters/invalid-output` (stdout is not one JSON object, or it
   violates the contract per `kimun.surface.adapters/validate`)."
  [a root argv ctx]
  (let [{:keys [exit out err]} (execute (:command a) (into [(str root)] argv) ctx)
        data {:adapter (:name a) :exit exit :stderr (str err)}]
    (cond (contains? #{3 127} exit)
          (err/raise :adapters/runtime (str "adapter `" (:name a) "` cannot run: " (error-line err)) data)
          (not (zero? exit))
          (err/raise :adapters/failed (str "adapter `" (:name a) "` failed (exit " exit "): " (error-line err)) data))
    (let [output (try (json/parse-string out) (catch Exception _ nil))
          problems (if (map? output) (vec (planner/validate output)) [{:code :not-json :message "stdout is not one JSON object"}])]
      (when (seq problems)
        (err/raise :adapters/invalid-output
                   (str "adapter `" (:name a) "` printed invalid output: " (:message (first problems))
                        (when (next problems) (str " (+" (dec (count problems)) " more)")))
                   {:adapter (:name a) :problems problems}))
      output)))

(defn- sha256-hex [bytes]
  #?(:clj (apply str (map #(format "%02x" %) (.digest (java.security.MessageDigest/getInstance "SHA-256") bytes)))))

(defn- sha256-text
  "Hex SHA-256 of the UTF-8 bytes of `s`, the `:sha256` function the planner
   needs: the host hasher (as `kimun.surface.docs/text-hash`) when its
   algorithm is sha256, else the platform digest."
  [s]
  (if (str/starts-with? (tdocs/fingerprint host/host "") "sha256:")
    (tdocs/text-hash host/host s)
    (sha256-hex (.getBytes (str s) "UTF-8"))))

(defn architecture-spec
  "v1 `_architecture_spec`: `Not declared.` without `file`, else `<path
   relative to root>; sha256=<hex of the file bytes>`; a file that does not
   exist or lies outside `root` is `:cli/usage`."
  [root file]
  (if (str/blank? (str file))
    "Not declared."
    (let [r (fs/canonicalize root) p (fs/canonicalize (fs/path r file))]
      (when-not (and (fs/starts-with? p r) (fs/regular-file? p))
        (usage! "sync" "--architecture-spec must be an existing file within the project"))
      (str (str/replace (str (fs/relativize r p)) "\\" "/") "; sha256=" (sha256-hex (fs/read-all-bytes p))))))

(defn- open-store [opts {:keys [env cwd]}] (cli-store/open! (cli-store/resolve-store opts env cwd)))
(defn- project-root [store-path] (str (fs/parent (fs/absolutize store-path))))
(defn- strings [m] (into {} (map (fn [[k v]] [(if (keyword? k) (name k) (str k)) v])) m))

(defn- planned
  "Runs the adapter and plans its documents: `{:scope :planned :output}`.
   `root` is the project root (store parent, or the cwd without a store)."
  [sub adapter path argv opts ctx root]
  (let [a (resolve-adapter adapter ctx)
        system (or (:system opts) (usage! sub "needs --system NAME"))
        target (fs/absolutize (fs/path (:cwd ctx) path))
        argv (cond-> (into ["--system" system] argv) (:factory opts) (into ["--factory" (:factory opts)]))
        output (run-adapter a target argv ctx)
        out-dir (str/replace (or (:output opts) default-output) #"/+$" "")
        popts {:system system :output out-dir :factory (:factory opts) :language (:language opts) :sha256 sha256-text
               :architecture-spec (architecture-spec root (:architecture-spec opts))}
        {:keys [documents problems]} (planner/plan-documents output popts)]
    (when (seq problems)
      (err/raise :adapters/invalid-output (str "cannot plan the documents of `" adapter "`: " (:message (first problems)))
                 {:adapter adapter :problems (vec problems)}))
    {:planned (vec documents)
     :scope {:adapter (get output "adapter") :profile (get output "profile")
             :language (or (:language opts) (get output "language")) :system system :output out-dir}
     :facts (count (get output "facts"))}))

(defn- tracked
  "The tracked documents `scope` owns (`kimun.surface.adapters/owned?`):
   `[{:name :model :path :payload :source :fingerprint}]`."
  [store scope]
  (let [models (set (get planner/profile-models (:profile scope)))]
    (vec (for [d (tdocs/documents store) :when (contains? models (:model d))
               :let [full (tdocs/document store (:tree d))
                     t {:name (:name d) :model (:model d) :path (:path d)
                        :source (:source full) :fingerprint (:fingerprint full)
                        :payload (when (map? (:payload full)) (strings (:payload full)))}]
               :when (and (:payload t) (planner/owned? scope t))]
           t))))

(defn- with-payload-file
  "Calls `(f path)` with a temporary JSON file holding `payload`."
  [payload f]
  (let [tmp (str (fs/create-temp-file {:prefix "kimun-adapters-" :suffix ".json"}))]
    (try (spit tmp (json/generate-string payload)) (f tmp) (finally (fs/delete-if-exists tmp)))))

(defn- fail! [envelope]
  (when-not (:ok envelope)
    (err/raise (keyword (get-in envelope [:error :type] "adapters/failed")) (get-in envelope [:error :message] "command failed") (get-in envelope [:error :data] {})))
  envelope)

(defn- ensure-models!
  "Defines the frozen models of `profile` the store lacks, through `models
   define` (a model already defined is left as it is): `{:defined :present}`."
  [profile opts ctx]
  (reduce (fn [acc model]
            (if (mstore/find-model (:store (open-store opts ctx)) model)
              (update acc :present conj model)
              (let [r (or (io/resource (str "kimun/adapters/models/" model ".edn"))
                          (err/raise :adapters/invalid-output (str "no frozen descriptor for model " model) {:model model}))
                    tmp (str (fs/create-temp-file {:prefix "kimun-model-" :suffix ".edn"}))]
                (try (spit tmp (slurp r))
                     (fail! (models/define [] (assoc (select-keys opts [:store :actor :format]) :file tmp) ctx))
                     (finally (fs/delete-if-exists tmp)))
                (update acc :defined conj model))))
          {:defined [] :present []}
          (get planner/profile-models profile)))

(defn- adoptable
  "v1 `_prepare` + the `untracked` branch of `_save_document`: the planned new
   documents whose file already exists and reads as a document of the planned
   model with the planned id — `[{:doc planned :existing payload :previous text
   :changed [parser-owned fields that differ]}]`. The model's stored descriptor
   is used, else the frozen one in memory (nothing is written)."
  [store root creates]
  (vec (for [d creates
             :let [f (fs/path root (:path d))]
             :when (fs/regular-file? f)
             :let [previous (try (slurp (str f)) (catch Exception _ nil))
                   existing (when previous
                              (try (let [desc (or (:descriptor (mstore/find-model store (:model d)))
                                                  (edn/read-string (slurp (io/resource (str "kimun/adapters/models/" (:model d) ".edn")))))
                                         payload (strings (mextract/extract desc previous))]
                                     (when (= (get payload "id") (get (:fields d) "id")) payload))
                                   (catch Exception _ nil)))]
             :when existing]
         {:doc d :existing existing :previous previous
          :changed (vec (sort (filter #(not= (get existing %) (get (:fields d) %)) (keys (:fields d)))))})))

(defn- current-source [root path]
  (let [f (fs/path root path)]
    (when (fs/regular-file? f) (slurp (str f)))))

(defn- matches-tracked-source? [host t disk-text]
  (if (:fingerprint t)
    (= (:fingerprint t) (tdocs/fingerprint host disk-text))
    (= disk-text (:source t))))

(defn- expected-before-image [store root adopt-by-path tracked-by-path d]
  (let [path (:path d)]
    (cond (contains? adopt-by-path path)
          (:previous (get adopt-by-path path))
          (contains? tracked-by-path path)
          (when-some [disk-text (current-source root path)]
            (if (matches-tracked-source? (or (:host store) host/host) (get tracked-by-path path) disk-text)
              disk-text
              :modified-externally))
          :else nil)))

(defn- before-images-for [store root planned adopt tracked-docs]
  (let [adopt-by-path (into {} (map (juxt (comp :path :doc) identity)) adopt)
        tracked-by-path (into {} (map (juxt :path identity)) tracked-docs)]
    (into {} (map (fn [d] [(:path d) (expected-before-image store root adopt-by-path tracked-by-path d)])) planned)))

(defn inspect
  "Plan against the store: `{:plan :report :restore :adopt :before-images
   :source-graph}`; `:restore` are the unchanged documents whose file is gone
   (v1 `missing`), which count as drift; `:before-images` maps each planned
   path to its verified on-disk before-image (nil when absent,
   `:modified-externally` when the file on disk diverged from the tracked
   store); `:source-graph` reports `source-graph-check` on symbol nodes."
  [store root {:keys [planned scope]}]
  (let [tracked-docs (tracked store scope)
        plan (planner/sync-plan planned tracked-docs)
        adopt (adoptable store root (:create plan))
        adopted (set (map (comp :name :doc) adopt))
        before-images (before-images-for store root planned adopt tracked-docs)
        restore (filterv #(not (fs/regular-file? (fs/path root (:path %)))) (:unchanged plan))
        tampered (filterv #(= :modified-externally (get before-images (:path %))) (:unchanged plan))
        report (planner/drift-report plan)
        gone (mapv (fn [d] {:name (:name d) :path (:path d) :model (:model d) :status "missing-file" :fields []}) restore)
        tampered-rows (mapv (fn [d] {:name (:name d) :path (:path d) :model (:model d) :status "modified-externally" :fields []}) tampered)
        actual-nodes (if (and (empty? tracked-docs)
                              (some #(= "PythonSymbolDoc" (:model %)) planned))
                       nil
                       tracked-docs)
        base-sg (planner/source-graph-check planned actual-nodes)
        missing-sym-ids (keep #(when (= "PythonSymbolDoc" (:model %)) (:name %)) restore)
        stale-sym-ids (keep #(when (= "PythonSymbolDoc" (:model %)) (:name %)) tampered)
        source-graph (let [miss (vec (sort (into (set (:missing base-sg)) missing-sym-ids)))
                           st (vec (sort (into (set (:stale base-sg)) stale-sym-ids)))
                           cur? (and (empty? miss) (empty? st) (empty? (:orphan base-sg)))
                           st-kw (cond
                                   cur? :current
                                   (= :missing (:status base-sg)) :missing
                                   (= :unreadable (:status base-sg)) :unreadable
                                   :else :stale)]
                       (assoc base-sg
                              :current? cur?
                              :status st-kw
                              :missing miss
                              :stale st))
        docs-report (into (mapv #(if (contains? adopted (:name %)) (assoc % :status "untracked") %) (:documents report))
                          (concat gone tampered-rows))]
    {:plan plan :restore restore :adopt adopt
     :before-images before-images
     :source-graph source-graph
     :report {:drift? (boolean (or (:drift? report)
                                   (seq gone)
                                   (seq tampered-rows)
                                   (and (= "symbols" (:profile scope))
                                        (not (:current? source-graph)))))
              :documents docs-report}}))

(defn- doc-opts [opts] (select-keys opts [:store :actor :format]))

(defn check-before-images!
  "Verifies that none of `paths` (default: all paths in `:before-images` of
   `inspected`) changed on disk since planning or were modified externally
   before `sync`. Raises `:adapters/conflict` (exit 4) before writing."
  ([inspected root]
   (check-before-images! inspected root (sort (keys (:before-images inspected)))))
  ([{:keys [before-images]} root paths]
   (let [changed (vec (for [path paths
                            :when (contains? before-images path)
                            :let [expected (get before-images path)
                                  current (current-source root path)]
                            :when (or (= :modified-externally expected)
                                      (not= current expected))]
                        path))]
     (when (seq changed)
       (err/raise :adapters/conflict
                  (str "refusing to sync: " (count changed)
                       " document(s) changed during planning or were modified externally (before-image guard), e.g. "
                       (first changed))
                  {:reason "before-image" :paths changed})))))

(defn- preflight!
  "v1's preflight: a planned file that exists without being a tracked
   generated document, or a file modified externally / during planning,
   refuses the whole sync (`:adapters/conflict`, exit 4) before anything is
   written."
  [{:keys [plan adopt] :as inspected} root]
  (when-let [taken (seq (filter #(fs/exists? (fs/path root (:path %))) (remove (set (map :doc adopt)) (:create plan))))]
    (err/raise :adapters/conflict
               (str "refusing to sync: " (count taken) " planned file(s) exist and are not tracked as generated documents, e.g. " (:path (first taken)))
               {:paths (mapv :path taken)}))
  (check-before-images! inspected root))

(defn apply-plan!
  "Applies a sync plan through `docs create`, `docs update` and `docs untrack`,
   re-verifying the before-image guard immediately before writing."
  [{:keys [plan restore adopt] :as inspected} opts ctx root]
  (check-before-images! inspected root)
  (doseq [{:keys [doc existing changed]} adopt]
    (check-before-images! inspected root [(:path doc)])
    (fail! (docs/track [(:model doc) (str (fs/path root (:path doc)))] (assoc (doc-opts opts) :name (:name doc)) ctx))
    (when (seq changed)
      (check-before-images! inspected root [(:path doc)])
      (with-payload-file (planner/merge-payload doc existing)
        #(fail! (docs/update-any [(:name doc)] (assoc (doc-opts opts) :data %) ctx)))))
  (doseq [d (remove (set (map :doc adopt)) (:create plan))]
    (check-before-images! inspected root [(:path d)])
    (with-payload-file (:payload d)
      #(fail! (docs/create [(:model d) (:name d)] (assoc (doc-opts opts) :data % :path (str (fs/path root (:path d)))) ctx))))
  (doseq [d (concat (:update plan) restore)]
    (check-before-images! inspected root [(:path d)])
    (with-payload-file (:payload d)
      #(fail! (docs/update-any [(:name d)] (assoc (doc-opts opts) :data %) ctx))))
  (doseq [d (:untrack plan)]
    (fail! (docs/untrack [(:name d)] (doc-opts opts) ctx))))

(defn- positional [sub args]
  [(or (get args 0) (usage! sub "needs an ADAPTER (see `adapters list`) and a PATH"))
   (or (get args 1) (usage! sub "needs the PATH to scan after the adapter"))
   (vec (drop 2 args))])

(defn- scope-data [{:keys [scope facts]}] (assoc scope :facts facts))
(defn- store-ref [descriptor path] {:name (:name descriptor) :path path})

(defn scan
  "`adapters scan ADAPTER PATH --system S […] [-- adapter options]`: runs the
   adapter and returns the planned documents `[{:name :model :path :fact
   :fields}]` with the scope. Writes nothing and needs no store (the project
   root is the store's parent when there is one, else the cwd)."
  ([args opts ctx] (scan "scan" args opts ctx))
  ([sub args opts ctx]
   (let [[adapter path argv] (positional sub args)
         root (or (try (project-root (:path (open-store opts ctx))) (catch Exception _ nil)) (str (:cwd ctx)))
         p (planned sub adapter path argv opts ctx root)]
     (out/ok (str "adapters " sub)
             {:data (assoc (scope-data p) :documents (mapv #(select-keys % [:name :model :path :fact :fields]) (:planned p)))}))))

(defn check
  "`adapters check ADAPTER PATH …`: the drift between the code and the tracked
   documents, without writing. No drift is `{:drift? false :documents []}`;
   drift raises `drift-type` (exit 1) with `{:reason \"drift\" :documents
   [{:name :path :model :status :fields}]}`, status `missing` | `changed` |
   `orphan` | `missing-file` | `untracked` (a valid generated file that
   sync would adopt)."
  ([args opts ctx] (check "check" args opts ctx))
  ([sub args opts ctx]
   (let [[adapter path argv] (positional sub args)
         {:keys [descriptor store] store-path :path} (open-store opts ctx)
         p (planned sub adapter path argv opts ctx (project-root store-path))
         {:keys [report source-graph]} (inspect store (project-root store-path) p)]
     (when (:drift? report)
       (err/raise drift-type (str "documentation drift: " (count (:documents report)) " document(s) differ from the code ("
                                  (str/join ", " (map (fn [[k v]] (str (count v) " " k)) (sort (group-by :status (:documents report)))))
                                  "); run `adapters sync`")
                  (merge (scope-data p) {:reason "drift" :source-graph source-graph} report)))
     (out/ok (str "adapters " sub) {:store (store-ref descriptor store-path) :revision (:head store)
                                    :data (merge (scope-data p) {:source-graph source-graph} report)}))))

(defn sync
  "`adapters sync ADAPTER PATH …`: defines the profile's models when absent,
   then creates, updates and untracks documents so the store matches the
   facts. Unchanged documents are not rewritten (a second sync commits
   nothing). Data: the scope plus `{:models {:defined :present} :counts
   {:created :updated :untracked :unchanged :restored} :created [names]
   :updated [{:name :changed}] :untracked [names] :unchanged [names]
   :restored [names] :adopted [names]}` (`restored`: files rewritten
   without a commit; `adopted`: valid untracked files at a planned path,
   tracked through `docs track` and updated only if a parser field differs)."
  ([args opts ctx] (sync "sync" args opts ctx))
  ([sub args opts ctx]
   (let [[adapter path argv] (positional sub args)
         {:keys [descriptor store] store-path :path} (open-store opts ctx)
         root (project-root store-path)
         p (planned sub adapter path argv opts ctx root)
         {:keys [plan restore adopt] :as inspected} (inspect store root p)
         _ (preflight! inspected root)
         defined (ensure-models! (:profile (:scope p)) opts ctx)
         _ (apply-plan! inspected opts ctx root)
         gone (set (map :name restore))
         data {:created (vec (remove (set (map (comp :name :doc) adopt)) (map :name (:create plan))))
               :adopted (mapv (comp :name :doc) adopt)
               :updated (vec (sort-by :name (concat (map #(hash-map :name (:name %) :changed (vec (:changed %))) (:update plan))
                                                    (for [a adopt :when (seq (:changed a))] {:name (:name (:doc a)) :changed (:changed a)}))))
               :untracked (mapv :name (:untrack plan))
               :unchanged (vec (remove gone (map :name (:unchanged plan))))
               :restored (vec (sort gone))}]
     (out/ok (str "adapters " sub)
             {:store (store-ref descriptor store-path) :revision (:head (:store (open-store opts ctx)))
              :data (merge (scope-data p) data {:models defined :counts (into {} (map (fn [[k v]] [k (count v)])) data)})}))))

(defn list-adapters
  "`adapters list`: `[{:name :file :available? :profile :languages :options
   :missing :error}]` for every built-in, from its `--describe` (a failing
   describe is `:available? false` with `:error`, never a failure)."
  [_args _opts {:keys [env] :as ctx}]
  (out/ok "adapters list"
          {:data (vec (for [[n file] (builtins env)
                            :let [{:keys [exit out err]} (execute [(or (get env "KIMUN_ADAPTER_PYTHON") "python3") file] ["--describe"] ctx)
                                  d (when (zero? exit) (try (json/parse-string out) (catch Exception _ nil)))]]
                        (if (map? d)
                          {:name n :file file :available? (boolean (get-in d ["runtime" "available"])) :profile (get d "profile")
                           :languages (get d "languages") :options (get d "options") :missing (vec (get-in d ["runtime" "missing"]))}
                          {:name n :file file :available? false :missing [] :error (error-line (if (str/blank? err) "describe printed no JSON" err))})))}))

(def aliases
  "v1 `selfdoc` subcommand → `[command adapter]`: `selfdoc-*` is v1 `selfdoc
   scan|sync|check` (argparse-cli), `python-*` v1 `selfdoc python-*`."
  {"selfdoc-scan" [scan "argparse-cli"] "selfdoc-sync" [sync "argparse-cli"] "selfdoc-check" [check "argparse-cli"]
   "python-scan" [scan "python-symbols"] "python-sync" [sync "python-symbols"] "python-check" [check "python-symbols"]})

(defn- alias-command
  "Runs a v1 alias: `--system` defaults to `sldb`; PATH is `--source-root`
   (default `src`) under the project root for `python-*` and the project root
   for `selfdoc-*`; `--package`, `--pythonpath` and `--include-hidden` are
   passed to the adapter."
  [sub args opts ctx]
  (let [[f adapter] (aliases sub)
        root (or (try (project-root (:path (open-store opts ctx))) (catch Exception _ nil)) (str (:cwd ctx)))
        python? (str/starts-with? sub "python-")
        path (str (if python? (fs/path root (or (:source-root opts) "src")) root))
        argv (cond-> (vec args)
               (and python? (:package opts)) (into ["--package" (:package opts)])
               (and (not python?) (:pythonpath opts)) (into ["--pythonpath" (:pythonpath opts)])
               (and (not python?) (:include-hidden opts)) (conj "--include-hidden"))]
    (when (and (not python?) (not (:factory opts))) (usage! sub "needs --factory module:callable (v1's default sldb.cli.parser:build_parser does not apply)"))
    (f sub (into [adapter path] argv) (update opts :system #(or % "sldb")) ctx)))

(def commands
  "Subcommand name → command fn `(fn [args opts ctx] envelope)`."
  (into {"scan" scan "sync" sync "check" check "list" list-adapters}
        (map (fn [sub] [sub (fn [args opts ctx] (alias-command sub args opts ctx))])) (keys aliases)))

(defn dispatch
  "Runs subcommand `sub` of the `adapters` group with the positional tokens
   that follow it; an unknown or missing subcommand is the failure `cli/usage`."
  [sub args opts ctx]
  (if-let [f (get commands sub)]
    (f (vec args) opts ctx)
    (out/failure :cli/usage
                 (str (if sub (str "unknown subcommand `adapters " sub "`") "missing subcommand for `adapters`")
                      "; expected one of: " (str/join ", " (sort (keys commands))))
                 {:group "adapters" :subcommand sub :known (vec (sort (keys commands)))}
                 6)))
