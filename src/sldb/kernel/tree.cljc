(ns sldb.kernel.tree
  "Trees as indexes over the node pool (docs/v2/02 §3, §3.1).

   A tree value:
   {:tree      <ULID>                      ; nominal id
    :descriptor {:tree :kind :name :root}   ; a CAS object; see descriptor-id
    :children  {node-id [child-id ...]}      ; ownership, sibling order = vector order
    :parent    {child-id parent-id}
    :objects   {node-id tree-hash}           ; committed tree objects, shared between states
    :dirty     #{node-id ...}}               ; nodes whose tree object must be recomputed

   tree-object(n) = {:node n :children [[child tree-hash] ...]} in sibling order
   tree-hash(n)   = H(canonical-bytes tree-object(n)); merkle-root = tree-hash(root).
   Only ownership changes mark the dirty set; commit recomputes dirty objects in
   post-order and reuses every other object from the previous state."
  (:require [sldb.kernel.canon :as canon]
            [sldb.host.ulid :as ulid]))

(def kinds #{:document :section-index :taxonomy :syntax :context})

(defn- fail [why data]
  (throw (ex-info (str "tree: " why) (assoc data :type :tree/invalid))))

;; ---------------------------------------------------------------- descriptor

(defn descriptor-id [hasher descriptor]
  (canon/digest hasher descriptor))

;; ---------------------------------------------------------------- creation

(defn create
  "A new tree of `kind` rooted at node `root`, with every node dirty (docs/v2/02 §3.1).
   `tree-id` defaults to a fresh ULID; pass one explicitly for reproducible fixtures."
  ([hasher kind name root] (create hasher kind name root (ulid/ulid)))
  ([_hasher kind name root tree-id]
   (when-not (contains? kinds kind) (fail "unknown tree kind" {:kind kind}))
   (when-not (ulid/ulid? tree-id) (fail "tree id must be a ULID" {:tree tree-id}))
   {:tree tree-id
    :descriptor {:tree tree-id :kind kind :name name :root root}
    :children {root []}
    :parent {}
    :objects {}
    :dirty #{root}}))

(defn root [tree] (get-in tree [:descriptor :root]))

(defn nodes [tree] (set (keys (:children tree))))

(defn contains-node? [tree node] (contains? (:children tree) node))

(defn ancestors
  "Ancestors of `node` in this tree, nearest first."
  [tree node]
  (loop [n (get-in tree [:parent node]) acc []]
    (if (nil? n) acc (recur (get-in tree [:parent n]) (conj acc n)))))

(defn- mark-dirty [tree node]
  (update tree :dirty into (cons node (ancestors tree node))))

(defn- insert-at [v i x]
  (let [v (vec v)] (into (conj (subvec v 0 i) x) (subvec v i))))

(defn- remove-item [v x] (vec (remove #{x} v)))

;; ---------------------------------------------------------------- ownership ops

(defn add-child
  "Attaches `child` under `parent` at sibling position `order` (dense: 0..count)."
  [tree parent child order]
  (when-not (contains-node? tree parent) (fail "parent not in tree" {:parent parent}))
  (when (contains-node? tree child) (fail "node already in this tree (one parent per tree)" {:child child}))
  (let [siblings (get-in tree [:children parent])]
    (when-not (and (integer? order) (<= 0 order (count siblings))) (fail "order out of range" {:order order}))
    (-> tree
        (assoc-in [:children parent] (insert-at siblings order child))
        (assoc-in [:children child] [])
        (assoc-in [:parent child] parent)
        (mark-dirty parent)
        (update :dirty conj child))))

(defn- subtree-nodes [tree node]
  (tree-seq (fn [_] true) #(get-in tree [:children %]) node))

(defn remove-subtree
  "Detaches `node` and its subtree from the tree."
  [tree node]
  (when (= node (root tree)) (fail "cannot remove the root" {:node node}))
  (let [parent (get-in tree [:parent node])
        gone (set (subtree-nodes tree node))]
    (when-not parent (fail "node not in tree" {:node node}))
    (-> tree
        (update-in [:children parent] remove-item node)
        (update :children #(apply dissoc % gone))
        (update :parent #(apply dissoc % gone))
        (update :objects #(apply dissoc % gone))
        (update :dirty #(apply disj % gone))
        (mark-dirty parent))))

(defn move
  "Re-parents `node` (with its subtree) under `new-parent` at `order`."
  [tree node new-parent order]
  (when (= node (root tree)) (fail "cannot move the root" {:node node}))
  (when (some #{new-parent} (cons node (rest (subtree-nodes tree node)))) (fail "cycle" {:node node :parent new-parent}))
  (let [old-parent (get-in tree [:parent node])]
    (when-not old-parent (fail "node not in tree" {:node node}))
    (let [t (-> tree (update-in [:children old-parent] remove-item node) (mark-dirty old-parent))
          siblings (get-in t [:children new-parent])]
      (when-not (and (integer? order) (<= 0 order (count siblings))) (fail "order out of range" {:order order}))
      (-> t
          (assoc-in [:children new-parent] (insert-at siblings order node))
          (assoc-in [:parent node] new-parent)
          (mark-dirty new-parent)
          (update :dirty conj node)))))

(defn replace-node
  "`new` takes the exact position of `old` and inherits its children (§5.1 :replace, structural part)."
  [tree old new]
  (when-not (contains-node? tree old) (fail "old node not in tree" {:node old}))
  (when (contains-node? tree new) (fail "new node already in tree" {:node new}))
  (let [parent (get-in tree [:parent old])
        kids (get-in tree [:children old])
        t (-> tree
              (update :children dissoc old)
              (assoc-in [:children new] kids)
              (update :parent dissoc old)
              (update :objects dissoc old)
              (update :dirty disj old))
        t (reduce (fn [t k] (assoc-in t [:parent k] new)) t kids)]
    (if parent
      (-> t
          (update-in [:children parent] #(mapv (fn [c] (if (= c old) new c)) %))
          (assoc-in [:parent new] parent)
          (mark-dirty parent)
          (update :dirty conj new))
      (-> t
          (assoc-in [:descriptor :root] new)
          (update :dirty conj new)))))

;; ---------------------------------------------------------------- validation

(defn valid?
  "One parent per node, no cycles, root has no parent, every child listed once."
  [tree]
  (let [{:keys [children parent]} tree
        r (root tree)]
    (and (contains? children r)
         (nil? (parent r))
         (every? (fn [[p kids]] (and (apply distinct? (cons ::none kids))
                                      (every? #(= p (parent %)) kids)))
                 children)
         (every? (fn [[c p]] (some #{c} (children p))) parent)
         (= (count children) (count (subtree-nodes tree r))))))

;; ---------------------------------------------------------------- merkle

(defn tree-object [tree node]
  {:node node
   :children (mapv (fn [c] [c (get-in tree [:objects c])]) (get-in tree [:children node]))})

(defn- depth [tree node] (count (ancestors tree node)))

(defn commit
  "Recomputes the tree objects of dirty nodes only, deepest first (post-order),
   reusing every other object. Returns the tree with :objects complete and :dirty empty."
  [hasher tree]
  (let [order (sort-by #(- (depth tree %)) (:dirty tree))
        t (reduce (fn [t n]
                    (assoc-in t [:objects n] (canon/digest hasher (tree-object t n))))
                  tree order)]
    (assoc t :dirty #{})))

(defn merkle-root
  "Root tree-hash of a committed tree; throws if dirty."
  [tree]
  (when (seq (:dirty tree)) (fail "tree has dirty nodes; commit first" {:dirty (:dirty tree)}))
  (get-in tree [:objects (root tree)]))

(defn objects
  "All tree objects of a committed tree, keyed by node id."
  [tree]
  (into {} (map (fn [n] [n (tree-object tree n)])) (keys (:children tree))))
