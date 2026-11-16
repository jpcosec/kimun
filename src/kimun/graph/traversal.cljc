(ns kimun.graph.traversal
  "Neighbourhood walks over the edge list within a depth, with the direction
   and relation-type filters v1 supports. All results are sorted or sets,
   never in edge-list order."
  (:require [kimun.graph.model :as m]))

(defn- step
  [adj id direction types]
  (let [ok? (fn [e] (or (empty? types) (contains? types (:type e))))
        outs (when (#{"outgoing" "both"} direction) (map :to (filter ok? (get-in adj [:out id]))))
        ins (when (#{"incoming" "both"} direction) (map :from (filter ok? (get-in adj [:in id]))))]
    (set (concat outs ins))))

(defn neighborhood-by-direction
  "Nodes within `depth` hops of the `starting` ids following `direction`
   (\"outgoing\", \"incoming\" or \"both\"); the seeds are not in the result.
   `:types` restricts the relation types followed (empty = any)."
  [g starting depth direction & {:keys [types]}]
  (let [adj (m/adjacency g)
        seeds (set starting)
        types (set types)]
    (loop [queue (mapv #(vector % 0) (sort seeds)) visited #{}]
      (if (empty? queue)
        visited
        (let [[[id level] & more] (seq queue)]
          (if (>= level depth)
            (recur (vec more) visited)
            (let [fresh (remove #(or (visited %) (seeds %)) (sort (step adj id direction types)))]
              (recur (into (vec more) (map #(vector % (inc level))) fresh)
                     (into visited fresh)))))))))

