(ns sldb.kernel.store
  "Persistence protocol and the host-independent parts of open/replay/verify
   (docs/v2/02 §8.1, milestone 3).

   Layout (files-only backend, see sldb.host.fs-store):
     <dir>/store.edn      {:format-version 1 :hash-alg :sha-256 :capabilities {...}}
     <dir>/objects/<id>   canonical EDN text of the hashed form of the object; H(file) == id
     <dir>/log.edn        one Transaction map per line, append-only
     <dir>/heads.edn      {:head rev-id :heads {tree-id rev-id}}, written atomically

   `open` never trusts objects/ or heads.edn for state: it replays log.edn through
   sldb.kernel.revision/apply-plan and checks every revision id against the log.
   `verify` walks the objects reachable from the heads and recomputes their hashes."
  (:require [sldb.kernel.canon :as canon]
            [sldb.kernel.node :as node]
            [sldb.kernel.edge :as edge]
            [sldb.kernel.revision :as revision]
            [sldb.host.hash]))

(defprotocol Backend
  (read-descriptor [b])
  (write-descriptor! [b descriptor])
  (read-object [b id] "String content of objects/<id>, or nil.")
  (write-object! [b id ^String content])
  (object-ids [b])
  (append-tx! [b tx])
  (read-log [b] "Vector of Transaction maps in order.")
  (read-heads [b] "{:head :heads} or nil.")
  (cas-heads! [b expected new] "Atomically replaces heads when the current value equals `expected`; returns true/false."))

;; ---------------------------------------------------------------- hashed forms

(defn hashed-form
  "The value whose canonical bytes hash to the object's id."
  [obj]
  (cond
    (and (map? obj) (:class obj) (:kind obj) (contains? obj :content)) (node/identity-form obj)
    (and (map? obj) (:type obj) (:from obj) (:to obj))                    (edge/identity-form obj)
    :else obj))

(defn serialize [obj] (canon/canon-str (hashed-form obj)))

(defn- fail [why data] (throw (ex-info (str "store: " why) (merge {:type :store/error} data))))

;; ---------------------------------------------------------------- init / open

(defn init!
  "Creates a new store in backend `b` with the given capabilities."
  [b hasher capabilities]
  (when (read-descriptor b) (fail "store already initialised" {}))
  (write-descriptor! b {:format-version 1 :hash-alg (sldb.host.hash/algorithm hasher) :capabilities capabilities})
  (revision/empty-store hasher capabilities))

(defn replay
  "Rebuilds the in-memory store from the log alone, checking each logged
   revision id. Returns the store value."
  [b hasher]
  (let [{:keys [capabilities hash-alg]} (or (read-descriptor b) (fail "no store.edn" {}))]
    (when (not= hash-alg (sldb.host.hash/algorithm hasher)) (fail "hash algorithm mismatch" {:store hash-alg :hasher (sldb.host.hash/algorithm hasher)}))
    (reduce (fn [store {:keys [id plan revision] :as tx}]
              (let [r (revision/apply-plan store plan)]
                (when (not= id (:id (:transaction r))) (fail "replayed transaction id differs from log" {:logged id :got (:id (:transaction r))}))
                (when (not= revision (:revision-id r)) (fail "replayed revision id differs from log" {:logged revision :got (:revision-id r) :tx id}))
                (:store r)))
            (revision/empty-store hasher capabilities)
            (read-log b))))

(defn open
  "replay + consistency of heads.edn with the replayed heads (when present)."
  [b hasher]
  (let [store (replay b hasher)
        hd (read-heads b)]
    (when (and hd (not= hd {:head (:head store) :heads (:heads store)}))
      (fail "heads.edn does not match the replayed log" {:file hd :replayed {:head (:head store) :heads (:heads store)}}))
    store))

;; ---------------------------------------------------------------- commit

(defn commit!
  "Applies `plan` to the in-memory `store` (loaded from backend `b`), then
   persists: new objects, the Transaction line, and the heads by CAS against
   the heads the caller loaded. Returns the apply result with the new store."
  [b store plan]
  (let [r (revision/apply-plan store plan)
        s' (:store r)
        new-ids (remove #(contains? (:objects store) %) (keys (:objects s')))
        expected {:head (:head store) :heads (:heads store)}]
    (doseq [id new-ids] (write-object! b id (serialize (get-in s' [:objects id]))))
    (append-tx! b (:transaction r))
    (when-not (cas-heads! b expected {:head (:head s') :heads (:heads s')})
      (fail "heads moved since the store was loaded; reload and retry" {:type :store/stale :expected expected}))
    r))

;; ---------------------------------------------------------------- verify

(defn reachable
  "Ids of every object reachable from the heads of `store` (docs/v2/02 §3.1)."
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
                         ;; revision
                         (and (map? o) (:roots o) (:tx o))
                         (concat (vals (:roots o)) [(:trees o) (:edges o) (:tx o)] (:parents o))
                         ;; tree object
                         (and (map? o) (:node o) (contains? o :children))
                         (cons (:node o) (mapcat identity (:children o)))
                         ;; tree-set [[tree-id descriptor-id] ...]
                         (and (vector? o) (every? vector? o)) (map second o)
                         ;; edge-set [edge-id ...]
                         (and (vector? o) (every? string? o)) o
                         ;; descriptor
                         (and (map? o) (:tree o) (:root o)) [(:root o)]
                         ;; edge
                         (and (map? o) (:type o) (:from o)) [(:from o) (:to o) (get-in o [:evidence :context])]
                         :else [])]
              (recur (into more (remove nil? refs)) (conj seen id)))))))))

(defn verify
  "Recomputes H(file) for every reachable object. Returns
   {:ok? bool :checked n :bad [{:id :reason}]}."
  [b hasher store]
  (let [bad (for [id (sort (reachable store))
                  :let [content (read-object b id)
                        reason (cond (nil? content) :missing
                                     (not= id (sldb.host.hash/hash-bytes hasher (sldb.host.hash/utf8-bytes content))) :hash-mismatch)]
                  :when reason]
              {:id id :reason reason})]
    {:ok? (empty? bad) :checked (count (reachable store)) :bad (vec bad)}))
