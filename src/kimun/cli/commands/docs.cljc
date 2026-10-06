(ns kimun.cli.commands.docs
  "The `kimun docs` group (docs/v2/06 §B, first slice of S2): `ingest`,
   `update`, `render` and `list`. A document is a `:document` tree of the
   store built by `sldb.surface.markdown`; every write is a `TransactionPlan`
   through `sldb.kernel.store/commit!` — no side door. Every command is
   `(fn [args opts ctx] envelope)` where `args` are the positional tokens
   after the subcommand and `ctx` is `{:env env :cwd cwd}`."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.models.extract :as mextract]
            [kimun.surface.models.render :as mrender]
            [kimun.surface.models.store :as mstore]
            [sldb.host.default :as host]
            [sldb.kernel.err :as err]
            [sldb.kernel.ports :as ports]
            [sldb.kernel.store :as store]
            [sldb.kernel.tree :as tree]
            [sldb.surface.markdown.plan :as md]))

(defn- store-ref [descriptor path] {:name (:name descriptor) :path path})

(defn timestamp
  "The current UTC instant in the plan's shape, `yyyy-MM-ddTHH:mm:ss.SSSZ`."
  []
  #?(:clj (.format (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                   (java.time.ZonedDateTime/now java.time.ZoneOffset/UTC))
     :cljs (.toISOString (js/Date.))))

(defn documents
  "`[{:name :tree :root}]` of every `:document` tree in `store`, by name then id."
  [store]
  (->> (apply dissoc (:trees store) (concat (tdocs/hidden-trees store) (map :tree (tdocs/documents store))))
       (keep (fn [[tid t]]
               (let [d (:descriptor t)]
                 (when (= :document (:kind d))
                   {:name (:name d) :tree tid :root (:root d)}))))
       (sort-by (juxt :name :tree))
       vec))

