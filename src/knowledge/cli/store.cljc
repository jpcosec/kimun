(ns knowledge.cli.store
  "Locating, opening and creating the `.knowledge/` store of the CLI (docs/v2/06
   §B): discovery walks up from the working directory, `--store` and
   `KNOWLEDGE_STORE` override it, and every open goes through
   `sldb.kernel.store/open` (replay from the log) with the standard host."
  (:require [babashka.fs :as fs]
            [sldb.host.default :as host]
            [sldb.host.fs-store :as fs-store]
            [sldb.kernel.err :as err]
            [sldb.kernel.store :as store]))

(def dirname
  "Name of the store directory inside a project."
  ".knowledge")

(defn descriptor-path
  "Path of `store.edn` inside store directory `dir`."
  [dir] (str (fs/path dir "store.edn")))

(defn store?
  "Whether `dir` holds a `store.edn`."
  [dir] (fs/exists? (descriptor-path dir)))

(defn discover
  "Walks up from `start-dir` to the filesystem root looking for
   `<dir>/.knowledge/store.edn`; returns the `.knowledge` path string or nil."
  [start-dir]
  (loop [dir (fs/absolutize start-dir)]
    (when dir
      (let [candidate (fs/path dir dirname)]
        (if (store? candidate)
          (str candidate)
          (recur (fs/parent dir)))))))

(defn normalize-store-path
  "A user-supplied store path: the `.knowledge` directory itself, or a project
   directory that contains one. Returns the `.knowledge` path string."
  [p]
  (let [p (fs/absolutize p)
        nested (fs/path p dirname)]
    (cond (= dirname (str (fs/file-name p))) (str p)
          (store? nested) (str nested)
          :else (str p))))

(defn resolve-store
  "Store directory for `opts` (parsed options), `env` (environment map) and
   `cwd`: `--store` > `KNOWLEDGE_STORE` > `(discover cwd)`. Nil when none."
  [opts env cwd]
  (cond (:store opts) (normalize-store-path (:store opts))
        (seq (get env "KNOWLEDGE_STORE")) (normalize-store-path (get env "KNOWLEDGE_STORE"))
        :else (discover cwd)))

(defn backend
  "The files backend of store directory `dir`."
  [dir] (fs-store/backend (str dir)))

(defn open!
  "Opens the store at `dir`: `{:backend b :descriptor d :store s :path dir}`,
   with `s` replayed by `sldb.kernel.store/open`. Raises `:cli/no-store`
   (message names the path) when `dir` is nil or holds no `store.edn`."
  [dir]
  (when (or (nil? dir) (not (store? dir)))
    (err/raise :cli/no-store
               (str "no knowledge store at " (or dir "(none found: run `knowledge stores init` or pass --store)"))
               {:path (some-> dir str)}))
  (let [b (backend dir)]
    {:backend b
     :descriptor (store/read-descriptor b)
     :store (store/open b host/host)
     :path (str dir)}))

(defn default-name
  "Default store name: the project directory's name when `dir` is a
   `.knowledge` directory, else the directory's own name."
  [dir]
  (let [p (fs/absolutize dir)]
    (str (fs/file-name (if (= dirname (str (fs/file-name p))) (fs/parent p) p)))))

(defn init!
  "Creates a store at `dir` for `actor`: `sldb.kernel.store/init!` with
   capabilities `{actor :all \"knowledge/derive\" :all \"knowledge/materialize\" :all}`,
   then the descriptor gains `:name` (default `default-name`) and `:links []`.
   Creates `dir` when missing. Raises `:cli/store-exists` when `store.edn` is
   already there. Returns `{:path :name :actor :capabilities :descriptor}`."
  [dir {:keys [name actor]}]
  (let [dir (str (fs/absolutize dir))]
    (when (store? dir)
      (err/raise :cli/store-exists (str "a knowledge store already exists at " dir) {:path dir}))
    (fs/create-dirs dir)
    (let [b (backend dir)
          capabilities {actor :all "knowledge/derive" :all "knowledge/materialize" :all}
          _ (store/init! b host/host capabilities)
          nm (or name (default-name dir))
          descriptor (assoc (store/read-descriptor b) :name nm :links [])]
      (store/write-descriptor! b descriptor)
      {:path dir :name nm :actor actor :capabilities capabilities :descriptor descriptor})))
