(ns kimun.cli.store
  "Locating, opening and creating the `.kimun/` store of the CLI (docs/v2/06
   §B): discovery walks up from the working directory, `--store` and
   `KIMUN_STORE` override it, and every open goes through
   `kimun.kernel.store/open` with the standard host."
  (:require [babashka.fs :as fs]
            [kimun.host.default :as host]
            [kimun.host.fs-store :as fs-store]
            [kimun.kernel.err :as err]
            [clojure.string :as str]
            [kimun.kernel.store :as store]))

(def dirname
  "Name of the store directory inside a project."
  ".kimun")

(defn descriptor-path
  "Path of `store.edn` inside store directory `dir`."
  [dir] (str (fs/path dir "store.edn")))

(defn store?
  "Whether `dir` holds a `store.edn`."
  [dir] (fs/exists? (descriptor-path dir)))

(defn discover
  "Walks up from `start-dir` to the filesystem root looking for
   `<dir>/.kimun/store.edn`; returns the `.kimun` path string or nil."
  [start-dir]
  (loop [dir (fs/absolutize start-dir)]
    (when dir
      (let [candidate (fs/path dir dirname)]
        (if (store? candidate)
          (str candidate)
          (recur (fs/parent dir)))))))

(defn normalize-store-path
  "A user-supplied store path: the `.kimun` directory itself, or a project
   directory that contains one. Returns the `.kimun` path string."
  [p]
  (let [p (fs/absolutize p)
        nested (fs/path p dirname)]
    (cond (= dirname (str (fs/file-name p))) (str p)
          (store? nested) (str nested)
          :else (str p))))

