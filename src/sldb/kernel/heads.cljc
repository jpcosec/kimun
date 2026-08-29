(ns sldb.kernel.heads
  "Heads and compare-and-swap over an in-memory store (docs/v2/02 §3.1, §5.2).

   `heads` is {tree-id revision-id}: the last revision that touched each tree.
   A commit moves, in one step, the entries of every tree the plan touched.
   `commit!` wraps sldb.kernel.revision/apply-plan with the CAS discipline on a
   mutable reference so concurrent writers observe a ConflictSet instead of a
   lost update."
  (:require [sldb.kernel.revision :as revision]))

(defn heads [store] (:heads store))

(defn head [store] (:head store))

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
   semantics. On success swaps the new store in and returns the apply result.
   On rejection or conflict rethrows the ex-info (:plan/rejected | :plan/conflict)."
  [store-ref plan]
  (loop [attempt 0]
    (let [store @store-ref
          result (revision/apply-plan store plan)]
      (if (compare-and-set! store-ref store (:store result))
        result
        (if (< attempt 3)
          (recur (inc attempt))
          (throw (ex-info "conflict" {:type :plan/conflict
                                      :conflict-set {:base (:base plan) :head (:head @store-ref) :plan plan
                                                     :conflicts [{:kind :store-moved}]}})))))))

(defn conflict-set
  "The ConflictSet carried by a :plan/conflict exception, or nil."
  [e]
  (when (= :plan/conflict (:type (ex-data e))) (:conflict-set (ex-data e))))
