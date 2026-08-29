(ns sldb.kernel.heads
  "Heads and compare-and-swap over an in-memory store (docs/v2/02 §3.1, §5.2).

   `heads` is {tree-id revision-id}: the last revision that touched each tree.
   A commit moves, in one step, the entries of every tree the plan touched.
   `commit!` wraps sldb.kernel.revision/apply-plan with the CAS discipline on a
   mutable reference so concurrent writers observe a ConflictSet instead of a
   lost update."
  (:require [sldb.kernel.revision :as revision]))

(def max-attempts
  "Compare-and-set retries of commit! before giving up with :store-moved."
  32)

(defn heads
  "{tree-id revision-id}: last revision that touched each tree."
  [store] (:heads store))

(defn head
  "Latest revision id of the store."
  [store] (:head store))

(defn cas
  "Pure CAS: returns store' when, for every tree in `expected` (a {tree-id rev-id}
   map), the current head entry equals the expected value; otherwise returns
   {:conflict {...}} describing the stale entries."
  [store expected new-heads new-head]
  (let [stale (into {} (filter (fn [[tid rev]] (not= rev (get-in store [:heads tid]))) expected))]
    (if (seq stale)
      {:conflict {:stale stale :heads (:heads store)}}
      (-> store (update :heads merge new-heads) (assoc :head new-head)))))

(defn commit!
  "Applies `plan` to the store held in atom `store-ref` with all-or-nothing
   semantics. `plan` may be a plan map or a function `store → plan`, so that a
   retry after a lost compare-and-set rebuilds the plan against the store it
   actually sees (typically to set :base to the current head). On success swaps
   the new store in and returns the apply result. On rejection or conflict
   rethrows the ex-info (:plan/rejected | :plan/conflict)."
  [store-ref plan]
  (loop [attempt 0]
    (let [store @store-ref
          built (if (fn? plan) (plan store) plan)
          result (revision/apply-plan store built)]
      (if (compare-and-set! store-ref store (:store result))
        result
        (if (< attempt max-attempts)
          (recur (inc attempt))
          (throw (ex-info "conflict" {:type :plan/conflict
                                      :conflict-set {:base (:base built) :head (:head @store-ref) :plan built
                                                     :conflicts [{:kind :store-moved :attempts attempt}]}})))))))

(defn conflict-set
  "The ConflictSet carried by a :plan/conflict exception, or nil."
  [e]
  (when (= :plan/conflict (:type (ex-data e))) (:conflict-set (ex-data e))))
