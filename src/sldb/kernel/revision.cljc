(ns sldb.kernel.revision
  "Applying a TransactionPlan to an in-memory store and producing an immutable
   revision (docs/v2/02 §5, §5.1, §5.2).

   Store (a value):
   {:host host :capabilities {...}
    :objects {id object}            ; the CAS: nodes, edges, tree objects, descriptors, edge-sets, tree-sets, revisions, resolved plans
    :trees {tree-id committed-tree}  ; current tree values (sldb.kernel.tree)
    :edges #{edge-id}               ; active edge set at :head
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
      (update :from #(r ws %)) (update :to #(r ws %))
      (cond-> (contains? raw :tree) (update :tree #(r ws %)))
      (cond-> (get-in raw [:evidence :ref-hash]) (update-in [:evidence :ref-hash] #(r ws %)))
      (cond-> (get-in raw [:evidence :context]) (update-in [:evidence :context] #(r ws %)))))

(defn- add-edge-op [ws op]
  (let [e0 (resolve-edge ws (:edge op))]
    (require-node ws op (:from e0)) (require-node ws op (:to e0))
    (if (= :ownership (:type e0))
      (let [tid (require-tree ws op (:tree e0))
            e (rejecting :evidence-ref-hash op #(edge/make (:host ws) e0))]
        (-> ws
            (with-tree op tid #(tree/add-child % (:from e) (:to e) (:order e)))
            (bind-alias op (:id e))))
      (let [e (rejecting :evidence-ref-hash op #(edge/make (:host ws) e0))]
        (when (and (get-in e [:evidence :ref-hash]) (not= (get-in e [:evidence :ref-hash]) (:to e)))
          (plan/reject :evidence-ref-hash op ":ref-hash must equal the id of :to in the plan-resolved state"))
        (when-let [ctx (get-in e [:evidence :context])] (require-node ws op ctx))
        (if (contains? (:edges ws) (:id e))
          (bind-alias ws op (:id e))                       ; idempotent no-op
          (-> ws
              (assoc-in [:objects (:id e)] e)
              (update :edges conj (:id e))
              (update :edges-added conj (:id e))
              (bind-alias op (:id e))))))))

(defn- remove-edge-op [ws op]
  (let [id (r ws (:edge op))]
    (when-not (contains? (:edges ws) id) (plan/reject :ids-exist op (str "unknown or inactive edge " id)))
    (when (= :ownership (:type (get-in ws [:objects id]))) (plan/reject :ids-exist op "ownership edges are removed through the tree, not by id"))
    (-> ws (update :edges disj id) (update :edges-removed conj id))))

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

(defn- replace-op [ws op]
  (let [tid (require-tree ws op (r ws (:tree op)))
        old (require-node ws op (r ws (:old op)))
        new (require-node ws op (r ws (:new op)))
        sup (edge/make (:host ws) {:type :supersedes :from new :to old :evidence {:actor (:actor (:plan ws))}})]
    (-> ws
        (with-tree op tid #(tree/replace-node % old new))
        (assoc-in [:objects (:id sup)] sup)
        (update :edges conj (:id sup))
        (update :edges-added conj (:id sup))
        (update :superseded conj [old new])
        (re-anchor old new))))

(defn- move-op [ws op]
  (let [tid (require-tree ws op (r ws (:tree op)))
        n (require-node ws op (r ws (:node op)))
        p (require-node ws op (r ws (:parent op)))]
    (with-tree ws op tid #(tree/move % n p (:order op)))))

(defn- apply-op [ws op]
  (case (:op op)
    :new-tree    (new-tree-op ws op)
    :add-node    (add-node-op ws op)
    :add-edge    (add-edge-op ws op)
    :remove-edge (remove-edge-op ws op)
    :replace     (replace-op ws op)
    :move        (move-op ws op)))

;; ---------------------------------------------------------------- diff (§5.2)

(defn- index-of [v x]
  (first (keep-indexed (fn [i y] (when (= x y) i)) v)))

(defn- tree-at
  "Reconstructs {:children {node [child…]} :parent {child parent}} for tree `tid`
   at revision `rev-id` from the tree objects in the CAS."
  [store rev-id tid]
  (let [root-hash (get-in (revision store rev-id) [:roots tid])]
    (when root-hash
      (loop [todo [root-hash] children {} parent {}]
        (if (empty? todo)
          {:children children :parent parent}
          (let [[hsh & more] todo
                {:keys [node] kids :children} (get-object store hsh)]
            (recur (into more (map second kids))
                   (assoc children node (mapv first kids))
                   (reduce (fn [p [c _]] (assoc p c node)) parent kids))))))))

(defn diff
  "Difference between two revisions (docs/v2/02 §5.2):
   {:trees {tree-id {:added :removed :moved :parents-changed}}
    :edges {:added :removed} :superseded [[old new] ...]}.
   Only trees whose merkle-root differs are descended."
  [store r1 r2]
  (let [rev1 (revision store r1) rev2 (revision store r2)
        tids (into #{} (concat (keys (:roots rev1)) (keys (:roots rev2))))
        trees (into {}
                    (for [tid tids
                          :when (not= (get-in rev1 [:roots tid]) (get-in rev2 [:roots tid]))]
                      (let [a (or (tree-at store r1 tid) {:children {} :parent {}})
                            b (or (tree-at store r2 tid) {:children {} :parent {}})
                            na (set (keys (:children a))) nb (set (keys (:children b)))
                            moved (for [n (filter nb na)
                                        :let [pa (get-in a [:parent n]) pb (get-in b [:parent n])]
                                        :when (not= pa pb)]
                                    {:node n :from-parent pa :to-parent pb
                                     :order (index-of (get-in b [:children pb]) n)})
                            parents-changed (for [n (filter nb na)
                                                  :when (not= (get-in a [:children n]) (get-in b [:children n]))]
                                              n)]
                        [tid {:added (vec (sort (remove na nb)))
                              :removed (vec (sort (remove nb na)))
                              :moved (vec moved)
                              :parents-changed (vec (sort parents-changed))}])))
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

(defn- op-refs [op]
  (remove nil? [(:old op) (:new op) (:node op) (:parent op) (get-in op [:edge :from]) (get-in op [:edge :to])]))

(defn- conflicts-for
  "ConflictSet entries between the plan and the changes base→head on tree `tid`."
  [store base tid resolved-ops]
  (let [head-rev (:head store)
        full (diff store base head-rev)
        d (get-in full [:trees tid])
        changed-parents (set (concat (map :to-parent (:moved d)) (map :from-parent (:moved d)) (:parents-changed d)))
        removed (set (:removed d))
        superseded (set (map first (:superseded full)))]
    (for [op resolved-ops
          :when (= tid (first (plan/touched-trees {:ops [op]})))
          ref (op-refs op)
          :let [kind (cond (contains? removed ref) :removed-target
                           (contains? superseded ref) :superseded-target
                           (contains? changed-parents ref) :same-parent-edit)]
          :when kind]
      {:tree tid :node ref :kind kind})))

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
                                      :conflict-set {:base base :head head :plan plan :conflicts (vec cs)}}))
          (assoc plan :base head :rebased-from base))))))

;; ---------------------------------------------------------------- objects of a revision

(defn- edge-set-id [host edges] (canon/digest host (vec (sort edges))))

(defn- tree-set-entries [host trees]
  (vec (sort-by first (map (fn [[tid t]] [tid (tree/descriptor-id host (:descriptor t))]) trees))))

(defn- store-tree-objects [objects t]
  (reduce (fn [objs [n obj]] (assoc objs (get-in t [:objects n]) obj)) objects (tree/objects t)))

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
        ws (reduce apply-op (ws-init store plan) (:ops plan))
        aliases (:aliases ws)
        resolved-ops (mapv (fn [op] (plan/substitute-aliases aliases (dissoc op :as))) (:ops plan))
        touched (:touched ws)
        _ (plan/check-capabilities (:capabilities store) (assoc plan :ops resolved-ops) aliases)
        plan' (check-base store (assoc plan :ops resolved-ops) resolved-ops touched)
        _ (doseq [tid touched]
            (when-not (tree/valid? (get-in ws [:trees tid]))
              (plan/reject :tree-integrity nil (str "tree " tid " is not a tree after applying the plan"))))
        trees' (reduce (fn [ts tid] (update ts tid #(tree/commit host %))) (:trees ws) touched)
        objects (reduce (fn [objs tid] (let [t (trees' tid)]
                                         (-> objs
                                             (store-tree-objects t)
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
