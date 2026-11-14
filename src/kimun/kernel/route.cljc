(ns kimun.kernel.route
  "Route Merkle over the tracked documents of a store (K2a, docs/v2/14 §20).

   A route entry names the tracked projection of one relative path with the IDs
   the store already has: `:path`, `:fingerprint`, `:class`,
   `:projection-hash`, `:content-hash` and, as a locator, `:tree-id`. The route
   root hashes only the first five, sorted by path: the nominal tree id, the
   revision and the timestamp do not enter it. Rebuilding the same files under a
   new ULID therefore gives the same root.

   `:path` is a locator, not an identity. Duplicate or unsafe paths are rejected
   instead of being resolved by hash or by last writer. This namespace is pure:
   no store, no I/O, no new identity."
  (:require [clojure.string :as str]
            [kimun.kernel.canon :as canon]
            [kimun.kernel.err :as err]))

(def schema
  "Schema marker of the hashed route value."
  "kimun.route/1")

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

(defn- hashed-entry
  "The value of an entry that enters the route root: the five stable fields, in
   order. `:tree-id` is deliberately absent."
  [e]
  [(str (:path e)) (:fingerprint e) (:class e) (:projection-hash e) (:content-hash e)])

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

(defn root
  "Root hash of the route tree of `entries` under `host`: the canonical digest
   of `{:route/version \"kimun.route/1\" :entries [hashed-entry …]}`, entries
   sorted by path. `:tree-id`, revisions and time do not enter the root."
  [host entries]
  (canon/digest host {:route/version schema
                      :entries (mapv hashed-entry (canonical-entries host entries))}))

(defn lookup
  "The entry at `path` in `entries`, normalized to NFC, or nil."
  [host entries path]
  (let [p (nfc-path host path)]
    (some #(when (= p (:path %)) %) (canonical-entries host entries))))

(defn diff
  "Path diff between two entry collections:
   `{:added [entry …] :removed [entry …] :changed [{:path p :before e :after e} …]}`.
   An entry whose `:tree-id` changed but whose hashed fields did not is not a
   change; a changed fingerprint, class, projection or content hash is."
  [host before after]
  (let [b (into {} (map (juxt :path identity)) (canonical-entries host before))
        a (into {} (map (juxt :path identity)) (canonical-entries host after))
        paths (sort (distinct (concat (keys b) (keys a))))]
    {:added (vec (keep #(when-not (contains? b %) (get a %)) paths))
     :removed (vec (keep #(when-not (contains? a %) (get b %)) paths))
     :changed (vec (keep (fn [p]
                           (let [x (get b p) y (get a p)]
                             (when (and x y (not= (hashed-entry x) (hashed-entry y)))
                               {:path p :before x :after y})))
                         paths))}))
