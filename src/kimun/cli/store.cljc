(ns kimun.cli.store
  "Locating, opening and creating the `.kimun/` store of the CLI (docs/v2/06
   §B): discovery walks up from the working directory, `--store` and
   `KIMUN_STORE` override it, and every open goes through
   `sldb.kernel.store/open` (replay from the log) with the standard host."
  (:require [babashka.fs :as fs]
            [sldb.host.default :as host]
            [sldb.host.fs-store :as fs-store]
            [sldb.kernel.err :as err]
            [clojure.string :as str]
            [sldb.kernel.store :as store]))

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

(defn resolve-store
  "Store directory for `opts` (parsed options), `env` (environment map) and
   `cwd`: `--store` > `KIMUN_STORE` > `(discover cwd)`. Nil when none."
  [opts env cwd]
  (cond (:store opts) (normalize-store-path (:store opts))
        (seq (get env "KIMUN_STORE")) (normalize-store-path (get env "KIMUN_STORE"))
        :else (discover cwd)))

(defn backend
  "The files backend of store directory `dir`."
  [dir] (fs-store/backend (str dir)))

(def ^:private link-name-re #"[A-Za-z0-9][A-Za-z0-9._-]*")

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

(defn open!
  "Opens the store at `dir`: `{:backend b :descriptor d :store s :path dir}`,
   with `s` replayed by `sldb.kernel.store/open`. Raises `:cli/no-store`
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
     :store (store/open b host/host)
     :path (str dir)}))

(defn default-name
  "Default store name: the project directory's name when `dir` is a
   `.kimun` directory, else the directory's own name."
  [dir]
  (let [p (fs/absolutize dir)]
    (str (fs/file-name (if (= dirname (str (fs/file-name p))) (fs/parent p) p)))))

(defn init!
  "Creates a store at `dir` for `actor`: `sldb.kernel.store/init!` with
   capabilities `{actor :all \"kimun/derive\" :all \"kimun/materialize\" :all}`,
   then the descriptor gains `:name` (default `default-name`) and `:links []`.
   Creates `dir` when missing. Raises `:cli/store-exists` when `store.edn` is
   already there. Returns `{:path :name :actor :capabilities :descriptor}`."
  [dir {:keys [name actor]}]
  (let [dir (str (fs/absolutize dir))]
    (when (store? dir)
      (err/raise :cli/store-exists (str "a kimun store already exists at " dir) {:path dir}))
    (fs/create-dirs dir)
    (let [b (backend dir)
          capabilities {actor :all "kimun/derive" :all "kimun/materialize" :all}
          _ (store/init! b host/host capabilities)
          nm (or name (default-name dir))
          descriptor (assoc (store/read-descriptor b) :name nm :links [])]
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
