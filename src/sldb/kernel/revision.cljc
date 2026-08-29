(ns sldb.kernel.revision
  "Applying a TransactionPlan to an in-memory store and producing an immutable
   revision (docs/v2/02 §5, §5.1, §5.2).

   Store (a value):
   {:hasher h :capabilities {...}
    :objects {id object}            ; the CAS: nodes, edges, tree objects, descriptors, edge-sets, tree-sets, revisions
    :trees {tree-id committed-tree}  ; current tree values (sldb.kernel.tree)
    :edges #{edge-id}               ; active edge set at :head
    :head <rev-id|nil>              ; latest revision
    :heads {tree-id rev-id}         ; last revision that touched each tree
    :revisions {rev-id Revision}}

   Revision {:roots {tree-id merkle-root} :trees tree-set-id :edges edge-set-id
             :parents [rev-id] :tx tx-id :actor :engines :timestamp}
   revision-id = H(canonical-bytes Revision); Transaction {:id H(resolved plan) :plan :revision}."
  (:require [clojure.walk :as walk]
            [sldb.kernel.canon :as canon]
            [sldb.kernel.node :as node]
            [sldb.kernel.edge :as edge]
            [sldb.kernel.tree :as tree]
            [sldb.kernel.plan :as plan]
            [sldb.host.ulid :as ulid]))

;; ---------------------------------------------------------------- store

