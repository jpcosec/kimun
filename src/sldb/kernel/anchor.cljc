(ns sldb.kernel.anchor
  "Anchor states and anchor queries (docs/v2/02 §6.2, §6.3).

   An anchor is an active edge of a revision whose type is one of
   `anchor-types`; `ownership` is structure and `supersedes` is the succession
   record, so neither is one. A state is derived — recomputed from the revision
   plus the pool, never stored (invariant 18) — and the state of an edge is the
   worst of its two endpoints under `intact < superseded < orphan`."
  (:require [sldb.kernel.err :as err]
            [sldb.kernel.ports :as ports]
            [sldb.kernel.revision :as revision]
            [sldb.kernel.tree :as tree]))

(def anchor-types
  "Edge types that are anchors (docs/v2/02 §6.2)."
  #{:reference :binding :projection :semantic :derived})

(def state-order
  "Severity of each anchor state; the state of an edge is the worst of its two
   endpoints (docs/v2/02 §6.2)."
  {:intact 0 :superseded 1 :orphan 2})

;; ---------------------------------------------------------------- revision context

(defn- revision-of [store rev-id]
  (or (revision/revision store rev-id)
      (err/raise :anchor/unknown-revision (str "unknown revision " rev-id) {:revision rev-id})))

(defn placements
  "Inverse index node → positions for a revision (docs/v2/02 §6.3):
   {node-id [[tree-id path] …]}, trees by ascending id and, within a tree,
   paths in document order."
  [store rev-id]
  (reduce (fn [acc tid]
            (if-let [pos (revision/tree-at store rev-id tid)]
              (reduce (fn [a [path node]] (update a node (fnil conj []) [tid path]))
                      acc
                      (tree/positions {:root pos}))
              acc))
          {}
          (sort (keys (:roots (revision-of store rev-id))))))

(defn- active-edges
  "[[edge-id edge] …] of the revision, in edge-set order (ascending id)."
  [store rev-id]
  (vec (for [eid (revision/get-object store (:edges (revision-of store rev-id)))
             :let [e (revision/get-object store eid)]
             :when (map? e)]
         [eid e])))

(defn- successors-index
  "{replaced #{successor …}} over the active :supersedes edges."
  [edges]
  (reduce (fn [m [_ e]] (if (= :supersedes (:type e))
                          (update m (:to e) (fnil conj #{}) (:from e))
                          m))
          {} edges))

(defn- context
  "Everything the queries of a single revision share."
  [store rev-id]
  (let [edges (active-edges store rev-id)]
    {:store store :revision rev-id :edges edges
     :placements (placements store rev-id)
     :successors (successors-index edges)}))

;; ---------------------------------------------------------------- resolution (§6.2)

(defn- resolves?
  "Whether endpoint `id` still has ground in the revision (docs/v2/02 §6.2)."
  [c id]
  (let [n (revision/get-object (:store c) id)]
    (cond
      (nil? n) false
      (= :span (:kind n))
      (let [{:keys [leaf range]} (:content n)
            lf (revision/get-object (:store c) leaf)]
        (and (resolves? c leaf)
             (map? lf) (= :text (:kind lf))
             (<= (second range)
                 (count (ports/graphemes (ports/segmenter (:host (:store c)))
                                         (get-in lf [:content :text]))))))
      (= :sign (:class n)) (contains? (:placements c) id)
      :else true)))

(defn- follow
  "Direct successors of `id` and the last node reached while every step has
   exactly one successor (docs/v2/02 §6.2). A cycle cuts the chain."
  [c id]
  (let [direct (vec (sort (get (:successors c) id)))]
    (if (empty? direct)
      {:successors [] :latest nil :ambiguous? false}
      (loop [cur id seen #{id}]
        (let [s (vec (sort (get (:successors c) cur)))]
          (cond
            (empty? s)                 {:successors direct :latest cur :ambiguous? false}
            (< 1 (count s))            {:successors direct :latest nil :ambiguous? true}
            (contains? seen (first s)) {:successors direct :latest cur :ambiguous? false}
            :else (recur (first s) (conj seen (first s)))))))))

(defn- endpoint-state* [c id]
  (let [{:keys [successors latest ambiguous?]} (follow c id)]
    {:node id
     :state (cond (seq successors) :superseded
                  (resolves? c id) :intact
                  :else :orphan)
     :successors successors
     :latest latest
     :ambiguous? ambiguous?
     :positions (vec (get (:placements c) id []))}))

(defn- state* [c eid e]
  (let [from (endpoint-state* c (:from e))
        to (endpoint-state* c (:to e))]
    {:edge eid :type (:type e)
     :state (max-key state-order (:state from) (:state to))
     :from from :to to}))

(defn- anchors-of [c]
  (filter (fn [[_ e]] (contains? anchor-types (:type e))) (:edges c)))

;; ---------------------------------------------------------------- queries (§6.3)

(defn endpoint-state
  "State of one endpoint node in a revision (docs/v2/02 §6.2, §6.3)."
  [store rev-id node-id]
  (endpoint-state* (context store rev-id) node-id))

(defn state
  "State of one active anchor edge of a revision. Raises :anchor/not-an-anchor
   when `edge-id` is not an active edge of the revision or is not an anchor."
  [store rev-id edge-id]
  (let [c (context store rev-id)
        e (some (fn [[id x]] (when (= id edge-id) x)) (:edges c))]
    (when-not e
      (err/raise :anchor/not-an-anchor (str "not an active edge of the revision: " edge-id)
                 {:edge edge-id :revision rev-id}))
    (when-not (contains? anchor-types (:type e))
      (err/raise :anchor/not-an-anchor (str "edge type " (:type e) " is not an anchor")
                 {:edge edge-id :type (:type e)}))
    (state* c edge-id e)))

(defn states
  "Every active anchor of the revision, ordered by edge id (docs/v2/02 §6.3)."
  [store rev-id]
  (let [c (context store rev-id)]
    (mapv (fn [[eid e]] (state* c eid e)) (anchors-of c))))

(defn anchored-in
  "What is anchored in what, for one node (docs/v2/02 §6.3): its positions and
   the anchors where it is the :from and where it is the :to, by edge id. An
   edge whose two endpoints are the same node appears in both lists."
  [store rev-id node-id]
  (let [c (context store rev-id)
        as (anchors-of c)]
    {:node node-id
     :positions (vec (get (:placements c) node-id []))
     :as-from (vec (for [[eid e] as :when (= node-id (:from e))] (state* c eid e)))
     :as-to (vec (for [[eid e] as :when (= node-id (:to e))] (state* c eid e)))}))

(defn report
  "Anchor summary of a revision (docs/v2/02 §6.3); the three states and the five
   anchor types are always present, with zeros."
  [store rev-id]
  (let [ss (states store rev-id)
        zero {:intact 0 :superseded 0 :orphan 0}]
    {:revision rev-id
     :anchors (count ss)
     :by-state (reduce (fn [m s] (update m (:state s) inc)) zero ss)
     :by-type (reduce (fn [m s] (update-in m [(:type s) (:state s)] inc))
                      (into {} (map (fn [t] [t zero])) anchor-types)
                      ss)
     :orphans (vec (sort (map :edge (filter #(= :orphan (:state %)) ss))))}))

(defn diff
  "Anchors added, removed and changed between two revisions (docs/v2/02 §6.3)."
  [store r1 r2]
  (let [a (into {} (map (juxt :edge identity)) (states store r1))
        b (into {} (map (juxt :edge identity)) (states store r2))
        in-a? (set (keys a)) in-b? (set (keys b))]
    {:added (mapv b (sort (remove in-a? (keys b))))
     :removed (vec (sort (remove in-b? (keys a))))
     :changed (vec (for [eid (sort (filter in-a? (keys b)))
                         :when (not= (:state (a eid)) (:state (b eid)))]
                     {:edge eid :type (:type (b eid))
                      :before (:state (a eid)) :after (:state (b eid))}))}))
