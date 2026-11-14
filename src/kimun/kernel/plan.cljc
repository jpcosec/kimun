(ns kimun.kernel.plan
  "TransactionPlan: schema, alias resolution and the seven validation checks
   (docs/v2/02 §5.1). A plan is pure EDN data; it applies entirely or not at all.

   {:plan/version 1 :base <rev|nil> :actor <str> :engines {} :timestamp <ISO ms UTC>
    :ops [{:op :new-tree    :tree {:kind :document :name \"…\"} :id <ulid|absent> :root <id|alias> :as :t1}
          {:op :add-node    :node {:class :kind :content} :as :n1}
          {:op :register-tree :tree {:kind :document :name \"…\" :class <id> :content-hash <hex> :cached? true}
                             :id <ulid> :root <id|alias> :merkle-root <hex> :as :t1}
          {:op :add-edge    :edge {:type :ownership :tree t :parent [path] :to <id|alias> :order n}}
          {:op :add-edge    :edge {:type :binding :from … :to … :evidence {…}} :as :e1}
          {:op :remove-edge :edge <edge-id|alias>}
          {:op :replace     :tree <tree-id|alias> :at [path] :new <id|alias>}
          {:op :move        :tree <tree-id|alias> :from [path] :to [parent-path] :order <n>}
          {:op :detach      :tree <tree-id|alias> :at [path]}]}

   Ownership is addressed by POSITION paths (docs/v2/02 §3.1): a node may occur
   several times in a tree. Aliases are keywords declared with :as and may be
   used wherever an id is expected in later ops. Validation checks by name:
   base-cas, ids-exist, tree-integrity, evidence-ref-hash, capability,
   opaque-replace-only, pure-data."
  (:require [clojure.string :as str]
            [kimun.kernel.canon :as canon]))

(def ops
  "The primitive operations (docs/v2/02 §5). `:register-tree` is the format-4
   registration of a tree whose structure is cache, not store (docs/v2/14
   §18.6): it records the descriptor and the merkle root the surface computed,
   without persisting positions, tree objects or block/text nodes."
  #{:new-tree :register-tree :remove-route :add-node :add-edge :remove-edge :replace :move :detach})

(def ^:private timestamp-re #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$")

(defn reject
  "Rejects the whole plan: ex-info of type :plan/rejected with the failing check and op."
  [check op why]
  (throw (ex-info (str "plan rejected [" (name check) "]: " why)
                  {:type :plan/rejected :check check :op op :why why})))

;; ---------------------------------------------------------------- pure-data (check 7) + schema

(defn- pure? [v]
  (cond
    (or (nil? v) (string? v) (keyword? v) (symbol? v) (boolean? v) (integer? v)) true
    (map? v) (every? (fn [[k x]] (and (pure? k) (pure? x))) v)
    (or (vector? v) (set? v) (list? v) (seq? v)) (every? pure? v)
    :else false))

(defn check-schema
  "Structural schema of the plan; also check 7 (pure-data)."
  [plan]
  (when-not (map? plan) (reject :pure-data nil "plan must be a map"))
  (when-not (pure? plan) (reject :pure-data nil "plan contains a value that is not pure EDN data"))
  (when-not (= 1 (:plan/version plan)) (reject :pure-data nil "unsupported :plan/version"))
  (when-not (string? (:actor plan)) (reject :pure-data nil ":actor must be a string"))
  (when-not (or (nil? (:base plan)) (string? (:base plan))) (reject :pure-data nil ":base must be a revision id or nil"))
  (when-not (map? (or (:engines plan) {})) (reject :pure-data nil ":engines must be a map"))
  (when-not (and (string? (:timestamp plan)) (re-matches timestamp-re (:timestamp plan)))
    (reject :pure-data nil ":timestamp must be YYYY-MM-DDTHH:MM:SS.mmmZ"))
  (when-not (vector? (:ops plan)) (reject :pure-data nil ":ops must be a vector"))
  (doseq [op (:ops plan)]
    (when-not (contains? ops (:op op)) (reject :pure-data op (str "unknown op " (:op op))))
    (when (and (contains? op :as) (not (keyword? (:as op)))) (reject :pure-data op ":as must be a keyword")))
  plan)

;; ---------------------------------------------------------------- aliases

(defn alias?
  "True for a plan alias (a keyword declared with :as)."
  [x] (keyword? x))

(defn resolve-ref
  "Substitutes an alias by its resolved id; ids pass through."
  [aliases x]
  (if (alias? x)
    (if-some [id (get aliases x)] id (reject :ids-exist nil (str "unknown alias " x)))
    x))

(defn substitute-aliases
  "Replaces declared aliases in map VALUES and vector elements (never map keys)."
  [aliases v]
  (cond
    (map? v)     (into {} (map (fn [[k x]] [k (substitute-aliases aliases x)])) v)
    (vector? v)  (mapv #(substitute-aliases aliases %) v)
    (and (alias? v) (contains? aliases v)) (aliases v)
    :else v))

(defn touched-trees
  "Tree ids (or aliases) of the trees a plan creates or changes by ownership."
  [plan]
  (into #{} (keep (fn [{:keys [op] :as o}]
                    (case op
                      :new-tree (or (:id o) (:as o))
                      :register-tree (or (:id o) (:as o))
                      (:replace :move :detach) (:tree o)
                      :add-edge (when (= :ownership (get-in o [:edge :type])) (get-in o [:edge :tree]))
                      :remove-edge nil
                      nil)))
        (:ops plan)))

(defn compose-plans
  "The plan that applies `plans` in order as ONE transaction: ops concatenated
   with each plan's aliases renamed so they stay distinct, and `opts`
   (`:base :actor :timestamp :engines`) defaulting to the first plan's. The
   plans must be independent of one another (each computed against the store the
   previous ones describe): composition is how `include build` writes a whole
   build in one revision (docs/v2/14 §10). Returns nil when `plans` is empty."
  [plans opts]
  (let [plans (vec (remove nil? plans))]
    (when (seq plans)
      (let [renamed (map-indexed
                     (fn [i p]
                       (let [m (into {} (keep (fn [op] (when-let [a (:as op)]
                                                        [a (keyword (str "p" i "-" (name a)))]))
                                             (:ops p)))]
                         (substitute-aliases m p)))
                     plans)]
        {:plan/version 1
         :base (if (contains? opts :base) (:base opts) (:base (first plans)))
         :actor (or (:actor opts) (:actor (first plans)))
         :timestamp (or (:timestamp opts) (:timestamp (first plans)))
         :engines (or (:engines opts) (reduce merge {} (map :engines plans)))
         :ops (vec (mapcat :ops renamed))}))))

;; ---------------------------------------------------------------- capability (check 5)

(defn- op-tree [op]
  (case (:op op)
    (:new-tree :register-tree) (:id op)
    (:replace :move :detach :remove-route) (:tree op)
    (:add-edge :remove-edge) (when (= :ownership (get-in op [:edge :type])) (get-in op [:edge :tree]))
    nil))

(defn capability-ok?
  "capabilities: {actor → :all | #{{:op x} {:op x :tree t} ...}}."
  [capabilities actor op resolved-tree]
  (let [caps (get capabilities actor)]
    (cond
      (nil? caps) false
      (= :all caps) true
      :else (boolean (some (fn [{o :op t :tree}]
                             (and (= o (:op op))
                                  (or (nil? t) (= t resolved-tree))))
                           caps)))))

(defn check-capabilities
  "Check 5: every op is authorized for the plan actor (per tree when the op carries one)."
  [capabilities plan aliases]
  (doseq [op (:ops plan)]
    (let [t (some->> (op-tree op) (resolve-ref aliases))]
      (when-not (capability-ok? capabilities (:actor plan) op t)
        (reject :capability op (str "actor " (:actor plan) " lacks capability for " (:op op)
                                    (when t (str " on tree " t)))))))
  plan)

;; ---------------------------------------------------------------- opaque (check 6)

(defn check-opaque-replace-only
  "Nodes are immutable, so the only way to change an opaque node is a whole
   :replace; any op that tries to edit content in place is rejected."
  [plan]
  (doseq [op (:ops plan)]
    (when (or (contains? op :patch) (contains? op :content) (= :edit-node (:op op)))
      (reject :opaque-replace-only op "in-place content edits are not admitted")))
  plan)

(defn canonical
  "The plan with every string NFC-normalized (what gets hashed as the transaction)."
  [host plan]
  (canon/normalize host plan))
