(ns kimun.kernel.store
  "Persistence port and the host-independent parts of open/replay/verify
   (docs/v2/02 §8.1, milestone 3).

   Layout (files-only backend, see kimun.host.fs-store; docs/v2/10 §11, 14 §10):
     <dir>/store.edn      {:format-version 4 :hash-alg :sha-256 :capabilities {...} :route-schema 2}
                          (:route-schema absent = route schema 1, docs/v2/10 §11.2)
     <dir>/objects/<id>   canonical EDN text of the hashed form of the object; H(file) == id
     <dir>/packs/<n>.pack K4: small loose objects moved explicitly by `stores pack`
                          (records + a footer naming the index digest; the pack is
                          store, its .idx is derived, docs/v2/10 §11.4)
     <dir>/cache/packs/   K4b: the same pack format for the DERIVED cache; not the
                          store (verify, object-ids and unreachable never see it)
     <dir>/cache/         format 4: derived cache of a document with an external anchor —
                          cache/routes/<root>.<head>.edn mirrors the durable CAS
                          route-map object of route schema 1 (schema 2 keeps the map
                          as tree nodes in objects/ and writes no mirror); open validates the CAS object first,
                          so this is a contract mirror, not a measured accelerator —
                          one entry per document keyed by (fingerprint, class, parser) plus
                          its tree/block/field objects by hash; gitignored, deletable,
                          recomputable from the file and verifiable against the root hash;
                          cache/plans/<tx-id>.edn holds the resolved build plan (K2a),
                          read by replay and checked by verify-full
     <dir>/log.edn        one Transaction map per line, append-only: {:id :plan :revision} in
                          formats 1–2, {:id :revision} in formats 3–4
     <dir>/heads.edn      formats 2–4: {:head rev-id :heads-root mmap-root-id} (the per-tree
                          heads tree-id -> rev-id are an mmap in the CAS); format 1: {:head rev-id
                          :heads {tree-id rev-id}}; written atomically (`heads-record`)
     <dir>/lock           empty; the exclusive lock of `commit-tx!` is taken on it

   heads.edn is the commit point. A Transaction is committed when heads.edn
   records its revision; `commit-tx!` compares the heads, appends the log line
   and replaces the heads as ONE backend operation under an exclusive lock, so
   a writer that lost the race writes nothing but write-once objects nobody
   references. The only state a dead writer can leave behind is one trailing
   log line (whole or torn) that the heads do not record: `log-tail` names it,
   `replay-open` ignores it, `verify` reports it and the next `commit-tx!`
   discards it before appending (never appends after it).

   :format-version 4 (new stores) is the revision format of kimun.kernel.revision;
   formats 2 and 3 share that engine; :format-version 1 is served by
   kimun.kernel.legacy.revision. The store value carries :format-version and
   every function here dispatches on it.

   `open` (formats 2–4, host with the LazyMaps port) reads store.edn and
   heads.edn, checks the head Revision objects and replays NOTHING: see its
   docstring for what it checks and what it trusts. `replay-open` is the full
   verification by replay (log.edn through apply-plan, every transaction and
   revision id, final heads against heads.edn); `verify` recomputes the hash
   of every object reachable from the heads; `verify-full` is both. Format 1
   (and hosts without lazy maps) open through `replay-open`. `commit!` writes
   exactly the objects the plan created, then calls `commit-tx!`."
  (:require [clojure.edn :as edn]
            [kimun.kernel.canon :as canon]
            [kimun.kernel.node :as node]
            [kimun.kernel.edge :as edge]
            [kimun.kernel.ports :as ports]
            [kimun.kernel.err :as err]
            [kimun.kernel.mmap :as mmap]
            [kimun.kernel.legacy.revision :as legacy]
            [kimun.kernel.revision :as revision]
            [kimun.kernel.route :as route]
            [kimun.kernel.route-tree :as route-tree]))

