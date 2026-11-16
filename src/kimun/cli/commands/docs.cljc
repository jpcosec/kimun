(ns kimun.cli.commands.docs
  "The `kimun docs` group (docs/v2/06 §3, first slice of S2): `ingest`,
   `update`, `render` and `list`. A document is a `:document` tree of the
   store built by `kimun.surface.markdown`; every write is a `TransactionPlan`
   through `kimun.kernel.store/commit!` — no side door. Every command is
   `(fn [args opts ctx] envelope)` where `args` are the positional tokens
   after the subcommand and `ctx` is `{:env env :cwd cwd}`."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [kimun.cli.compose :as compose]
            [kimun.cli.linked :as linked]
            [kimun.cli.args :as cli-args]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.models.extract :as mextract]
            [kimun.surface.models.noise :as noise]
            [kimun.surface.models.render :as mrender]
            [kimun.surface.models.store :as mstore]
            [kimun.host.default :as host]
            [kimun.kernel.err :as err]
            [kimun.kernel.ports :as ports]
            [kimun.kernel.revision :as revision]
            [kimun.kernel.route :as route]
            [kimun.kernel.store :as store]
            [kimun.kernel.tree :as tree]
            [kimun.surface.markdown.anchors :as anchors]
            [kimun.surface.markdown.plan :as md]
            [kimun.surface.models.capture :as capture]
            [kimun.surface.parse-contract :as parse-contract]))


(defn timestamp
  "The current UTC instant in the plan's shape, `yyyy-MM-ddTHH:mm:ss.SSSZ`."
  []
  #?(:clj (.format (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                   (java.time.ZonedDateTime/now java.time.ZoneOffset/UTC))
     :cljs (.toISOString (js/Date.))))

