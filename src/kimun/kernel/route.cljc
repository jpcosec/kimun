(ns kimun.kernel.route
  "Route entries over the tracked documents of a store (K2a, docs/v2/14 §20).

   A route entry names the tracked projection of one relative path with the IDs
   the store already has: `:path`, `:fingerprint`, `:class`,
   `:projection-hash`, `:content-hash` and, as a locator, `:tree-id`. The Merkle
   tree of the entries by path is `kimun.kernel.route-tree`.

   `:path` is a locator, not an identity. Duplicate or unsafe paths are rejected
   instead of being resolved by hash or by last writer. This namespace is pure:
   no store, no I/O, no new identity."
  (:require [clojure.string :as str]
            [kimun.kernel.canon :as canon]
            [kimun.kernel.err :as err]))

(def required
  "Keys every route entry must carry (all strings). `:tree-id` is optional."
  [:path :fingerprint :class :projection-hash :content-hash])

(defn- invalid [why data]
  (err/raise :route/invalid (str "route: " why) data))

(defn normalize-path
  "`p` with `\\` as `/` and leading `./` segments removed; nil when `p` is not
   a string. The result is checked by `valid-path?`."
  [p]
  (when (string? p)
    (-> p
        (str/replace "\\" "/")
        (str/replace #"^(\./)+" ""))))

(defn valid-path?
  "True for a relative, non-empty path without `.`/`..`/empty segments, NUL,
   an absolute leading slash or a Windows drive prefix (`C:/…`, `C:…`). The
   path is a locator; this check does not make it an identity."
  [p]
  (and (string? p)
       (not (str/blank? p))
       (not (str/starts-with? p "/"))
       (not (str/includes? p "\u0000"))
       (not (re-matches #"[A-Za-z]:.*" p))
       (every? (complement #{"" "." ".."}) (str/split p #"/" -1))))

(defn- nfc-path
  "The normalized path in NFC; nil when `p` is not a string."
  [host p]
  (some->> (normalize-path p) (canon/normalize host)))

(defn path-key
  "The NFC-normalized path key of `p`, or nil when it is unsafe/not a string."
  [host p]
  (let [p (nfc-path host p)]
    (when (valid-path? p) p)))

(defn entry
  "A validated route entry: the required string fields, `:path` normalized and
   NFC (through `host`), and `:tree-id` when supplied. Extra keys are ignored.
   Raises `:route/invalid` for a missing field, a non-string value or an unsafe
   path."
  [host {:keys [path tree-id] :as m}]
  (let [p (nfc-path host path)]
    (when-not (valid-path? p)
      (invalid (str "unsafe path " (pr-str path)) {:path path}))
    (doseq [k required]
      (when-not (string? (get m k))
        (invalid (str "missing or non-string " k) {:path p :key k})))
    (when (and (some? tree-id) (not (string? tree-id)))
      (invalid "tree-id must be a string when present" {:path p :tree-id tree-id}))
    (cond-> (select-keys (assoc m :path p) (conj required :tree-id))
      (nil? tree-id) (dissoc :tree-id))))

(defn canonical-entries
  "Validated entries sorted by their NFC path. Raises `:route/ambiguous` when
   two entries address the same NFC path; the conflict is visible, never
   resolved by hash."
  [host entries]
  (let [es (->> entries (map #(entry host %)) (sort-by :path) vec)]
    (doseq [[a b] (partition 2 1 es)
            :when (= (:path a) (:path b))]
      (err/raise :route/ambiguous (str "route: duplicate path " (:path a)) {:path (:path a)}))
    es))
