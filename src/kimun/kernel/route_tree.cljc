(ns kimun.kernel.route-tree
  "The route map as a Merkle tree by path (K2a, docs/v2/14 §20; route schema 2,
   docs/v2/10 §11.2): one node per directory, so a change in one tracked path
   rewrites the nodes from that path up to the root and no other node.

   What that buys and what it does not. The NUMBER of nodes read or written
   follows the depth of the path, not the number of routes. Each of those nodes
   is read, hashed and rewritten WHOLE, so the work is the sum of the children
   of the directories on the path: a directory with many direct children costs
   in proportion to them, and a store whose tracked files all sit in one
   directory pays for every route on every write. There is no fan-out inside a directory; bounding that cost would be
   another node shape, hence another route schema.

   Node object (a plain EDN value in the CAS, id = `canon/digest` of the node):

     {:route/version \"kimun.route/2\"
      :children {segment {:entry {:fingerprint :class :projection-hash
                                  :content-hash :tree-id?}
                          :dir   {:hash h :object id :count n}}}}

   A child has `:entry` (a tracked path ends here), `:dir` (tracked paths go on
   below) or both. `:dir` names the node below three ways: `:object` is its CAS
   id, which covers everything including the `:tree-id` locators; `:hash` is its
   route hash (`node-hash`), which leaves them out; `:count` is the number of
   entries below it.

   Two hashes, one tree. The ROUTE HASH of the root is the route root of the
   store: it covers path segments and the four sensed fields of each entry, never
   a tree id, a revision or a time, so the same files registered under new ULIDs
   give the same root. The OBJECT ID of the root is what the heads name as the
   durable locator. Both are checked on the way down (`:route/corrupt`), node by
   node, without reading the rest: every node read — the root included, in
   `lookup`, `entries` and `update` alike — must hash to the object id
   it was asked for, and a node below another must also have the route hash and
   the count its parent recorded.

   Canonical shape: a directory node other than the root is never empty, and a
   child is never an empty map. The tree is therefore a function of its entries
   alone, whatever the order of the writes that built it.

   A path is a locator, not an identity; nothing here decides what a moved file
   is. `load` is a function id -> node object (nil when absent). Nothing here
   does I/O: an update returns the new node objects as `{:new {id node}}`."
  (:refer-clojure :exclude [empty update])
  (:require [clojure.string :as str]
            [kimun.kernel.canon :as canon]
            [kimun.kernel.err :as err]
            [kimun.kernel.route :as route]))

(def schema
  "Schema marker of a route tree node."
  "kimun.route/2")

(def removal
  "Sentinel value for `update`: `{path removal}` removes the entry at `path`."
  ::remove)

(def ^:private entry-keys
  "Keys of an entry as a node keeps it: the path is given by the position."
  [:fingerprint :class :projection-hash :content-hash :tree-id])

(defn- fail [type why data] (err/raise type (str "route tree: " why) data))

(defn node?
  "True when `o` has the shape of a route tree node."
  [o]
  (and (map? o) (= schema (:route/version o)) (map? (:children o))))

(defn- node [children] {:route/version schema :children children})

(defn- sensed
  "The fields of a stored entry that enter the route hash. `:tree-id` is
   deliberately absent."
  [e]
  [(:fingerprint e) (:class e) (:projection-hash e) (:content-hash e)])

(defn node-hash
  "The route hash of node `n`: the canonical digest of its children as
   `[segment sensed-fields-or-nil route-hash-below-or-nil]`, sorted by segment.
   Tree ids and object ids do not enter it."
  [host n]
  (canon/digest host {:route/version schema
                      :children (mapv (fn [[seg c]] [seg (some-> (:entry c) sensed) (get-in c [:dir :hash])])
                                      (sort-by key (:children n)))}))

(defn node-count
  "Number of entries at and below node `n`, from the node alone."
  [n]
  (reduce + 0 (map (fn [c] (+ (if (:entry c) 1 0) (get-in c [:dir :count] 0))) (vals (:children n)))))

