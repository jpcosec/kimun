(ns sldb.kernel.revision
  "Applying a TransactionPlan to an in-memory store and producing an immutable
   revision (docs/v2/02 §5, §5.1, §5.2). Ownership is addressed by position paths
   (§3.1): a node may occur at several positions of a tree.

   Store (a value):
   {:host host :capabilities {...}
    :objects {id object}            ; the CAS: nodes, edges, tree objects, descriptors, edge-sets, tree-sets, revisions, resolved plans
    :trees {tree-id committed-tree}  ; current tree values (sldb.kernel.tree)
    :edges #{edge-id}               ; active non-ownership edge set at :head
    :head <rev-id|nil>              ; latest revision
    :heads {tree-id rev-id}         ; last revision that touched each tree
    :revisions {rev-id Revision}}

   Revision {:roots {tree-id merkle-root} :trees tree-set-id :edges edge-set-id
             :parents [rev-id] :tx tx-id :actor :engines :timestamp}
   revision-id = H(canonical-bytes Revision); Transaction {:id H(resolved plan) :plan :revision}."
  (:require [sldb.kernel.canon :as canon]
            [sldb.kernel.node :as node]
            [sldb.kernel.edge :as edge]
            [sldb.kernel.tree :as tree]
            [sldb.kernel.plan :as plan]
            [sldb.kernel.err :as err]))

;; ---------------------------------------------------------------- store

(defn empty-store
  "A store with no objects, trees, edges or revisions."
  [host capabilities]
  {:host host :capabilities capabilities
   :objects {} :trees {} :edges #{} :head nil :heads {} :revisions {}})

(defn get-object
  "The CAS object with `id`, or nil."
  [store id] (get-in store [:objects id]))

(defn revision
  "The Revision map with `rev-id`, or nil."
  [store rev-id] (get-in store [:revisions rev-id]))

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

;; ---------------------------------------------------------------- working state

(defn- ws-init [store plan]
  {:store store :host (:host store) :plan plan
   :aliases {} :nodes-added #{} :edges-added #{} :edges-removed #{}
   :trees (:trees store) :touched #{} :superseded []
   :edges (:edges store) :objects (:objects store)})

(defn- node-exists? [ws id] (contains? (:objects ws) id))

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
    (-> ws
        (assoc-in [:objects (:id n)] n)
        (update :nodes-added conj (:id n))
        (bind-alias op (:id n)))))

(defn- new-tree-op [ws op]
  (let [root (require-node ws op (r ws (:root op)))
        {:keys [kind name]} (:tree op)
        t (rejecting :tree-integrity op
                     #(if (:id op)
                        (tree/create (:host ws) kind (or name "") root (:id op))
                        (tree/create (:host ws) kind (or name "") root)))
        id (:tree t)]
    (when (contains? (:trees ws) id) (plan/reject :ids-exist op (str "tree already exists " id)))
    (-> ws (assoc-in [:trees id] t) (update :touched conj id) (bind-alias op id))))

(defn- require-tree [ws op tid]
  (when-not (contains? (:trees ws) tid) (plan/reject :ids-exist op (str "unknown tree " tid)))
  tid)

