(ns sldb.kernel.pool
  "In-memory pool of content-addressed nodes (docs/v2/02 §2; milestone 0).
   A pool is a plain map id → node. `put` is idempotent: the same node stored
   twice is the same entry, and a node whose id does not match its content is
   rejected."
  (:require [sldb.kernel.node :as node]))

(defn empty-pool [] {})

(defn put
  "Adds `n` (a node built with `sldb.kernel.node/make`) to `pool`."
  [hasher pool n]
  (let [expected (node/node-id hasher n)]
    (when (not= expected (:id n))
      (throw (ex-info "node id does not match its content"
                      {:type :pool/id-mismatch :id (:id n) :expected expected})))
    (if (contains? pool (:id n))
      pool
      (assoc pool (:id n) n))))

(defn get-node [pool id] (get pool id))

(defn has? [pool id] (contains? pool id))

(defn size [pool] (count pool))
