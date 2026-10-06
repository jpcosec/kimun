(ns kimun.cli.commands.docs
  "The `kimun docs` group (docs/v2/06 §B, first slice of S2): `ingest`,
   `update`, `render` and `list`. A document is a `:document` tree of the
   store built by `sldb.surface.markdown`; every write is a `TransactionPlan`
   through `sldb.kernel.store/commit!` — no side door. Every command is
   `(fn [args opts ctx] envelope)` where `args` are the positional tokens
   after the subcommand and `ctx` is `{:env env :cwd cwd}`."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
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
  (->> (:trees store)
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
  (if (and (tree/ulid? token) (contains? (:trees store) token))
    (let [d (:descriptor (get-in store [:trees token]))]
      {:name (:name d) :tree token :root (:root d)})
    (let [found (filterv #(= token (:name %)) (documents store))]
      (case (count found)
        0 (err/raise :docs/missing (str "no document named or identified by `" token "`") {:document token})
        1 (first found)
        (err/raise :docs/ambiguous (str (count found) " documents are named `" token "`; use the tree id")
                   {:document token :trees (mapv :tree found)})))))

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
  "`docs list`: every document of the store, `[{:name :tree :root}]` by name."
  [_args opts {:keys [env cwd]}]
  (let [{:keys [descriptor store] store-path :path} (cli-store/open! (cli-store/resolve-store opts env cwd))]
    (out/ok "docs list" {:store (store-ref descriptor store-path)
                         :revision (:head store)
                         :data (documents store)})))

(def commands
  "Subcommand name → command fn `(fn [args opts ctx] envelope)`."
  {"ingest" ingest "update" update-doc "render" render "list" list-docs})

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