(defn- with-tree [ws op tid f]
  (rejecting :tree-integrity op #(-> ws (update-in [:trees tid] f) (update :touched conj tid))))

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
   (removed from the active set); semantic/projection are left as they are."
  [ws old new]
  (let [host (:host ws)
        touching (for [eid (:edges ws)
                       :let [e (get-in ws [:objects eid])]
                       :when (or (= old (:to e)) (= old (:from e)))]
                   e)]
    (reduce (fn [ws e]
              (case (:type e)
                (:reference :binding)
                (let [e' (edge/make host (cond-> (dissoc e :id)
                                           (= old (:from e)) (assoc :from new)
                                           (= old (:to e))   (-> (assoc :to new) (assoc-in [:evidence :ref-hash] new))))]
                  (if (contains? (:edges ws) (:id e'))
                    ws
                    (-> ws (assoc-in [:objects (:id e')] e') (update :edges conj (:id e')) (update :edges-added conj (:id e')))))
                :derived
                (-> ws (update :edges disj (:id e)) (update :edges-removed conj (:id e)))
                ws))
            ws touching)))

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
        (if (contains? (:edges ws) (:id e))
          (bind-alias ws op (:id e))                       ; idempotent no-op (§3.2): never re-anchors again
          (let [ws' (-> ws
                        (assoc-in [:objects (:id e)] e)
                        (update :edges conj (:id e))
                        (update :edges-added conj (:id e))
                        (bind-alias op (:id e)))]
            ;; §6.1, second re-anchoring trigger: :from is the successor, :to the
            ;; replaced node, and the rules run with the edge already active.
            (if (= :supersedes (:type e))
              (-> ws'
                  (update :superseded conj [(:to e) (:from e)])
                  (re-anchor (:to e) (:from e)))
              ws')))))))

(defn- remove-edge-op [ws op]
  (let [id (r ws (:edge op))]
    (when-not (contains? (:edges ws) id) (plan/reject :ids-exist op (str "unknown or inactive edge " id)))
    (-> ws (update :edges disj id) (update :edges-removed conj id))))

(defn- replace-op [ws op]
  (let [tid (require-tree ws op (r ws (:tree op)))
        path (:at op)
        old (or (tree/node-at (get-in ws [:trees tid]) path) (plan/reject :ids-exist op (str "no position " path)))
        new (require-node ws op (r ws (:new op)))
        sup (edge/make (:host ws) {:type :supersedes :from new :to old :evidence {:actor (:actor (:plan ws))}})]
    (-> ws
        (with-tree op tid #(tree/replace-node % path new))
        (assoc-in [:objects (:id sup)] sup)
        (update :edges conj (:id sup))
        (update :edges-added conj (:id sup))
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
  (when-let [root-hash (get-in (revision store rev-id) [:roots tid])]
    (tree/from-objects root-hash #(get-object store %))))

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
   Only trees whose merkle-root differs are descended."
  [store r1 r2]
  (let [rev1 (revision store r1) rev2 (revision store r2)
        tids (into #{} (concat (keys (:roots rev1)) (keys (:roots rev2))))
        trees (into {}
                    (for [tid tids
                          :when (not= (get-in rev1 [:roots tid]) (get-in rev2 [:roots tid]))]
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
        e1 (set (get-object store (:edges rev1))) e2 (set (get-object store (:edges rev2)))
        superseded (for [eid (remove e1 e2)
                         :let [e (get-object store eid)]
                         :when (= :supersedes (:type e))]
                     [(:to e) (:from e)])]
    {:trees trees
     :edges {:added (vec (sort (remove e1 e2))) :removed (vec (sort (remove e2 e1)))}
     :superseded (vec superseded)}))

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

(defn- edge-set-id [host edges] (canon/digest host (vec (sort edges))))

(defn- tree-set-entries [host trees]
  (vec (sort-by first (map (fn [[tid t]] [tid (tree/descriptor-id host (:descriptor t))]) trees))))

;; ---------------------------------------------------------------- apply

(defn apply-plan
  "Validates and applies `plan` to `store` (§5.1). Returns
   {:store store' :transaction tx :revision rev :revision-id id :aliases {...}
    :edges-added [...] :superseded [[old new] ...] :rebased-from base|nil}
   or raises :plan/rejected (with :check and :op) / :plan/conflict (with
   :conflict-set). All-or-nothing: the input store is never mutated."
  [store plan]
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
        trees' (reduce (fn [ts tid] (update ts tid #(tree/commit host %))) (:trees ws) touched)
        objects (reduce (fn [objs tid] (let [t (trees' tid)]
                                         (-> objs
                                             (merge (tree/objects t))
                                             (assoc (tree/descriptor-id host (:descriptor t)) (:descriptor t)))))
                        (:objects ws) touched)
        edge-set (vec (sort (:edges ws)))
        es-id (edge-set-id host edge-set)
        ts-entries (tree-set-entries host trees')
        ts-id (canon/digest host ts-entries)
        resolved-plan (dissoc plan' :rebased-from)
        tx-id (canon/digest host resolved-plan)
        rev {:roots (into {} (map (fn [[tid t]] [tid (tree/merkle-root t)])) trees')
             :trees ts-id
             :edges es-id
             :parents (if (:head store) [(:head store)] [])
             :tx tx-id
             :actor (:actor plan)
             :engines (or (:engines plan) {})
             :timestamp (:timestamp plan)}
        rev-id (canon/digest host rev)
        tx {:id tx-id :plan resolved-plan :revision rev-id}
        store' (-> store
                   ;; every CAS object satisfies H(canonical-bytes object) == id (invariant 17):
                   ;; the transaction is stored as its resolved plan; the Transaction map lives in the log
                   (assoc :objects (-> objects (assoc es-id edge-set) (assoc ts-id ts-entries) (assoc rev-id rev) (assoc tx-id resolved-plan)))
                   (assoc :trees trees')
                   (assoc :edges (:edges ws))
                   (assoc :head rev-id)
                   (update :heads merge (into {} (map (fn [tid] [tid rev-id])) touched))
                   (assoc-in [:revisions rev-id] rev))]
    {:store store' :transaction tx :revision rev :revision-id rev-id
     :aliases aliases :edges-added (vec (sort (:edges-added ws))) :superseded (:superseded ws)
     :rebased-from (:rebased-from plan')}))
