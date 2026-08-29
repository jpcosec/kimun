(ns sldb.kernel.pool
  "In-memory pool of content-addressed nodes (docs/v2/02 §2; milestone 0).
   A pool is a plain map id → node. `put` is idempotent: the same node stored
   twice is the same entry, and a node whose id does not match its content is
   rejected (invariant 1)."
  (:require [sldb.kernel.node :as node]
            [sldb.kernel.err :as err]))

(defn empty-pool
  "A pool with no nodes."
  [] {})

(defn put
  "Adds `n` (a node built with `sldb.kernel.node/make`) to `pool`; raises
   :pool/id-mismatch when the id does not hash the content."
  [host pool n]
  (let [expected (node/node-id host n)]
    (when (not= expected (:id n))
      (err/raise :pool/id-mismatch "node id does not match its content" {:id (:id n) :expected expected}))
    (if (contains? pool (:id n))
      pool
      (assoc pool (:id n) n))))

(defn get-node
  "The node with `id`, or nil."
  [pool id] (get pool id))

(defn has?
  "True when the pool holds `id`."
  [pool id] (contains? pool id))

(defn size
  "Number of nodes in the pool."
  [pool] (count pool))