(defn empty-store
  [hasher capabilities]
  {:hasher hasher :capabilities capabilities
   :objects {} :trees {} :edges #{} :head nil :heads {} :revisions {}})

(defn- put-object [store id obj] (assoc-in store [:objects id] obj))

(defn get-object [store id] (get-in store [:objects id]))

(defn revision [store rev-id] (get-in store [:revisions rev-id]))

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

(defn- ws-init [store]
  {:store store
   :aliases {}
   :nodes-added #{}
   :edges-added #{}
   :edges-removed #{}
   :trees (:trees store)
   :touched #{}
   :superseded []
   :edges (:edges store)
   :objects (:objects store)})

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

(defn- add-node-op [ws op]
  (let [{:keys [class kind content]} (:node op)
        n (try (node/make (get-in ws [:store :hasher]) class kind content)
               (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e
                 (plan/reject :ids-exist op (str "invalid node: " (ex-message e)))))]
    (-> ws
        (assoc-in [:objects (:id n)] n)
        (update :nodes-added conj (:id n))
        (bind-alias op (:id n)))))

(defn- new-tree-op [ws op]
  (let [id (or (:id op) (ulid/ulid))
        root (require-node ws op (r ws (:root op)))
        {:keys [kind name]} (:tree op)
        t (try (tree/create (get-in ws [:store :hasher]) kind (or name "") root id)
               (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e
                 (plan/reject :tree-integrity op (ex-message e))))]
    (when (contains? (:trees ws) id) (plan/reject :ids-exist op (str "tree already exists " id)))
    (-> ws (assoc-in [:trees id] t) (update :touched conj id) (bind-alias op id))))

(defn- require-tree [ws op tid]
  (when-not (contains? (:trees ws) tid) (plan/reject :ids-exist op (str "unknown tree " tid)))
  tid)

(defn- with-tree [ws op tid f]
  (try (-> ws (update-in [:trees tid] f) (update :touched conj tid))
       (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e
         (plan/reject :tree-integrity op (ex-message e)))))

(defn- add-edge-op [ws op]
  (let [raw (:edge op)
        e0 (-> raw
               (update :from #(r ws %)) (update :to #(r ws %))
               (cond-> (contains? raw :tree) (update :tree #(r ws %)))
               (cond-> (get-in raw [:evidence :ref-hash]) (update-in [:evidence :ref-hash] #(r ws %)))
               (cond-> (get-in raw [:evidence :context]) (update-in [:evidence :context] #(r ws %))))]
    (require-node ws op (:from e0)) (require-node ws op (:to e0))
    (if (= :ownership (:type e0))
      (let [tid (require-tree ws op (:tree e0))
            e (try (edge/make (get-in ws [:store :hasher]) e0)
                   (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) x (plan/reject :evidence-ref-hash op (ex-message x))))]
        (-> ws
            (with-tree op tid #(tree/add-child % (:from e) (:to e) (:order e)))
            (bind-alias op (:id e))))
      (let [e (try (edge/make (get-in ws [:store :hasher]) e0)
                   (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) x (plan/reject :evidence-ref-hash op (ex-message x))))]
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
    (when (= :ownership (:type (get-object {:objects (:objects ws)} id))) (plan/reject :ids-exist op "ownership edges are removed through the tree, not by id"))
    (-> ws (update :edges disj id) (update :edges-removed conj id))))

(defn- re-anchor
  "§6.1 inside the transaction: reference/binding edges pointing to `old` get a
   successor edge pointing to `new`; semantic/projection/derived are left as they are."
  [ws op old new]
  (let [h (get-in ws [:store :hasher])
        followers (for [eid (:edges ws)
                        :let [e (get-in ws [:objects eid])]
                        :when (and (or (= old (:to e)) (= old (:from e)))
                                   (contains? #{:reference :binding} (:type e)))]
                    e)]
    (reduce (fn [ws e]
              (let [e' (edge/make h (cond-> (dissoc e :id)
                                      (= old (:from e)) (assoc :from new)
                                      (= old (:to e))   (-> (assoc :to new) (assoc-in [:evidence :ref-hash] new))))]
                (if (contains? (:edges ws) (:id e'))
                  ws
                  (-> ws (assoc-in [:objects (:id e')] e') (update :edges conj (:id e')) (update :edges-added conj (:id e'))))))
            ws followers)))

(defn- replace-op [ws op]
  (let [tid (require-tree ws op (r ws (:tree op)))
        old (require-node ws op (r ws (:old op)))
        new (require-node ws op (r ws (:new op)))
        h (get-in ws [:store :hasher])
        sup (edge/make h {:type :supersedes :from new :to old :evidence {:actor (:actor (:plan ws))}})]
    (-> ws
        (with-tree op tid #(tree/replace-node % old new))
        (assoc-in [:objects (:id sup)] sup)
        (update :edges conj (:id sup))
        (update :edges-added conj (:id sup))
        (update :superseded conj [old new])
        (re-anchor op old new))))

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

;; ---------------------------------------------------------------- base-cas (check 1) and conflicts

(defn- tree-changed-since?
  "True when tree `tid` was touched by a revision that is not an ancestor of `base`."
  [store base tid]
  (let [last (get-in store [:heads tid])]
    (and last (not (contains? (ancestors store base) last)))))

(declare diff)

(defn- conflicts-for
  "ConflictSet entries between the plan and the changes base→head on tree `tid`."
  [store base plan tid resolved-ops]
  (let [head-rev (:head store)
        d (get-in (diff store base head-rev) [:trees tid])
        changed-parents (set (concat (map :to-parent (:moved d)) (map :from-parent (:moved d))
                                     (:parents-changed d)))
        removed (set (:removed d))
        superseded (set (map first (:superseded (diff store base head-rev))))
        refs (fn [op] (remove nil? [(:old op) (:new op) (:node op) (:parent op) (get-in op [:edge :from]) (get-in op [:edge :to])]))]
    (for [op resolved-ops
          :when (= tid (some-> (plan/touched-trees {:ops [op]}) first))
          ref (refs op)
          :let [kind (cond (contains? removed ref) :removed-target
                           (contains? superseded ref) :superseded-target
                           (contains? changed-parents ref) :same-parent-edit)]
          :when kind]
      {:tree tid :node ref :kind kind})))

(defn- check-base [store plan resolved-ops touched]
  (let [base (:base plan) head (:head store)]
    (cond
      (and (nil? base) (nil? head)) plan
      (nil? base) (plan/reject :base-cas nil "store is not empty; :base is required")
      (not (contains? (:revisions store) base)) (plan/reject :base-cas nil (str "unknown base revision " base))
      (= base head) plan
      :else
      (let [changed (filter #(tree-changed-since? store base %) touched)
            cs (mapcat #(conflicts-for store base plan % resolved-ops) changed)]
        (if (seq cs)
          (throw (ex-info "conflict" {:type :plan/conflict
                                      :conflict-set {:base base :head head :plan plan :conflicts (vec cs)}}))
          (assoc plan :base head :rebased-from base))))))

;; ---------------------------------------------------------------- objects of a revision

(defn- edge-set-id [h edges] (canon/digest h (vec (sort edges))))
(defn- tree-set-entries [h trees] (vec (sort-by first (map (fn [[tid t]] [tid (tree/descriptor-id h (:descriptor t))]) trees))))

(defn- store-tree-objects [objects h t]
  (reduce (fn [objs [n obj]] (assoc objs (get-in t [:objects n]) obj)) objects (tree/objects t)))

;; ---------------------------------------------------------------- apply

(defn apply-plan
  "Validates and applies `plan` to `store`. Returns {:store store' :transaction tx
   :revision rev :aliases {...} :edges-added [...]} or throws :plan/rejected /
   :plan/conflict. All-or-nothing: the input store is never mutated."
  [store plan]
  (let [h (:hasher store)
        plan (plan/canonical (plan/check-schema plan))
        _ (plan/check-opaque-replace-only plan)
        ws0 (assoc (ws-init store) :plan plan)
        ;; execute ops on the working state (checks 2, 3-partial, 4 raised inline)
        ws (reduce apply-op ws0 (:ops plan))
        aliases (:aliases ws)
        resolved-ops (mapv (fn [op] (walk/postwalk (fn [v] (if (and (keyword? v) (contains? aliases v)) (aliases v) v))
                                                   (dissoc op :as)))
                           (:ops plan))
        touched (:touched ws)
        _ (plan/check-capabilities (:capabilities store) (assoc plan :ops resolved-ops) aliases)
        plan' (check-base store (assoc plan :ops resolved-ops) resolved-ops touched)
        ;; check 3: every touched tree is a tree
        _ (doseq [tid touched]
            (when-not (tree/valid? (get-in ws [:trees tid]))
              (plan/reject :tree-integrity nil (str "tree " tid " is not a tree after applying the plan"))))
        trees' (reduce (fn [ts tid] (update ts tid #(tree/commit h %))) (:trees ws) touched)
        objects (reduce (fn [objs tid] (let [t (trees' tid)]
                                         (-> objs
                                             (store-tree-objects h t)
                                             (assoc (tree/descriptor-id h (:descriptor t)) (:descriptor t)))))
                        (:objects ws) touched)
        edge-set (vec (sort (:edges ws)))
        es-id (edge-set-id h edge-set)
        ts-entries (tree-set-entries h trees')
        ts-id (canon/digest h ts-entries)
        resolved-plan (dissoc plan' :rebased-from)
        tx-id (canon/digest h resolved-plan)
        rev {:roots (into {} (map (fn [[tid t]] [tid (tree/merkle-root t)])) trees')
             :trees ts-id
             :edges es-id
             :parents (if (:head store) [(:head store)] [])
             :tx tx-id
             :actor (:actor plan)
             :engines (or (:engines plan) {})
             :timestamp (:timestamp plan)}
        rev-id (canon/digest h rev)
        tx {:id tx-id :plan resolved-plan :revision rev-id}
        store' (-> store
                   (assoc :objects (-> objects (assoc es-id edge-set) (assoc ts-id ts-entries) (assoc rev-id rev) (assoc tx-id tx)))
                   (assoc :trees trees')
                   (assoc :edges (:edges ws))
                   (assoc :head rev-id)
                   (update :heads merge (into {} (map (fn [tid] [tid rev-id])) touched))
                   (assoc-in [:revisions rev-id] rev))]
    {:store store' :transaction tx :revision rev :revision-id rev-id
     :aliases aliases :edges-added (vec (sort (:edges-added ws))) :superseded (:superseded ws)
     :rebased-from (:rebased-from plan')}))

;; ---------------------------------------------------------------- diff (§5.2)

(defn- index-of [v x]
  (first (keep-indexed (fn [i y] (when (= x y) i)) v)))

(defn- tree-at
  "Reconstructs {:children {node [child…]}} for tree `tid` at revision `rev-id`
   from the tree objects in the CAS."
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
  "Difference between two revisions (docs/v2/02 §5.2)."
  [store r1 r2]
  (let [rev1 (revision store r1) rev2 (revision store r2)
        h (:hasher store)
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
