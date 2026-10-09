(ns kimun.kernel.store
  "Persistence port and the host-independent parts of open/replay/verify
   (docs/v2/02 §8.1, milestone 3).

   Layout (files-only backend, see kimun.host.fs-store):
     <dir>/store.edn      {:format-version 2 :hash-alg :sha-256 :capabilities {...}}
     <dir>/objects/<id>   canonical EDN text of the hashed form of the object; H(file) == id
     <dir>/log.edn        one Transaction map {:id :plan :revision} per line, append-only
     <dir>/heads.edn      format 2: {:head rev-id :heads-root mmap-root-id} (the per-tree heads
                          tree-id -> rev-id are an mmap in the CAS); format 1: {:head rev-id
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

   :format-version 2 (new stores) is the revision format of kimun.kernel.revision;
   :format-version 1 is served by kimun.kernel.legacy.revision. The store value
   carries :format-version and every function here dispatches on it.

   `open` (format 2, host with the LazyMaps port) reads store.edn and
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
            [kimun.kernel.revision :as revision]))

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

;; ---------------------------------------------------------------- object cache (optional port)

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
   does not grow with the number of trees); format 1 {:head rev-id :heads {tree-id rev-id}}."
  [store]
  (cond (nil? (:head store)) no-heads
        (revision/legacy? store) {:head (:head store) :heads (:heads store)}
        :else {:head (:head store) :heads-root (:heads-root store)}))

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

(defn- empty-store
  "The empty store value of format `fv` (1 = legacy, 2/3 = current)."
  [host capabilities fv]
  (case fv
    1 (assoc (legacy/empty-store host capabilities) :format-version 1)
    (2 3 4) (assoc (revision/empty-store host capabilities) :format-version fv)
    (fail "unsupported store format" {:format-version fv})))

(defn init!
  "Creates a new store in backend `b` with the given capabilities; returns the
   empty in-memory store. `opts` {:format-version 1} creates a legacy format-1
   store (tests and upgrade); the default is `format-version`."
  ([b host capabilities] (init! b host capabilities {}))
  ([b host capabilities opts]
   (when (read-descriptor b) (fail "store already initialised" {}))
   (let [fv (or (:format-version opts) format-version)
         store (empty-store host capabilities fv)]
     (write-descriptor! b {:format-version fv :hash-alg (ports/algorithm (ports/hasher host)) :capabilities capabilities})
     store)))

(defn- plan-of
  "The resolved plan of a log entry of `b`: `:plan` when the line carries it
   (format 2), else the CAS object `:id` (format 3; its text is what is
   hashed, and the caller checks `H(text) == id` through `apply-plan`)."
  [b entry]
  (or (:plan entry)
      (some-> (read-object b (:id entry)) edn/read-string)))

