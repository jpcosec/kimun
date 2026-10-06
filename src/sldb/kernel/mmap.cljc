(ns sldb.kernel.mmap
  "Persistent Merkle map: a history-independent hash trie whose nodes are plain
   EDN values stored in the CAS like any other object (id = `canon/digest` of
   the node). A set is a map to `true`.

   Node objects (the only two shapes; `node?` tells them from every other object):

     leaf    {:mmap :leaf   :entries [[k v] ...]}            ; entries sorted by path
     branch  {:mmap :branch :count n :children {slice id}}   ; n = entries below, > leaf-capacity

   Empty map: an ordinary object, the leaf with no entries (`empty-leaf`); its id
   is `(:root (empty host))`. A root is therefore ALWAYS the id of a loadable
   node object, never nil — whoever creates a store must write `(:new (empty host))`.

   Key path: `(path host k)`. A key that is a string of exactly 64 lower-case hex
   characters (node ids, edge ids, any sha-256 object id) is its own path; every
   other key (tree ULIDs, keywords, vectors…) takes the path `canon/digest` of the
   key. A branch at depth d (root = 0) indexes its children by the path slice
   [d*nibbles-per-level, (d+1)*nibbles-per-level), a string. Two different keys with
   the same path raise :mmap/path-collision.

   Canonical shape (what makes the root id a function of the contents only): a
   subtree holding <= `leaf-capacity` entries is ONE leaf; a subtree holding more
   is a branch whose children obey the same rule one level down; a branch never
   has an empty child. Insertion splits a leaf that overflows, removal collapses
   a branch that falls to `leaf-capacity` entries back into a leaf.

   Keys and values are any canonical value (`sldb.kernel.canon`); they are stored
   normalized (`canon/normalize`). The value `removal` is reserved. `load` is a
   function id -> node object (or nil when absent, which raises
   :mmap/missing-node). Nothing here does I/O: updates return the new node objects
   explicitly as `{:root id :new {id node}}`.

   `leaf-capacity` and `nibbles-per-level` are part of the on-disk format."
  (:refer-clojure :exclude [empty keys count])
  (:require [sldb.kernel.canon :as canon]
            [sldb.kernel.err :as err]))

(def ^:private cnt clojure.core/count)

(def leaf-capacity
  "B: the largest number of entries a leaf may hold; a subtree with more is a
   branch. Part of the on-disk format (changing it changes every root id).
   16 was chosen by measurement over {8 16 32 64} x fan-out {16 256} on the
   shapes of a real store (set of edge ids, tree-id -> [id id], node-id ->
   edge ids): with fan-out 16 it writes the fewest canonical bytes per commit
   without more files than B = 32, and every node stays under one 4 KB block."
  16)

(def nibbles-per-level
  "Hex characters of the key path consumed by each branch level: 1 = fan-out 16.
   Part of the on-disk format. Fan-out 256 was measured and rejected: every
   commit rewrites an 18 KB root branch per map."
  1)

(def removal
  "Sentinel value for `update-many`: `{k removal}` removes key `k`."
  ::remove)

(def empty-leaf
  "The node object of the empty map."
  {:mmap :leaf :entries []})

;; ---------------------------------------------------------------- node shapes

(defn leaf?
  "True when `obj` is an mmap leaf node object."
  [obj] (and (map? obj) (= :leaf (:mmap obj)) (vector? (:entries obj))))

(defn branch?
  "True when `obj` is an mmap branch node object."
  [obj] (and (map? obj) (= :branch (:mmap obj)) (map? (:children obj))))

(defn node?
  "True when `obj` is an mmap node object (leaf or branch). No node, edge,
   tree object, descriptor or Revision carries the key :mmap."
  [obj] (or (leaf? obj) (branch? obj)))

(defn child-ids
  "Ids of the child nodes of mmap node `obj`, in slice order: empty for a leaf.
   These are the only CAS references an mmap node holds by itself; ids kept as
   keys or values of the entries are the business of whoever owns the map."
  [obj]
  (if (branch? obj) (mapv val (sort-by key (:children obj))) []))

(defn node-count
  "Number of entries at or below mmap node `obj`, without loading anything."
  [obj]
  (if (branch? obj) (:count obj) (cnt (:entries obj))))

;; ---------------------------------------------------------------- paths

(defn- fail [type why data] (err/raise type (str "mmap: " why) data))

