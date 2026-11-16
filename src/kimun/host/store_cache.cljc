(ns kimun.host.store-cache
  "Cache by hash of a store (docs/v2/14 §18.6, §19): the derived objects of a
   document with an external anchor — its tree objects and its block/text/
   opaque nodes — keyed by their content id, plus one entry per document keyed
   by `(fingerprint, clase, versión del parser)`. It lives in `<store>/cache/`
   (gitignored, deletable) and is recomputable from the file + the class and
   verifiable against the registered root hash. Reading is ring 1: the kernel
   asks the backend for an object and the backend falls back here (see
   `kimun.host.fs-store`); the surface repopulates it through the resolver when
   it is absent (`kimun.surface.docs`).
   Pure I/O over a store directory; nothing here knows about nodes or trees."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]))

(def dirname
  "Name of the cache directory inside a store."
  "cache")

(def parser-version
  "Version of the parser/extractor whose output the cache holds. Bump it to
   invalidate every entry when the structure produced for the same file+class
   changes (docs/v2/14 §18.6: the cache key)."
  "1")

(defn cache-dir
  "Path of the cache directory of store `store-dir`."
  [store-dir] (str (fs/path store-dir dirname)))

(defn objects-dir
  "Path of the content-addressed object cache of `store-dir`."
  [store-dir] (str (fs/path (cache-dir store-dir) "objects")))

(defn docs-dir
  "Path of the per-document cache entries of `store-dir`."
  [store-dir] (str (fs/path (cache-dir store-dir) "docs")))

(defn plans-dir
  "Path of the resolved-plan cache of `store-dir` (K2a): the build plan is
   provenance/audit, not a CAS object."
  [store-dir] (str (fs/path (cache-dir store-dir) "plans")))

(defn plan-path
  "Path of the cached plan `tx-id` of `store-dir`."
  [store-dir tx-id] (str (fs/path (plans-dir store-dir) (str tx-id ".edn"))))

(defn read-plan
  "The resolved plan cached under `tx-id` of `store-dir`, or nil when absent or
   unreadable. The digest check belongs to the caller."
  [store-dir tx-id]
  (let [f (plan-path store-dir tx-id)]
    (when (fs/regular-file? f)
      (try (edn/read-string (slurp (str f)))
           (catch Exception _ nil)))))

(defn write-plan!
  "Writes the resolved plan once under `tx-id`; existing plans are not
   rewritten. Returns `tx-id`."
  [store-dir tx-id plan]
  (let [d (plans-dir store-dir)
        f (plan-path store-dir tx-id)]
    (when-not (fs/exists? f)
      (fs/create-dirs d)
      (spit (str f) (pr-str plan)))
    tx-id))

(defn write-object!
  "Writes cached object `id` once (existing objects are never rewritten; they
   are content-addressed); returns `id`."
  [store-dir id content]
  (let [d (objects-dir store-dir)
        f (fs/path d id)]
    (when-not (fs/exists? f)
      (fs/create-dirs d)
      (spit (str f) content))
    id))

(defn sha256-hex
  "Lower-case hex SHA-256 of the bytes `content`: the id a packed object has and
   the hash of a cache key (the store's hasher may differ but a cache key is an
   internal address, never a store id)."
  [^bytes content]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")]
    (apply str (map (fn [b] (format "%02x" (bit-and (int b) 0xff))) (.digest md content)))))

(defn cache-key
  "Cache key of a document: hex SHA-256 of `(fingerprint, class, parser)`
   (docs/v2/14 §18.6)."
  [fingerprint class parser]
  (sha256-hex (.getBytes (str fingerprint "\u0000" class "\u0000" parser) "UTF-8")))

(defn entry-path
  "Path of the document cache entry `key` of `store-dir`."
  [store-dir key] (str (fs/path (docs-dir store-dir) (str key ".edn"))))

(defn write-entry!
  "Writes the document cache entry `key` (index: fingerprint, class, parser,
   root hash, tree id); returns `key`."
  [store-dir key entry]
  (let [d (docs-dir store-dir)]
    (fs/create-dirs d)
    (spit (entry-path store-dir key) (pr-str entry))
    key))