(defn- replay-log
  "Replays the entries `log` over the empty store of backend `b`."
  [b host log]
  (let [{:keys [capabilities hash-alg] fv :format-version} (or (read-descriptor b) (fail "no store.edn" {}))
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
            (empty-store host capabilities (or fv 1))
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
             (and (= #{:head :heads-root} (set (keys hd))) (string? (:head hd)) (string? (:heads-root hd)))))))

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

(defn- open-from-heads
  [b host capabilities fv]
  (let [lm (ports/lazy-maps host)
        hd (checked-heads b fv)
        {:keys [head heads-root]} hd
        checked (cond-> {}
                  head (assoc head (checked-head-object b host head revision-object? :not-a-revision))
                  heads-root (assoc heads-root (checked-head-object b host heads-root mmap/node? :not-an-mmap-node)))
        objects (ports/lazy-map lm
                                (fn [id] (when (string? id) (load-object b id)))
                                #(object-ids b)
                                checked)
        load #(get objects %)
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
                          :objects objects :revisions revisions :head head :heads heads :heads-root heads-root})))

(defn open
  "Opens the store in backend `b`.

   Format 2 on a host with the LazyMaps port: NO replay, the log is not read.
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
  (let [{:keys [capabilities hash-alg] fv :format-version} (or (read-descriptor b) (fail "no store.edn" {}))
        alg (ports/algorithm (ports/hasher host))]
    (if (and (>= (or fv 1) 2) (ports/lazy-maps? host))
      (do (when (not= hash-alg alg) (fail "hash algorithm mismatch" {:store hash-alg :hasher alg}))
          (open-from-heads b host capabilities fv))
      (replay-open b host))))

;; ---------------------------------------------------------------- commit

(defn- tx-line
  "The log line written for transaction `tx` of `store`: the whole Transaction
   in formats 1–2, `{:id :revision}` in format 3 — the resolved plan is in the
   CAS (invariant 17) and the line no longer duplicates it."
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
        new-objects (if (revision/legacy? store)
                      (into {} (remove #(contains? (:objects store) (key %))) (:objects s'))
                      (:new-objects r))
        expected (heads-record store)]
    (doseq [[id obj] new-objects] (write-object! b id (serialize host obj)))
    (when-not (commit-tx! b expected (tx-line store (:transaction r)) (heads-record s'))
      (fail "heads moved since the store was loaded; reload and retry" {:type :store/stale :expected expected}))
    r))

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
      (mmap/branch? o) (as (if (#{:trees :heads} kind) kind :obj) (mmap/child-ids o))
      (and (mmap/leaf? o) (= :heads kind)) (as :obj (map second (:entries o)))
      (and (mmap/leaf? o) (= :trees kind))
      ;; [descriptor-id merkle-root]: the descriptor is a CAS object; the merkle
      ;; root names tree objects that may be cache, not CAS (docs/v2/14 §18.6)
      (vec (mapcat (fn [[_ v]] (if (vector? v) [[(first v) :obj] [(second v) :tree-hash]] [])) (:entries o)))
      (mmap/leaf? o) (as :obj (mapcat (fn [[k v]] (concat (when-not (= :trees kind) [k]) (cond (vector? v) v (and (map? v) (:set v)) [(:set v)]))) (:entries o)))
      (and (map? o) (:incidence o) (:tx o)) (concat (as :trees [(:trees o)]) (as :obj (concat [(:edges o) (:incidence o) (:tx o)] (:parents o))))
      (and (map? o) (:roots o) (:tx o))            (as :soft (concat (vals (:roots o)) [(:trees o) (:edges o) (:tx o)] (:parents o)))
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
   the :heads-root mmap; format-1 Revisions through :roots and the sets.
   For a format-2 `:trees` mmap leaf, the descriptor is followed as an object
   and its merkle root as a `:tree-hash`: if the tree is marked `:cached?` the
   root is derived (cache, docs/v2/14 §19) and is not required to be in the
   CAS; otherwise it is verified like any other object.
   Never raises on damaged content: an id that a format-2 structure names but
   that is absent or unreadable IS in the result (so `verify` reports it) and
   the walk goes on with what it can still read; what was reachable only
   through an unreadable object is not in the result. The empty map, a constant
   (`revision/loader`), is not required in the CAS: absent, it is not reported."
  [store]
  (let [objs (:objects store)
        cached (err/rescue-any (fn [_] #{}) (revision/cached-tree-roots store))
        ;; the empty map is a constant the loader resolves without the CAS (revision/loader):
        ;; ABSENT it is not damage; present but unreadable or altered it is reported as usual
        empty-id (:root (mmap/empty (:host store)))
        absent? (fn [id] (not (err/rescue-any (fn [_] true) (contains? objs id))))]
    ;; format 2: the per-tree heads through the nodes of the heads mmap (a damaged one is
    ;; reported like any other, never raised while enumerating a view); format 1: the :heads map
    (loop [todo (if-let [r (:heads-root store)]
                  (into [[r :heads]] (when (:head store) [[(:head store) :obj]]))
                  (mapv #(vector % :obj) (remove nil? (cons (:head store) (vals (:heads store))))))
           seen #{}]
      (if (empty? todo)
        seen
        (let [[id kind] (peek todo) more (pop todo)]
          (if (seen id)
            (recur more seen)
            (let [o (object-or-nil objs id)]
              (cond
                (and (= :tree-hash kind) (contains? cached id)) (recur more seen)
                (and (nil? o) (= :soft kind)) (recur more seen)
                (and (nil? o) (= empty-id id) (absent? id)) (recur more seen)
                (nil? o) (recur more (conj seen id))
                :else (recur (into more (err/rescue-any (fn [_] []) (vec (references o kind)))) (conj seen id))))))))))

(defn verify
  "The cheap-ish check: recomputes H(file) for every object reachable from the
   heads of `store` (detects a missing or tampered object; does not read the
   log beyond its tail and does not detect heads that disagree with it). Returns
   {:ok? bool :checked n :bad [{:id :reason (:missing | :hash-mismatch)}]},
   plus :uncommitted-tail <log entry> when the log ends with a line the heads
   do not record (reported, not an error: see `log-tail`). Also reads the log
   (no replay) to compare its END with the heads: an :inconsistent tail
   (`log-tail`: the last committed line is not the recorded head, e.g. a log
   cut or overwritten) is the bad entry {:reason :log-heads-mismatch}."
  [b store]
  (let [hasher (ports/hasher (:host store))
        ids (sort (reachable store))
        log (vec (read-log b))
        state (log-tail (read-heads b) (last-two log))
        tail (when (= :uncommitted state) (peek log))
        bad (concat
             (for [id ids
                   :let [content (read-object b id)
                         reason (cond (nil? content) :missing
                                      (not= id (ports/digest-str hasher content)) :hash-mismatch)]
                   :when reason]
               {:id id :reason reason})
             (when (= :inconsistent state) [{:reason :log-heads-mismatch}]))]
    (cond-> {:ok? (empty? bad) :checked (count ids) :bad (vec bad)}
      tail (assoc :uncommitted-tail tail))))

(defn verify-full
  "The full verification, trusting only store.edn and log.edn: `replay-open`
   (every logged plan through apply-plan, every transaction and revision id,
   the final heads against heads.edn, :incidence recomputed) and then `verify`
   over the REPLAYED store. Detects what `verify` detects plus a truncated or
   altered log line and heads that disagree with the log. Never raises for a
   corrupt store: returns `verify`'s map plus :store (the replayed value), or
   {:ok? false :checked 0 :bad [{:reason :replay-failed :message m :data d}]}."
  [b host]
  (let [s (err/rescue (fn [e] {::failed {:reason :replay-failed :message (ex-message e) :data (dissoc (ex-data e) :file :replayed)}})
                      (replay-open b host))]
    (if-let [f (::failed s)]
      {:ok? false :checked 0 :bad [f]}
      (assoc (verify b s) :store s))))