(defprotocol Backend
  "Port implemented by a persistence adapter (ring 1)."
  (read-descriptor [b] "The store.edn map, or nil when the directory is not a store.")
  (write-descriptor! [b descriptor] "Creates the store layout and writes store.edn.")
  (read-object [b id] "String content of objects/<id>, or nil.")
  (write-object! [b id content] "Writes objects/<id> once; existing objects are never rewritten.")
  (object-ids [b] "Ids present under objects/.")
  (read-log [b] "Vector of Transaction maps in log order; a final line cut short (unterminated and unreadable) is the entry {:torn-line n}.")
  (read-heads [b] "The heads.edn map (`heads-record`), or nil when absent; raises :store/corrupt-heads when the file cannot be read as EDN.")
  (commit-tx! [b expected tx new]
    "The single mutation of log and heads, atomic against other writers (one
     exclusive lock around all of it). When the current heads (absent =
     {:head nil :heads {}}) differ from `expected`: writes nothing, returns
     false. Otherwise: discards an :uncommitted log tail (`log-tail`; raises
     :store/error on an :inconsistent one, writing nothing), appends `tx` as a
     log line, then replaces the heads with `new`; returns true. An adapter
     whose lock is held by another process may raise :store/busy instead of
     waiting, having written nothing."))

;; ---------------------------------------------------------------- heads swap (optional port)

(defprotocol HeadsSwap
  "Optional backend capability: replace the heads without a transaction. Only
   for a change that names the same head revision and the same per-tree heads
   under another route root (`upgrade-routes!`): the log does not move."
  (swap-heads! [b expected new]
    "Under the same exclusive lock as `commit-tx!`: when the current heads
     differ from `expected`, writes nothing and returns false; otherwise
     replaces them with `new` and returns true. Appends nothing to the log."))

;; ---------------------------------------------------------------- plan cache (optional port)

(defprotocol PlanCache
  "Optional backend capability: the resolved plan of a transaction lives in the
   store's derived cache, not in the CAS. A backend without it keeps the plan in
   `objects/` (formats 1–3 and in-memory test backends)."
  (read-plan [b tx-id] "The resolved plan cached under `tx-id`, or nil.")
  (write-plan! [b tx-id plan] "Writes the resolved plan once; returns its id."))

(defprotocol RouteCache
  "Optional backend capability: the route entries behind `:routes-root` are
   derived cache. The root is durable in heads.edn; the entries may be rebuilt
   by replay from the `:route` values in the log, never invented by `open`."
  (read-routes [b root head] "The route entry map cached under `root` at `head`, or nil.")
  (write-routes! [b root head routes] "Writes route entries once; returns `root`."))

;; ---------------------------------------------------------------- object cache (optional port)

(defprotocol StoredObjects
  "Optional backend capability: read an object from the store itself, never from
   a derived cache. A backend that serves some objects from a recomputable cache
   (the files backend, format 4: docs/v2/10 §11) implements it so that `verify`
   cannot mistake a cached copy for the stored object it must hold."
  (read-stored-object [b id] "String content of the stored object `id`, or nil."))

(defn- stored-content
  "Content of object `id` as the store holds it: `read-stored-object` when the
   backend separates its cache, else `read-object`."
  [b id]
  (if (satisfies? StoredObjects b) (read-stored-object b id) (read-object b id)))

(defprotocol ObjectCache
  "Optional port of a Backend: a per-backend-instance cache id -> PARSED object
   (the value `deserialize` returns). CAS objects are immutable, so an entry is
   never invalidated. The state lives in the host adapter; the kernel only asks.
   The cache never answers a hash check: `verify` and the head check of `open`
   hash the text `read-object` returns, which an adapter must always read from
   its medium."
  (cached-object [b id] "The parsed object cached under `id`, or nil.")
  (cache-object! [b id obj] "Remembers `obj` as the parsed object of `id`; returns `obj`."))

(defn- caching? [b] (satisfies? ObjectCache b))

;; ---------------------------------------------------------------- hashed forms

(defn hashed-form
  "The value whose canonical bytes hash to the object's id (nodes and edges
   are stored without their derived :id/:address)."
  [obj]
  (cond
    (and (map? obj) (:class obj) (:kind obj) (contains? obj :content)) (node/identity-form obj)
    (and (map? obj) (:type obj) (:from obj) (:to obj))                    (edge/identity-form obj)
    :else obj))

(defn serialize
  "Canonical text written to objects/<id>."
  [host obj] (canon/canon-str host (hashed-form obj)))

(defn deserialize
  "Inverse of `serialize`: the in-memory object of `id` from the text of
   objects/<id>. Nodes get back :id and :address and edges :id (the keys their
   hashed form leaves out). Recomputes no hash: the caller trusts the content
   or checks it (`open` for the head Revisions, `verify` for the rest)."
  [id content]
  (let [o (edn/read-string content)]
    (cond
      (and (map? o) (:class o) (:kind o) (contains? o :content)) (assoc o :id id :address (node/address o))
      (and (map? o) (:type o) (:from o) (:to o))                 (assoc o :id id)
      :else o)))

(defn- fail [why data] (err/raise (or (:type data) :store/error) (str "store: " why) data))

(defn load-object
  "The parsed object `id` of backend `b`, or nil when absent: from the
   backend's `ObjectCache` when it has one (each object is then read and parsed
   at most once per backend instance), else read and parsed on every call.
   Checks no hash (see `deserialize`)."
  [b id]
  (if (caching? b)
    (or (cached-object b id)
        (some->> (read-object b id) (deserialize id) (cache-object! b id)))
    (some->> (read-object b id) (deserialize id))))

(defn- plan-node-refs
  "Ids of the nodes a resolved plan names by id (`:id` with no `:node`): a plan
   written by this format does not embed node content (docs/v2/14 §10)."
  [plan]
  (into [] (keep (fn [op] (when (and (= :add-node (:op op)) (nil? (:node op))) (:id op)))) (:ops plan)))

(defn- load-plan-objects
  "`store` with the node objects `plan` names by id added to its :objects: they
   are in the CAS (`commit!` writes every new object before the log line), and a
   replay needs them to apply a node-free plan. Objects already in the store are
   left alone; an object that cannot be read is left out (the hash check reports
   it, the replay of the plan does not fail on it)."
  [b store plan]
  (reduce (fn [s id]
            (if (or (nil? id) (contains? (:objects s) id))
              s
              (if-some [o (err/rescue-any (fn [_] nil) (load-object b id))]
                (assoc-in s [:objects id] o)
                s)))
          store (plan-node-refs plan)))

;; ---------------------------------------------------------------- log tail

(def no-heads
  "The heads of a store without heads.edn (nothing committed), both formats."
  {:head nil :heads {}})

(defn heads-record
  "The heads.edn value of `store`, the commit point `commit-tx!` compares and
   replaces: `no-heads` before the first commit; format 2 {:head rev-id
   :heads-root id}, the root of the mmap tree-id -> rev-id in the CAS (its size
   does not grow with the number of trees); format 4 adds `:routes-root` when
   the store tracks a route tree; format 1 {:head rev-id :heads {tree-id rev-id}}."
  [store]
  (cond (nil? (:head store)) no-heads
        (revision/legacy? store) {:head (:head store) :heads (:heads store)}
        :else (cond-> {:head (:head store) :heads-root (:heads-root store)}
                (some? (:routes-root store)) (assoc :routes-root (:routes-root store))
                (some? (:routes-object store)) (assoc :routes-object (:routes-object store)))))

(defn transaction?
  "True for a well-formed log entry: `{:id :plan :revision}` in format 2,
   `{:id :revision}` in format 3 (the resolved plan lives in the CAS under
   `:id`, so it is not duplicated in the log)."
  [entry]
  (boolean (and (map? entry) (:id entry) (:revision entry))))

(defn log-tail
  "Pure rule relating the recorded `heads` ({:head :heads} or nil) to `tail`,
   the last two entries of the log (fewer when the log is shorter; an entry
   that is not a Transaction stands for a torn or unreadable line):
     :clean        the log ends at the recorded head (or both are empty);
     :uncommitted  the last line is not recorded by the heads but the line
                   before it is the recorded head (or it is the only line and
                   nothing is recorded): a writer died after appending and
                   before replacing the heads, or mid-append. The line never
                   was a commit; it is ignored by open and discarded by the
                   next commit;
     :inconsistent anything else: log and heads disagree beyond what a dead
                   writer can cause."
  [heads tail]
  (let [head (:head (or heads no-heads))
        tail (vec tail)
        n (count tail)
        last-entry (when (pos? n) (peek tail))
        prev (when (> n 1) (nth tail (- n 2)))]
    (cond
      (and (zero? n) (nil? head)) :clean
      (and (transaction? last-entry) (= head (:revision last-entry))) :clean
      (and (pos? n) (nil? prev) (nil? head)) :uncommitted
      (and (transaction? prev) (some? head) (= head (:revision prev))) :uncommitted
      :else :inconsistent)))

(defn- last-two [log] (subvec log (max 0 (- (count log) 2))))

(defn- split-log
  "[committed-entries uncommitted-entry-or-nil] of the log of `b` against its heads."
  [b]
  (let [log (vec (read-log b))]
    (if (= :uncommitted (log-tail (read-heads b) (last-two log)))
      [(pop log) (peek log)]
      [log nil])))

(defn committed-log
  "The log of `b` without an uncommitted tail (see `log-tail`): what readers
   of the history must use. Reads the whole log."
  [b] (first (split-log b)))

(defn uncommitted-tail
  "The trailing log entry the recorded heads do not reflect (see `log-tail`):
   a Transaction, or {:torn-line n}; nil when there is none. Reads the whole log."
  [b] (second (split-log b)))

;; ---------------------------------------------------------------- init / open

(def format-version
  "The format new stores are created with (docs/v2/14 §10, §18.6): format 4
   registers a document with a file as cache (descriptor + merkle root, no tree
   object and no block/text node in the CAS)."
  4)

(def route-schema
  "The route schema new stores are created with (docs/v2/10 §11.2): 2, the
   route map as a Merkle tree by path. store.edn records it as `:route-schema`;
   a store.edn without the key is a store of schema 1 (one flat map object),
   which keeps opening and writing as it did until `upgrade-routes!`."
  2)

(defn- descriptor-route-schema
  "The route schema a store.edn map declares: its `:route-schema`, else 1."
  [descriptor]
  (or (:route-schema descriptor) 1))

(defn- empty-store
  "The empty store value of format `fv` (1 = legacy, 2–4 = current) and route
   schema `rs`."
  [host capabilities fv rs]
  (case fv
    1 (assoc (legacy/empty-store host capabilities) :format-version 1)
    (2 3 4) (assoc (revision/empty-store host capabilities) :format-version fv :routes-schema rs)
    (fail "unsupported store format" {:format-version fv})))

(defn init!
  "Creates a new store in backend `b` with the given capabilities; returns the
   empty in-memory store. `opts` {:format-version 1} creates a legacy format-1
   store (tests and upgrade); the default is `format-version`. A format-4 store
   is created with route schema `route-schema`, recorded in store.edn;
   `opts` {:route-schema 1} creates one of schema 1 (tests and upgrade)."
  ([b host capabilities] (init! b host capabilities {}))
  ([b host capabilities opts]
   (when (read-descriptor b) (fail "store already initialised" {}))
   (let [fv (or (:format-version opts) format-version)
         rs (if (= 4 fv) (or (:route-schema opts) route-schema) 1)
         store (empty-store host capabilities fv rs)]
     (write-descriptor! b (cond-> {:format-version fv :hash-alg (ports/algorithm (ports/hasher host)) :capabilities capabilities}
                            (not= 1 rs) (assoc :route-schema rs)))
     store)))

(defn- plan-of
  "The resolved plan of a log entry of `b`: `:plan` when the line carries it
   (format 2), else the CAS object `:id` (legacy), else the derived plan cache
   (`PlanCache`). A plan is identified by the digest of its resolved form; its
   absence is reported by replay, never silently accepted."
  [b entry]
  (or (:plan entry)
      (some-> (read-object b (:id entry)) edn/read-string)
      (when (satisfies? PlanCache b) (read-plan b (:id entry)))))

(defn- replay-log
  "Replays the entries `log` over the empty store of backend `b`."
  [b host log]
  (let [{:keys [capabilities hash-alg] fv :format-version :as descriptor} (or (read-descriptor b) (fail "no store.edn" {}))
        alg (ports/algorithm (ports/hasher host))]
    (when (not= hash-alg alg) (fail "hash algorithm mismatch" {:store hash-alg :hasher alg}))
    (reduce (fn [store {:keys [id revision] :as entry}]
              (when-not (transaction? entry)
                (fail (str "corrupt log line " (:torn-line entry)) {:type :store/corrupt-log :line (:torn-line entry) :cause "unterminated line"}))
              (let [plan (or (plan-of b entry)
                             (fail "missing resolved plan for a log line" {:type :store/corrupt-log :id id :cause "plan not found in the CAS"}))
                    store (load-plan-objects b store plan)
                    r (revision/apply-plan store plan)]
                (when (not= id (:id (:transaction r))) (fail "replayed transaction id differs from log" {:logged id :got (:id (:transaction r))}))
                (when (not= revision (:revision-id r)) (fail "replayed revision id differs from log" {:logged revision :got (:revision-id r) :tx id}))
                (:store r)))
            ;; the route schema of every replayed write is the one store.edn declares
            (empty-store host capabilities (or fv 1) (descriptor-route-schema descriptor))
            log)))

(defn replay
  "Rebuilds the in-memory store from log.edn alone (through the apply-plan of
   the store's :format-version), checking each logged transaction and revision
   id. Every line counts, an uncommitted tail included. Returns the store value."
  [b host]
  (replay-log b host (read-log b)))

(defn replay-open
  "Full verification by replay, for both formats: `replay` plus consistency of
   heads.edn with the replayed heads (when present). Trusts only store.edn and
   log.edn. An :uncommitted log tail (`log-tail`) is not replayed: the store
   opens at the recorded heads (`uncommitted-tail` and `verify` report it).
   Returns the store value."
  [b host]
  (let [hd (read-heads b)
        store (replay-log b host (committed-log b))]
    (when (and hd (not= hd (heads-record store)))
      (fail "heads.edn does not match the replayed log" {:file hd :replayed (heads-record store)}))
    store))

(defn- revision-object? [o]
  (and (map? o) (contains? o :tx) (contains? o :parents) (contains? o :trees) (contains? o :edges)))

(defn- well-formed-heads?
  "Format 2: `no-heads` or {:head id :heads-root id}; format 1: {:head id|nil :heads {tree-id rev-id}}."
  [fv hd]
  (and (map? hd)
       (or (= no-heads hd)
           (if (= 1 fv)
             (and (= #{:head :heads} (set (keys hd)))
                  (map? (:heads hd)) (every? string? (keys (:heads hd))) (every? string? (vals (:heads hd)))
                  (string? (:head hd)))
             (or (and (= #{:head :heads-root} (set (keys hd)))
                      (string? (:head hd)) (string? (:heads-root hd)))
                 (and (= #{:head :heads-root :routes-root} (set (keys hd)))
                      (string? (:head hd)) (string? (:heads-root hd)) (string? (:routes-root hd)))
                 (and (= #{:head :heads-root :routes-root :routes-object} (set (keys hd)))
                      (string? (:head hd)) (string? (:heads-root hd))
                      (string? (:routes-root hd)) (string? (:routes-object hd))))))))

(defn- checked-heads
  "heads.edn of `b` (absent = `no-heads`), raising :store/corrupt-heads when it
   is not the shape of format `fv` (`heads-record`; an unreadable file is the
   backend's to report, with the same type)."
  [b fv]
  (let [hd (or (read-heads b) no-heads)]
    (when-not (well-formed-heads? fv hd) (fail "heads.edn is malformed" {:type :store/corrupt-heads :heads hd}))
    hd))

(defn- checked-head-object
  "The object `id` named by heads.edn, read from `b` and checked: present,
   H(content) == id, and of the expected shape (`ok?`, named `shape`: a
   Revision for :head, an mmap node for :heads-root). Raises :store/corrupt-head
   with :reason :missing | :hash-mismatch | :not-a-revision | :not-an-mmap-node."
  [b host id ok? shape]
  (let [content (read-object b id)
        bad (fn [reason] (fail (str "head object " id ": " (name reason)) {:type :store/corrupt-head :id id :reason reason}))]
    (cond
      (nil? content) (bad :missing)
      (not= id (ports/digest-str (ports/hasher host) content)) (bad :hash-mismatch)
      ;; the hash is over the text just read; only then may the cache spare the parse or learn the object
      :else (let [o (or (when (caching? b) (cached-object b id)) (deserialize id content))]
              (if (ok? o)
                (if (caching? b) (cache-object! b id o) o)
                (bad shape))))))

(defn- flat-route-object?
  "Shape of the durable route-map object of schema 1: `{:route/version …
   :entries path→entry}`."
  [o]
  (and (map? o) (= route/schema (:route/version o)) (map? (:entries o))))

(defn- route-object?
  "Shape of the object `:routes-object` names: the flat map of schema 1 or the
   root node of the tree of schema 2."
  [o]
  (or (flat-route-object? o) (route-tree/node? o)))

(defn- open-from-heads
  [b host capabilities fv rs]
  (let [lm (ports/lazy-maps host)
        hd (checked-heads b fv)
        {:keys [head heads-root routes-root routes-object]} hd
        checked (cond-> {}
                  head (assoc head (checked-head-object b host head revision-object? :not-a-revision))
                  heads-root (assoc heads-root (checked-head-object b host heads-root mmap/node? :not-an-mmap-node))
                  routes-object (assoc routes-object
                                       (checked-head-object b host routes-object route-object? :not-a-route-map)))
        ;; the object itself says which schema it is; store.edn decides only while
        ;; the store has no route yet
        tree? (route-tree/node? (get checked routes-object))
        cas-routes (when (and routes-object (not tree?)) (some-> (get checked routes-object) :entries))
        cached-routes (when (and routes-root (not tree?) (satisfies? RouteCache b))
                        (read-routes b routes-root head))
        route-ok? (fn [m]
                    (boolean
                     (and (map? m)
                          (err/rescue-any
                           (fn [_] false)
                           (let [es (route/canonical-entries host (vals m))]
                             (and (every? (fn [[k e]]
                                            (= k (:path (route/entry host e))))
                                          m)
                                  (= routes-root (route/root host es))
                                  (= routes-object (route/entries-digest host es))))))))
        cached-ok? (route-ok? cached-routes)
        cas-ok? (route-ok? cas-routes)
        raw-routes (if cached-ok? cached-routes (when cas-ok? cas-routes))
        objects (ports/lazy-map lm
                                (fn [id] (when (string? id) (load-object b id)))
                                #(object-ids b)
                                checked)
        load #(get objects %)
        ;; schema 2: the root node was read and rehashed above; here its route hash is
        ;; checked against the heads, and every other node is read and checked against
        ;; its parent only when a lookup goes through it
        tree-ok? (and tree?
                      (err/rescue-any (fn [_] false)
                                      (some? (route-tree/root-node host load routes-root routes-object))))
        routes (cond
                 tree-ok? (ports/lazy-map lm
                                          #(route-tree/lookup host load routes-object %)
                                          #(map :path (route-tree/entries host load routes-object))
                                          {})
                 raw-routes raw-routes)
        heads (if heads-root
                (ports/lazy-map lm #(mmap/lookup host load heads-root % nil) #(mmap/keys load heads-root) {})
                {})
        head-ids #(distinct (remove nil? (cons head (vals heads))))
        revisions (ports/lazy-map lm
                                  (fn [id] (let [o (get objects id)] (when (revision-object? o) o)))
                                  ;; every revision = the ancestors of the heads (walks history: only when enumerated)
                                  (fn [] (loop [todo (vec (head-ids)) seen #{}]
                                           (if-let [id (peek todo)]
                                             (if (seen id)
                                               (recur (pop todo) seen)
                                               (recur (into (pop todo) (:parents (get objects id))) (conj seen id)))
                                             seen)))
                                  {})]
    (revision/lazy-store {:host host :capabilities capabilities :format-version fv
                          :objects objects :revisions revisions :head head :heads heads :heads-root heads-root
                          :routes-root routes-root :routes-object routes-object :routes routes
                          :routes-schema (cond tree? 2 routes-object 1 :else rs)
                          :routes-available? (boolean (or (nil? routes-root) (some? routes)))
                          :routes-invalid? (boolean (and routes-root
                                                         (or (and tree? (not tree-ok?))
                                                             (and cached-routes (not cached-ok?)))))})))

(defn open
  "Opens the store in backend `b`.

   Formats 2–4 on a host with the LazyMaps port: NO replay, the log is not read.
   CHECKS: store.edn exists and its :hash-alg is the host's; heads.edn is
   well-formed (`no-heads` or {:head id :heads-root id}, :store/corrupt-heads);
   the :head object exists, its content hashes to the id and is a Revision,
   and the :heads-root object exists, hashes to its id and is an mmap node
   (:store/corrupt-head, :reason :missing | :hash-mismatch | :not-a-revision |
   :not-an-mmap-node). Reads exactly those two objects, whatever the number
   of trees or the length of the history.
   TRUSTS: that heads.edn names the heads the log leads to (it is the commit
   point; `replay-open` checks it); the per-tree heads below :heads-root (the
   other mmap nodes and the Revisions they name, read on demand: `verify`
   rehashes them, `replay-open` recomputes the root); every other object, which is read lazily
   on demand WITHOUT rehashing (`verify` checks them all); the :incidence map
   and every other derived structure inside the Revisions (`replay-open`
   recomputes them). An :uncommitted log tail is not looked for: the store
   opens at the recorded heads either way, `verify` reports the tail and the
   next `commit-tx!` discards it.
   The value returned is `revision/lazy-store`: :objects (every id of the
   backend; orphans of a losing writer included when enumerated), :revisions
   (ancestors of the heads), :trees and :edges are computed on first use.

   Format 1, or a host without the port: `replay-open` (full replay)."
  [b host]
  (let [{:keys [capabilities hash-alg] fv :format-version :as descriptor} (or (read-descriptor b) (fail "no store.edn" {}))
        alg (ports/algorithm (ports/hasher host))]
    (if (and (>= (or fv 1) 2) (ports/lazy-maps? host))
      (do (when (not= hash-alg alg) (fail "hash algorithm mismatch" {:store hash-alg :hasher alg}))
          (open-from-heads b host capabilities fv (descriptor-route-schema descriptor)))
      (replay-open b host))))

;; ---------------------------------------------------------------- commit

(defn- tx-line
  "The log line written for transaction `tx` of `store`: the whole Transaction
   in formats 1–2, `{:id :revision}` in formats 3–4 — the resolved plan lives in
   the CAS (legacy) or the derived plan cache and the line never duplicates it."
  [store tx]
  (if (>= (or (:format-version store) 2) 3) (dissoc tx :plan) tx))

(defn commit!
  "Applies `plan` to the in-memory `store` (loaded from backend `b`), then
   persists: the objects the plan created (:new-objects; format 1: the objects
   absent from the loaded store), then the Transaction line and the heads
   through `commit-tx!` against the heads the caller loaded. When another
   writer moved them the result is :store/stale and neither log.edn nor
   heads.edn changed; the objects already written stay as unreferenced
   write-once files. Both formats take this path. Never lists the backend's
   objects. Returns the apply result."
  [b store plan]
  (let [host (:host store)
        r (revision/apply-plan store plan)
        s' (:store r)
        tx (:transaction r)
        ;; Only format 4 is the K2a plan-cache format. Format 3 is legacy:
        ;; its resolved plan stays in the CAS so its replay path is unchanged.
        plan-cache? (and (satisfies? PlanCache b)
                         (>= (or (:format-version store) 2) 4))
        new-objects (cond-> (if (revision/legacy? store)
                              (into {} (remove #(contains? (:objects store) (key %))) (:objects s'))
                              (:new-objects r))
                      plan-cache? (dissoc (:id tx)))
        expected (heads-record store)]
    ;; The plan is provenance/audit, not authority: with a PlanCache backend it
    ;; is written to the derived cache before the commit, so a successful commit
    ;; is replayable. A backend without the port keeps the legacy CAS shape.
    (when plan-cache? (write-plan! b (:id tx) (:plan tx)))
    (when (and (satisfies? RouteCache b)
               (:routes-write? s')
               (:routes-available? s')
               (some? (:routes-root s'))
               (some? (:routes s')))
      (write-routes! b (:routes-root s') (:head s') (:routes s')))
    (doseq [[id obj] new-objects] (write-object! b id (serialize host obj)))
    (when-not (commit-tx! b expected (tx-line store tx) (heads-record s'))
      (fail "heads moved since the store was loaded; reload and retry" {:type :store/stale :expected expected}))
    (if plan-cache?
      (-> r
          (update :store update :objects dissoc (:id tx))
          (update :new-objects dissoc (:id tx)))
      r)))

(defn upgrade-routes!
  "Moves the store of backend `b` from route schema 1 (one flat map object) to
   schema 2 (the Merkle tree by path, `kimun.kernel.route-tree`), in place and
   only when asked (docs/v2/10 §11.2). The same entries, tree ids included, are
   written as tree nodes; the heads then name the root of the tree and
   store.edn records `:route-schema 2`. No transaction is added and no revision
   changes: the head, the per-tree heads and every document stay as they were.
   The route root CHANGES, because schema 2 hashes a tree and schema 1 a list;
   both are returned.

   Order: node objects (unreferenced until the next step), heads
   (`swap-heads!`, refused with :store/stale when a writer moved them), then
   store.edn. Stopped between the last two, the store opens and writes as
   schema 2 and a second call completes it.

   Returns `{:upgraded bool :route-schema 2 :routes n}` plus, when the heads
   changed, `:routes-root-before`, `:routes-root`, `:routes-object` and `:nodes`
   (node objects of the tree). `:upgraded false`: there was nothing to do.
   Raises :store/error for a format other than 4, a backend without
   `HeadsSwap`, or a schema-1 route map that cannot be read (rebuild it first:
   nothing is invented)."
  [b host]
  (let [descriptor (or (read-descriptor b) (fail "no store.edn" {}))
        store (open b host)
        declared (descriptor-route-schema descriptor)
        record! #(write-descriptor! b (assoc descriptor :route-schema 2))]
    (when-not (= 4 (:format-version descriptor))
      (fail "route schema 2 needs a format-4 store" {:format-version (:format-version descriptor)}))
    (cond
      ;; no route yet, or the heads already name a tree: only store.edn may be behind
      (or (nil? (:routes-object store)) (= 2 (:routes-schema store)))
      (do (when (not= 2 declared) (record!))
          {:upgraded (not= 2 declared) :route-schema 2
           :routes (if (:routes-object store) (count (:routes store)) 0)})

      (not (:routes-available? store))
      (fail "the route map of schema 1 cannot be read; rebuild the store before upgrading its routes"
            {:type :store/error :routes-root (:routes-root store)})

      (not (satisfies? HeadsSwap b))
      (fail "this backend cannot replace its heads in place" {:type :store/error})

      :else
      (let [entries (vec (vals (:routes store)))
            tree (route-tree/from-entries host entries)
            expected (heads-record store)
            heads' (assoc expected :routes-root (:hash tree) :routes-object (:object tree))]
        ;; the tree must give back exactly what the flat map held before anything is replaced
        (when (not= (route/canonical-entries host entries) (route-tree/entries host (:new tree) (:object tree)))
          (fail "the route tree does not hold the entries of the flat map" {:type :store/error}))
        (doseq [[id node] (:new tree)] (write-object! b id (serialize host node)))
        (when-not (swap-heads! b expected heads')
          (fail "heads moved while the routes were being upgraded; run the upgrade again" {:type :store/stale :expected expected}))
        (record!)
        {:upgraded true :route-schema 2 :routes (count entries)
         :routes-root-before (:routes-root store) :routes-root (:hash tree)
         :routes-object (:object tree) :nodes (count (:new tree))}))))

(defn route-coverage
  "Verifiable coverage of the durable route map against the documents CURRENTLY
   anchored to a file at the head: a cached tree counts only when an active
   `:reference` edge points from its root to an external node. Historical trees
   left by untrack do not count and do not make the root partial. Returns
   `:missing-trees` (anchored tree ids with no route) and `:orphan-routes`
   (paths whose tree is no longer anchored). `:complete?` is false when the
   route source is unavailable, even if both lists are empty. This reports
   coverage; it does not invent a missing route."
  [store]
  (let [trees (revision/tree-descriptors store)
        root->tree (into {} (map (fn [[tid d]] [(:root d) tid]) trees))
        external? (fn [id] (= :external (:kind (revision/get-object store id))))
        anchored (set (keep (fn [e]
                              (when (and (= :reference (:type e))
                                         (external? (:to e)))
                                (get root->tree (:from e))))
                            (map #(revision/get-object store %) (revision/active-edges store))))
        cached (filter (fn [[tid d]] (and (true? (:cached? d)) (contains? anchored tid))) trees)
        anchored-ids (set anchored)
        cached-ids (set (map first cached))
        routes (or (:routes store) {})
        owners (set (keep :tree-id (vals routes)))
        missing (vec (sort (remove owners cached-ids)))
        orphan (vec (sort (keep (fn [[p e]]
                                  (when-not (contains? anchored-ids (:tree-id e)) p))
                                routes)))]
    {:complete? (boolean (and (:routes-available? store)
                              (empty? missing) (empty? orphan)))
     :routes-available? (boolean (:routes-available? store))
     :routes-root (:routes-root store)
     :cached-trees (count cached)
     :routed-trees (count (filter owners cached-ids))
     :missing-trees missing
     :orphan-routes orphan}))

;; ---------------------------------------------------------------- verify

(defn- object-or-nil
  "Object `id` of the `:objects` view, or nil when it is absent OR unreadable:
   on a lazy store the lookup parses the stored bytes, which may raise for a
   damaged file. The reachability walk must report damage, never raise."
  [objs id]
  (err/rescue-any (fn [_] nil) (get objs id)))

(defn- references
  "What object `o`, reached as `kind`, refers to: a seq of [id kind]. Kinds:
   :obj (the id must be a stored object), :heads (a node of the per-tree heads
   mmap of heads.edn: its leaf values are Revisions, its keys tree ULIDs), :trees (a node of a Revision's :trees
   mmap: its leaf keys are tree ULIDs, not objects) and :soft (format-1 shapes
   and edge endpoints: followed only when stored, as before format 2)."
  [o kind]
  (let [as (fn [k ids] (for [id ids :when (string? id)] [id k]))]
    (cond
      ;; a node of the route tree (schema 2) names the nodes below it
      (route-tree/node? o) (as :obj (route-tree/child-ids o))
      (mmap/branch? o) (as (if (#{:trees :heads} kind) kind :obj) (mmap/child-ids o))
      (and (mmap/leaf? o) (= :heads kind)) (as :obj (map second (:entries o)))
      (and (mmap/leaf? o) (= :trees kind))
      ;; [descriptor-id merkle-root]: the descriptor is a CAS object; the merkle
      ;; root names tree objects that may be cache, not CAS (docs/v2/14 §18.6)
      ;; the root's kind carries its descriptor id, so reachability decides per tree
      (vec (mapcat (fn [[_ v]] (if (vector? v) [[(first v) :obj] [(second v) [:tree-hash (first v)]]] [])) (:entries o)))
      (mmap/leaf? o) (as :obj (mapcat (fn [[k v]] (concat (when-not (= :trees kind) [k]) (cond (vector? v) v (and (map? v) (:set v)) [(:set v)]))) (:entries o)))
      ;; `:tx` is a provenance digest, not a CAS object: the resolved plan may
      ;; live in the plan cache (K2a). `verify-full` checks plans when present;
      ;; this walk deliberately does not turn a cache absence into corruption.
      (and (map? o) (:incidence o) (:tx o)) (concat (as :trees [(:trees o)]) (as :obj (concat [(:edges o) (:incidence o)] (:parents o))))
      (and (map? o) (:roots o) (:tx o))            (as :soft (concat (vals (:roots o)) [(:trees o) (:edges o)] (:parents o)))
      (and (map? o) (:node o) (contains? o :children)) (as :obj (cons (:node o) (mapcat identity (:children o))))
      (and (vector? o) (every? vector? o))         (as :soft (map second o))
      (and (vector? o) (every? string? o))         (as :soft o)
      (and (map? o) (:tree o) (:root o))           (as :soft [(:root o)])
      (and (map? o) (:type o) (:from o))           (as :soft [(:from o) (:to o) (get-in o [:evidence :context])])
      :else [])))

(defn reachable
  "Ids of every object reachable from the heads of `store` (docs/v2/02 §3.1):
   the transitive closure over id references, acyclic because ids are hashes.
   Format-2 Revisions are walked through their three mmaps (branch children,
   leaf keys, id vectors and the nested edge-id set of a hub
   :incidence entry `{:set root}`), and the per-tree heads through the nodes of
   the :heads-root mmap; format-1 Revisions through :roots and the sets. The
   `:tx` digest is provenance, not an object: a resolved plan in the CAS or the
   PlanCache is verified by `verify-full`, not by this walk.
   For a format-2 `:trees` mmap leaf, the descriptor is followed as an object
   and its merkle root as a `[:tree-hash descriptor-id]`: if the tree is marked `:cached?` the
   root is derived (cache, docs/v2/14 §19) and is not required to be in the
   CAS; otherwise it is verified like any other object.
   Never raises on damaged content: an id that a format-2 structure names but
   that is absent or unreadable IS in the result (so `verify` reports it) and
   the walk goes on with what it can still read; what was reachable only
   through an unreadable object is not in the result. The empty map, a constant
   (`revision/loader`), is not required in the CAS: absent, it is not reported."
  [store]
  (let [objs (:objects store)
        ;; a tree whose descriptor is marked :cached? keeps its tree objects in the
        ;; store cache (docs/v2/14 §18.6): its root is derived, not required in the CAS.
        ;; A descriptor that is absent, unreadable or altered (its content no longer
        ;; hashes to its id) is reported on its own; its tree is treated as derived so
        ;; one damaged descriptor does not make a whole tree look missing.
        derived-tree? (fn [desc-id]
                        (let [d (object-or-nil objs desc-id)]
                          (or (not (and (map? d) (= desc-id (canon/digest (:host store) d))))
                              (true? (:cached? d)))))
        ;; the empty map is a constant the loader resolves without the CAS (revision/loader):
        ;; ABSENT it is not damage; present but unreadable or altered it is reported as usual
        empty-id (:root (mmap/empty (:host store)))
        absent? (fn [id] (not (err/rescue-any (fn [_] true) (contains? objs id))))]
    ;; format 2: the per-tree heads through the nodes of the heads mmap (a damaged one is
    ;; reported like any other, never raised while enumerating a view); format 1: the :heads map
    (loop [todo (cond-> (if-let [r (:heads-root store)]
                         (into [[r :heads]] (when (:head store) [[(:head store) :obj]]))
                         (mapv #(vector % :obj) (remove nil? (cons (:head store) (vals (:heads store))))))
                   (:routes-object store) (conj [(:routes-object store) :obj]))
           seen #{}]
      (if (empty? todo)
        seen
        (let [[id kind] (peek todo) more (pop todo)]
          (if (seen id)
            (recur more seen)
            (let [o (object-or-nil objs id)]
              (cond
                (and (vector? kind) (derived-tree? (second kind))) (recur more seen)
                (and (nil? o) (= :soft kind)) (recur more seen)
                (and (nil? o) (= empty-id id) (absent? id)) (recur more seen)
                (nil? o) (recur more (conj seen id))
                :else (recur (into more (err/rescue-any (fn [_] []) (vec (references o (if (vector? kind) :obj kind))))) (conj seen id))))))))))

;; ---------------------------------------------------------------- unreachable objects (K5, first cut)

(defn object-class
  "The class of a stored object by its shape: `:revision`, `:mmap-leaf`,
   `:mmap-branch`, `:route-map` (route schema 1), `:route-node` (schema 2),
   `:plan` (a resolved plan kept in the CAS by formats 1–3), `:descriptor`,
   `:node`, `:edge`, `:tree-object`, else `:unknown`. A shape is what the object
   is, not why it is where it is."
  [o]
  (cond
    (mmap/leaf? o) :mmap-leaf
    (mmap/branch? o) :mmap-branch
    (route-tree/node? o) :route-node
    (flat-route-object? o) :route-map
    (revision-object? o) :revision
    (and (map? o) (contains? o :ops) (contains? o :plan/version)) :plan
    (and (map? o) (:tree o) (:root o)) :descriptor
    (and (map? o) (:class o) (:kind o) (contains? o :content)) :node
    (and (map? o) (:type o) (:from o) (:to o)) :edge
    (and (map? o) (:node o) (contains? o :children)) :tree-object
    :else :unknown))

(defn- mentioned-ids
  "Every string inside value `o` that has the form of an object id (64
   lower-case hex characters). A superset of what `o` refers to: it does not
   interpret the shape."
  [o]
  (into #{} (filter #(and (string? %) (re-matches #"[0-9a-f]{64}" %)))
        (tree-seq coll? seq o)))

(defn unreachable
  "The inventory of the objects stored in backend `b` that the heads of
   `store` do not reach (docs/v2/10 §5, §7, §11.3): read-only, it writes
   nothing and removes nothing.

   STORED is every id under objects/ (`object-ids`); the derived cache is not
   looked at. REACHED is `reachable`, the same walk `verify` rehashes: the head
   revision and its whole history, the per-tree heads, and the route map the
   heads name. Unreachable = stored and not reached. Each one is read from the
   store itself (never from a cache) and rehashed, and falls in one of three
   lists:

   - `:held` — a resolved plan (`:plan`) whose id a COMMITTED log line names.
     The walk does not follow it, but without it the log cannot be replayed
     (`verify-full` answers `:replay-unavailable`; formats 1–3 keep plans in
     the CAS): it is not a candidate for anything.
   - `:damaged` — `{:id :reason}` with `:hash-mismatch` or `:unreadable`: its
     class cannot be trusted, so it is only reported.
   - `:candidates` — the rest: `{:id :class :root?}`. `:root?` is true when no
     other unreachable object mentions its id: a root of the heads map or of
     the route map that a later commit replaced or that a losing writer wrote.
     A candidate of class `:revision` is a commit that is not in the history
     of the head: the revision of a writer that lost the race, or of the
     uncommitted tail. It is not a top: the heads map written with it
     mentions it.

   Returns `{:stored n :reached n :unreachable n :candidates [...] :held [...]
   :damaged [...] :stable? bool}` plus `:uncommitted-tail` (the log entry the
   heads do not record) when there is one: what that line wrote is unreachable
   and is among the candidates. `:reached` counts the reached ids that are
   stored. `:stable?` is false when the heads on disk are no longer the ones
   `store` was opened at.

   A candidate is NOT known to be garbage. A writer puts its objects in the
   store before it moves the heads, so an inventory taken during a commit
   lists objects that are about to be reached; removing anything needs the
   writer lock and a second look, which this function does not take."
  [b store]
  (let [host (:host store)
        hasher (ports/hasher host)
        stored (set (object-ids b))
        live (reachable store)
        [committed tail] (split-log b)
        logged (into #{} (keep :id) committed)
        read (fn [id]
               (let [content (stored-content b id)]
                 (cond
                   (or (nil? content) (not= id (ports/digest-str hasher content))) {:id id :damage :hash-mismatch}
                   :else (let [o (err/rescue-any (fn [_] ::unreadable) (deserialize id content))]
                           (if (= ::unreadable o)
                             {:id id :damage :unreadable}
                             {:id id :class (object-class o) :mentions (disj (mentioned-ids o) id)})))))
        items (mapv read (sort (remove live stored)))
        sound (remove :damage items)
        mentioned (into #{} (mapcat :mentions) sound)
        held? (fn [i] (and (= :plan (:class i)) (contains? logged (:id i))))]
    (cond-> {:stored (count stored)
             :reached (count (filter stored live))
             :unreachable (count items)
             :candidates (mapv (fn [i] {:id (:id i) :class (:class i) :root? (not (contains? mentioned (:id i)))})
                               (remove held? sound))
             :held (mapv #(select-keys % [:id :class]) (filter held? sound))
             :damaged (mapv (fn [i] {:id (:id i) :reason (:damage i)}) (filter :damage items))
             :stable? (= (heads-record store) (or (read-heads b) no-heads))}
      tail (assoc :uncommitted-tail tail))))

(defn- plan-ids
  "The transaction digests named by log entries, ascending. Reading the log
   avoids parsing a damaged older Revision just to decide which plans to check."
  [log]
  (->> log
       (map :id)
       (remove nil?)
       set
       sort
       vec))

(defn verify
  "The cheap-ish check: recomputes H(file) for every object reachable from the
   heads of `store` (detects a missing or tampered object; does not read the
   log beyond its tail, does not detect heads that disagree with it, and does
   not check transaction plans: `verify-full` does). Returns
   {:ok? bool :checked n :bad [{:id :reason (:missing | :hash-mismatch)}]},
   plus :uncommitted-tail <log entry> when the log ends with a line the heads
   do not record (reported, not an error: see `log-tail`). Also reads the log
   (no replay) to compare its END with the heads: an :inconsistent tail
   (`log-tail`: the last committed line is not the recorded head, e.g. a log
   cut or overwritten) is the bad entry {:reason :log-heads-mismatch}."
  [b store]
  (let [hasher (ports/hasher (:host store))
        log (vec (read-log b))
        ids (sort (reachable store))
        state (log-tail (read-heads b) (last-two log))
        tail (when (= :uncommitted state) (peek log))
        bad (concat
             (for [id ids
                   :let [content (stored-content b id)
                         reason (cond (nil? content) :missing
                                      (not= id (ports/digest-str hasher content)) :hash-mismatch)]
                   :when reason]
               {:id id :reason reason})
             (when (= :inconsistent state) [{:reason :log-heads-mismatch}]))]
    (cond-> {:ok? (empty? bad) :checked (count ids) :bad (vec bad)}
      tail (assoc :uncommitted-tail tail))))

(defn- cached-plan
  "The plan object of `id`: legacy CAS text first, then the PlanCache value;
   nil when neither exists. A damaged cached plan is reported as missing, never
   as a corrupt object."
  [b id]
  (or (when (satisfies? PlanCache b)
        (err/rescue-any (fn [_] nil) (read-plan b id)))
      (err/rescue-any (fn [_] nil) (read-object b id))))

(defn- plan-check
  "Plan evidence of `store`: `{:ids [...] :missing [...] :bad [...]}`. A plan
   present in the CAS or the cache must hash to its transaction id; an absent
   plan is `:missing`, not `:bad`."
  [b host log]
  (let [ids (plan-ids log)
        entries (keep (fn [entry]
                        (when (string? (:id entry))
                          [(:id entry) (or (:plan entry) (cached-plan b (:id entry)))]))
                      log)
        digest (fn [content]
                 (if (string? content)
                   (ports/digest-str (ports/hasher host) content)
                   (canon/digest host content)))
        missing (mapv first (remove second entries))
        bad (vec (for [[id content] entries
                       :when (and content
                                  (not= id (err/rescue-any (fn [_] nil) (digest content))))]
                   {:id id :reason :hash-mismatch}))]
    {:ids ids :missing missing :bad bad}))

(defn- log-id-mismatches
  "Log entries whose `:id` disagrees with the `:tx` of the Revision they name.
   A damaged/unreadable Revision is left to `verify`/replay; this check catches
   a hand-altered durable log line before a missing plan can be mistaken for a
   cache absence."
  [store log]
  (vec (for [{:keys [id revision]} log
             :when (and (string? id) (string? revision))
             :let [rev (err/rescue-any (fn [_] nil) (get (:revisions store) revision))]
             :when (and (map? rev) (string? (:tx rev)) (not= id (:tx rev)))]
         {:id id :revision revision :tx (:tx rev)})))

(defn verify-full
  "The full verification. Replays the log only when every transaction plan is
   available from the CAS or the `PlanCache`; a complete replay checks every
   transaction and revision id, the final heads and the recomputed incidence,
   then `verify` checks the replayed store. A missing plan is not corruption:
   the object check still runs on the store opened from the heads, and the
   result carries `:replay-unavailable {:reason :plan-cache-missing :missing
   [tx-id …]}`; the command must not claim a complete replay. A present plan
   whose digest differs is `:bad` (`:hash-mismatch`). Never raises for a corrupt
   store: returns `verify`'s map plus `:store` and `:replay`, or
   `{:ok? false :checked 0 :bad [{:reason :replay-failed …}]}`."
  [b host]
  (let [opened (err/rescue-any (fn [_] nil) (open b host))
        log-result (err/rescue-any
                    (fn [e] {::log-failed {:reason :replay-failed :message (ex-message e)
                                           :data (dissoc (ex-data e) :file :replayed)}})
                    (vec (committed-log b)))
        log-error (::log-failed log-result)
        log (if log-error [] log-result)
        log-bad (when opened (log-id-mismatches opened log))
        plans (when opened (plan-check b host log))
        missing (:missing plans)
        plan-bad (:bad plans)
        replayed (when-not (or log-error (seq log-bad) (seq plan-bad) (seq missing))
                   (err/rescue (fn [e] {::failed {:reason :replay-failed :message (ex-message e)
                                                   :data (dissoc (ex-data e) :file :replayed)}})
                               (replay-open b host)))]
    (cond
      log-error
      {:ok? false :checked 0 :bad [log-error]}

      (seq log-bad)
      {:ok? false :checked 0
       :bad [{:reason :replay-failed
              :message "a durable log line names a transaction id that is not its revision's tx"
              :data {:log-id-mismatch log-bad}}]}

      ;; A present plan whose digest differs is a hash mismatch. Do not replay:
      ;; replaying would report only :replay-failed and lose the evidence.
      (and (seq plan-bad) opened)
      (let [v (verify b opened)]
        (assoc v
               :store opened
               :bad (vec (concat (:bad v) plan-bad))
               :ok? false
               :replay {:status :skipped :reason :plan-hash-mismatch}))

      (and (seq missing) opened)
      (let [v (verify b opened)]
        (assoc v
               :store opened
               :bad (vec (concat (:bad v) plan-bad))
               :ok? (and (:ok? v) (empty? plan-bad))
               :replay-unavailable {:reason :plan-cache-missing :missing missing}
               :replay {:status :unavailable :reason :plan-cache-missing}))

      (::failed replayed)
      {:ok? false :checked 0 :bad [(::failed replayed)]}

      (map? replayed)
      (let [v (verify b replayed)
            bad (vec (concat (:bad v) plan-bad))]
        (assoc v :store replayed :bad bad :ok? (and (:ok? v) (empty? bad))
               :replay {:status :complete}))

      :else
      {:ok? false :checked 0 :bad [{:reason :replay-failed
                                    :message "the store could not be opened for verification"}]})))
