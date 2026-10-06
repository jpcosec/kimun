(ns kimun.surface.graph.traversal
  "Walks over the edge list: one-relation steps (targets, sources, roots,
   descendants, shared-target neighbours) and neighbourhood within a depth,
   with the direction and relation-type filters v1 supports. All results are
   sorted or sets, never in edge-list order."
  (:require [clojure.string :as str]
            [kimun.surface.graph.model :as m]))

(defn targets
  "Sorted distinct targets of `id` along relation `rel`."
  [g id rel]
  (vec (sort (distinct (map :to (m/edges-from g id rel))))))

(defn sources
  "Sorted distinct sources pointing at `id` along relation `rel`."
  [g id rel]
  (vec (sort (distinct (map :from (m/edges-to g id rel))))))

(defn roots
  "Ids (sorted) of nodes of `node-type` with no outgoing `rel` edge."
  [g node-type rel]
  (vec (filter #(empty? (m/edges-from g % rel))
               (filter #(= node-type (m/node-type g %)) (m/node-ids g)))))

(defn children
  "Nodes that point at `id` along `rel` (alias of `sources`)."
  [g id rel]
  (sources g id rel))

(defn parent
  "The first (sorted) target of `id` along `rel`, or nil."
  [g id rel]
  (first (targets g id rel)))

(defn descendants
  "Everything that reaches `id` through `rel` edges backwards, within `depth`
   levels (nil = unbounded), sorted; `id` itself is excluded."
  ([g id rel] (descendants g id rel nil))
  ([g id rel depth]
   (loop [frontier [id] seen #{} level 0]
     (if (or (empty? frontier) (and depth (>= level depth)))
       (vec (sort seen))
       (let [nxt (vec (distinct (for [n frontier c (sources g n rel)
                                      :when (and (not (seen c)) (not= c id))]
                                  c)))]
         (recur nxt (into seen nxt) (inc level)))))))

(defn kind
  "The `<kind>` of an `sldb://<kind>/...` id, or nil."
  [id]
  (when (str/starts-with? id "sldb://")
    (let [rest* (subs id 7)]
      (when (str/includes? rest* "/") (first (str/split rest* #"/" 2))))))

(defn bare
  "The id without its `sldb://<kind>/` prefix; others pass through."
  [id]
  (if (str/starts-with? id "sldb://")
    (let [rest* (subs id 7)]
      (if (str/includes? rest* "/") (second (str/split rest* #"/" 2)) rest*))
    id))

(defn neighbors-via
  "Nodes sharing a target with `id`: out along `out-rel`, back along `in-rel`
   (default `out-rel`). Targets whose bare id starts with an `:exclude-prefixes`
   entry are skipped; `:same-kind` (default true) keeps ids of `id`'s kind."
  [g id out-rel & {:keys [in-rel exclude-prefixes same-kind] :or {same-kind true}}]
  (let [back (or in-rel out-rel)
        found (-> (set (for [t (targets g id out-rel)
                             :when (not-any? #(str/starts-with? (bare t) %) exclude-prefixes)
                             s (sources g t back)]
                         s))
                  (disj id))]
    (vec (sort (if same-kind (filter #(= (kind %) (kind id)) found) found)))))

(defn edge-between
  "The first edge `source -[rel]-> target`, or nil."
  [g source target rel]
  (first (filter #(= target (:to %)) (m/edges-from g source rel))))

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

(defn neighborhood
  "The seeds plus every node within `depth` hops in either direction."
  [g starting depth & opts]
  (into (set starting) (apply neighborhood-by-direction g starting depth "both" opts)))