(defn resolve-document
  "The document named or identified by `token`: a tree id (ULID) present in
   the store, else the single `:document` tree with that name. Raises
   `:docs/missing` (exit 1) when none and `:docs/ambiguous` (exit 2) when
   several documents share the name."
  [store token]
  (if-let [typed (tdocs/find-document store token)]
    (select-keys typed [:name :tree :root])
  (if (and (tree/ulid? token) (contains? (:trees store) token))
    (let [d (:descriptor (get-in store [:trees token]))]
      {:name (:name d) :tree token :root (:root d)})
    (let [found (filterv #(= token (:name %)) (documents store))]
      (case (count found)
        0 (err/raise :docs/missing (str "no document named or identified by `" token "`") {:document token})
        1 (first found)
        (err/raise :docs/ambiguous (str (count found) " documents are named `" token "`; use the tree id")
                   {:document token :trees (mapv :tree found)}))))))

(defn- require-arg! [args i what sub]
  (or (get args i)
      (err/raise :cli/usage (str "`docs " sub "` needs " what) {:group "docs" :subcommand sub})))

(defn- read-file! [path cwd]
  (let [p (fs/absolutize (fs/path cwd path))]
    (when-not (fs/regular-file? p)
      (err/raise :docs/no-file (str "no such file: " (str p)) {:path (str p)}))
    [(str p) (slurp (str p))]))

(defn- name-of [opts path]
  (or (:name opts) (str (fs/strip-ext (fs/file-name path)))))

(defn ingest
  "`docs ingest FILE [--name NAME]`: parses the Markdown file and commits a
   new `:document` tree named NAME (default: the file name without extension).
   Data: `{:name :tree :path :blocks}`. Raises `:docs/exists` (exit 3) when a
   document with that name is already in the store."
  [args opts {:keys [env cwd]}]
  (let [file (require-arg! args 0 "a Markdown FILE" "ingest")
        [path text] (read-file! file cwd)
        name (name-of opts path)
        {:keys [backend descriptor store] store-path :path} (cli-store/open! (cli-store/resolve-store opts env cwd))
        _ (when (seq (filter #(= name (:name %)) (documents store)))
            (err/raise :docs/exists (str "a document named `" name "` already exists; use `docs update`") {:document name}))
        tree-id (ports/ulid (:ids host/host))
        plan (md/markdown->plan host/host text {:tree-id tree-id :name name :actor (:actor opts)
                                                :timestamp (timestamp) :base (:head store)})
        r (store/commit! backend store plan)
        t (get-in r [:store :trees tree-id])]
    (out/ok "docs ingest" {:store (store-ref descriptor store-path)
                           :revision (:revision-id r)
                           :data {:name name :tree tree-id :path path
                                  :blocks (count (tree/children-at t []))}})))

(defn update-doc
  "`docs update FILE --name NAME|TREE`: re-ingests the file into the existing
   document, as an edit made outside the kernel (`docs/v2/04 §8`: no
   succession is recorded). Data: `{:name :tree :path :blocks}`."
  [args opts {:keys [env cwd]}]
  (let [file (require-arg! args 0 "a Markdown FILE" "update")
        token (or (:name opts) (get args 1)
                  (err/raise :cli/usage "`docs update` needs --name NAME (or a second token naming the document)" {:group "docs" :subcommand "update"}))
        [path text] (read-file! file cwd)
        {:keys [backend descriptor store] store-path :path} (cli-store/open! (cli-store/resolve-store opts env cwd))
        {:keys [name tree]} (resolve-document store token)
        plan (md/markdown->update-plan host/host store tree text
                                       {:actor (:actor opts) :timestamp (timestamp) :base (:head store)})
        r (store/commit! backend store plan)
        t (get-in r [:store :trees tree])]
    (out/ok "docs update" {:store (store-ref descriptor store-path)
                           :revision (:revision-id r)
                           :data {:name name :tree tree :path path
                                  :blocks (count (tree/children-at t []))}})))

(defn render
  "`docs render NAME|TREE`: the canonical Markdown of the document, as the
   envelope's data (a string, so `--format text` prints the document itself)."
  [args opts {:keys [env cwd]}]
  (let [token (require-arg! args 0 "a document NAME or tree id" "render")
        {:keys [descriptor store] store-path :path} (cli-store/open! (cli-store/resolve-store opts env cwd))
        {:keys [tree]} (resolve-document store token)]
    (out/ok "docs render" {:store (store-ref descriptor store-path)
                           :revision (:head store)
                           :data (md/store->markdown host/host store tree)})))

(defn list-docs
  "`docs list [--model M]`: every document of the store by name. An untyped
   document is `{:name :tree :root}`; a typed one adds `:model :model-version
   :state :path`. `--model` keeps the documents of that model."
  [_args opts {:keys [env cwd]}]
  (let [{:keys [descriptor store] store-path :path} (cli-store/open! (cli-store/resolve-store opts env cwd))
        typed (map #(select-keys % [:name :tree :root :model :model-version :state :path]) (tdocs/documents store))
        all (if (:model opts) (filter #(= (:model opts) (:model %)) typed) (concat typed (documents store)))]
    (out/ok "docs list" {:store (store-ref descriptor store-path)
                         :revision (:head store)
                         :data (vec (sort-by (juxt :name :tree) all))})))


;; ---------------------------------------------------------------- typed documents

(defn- plain [v] (walk/postwalk #(cond (map? %) (into {} (map (fn [[k x]] [(if (keyword? k) (name k) k) x])) %) (seq? %) (vec %) :else %) v))

(defn- read-payload
  "The payload given by `--data FILE` (JSON by extension, else YAML) merged
   with every `--set field=value` (the value read as a YAML scalar)."
  [opts cwd]
  (let [from-file (when-let [f (:data opts)]
                    (let [[p text] (read-file! f cwd)
                          v (try (plain (if (str/ends-with? p ".json") (json/parse-string text) (yaml/parse-string text :keywords false)))
                                 (catch Exception e (err/raise :validate/bad-data (str "cannot read payload " p ": " (ex-message e)) {:path p})))]
                      (if (map? v) v (err/raise :validate/bad-data (str "payload " p " must be a mapping of field → value") {:path p}))))
        sets (into {} (map (fn [kv]
                             (let [[k v] (str/split kv #"=" 2)]
                               (when (or (str/blank? k) (nil? v))
                                 (err/raise :cli/usage (str "--set expects field=value, got `" kv "`") {:set kv}))
                               [k (plain (yaml/parse-string v :keywords false))])))
                   (:set opts))]
    (when (and (nil? from-file) (empty? sets))
      (err/raise :cli/usage "a payload is required: --data FILE and/or --set field=value" {}))
    (merge from-file sets)))

(defn- project-root [store-path] (str (fs/parent (fs/absolutize store-path))))

(defn- relative-path [root p]
  (str (fs/relativize (fs/path root) (fs/absolutize p))))

(defn- write-file!
  "Writes `text` at `root`/`path`; returns a warnings vector (empty on success)."
  [root path text]
  (try (let [f (fs/path root path)] (some-> (fs/parent f) fs/create-dirs) (spit (str f) text) [])
       (catch Exception e [{:type "docs/file-not-written" :message (str "could not write " path ": " (ex-message e))}])))

(defn- open [opts {:keys [env cwd]}] (cli-store/open! (cli-store/resolve-store opts env cwd)))

(defn- doc-data [d] (select-keys d [:name :model :model-version :state :path :tree :payload :error :source-hash :content-hash :field-hashes]))

(defn- done [command descriptor store-path r store' name extra]
  (out/ok command (merge {:store (store-ref descriptor store-path) :revision (:revision-id r)
                          :data (doc-data (tdocs/document store' name))} extra)))

(defn create
  "`docs create MODEL NAME --data FILE | --set field=value… [--path P]`:
   renders the payload with the model, tracks it and writes the file (default
   `NAME.md` in the working directory). Data: the document map."
  [args opts {:keys [cwd] :as ctx}]
  (let [model (require-arg! args 0 "a MODEL" "create") name (require-arg! args 1 "a document NAME" "create")
        {:keys [backend descriptor store] store-path :path} (open opts ctx)
        root (project-root store-path)
        path (relative-path root (fs/path cwd (or (:path opts) (str name ".md"))))
        {:keys [plan text]} (tdocs/create-plan host/host store model name (read-payload opts cwd)
                                               {:doc (ports/ulid (:ids host/host)) :tree-id (ports/ulid (:ids host/host))
                                                :path path :actor (:actor opts) :timestamp (timestamp)})
        _ (when (fs/exists? (fs/path root path))
            (err/raise :docs/conflict (str "the file " path " already exists; choose another --path") {:path path}))
        r (store/commit! backend store plan)]
    (done "docs create" descriptor store-path r (:store r) name {:warnings (write-file! root path text)})))

(defn track
  "`docs track MODEL FILE [--name N]`: tracks an existing Markdown file as a
   document of MODEL (the file is not rewritten). A file that does not
   round-trip — rendering its extracted payload gives another text — is
   refused (`docs/not-idempotent`) unless `--force`. Data: the document map."
  [args opts {:keys [cwd] :as ctx}]
  (let [model (require-arg! args 0 "a MODEL" "track") file (require-arg! args 1 "a Markdown FILE" "track")
        [p text] (read-file! file cwd)
        {:keys [backend descriptor store] store-path :path} (open opts ctx)
        name (name-of opts p)
        d (:descriptor (mstore/model store model))
        _ (when-not (:force opts)
            (let [again (mrender/render d (mextract/extract d text))]
              (when (not= (str/trim again) (str/trim text))
                (err/raise :docs/not-idempotent
                           (str "`" file "` does not round-trip under " model ": rendering what was extracted gives a different text; fix the file or pass --force")
                           {:document name :model model}))))
        {:keys [plan]} (tdocs/track-plan host/host store model name text
                                         {:doc (ports/ulid (:ids host/host)) :tree-id (ports/ulid (:ids host/host))
                                          :path (relative-path (project-root store-path) p) :actor (:actor opts) :timestamp (timestamp)})
        r (store/commit! backend store plan)]
    (done "docs track" descriptor store-path r (:store r) name {})))

(defn update-typed
  "`docs update NAME [FILE] [--data F | --set k=v]`: re-reads the document's
   file (or FILE), or re-renders it from a payload merged over the current
   one, and commits the new source. Data: the document map plus `:unchanged?`."
  [doc args opts {:keys [cwd] :as ctx}]
  (let [{:keys [backend descriptor store] store-path :path} (open opts ctx)
        root (project-root store-path)
        payload? (or (:data opts) (seq (:set opts)))
        m (when payload? (:descriptor (mstore/model store (:model doc))))
        text (cond payload? (str (mrender/render m (merge (:payload (tdocs/document store (:name doc))) (read-payload opts cwd))) "\n")
                   (get args 1) (second (read-file! (get args 1) cwd))
                   (:path doc) (second (read-file! (str (fs/path root (:path doc))) cwd))
                   :else (err/raise :cli/usage "`docs update` needs a FILE, --data or --set for a document without a file" {}))
        {:keys [plan unchanged?]} (tdocs/update-plan host/host store (:name doc) text {:actor (:actor opts) :timestamp (timestamp)})
        r (if plan (store/commit! backend store plan) {:store store :revision-id (:head store)})
        warnings (if (and payload? (:path doc)) (write-file! root (:path doc) text) [])]
    (update (done "docs update" descriptor store-path r (:store r) (:name doc) {:warnings warnings})
            :data assoc :unchanged? (boolean unchanged?))))

(defn untrack
  "`docs untrack NAME`: stops tracking the document; its file stays. With
   `delete?` (the `docs delete` command) the file is removed too, and `--yes`
   is required (`docs/confirmation-required` otherwise, nothing touched)."
  ([args opts ctx] (untrack args opts ctx false))
  ([args opts ctx delete?]
   (let [sub (if delete? "delete" "untrack")
         token (require-arg! args 0 "a document NAME" sub)
         _ (when (and delete? (not (:yes opts)))
             (err/raise :docs/confirmation-required "`docs delete` removes the file too: pass --yes to confirm" {:document token}))
         {:keys [backend descriptor store] store-path :path} (open opts ctx)
         {:keys [plan name path]} (tdocs/untrack-plan store token {:actor (:actor opts) :timestamp (timestamp) :operation (str "docs " sub)})
         r (store/commit! backend store plan)
         f (when path (fs/path (project-root store-path) path))]
     (when (and delete? f (fs/exists? f)) (fs/delete f))
     (out/ok (str "docs " sub) {:store (store-ref descriptor store-path) :revision (:revision-id r)
                                :data {:name name :path path :file-deleted? (boolean (and delete? f))}}))))

(defn show
  "`docs show NAME [--format md]`: the document map with payload, hashes and
   `:markdown` (its source; `--format md` prints it)."
  [args opts ctx]
  (let [{:keys [descriptor store] store-path :path} (open opts ctx)
        d (tdocs/document store (require-arg! args 0 "a document NAME" "show"))]
    (out/ok "docs show" {:store (store-ref descriptor store-path) :revision (:head store)
                         :data (assoc (doc-data d) :markdown (:source d))})))

(defn status
  "`docs status`: `[{:name :path :status}]` comparing each tracked file with
   the fingerprint recorded for it (`ok`, `modified`, `missing`)."
  [_args opts ctx]
  (let [{:keys [descriptor store] store-path :path} (open opts ctx)
        root (project-root store-path)]
    (out/ok "docs status" {:store (store-ref descriptor store-path) :revision (:head store)
                           :data (tdocs/sync-report store #(let [f (fs/path root %)] (when (fs/regular-file? f) (slurp (str f)))))})))

(defn update-any
  "`docs update`: for a tracked typed document, `docs update NAME [FILE]`
   (`update-typed`); otherwise the untyped re-ingest `docs update FILE --name N`."
  [args opts {:keys [env cwd] :as ctx}]
  (let [store (:store (cli-store/open! (cli-store/resolve-store opts env cwd)))
        by-arg (some->> (get args 0) (tdocs/find-document store))
        by-name (some->> (:name opts) (tdocs/find-document store))]
    (cond by-arg (update-typed by-arg args opts ctx)
          by-name (update-typed by-name [(:name by-name) (get args 0)] opts ctx)
          :else (update-doc args opts ctx))))

(def spec
  "Options of the `docs` group (babashka.cli spec, merged by `kimun.cli.main`)."
  {:name {:ref "NAME" :desc "Document name (docs ingest, docs update, docs track)"}
   :data {:ref "FILE" :desc "Payload file, JSON or YAML (docs create, docs update)"}
   :set {:ref "FIELD=VALUE" :desc "One payload field (repeatable)" :coerce [:string]}
   :path {:ref "PATH" :desc "File of the new document (docs create)"}
   :model {:ref "NAME" :desc "Only documents of this model (docs list)"}
   :force {:desc "Track a file that does not round-trip under its model (docs track)" :coerce :boolean}
   :yes {:desc "Confirm a destructive command (docs delete)" :coerce :boolean}})

(def commands
  "Subcommand name → command fn `(fn [args opts ctx] envelope)`."
  {"ingest" ingest "update" update-any "render" render "list" list-docs "create" create "track" track
   "untrack" untrack "delete" (fn [args opts ctx] (untrack args opts ctx true)) "show" show "status" status})

(defn dispatch
  "Runs subcommand `sub` of the `docs` group with the positional tokens that
   follow it; an unknown or missing subcommand is the failure `cli/usage`."
  [sub args opts ctx]
  (if-let [f (get commands sub)]
    (f (vec args) opts ctx)
    (out/failure :cli/usage
                 (str (if sub (str "unknown subcommand `docs " sub "`") "missing subcommand for `docs`")
                      "; expected one of: " (str/join ", " (sort (keys commands))))
                 {:group "docs" :subcommand sub :known (vec (sort (keys commands)))}
                 6)))
