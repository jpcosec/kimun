(ns kimun.surface.index.cache
  "The derived index on disk: `<store-dir>/index/<head>.edn` (docs/v2/06 §B,
   §6). The file is a cache of `kimun.surface.index.build/build` and never has
   authority: it is used only when it parses, has the expected shape and
   version and names the head the opened store replayed to; on any doubt the
   index is rebuilt in memory.

   Reading never writes (v1 rewrote its indexes on every read; that defect is
   not copied): `load!`, `lookup!` and `federated!` only read files, and a
   missing, stale, truncated or unreadable cache never makes them fail.
   `rebuild!` — the `rebuild-indexes` command — is the only writer, and what
   it writes lives under `<store-dir>/index/` alone."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [kimun.cli.store :as cli-store]
            [kimun.surface.index.build :as build]))

(def dirname "Name of the cache directory inside the store directory." "index")

(defn cache-path
  "Path of the cache file of revision `head` in store directory `dir`, or nil
   when `dir` or `head` is nil (a store without revisions has no cache)."
  [dir head]
  (when (and dir head) (str (fs/path dir dirname (str head ".edn")))))

(defn- kernel-store
  "The kernel store of `x`: an `open!` result (`{:store :path …}`) or the
   kernel store itself."
  [x]
  (if (and (map? x) (contains? x :store) (contains? x :path)) (:store x) x))

(defn- store-dir [x] (when (and (map? x) (contains? x :store)) (:path x)))

(defn- valid?
  "Whether `idx` (read from a cache file) is an index of `head`."
  [idx head]
  (and (map? idx)
       (= build/version (:version idx))
       (= head (:head idx))
       (every? #(contains? idx %) build/contract-keys)
       (every? #(map? (get idx %)) [:models :docs :by-model :by-name :tags :tag-dag :equivalences :sections :links :anchors])
       (= (set (keys (:docs idx))) (set (vals (:by-name idx))))))

(defn- read-cache
  "`{:index idx}` when the file at `path` holds a valid index of `head`, else
   `{:reason kw}`: `:no-cache`, `:corrupt` (unreadable, not EDN) or `:stale`
   (another head, version or shape). Never raises."
  [path head]
  (cond (nil? path) {:reason :no-head}
        (not (fs/regular-file? path)) {:reason :no-cache}
        :else (try (let [idx (edn/read-string (slurp path))]
                     (if (valid? idx head) {:index idx} {:reason :stale}))
                   (catch #?(:clj Throwable :cljs :default) _ {:reason :corrupt}))))

(defn lookup!
  "The index of `opened` and where it came from: `{:index idx :source :cache}`
   or `{:index idx :source :built :reason kw}` with `:reason` one of
   `:no-cache`, `:stale`, `:corrupt`, `:no-head` (store without revisions) or
   `:no-dir` (a bare kernel store was given). `opened` is the result of
   `kimun.cli.store/open!` or a kernel store. Reads at most one file, writes
   nothing, never raises because of the cache."
  [opened]
  (let [store (kernel-store opened)
        dir (store-dir opened)
        r (if dir (read-cache (cache-path dir (:head store)) (:head store)) {:reason :no-dir})]
    (if (:index r)
      {:index (:index r) :source :cache}
      {:index (build/build store) :source :built :reason (:reason r)})))

(defn load!
  "The index (`kimun.surface.index.build/build` shape) of `opened` — the
   result of `kimun.cli.store/open!`, or a kernel store (then always built).
   Served from `<store-dir>/index/<head>.edn` when that file is a valid index
   of the store's head, otherwise built in memory. Equal to `(build/build
   (:store opened))` either way. Writes nothing; never raises because of the
   cache."
  [opened]
  (:index (lookup! opened)))

(defn federated!
  "The federated view of `opened` (an `open!` result): `[{:store name :index
   idx} …]`, the store itself first (under its descriptor `:name`), then its
   reachable linked stores in link order under their link names (see
   `kimun.cli.store/linked-stores`; `opts` is passed to it, e.g.
   `{:transitive? true}`). Each index comes from `load!`. Writes nothing."
  ([opened] (federated! opened {}))
  ([opened opts]
   (into [{:store (get-in opened [:descriptor :name]) :index (load! opened)}]
         (map (fn [l] {:store (:name l) :index (load! (:open l))}))
         (cli-store/linked-stores (:path opened) opts))))

(defn- write-atomically! [path text]
  (let [tmp (str path ".tmp")]
    (spit tmp text)
    (fs/move tmp path {:replace-existing true :atomic-move true})))

(defn rebuild!
  "Rebuilds the index of `opened` (an `open!` result) from the store, ignoring
   any cache, writes it to `<store-dir>/index/<head>.edn` (atomically; with an
   `index/.gitignore` so the directory stays out of version control) and
   deletes the cache files of other revisions. Nothing outside
   `<store-dir>/index/` is touched. A store without revisions writes no cache
   file. Returns the report
   `{:index idx :head h :cache \"index/<head>.edn\"|nil :bytes n :documents n
   :models n :tags n :skipped [doc-name …] :complete bool :pruned n}` —
   `:skipped` names the documents indexed without payload (`:error`),
   `:complete` is true when there is none, `:cache` is relative to the store
   directory."
  [opened]
  (let [store (kernel-store opened)
        dir (:path opened)
        idx (build/build store)
        path (cache-path dir (:head store))
        text (pr-str idx)
        skipped (build/skipped idx)
        index-dir (fs/path dir dirname)]
    (when path
      (fs/create-dirs index-dir)
      (let [ignore (str (fs/path index-dir ".gitignore"))]
        (when-not (fs/exists? ignore) (spit ignore "*\n")))
      (write-atomically! path text))
    (let [stale (when (fs/directory? index-dir)
                  (remove #(= (str %) (str path)) (fs/glob index-dir "*.edn")))]
      (doseq [f stale] (fs/delete-if-exists f))
      {:index idx :head (:head store)
       :cache (when path (str (fs/path dirname (str (:head store) ".edn"))))
       :bytes (if path (count (.getBytes ^String text "UTF-8")) 0)
       :documents (count (:docs idx)) :models (count (:models idx)) :tags (count (get-in idx [:tag-dag :nodes]))
       :skipped skipped :complete (empty? skipped) :pruned (count stale)})))
