(ns sldb.kernel.revision
  "Applying a TransactionPlan to a store value and producing an immutable
   revision (docs/v2/02 §5, §5.1, §5.2), format 2: every cost is proportional
   to the plan, never to the number of trees, edges or revisions.

   Store (a value):
   {:host host :capabilities {...} :format-version 2
    :objects {id object}             ; the CAS: nodes, edges, tree objects, descriptors, mmap nodes, revisions, resolved plans
    :trees {tree-id committed-tree}  ; VIEW of the head: current tree values (sldb.kernel.tree)
    :edges #{edge-id}                ; VIEW of the head: active non-ownership edge set
    :head <rev-id|nil>               ; latest revision
    :heads {tree-id rev-id}          ; last revision that touched each tree (a VIEW of :heads-root on a lazy store)
    :heads-root <mmap root|nil>      ; the same map as an mmap tree-id -> rev-id (nil before the first commit)
    :revisions {rev-id Revision}}
   The two views are built only by `lazy-store` (host with the LazyMaps port)
   or `refresh-views` (eager fallback); kernel code asks through
   the accessors below (all of them load through `get-object`).

   Revision {:trees     <mmap root: tree-id -> [descriptor-id merkle-root]>
             :edges     <mmap root: active edge id -> true>
             :incidence <mmap root: node-id -> the active edge ids with it as :from or :to,
                         a sorted vector up to `hub-threshold` ids and {:set <root of the mmap edge-id -> true>} above>
             :parents [rev-id] :tx tx-id :actor :engines :timestamp}
   (sldb.kernel.mmap). :incidence is derived from :edges, deterministic, and never
   trusted by verification (a full replay recomputes it).
   revision-id = H(canonical-bytes Revision); Transaction {:id H(resolved plan) :plan :revision}.

   A store value with :format-version 1 is served by sldb.kernel.legacy.revision:
   apply-plan, diff, tree-at and the accessors dispatch on it."
  (:refer-clojure :exclude [ancestors])
  (:require [sldb.kernel.canon :as canon]
            [sldb.kernel.node :as node]
            [sldb.kernel.edge :as edge]
            [sldb.kernel.tree :as tree]
            [sldb.kernel.plan :as plan]
            [sldb.kernel.mmap :as mmap]
            [sldb.kernel.ports :as ports]
            [sldb.kernel.legacy.revision :as legacy]
            [sldb.kernel.err :as err]))

;; ---------------------------------------------------------------- store

