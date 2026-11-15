(ns kimun.cli.commands.docs
  "The `kimun docs` group (docs/v2/06 §3, first slice of S2): `ingest`,
   `update`, `render` and `list`. A document is a `:document` tree of the
   store built by `kimun.surface.markdown`; every write is a `TransactionPlan`
   through `kimun.kernel.store/commit!` — no side door. Every command is
   `(fn [args opts ctx] envelope)` where `args` are the positional tokens
   after the subcommand and `ctx` is `{:env env :cwd cwd}`."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [kimun.cli.compose :as compose]
            [kimun.cli.linked :as linked]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.models.extract :as mextract]
            [kimun.surface.models.noise :as noise]
            [kimun.surface.models.render :as mrender]
            [kimun.surface.models.store :as mstore]
            [kimun.host.default :as host]
            [kimun.kernel.canon :as canon]
            [kimun.kernel.err :as err]
            [kimun.kernel.ports :as ports]
            [kimun.kernel.revision :as revision]
            [kimun.kernel.route :as route]
            [kimun.kernel.store :as store]
            [kimun.kernel.tree :as tree]
            [kimun.surface.markdown.anchors :as anchors]
            [kimun.surface.markdown.ast :as ast]
            [kimun.surface.markdown.plan :as md]
            [kimun.surface.models.blocks :as blocks]
            [kimun.surface.parse-contract :as parse-contract]))

(defn- store-ref [descriptor path] {:name (:name descriptor) :path path})

(defn timestamp
  "The current UTC instant in the plan's shape, `yyyy-MM-ddTHH:mm:ss.SSSZ`."
  []
  #?(:clj (.format (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                   (java.time.ZonedDateTime/now java.time.ZoneOffset/UTC))
     :cljs (.toISOString (js/Date.))))

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

