(ns kimun.surface.graph.query
  "v1's structured graph query language and its executor. A query is a map
   (typically read from JSON, string keys, snake_case) with three optional
   parts: `scope` (`descendant_of` | `ancestor_of` | `node_id_prefix`),
   `filters` (a node must match all: `{facet, conditions [{field, op, value}]}`
   with ops eq ne is_null is_not_null contains gt lt starts_with) and
   `relations` (edge filters `{relation_types [], direction
   outgoing|incoming|both}`). `parse-query` validates and normalises it to
   keyword keys; `execute` returns node views sorted by id."
  (:require [clojure.string :as str]
            [kimun.kernel.err :as err]
            [kimun.surface.graph.model :as m]))

(def operators
  "The eight comparison operators of a field condition."
  #{"eq" "ne" "is_null" "is_not_null" "contains" "gt" "lt" "starts_with"})

(def directions
  "Valid relation-filter directions."
  #{"outgoing" "incoming" "both"})

(defn- pick
  "Reads `k` (a snake_case string) from `m` under a string, snake keyword or
   kebab keyword key."
  [m k]
  (let [kebab (str/replace k "_" "-")]
    (some (fn [key] (when (contains? m key) (get m key)))
          [k (keyword k) (keyword kebab)])))

(defn- bad
  [msg data]
  (err/raise :graph/bad-query msg data))

(defn- parse-condition
  [c]
  (let [op (pick c "op") field (pick c "field")]
    (when-not (contains? operators op) (bad (str "Unknown operator " (pr-str op)) {:op op}))
    (when-not (string? field) (bad "A condition needs a string `field`" {:condition c}))
    {:field field :op op :value (pick c "value")}))

(defn- parse-filter
  [f]
  (let [facet (pick f "facet")]
    (when-not (string? facet) (bad "A facet filter needs a string `facet`" {:filter f}))
    {:facet facet :conditions (mapv parse-condition (or (pick f "conditions") []))}))

(defn- parse-relation
  [r]
  (let [direction (or (pick r "direction") "both")]
    (when-not (directions direction) (bad (str "Unknown direction " (pr-str direction)) {:direction direction}))
    {:relation-types (vec (or (pick r "relation_types") [])) :direction direction}))

(defn- parse-scope
  [s]
  (when s
    {:descendant-of (pick s "descendant_of") :ancestor-of (pick s "ancestor_of")
     :node-id-prefix (pick s "node_id_prefix")}))

(defn parse-query
  "Validates a query map (any key style) and returns
   `{:filters [...] :relations [...] :scope {...}|nil}`. Raises `:graph/bad-query`."
  [q]
  (when-not (map? q) (bad "A query must be a map" {:query q}))
  {:filters (mapv parse-filter (or (pick q "filters") []))
   :relations (mapv parse-relation (or (pick q "relations") []))
   :scope (parse-scope (pick q "scope"))})

(defn- read-field
  [v field]
  (when (map? v)
    (let [r (get v field (get v (keyword field)))] r)))

(defn- resolve-field
  [v field]
  (cond (nil? v) nil
        (sequential? v) (some #(read-field % field) v)
        :else (read-field v field)))

(defn- ordered
  [actual value pred]
  (and (some? actual)
       (or (and (number? actual) (number? value)) (and (string? actual) (string? value)))
       (pred (compare actual value))))

(defn- condition-matches?
  [v {:keys [field op value]}]
  (let [actual (resolve-field v field)]
    (case op
      "is_null" (nil? actual)
      "is_not_null" (some? actual)
      "eq" (if (and (number? actual) (number? value)) (== actual value) (= actual value))
      "ne" (not (if (and (number? actual) (number? value)) (== actual value) (= actual value)))
      "contains" (and (sequential? actual) (boolean (some #(= value %) actual)))
      "starts_with" (and (string? actual) (string? value) (str/starts-with? actual value))
      "gt" (ordered actual value pos?)
      "lt" (ordered actual value neg?))))

(defn- facet-value
  [g id facet]
  (let [attrs (get-in g [:nodes id])]
    (case facet
      "identity" {:node_id id :node_type (:type attrs)}
      "semantics" (:semantics attrs)
      (get (:facets attrs) facet))))

(defn- node-matches?
  [g id filters]
  (every? (fn [{:keys [facet conditions]}]
            (let [v (facet-value g id facet)]
              (every? #(condition-matches? v %) conditions)))
          filters))

(defn- reach
  [adj side next-key root]
  (loop [queue [root] visited #{root}]
    (if (empty? queue)
      (disj visited root)
      (let [fresh (distinct (remove visited (map next-key (get-in adj [side (first queue)]))))]
        (recur (into (subvec queue 1) fresh) (into visited fresh))))))

(defn- candidate-ids
  [g adj scope]
  (let [{:keys [descendant-of ancestor-of node-id-prefix]} scope]
    (vec (sort (cond
                 (not (str/blank? descendant-of)) (reach adj :out :to descendant-of)
                 (not (str/blank? ancestor-of)) (reach adj :in :from ancestor-of)
                 (not (str/blank? node-id-prefix)) (filter #(str/starts-with? % node-id-prefix) (keys (:nodes g)))
                 :else (keys (:nodes g)))))))

(defn- edge-matches?
  [{:keys [relation-types direction]} edge side]
  (and (or (empty? relation-types) (some #{(:type edge)} relation-types))
       (contains? #{"both" side} direction)))

(defn- filtered-edges
  [adj id relations]
  (if (empty? relations)
    (vec (get-in adj [:out id]))
    (vec (concat (filter (fn [e] (some #(edge-matches? % e "outgoing") relations)) (get-in adj [:out id]))
                 (filter (fn [e] (some #(edge-matches? % e "incoming") relations)) (get-in adj [:in id]))))))

(defn execute
  "Runs `query` (raw or `parse-query` output; both parse idempotently on string
   or keyword keys) over graph `g`. Returns node views
   `{:id :type :semantics :facets :edges}` sorted by id; `:edges` are the node's
   edges after relation filtering (outgoing only when no relation filter)."
  [g query]
  (let [{:keys [filters relations scope]} (parse-query query)
        adj (m/adjacency g)]
    (vec (for [id (candidate-ids g adj scope)
               :when (m/has-node? g id)
               :when (node-matches? g id filters)
               :let [attrs (get-in g [:nodes id])]]
           {:id id :type (:type attrs) :semantics (or (:semantics attrs) {})
            :facets (or (:facets attrs) {}) :edges (filtered-edges adj id relations)}))))
