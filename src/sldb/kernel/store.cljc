(ns sldb.kernel.store
  "Persistence port and the host-independent parts of open/replay/verify
   (docs/v2/02 §8.1, milestone 3).

   Layout (files-only backend, see sldb.host.fs-store):
     <dir>/store.edn      {:format-version 1 :hash-alg :sha-256 :capabilities {...}}
     <dir>/objects/<id>   canonical EDN text of the hashed form of the object; H(file) == id
     <dir>/log.edn        one Transaction map per line, append-only
     <dir>/heads.edn      {:head rev-id :heads {tree-id rev-id}}, written atomically

   `open` never trusts objects/ or heads.edn for state: it replays log.edn
   through sldb.kernel.revision/apply-plan and checks every revision id against
   the log. `verify` walks the objects reachable from the heads and recomputes
   their hashes."
  (:require [sldb.kernel.canon :as canon]
            [sldb.kernel.node :as node]
            [sldb.kernel.edge :as edge]
            [sldb.kernel.ports :as ports]
            [sldb.kernel.err :as err]
            [sldb.kernel.revision :as revision]))

(defprotocol Backend
  "Port implemented by a persistence adapter (ring 1)."
  (read-descriptor [b] "The store.edn map, or nil when the directory is not a store.")
  (write-descriptor! [b descriptor] "Creates the store layout and writes store.edn.")
  (read-object [b id] "String content of objects/<id>, or nil.")
  (write-object! [b id content] "Writes objects/<id> once; existing objects are never rewritten.")
  (object-ids [b] "Ids present under objects/.")
  (append-tx! [b tx] "Appends one Transaction map as a line of log.edn.")
  (read-log [b] "Vector of Transaction maps in log order.")
  (read-heads [b] "{:head :heads} or nil.")
  (cas-heads! [b expected new] "Atomically replaces heads when the current value equals `expected`; returns true/false."))

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

(defn- fail [why data] (err/raise (or (:type data) :store/error) (str "store: " why) data))

;; ---------------------------------------------------------------- init / open

(defn init!
  "Creates a new store in backend `b` with the given capabilities; returns the
   empty in-memory store."
  [b host capabilities]
  (when (read-descriptor b) (fail "store already initialised" {}))
  (write-descriptor! b {:format-version 1 :hash-alg (ports/algorithm (ports/hasher host)) :capabilities capabilities})
  (revision/empty-store host capabilities))

(defn replay
  "Rebuilds the in-memory store from log.edn alone, checking each logged
   transaction and revision id. Returns the store value."
  [b host]
  (let [{:keys [capabilities hash-alg]} (or (read-descriptor b) (fail "no store.edn" {}))
        alg (ports/algorithm (ports/hasher host))]
    (when (not= hash-alg alg) (fail "hash algorithm mismatch" {:store hash-alg :hasher alg}))
    (reduce (fn [store {:keys [id plan revision]}]
              (let [r (revision/apply-plan store plan)]
                (when (not= id (:id (:transaction r))) (fail "replayed transaction id differs from log" {:logged id :got (:id (:transaction r))}))
                (when (not= revision (:revision-id r)) (fail "replayed revision id differs from log" {:logged revision :got (:revision-id r) :tx id}))
                (:store r)))
            (revision/empty-store host capabilities)
            (read-log b))))

(defn open
  "replay + consistency of heads.edn with the replayed heads (when present)."
  [b host]
  (let [store (replay b host)
        hd (read-heads b)]
    (when (and hd (not= hd {:head (:head store) :heads (:heads store)}))
      (fail "heads.edn does not match the replayed log" {:file hd :replayed {:head (:head store) :heads (:heads store)}}))
    store))

;; ---------------------------------------------------------------- commit

(defn commit!
  "Applies `plan` to the in-memory `store` (loaded from backend `b`), then
   persists: new objects, the Transaction line, and the heads by CAS against
   the heads the caller loaded (:store/stale otherwise). Returns the apply result."
  [b store plan]
  (let [host (:host store)
        r (revision/apply-plan store plan)
        s' (:store r)
        new-ids (remove #(contains? (:objects store) %) (keys (:objects s')))
        expected {:head (:head store) :heads (:heads store)}]
    (doseq [id new-ids] (write-object! b id (serialize host (get-in s' [:objects id]))))
    (append-tx! b (:transaction r))
    (when-not (cas-heads! b expected {:head (:head s') :heads (:heads s')})
      (fail "heads moved since the store was loaded; reload and retry" {:type :store/stale :expected expected}))
    r))

;; ---------------------------------------------------------------- verify

(defn reachable
  "Ids of every object reachable from the heads of `store` (docs/v2/02 §3.1):
   the transitive closure over id references, acyclic because ids are hashes."
  [store]
  (let [objs (:objects store)]
    (loop [todo (vec (remove nil? (cons (:head store) (vals (:heads store))))) seen #{}]
      (if (empty? todo)
        seen
        (let [[id & more] todo]
          (if (or (nil? id) (seen id) (not (contains? objs id)))
            (recur more seen)
            (let [o (objs id)
                  refs (cond
                         (and (map? o) (:roots o) (:tx o))            (concat (vals (:roots o)) [(:trees o) (:edges o) (:tx o)] (:parents o))
                         (and (map? o) (:node o) (contains? o :children)) (cons (:node o) (mapcat identity (:children o)))
                         (and (vector? o) (every? vector? o))         (map second o)
                         (and (vector? o) (every? string? o))         o
                         (and (map? o) (:tree o) (:root o))           [(:root o)]
                         (and (map? o) (:type o) (:from o))           [(:from o) (:to o) (get-in o [:evidence :context])]
                         :else [])]
              (recur (into more (remove nil? refs)) (conj seen id)))))))))

(defn verify
  "Recomputes H(file) for every reachable object. Returns
   {:ok? bool :checked n :bad [{:id :reason (:missing | :hash-mismatch)}]}."
  [b store]
  (let [hasher (ports/hasher (:host store))
        ids (sort (reachable store))
        bad (for [id ids
                  :let [content (read-object b id)
                        reason (cond (nil? content) :missing
                                     (not= id (ports/digest-str hasher content)) :hash-mismatch)]
                  :when reason]
              {:id id :reason reason})]
    {:ok? (empty? bad) :checked (count ids) :bad (vec bad)}))