(def ^:private link-name-re #"[A-Za-z0-9][A-Za-z0-9._-]*")

(declare links)

(defn- registry-stores
  "`.kimun` directories whose registries are searched for a link name: the
   `$KIMUN_STORE` store when set, then every store found walking up from
   `cwd` (the current store first, then its ancestors)."
  [env cwd]
  (let [from-env (when (seq (get env "KIMUN_STORE")) (normalize-store-path (get env "KIMUN_STORE")))
        walk (loop [dir (fs/absolutize cwd) acc []]
               (if dir
                 (let [c (fs/path dir dirname)]
                   (recur (fs/parent dir) (if (store? c) (conj acc (str c)) acc)))
                 acc))]
    (distinct (concat (when (and from-env (store? from-env)) [from-env]) walk))))

(defn- link-target
  "The `.kimun` directory that link `link-name` points to in the nearest
   registry (see `registry-stores`) that has it, or nil. A link whose target
   is not a store raises `:cli/no-store` naming the link and its registry."
  [link-name env cwd]
  (some (fn [reg]
          (when-let [l (first (filter #(= link-name (:name %)) (links reg)))]
            (if (= :ok (:status l))
              (:resolved l)
              (err/raise :cli/no-store
                         (str "link " (pr-str link-name) " in " (descriptor-path reg)
                              " points to " (:resolved l) ", which is not a kimun store")
                         {:path (:resolved l) :name link-name :registry reg}))))
        (registry-stores env cwd)))

(defn resolve-store
  "Store directory for `opts` (parsed options), `env` (environment map) and
   `cwd`: `--store` > `KIMUN_STORE` > `(discover cwd)`. Nil when none.
   A relative `--store` value is read against `cwd`, never against the
   process directory. A `--store` value that is not an existing path (neither itself nor
   holding a `.kimun`) and looks like a link name is looked up as a link
   registered in the current store or an ancestor store (nearest wins); an
   existing path always wins over a link of the same name. A bare name that
   is neither raises `:cli/no-store` (exit 5) saying it is not a registered
   link either; any other missing path is returned as before (`open!`
   raises)."
  [opts env cwd]
  (cond (:store opts)
        (let [v (str (:store opts))
              at (str (if (fs/absolute? v) v (fs/path (or cwd ".") v)))]
          (if (or (fs/exists? at) (not (re-matches link-name-re v)))
            (normalize-store-path at)
            (or (link-target v env cwd)
                (let [p (normalize-store-path at)]
                  (err/raise :cli/no-store
                             (str "no kimun store at " p "; " (pr-str v) " is not a registered link either (current or ancestor stores)")
                             {:path p :name v})))))
        (seq (get env "KIMUN_STORE")) (normalize-store-path (get env "KIMUN_STORE"))
        :else (discover cwd)))

(def ^:dynamic *wait*
  "True while a command runs with `--wait` (bound by `kimun.cli.main/run`): the
   backends `backend` makes then wait for another process's write lock instead
   of failing with :store/busy."
  false)

(defn backend
  "The files backend of store directory `dir` (waiting for the write lock when
   `*wait*` is true)."
  [dir] (fs-store/backend (str dir) {:wait? *wait*}))

(defn- validate-links!
  "Raises `:store/invalid-links` unless every link of `descriptor` is
   `{:name valid :path relative}` with unique names (docs/v2/06 §4: link
   paths are relative to the store, never absolute)."
  [descriptor dir]
  (let [links (or (:links descriptor) [])
        bad (fn [msg l] (err/raise :store/invalid-links
                                   (str msg " in " (descriptor-path dir) ": " (pr-str l))
                                   {:path (str dir) :link l}))]
    (when-not (sequential? links)
      (bad ":links must be a vector" links))
    (doseq [l links]
      (cond (not (and (map? l) (string? (:name l)) (string? (:path l)))) (bad "link must be {:name :path} strings" l)
            (not (re-matches link-name-re (:name l))) (bad "invalid link name" l)
            (or (str/blank? (:path l)) (fs/absolute? (:path l))) (bad "link path must be non-empty and relative to the store" l)))
    (when-let [dup (->> links (map :name) frequencies (some (fn [[n c]] (when (> c 1) n))))]
      (bad (str "duplicate link name " dup) {:name dup}))))

(defn- project-file-reader
  "Reads a document path relative to the project root of store `dir` (the
   parent of the `.kimun` directory): `(fn [relative-path] text-or-nil)`, the
   reader the typed-document surface uses for by-reference content (docs/v2/14
   §10). Never raises: a missing or unreadable file reads as nil."
  [dir]
  (let [root (str (fs/parent (fs/absolutize dir)))]
    (fn [rel]
      (when rel
        (let [f (fs/path root rel)]
          (when (fs/regular-file? f) (slurp (str f))))))))

(defn open!
  "Opens the store at `dir`: `{:backend b :descriptor d :store s :path dir}`,
   with `s` opened by `kimun.kernel.store/open` and carrying `:read-file`
   (`project-file-reader`). Raises `:cli/no-store`
   (message names the path) when `dir` is nil or holds no `store.edn`, and
   `:store/invalid-links` when the descriptor's `:links` are malformed or
   absolute."
  [dir]
  (when (or (nil? dir) (not (store? dir)))
    (err/raise :cli/no-store
               (if dir
                 (str "no kimun store at " dir)
                 "no kimun store found (no .kimun/ here or in a parent directory, no --store, no $KIMUN_STORE): run `kimun stores init` or pass --store PATH")
               (if dir {:path (str dir)} {})))
  (let [b (backend dir)
        descriptor (store/read-descriptor b)]
    (validate-links! descriptor dir)
    {:backend b
     :descriptor descriptor
     :store (assoc (store/open b host/host)
                   :read-file (project-file-reader dir)
                   :store-dir (str dir)
                   :content (or (:content descriptor) :by-reference))
     :path (str dir)}))

(defn default-name
  "Default store name: the project directory's name when `dir` is a
   `.kimun` directory, else the directory's own name."
  [dir]
  (let [p (fs/absolutize dir)]
    (str (fs/file-name (if (= dirname (str (fs/file-name p))) (fs/parent p) p)))))

(defn init!
  "Creates a store at `dir` for `actor`: `kimun.kernel.store/init!` with
   capabilities `{actor :all \"kimun/derive\" :all \"kimun/materialize\" :all}`,
   then the descriptor gains `:name` (default `default-name`), `:links []` and,
   when `:content` is `:inline`, `:content :inline` (docs/v2/14 §10: a store
   whose documents carry their own text).
   Creates `dir` when missing. Raises `:cli/store-exists` when `store.edn` is
   already there. Returns `{:path :name :actor :capabilities :descriptor}`."
  [dir {:keys [name actor content]}]
  (let [dir (str (fs/absolutize dir))]
    (when (store? dir)
      (err/raise :cli/store-exists (str "a kimun store already exists at " dir) {:path dir}))
    (fs/create-dirs dir)
    (let [b (backend dir)
          capabilities {actor :all "kimun/derive" :all "kimun/materialize" :all}
          _ (store/init! b host/host capabilities)
          nm (or name (default-name dir))
          descriptor (cond-> (assoc (store/read-descriptor b) :name nm :links [])
                       (= :inline content) (assoc :content :inline))]
      (store/write-descriptor! b descriptor)
      {:path dir :name nm :actor actor :capabilities capabilities :descriptor descriptor})))

;; ---------------------------------------------------------------- federation

(defn- abs-path
  "Absolute, normalized path of `p` (relative to `base` when not absolute)."
  [base p]
  (fs/normalize (fs/absolutize (if (fs/absolute? p) p (fs/path base p)))))

(defn- canonical
  "Identity of directory `p` for comparisons: its real path when it exists."
  [p]
  (str (if (fs/exists? p) (fs/real-path p) p)))

(defn- read-links [dir]
  (vec (or (:links (store/read-descriptor (backend dir))) [])))

(defn- write-links! [dir links]
  (let [b (backend dir)]
    (store/write-descriptor! b (assoc (store/read-descriptor b) :links (vec links)))))

(defn link!
  "Links the store at `dir` to the store at `target` under `link-name`.
   `target` is a `.kimun` directory or a project directory containing one,
   absolute or relative to `cwd`; what is persisted in `store.edn` is
   `{:name link-name :path REL}`, the target `.kimun` relative to `dir`
   (never absolute). Raises `:store/invalid-link-name`, `:store/not-a-store`
   (target holds no `store.edn`), `:store/self-link`, `:store/link-exists`
   (name already linked). Returns the stored link."
  [dir link-name target cwd]
  (when-not (and (string? link-name) (re-matches link-name-re link-name))
    (err/raise :store/invalid-link-name
               (str "invalid link name " (pr-str link-name) ": use letters, digits, . _ - (starting with a letter or digit)")
               {:name link-name}))
  (let [dir (abs-path cwd dir)
        target-dir (abs-path cwd (normalize-store-path (abs-path cwd target)))]
    (when-not (store? target-dir)
      (err/raise :store/not-a-store (str "no kimun store at " target-dir) {:path (str target-dir)}))
    (when (= (canonical dir) (canonical target-dir))
      (err/raise :store/self-link (str "a store cannot link to itself: " target-dir) {:path (str target-dir)}))
    (let [links (read-links dir)]
      (when (some #(= link-name (:name %)) links)
        (err/raise :store/link-exists (str "a link named " link-name " already exists") {:name link-name}))
      (let [l {:name link-name :path (str (fs/relativize dir target-dir))}]
        (write-links! dir (conj links l))
        l))))

(defn unlink!
  "Removes the link `link-name` from the store at `dir`; returns the removed
   link. Raises `:store/missing` (exit 1) when the name is not linked."
  [dir link-name]
  (let [links (read-links dir)
        l (first (filter #(= link-name (:name %)) links))]
    (when-not l
      (err/raise :store/missing (str "no link named " (pr-str link-name))
                 {:name link-name :known (mapv :name links)}))
    (write-links! dir (remove #(= link-name (:name %)) links))
    l))

(defn links
  "The links of the store at `dir`, each `{:name :path :resolved :status}`:
   `:path` as stored (relative), `:resolved` the absolute `.kimun` path and
   `:status` one of `:ok`, `:missing` (target directory absent) or
   `:not-a-store` (directory without `store.edn`). A broken link is
   reported, never raised."
  [dir]
  (let [dir (abs-path "." dir)]
    (mapv (fn [{:keys [path] :as l}]
            (let [resolved (abs-path dir path)]
              (assoc l :resolved (str resolved)
                     :status (cond (not (fs/exists? resolved)) :missing
                                   (store? resolved) :ok
                                   :else :not-a-store))))
          (read-links dir))))

(defn linked-stores
  "Opens (through `open!`) every reachable linked store of `dir`, returning
   `[{:name :link-path :open}]` where `:open` is the `open!` result. Links
   whose target is missing or not a store are skipped (see `links`).
   Not transitive by default: only the direct links of `dir`. With
   `{:transitive? true}` links of links are followed too, named by the path
   of link names (\"b/c\"); a visited set (by real path, seeded with `dir`
   itself) breaks cycles (A links B, B links A) and diamonds."
  ([dir] (linked-stores dir {}))
  ([dir {:keys [transitive?]}]
   (let [root (abs-path "." dir)]
     (loop [queue (mapv #(assoc % :prefix nil) (links root))
            seen #{(canonical root)}
            out []]
       (if-let [{:keys [name resolved status prefix]} (first queue)]
         (let [full (if prefix (str prefix "/" name) name)
               id (canonical resolved)]
           (if (or (not= :ok status) (contains? seen id))
             (recur (subvec queue 1) seen out)
             (let [o (open! resolved)
                   more (if transitive? (mapv #(assoc % :prefix full) (links resolved)) [])]
               (recur (into (subvec queue 1) more) (conj seen id)
                      (conj out {:name full :link-path resolved :open o})))))
         out)))))