(defn- hex-id? [k] (and (string? k) (some? (re-matches #"[0-9a-f]{64}" k))))

(defn- norm-key [host k] (if (hex-id? k) k (canon/normalize host k)))

(defn path
  "Key path of `k`: `k` itself when it is a string of exactly 64 lower-case hex
   characters, otherwise `(canon/digest host k)`. Entries are ordered by path."
  [host k]
  (if (hex-id? k) k (canon/digest host k)))

(defn- slice [p depth]
  (let [s (* depth nibbles-per-level) e (+ s nibbles-per-level)]
    (when (> e (cnt p)) (fail :mmap/path-exhausted "more than leaf-capacity keys share a whole path" {:path p}))
    (subs p s e)))

(defn- fetch [load id]
  (let [o (load id)]
    (when-not (node? o) (fail :mmap/missing-node (str "no mmap node at " id) {:id id}))
    o))

;; ---------------------------------------------------------------- reads

(defn- obj-entries [load obj]
  (if (leaf? obj)
    (seq (:entries obj))
    (mapcat (fn [id] (lazy-seq (obj-entries load (fetch load id)))) (child-ids obj))))

(defn entries
  "Lazy seq of the [k v] entries of the map rooted at `root`, in path order
   (for 64-hex id keys: ascending id). Loads nodes as the seq is consumed."
  [load root]
  (lazy-seq (obj-entries load (fetch load root))))

(defn keys
  "Lazy seq of the keys of the map rooted at `root`, in path order."
  [load root]
  (map first (entries load root)))

(defn count
  "Number of entries of the map rooted at `root`. Loads the root node only."
  [load root]
  (node-count (fetch load root)))

(defn- find-entry [host load root k]
  (let [k (norm-key host k) p (path host k)]
    (loop [obj (fetch load root) depth 0]
      (if (leaf? obj)
        (some (fn [e] (when (= k (first e)) e)) (:entries obj))
        (when-some [child (get (:children obj) (slice p depth))]
          (recur (fetch load child) (inc depth)))))))

(defn lookup
  "Value of key `k` in the map rooted at `root`, or `not-found` (default nil).
   Loads one node per level on the path of `k`."
  ([host load root k] (lookup host load root k nil))
  ([host load root k not-found]
   (if-some [e (find-entry host load root k)] (second e) not-found)))

(defn has-key?
  "True when key `k` is present in the map rooted at `root` (even with a nil value)."
  [host load root k]
  (some? (find-entry host load root k)))

;; ---------------------------------------------------------------- writes
;; In-memory results while updating: nil (empty subtree), an id string (child
;; not visited), {:ref id :n n} (visited, unchanged), {:leaf entries :n n} or
;; {:branch {slice result} :n n} (new, not hashed yet). Only what survives to
;; the end is hashed by `seal`, so `:new` never holds discarded intermediates.

(defn- build [depth pes]
  (let [n (cnt pes)]
    (cond
      (zero? n) nil
      (<= n leaf-capacity) {:leaf (mapv (fn [[_ k v]] [k v]) pes) :n n}
      :else {:branch (into {} (map (fn [grp] [(slice (ffirst grp) depth) (build (inc depth) (vec grp))]))
                           (partition-by #(slice (first %) depth) pes))
             :n n})))

(defn- merge-entries [host old changes]
  (let [m (into (sorted-map) (map (fn [[k v]] [(path host k) [k v]])) old)]
    (->> changes
         (reduce (fn [m [p k v]]
                   (when-some [[k0 _] (get m p)]
                     (when (not= k0 k) (fail :mmap/path-collision "two keys with the same path" {:keys [k0 k] :path p})))
                   (if (= removal v) (dissoc m p) (assoc m p [k v])))
                 m)
         (mapv (fn [[p [k v]]] [p k v])))))

(defn- res-entries [load r]
  (cond
    (nil? r)    nil
    (string? r) (entries load r)
    (:ref r)    (entries load (:ref r))
    (:leaf r)   (:leaf r)
    :else       (mapcat #(res-entries load (val %)) (sort-by key (:branch r)))))

(defn- upd
  "[result delta] of applying `changes` (sorted [path k v]) under node `id` (nil = empty)."
  [host load id depth changes]
  (let [obj (when id (fetch load id))]
    (if (or (nil? obj) (leaf? obj))
      (let [old (if obj (:entries obj) [])
            pes (merge-entries host old changes)]
        (if (= old (mapv (fn [[_ k v]] [k v]) pes))
          [(when id {:ref id :n (cnt old)}) 0]
          [(build depth pes) (- (cnt pes) (cnt old))]))
      (let [[kids delta changed?]
            (reduce (fn [[kids delta changed?] [sl grp]]
                      (let [[r d] (upd host load (get kids sl) (inc depth) grp)
                            same? (if r (some? (:ref r)) (nil? (get kids sl)))]
                        [(if r (assoc kids sl r) (dissoc kids sl)) (+ delta d) (or changed? (not same?))]))
                    [(:children obj) 0 false]
                    (sort-by key (group-by #(slice (first %) depth) changes)))
            n (+ (:count obj) delta)]
        (cond
          (not changed?) [{:ref id :n (:count obj)} 0]
          (zero? n) [nil delta]
          (<= n leaf-capacity) [{:leaf (vec (res-entries load {:branch kids})) :n n} delta]
          :else [{:branch kids :n n} delta])))))

(defn- seal
  "[id new'] hashing the in-memory result `r` bottom-up into `new`."
  [host r new]
  (cond
    (string? r) [r new]
    (:ref r)    [(:ref r) new]
    :else
    (let [[obj new] (if (:leaf r)
                      [{:mmap :leaf :entries (:leaf r)} new]
                      (let [[kids new] (reduce (fn [[kids new] [sl c]]
                                                 (let [[id new] (seal host c new)] [(assoc kids sl id) new]))
                                               [{} new]
                                               (sort-by key (:branch r)))]
                        [{:mmap :branch :count (:n r) :children kids} new]))
          id (canon/digest host obj)]
      [id (assoc new id obj)])))

(defn- finish [host r]
  (let [[root new] (seal host (or r {:leaf []}) {})]
    {:root root :new new}))

(defn empty
  "The empty map: `{:root id :new {id empty-leaf}}`."
  [host]
  (finish host nil))

(defn- path-entries [host changes]
  (->> changes
       (map (fn [[k v]]
              (let [k (norm-key host k)]
                [(path host k) k (if (= removal v) v (canon/normalize host v))])))
       (sort-by first)
       vec))

(defn update-many
  "Applies `changes`, a map k -> v (set) or k -> `removal` (remove), to the map
   rooted at `root`. Returns `{:root id :new {id node}}` where `:new` holds
   exactly the node objects of the result that did not exist at the same
   position before: nothing when nothing changes (same value written again,
   absent key removed), and never a discarded intermediate. A new node may
   coincide with an object already in the CAS elsewhere; writing it again is
   idempotent. Loads only the nodes on the paths of the changed keys, plus —
   when a removal collapses a branch — the at most `leaf-capacity` entries of
   its other children."
  [host load root changes]
  (let [pes (path-entries host changes)]
    (if (empty? pes)
      {:root root :new {}}
      (finish host (first (upd host load root 0 pes))))))

(defn from-entries
  "Builds from nothing the map holding `kvs` (a map or a seq of [k v]; a later
   pair wins over an earlier one with the same key; pairs whose value is
   `removal` are ignored). Same result as
   `update-many` over `(empty host)`, with every node in `:new`."
  [host kvs]
  (let [pes (path-entries host (into {} (comp (remove #(= removal (second %)))
                                              (map (fn [[k v]] [(norm-key host k) v])))
                                     kvs))]
    (doseq [[[p k] [p' k']] (map vector pes (rest pes))]
      (when (= p p') (fail :mmap/path-collision "two keys with the same path" {:keys [k k'] :path p})))
    (finish host (build 0 pes))))

;; ---------------------------------------------------------------- diff

(defn- diff-entries [a b]
  (let [ma (into {} a) mb (into {} b)]
    (-> {}
        (into (keep (fn [[k v]]
                      (cond (not (contains? mb k)) [k {:before v}]
                            (not= v (get mb k))    [k {:before v :after (get mb k)}])))
              ma)
        (into (keep (fn [[k v]] (when-not (contains? ma k) [k {:after v}]))) mb))))

(defn- diff-ids [load a b]
  (if (= a b)
    {}
    (let [oa (when a (fetch load a)) ob (when b (fetch load b))]
      (if (and oa ob (branch? oa) (branch? ob))
        (let [ca (:children oa) cb (:children ob)]
          (reduce (fn [acc sl] (into acc (diff-ids load (get ca sl) (get cb sl))))
                  {}
                  (sort (distinct (concat (clojure.core/keys ca) (clojure.core/keys cb))))))
        (diff-entries (when oa (obj-entries load oa)) (when ob (obj-entries load ob)))))))

(defn diff
  "Difference between the maps rooted at `a` and `b`: a map
   `{k {:before v-in-a :after v-in-b}}` with one entry per key whose presence or
   value differs; `:before` is absent for a key only in `b`, `:after` for a key
   only in `a`. Equal ids are never loaded: equal roots load nothing, and the
   descent enters only the children whose ids differ."
  [load a b]
  (diff-ids load a b))