(defn commit-built
  "Opens the store, builds a plan with `build` (`(build host store arg
   {:actor :timestamp :tree-id})` returning `{:changed :plan}`) and commits it
   when it changes anything: `{:opened :store :revision :changed}`."
  [build arg opts ctx]
  (let [{:keys [backend] s :store :as o} (cli-store/open-for opts ctx)
        {:keys [changed plan]} (build host/host s arg {:actor (:actor opts) :timestamp (timestamp)
                                                       :tree-id (ports/ulid (:ids host/host))})
        r (when changed (store/commit! backend s plan))]
    {:opened o :store (if r (:store r) s) :revision (if r (:revision-id r) (:head s)) :changed (boolean changed)}))

(defn documents
  "`[{:name :tree :root}]` of every untyped `:document` tree in `store` (plus
   `:class` for one registered under the generic class, K3), by
   name then id (typed ones, tracked or hidden, are left out). `typed` is
   `(tdocs/documents store)` when the caller already has it. Reads the tree
   descriptors only."
  ([store] (documents store (tdocs/documents store)))
  ([store typed]
   (let [skip (into (tdocs/hidden-trees store typed) (map :tree typed))]
     (->> (revision/tree-descriptors store)
          (keep (fn [[tid d]]
                  (when (and (= :document (:kind d)) (not (skip tid)))
                    (cond-> {:name (:name d) :tree tid :root (:root d)}
                      ;; registered under the generic class (K3): its tree is cache
                      (tdocs/generic-class? (:class d)) (assoc :class (:class d))))))
          (sort-by (juxt :name :tree))
          vec))))

(defn resolve-document
  "The document named or identified by `token`: a tree id (ULID) present in
   the store, a tracked document (typed or of a generic class) by short or
   qualified name, else the single `:document` tree with that name. Raises
   `:docs/missing` (exit 1) when none and `:docs/ambiguous` (exit 2) when
   several documents share the name."
  [store token]
  ;; typed and generic documents are resolved together: an alias they share is ambiguous (K8)
  (if-let [tracked (tdocs/resolve-any store token)]
    (select-keys tracked [:name :tree :root])
  (if (and (tree/ulid? token) (contains? (:trees store) token))
    (let [d (:descriptor (get-in store [:trees token]))]
      {:name (:name d) :tree token :root (:root d)})
    (let [found (filterv #(= token (:name %)) (documents store))]
      (case (count found)
        0 (err/raise :docs/missing (str "no document named or identified by `" token "`") {:document token})
        1 (first found)
        (err/raise :docs/ambiguous (str (count found) " documents are named `" token "`; use the tree id")
                   {:document token :trees (mapv :tree found)}))))))


(defn- mint-unit
  "A fresh id for a field unit: a ULID from the host's minter (identity is
   minted when the field is marked, never recomputed)."
  []
  (ports/ulid (:ids host/host)))

(defn- read-file! [path cwd]
  (let [p (fs/absolutize (fs/path cwd path))]
    (when-not (fs/regular-file? p)
      (err/raise :docs/no-file (str "no such file: " (str p)) {:path (str p)}))
    [(str p) (slurp (str p))]))

(defn- name-of [opts path]
  (or (:name opts) (str (fs/strip-ext (fs/file-name path)))))

(defn- project-root* [store-path] (str (fs/parent (fs/absolutize store-path))))

(defn- inside-path
  "`abs` relative to the project of the store at `store-path`, or nil when the
   file is outside it (it cannot be anchored by a relative path). The path is
   taken as written: a symlink inside the project is anchored by its own path,
   whatever it points to, as `docs track` does."
  [store-path abs]
  (let [rel (route/normalize-path (str (fs/relativize (fs/path (project-root* store-path)) (fs/absolutize abs))))]
    ;; by segment: `../x.md` is outside, `..foo.md` is a file name like any other
    (when (route/valid-path? rel) rel)))

(defn ingest
  "`docs ingest FILE [--name NAME]`: registers the Markdown file as a new
   document without a model, named NAME (default: the file name without
   extension). In a by-reference store (the default) the document is
   registered under the generic class (K3, docs/v2/14 §18.1): its external
   anchor, the class `generic/md` and the root hash enter the store and its
   tree is cache — `:class` and `:anchor` (the relative path) are in the data,
   and the file is watched by `stores sense`. In an `:inline`
   store, or for a file outside the project, the tree is persisted as before.
   Data: `{:name :tree :path :blocks}` plus `:class :anchor` when registered.
   Raises `:docs/exists` (exit 3) when a document with that name is already in
   the store."
  [args opts {:keys [env cwd]}]
  (let [file (cli-args/require-arg! "docs" args 0 "a Markdown FILE" "ingest")
        [path text] (read-file! file cwd)
        name (name-of opts path)
        {:keys [backend descriptor store] store-path :path} (cli-store/open! (cli-store/resolve-store opts env cwd))
        _ (when (seq (filter #(= name (:name %)) (documents store)))
            (err/raise :docs/exists (str "a document named `" name "` already exists; use `docs update`") {:document name}))
        tree-id (ports/ulid (:ids host/host))
        anchor (when (tdocs/generic-registrable? store) (inside-path store-path path))]
    (if anchor
      (let [{:keys [plan blocks]} (tdocs/generic-plan host/host store name text anchor
                                                      {:doc (ports/ulid (:ids host/host)) :tree-id tree-id
                                                       :actor (:actor opts) :timestamp (timestamp)})
            r (store/commit! backend store plan)]
        (out/ok "docs ingest" {:store (cli-store/store-ref descriptor store-path)
                               :revision (:revision-id r)
                               :data {:name name :tree tree-id :path path :blocks blocks
                                      :class tdocs/generic-class :anchor anchor}}))
      (let [plan (md/markdown->plan host/host text {:tree-id tree-id :name name :actor (:actor opts)
                                                    :timestamp (timestamp) :base (:head store)})
            r (store/commit! backend store plan)
            t (get-in r [:store :trees tree-id])]
        (out/ok "docs ingest" {:store (cli-store/store-ref descriptor store-path)
                               :revision (:revision-id r)
                               :data {:name name :tree tree-id :path path
                                      :blocks (count (tree/children-at t []))}})))))

(defn update-doc
  "`docs update FILE --name NAME|TREE`: re-ingests the file into the existing
   document, as an edit made outside the kernel (`docs/v2/04 §8`: no
   succession is recorded). A document registered under the generic class
   (K3) is registered again from FILE — its anchor, fingerprint, root hash and
   parse contract — and `:unchanged?` says whether anything moved. Data:
   `{:name :tree :path :blocks}`."
  [args opts {:keys [env cwd]}]
  (let [file (cli-args/require-arg! "docs" args 0 "a Markdown FILE" "update")
        token (or (:name opts) (get args 1)
                  (err/raise :cli/usage "`docs update` needs --name NAME (or a second token naming the document)" {:group "docs" :subcommand "update"}))
        [path text] (read-file! file cwd)
        {:keys [backend descriptor store] store-path :path} (cli-store/open! (cli-store/resolve-store opts env cwd))
        {:keys [name tree]} (resolve-document store token)
        generic (tdocs/generic-document store tree)
        anchor (when generic (inside-path store-path path))]
    (when (and generic (nil? anchor))
      (err/raise :docs/outside-project (str "`" file "` is outside the project of this store: a registered document needs a file inside it")
                 {:path path :document name}))
    (if generic
      (let [{:keys [plan blocks unchanged?]} (tdocs/generic-update-plan host/host store generic text
                                                                        {:path anchor :actor (:actor opts) :timestamp (timestamp)})
            r (if plan (store/commit! backend store plan) {:revision-id (:head store)})]
        (out/ok "docs update" {:store (cli-store/store-ref descriptor store-path)
                               :revision (:revision-id r)
                               :data {:name name :tree tree :path path :blocks blocks
                                      :class (:class generic) :anchor anchor :unchanged? (boolean unchanged?)}}))
      (let [plan (md/markdown->update-plan host/host store tree text
                                           {:actor (:actor opts) :timestamp (timestamp) :base (:head store)})
            r (store/commit! backend store plan)
            t (get-in r [:store :trees tree])]
        (out/ok "docs update" {:store (cli-store/store-ref descriptor store-path)
                               :revision (:revision-id r)
                               :data {:name name :tree tree :path path
                                      :blocks (count (tree/children-at t []))}})))))

(defn render
  "`docs render NAME|TREE`: the canonical Markdown of the document, as the
   envelope's data (a string, so `--format text` prints the document itself)."
  [args opts {:keys [env cwd]}]
  (let [token (cli-args/require-arg! "docs" args 0 "a document NAME or tree id" "render")
        {:keys [descriptor store] store-path :path} (cli-store/open! (cli-store/resolve-store opts env cwd))
        {:keys [tree]} (resolve-document store token)]
    (out/ok "docs render" {:store (cli-store/store-ref descriptor store-path)
                           :revision (:head store)
                           :data (or (tdocs/document-markdown store (or (tdocs/find-document store token)
                                                                        (tdocs/generic-document store tree) tree))
                                     (some-> (or (tdocs/find-document store token) (tdocs/generic-document store tree))
                                             tdocs/file-missing!)
                                     (md/store->markdown host/host store tree))})))

(defn list-docs
  "`docs list [--model M]`: every document of the store by name. An untyped
   document is `{:name :tree :root}`; a typed one adds `:qualified :model
   :model-version :state :path` (`:qualified` is the path of its file, the
   name that tells apart documents sharing a short name). `--model` keeps the documents of that model."
  [_args opts {:keys [env cwd]}]
  (let [{:keys [descriptor store] store-path :path} (cli-store/open! (cli-store/resolve-store opts env cwd))
        docs (tdocs/documents store)
        typed (map #(select-keys % [:name :qualified :tree :root :model :model-version :state :path]) docs)
        all (if (:model opts) (filter #(= (:model opts) (:model %)) typed) (concat typed (documents store docs)))]
    (out/ok "docs list" {:store (cli-store/store-ref descriptor store-path)
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

(defn- doc-data
  "The document map as `docs` answers it. A document of a spec 16 class (M1g, `:spec16`)
   also reports the exact class it was anchored under (`:class`, the address of its
   descriptor) and the ancestors its selector was composed with (`:ancestors`); a v1
   document answers exactly as before."
  [d]
  (cond-> (select-keys d [:name :qualified :model :model-version :state :path :tree :payload :error :source-hash :content-hash :field-hashes])
    (:spec16 d) (assoc :class (:contract-id d) :ancestors (vec (:ancestors d)))))

(defn- refuse-spec16-render!
  "Raises `cli/usage` when `d` is a spec 16 class and `op` needs a renderer: generation
   from a spec 16 descriptor is M2, so a document of such a class is tracked and
   updated from its file only (M1g)."
  [d op]
  (when (mstore/spec16? d)
    (err/raise :cli/usage (str "`docs " op "` renders the payload, and a spec 16 class (" (:model/name d) ") has no renderer yet (M2): "
                               "write the file and use `docs track` / `docs update FILE`")
               {:model (:model/name d)})))

(defn- done
  "The envelope of a document write; `token` is the tree id of the document
   written (a short name may be shared, K8)."
  [command descriptor store-path r store' token extra]
  (out/ok command (merge {:store (cli-store/store-ref descriptor store-path) :revision (:revision-id r)
                          :data (doc-data (tdocs/document store' token))} extra)))

;; The refusal of v1's `refuse_structural_noise` + `refuse_undeclared_frontmatter`: a heading the
;; template never declares is swallowed into a field, a frontmatter key it never declares is lost
;; on the next render. Raised before anything is committed or written.
(defn- refuse-noise!
  [model-name descriptor name text]
  (let [r (noise/structural-noise descriptor text)]
    (when (noise/noisy? r)
      (err/raise :docs/structural-noise
                 (str "structural noise in `" name "`: " model-name " does not declare " (noise/describe r)
                      ". A heading inside a field is a field the model is missing: define a model that extends "
                      model-name " and declares those sections as optrev fields; an undeclared frontmatter key is lost on re-render")
                 {:document name :model model-name
                  :undeclared (mapv :raw (:headings r))
                  :headings (:headings r)
                  :frontmatter (:frontmatter r)}))))

(defn- bind-model!
  "Commits the binding of the foreign model `m` (`kimun.cli.linked/import-plan`)
   when there is one to write; returns the store to go on with."
  [backend store m opts]
  (if-let [plan (linked/import-plan host/host store m {:actor (:actor opts) :timestamp (timestamp)
                                                       :tree-id (ports/ulid (:ids host/host))})]
    (:store (store/commit! backend store plan))
    store))

(defn- refuse-tracked! [store name path] (tdocs/refuse-collision! store name path))

(defn create
  "`docs create MODEL NAME --data FILE | --set field=value… [--path P]`:
   renders the payload with the model (its compositions expanded against the
   project root, `kimun.cli.compose/context`), tracks it and writes the file
   (default `NAME.md` in the working directory). MODEL may live in a linked
   store — `ALIAS:Model`, or a bare name only a linked store defines — and
   `--store` may be the ALIAS of a linked store (`kimun.cli.linked`); with an
   alias `--path` is relative to that store's project root, as in v1. A
   foreign model is bound in the store, in its own commit, only once the
   document has passed every check. Data: the document map."
  [args opts {:keys [env cwd]}]
  (let [token (cli-args/require-arg! "docs" args 0 "a MODEL" "create") name (cli-args/require-arg! "docs" args 1 "a document NAME" "create")
        {:keys [opened alias home]} (linked/target opts env cwd)
        {:keys [backend descriptor store] store-path :path} opened
        payload (read-payload opts cwd)
        {model :name d :descriptor :as m} (linked/resolve-model opened home token)
        _ (refuse-spec16-render! d "create")
        root (project-root store-path)
        path (relative-path root (fs/path (if alias root cwd) (or (:path opts) (str name ".md"))))
        text (str (mrender/render d payload (compose/context store store-path)) "\n")
        _ (refuse-tracked! store name path)
        _ (mextract/extract d text)
        _ (refuse-noise! model d name text)
        _ (when (fs/exists? (fs/path root path))
            (err/raise :docs/conflict (str "the file " path " already exists; choose another --path") {:path path}))
        store (bind-model! backend store m opts)
        {:keys [plan tree]} (tdocs/track-plan host/host store model name text
                                         {:mint mint-unit
                                          :doc (ports/ulid (:ids host/host)) :tree-id (ports/ulid (:ids host/host))
                                          :path path :actor (:actor opts) :timestamp (timestamp)
                                          :inline (:inline opts)})
        r (store/commit! backend store plan)]
    (done "docs create" descriptor store-path r (:store r) tree {:warnings (cli-store/write-file! root path text)})))

(defn track
  "`docs track MODEL FILE [--name N]`: tracks an existing Markdown file as a
   document of MODEL (the file is not rewritten). A file that does not
   round-trip — rendering its extracted payload gives another text — is
   refused (`docs/not-idempotent`) unless `--force`. MODEL and `--store`
   accept a linked store as in `docs create`. Data: the document map."
  [args opts {:keys [env cwd]}]
  (let [token (cli-args/require-arg! "docs" args 0 "a MODEL" "track") file (cli-args/require-arg! "docs" args 1 "a Markdown FILE" "track")
        [p text] (read-file! file cwd)
        {:keys [opened home]} (linked/target opts env cwd)
        {:keys [backend descriptor store] store-path :path} opened
        name (name-of opts p)
        {model :name d :descriptor :as m} (linked/resolve-model opened home token)
        _ (refuse-tracked! store name (relative-path (project-root store-path) p))
        _ (when-not (or (:force opts) (mstore/spec16? d))
            ;; a spec 16 class has no template to render, so no round trip (and no noise check) applies: M1g
            ;; noise first: a dropped frontmatter key would otherwise surface as a bare round-trip failure
            (refuse-noise! model d name text)
            (let [again (mrender/render d (mextract/extract d text) (compose/context store store-path))]
              (when (not= (str/trim again) (str/trim text))
                (err/raise :docs/not-idempotent
                           (str "`" file "` does not round-trip under " model ": rendering what was extracted gives a different text; fix the file or pass --force")
                           {:document name :model model}))))
        store (bind-model! backend store m opts)
        {:keys [plan tree]} (tdocs/track-plan host/host store model name text
                                         {:mint mint-unit
                                          :doc (ports/ulid (:ids host/host)) :tree-id (ports/ulid (:ids host/host))
                                          :path (relative-path (project-root store-path) p) :actor (:actor opts) :timestamp (timestamp)
                                          :inline (:inline opts)})
        r (store/commit! backend store plan)]
    (done "docs track" descriptor store-path r (:store r) tree {})))

(defn update-typed
  "`docs update NAME [FILE] [--data F | --set k=v]`: re-reads the document's
   file (or FILE), or re-renders it from a payload merged over the current
   one, and commits the new source. A `:superseded` document whose file/stored
   source has not moved is migrated: its old payload (read under the contract
   it was written with) is re-rendered under the model's current contract, so
   a section the new contract adds cannot swallow the old text. Data: the
   document map plus `:unchanged?`."
  [doc args opts {:keys [cwd] :as ctx}]
  (let [{:keys [backend descriptor store] store-path :path} (cli-store/open-for opts ctx)
        root (project-root store-path)
        payload? (or (:data opts) (seq (:set opts)))
        current (:descriptor (mstore/model store (:model doc)))
        full (tdocs/document store (:tree doc))
        no-input? (and (not payload?) (not (get args 1)))
        stored (when (and no-input? (:path doc)) (second (read-file! (str (fs/path root (:path doc))) cwd)))
        spec16? (mstore/spec16? current)
        _ (when (and payload? spec16?) (refuse-spec16-render! current "update --data/--set"))
        migrate? (and (not spec16?) no-input? (= :superseded (:state doc)) (= (or stored (:source full)) (:source full)))
        text (cond payload? (str (mrender/render current (merge (:payload full) (read-payload opts cwd))
                                                    (compose/context store store-path)) "\n")
                   (get args 1) (second (read-file! (get args 1) cwd))
                   migrate? (tdocs/move-text store full nil (compose/context store store-path))
                   stored stored
                   :else (err/raise :cli/usage "`docs update` needs a FILE, --data or --set for a document without a file" {}))
        _ (when-not spec16? (refuse-noise! (:model doc) (or current {}) (:name doc) text))
        {:keys [plan unchanged?]} (tdocs/update-plan host/host store (:tree doc) text
                                                     {:mint mint-unit :actor (:actor opts) :timestamp (timestamp)})
        r (if plan (store/commit! backend store plan) {:store store :revision-id (:head store)})
        warnings (if (and (or payload? migrate?) (:path doc)) (cli-store/write-file! root (:path doc) text) [])]
    (update (done "docs update" descriptor store-path r (:store r) (:tree doc) {:warnings warnings})
            :data assoc :unchanged? (boolean unchanged?))))

(defn- document-at
  "The tracked document (typed or generic) `token` names, as a map with
   `:name :tree :path :model :class :generic :doc :qualified`; raises
   `:docs/missing`."
  [store token]
  (or (tdocs/resolve-any store token)
      (err/raise :docs/missing (str "no document named or identified by `" token "`") {:document token})))

(defn reanchor
  "`docs reanchor NAME FILE --reason TEXT`: DECLARES that document NAME (typed
   or generic) is anchored at FILE from now on, keeping its document id, its
   tree id and its name (docs/v2/14 §21, rules 3 and 6). kimun neither moves
   nor deletes the file: this command only records the declaration, and the
   sensor never decides continuity. `--reason` is required; FILE must exist
   inside the project; an already tracked path, the current path and a
   document without a file are refused. The declaration travels in the
   `:reanchored`. When the previous file is
   still there it stays untracked and the answer warns
   `docs/previous-file-present`. Data: what `docs update` returns for that
   document, plus `:reanchored {:from :to :previous-file :reason}`."
  [args opts {:keys [env cwd] :as ctx}]
  (let [token (cli-args/require-arg! "docs" args 0 "a document NAME" "reanchor")
        file (cli-args/require-arg! "docs" args 1 "a Markdown FILE" "reanchor")
        reason (:reason opts)
        _ (when (or (not (string? reason)) (str/blank? (str/trim reason)))
            (err/raise :cli/usage "`docs reanchor` needs --reason TEXT: the declaration says what happened"
                       {:group "docs" :subcommand "reanchor"}))
        [path text] (read-file! file cwd)
        {:keys [backend descriptor store] store-path :path} (cli-store/open-for opts ctx)
        root (project-root store-path)
        d (document-at store token)
        _ (when-not (:path d)
            (err/raise :docs/not-anchored
                       (str "`" (:name d) "` has no file to re-anchor: it was registered without a path")
                       {:document (:name d) :tree (:tree d)}))
        anchor (inside-path store-path path)
        _ (when (nil? anchor)
            (err/raise :docs/outside-project
                       (str "`" file "` is outside the project of this store: a registered document needs a file inside it")
                       {:path path :document (:name d)}))
        _ (when (= anchor (:path d))
            (err/raise :cli/usage (str "`" (:name d) "` is already anchored there: " anchor)
                       {:document (:name d) :path anchor}))
        _ (tdocs/refuse-anchored! store anchor (:tree d))
        previous-file (if (fs/regular-file? (fs/path root (:path d))) "present" "missing")
        opts* {:path anchor :mint mint-unit :actor (:actor opts) :timestamp (timestamp)}
        res (if (:generic d)
              (let [{:keys [plan unchanged? blocks]} (tdocs/generic-update-plan host/host store d text opts*)
                    r (if plan (store/commit! backend store plan) {:store store :revision-id (:head store)})]
                {:revision-id (:revision-id r) :store (:store r)
                 :data {:name (:name d) :tree (:tree d) :path path :blocks blocks
                        :class (:class d) :anchor anchor :unchanged? (boolean unchanged?)}})
              ;; the same refusal `docs update` makes: nothing is committed for a noisy file
              (let [_ (let [md (or (:descriptor (mstore/model store (:model d))) {})]
                        (when-not (mstore/spec16? md) (refuse-noise! (:model d) md (:name d) text)))
                    {:keys [plan unchanged?]} (tdocs/update-plan host/host store (:tree d) text opts*)
                    r (if plan (store/commit! backend store plan) {:store store :revision-id (:head store)})]
                {:revision-id (:revision-id r) :store (:store r)
                 :data (assoc (doc-data (tdocs/document (:store r) (:tree d))) :unchanged? (boolean unchanged?))}))
        warnings (when (= "present" previous-file)
                   [{:type "docs/previous-file-present"
                     :message (str "the previous file " (:path d) " is still there and is no longer tracked:"
                                   " if it was a copy, it is a different unit — declare it with `docs track`/`docs ingest`")
                     :path (:path d) :document (:name d)}])]
    (out/ok "docs reanchor" {:store (cli-store/store-ref descriptor store-path)
                             :revision (:revision-id res)
                             :data (assoc (:data res)
                                          :reanchored {:from (:path d) :to anchor
                                                       :previous-file previous-file :reason reason})
                             :warnings (vec warnings)})))

(defn untrack
  "`docs untrack NAME`: stops tracking the document; its file stays. With
   `delete?` (the `docs delete` command) the file is removed too, and `--yes`
   is required (`docs/confirmation-required` otherwise, nothing touched)."
  ([args opts ctx] (untrack args opts ctx false))
  ([args opts ctx delete?]
   (let [sub (if delete? "delete" "untrack")
         token (cli-args/require-arg! "docs" args 0 "a document NAME" sub)
         _ (when (and delete? (not (:yes opts)))
             (err/raise :docs/confirmation-required "`docs delete` removes the file too: pass --yes to confirm" {:document token}))
         {:keys [backend descriptor store] store-path :path} (cli-store/open-for opts ctx)
         doc (document-at store token)
         ;; the state of the file BEFORE acting: the closure declaration says what was there
         file-state (cond (nil? (:path doc)) "none"
                          (fs/regular-file? (fs/path (project-root store-path) (:path doc))) "present"
                          :else "missing")
         {:keys [plan name path]} (tdocs/untrack-plan store token {:actor (:actor opts) :timestamp (timestamp)})
         r (store/commit! backend store plan)
         f (when path (fs/path (project-root store-path) path))]
     (when (and delete? f (fs/exists? f)) (fs/delete f))
     (out/ok (str "docs " sub) {:store (cli-store/store-ref descriptor store-path) :revision (:revision-id r)
                                :data {:name name :path path :file-deleted? (boolean (and delete? f))
                                       :closure {:file file-state :reason (:reason opts)}}}))))

(defn- document-text
  "The text of document `d`: its file as it is now, else the text the store
   keeps (an inline document). Nil when there is neither."
  [store store-path d]
  (let [root (project-root store-path)]
    (or (when-let [rel (:path d)]
          (let [f (fs/path root rel)] (when (fs/regular-file? f) (slurp (str f)))))
        (:source (or (tdocs/find-document store (:tree d))
                     (tdocs/generic-document store (:tree d)))))))

(defn- loc-key
  "Keys of a locator as keywords (EDN or JSON), recursively."
  [x]
  (cond (map? x) (into {} (map (fn [[k v]] [(keyword (name k)) (loc-key v)]) x))
        (vector? x) (mapv loc-key x)
        :else x))

(defn- parse-locator
  "A locator read from `--locator`: EDN or JSON (what `docs anchors` prints),
   keys as keywords and the values of `:type`/`:kind` as keywords. Malformed
   input is `cli/usage`."
  [text]
  (let [raw (or (err/rescue-any (fn [_] nil) (edn/read-string text))
                (err/rescue-any (fn [_] nil) (json/parse-string text)))]
    (when-not (or (map? raw) (vector? raw))
      (err/raise :cli/usage "`--locator` needs the locator of `docs anchors` as EDN or JSON" {:value text}))
    (let [l (loc-key raw)]
      (cond-> l
        (and (map? l) (map? (:block l))) (update-in [:block :type] #(some-> % keyword))
        (and (map? l) (contains? l :type)) (update :type keyword)
        (and (map? l) (contains? l :kind)) (update :kind keyword)))))

(defn- locator-kind
  "`:block` when the locator names a block (`:type`), `:item` / `:nested` when it
   names an item (`:field` + `:index`) or a nested key (`:field` + `:path`) of a
   field (K1h), `:span` when it is a block plus a range of graphemes of its
   plain text (S2b-r), `:section` when it is a path of titles."
  [loc]
  (cond (anchors/span-locator? loc) :span
        (:type loc) :block
        (and (map? loc) (contains? loc :field) (contains? loc :index)) :item
        (and (map? loc) (contains? loc :field) (contains? loc :path)) :nested
        (or (vector? loc) (sequential? loc)) :section
        (and (map? loc) (contains? loc :section)) :block
        :else :section))

(defn anchors
  "`docs anchors NAME [--sections]`: the structural anchors of a document as
   EVIDENCE (S2a, docs/v2/12 §1): its sections and blocks with a locator, a
   hash and, when the block layer aligns with the neutral AST, line and byte
   ranges, plus the reading contract they were computed under
   (`kimun.surface.markdown.anchors`). Typed or generic document. Read-only:
   it writes nothing to the store or to `<store>/cache/`, and it mints no id:
   a locator is evidence of where something was read, not an identity. `--sections`
   omits `:blocks`. Data: `{:name :tree :path :parse-contract :sections
   :blocks}`."
  [args opts {:keys [cwd] :as ctx}]
  (let [token (cli-args/require-arg! "docs" args 0 "a document NAME" "anchors")
        {:keys [descriptor store] store-path :path} (cli-store/open-for opts ctx)
        d (or (tdocs/resolve-any store token)
              (err/raise :docs/missing (str "no document named or identified by `" token "`") {:document token}))
        root (project-root store-path)
        ;; the file as it is now; else the text the store keeps (an inline document)
        text (or (when-let [rel (:path d)]
                   (let [f (fs/path root rel)] (when (fs/regular-file? f) (slurp (str f)))))
                 (:source (or (tdocs/find-document store (:tree d))
                              (tdocs/generic-document store (:tree d)))))
        _ (when (or (nil? text) (str/blank? (str text)))
            (err/raise :docs/no-file
                       (str "`" (:name d) "` has no readable source: its file is not there and it stores no text")
                       {:document (:name d) :path (:path d)}))
        contract (parse-contract/record host/host (parse-contract/environment) (or (:class d) "generic/md"))
        a (anchors/anchors host/host (str text) {:spans (capture/aligned-spans (str text))
                                                 :parse-contract contract})]
    (out/ok "docs anchors"
            {:store (cli-store/store-ref descriptor store-path)
             :revision (:head store)
             :data (cond-> {:name (:name d) :tree (:tree d) :path (:path d)
                            :parse-contract (:parse-contract a)
                            :sections (:sections a)}
                     (not (:sections opts)) (assoc :blocks (:blocks a)))})))

;; ---------------------------------------------------------------- structural marks (K1e)

(defn- require-reference!
  "A document whose structure is store: by reference, in a format-4 store. A
   mark lives in the tree descriptor, which an inline or legacy document does
   not have."
  [d]
  (when (or (:inline d) (nil? (:path d)))
    (err/raise :docs/not-anchored
               (str "`" (:name d) "` is not a by-reference document of a format-4 store: its structure is not registered as cache")
               {:document (:name d) :path (:path d)}))
  d)

(defn- mark-target
  "The document `token` names and its text, after the by-reference check."
  [store store-path token]
  (let [d (require-reference! (or (tdocs/resolve-any store token)
                                  (err/raise :docs/missing (str "no document named or identified by `" token "`") {:document token})))
        text (document-text store store-path d)]
    (when (or (nil? text) (str/blank? (str text)))
      (err/raise :docs/no-file (str "`" (:name d) "` has no readable source") {:document (:name d) :path (:path d)}))
    [d (str text)]))

(defn- field-locator
  "The locator of an item or a nested key given as options (K1h): `--field F
   --index N` or `--field F --path a.b` (keys split on `.`; a key with a dot is
   written with `--locator`). Nil when neither `--index` nor `--path` is there
   (a bare `--field` names a field unit)."
  [opts]
  (let [f (:field opts)]
    (cond (and f (some? (:index opts)) (some? (:path opts)))
          (err/raise :cli/usage "`--index` and `--path` are alternatives: an item or a nested key" {:group "docs" :subcommand "mark"})
          (and f (some? (:index opts)))
          {:field f :index (or (parse-long (str (:index opts)))
                               (err/raise :cli/usage "`--index` needs a whole number: the position of the item" {:group "docs" :subcommand "mark" :value (:index opts)}))}
          (and f (some? (:path opts))) {:field f :path (str/split (str (:path opts)) #"\.")}
          (or (some? (:index opts)) (some? (:path opts)))
          (err/raise :cli/usage "`--index` / `--path` need --field F: the field the item or the nested key lives in" {:group "docs" :subcommand "mark"}))))

(defn- range-opt
  "`--range S:E` as `[S E]`: graphemes of the NFC plain text of the block, `[S E)`.
   Anything else is `cli/usage`; whether it is empty or past the text is decided
   against the block (`resolve-locator!`)."
  [text]
  (let [[_ s e] (re-matches #"\s*(\d+)\s*:\s*(\d+)\s*" (str text))]
    (if s
      [(parse-long s) (parse-long e)]
      (err/raise :cli/usage "`--range` is S:E, two whole numbers: [S E) in graphemes of the plain text of the block"
                 {:group "docs" :subcommand "mark" :value text}))))

(defn- locator-opt
  "The locator the options give: `--locator L`, or the item/nested one of
   `field-locator`; nil when there is none. Both at once is `cli/usage`."
  [opts]
  (let [fl (field-locator opts)
        usage! #(err/raise :cli/usage % {:group "docs" :subcommand "mark"})]
    (when (and fl (:locator opts))
      (usage! "give --locator L or --field F with --index/--path, not both"))
    (let [l (or fl (some-> (:locator opts) str parse-locator))]
      (if (some? (:range opts))
        (do (when (or fl (not (and (map? l) (:type l))))
              (usage! "`--range S:E` goes with --locator B, the locator of a BLOCK (a paragraph or a heading), and not with --field"))
            {:block l :range (range-opt (:range opts))})
        l))))

(defn- field-payload
  "The payload of typed document `d` read from `text`, where its items and nested
   keys are marked (K1h); nil for a generic document or one that no longer
   extracts (its field marks then read `:missing`)."
  [store d text]
  (when (and (not (:generic d)) (:model d))
    (:payload (tdocs/payload-of store (assoc d :source text)))))

(defn- resolve-locator!
  "The anchor of `locator` in `text` (the S2a anchors; for a typed document also
   the items and nested keys of its payload, K1h), raising `docs/anchor-unresolved`
   when nothing is there and `docs/not-anchored` when the locator names a field
   of a document that has none (a generic one)."
  [locator text store d]
  (let [kind (locator-kind locator)
        _ (when (and (#{:item :nested} kind) (or (:generic d) (nil? (:model d))))
            (err/raise :docs/not-anchored
                       (str "`" (:name d) "` is a generic document: it has no fields, so no item or nested key to mark")
                       {:document (:name d) :locator locator}))
        as (tdocs/mark-anchors host/host store d text)
        span (when (= :span kind) (anchors/span-anchor as locator))
        _ (when (:error span)
            (err/raise (if (= :invalid-range (:error span)) :cli/usage :docs/anchor-unresolved)
                       (str (:why span) ": " (pr-str locator))
                       (assoc (dissoc span :why) :locator locator :group "docs" :subcommand "mark")))
        a (or span (anchors/resolve-anchor as locator))]
    (or a
        (err/raise :docs/anchor-unresolved
                   (str "no anchor of this document has that locator: " (pr-str locator))
                   {:locator locator}))))

(defn- taken-locator
  "The id of the mark of `marks` (other than `except`) whose locator is `locator`."
  [marks locator except]
  (first (keep (fn [[id m]] (when (and (not= id except) (= locator (:locator m))) id)) marks)))

(defn- move-unit
  "`docs mark OTHER --unit ID --from NAME (--locator L | --field F) --reason TEXT`:
   DECLARES that unit `ID` of document NAME is now anchored in OTHER (K1f, docs/v2/14
   §21 rule 3): the same id, one revision touching both trees. See `mark`."
  [args opts {:keys [env cwd] :as ctx}]
  (let [token (cli-args/require-arg! "docs" args 0 "a document NAME" "mark")
        usage! (fn [msg] (err/raise :cli/usage msg {:group "docs" :subcommand "mark"}))
        unit (or (:unit opts) (usage! "`docs mark --from` needs --unit ID: the unit that moves"))
        _ (when-not (string? (:reason opts)) (usage! "`docs mark --from` needs --reason TEXT: the declaration says what happened"))
        _ (when-not (= 1 (count (filter some? [(or (:locator opts) (field-locator opts))
                                               (when-not (field-locator opts) (:field opts))])))
            (usage! "`docs mark --from` needs exactly one of --locator L (a structural unit; or --field F --index N / --path a.b for an item or a nested key) or --field F (a field unit)"))
        {:keys [backend descriptor store] store-path :path} (cli-store/open-for opts ctx)
        [from from-text] (mark-target store store-path (:from opts))
        [to to-text] (mark-target store store-path token)
        _ (when (= (:tree from) (:tree to))
            (usage! "`docs mark --from` moves a unit to ANOTHER document; re-anchoring in the same one is `docs mark --unit`"))
        locator (locator-opt opts)
        a (when locator (resolve-locator! locator to-text store to))
        mark (when locator
               (let [label (or (:label opts) (:label (get (:marks from) unit)))]
                 (cond-> {:kind (locator-kind locator) :locator locator :hash (:hash a)}
                   label (assoc :label label))))
        declared (cond-> {:kind "unit-move" :unit unit :from (:name from) :to (:name to) :reason (:reason opts)}
                   locator (assoc :locator locator)
                   (and (:field opts) (not locator)) (assoc :field (:field opts)))
        mp (tdocs/move-unit-plan host/host store from to
                                 {:unit unit :mark mark :field (when-not locator (:field opts)) :from-text from-text :to-text to-text
                                  :actor (:actor opts) :timestamp (timestamp)})
        r (store/commit! backend store (:plan mp))]
    (out/ok "docs mark"
            {:store (cli-store/store-ref descriptor store-path) :revision (:revision-id r)
             :data (assoc (doc-data (tdocs/resolve-any (:store r) (:tree to)))
                          :id unit :moved (select-keys declared [:unit :from :to :locator :field]))})))

(defn- mark-here
  "`docs mark NAME --locator L [--label TEXT] [--reason TEXT]`, or
   `docs mark NAME --unit ID --locator L --reason TEXT`: DECLARES a structural
   unit on a section or a block of a by-reference document (K1e, docs/v2/14
   §20–§21). Without `--unit` it mints an id; with it, the SAME unit is
   re-anchored (the `--reason` is then required: the actor says what happened).
   The locator is what `docs anchors` prints (EDN or JSON). The mark records the
   locator, the current hash and an optional label in the tree descriptor
   (store, never part of the root hash). Rejections write nothing: no anchor there
   (`docs/anchor-unresolved`), the locator a mark already has (`docs/exists`),
   an unknown unit (`docs/missing`), a document that is not by reference
   (`docs/not-anchored`), or no `--reason` on a re-anchor (`cli/usage`).
   Data: the document map plus `:marked {:id :locator :kind :label}`."
  [args opts {:keys [env cwd] :as ctx}]
  (let [token (cli-args/require-arg! "docs" args 0 "a document NAME" "mark")
        locator (locator-opt opts)
        _ (when-not locator
            (err/raise :cli/usage "`docs mark` needs --locator L (the locator `docs anchors` prints), or --field F with --index N / --path a.b (an item or a nested key)"
                       {:group "docs" :subcommand "mark"}))
        unit (:unit opts)
        _ (when (and unit (not (string? (:reason opts))))
            (err/raise :cli/usage "`docs mark --unit` needs --reason TEXT: the declaration says what happened"
                       {:group "docs" :subcommand "mark"}))
        {:keys [backend descriptor store] store-path :path} (cli-store/open-for opts ctx)
        [d text] (mark-target store store-path token)
        a (resolve-locator! locator text store d)
        marks (or (:marks d) {})
        _ (when-let [holder (taken-locator marks locator unit)]
            (err/raise :docs/exists
                       (str "a mark of `" (:name d) "` already has that locator: " holder)
                       {:document (:name d) :unit holder :locator locator}))
        _ (when (and unit (not (contains? marks unit)))
            (err/raise :docs/missing (str "no unit `" unit "` in `" (:name d) "`") {:document (:name d) :unit unit}))
        id (or unit (ports/ulid (:ids host/host)))
        kind (locator-kind locator)
        mark {:kind kind :locator locator :hash (:hash a)}
        mark (cond-> mark (:label opts) (assoc :label (:label opts)))
        mp (tdocs/mark-plan host/host store (:tree d) text
                            {:id id :actor (:actor opts) :timestamp (timestamp)
                             :kind kind :locator locator :hash (:hash a)
                             :label (:label opts)})
        r (store/commit! backend store (:plan mp))]
    (out/ok "docs mark"
            {:store (cli-store/store-ref descriptor store-path) :revision (:revision-id r)
             :data (assoc (doc-data (tdocs/resolve-any (:store r) (:tree d)))
                          :marked (select-keys mark [:kind :locator :hash :label])
                          :id id)})))

(defn mark
  "`docs mark`: declares a structural unit (`mark-here`) or, with `--from NAME`,
   MOVES unit `--unit ID` of NAME to this document (K1f): `docs mark OTHER --unit
   ID --from NAME (--locator L | --field F) --reason TEXT`. The unit keeps its
   id, leaves NAME and is anchored in OTHER (a locator that resolves in OTHER, or
   a field of OTHER's payload without a unit), in ONE revision. A
   mark of a generic document moves too; a field unit needs two typed
   documents. kimun never moves a unit on its own. Rejections write nothing:
   no `--reason` or neither/both of `--locator`/`--field` (`cli/usage`), a
   locator or field of OTHER that already holds a unit (`docs/exists`: units are
   not fused, close the other first), an unknown unit (`docs/missing`), a
   locator that does not resolve in OTHER (`docs/anchor-unresolved`), a field
   that is not in OTHER's payload (`docs/not-anchored`). Data: the document map
   of OTHER plus `:id` and `:moved`."
  [args opts ctx]
  (if (:from opts) (move-unit args opts ctx) (mark-here args opts ctx)))

(defn unmark
  "`docs unmark NAME ID [--reason TEXT]`: DECLARES the closure of unit `ID` of a
   document. A structural unit (K1e): the mark leaves the tree descriptor. A FIELD
   unit of a typed document (K1f): the id leaves `:units` and is never reused;
   `--reason` is then required, and a field still in the payload gets a NEW id on
   the next write (the replacement is declared, 14 §21 rule 5). The document itself is written as an update of the same
   text. Unknown unit: `docs/missing`. Data: the document map plus `:unmarked`."
  [args opts {:keys [env cwd] :as ctx}]
  (let [token (cli-args/require-arg! "docs" args 0 "a document NAME" "unmark")
        id (cli-args/require-arg! "docs" args 1 "a unit ID" "unmark")
        {:keys [backend descriptor store] store-path :path} (cli-store/open-for opts ctx)
        [d text] (mark-target store store-path token)
        mark? (contains? (or (:marks d) {}) id)
        field (when-not mark? (tdocs/unit-field d id))
        _ (when-not (or mark? field)
            (err/raise :docs/missing (str "no unit `" id "` in `" (:name d) "`") {:document (:name d) :unit id}))
        _ (when (and field (not (string? (:reason opts))))
            (err/raise :cli/usage "`docs unmark` of a field unit needs --reason TEXT: the declaration says what happened"
                       {:group "docs" :subcommand "unmark"}))
        base {:id id :actor (:actor opts) :timestamp (timestamp)}
        mp (if mark?
             (tdocs/mark-plan host/host store (:tree d) text (assoc base :unmark? true))
             (tdocs/close-unit-plan host/host store d text base))
        r (store/commit! backend store (:plan mp))]
    (out/ok "docs unmark"
            {:store (cli-store/store-ref descriptor store-path) :revision (:revision-id r)
             :data (assoc (doc-data (tdocs/resolve-any (:store r) (:tree d))) :unmarked id)})))

(defn marks
  "`docs marks NAME`: the structural marks of a document and their state against
   the text read now (K1e): `[{:id :kind :label :locator :status :candidates}]`
   sorted by id, plus `:parse-contract`. Read-only: it writes nothing to the
   store or to `<store>/cache/`. The state is derived (`anchored`, `mutated`,
   `ambiguous`, `missing`) and the candidates are PROPOSALS: kimun never decides
   that a unit moved, was split or is another one."
  [args opts {:keys [cwd] :as ctx}]
  (let [token (cli-args/require-arg! "docs" args 0 "a document NAME" "marks")
        {:keys [descriptor store] store-path :path} (cli-store/open-for opts ctx)
        d (or (tdocs/resolve-any store token)
              (err/raise :docs/missing (str "no document named or identified by `" token "`") {:document token}))
        text (or (document-text store store-path d) "")
        contract (parse-contract/record host/host (parse-contract/environment) (or (:class d) "generic/md"))
        r (tdocs/marks-of host/host (str text) d contract (field-payload store d (str text)))]
    (out/ok "docs marks"
            {:store (cli-store/store-ref descriptor store-path) :revision (:head store)
             :data {:name (:name d) :tree (:tree d) :path (:path d)
                    :parse-contract (:parse-contract r) :marks (:marks r)}})))

(defn show
  "`docs show NAME [--format md] [--ast]`: the document map with payload, hashes and
   `:markdown` (its source; `--format md` prints it). With `--ast` (or `--format ast`,
   once the global `--format` accepts it) the data is instead the document's SLDB-MD
   AST (`kimun.surface.markdown.plan/store->ast`, docs/v2/04 §8) as `{:name :tree :ast}`;
   an untyped document is resolved too."
  [args opts ctx]
  (let [{:keys [descriptor store] store-path :path} (cli-store/open-for opts ctx)
        token (cli-args/require-arg! "docs" args 0 "a document NAME" "show")
        ast? (or (:ast opts) (= "ast" (:format opts)))]
    (out/ok "docs show" {:store (cli-store/store-ref descriptor store-path) :revision (:head store)
                         :data (if ast?
                                 (let [{:keys [name tree]} (resolve-document store token)]
                                   {:name name :tree tree
                                    :ast (or (tdocs/document-ast store (or (tdocs/find-document store token)
                                                                           (tdocs/generic-document store tree) tree))
                                             (some-> (or (tdocs/find-document store token) (tdocs/generic-document store tree))
                                                     tdocs/file-missing!)
                                             (md/store->ast store tree))})
                                 (let [d (tdocs/document store token)
                                       found (tdocs/find-document store token)]
                                   (assoc (doc-data d) :markdown (:source d)
                                          :units (tdocs/unit-states store found)
                                          :marks (:marks (tdocs/marks-of host/host (str (:source d)) found nil
                                                                                  (field-payload store found (str (:source d))))))))})))

(defn update-any
  "`docs update`: for a tracked typed document, `docs update NAME [FILE]`
   (`update-typed`); otherwise the untyped re-ingest `docs update FILE --name N`."
  [args opts {:keys [env cwd] :as ctx}]
  (let [store (:store (cli-store/open! (cli-store/resolve-store opts env cwd)))
        by-arg (some->> (get args 0) (tdocs/find-document store))
        by-name (some->> (:name opts) (tdocs/find-document store))]
    (cond by-arg (update-typed by-arg args opts ctx)
          by-name (update-typed by-name [(:name by-name) (get args 0)] opts ctx)
          ;; a payload for a document nobody tracks: name it (v1: "Doc 'x' not registered.")
          (or (:data opts) (seq (:set opts)))
          (let [token (or (:name opts) (get args 0))]
            (err/raise :docs/missing (str "no tracked document `" token "`: nothing to update with a payload") {:document token}))
          :else (update-doc args opts ctx))))

(def spec
  "Options of the `docs` group (babashka.cli spec, merged by `kimun.cli.main`)."
  {:name {:ref "NAME" :desc "Document name (docs ingest, docs update, docs track)"}
   :data {:ref "FILE" :desc "Payload file, JSON or YAML (docs create, docs update)"}
   :set {:ref "FIELD=VALUE" :desc "One payload field (repeatable)" :coerce [:string]}
   :path {:ref "PATH" :desc "A project file, relative to the working directory: the new document (docs create) or the file to ask about (declare about)"}
   :inline {:desc "Store the source text in the store instead of reading it from the file (docs create, docs track)" :coerce :boolean}
   :model {:ref "NAME" :desc "Only documents of this model (docs list)"}
   :force {:desc "Track a file that does not round-trip under its model (docs track)" :coerce :boolean}
   :ast {:desc "Print the document's SLDB-MD AST instead of its payload (docs show)" :coerce :boolean}
   :yes {:desc "Confirm a destructive command (docs delete)" :coerce :boolean}
   :reason {:ref "TEXT" :desc "What happened (docs reanchor, docs untrack, docs delete)"}
   :sections {:desc "Only the sections of the document (docs anchors)" :coerce :boolean}
   :locator {:ref "L" :desc "Locator of a section or a block, as `docs anchors` prints it (docs mark)"}
   :unit {:ref "ID" :desc "Unit to re-anchor, move or close (docs mark, docs unmark)"}
   :label {:ref "TEXT" :desc "Optional label of a structural unit (docs mark)"}
   :index {:ref "N" :desc "Position of the list item to mark, with --field (docs mark)"}
   :range {:ref "S:E" :desc "Graphemes [S E) of the plain text of the block --locator names (docs mark)"}})

(def commands
  "Subcommand name → command fn `(fn [args opts ctx] envelope)`."
  {"ingest" ingest "update" update-any "render" render "list" list-docs "create" create "track" track
   "untrack" untrack "delete" (fn [args opts ctx] (untrack args opts ctx true))
   "reanchor" reanchor "anchors" anchors "mark" mark "unmark" unmark "marks" marks
   "show" show})

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