(defn empty-store
  "A format-2 store with no objects, trees, edges or revisions."
  [host capabilities]
  {:host host :capabilities capabilities :format-version 2
   :objects {} :trees {} :edges #{} :head nil :heads {} :heads-root nil :revisions {}})

(defn legacy?
  "True for a format-1 store value (served by sldb.kernel.legacy.revision)."
  [store] (= 1 (:format-version store)))

(defn get-object
  "The CAS object with `id`, or nil. The one loader every accessor goes through."
  [store id] (get (:objects store) id))

(defn revision
  "The Revision map with `rev-id`, or nil."
  [store rev-id] (get (:revisions store) rev-id))

(defn ancestors
  "Revision ids reachable from `rev-id` through :parents (including itself)."
  [store rev-id]
  (loop [todo [rev-id] seen #{}]
    (if (empty? todo)
      seen
      (let [[r & more] todo]
        (if (or (nil? r) (seen r))
          (recur more seen)
          (recur (into more (:parents (revision store r))) (conj seen r)))))))

;; ---------------------------------------------------------------- accessors

(defn- loader
  "id -> mmap node over `get-object`. The empty map of a store without
   revisions is not in the CAS yet (the first revision writes it)."
  [store]
  (if (:head store)
    #(get-object store %)
    (let [e (:new (mmap/empty (:host store)))] #(or (get-object store %) (get e %)))))

(defn- roots
  "{:trees :edges :incidence} mmap roots of `rev-id` (the empty maps when nil/unknown)."
  [store rev-id]
  (or (revision store rev-id)
      (let [e (:root (mmap/empty (:host store)))] {:trees e :edges e :incidence e})))

(defn active-edges
  "Lazy seq of the active edge ids, ascending, at the head or at `rev-id`."
  ([store] (active-edges store (:head store)))
  ([store rev-id]
   (if (legacy? store)
     (seq (get-object store (:edges (revision store rev-id))))
     (mmap/keys (loader store) (:edges (roots store rev-id))))))

(defn edge-active?
  "True when `edge-id` is in the active edge set at the head or at `rev-id`
   (one mmap lookup)."
  ([store edge-id] (edge-active? store edge-id (:head store)))
  ([store edge-id rev-id]
   (cond
     (not (legacy? store)) (mmap/has-key? (:host store) (loader store) (:edges (roots store rev-id)) edge-id)
     (= rev-id (:head store)) (contains? (:edges store) edge-id)
     :else (boolean (some #{edge-id} (active-edges store rev-id))))))

(def hub-threshold
  "Largest number of edges a node's :incidence entry holds inline. Measured
   (gen_store, 200 docs; object bytes per commit): 8/16/32/64 -> 40.1/40.0/39.9/40.3 KB,
   inline only -> 50.7 KB and growing with the hub;
   16 = mmap/leaf-capacity keeps every inline entry near 1 KB."
  16)

(defn hub-value?
  "True when an :incidence value is the nested form `{:set <mmap root id>}`
   (a node with more than `hub-threshold` edges); the inline form is a vector."
  [v]
  (map? v))

(defn edges-touching
  "Sorted vector of the active edge ids having `node-id` as :from or :to, at
   the head or at `rev-id`. One :incidence lookup; for a hub (more than
   `hub-threshold` edges) it also enumerates the nested set."
  ([store node-id] (edges-touching store node-id (:head store)))
  ([store node-id rev-id]
   (if (legacy? store)
     (vec (filter #(let [e (get-object store %)] (or (= node-id (:from e)) (= node-id (:to e))))
                  (active-edges store rev-id)))
     (let [load (loader store)
           v (mmap/lookup (:host store) load (:incidence (roots store rev-id)) node-id [])]
       (if (hub-value? v) (vec (mmap/keys load (:set v))) v)))))

(defn edge-count
  "Number of active edges at the head or at `rev-id` (reads one mmap node)."
  ([store] (edge-count store (:head store)))
  ([store rev-id]
   (if (legacy? store)
     (count (active-edges store rev-id))
     (mmap/count (loader store) (:edges (roots store rev-id))))))

(defn tree-ids
  "Ids of the trees at the head or at `rev-id`, ascending."
  ([store] (tree-ids store (:head store)))
  ([store rev-id]
   (sort (if (legacy? store)
           (keys (:roots (revision store rev-id)))
           (mmap/keys (loader store) (:trees (roots store rev-id)))))))

(defn tree-count
  "Number of trees at the head or at `rev-id` (reads one mmap node)."
  ([store] (tree-count store (:head store)))
  ([store rev-id]
   (if (legacy? store)
     (count (:roots (revision store rev-id)))
     (mmap/count (loader store) (:trees (roots store rev-id))))))

(defn tree-entry
  "[descriptor-id merkle-root] of tree `tid` at the head or at `rev-id`, without
   loading any of its objects; nil when the revision has no such tree."
  ([store tid] (tree-entry store tid (:head store)))
  ([store tid rev-id]
   (if (legacy? store)
     (let [rev (revision store rev-id)]
       (when-let [root (get-in rev [:roots tid])]
         [(some (fn [[t d]] (when (= t tid) d)) (get-object store (:trees rev))) root]))
     (mmap/lookup (:host store) (loader store) (:trees (roots store rev-id)) tid nil))))

(defn tree-descriptor
  "Descriptor {:tree :kind :name :root} of tree `tid` at the head or at `rev-id`
   (loads one object), or nil."
  ([store tid] (tree-descriptor store tid (:head store)))
  ([store tid rev-id]
   (when-let [[desc-id _] (tree-entry store tid rev-id)] (get-object store desc-id))))

(defn tree-descriptors
  "`[[tree-id descriptor] …]` of every tree at the head, ascending by tree id:
   one walk of the :trees mmap plus one descriptor object per tree, and no
   tree object (a descriptor carries :tree :kind :name and the root node id)."
  [store]
  (if (legacy? store)
    (map (fn [[tid t]] [tid (:descriptor t)]) (sort-by key (:trees store)))
    (map (fn [[tid [desc-id _]]] [tid (get-object store desc-id)])
         (sort-by first (mmap/entries (loader store) (:trees (roots store (:head store))))))))

(defn load-tree
  "Committed tree {:tree :descriptor :root} of `tid` at `rev-id`, rebuilt from
   the CAS (loads the tree objects of that one tree); nil when absent. Equal to
   the value `(:trees store)` holds for the head."
  [store tid rev-id]
  (when-let [[desc-id root] (tree-entry store tid rev-id)]
    {:tree tid :descriptor (get-object store desc-id)
     :root (tree/from-objects root #(get-object store %))}))

(defn committed-tree
  "Committed tree of `tid` at the head (a lookup in the :trees view) or at
   `rev-id` (see `load-tree`); nil when absent."
  ([store tid] (get (:trees store) tid))
  ([store tid rev-id]
   (if (= rev-id (:head store)) (committed-tree store tid) (load-tree store tid rev-id))))

(defn lazy-store
  "The store value over `base`, a map with :host :capabilities :format-version
   :objects :revisions :head :heads, and optionally views that ALREADY describe
   that head and are kept as they are: :trees (a tree-id -> committed tree view)
   and :edges (the set of active edge ids). When the host has the optional
   LazyMaps port the result is a lazy map whose views of the head are computed
   on demand and never when nobody asks: :trees is a lazy map tree-id ->
   committed tree (`load-tree` per tree on lookup, `tree-ids` to enumerate) and
   :edges is the set of active edge ids, materialised on first access. The
   :edges view is a `delay` (a memo, not state) also kept in the metadata of
   the result, so that `apply-plan` can tell whether somebody forced it and
   derive the next one from it in O(plan) (`head-views`). Without the port (or
   for format 1) `base` is returned as is: the caller keeps the eager views
   (`refresh-views`). The index of `trees-named` is a third such memo (base key
   :tree-names, {name sorted-set-of-tree-ids}, also kept only in the metadata)."
  [base]
  (if-let [lm (when-not (legacy? base) (ports/lazy-maps (:host base)))]
    (let [head (:head base)
          core (select-keys base [:host :format-version :objects :revisions :head :heads-root])
          ids (delay (tree-ids core head))   ; enumerated at most once per view lineage, not once per scan
          trees (or (:trees base) (ports/lazy-map lm #(load-tree core % head) #(deref ids) {}))
          known (:edges base)
          edges (if known (doto (delay known) deref) (delay (set (active-edges core head))))
          named (:tree-names base)
          names (if named
                  (doto (delay named) deref)
                  (delay (reduce (fn [m tid] (update m (:name (tree-descriptor core tid head)) (fnil conj (sorted-set)) tid))
                                 {} @ids)))]
      (with-meta
        (ports/lazy-map lm
                        (fn [k] (when (= :edges k) @edges))
                        (constantly [:edges])
                        (assoc (dissoc base :edges :tree-names) :trees trees))
        {::edges-view edges ::names-view names}))
    base))

(defn trees-named
  "Ascending ids of the trees of the head whose descriptor :name is `name`.
   On a lazy store (`lazy-store`) the index name -> tree ids is built on the
   first call, once per view lineage (one descriptor per tree), and carried to
   the next head in O(plan) by `apply-plan`; otherwise the :trees view is scanned."
  [store name]
  (if-let [names (::names-view (meta store))]
    (vec (get @names name))
    (vec (sort (keep (fn [[tid t]] (when (= name (get-in t [:descriptor :name])) tid)) (:trees store))))))

(defn refresh-views
  "The EAGER views of the head, for hosts without the LazyMaps port, in O(plan):
   `trees'` {tree-id committed-tree} of the trees the transaction touched is
   merged into :trees, and the net edge delta (`added`/`removed` id sets) is
   applied to :edges. With the port, `lazy-store` computes the same views from
   the head Revision instead."
  [store trees' added removed]
  (-> store
      (update :trees merge trees')
      (update :edges #(reduce conj (reduce disj % removed) added))))

(defn- head-views
  "`store` (already at its new head: :objects :revisions :head :heads updated)
   with the views of that head: lazy when the host has the port, else eager.
   Lazy views cost O(plan) per commit whatever the callers forced before: the
   :trees view of the previous head is kept with the touched trees merged in
   (untouched trees are equal in both heads and no plan removes a tree; what
   the previous view already loaded stays loaded), and when the previous
   :edges set was materialised the new one is that set plus the net delta of
   the transaction (when it was not, it stays unmaterialised)."
  [store trees' added removed]
  (if (ports/lazy-maps? (:host store))
    (let [view (::edges-view (meta store))
          edges (when (and view (realized? view)) (reduce conj (reduce disj @view removed) added))
          nview (::names-view (meta store))
          ;; a touched tree leaves the name it had in the previous head (its old value is in
          ;; the previous :trees view, already loaded by the transaction) and joins its new one
          names (when (and nview (realized? nview))
                  (reduce (fn [m [tid t]]
                            (let [old (get-in (:trees store) [tid :descriptor :name])
                                  m (if (some? (get (:trees store) tid))
                                      (let [s (disj (get m old) tid)] (if (empty? s) (dissoc m old) (assoc m old s)))
                                      m)]
                              (update m (get-in t [:descriptor :name]) (fnil conj (sorted-set)) tid)))
                          @nview trees'))]
      ;; dissoc + seq + merge on a lazy map force nothing
      (lazy-store (cond-> (assoc (into {} (dissoc store :trees :edges)) :trees (merge (:trees store) trees'))
                    edges (assoc :edges edges)
                    names (assoc :tree-names names))))
    (refresh-views store trees' added removed)))

;; ---------------------------------------------------------------- working state

(defn- ws-init [store plan]
  {:store store :host (:host store) :plan plan
   :aliases {} :edges-added #{} :edges-removed #{}
   :trees {}          ; only the trees this transaction read for writing
   :touched #{} :superseded []
   :e+ #{} :e- #{}    ; net delta over the active edge set of the base
   :new {}})          ; objects created by this transaction

(defn- ws-object [ws id] (or (get (:new ws) id) (get-object (:store ws) id)))

(defn- node-exists? [ws id]
  (or (contains? (:new ws) id) (contains? (:objects (:store ws)) id)))

(defn- ws-tree [ws tid] (or (get-in ws [:trees tid]) (committed-tree (:store ws) tid)))

(defn- ws-active? [ws id]
  (or (contains? (:e+ ws) id)
      (and (not (contains? (:e- ws) id)) (edge-active? (:store ws) id))))

(defn- activate
  "Adds the (inactive) edge `e` to the active set."
  [ws e]
  (let [id (:id e)]
    (cond-> (-> ws (assoc-in [:new id] e) (update :edges-added conj id))
      (contains? (:e- ws) id)       (update :e- disj id)
      (not (contains? (:e- ws) id)) (update :e+ conj id))))

(defn- deactivate
  "Removes the (active) edge `id` from the active set."
  [ws id]
  (cond-> (update ws :edges-removed conj id)
    (contains? (:e+ ws) id)       (update :e+ disj id)
    (not (contains? (:e+ ws) id)) (update :e- conj id)))

(defn- ws-touching
  "Active edges of the working state with `node` at either endpoint: the
   :incidence entry of the base corrected by the transaction's own delta."
  [ws node]
  (let [base (remove (:e- ws) (edges-touching (:store ws) node))
        fresh (filter #(let [e (ws-object ws %)] (or (= node (:from e)) (= node (:to e)))) (:e+ ws))]
    (mapv #(ws-object ws %) (sort (distinct (concat base fresh))))))

(defn- require-node [ws op id]
  (when-not (and (string? id) (node-exists? ws id))
    (plan/reject :ids-exist op (str "unknown node id " id)))
  id)

(defn- bind-alias [ws op id]
  (if-let [a (:as op)]
    (do (when (contains? (:aliases ws) a) (plan/reject :ids-exist op (str "alias declared twice " a)))
        (assoc-in ws [:aliases a] id))
    ws))

(defn- r [ws x] (plan/resolve-ref (:aliases ws) x))

(defn- rejecting [check op f]
  (err/rescue (fn [e] (plan/reject check op (ex-message e))) (f)))

(defn- add-node-op [ws op]
  (let [{:keys [class kind content]} (:node op)
        n (rejecting :ids-exist op #(node/make (:host ws) class kind content))]
    (-> ws (assoc-in [:new (:id n)] n) (bind-alias op (:id n)))))

(defn- new-tree-op [ws op]
  (let [root (require-node ws op (r ws (:root op)))
        {:keys [kind name]} (:tree op)
        t (rejecting :tree-integrity op
                     #(if (:id op)
                        (tree/create (:host ws) kind (or name "") root (:id op))
                        (tree/create (:host ws) kind (or name "") root)))
        id (:tree t)]
    (when (ws-tree ws id) (plan/reject :ids-exist op (str "tree already exists " id)))
    (-> ws (assoc-in [:trees id] t) (update :touched conj id) (bind-alias op id))))

(defn- require-tree [ws op tid]
  (when-not (ws-tree ws tid) (plan/reject :ids-exist op (str "unknown tree " tid)))
  tid)

(defn- with-tree [ws op tid f]
  (rejecting :tree-integrity op #(-> ws (assoc-in [:trees tid] (f (ws-tree ws tid))) (update :touched conj tid))))

(defn- resolve-edge [ws raw]
  (-> raw
      (cond-> (contains? raw :from) (update :from #(r ws %)))
      (update :to #(r ws %))
      (cond-> (contains? raw :tree) (update :tree #(r ws %)))
      (cond-> (get-in raw [:evidence :ref-hash]) (update-in [:evidence :ref-hash] #(r ws %)))
      (cond-> (get-in raw [:evidence :context]) (update-in [:evidence :context] #(r ws %)))))

(defn- re-anchor
  "§6.1 inside the transaction: reference/binding edges with `old` at either
   endpoint get a successor edge; derived edges pointing at `old` are invalidated
   (removed from the active set); semantic/projection are left as they are.
   Costs the edges touching `old` (:incidence), not the edge set."
  [ws old new]
  (let [host (:host ws)]
    (reduce (fn [ws e]
              (case (:type e)
                (:reference :binding)
                (let [e' (edge/make host (cond-> (dissoc e :id)
                                           (= old (:from e)) (assoc :from new)
                                           (= old (:to e))   (-> (assoc :to new) (assoc-in [:evidence :ref-hash] new))))]
                  (if (ws-active? ws (:id e')) ws (activate ws e')))
                :derived (deactivate ws (:id e))
                ws))
            ws (ws-touching ws old))))

(defn- add-edge-op [ws op]
  (let [e0 (resolve-edge ws (:edge op))]
    (require-node ws op (:to e0))
    (if (= :ownership (:type e0))
      (let [tid (require-tree ws op (:tree e0))
            e (rejecting :evidence-ref-hash op #(edge/make (:host ws) e0))]
        (-> ws
            (with-tree op tid #(tree/add-child % (:parent e) (:to e) (:order e)))
            (bind-alias op (:id e))))
      (let [_ (require-node ws op (:from e0))
            e (rejecting :evidence-ref-hash op #(edge/make (:host ws) e0))]
        (when (and (get-in e [:evidence :ref-hash]) (not= (get-in e [:evidence :ref-hash]) (:to e)))
          (plan/reject :evidence-ref-hash op ":ref-hash must equal the id of :to in the plan-resolved state"))
        (when-let [ctx (get-in e [:evidence :context])] (require-node ws op ctx))
        (if (ws-active? ws (:id e))
          (bind-alias ws op (:id e))                       ; idempotent no-op (§3.2): never re-anchors again
          (let [ws' (-> ws (activate e) (bind-alias op (:id e)))]
            ;; §6.1, second re-anchoring trigger: :from is the successor, :to the
            ;; replaced node, and the rules run with the edge already active.
            (if (= :supersedes (:type e))
              (-> ws'
                  (update :superseded conj [(:to e) (:from e)])
                  (re-anchor (:to e) (:from e)))
              ws')))))))

(defn- remove-edge-op [ws op]
  (let [id (r ws (:edge op))]
    (when-not (ws-active? ws id) (plan/reject :ids-exist op (str "unknown or inactive edge " id)))
    (deactivate ws id)))

(defn- replace-op [ws op]
  (let [tid (require-tree ws op (r ws (:tree op)))
        path (:at op)
        old (or (tree/node-at (ws-tree ws tid) path) (plan/reject :ids-exist op (str "no position " path)))
        new (require-node ws op (r ws (:new op)))
        sup (edge/make (:host ws) {:type :supersedes :from new :to old :evidence {:actor (:actor (:plan ws))}})]
    (-> ws
        (with-tree op tid #(tree/replace-node % path new))
        (as-> w (if (ws-active? w (:id sup))
                  (-> w (assoc-in [:new (:id sup)] sup) (update :edges-added conj (:id sup)))
                  (activate w sup)))
        (update :superseded conj [old new])
        (re-anchor old new))))

(defn- move-op [ws op]
  (let [tid (require-tree ws op (r ws (:tree op)))]
    (with-tree ws op tid #(tree/move % (:from op) (:to op) (:order op)))))

(defn- detach-op [ws op]
  (let [tid (require-tree ws op (r ws (:tree op)))]
    (with-tree ws op tid #(tree/detach % (:at op)))))

(defn- apply-op [ws op]
  (case (:op op)
    :new-tree    (new-tree-op ws op)
    :add-node    (add-node-op ws op)
    :add-edge    (add-edge-op ws op)
    :remove-edge (remove-edge-op ws op)
    :replace     (replace-op ws op)
    :move        (move-op ws op)
    :detach      (detach-op ws op)))

;; ---------------------------------------------------------------- diff (§5.2)

(defn tree-at
  "Committed tree :root Position of `tid` at `rev-id`, rebuilt from the CAS; nil
   when the revision has no such tree."
  [store rev-id tid]
  (if (legacy? store)
    (legacy/tree-at store rev-id tid)
    (when-let [[_ root-hash] (tree-entry store tid rev-id)]
      (tree/from-objects root-hash #(get-object store %)))))

(defn- position-map
  "{path node} of a :root Position."
  [pos]
  (into {} (tree/positions {:root pos})))

(defn- children-map
  "{path [child-node …]} of a :root Position."
  [pos]
  (letfn [(walk [p path] (cons [path (mapv :node (:children p))]
                               (mapcat (fn [i c] (walk c (conj path i))) (range) (:children p))))]
    (into {} (walk pos []))))


(defn diff
  "Difference between two revisions (docs/v2/02 §5.2):
   {:trees {tree-id {:added [node …] :removed [node …]
                     :moved [{:node :from-paths :to-paths} …] :parents-changed [path …]}}
    :edges {:added :removed} :superseded [[old new] …]}.
   Walks the mmaps of both revisions only where they differ, and descends only
   the trees whose merkle-root differs."
  [store r1 r2]
  (if (legacy? store)
    (legacy/diff store r1 r2)
    (let [load (loader store) m1 (roots store r1) m2 (roots store r2)
          trees (into {}
                      (for [[tid {:keys [before after]}] (mmap/diff load (:trees m1) (:trees m2))
                            :when (not= (second before) (second after))]
                        (let [a (tree-at store r1 tid) b (tree-at store r2 tid)
                              pa (if a (position-map a) {}) pb (if b (position-map b) {})
                              na (set (vals pa)) nb (set (vals pb))
                              paths-of (fn [pm n] (vec (sort (keep (fn [[p x]] (when (= x n) p)) pm))))
                              moved (for [n (sort (filter nb na))
                                          :let [fa (paths-of pa n) fb (paths-of pb n)]
                                          :when (not= fa fb)]
                                      {:node n :from-paths fa :to-paths fb})
                              ca (if a (children-map a) {}) cb (if b (children-map b) {})
                              parents-changed (for [p (sort (distinct (concat (keys ca) (keys cb))))
                                                    :when (not= (get ca p) (get cb p))]
                                                p)]
                          [tid {:added (vec (sort (remove na nb)))
                                :removed (vec (sort (remove nb na)))
                                :moved (vec moved)
                                :parents-changed (vec parents-changed)}])))
          ed (mmap/diff load (:edges m1) (:edges m2))
          added (vec (sort (keep (fn [[id d]] (when-not (contains? d :before) id)) ed)))
          removed (vec (sort (keep (fn [[id d]] (when-not (contains? d :after) id)) ed)))
          superseded (for [eid added
                           :let [e (get-object store eid)]
                           :when (= :supersedes (:type e))]
                       [(:to e) (:from e)])]
      {:trees trees
       :edges {:added added :removed removed}
       :superseded (vec superseded)})))

;; ---------------------------------------------------------------- base-cas (check 1) and conflicts


;; ---------------------------------------------------------------- base-cas (check 1) and conflicts

(defn- tree-changed-since?
  "True when tree `tid` was touched by a revision that is not an ancestor of `base`."
  [store base tid]
  (let [last (get-in store [:heads tid])]
    (and last (not (contains? (ancestors store base) last)))))

(defn- op-parent-paths
  "Parent positions an op edits in its tree."
  [op]
  (case (:op op)
    :add-edge (when (= :ownership (get-in op [:edge :type])) [(get-in op [:edge :parent])])
    :replace  [(tree/parent-path (:at op))]
    :detach   [(tree/parent-path (:at op))]
    :move     [(tree/parent-path (:from op)) (:to op)]
    nil))

(defn- op-position-paths
  "Positions an op addresses as a target (whose node may have been replaced/removed)."
  [op]
  (case (:op op)
    :replace [(:at op)]
    :detach  [(:at op)]
    :move    [(:from op)]
    nil))

(defn- conflicts-for
  "ConflictSet entries between the plan and the changes base→head on tree `tid`."
  [store base tid resolved-ops]
  (let [head-rev (:head store)
        full (diff store base head-rev)
        d (get-in full [:trees tid])
        changed-parents (set (:parents-changed d))
        base-pos (some-> (tree-at store base tid) position-map)
        removed (set (:removed d))
        superseded (set (map first (:superseded full)))]
    (for [op resolved-ops
          :when (= tid (first (plan/touched-trees {:ops [op]})))
          entry (concat
                 (for [p (op-parent-paths op) :when (and p (contains? changed-parents p))]
                   {:tree tid :path p :kind :same-parent-edit})
                 (for [p (op-position-paths op)
                       :let [n (get base-pos p)]
                       :when n
                       :let [kind (cond (contains? superseded n) :superseded-target
                                        (contains? removed n) :removed-target)]
                       :when kind]
                   {:tree tid :path p :node n :kind kind}))]
      entry)))

(defn- removed-edge-conflicts
  "A :remove-edge whose target head already removed is a :removed-target conflict."
  [store base resolved-ops]
  (let [gone (set (get-in (diff store base (:head store)) [:edges :removed]))]
    (for [op resolved-ops
          :when (and (= :remove-edge (:op op)) (contains? gone (:edge op)))]
      {:tree nil :node (:edge op) :kind :removed-target})))

(defn- check-base
  "Check 1 (base-cas). Returns the plan, possibly rebased onto head."
  [store plan resolved-ops touched]
  (let [base (:base plan) head (:head store)]
    (cond
      (and (nil? base) (nil? head)) plan
      (nil? base) (plan/reject :base-cas nil "store is not empty; :base is required")
      (not (contains? (:revisions store) base)) (plan/reject :base-cas nil (str "unknown base revision " base))
      (= base head) plan
      :else
      (let [changed (filter #(tree-changed-since? store base %) touched)
            cs (concat (mapcat #(conflicts-for store base % resolved-ops) changed)
                       (removed-edge-conflicts store base resolved-ops))]
        (if (seq cs)
          (throw (ex-info "conflict" {:type :plan/conflict
                                      :conflict-set {:base base :head head :plan plan :conflicts (vec (distinct cs))}}))
          (assoc plan :base head :rebased-from base))))))

;; ---------------------------------------------------------------- objects of a revision


;; ---------------------------------------------------------------- apply

(defn incidence-value
  "The canonical :incidence value of a node whose current value is `cur`
   (nil, an inline vector or `{:set root}`) after adding the edge ids `add` and
   removing `del`: {:value v :new {id mmap-node}}. The shape is a function of
   the resulting set alone: no edges -> mmap/removal; at most `hub-threshold`
   -> the sorted vector of ids; more -> `{:set root}`, root of the mmap
   edge-id -> true. A hub costs O(log) mmap nodes per edge, never its whole
   set; it collapses back to the vector when it shrinks to the threshold."
  [host load cur add del]
  (if (hub-value? cur)
    (let [{:keys [root new]} (mmap/update-many host load (:set cur)
                                               (merge (zipmap del (repeat mmap/removal)) (zipmap add (repeat true))))
          load' #(or (get new %) (load %))]
      (if (> (mmap/count load' root) hub-threshold)
        {:value {:set root} :new new}
        {:value (vec (mmap/keys load' root)) :new {}}))
    (let [ids (vec (sort (into (reduce disj (set cur) del) add)))]
      (cond
        (empty? ids) {:value mmap/removal :new {}}
        (> (count ids) hub-threshold) (let [m (mmap/from-entries host (map #(vector % true) ids))]
                                        {:value {:set (:root m)} :new (:new m)})
        :else {:value ids :new {}}))))

(defn- incidence-delta
  "{:delta {node-id value | mmap/removal} :new {id mmap-node}} for the
   endpoints of the net edge delta (values as `incidence-value`)."
  [ws load base-root]
  (let [host (:host (:store ws))
        ends (fn [id] (let [e (ws-object ws id)] (distinct [(:from e) (:to e)])))
        collect (fn [m ids k]
                  (reduce (fn [m id] (reduce #(update-in %1 [%2 k] (fnil conj #{}) id) m (ends id))) m ids))]
    (reduce (fn [acc [n {:keys [add del]}]]
              (let [{:keys [value new]} (incidence-value host load (mmap/lookup host load base-root n nil) add del)]
                (-> acc (assoc-in [:delta n] value) (update :new merge new))))
            {:delta {} :new {}}
            (-> {} (collect (:e+ ws) :add) (collect (:e- ws) :del)))))

(defn- commit-tree
  "[committed-tree {id object}] with exactly the tree objects of the positions
   the transaction dirtied, plus the descriptor."
  [host t]
  (let [dirty (tree/dirty-paths t)
        t' (tree/commit host t)]
    [t' (into {(tree/descriptor-id host (:descriptor t')) (:descriptor t')}
              (map (fn [p] (let [pos (tree/position-at t' p)] [(:hash pos) (tree/tree-object pos)])))
              dirty)]))

(defn apply-plan
  "Validates and applies `plan` to `store` (§5.1). Returns
   {:store store' :transaction tx :revision rev :revision-id id :aliases {...}
    :edges-added [...] :superseded [[old new] ...] :rebased-from base|nil
    :new-objects {id object}}
   or raises :plan/rejected (with :check and :op) / :plan/conflict (with
   :conflict-set). All-or-nothing: the input store is never mutated.
   :new-objects is everything the transaction adds to the CAS (nodes, edges,
   tree objects of dirtied positions, descriptors of touched trees, mmap nodes,
   the resolved plan and the Revision): a committer persists exactly those. An
   object equal to an already stored one may appear in it. Cost: the plan, the
   trees it touches and a few mmap nodes per changed key."
  [store plan]
  (if (legacy? store)
    (legacy/apply-plan store plan)
    (let [host (:host store)
          plan (plan/canonical host (plan/check-schema plan))
          _ (plan/check-opaque-replace-only plan)
          ;; check 1 runs BEFORE executing the ops so that a plan addressing a position
          ;; head replaced or removed yields a ConflictSet (§5.2) instead of an execution
          ;; rejection. Alias refs are keywords and can never match the base→head delta.
          raw-ops (mapv #(dissoc % :as) (:ops plan))
          plan0 (check-base store (assoc plan :ops raw-ops) raw-ops (plan/touched-trees plan))
          ws (reduce apply-op (ws-init store plan) (:ops plan))
          aliases (:aliases ws)
          resolved-ops (mapv (fn [op] (plan/substitute-aliases aliases (dissoc op :as))) (:ops plan))
          touched (:touched ws)
          _ (plan/check-capabilities (:capabilities store) (assoc plan :ops resolved-ops) aliases)
          plan' (cond-> (assoc plan :ops resolved-ops)
                  (:rebased-from plan0) (assoc :base (:base plan0) :rebased-from (:rebased-from plan0)))
          _ (doseq [tid touched]
              (when-not (tree/valid? (get-in ws [:trees tid]))
                (plan/reject :tree-integrity nil (str "tree " tid " is not a tree after applying the plan"))))
          committed (into {} (map (fn [tid] [tid (commit-tree host (get-in ws [:trees tid]))])) touched)
          trees' (into {} (map (fn [[tid [t _]]] [tid t])) committed)
          load (loader store)
          empty-map (mmap/empty host)   ; referenced by a revision with no trees/edges: written with it
          base (roots store (:head store))
          tm (mmap/update-many host load (:trees base)
                               (into {} (map (fn [[tid t]] [tid [(tree/descriptor-id host (:descriptor t)) (tree/merkle-root t)]])) trees'))
          em (mmap/update-many host load (:edges base)
                               (merge (zipmap (:e- ws) (repeat mmap/removal)) (zipmap (:e+ ws) (repeat true))))
          idelta (incidence-delta ws load (:incidence base))
          im (update (mmap/update-many host load (:incidence base) (:delta idelta)) :new #(merge (:new idelta) %))
          resolved-plan (dissoc plan' :rebased-from)
          tx-id (canon/digest host resolved-plan)
          rev {:trees (:root tm)
               :edges (:root em)
               :incidence (:root im)
               :parents (if (:head store) [(:head store)] [])
               :tx tx-id
               :actor (:actor plan)
               :engines (or (:engines plan) {})
               :timestamp (:timestamp plan)}
          rev-id (canon/digest host rev)
          ;; the per-tree heads live outside the Revision (each touched tree's value is rev-id)
          hm (mmap/update-many host load (or (:heads-root store) (:root empty-map))
                               (into {} (map (fn [tid] [tid rev-id])) touched))
          tx {:id tx-id :plan resolved-plan :revision rev-id}
          ;; every CAS object satisfies H(canonical-bytes object) == id (invariant 17):
          ;; the transaction is stored as its resolved plan; the Transaction map lives in the log
          new-objects (-> (:new ws)
                          (into (mapcat (fn [[_ [_ objs]]] objs)) committed)
                          (cond-> (some #{(:root empty-map)} [(:root tm) (:root em) (:root im) (:root hm)]) (merge (:new empty-map)))
                          (merge (:new tm) (:new em) (:new im) (:new hm))
                          (assoc rev-id rev tx-id resolved-plan))
          store' (-> store
                     (update :objects merge new-objects)
                     (assoc :head rev-id)
                     (update :heads merge (into {} (map (fn [tid] [tid rev-id])) touched))
                     (assoc :heads-root (:root hm))
                     (assoc-in [:revisions rev-id] rev)
                     (head-views trees' (:e+ ws) (:e- ws)))]
      {:store store' :transaction tx :revision rev :revision-id rev-id
       :aliases aliases :edges-added (vec (sort (:edges-added ws))) :superseded (:superseded ws)
       :rebased-from (:rebased-from plan') :new-objects new-objects})))
