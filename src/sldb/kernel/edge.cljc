(ns sldb.kernel.edge
  "Edges and evidence (docs/v2/02 §3, §3.2).

   Edge     {:type t :from id :to id :tree tree-id|absent :order n|absent :evidence Evidence}
   Evidence {:ref-hash id, exactly one origin (:actor | :engine + :version),
             :context node-id, :status kw}
   id(edge) = H(canonical-bytes edge). The timestamp is never part of an edge
   (invariant 16): it belongs to the transaction. Ownership edges are
   represented by tree objects (sldb.kernel.tree); their shape exists here so
   plans can express them."
  (:require [sldb.kernel.canon :as canon]
            [sldb.kernel.err :as err]))

(def types
  "The seven edge types."
  #{:ownership :supersedes :reference :binding :projection :semantic :derived})

(def statuses
  "Sense statuses a projection may carry."
  #{:sinnvoll :sinnlos :unsinnig})

(def required-evidence
  "type → mandatory Evidence keys (docs/v2/02 §3.2); :origin means exactly one
   of :actor or :engine(+:version)."
  {:ownership  #{}
   :supersedes #{:actor}
   :reference  #{:ref-hash :origin}
   :binding    #{:ref-hash :origin :context}
   :projection #{:ref-hash :origin :context :status}
   :semantic   #{:ref-hash :origin :context}
   :derived    #{:ref-hash :engine :version}})

(defn- fail [edge why]
  (err/raise :edge/invalid (str "invalid edge: " why) {:edge edge :why why}))

(defn- origin-ok? [{:keys [actor engine version]}]
  (or (and (string? actor) (nil? engine) (nil? version))
      (and (nil? actor) (string? engine) (string? version))))

(defn validate
  "Returns the edge when its shape and mandatory evidence are admitted; raises
   :edge/invalid otherwise."
  [host {:keys [type from to tree order evidence] :as edge}]
  (when-not (contains? types type) (fail edge (str "unknown type " type)))
  (when-not (and (string? from) (string? to)) (fail edge ":from and :to must be node ids"))
  (when (contains? edge :timestamp) (fail edge "timestamp is not part of an edge (invariant 16)"))
  (if (= type :ownership)
    (do (when-not (string? tree) (fail edge "ownership edges carry :tree"))
        (when-not (and (integer? order) (<= 0 order)) (fail edge "ownership edges carry a non-negative :order")))
    (do (when (or (contains? edge :tree) (contains? edge :order))
          (fail edge "only ownership edges carry :tree/:order"))
        (let [ev (or evidence {})
              req (required-evidence type)]
          (when-not (map? ev) (fail edge "evidence must be a map"))
          (when (and (contains? req :origin) (not (origin-ok? ev)))
            (fail edge "evidence needs exactly one origin: :actor or :engine+:version"))
          (doseq [k (disj req :origin)]
            (when-not (contains? ev k) (fail edge (str "evidence missing " k))))
          (when (and (contains? ev :status) (not (contains? statuses (:status ev))))
            (fail edge "evidence :status must be sinnvoll/sinnlos/unsinnig"))
          (when-not (canon/valid? host ev) (fail edge "evidence contains a value not admitted in canonical content")))))
  edge)

(defn identity-form
  "The exact map that is hashed: present keys among :type :from :to :tree :order :evidence."
  [edge]
  (into {} (filter (fn [[k v]] (and (contains? #{:type :from :to :tree :order :evidence} k) (some? v)))) edge))

(defn edge-id
  "id(edge) = H(canonical-bytes (identity-form edge))."
  [host edge]
  (canon/digest host (identity-form edge)))

(defn make
  "Builds a validated, normalized edge with its :id."
  [host edge]
  (let [e (identity-form (validate host (canon/normalize host edge)))]
    (assoc e :id (edge-id host e))))
