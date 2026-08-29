(ns sldb.kernel.plan
  "TransactionPlan: schema, alias resolution and the seven validation checks
   (docs/v2/02 §5.1). A plan is pure EDN data; it applies entirely or not at all.

   {:plan/version 1 :base <rev|nil> :actor <str> :engines {} :timestamp <ISO ms UTC>
    :ops [{:op :new-tree    :tree {:kind :document :name \"…\"} :id <ulid|absent> :root <id|alias> :as :t1}
          {:op :add-node    :node {:class :kind :content} :as :n1}
          {:op :add-edge    :edge {…} :as :e1}
          {:op :remove-edge :edge <edge-id|alias>}
          {:op :replace     :tree <tree-id|alias> :old <id> :new <id|alias>}
          {:op :move        :tree <tree-id|alias> :node <id> :parent <id> :order <n>}]}

   Aliases are keywords declared with :as and may be used wherever an id is
   expected in later ops. Validation checks by name: base-cas, ids-exist,
   tree-integrity, evidence-ref-hash, capability, opaque-replace-only, pure-data."
  (:require [clojure.string :as str]
            [sldb.kernel.canon :as canon]))

(def ops #{:new-tree :add-node :add-edge :remove-edge :replace :move})

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

(defn alias? [x] (keyword? x))

(defn resolve-ref
  "Substitutes an alias by its resolved id; ids pass through."
  [aliases x]
  (if (alias? x)
    (if-some [id (get aliases x)] id (reject :ids-exist nil (str "unknown alias " x)))
    x))

(defn touched-trees
  "Tree ids (or aliases) of the trees a plan creates or changes by ownership."
  [plan]
  (into #{} (keep (fn [{:keys [op] :as o}]
                    (case op
                      :new-tree (or (:id o) (:as o))
                      (:replace :move) (:tree o)
                      :add-edge (when (= :ownership (get-in o [:edge :type])) (get-in o [:edge :tree]))
                      :remove-edge nil
                      nil)))
        (:ops plan)))

;; ---------------------------------------------------------------- capability (check 5)

(defn- tree-op? [op] (contains? #{:new-tree :replace :move} (:op op)))

(defn- op-tree [op]
  (case (:op op)
    :new-tree (:id op)
    (:replace :move) (:tree op)
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

(defn check-capabilities [capabilities plan aliases]
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
  [plan]
  (canon/normalize plan))
