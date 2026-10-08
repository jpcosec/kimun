(ns kimun.surface.models.store
  "Models as nodes of the store (docs/v2/07 §3): a descriptor is the opaque
   `edn/model` node, placed as a child of the root of the taxonomy tree
   `models` in definition order. Defining is a TransactionPlan; redefining a
   name is a `:replace` of its position, which records `supersedes`. Pure:
   reads an opened store value and builds plans, never commits."
  (:require [clojure.edn :as edn]
            [clojure.walk :as walk]
            [kimun.kernel.err :as err]
            [kimun.kernel.node :as node]
            [kimun.kernel.revision :as revision]
            [kimun.surface.models.descriptor :as descriptor]))

(def tree-name
  "Name of the taxonomy tree that holds the model descriptors."
  "models")

(def blob-format
  "`:format` of the opaque node that carries a descriptor."
  "edn/model")

(def ^:private root-node {:class :sign :kind :block :content {:format :markdown :type :document :attrs {}}})

(defn serialize
  "The canonical text of `descriptor`: EDN with every map sorted by key, so
   equal descriptors are equal strings and therefore the same node."
  [descriptor]
  (pr-str (walk/postwalk #(if (map? %) (into (sorted-map-by (fn [a b] (compare (str a) (str b)))) %) %) descriptor)))

(defn descriptor-node
  "The opaque `edn/model` node spec (`{:class :kind :content}`) of `descriptor`."
  [descriptor]
  {:class :sign :kind :opaque :content {:format blob-format :blob (serialize descriptor)}})

(defn node-id
  "Content-addressed id of the node that carries `descriptor`."
  [host descriptor]
  (let [n (descriptor-node descriptor)] (:id (node/make host (:class n) (:kind n) (:content n)))))

(defn models-tree
  "`[tree-id tree]` of the `models` taxonomy in `store`, or nil when no model
   has been defined yet."
  [store]
  (let [trees (:trees store)]
    (first (keep (fn [tid] (let [t (get trees tid)] (when (= :taxonomy (get-in t [:descriptor :kind])) [tid t])))
                 (revision/trees-named store tree-name)))))

(defn- read-node [store id]
  (let [c (:content (revision/get-object store id))]
    (when (= blob-format (:format c)) (edn/read-string (:blob c)))))

(defn models
  "Every model of `store` in definition order:
   `[{:name :version :id :position :descriptor}]`."
  [store]
  (if-let [[_ t] (models-tree store)]
    (vec (keep-indexed (fn [i pos]
                         (when-let [d (read-node store (:node pos))]
                           {:name (:model/name d) :version (:model/version d) :id (:node pos) :position i :descriptor d}))
                       (:children (:root t))))
    []))

(defn model
  "The current entry (`models` shape) of the model called `name`; raises
   `:models/missing` (exit 1) naming the known models otherwise."
  [store name]
  (or (first (filter #(= name (:name %)) (models store)))
      (err/raise :models/missing (str "no model named `" name "`") {:model name :known (mapv :name (models store))})))

(defn find-model
  "Like `model` but nil when the name is unknown."
  [store name]
  (first (filter #(= name (:name %)) (models store))))

(defn descriptor-by-id
  "The descriptor carried by the `edn/model` node `id`, or nil when the object
   is absent or is not such a node. Superseded descriptors stay in the CAS, so
   a document can read the contract it was extracted under."
  [store id]
  (when (string? id) (read-node store id)))

(defn- same-contract? [a b] (= (dissoc a :model/version) (dissoc b :model/version)))

(defn define-plan
  "The TransactionPlan that defines `descriptor` in `store`, as
   `{:plan p :name n :version v :id node-id :created? bool}`, or
   `{:unchanged true …}` (no plan) when the name already holds the same
   contract. A new name is appended to the `models` tree (created on first
   use) with `:model/version` 1; an existing name gets its version + 1 and a
   `:replace` of its position, which records `supersedes` (07 §7). The
   descriptor's shape is validated first; missing bases are reported by
   `models check`, not rejected here, so models may be defined in any order.
   `:tree-id` is the ULID the `models` tree gets when this plan creates it: the
   caller mints it, because a plan must replay to the same ids."
  [host store descriptor {:keys [actor timestamp tree-id]}]
  (let [current (find-model store (:model/name descriptor))
        version (if current (inc (or (:version current) 1)) 1)
        d (assoc descriptor :model/version version)
        _ (descriptor/validate! d)
        base {:plan/version 1 :base (:head store) :actor actor :engines {"kimun.surface.models" "1"} :timestamp timestamp}
        out {:name (:model/name d) :version version :id (node-id host d) :created? (nil? current)}]
    (cond
      (and current (same-contract? (:descriptor current) d))
      {:unchanged true :name (:name current) :version (:version current) :id (:id current) :created? false}

      current
      (assoc out :plan (assoc base :ops [{:op :add-node :node (descriptor-node d) :as :m}
                                         {:op :replace :tree (first (models-tree store)) :at [(:position current)] :new :m}]))

      (models-tree store)
      (assoc out :plan (assoc base :ops [{:op :add-node :node (descriptor-node d) :as :m}
                                         {:op :add-edge :edge {:type :ownership :tree (first (models-tree store)) :parent []
                                                               :to :m :order (count (models store))}}]))

      :else
      (assoc out :plan (assoc base :ops [{:op :add-node :node root-node :as :root}
                                         {:op :new-tree :tree {:kind :taxonomy :name tree-name} :id tree-id :root :root :as :t}
                                         {:op :add-node :node (descriptor-node d) :as :m}
                                         {:op :add-edge :edge {:type :ownership :tree :t :parent [] :to :m :order 0}}])))))

(defn- lineage
  "Revision ids from the head back to the first one, following first parents."
  [store]
  (loop [id (:head store) acc []]
    (if-let [r (and id (revision/revision store id))]
      (recur (first (:parents r)) (conj acc id))
      acc)))

(defn- first-revision-with [store tid node-id]
  (last (filter (fn [rid] (some #(= node-id (:node %)) (:children (revision/tree-at store rid tid))))
                (lineage store))))

(defn history
  "The versions of model `name`, newest first, following the `supersedes`
   chain of its descriptor node: `[{:version :id :revision :actor :timestamp}]`."
  [store name]
  (let [current (model store name)
        [tid _] (models-tree store)
        older (into {} (keep (fn [eid] (let [e (revision/get-object store eid)]
                                         (when (= :supersedes (:type e)) [(:from e) (:to e)]))))
                    (:edges store))]
    (loop [id (:id current) acc []]
      (if-let [d (and id (read-node store id))]
        (let [rid (first-revision-with store tid id)
              r (revision/revision store rid)]
          (recur (get older id)
                 (conj acc {:version (:model/version d) :id id :revision rid :actor (:actor r) :timestamp (:timestamp r)})))
        acc))))