(defn child-ids
  "Object ids of the nodes directly below node `n`."
  [n]
  (vec (keep #(get-in % [:dir :object]) (vals (:children n)))))

(defn- fetch
  "The node `id` through `load`, checked against its own id: raises
   `:route/missing-node` when it is absent or is not a node, and
   `:route/corrupt` when its content does not hash to `id`. `load` is not
   trusted to have rehashed what it returns (a store reads objects lazily
   without doing so), and the object id is the only hash that covers the
   `:tree-id` locators: without this check a node altered only there would
   keep its route hash and pass."
  [host load id]
  (let [n (load id)]
    (when-not (node? n) (fail :route/missing-node (str "node " id " is absent or is not a route tree node") {:id id}))
    (when-not (= id (canon/digest host n))
      (fail :route/corrupt (str "node " id " does not hash to its object id") {:id id}))
    n))

(defn- below
  "The node a `:dir` reference names, checked three ways (`:route/corrupt`): its
   content hashes to the `:object` id the parent recorded (`fetch`), and its
   route hash and its count are the ones the parent recorded."
  [host load {:keys [hash object count] :as ref}]
  (let [n (fetch host load object)]
    (when-not (and (= hash (node-hash host n)) (= count (node-count n)))
      (fail :route/corrupt (str "node " object " does not match the reference of its parent") {:ref ref}))
    n))

(defn root-node
  "The root node `object` through `load`, checked against its object id and
   against the route root `hash` the heads record (`:route/corrupt` when
   either is another)."
  [host load hash object]
  (let [n (fetch host load object)]
    (when-not (= hash (node-hash host n))
      (fail :route/corrupt (str "root node " object " does not hash to the route root " hash) {:root hash :object object}))
    n))

(defn- segments [path] (str/split path #"/" -1))

(defn- path-key
  "The normalized key of `path`; raises `:route/invalid` for an unsafe one."
  [host path]
  (or (route/path-key host path)
      (fail :route/invalid (str "unsafe path " (pr-str path)) {:path path})))

(defn empty
  "The tree with no entries: `{:hash :object :count 0 :new {id node}}`. Its
   root is an ordinary node object, so a root always names a loadable node."
  [host]
  (let [n (node {})
        id (canon/digest host n)]
    {:hash (node-hash host n) :object id :count 0 :new {id n}}))

(defn- upd
  "Applies `changes` (`[[segments value] …]`, relative to node `n`; `n` nil =
   no node yet). Returns `{:node n' :new {id node}}`; `:node` is nil when
   nothing is left below."
  [host load n changes]
  (let [step (fn [{:keys [children new]} [seg items]]
               (let [c (get children seg)
                     here (first (filter #(= 1 (count (first %))) items))
                     deeper (seq (for [[segs v] items :when (> (count segs) 1)] [(rest segs) v]))
                     c (if here
                         (if (= removal (second here)) (dissoc c :entry) (assoc c :entry (second here)))
                         c)
                     r (when deeper (upd host load (some->> (:dir c) (below host load)) deeper))
                     c (cond
                         (nil? r) c
                         (:node r) (assoc c :dir {:hash (node-hash host (:node r))
                                                  :object (canon/digest host (:node r))
                                                  :count (node-count (:node r))})
                         :else (dissoc c :dir))
                     new (cond-> (merge new (:new r))
                           (:node r) (assoc (get-in c [:dir :object]) (:node r)))]
                 {:children (if (seq c) (assoc children seg c) (dissoc children seg)) :new new}))
        r (reduce step {:children (or (:children n) {}) :new {}} (group-by ffirst changes))]
    {:node (when (seq (:children r)) (node (:children r))) :new (:new r)}))

(defn update
  "The tree whose root node is `object` with `changes` applied: `{path entry}`
   to register or replace the entry of a path, `{path removal}` to remove it
   (removing an absent path changes nothing). An entry is a route entry
   (`kimun.kernel.route/entry`); its `:path` is taken from the key. Returns
   `{:hash :object :count :new {id node}}`: the route root, the id of the root
   node, the number of entries and the node objects to write. Reads only the
   nodes on the way to the changed paths; raises `:route/invalid` for an unsafe
   path."
  [host load object changes]
  (let [items (for [[p v] changes]
                [(segments (path-key host p))
                 (if (= removal v) removal (select-keys v entry-keys))])
        r (upd host load (fetch host load object) items)
        n (or (:node r) (node {}))
        id (canon/digest host n)]
    {:hash (node-hash host n) :object id :count (node-count n) :new (assoc (:new r) id n)}))

(defn from-entries
  "The tree of `entries` (route entries with `:path`), built from nothing:
   `{:hash :object :count :new}`. Raises `:route/ambiguous` when two entries
   name the same path."
  [host entries]
  (let [es (route/canonical-entries host entries)
        e (empty host)
        r (update host (:new e) (:object e) (into {} (map (juxt :path identity)) es))]
    (clojure.core/update r :new #(merge (:new e) %))))

(defn lookup
  "The entry at `path` (with its `:path`) in the tree whose root node is
   `object`, or nil. Reads one node per segment of the path."
  [host load object path]
  (when-let [p (route/path-key host path)]
    (loop [n (fetch host load object) [seg & more] (segments p)]
      (let [c (get-in n [:children seg])]
        (cond
          (nil? c) nil
          (nil? more) (some-> (:entry c) (assoc :path p))
          (:dir c) (recur (below host load (:dir c)) more)
          :else nil)))))

(defn- walk
  "Every entry at and below node `n`, whose path prefix is `prefix`."
  [host load n prefix]
  (mapcat (fn [[seg c]]
            (let [p (if prefix (str prefix "/" seg) seg)]
              (concat (when-let [e (:entry c)] [(assoc e :path p)])
                      (when-let [ref (:dir c)] (walk host load (below host load ref) p)))))
          (:children n)))

(defn entries
  "Every entry of the tree whose root node is `object`, sorted by path. Reads
   and checks every node."
  [host load object]
  (vec (sort-by :path (walk host load (fetch host load object) nil))))