(defn- require-arg! [args i what sub]
  (or (get args i)
      (err/raise :cli/usage (str "`docs " sub "` needs " what) {:group "docs" :subcommand sub})))

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
   and the file is watched by `stores sense`. In an `:inline` or format-1
   store, or for a file outside the project, the tree is persisted as before.
   Data: `{:name :tree :path :blocks}` plus `:class :anchor` when registered.
   Raises `:docs/exists` (exit 3) when a document with that name is already in
   the store."
  [args opts {:keys [env cwd]}]
  (let [file (require-arg! args 0 "a Markdown FILE" "ingest")
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
        (out/ok "docs ingest" {:store (store-ref descriptor store-path)
                               :revision (:revision-id r)
                               :data {:name name :tree tree-id :path path :blocks blocks
                                      :class tdocs/generic-class :anchor anchor}}))
      (let [plan (md/markdown->plan host/host text {:tree-id tree-id :name name :actor (:actor opts)
                                                    :timestamp (timestamp) :base (:head store)})
            r (store/commit! backend store plan)
            t (get-in r [:store :trees tree-id])]
        (out/ok "docs ingest" {:store (store-ref descriptor store-path)
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
  (let [file (require-arg! args 0 "a Markdown FILE" "update")
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
        (out/ok "docs update" {:store (store-ref descriptor store-path)
                               :revision (:revision-id r)
                               :data {:name name :tree tree :path path :blocks blocks
                                      :class (:class generic) :anchor anchor :unchanged? (boolean unchanged?)}}))
      (let [plan (md/markdown->update-plan host/host store tree text
                                           {:actor (:actor opts) :timestamp (timestamp) :base (:head store)})
            r (store/commit! backend store plan)
            t (get-in r [:store :trees tree])]
        (out/ok "docs update" {:store (store-ref descriptor store-path)
                               :revision (:revision-id r)
                               :data {:name name :tree tree :path path
                                      :blocks (count (tree/children-at t []))}})))))

(defn render
  "`docs render NAME|TREE`: the canonical Markdown of the document, as the
   envelope's data (a string, so `--format text` prints the document itself)."
  [args opts {:keys [env cwd]}]
  (let [token (require-arg! args 0 "a document NAME or tree id" "render")
        {:keys [descriptor store] store-path :path} (cli-store/open! (cli-store/resolve-store opts env cwd))
        {:keys [tree]} (resolve-document store token)]
    (out/ok "docs render" {:store (store-ref descriptor store-path)
                           :revision (:head store)
                           :data (or (tdocs/document-markdown store (or (tdocs/find-document store token)
                                                                        (tdocs/generic-document store tree) tree))
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

(defn- doc-data [d] (select-keys d [:name :qualified :model :model-version :state :path :tree :payload :error :source-hash :content-hash :field-hashes]))

(defn- done
  "The envelope of a document write; `token` is the tree id of the document
   written (a short name may be shared, K8)."
  [command descriptor store-path r store' token extra]
  (out/ok command (merge {:store (store-ref descriptor store-path) :revision (:revision-id r)
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
  (let [token (require-arg! args 0 "a MODEL" "create") name (require-arg! args 1 "a document NAME" "create")
        {:keys [opened alias home]} (linked/target opts env cwd)
        {:keys [backend descriptor store] store-path :path} opened
        payload (read-payload opts cwd)
        {model :name d :descriptor :as m} (linked/resolve-model opened home token)
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
                                          :path path :actor (:actor opts) :timestamp (timestamp) :operation "docs create"
                                          :inline (:inline opts)})
        r (store/commit! backend store plan)]
    (done "docs create" descriptor store-path r (:store r) tree {:warnings (write-file! root path text)})))

(defn track
  "`docs track MODEL FILE [--name N]`: tracks an existing Markdown file as a
   document of MODEL (the file is not rewritten). A file that does not
   round-trip — rendering its extracted payload gives another text — is
   refused (`docs/not-idempotent`) unless `--force`. MODEL and `--store`
   accept a linked store as in `docs create`. Data: the document map."
  [args opts {:keys [env cwd]}]
  (let [token (require-arg! args 0 "a MODEL" "track") file (require-arg! args 1 "a Markdown FILE" "track")
        [p text] (read-file! file cwd)
        {:keys [opened home]} (linked/target opts env cwd)
        {:keys [backend descriptor store] store-path :path} opened
        name (name-of opts p)
        {model :name d :descriptor :as m} (linked/resolve-model opened home token)
        _ (refuse-tracked! store name (relative-path (project-root store-path) p))
        _ (when-not (:force opts)
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
  (let [{:keys [backend descriptor store] store-path :path} (open opts ctx)
        root (project-root store-path)
        payload? (or (:data opts) (seq (:set opts)))
        current (:descriptor (mstore/model store (:model doc)))
        full (tdocs/document store (:tree doc))
        no-input? (and (not payload?) (not (get args 1)))
        stored (when (and no-input? (:path doc)) (second (read-file! (str (fs/path root (:path doc))) cwd)))
        migrate? (and no-input? (= :superseded (:state doc)) (= (or stored (:source full)) (:source full)))
        text (cond payload? (str (mrender/render current (merge (:payload full) (read-payload opts cwd))
                                                    (compose/context store store-path)) "\n")
                   (get args 1) (second (read-file! (get args 1) cwd))
                   migrate? (tdocs/move-text store full nil (compose/context store store-path))
                   stored stored
                   :else (err/raise :cli/usage "`docs update` needs a FILE, --data or --set for a document without a file" {}))
        _ (refuse-noise! (:model doc) (or current {}) (:name doc) text)
        {:keys [plan unchanged?]} (tdocs/update-plan host/host store (:tree doc) text
                                                     {:mint mint-unit :actor (:actor opts) :timestamp (timestamp)})
        r (if plan (store/commit! backend store plan) {:store store :revision-id (:head store)})
        warnings (if (and (or payload? migrate?) (:path doc)) (write-file! root (:path doc) text) [])]
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
   journal (`:declared {:kind reanchor …}`). When the previous file is
   still there it stays untracked and the answer warns
   `docs/previous-file-present`. Data: what `docs update` returns for that
   document, plus `:reanchored {:from :to :previous-file :reason}`."
  [args opts {:keys [env cwd] :as ctx}]
  (let [token (require-arg! args 0 "a document NAME" "reanchor")
        file (require-arg! args 1 "a Markdown FILE" "reanchor")
        reason (:reason opts)
        _ (when (or (not (string? reason)) (str/blank? (str/trim reason)))
            (err/raise :cli/usage "`docs reanchor` needs --reason TEXT: the declaration says what happened"
                       {:group "docs" :subcommand "reanchor"}))
        [path text] (read-file! file cwd)
        {:keys [backend descriptor store] store-path :path} (open opts ctx)
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
        declared {:kind "reanchor" :from (:path d) :to anchor
                  :previous-file previous-file :reason reason}
        opts* {:path anchor :mint mint-unit :actor (:actor opts) :timestamp (timestamp)
               :operation "docs reanchor" :declared declared}
        res (if (:generic d)
              (let [{:keys [plan unchanged? blocks]} (tdocs/generic-update-plan host/host store d text opts*)
                    r (if plan (store/commit! backend store plan) {:store store :revision-id (:head store)})]
                {:revision-id (:revision-id r) :store (:store r)
                 :data {:name (:name d) :tree (:tree d) :path path :blocks blocks
                        :class (:class d) :anchor anchor :unchanged? (boolean unchanged?)}})
              ;; the same refusal `docs update` makes: nothing is committed for a noisy file
              (let [_ (refuse-noise! (:model d) (or (:descriptor (mstore/model store (:model d))) {}) (:name d) text)
                    {:keys [plan unchanged?]} (tdocs/update-plan host/host store (:tree d) text opts*)
                    r (if plan (store/commit! backend store plan) {:store store :revision-id (:head store)})]
                {:revision-id (:revision-id r) :store (:store r)
                 :data (assoc (doc-data (tdocs/document (:store r) (:tree d))) :unchanged? (boolean unchanged?))}))
        warnings (when (= "present" previous-file)
                   [{:type "docs/previous-file-present"
                     :message (str "the previous file " (:path d) " is still there and is no longer tracked:"
                                   " if it was a copy, it is a different unit — declare it with `docs track`/`docs ingest`")
                     :path (:path d) :document (:name d)}])]
    (out/ok "docs reanchor" {:store (store-ref descriptor store-path)
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
         token (require-arg! args 0 "a document NAME" sub)
         _ (when (and delete? (not (:yes opts)))
             (err/raise :docs/confirmation-required "`docs delete` removes the file too: pass --yes to confirm" {:document token}))
         {:keys [backend descriptor store] store-path :path} (open opts ctx)
         doc (document-at store token)
         ;; the state of the file BEFORE acting: the closure declaration says what was there
         file-state (cond (nil? (:path doc)) "none"
                          (fs/regular-file? (fs/path (project-root store-path) (:path doc))) "present"
                          :else "missing")
         {:keys [plan name path]} (tdocs/untrack-plan store token {:actor (:actor opts) :timestamp (timestamp)
                                                                   :operation (str "docs " sub)
                                                                   :declared {:kind "closure" :file file-state
                                                                              :reason (:reason opts)
                                                                              :file-deleted? (boolean delete?)}})
         r (store/commit! backend store plan)
         f (when path (fs/path (project-root store-path) path))]
     (when (and delete? f (fs/exists? f)) (fs/delete f))
     (out/ok (str "docs " sub) {:store (store-ref descriptor store-path) :revision (:revision-id r)
                                :data {:name name :path path :file-deleted? (boolean (and delete? f))
                                       :closure {:file file-state :reason (:reason opts)}}}))))

(defn- aligned-spans
  "Line spans `[[start end) …]` of the top-level blocks of `text` when the block
   layer (`kimun.surface.models.blocks/parse`, the pod when it is there) matches
   the neutral AST one to one: same count, in order, and each slice parsed
   alone yields exactly one block with the digest of its AST child. Returns nil
   when that does not hold, so no range is invented from a misaligned layer."
  [host text]
  (let [children (:children (ast/parse host text))]
    (try
      (let [blocks (blocks/parse text)
            spans (mapv :map blocks)
            lines (vec (clojure.string/split-lines text))
            slice (fn [[ls le]] (clojure.string/join "\n" (subvec lines (long ls)
                                                                  (long (min le (count lines))))))
            ok? (and (every? some? spans) (= (count blocks) (count children))
                     (every? true? (map (fn [blk b]
                                          (let [kids (:children (ast/parse host (slice (:map blk))))]
                                            (and (= 1 (count kids))
                                                 (= (canon/digest host (first kids))
                                                    (canon/digest host b)))))
                                        blocks children)))]
        (when ok? spans))
      (catch Exception _ nil))))

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
  (let [token (require-arg! args 0 "a document NAME" "anchors")
        {:keys [descriptor store] store-path :path} (open opts ctx)
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
        a (anchors/anchors host/host (str text) {:spans (aligned-spans host/host (str text))
                                                 :parse-contract contract})]
    (out/ok "docs anchors"
            {:store (store-ref descriptor store-path)
             :revision (:head store)
             :data (cond-> {:name (:name d) :tree (:tree d) :path (:path d)
                            :parse-contract (:parse-contract a)
                            :sections (:sections a)}
                     (not (:sections opts)) (assoc :blocks (:blocks a)))})))

(defn show
  "`docs show NAME [--format md] [--ast]`: the document map with payload, hashes and
   `:markdown` (its source; `--format md` prints it). With `--ast` (or `--format ast`,
   once the global `--format` accepts it) the data is instead the document's SLDB-MD
   AST (`kimun.surface.markdown.plan/store->ast`, docs/v2/04 §8) as `{:name :tree :ast}`;
   an untyped document is resolved too."
  [args opts ctx]
  (let [{:keys [descriptor store] store-path :path} (open opts ctx)
        token (require-arg! args 0 "a document NAME" "show")
        ast? (or (:ast opts) (= "ast" (:format opts)))]
    (out/ok "docs show" {:store (store-ref descriptor store-path) :revision (:head store)
                         :data (if ast?
                                 (let [{:keys [name tree]} (resolve-document store token)]
                                   {:name name :tree tree
                                    :ast (or (tdocs/document-ast store (or (tdocs/find-document store token)
                                                                           (tdocs/generic-document store tree) tree))
                                             (md/store->ast store tree))})
                                 (let [d (tdocs/document store token)]
                                   (assoc (doc-data d) :markdown (:source d)
                                          :units (tdocs/unit-states store (tdocs/find-document store token)))))})))

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
   :path {:ref "PATH" :desc "File of the new document (docs create)"}
   :inline {:desc "Store the source text in the store instead of reading it from the file (docs create, docs track)" :coerce :boolean}
   :model {:ref "NAME" :desc "Only documents of this model (docs list)"}
   :force {:desc "Track a file that does not round-trip under its model (docs track)" :coerce :boolean}
   :ast {:desc "Print the document's SLDB-MD AST instead of its payload (docs show)" :coerce :boolean}
   :yes {:desc "Confirm a destructive command (docs delete)" :coerce :boolean}
   :reason {:ref "TEXT" :desc "What happened (docs reanchor, docs untrack, docs delete)"}
   :sections {:desc "Only the sections of the document (docs anchors)" :coerce :boolean}})

(def commands
  "Subcommand name → command fn `(fn [args opts ctx] envelope)`."
  {"ingest" ingest "update" update-any "render" render "list" list-docs "create" create "track" track
   "untrack" untrack "delete" (fn [args opts ctx] (untrack args opts ctx true))
   "reanchor" reanchor "anchors" anchors "show" show "status" status})

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
