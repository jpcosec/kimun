(ns kimun.graph.analysis
  "Whole-graph questions on the `kimun.graph.model` value: paths,
   cycles, dependency order, components, centrality and similarity by shared
   targets. Every function takes the relations to walk and defaults to the
   authored ones (see `model/resolve-relations`). Reimplements, without
   networkx: shortest_path, all_simple_paths, simple_cycles, topological
   generations, weakly_connected_components, pagerank, betweenness_centrality
   and degree. Results are sorted so the output is deterministic."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [kimun.kernel.err :as err]
            [kimun.graph.model :as m]))

;; ---------------------------------------------------------------- paths

(defn- bfs-path
  [nbrs source target]
  (loop [queue [source] prev {source nil}]
    (cond
      (empty? queue) nil
      (contains? prev target) (vec (reverse (take-while some? (iterate prev target))))
      :else
      (let [[found prev'] (reduce (fn [[acc pv] n]
                                    (reduce (fn [[a p] x] (if (contains? p x) [a p] [(conj a x) (assoc p x n)]))
                                            [acc pv] (nbrs n)))
                                  [[] prev] queue)]
        (recur found prev')))))

(defn path-between
  "Shortest path `[source ... target]` or nil. Undirected unless `:directed`.
   `:relations` as in `model/resolve-relations`. Ties break on sorted ids."
  [g source target & {:keys [relations directed]}]
  (let [c (m/collapsed g relations)
        nbrs (if directed #(get-in c [:succ %]) #(m/undirected-neighbors c %))]
    (when (and (m/has-node? g source) (m/has-node? g target))
      (bfs-path nbrs source target))))

;; --------------------------------------------------------------- cycles

(defn- cycles-from
  [succ s]
  (letfn [(walk [path on]
            (mapcat (fn [n]
                      (cond (= n s) [path]
                            (and (pos? (compare n s)) (not (on n))) (walk (conj path n) (conj on n))))
                    (succ (peek path))))]
    (walk [s] #{s})))

(defn cycles
  "Simple cycles of the relation graph, each starting at its smallest id. Takes
   the first `:limit` (default 10) in sorted-start order, then sorts by length."
  [g & {:keys [relations limit] :or {limit 10}}]
  (let [c (m/collapsed g relations)]
    (->> (mapcat #(cycles-from (:succ c) %) (:nodes c))
         (take limit)
         (sort-by count)
         vec)))

;; ---------------------------------------------------------------- order

(defn layers
  "Dependency layers: first the nodes pointing at nothing, then those pointing
   only at earlier layers; each layer sorted. Raises `:graph/cycle` when the
   relation is not a DAG."
  [g & {:keys [relations]}]
  (let [c (m/collapsed g relations)]
    (loop [remaining (set (:nodes c)) out []]
      (if (empty? remaining)
        out
        (let [layer (vec (sort (filter #(empty? (set/intersection remaining (set (get-in c [:succ %])))) remaining)))]
          (when (empty? layer)
            (err/raise :graph/cycle
                       (str "Relation is not acyclic: " (let [cyc (first (cycles g :relations relations :limit 1))]
                                                          (m/relation-label (conj cyc (first cyc)))))
                       {:cycle (first (cycles g :relations relations :limit 1))}))
          (recur (reduce disj remaining layer) (conj out layer)))))))

;; ----------------------------------------------------------- components

(defn components
  "Weakly connected components of the view (nodes restricted to `:node-types`),
   each sorted, largest first, ties by first id."
  [g & {:keys [relations node-types]}]
  (let [c (m/collapsed g relations node-types)]
    (loop [todo (:nodes c) seen #{} out []]
      (if-let [s (first todo)]
        (if (seen s)
          (recur (rest todo) seen out)
          (let [grp (loop [stack [s] grp #{s}]
                      (if (empty? stack)
                        grp
                        (let [fresh (remove grp (m/undirected-neighbors c (peek stack)))]
                          (recur (into (pop stack) fresh) (into grp fresh)))))]
            (recur (rest todo) (into seen grp) (conj out (vec (sort grp))))))
        (vec (sort-by (juxt (comp - count) first) out))))))

(defn of-types
  "`scores` (id -> value) restricted to nodes of `node-types` (nil keeps all)."
  [g scores node-types]
  (if (nil? node-types)
    scores
    (let [wanted (set node-types)]
      (into {} (filter (fn [[id _]] (wanted (m/node-type g id)))) scores))))

(defn isolated
  "Sorted ids touched by no edge of the relation graph. Measured on the whole
   graph, then filtered by `:node-types`."
  [g & {:keys [relations node-types]}]
  (let [v (m/view g relations)
        touched (into #{} (mapcat (juxt :from :to)) (:edges v))]
    (vec (sort (keys (of-types g (into {} (comp (remove touched) (map #(vector % 0))) (m/node-ids v)) node-types))))))

;; ----------------------------------------------------------- centrality

(def centrality-kinds
  "The centrality kinds `central` accepts."
  ["pagerank" "betweenness" "degree" "in_degree" "out_degree"])

(defn- pagerank
  "networkx.pagerank: alpha 0.85, uniform personalisation and dangling nodes,
   edge weight = number of distinct relations; iterated to l1 error 1e-13."
  [c]
  (let [ids (:nodes c) n (count ids) alpha 0.85
        out-w (into {} (map (fn [i] [i (reduce + 0.0 (map #(get-in c [:weight [i %]]) (get-in c [:succ i])))])) ids)
        dangling (filter #(zero? (out-w %)) ids)]
    (loop [x (zipmap ids (repeat (/ 1.0 n))) iter 0]
      (let [dsum (reduce + 0.0 (map x dangling))
            base (+ (* (- 1.0 alpha) (/ 1.0 n)) (* alpha dsum (/ 1.0 n)))
            x' (into {} (map (fn [i]
                               [i (+ base (* alpha (reduce + 0.0 (map (fn [j] (* (x j) (/ (get-in c [:weight [j i]]) (out-w j))))
                                                                      (get-in c [:pred i])))))]))
                     ids)
            err (reduce + 0.0 (map #(Math/abs (- (x' %) (x %))) ids))]
        (if (or (< err 1e-13) (>= iter 1000)) x' (recur x' (inc iter)))))))

(defn- betweenness
  "networkx.betweenness_centrality (normalized, directed, unweighted): Brandes."
  [c]
  (let [ids (:nodes c) n (count ids)
        scale (when (> n 2) (/ 1.0 (* (dec n) (- n 2))))
        accumulate
        (fn [acc s]
          (let [{:keys [order sigma preds]}
                (loop [queue [s] order [] dist {s 0} sigma {s 1.0} preds {}]
                  (if (empty? queue)
                    {:order order :sigma sigma :preds preds}
                    (let [v (first queue)
                          [q d sg pr] (reduce (fn [[q d sg pr] w]
                                                (let [new? (not (contains? d w))
                                                      d (if new? (assoc d w (inc (d v))) d)
                                                      q (if new? (conj q w) q)]
                                                  (if (= (d w) (inc (d v)))
                                                    [q d (update sg w (fnil + 0.0) (sigma v)) (update pr w (fnil conj []) v)]
                                                    [q d sg pr])))
                                              [(subvec queue 1) dist sigma preds] (get-in c [:succ v]))]
                      (recur q (conj order v) d sg pr))))
                delta (reduce (fn [delta w]
                                (reduce (fn [dl v]
                                          (update dl v (fnil + 0.0) (* (/ (sigma v) (sigma w)) (+ 1.0 (get delta w 0.0)))))
                                        delta (get preds w)))
                              {} (reverse order))]
            (reduce (fn [a w] (if (= w s) a (update a w + (get delta w 0.0)))) acc order)))
        raw (reduce accumulate (zipmap ids (repeat 0.0)) ids)]
    (if scale (update-vals raw #(* % scale)) raw)))

(defn- scores
  [c kind]
  (case kind
    "pagerank" (if (seq (:weight c)) (pagerank c) (zipmap (:nodes c) (repeat 0.0)))
    "betweenness" (betweenness c)
    "degree" (into {} (map (fn [i] [i (double (+ (count (get-in c [:succ i])) (count (get-in c [:pred i]))))])) (:nodes c))
    "in_degree" (into {} (map (fn [i] [i (double (count (get-in c [:pred i])))])) (:nodes c))
    "out_degree" (into {} (map (fn [i] [i (double (count (get-in c [:succ i])))])) (:nodes c))))

(defn central
  "Top `:limit` (default 20) `[id score]` pairs by centrality `:kind` (default
   \"pagerank\"; one of `centrality-kinds`), ties by id. Computed on the whole
   graph and filtered by `:node-types` afterwards. Raises
   `:graph/unknown-centrality` on a bad kind."
  [g & {:keys [kind relations node-types limit] :or {kind "pagerank" limit 20}}]
  (when-not (some #{kind} centrality-kinds)
    (err/raise :graph/unknown-centrality
               (str "Unknown centrality " (pr-str kind) "; expected one of "
                    (str/join ", " centrality-kinds) ".")
               {:kind kind}))
  (let [c (m/collapsed g relations)]
    (->> (of-types g (scores c kind) node-types)
         (sort-by (fn [[id s]] [(- s) id]))
         (take limit)
         (mapv (fn [[id s]] [id (double s)])))))

;; ----------------------------------------------------------- similarity

(defn similar-to
  "Top `:limit` (default 10) `[id shared-count]` pairs: nodes sharing targets
   with `id` along `:relations` (default [\"tagged_as\"]), ties by id.
   `:node-types` filters the answer, not the graph."
  [g id & {:keys [relations limit node-types] :or {relations ["tagged_as"] limit 10}}]
  (let [v (m/view g relations)]
    (if-not (m/has-node? v id)
      []
      (let [adj (m/adjacency v)
            counts (frequencies (for [t (set (map :to (get-in adj [:out id])))
                                      o (set (map :from (get-in adj [:in t])))
                                      :when (not= o id)]
                                  o))]
        (->> (of-types g counts node-types)
             (sort-by (fn [[n k]] [(- k) n]))
             (take limit)
             (mapv (fn [[n k]] [n (long k)])))))))
