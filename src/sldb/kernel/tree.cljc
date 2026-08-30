(ns sldb.kernel.tree
  "Trees as indexes over the node pool, as trees of POSITIONS (docs/v2/02 §3, §3.1).

   A node may occur at several positions of the same tree (all the items of a list
   are the same node; a paragraph can be repeated); the identity of a position is
   its `path`, the vector of sibling indices from the root (`[]` is the root).

   A tree value:
   {:tree       <ULID>                          ; nominal id
    :descriptor {:tree :kind :name :root}       ; a CAS object; see descriptor-id
    :root       Position}
   Position = {:node <id> :children [Position …] :hash <tree-hash | nil>}

   tree-object(pos) = {:node n :children [[child-node child-tree-hash] …]} in sibling order
   tree-hash(pos)   = H(canonical-bytes tree-object(pos)); merkle-root = tree-hash(root).
   A position whose :hash is nil is dirty; ownership changes set :hash nil along the
   path to the root, and `commit` recomputes only those (structural sharing: an
   untouched subtree keeps its hash, and identical subtrees share one object)."
  (:require [sldb.kernel.canon :as canon]
            [sldb.kernel.ports :as ports]
            [sldb.kernel.err :as err]))

(def kinds
  "Admitted tree kinds."
  #{:document :section-index :taxonomy :syntax :context})

(def ^:private ulid-alphabet "0123456789ABCDEFGHJKMNPQRSTVWXYZ")

(defn ulid?
  "True for a 26-character Crockford-base32 string (the nominal tree id shape)."
  [s]
  (and (string? s) (= 26 (count s)) (every? #(some #{%} ulid-alphabet) s)))

(defn path?
  "True for a position path: a vector of non-negative integers."
  [p] (and (vector? p) (every? #(and (integer? %) (<= 0 %)) p)))

(defn- fail [why data]
  (err/raise :tree/invalid (str "tree: " why) data))

;; ---------------------------------------------------------------- descriptor / creation

(defn descriptor-id
  "Content-addressed id of a tree descriptor {:tree :kind :name :root} (§3.1)."
  [host descriptor]
  (canon/digest host descriptor))

(defn- position [node] {:node node :children [] :hash nil})

(defn create
  "A new tree of `kind` rooted at node `root` (dirty). `tree-id` defaults to a fresh
   ULID from the host IdMinter; pass one explicitly for reproducible fixtures."
  ([host kind name root] (create host kind name root (ports/ulid (ports/ids host))))
  ([_host kind name root tree-id]
   (when-not (contains? kinds kind) (fail "unknown tree kind" {:kind kind}))
   (when-not (ulid? tree-id) (fail "tree id must be a ULID" {:tree tree-id}))
   (when-not (string? root) (fail "root must be a node id" {:root root}))
   {:tree tree-id
    :descriptor {:tree tree-id :kind kind :name name :root root}
    :root (position root)}))

;; ---------------------------------------------------------------- navigation

(defn- pos-path
  "Internal key path into the nested :root structure for a position path."
  [path] (into [:root] (mapcat (fn [i] [:children i]) path)))

(defn position-at
  "The Position at `path`, or nil."
  [tree path]
  (when (path? path) (get-in tree (pos-path path))))

(defn node-at
  "Node id at `path`, or nil."
  [tree path] (:node (position-at tree path)))

(defn children-at
  "Node ids of the children at `path`."
  [tree path] (mapv :node (:children (position-at tree path))))

(defn root
  "The root node id."
  [tree] (get-in tree [:descriptor :root]))

(defn positions
  "Seq of [path node] for every position, in document (pre-)order."
  [tree]
  (letfn [(walk [pos path]
            (cons [path (:node pos)]
                  (mapcat (fn [i c] (walk c (conj path i))) (range) (:children pos))))]
    (walk (:root tree) [])))

(defn nodes
  "Set of node ids in the tree."
  [tree] (set (map second (positions tree))))

(defn contains-node?
  "True when `node` occurs somewhere in the tree."
  [tree node] (contains? (nodes tree) node))

(defn paths-of
  "All paths where `node` occurs."
  [tree node] (vec (keep (fn [[p n]] (when (= n node) p)) (positions tree))))

(defn parent-path
  "Path of the parent position; nil for the root."
  [path] (when (seq path) (pop path)))

(defn ancestor-path?
  "True when `a` is a strict ancestor path of `b`."
  [a b] (and (< (count a) (count b)) (= a (subvec b 0 (count a)))))

;; ---------------------------------------------------------------- dirty marking

(defn- mark-dirty
  "Sets :hash nil at `path` and every ancestor."
  [tree path]
  (reduce (fn [t p] (assoc-in t (conj (pos-path p) :hash) nil))
          tree
          (map #(subvec path 0 %) (range (inc (count path))))))

(defn- require-position [tree path what]
  (or (position-at tree path) (fail (str what " not in tree") {:path path})))

(defn- insert-at [v i x] (into (conj (subvec v 0 i) x) (subvec v i)))
(defn- remove-at [v i] (into (subvec v 0 i) (subvec v (inc i))))

;; ---------------------------------------------------------------- ownership ops

(defn add-child
  "Attaches node `child` as a new position under `parent` (a path) at sibling
   position `order` (dense: 0..count)."
  [tree parent child order]
  (let [p (require-position tree parent "parent")
        n (count (:children p))]
    (when-not (string? child) (fail "child must be a node id" {:child child}))
    (when-not (and (integer? order) (<= 0 order n)) (fail "order out of range" {:order order :count n}))
    (-> tree
        (update-in (conj (pos-path parent) :children) insert-at order (position child))
        (mark-dirty parent))))

(defn detach
  "Removes the position at `path` with its subtree; returns the tree."
  [tree path]
  (when (empty? path) (fail "cannot detach the root" {}))
  (require-position tree path "position")
  (let [parent (parent-path path)]
    (-> tree
        (update-in (conj (pos-path parent) :children) remove-at (peek path))
        (mark-dirty parent))))

(defn move
  "Moves the subtree at `from` under `to-parent` at `order`; rejects moving a
   position into its own subtree. `order` refers to the parent's children AFTER
   the detach."
  [tree from to-parent order]
  (when (empty? from) (fail "cannot move the root" {}))
  (require-position tree from "position")
  (require-position tree to-parent "parent")
  (when (or (= from to-parent) (ancestor-path? from to-parent)) (fail "cycle" {:from from :to to-parent}))
  (let [sub (position-at tree from)
        t (detach tree from)
        ;; the target parent path may shift when `from` was an earlier sibling on the same level
        to-parent (let [fp (parent-path from)]
                    (if (and (ancestor-path? fp to-parent) (> (nth to-parent (count fp)) (peek from)))
                      (update to-parent (count fp) dec)
                      to-parent))
        p (require-position t to-parent "parent")
        n (count (:children p))]
    (when-not (and (integer? order) (<= 0 order n)) (fail "order out of range" {:order order :count n}))
    (-> t
        (update-in (conj (pos-path to-parent) :children) insert-at order (assoc sub :hash nil))
        (mark-dirty to-parent))))

(defn replace-node
  "`new` takes the position `path` (same order, same subtree); replacing the root
   re-roots the tree. Returns the tree."
  [tree path new]
  (require-position tree path "position")
  (when-not (string? new) (fail "new must be a node id" {:new new}))
  (cond-> (-> tree (assoc-in (conj (pos-path path) :node) new) (mark-dirty path))
    (empty? path) (assoc-in [:descriptor :root] new)))

;; ---------------------------------------------------------------- validation

(defn valid?
  "Every position holds a node id and a vector of children; the root matches the
   descriptor. (Acyclicity and dense sibling order hold by construction.)"
  [tree]
  (letfn [(ok? [pos] (and (string? (:node pos)) (vector? (:children pos)) (every? ok? (:children pos))))]
    (and (ok? (:root tree)) (= (root tree) (get-in tree [:root :node])))))

;; ---------------------------------------------------------------- merkle

(defn tree-object
  "{:node n :children [[child-node child-tree-hash] …]} of a committed position."
  [pos]
  {:node (:node pos) :children (mapv (fn [c] [(:node c) (:hash c)]) (:children pos))})

(defn- commit-pos [host pos]
  (if (:hash pos)
    pos
    (let [kids (mapv #(commit-pos host %) (:children pos))
          pos' (assoc pos :children kids)]
      (assoc pos' :hash (canon/digest host (tree-object pos'))))))

(defn commit
  "Recomputes the hashes of dirty positions only, post-order; the rest are reused."
  [host tree]
  (update tree :root #(commit-pos host %)))

(defn dirty?
  "True when some position still has no hash."
  [tree]
  (letfn [(d? [pos] (or (nil? (:hash pos)) (some d? (:children pos)))) ]
    (boolean (d? (:root tree)))))

(defn dirty-paths
  "Paths of the positions without hash."
  [tree]
  (letfn [(walk [pos path] (concat (when (nil? (:hash pos)) [path])
                                   (mapcat (fn [i c] (walk c (conj path i))) (range) (:children pos))))]
    (vec (walk (:root tree) []))))

(defn merkle-root
  "Root tree-hash of a committed tree; raises :tree/invalid if dirty."
  [tree]
  (when (dirty? tree) (fail "tree has dirty positions; commit first" {:dirty (dirty-paths tree)}))
  (get-in tree [:root :hash]))

(defn hash-at
  "Tree-hash of the committed position at `path`."
  [tree path] (:hash (position-at tree path)))

(defn objects
  "All tree objects of a committed tree keyed by tree-hash (identical subtrees share one)."
  [tree]
  (letfn [(walk [pos] (cons [(:hash pos) (tree-object pos)] (mapcat walk (:children pos))))]
    (into {} (walk (:root tree)))))

(defn from-objects
  "Rebuilds a committed :root Position from a root tree-hash and a lookup fn
   hash → tree-object (used by diff and store->ast on the CAS)."
  [root-hash lookup]
  (letfn [(build [h] (let [{:keys [node children]} (lookup h)]
                       {:node node :hash h :children (mapv (fn [[_ ch]] (build ch)) children)}))]
    (build root-hash)))
