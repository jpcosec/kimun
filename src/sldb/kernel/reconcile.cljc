(ns sldb.kernel.reconcile
  "Reconciliation of drifted anchors (docs/v2/02 §6.5).

   Takes the orphan endpoints of a revision and proposes what replaced them.
   A proposal is neither node nor edge: it lives outside the pool until an
   actor accepts it, and accepting it is an ordinary transaction that records
   a `supersedes` edge whose only evidence is that actor (invariant 18)."
  (:require [sldb.kernel.anchor :as anchor]
            [sldb.kernel.err :as err]
            [sldb.kernel.ports :as ports]
            [sldb.kernel.revision :as revision]
            [sldb.kernel.tree :as tree]))

(def default-min-confidence
  "Confidence below which a `:sample` candidate is not proposed (docs/v2/02 §6.5)."
  0.6)

;; ---------------------------------------------------------------- dice (§6.5)

(defn- trigrams
  "Multiset of grapheme trigrams of `s` in NFC (docs/v2/02 §6.5); a text shorter
   than three graphemes contributes one gram, the whole text."
  [host s]
  (let [t (ports/nfc (ports/text host) s)
        g (ports/graphemes (ports/segmenter host) t)]
    (frequencies (if (< (count g) 3) [t] (map #(apply str %) (partition 3 1 g))))))

(defn dice
  "Dice coefficient over the multiset of grapheme trigrams (docs/v2/02 §6.5):
   2·|A ∩ B| / (|A| + |B|), the intersection taken as the minimum of
   multiplicities. Integer arithmetic with a single final division, so the
   value is identical on any IEEE-754 host."
  [host a b]
  (let [ta (trigrams host a) tb (trigrams host b)
        inter (reduce + 0 (map (fn [[k v]] (min v (get tb k 0))) ta))
        total (+ (reduce + 0 (vals ta)) (reduce + 0 (vals tb)))]
    (if (zero? total) 0.0 (/ (double (* 2 inter)) total))))

;; ---------------------------------------------------------------- candidates

(defn- comparable-text
  "The text a `:sample` comparison uses for a node, or nil when it has none."
  [n]
  (case (:kind n)
    :text (get-in n [:content :text])
    :opaque (get-in n [:content :blob])
    :external (get-in n [:content :sample])
    nil))

(defn orphans
  "{node-id [edge-id …]} for every anchor endpoint that is orphan in the
   revision (docs/v2/02 §6.5, input of the reconciliation)."
  [store rev-id]
  (reduce (fn [m s]
            (reduce (fn [m ep]
                      (if (= :orphan (:state ep))
                        (update m (:node ep) (fnil conj []) (:edge s))
                        m))
                    m [(:from s) (:to s)]))
          {} (anchor/states store rev-id)))

(defn- base-of
  "docs/v2/02 §6.5: :base defaults to the parent only when there is exactly one."
  [store rev-id opts]
  (or (:base opts)
      (let [ps (:parents (revision/revision store rev-id))]
        (if (= 1 (count ps))
          (first ps)
          (err/raise :reconcile/base-required
                     (str "revision " rev-id " has " (count ps) " parents; :base is required")
                     {:revision rev-id :parents (vec ps)})))))

(defn- node-of [store id] (revision/get-object store id))

(defn- same-shape? [a b]
  (and (map? a) (map? b) (= (:class a) (:class b)) (= (:kind a) (:kind b))))

(defn- by-position
  "§6.5 `:position`: `old` held path p of tree T in the base revision and a
   different node of the same class and kind holds it in this one."
  [store rev-id base old]
  (when base
    (let [n (node-of store old)]
      (first
       (for [[tid path] (get (anchor/placements store base) old)
             :let [pos (revision/tree-at store rev-id tid)
                   cand (when pos (tree/node-at {:root pos} path))
                   base-pos (revision/tree-at store base tid)]
             :when (and cand (not= cand old) (same-shape? n (node-of store cand)))
             :let [was-there? (and base-pos (tree/contains-node? {:root base-pos} cand))]]
         {:old old :candidate cand :method :position
          :confidence (if was-there? 0.9 1.0)
          :evidence {:tree tid :path path :base base}})))))

(defn- by-fingerprint
  "§6.5 `:fingerprint`: another external node with the same locator and a
   different digest that resolves in this revision."
  [store rev-id old placed]
  (let [n (node-of store old)]
    (when (and (= :external (:kind n)) (= :sign (:class n)))
      (let [loc (get-in n [:content :locator])
            fp (get-in n [:content :fingerprint])]
        (first
         (for [cand (sort (keys placed))
               :let [c (node-of store cand)]
               :when (and (not= cand old)
                          (same-shape? n c)
                          (= loc (get-in c [:content :locator]))
                          (not= fp (get-in c [:content :fingerprint])))]
           {:old old :candidate cand :method :fingerprint :confidence 1.0
            :evidence {:locator loc}}))))))

(defn- by-sample
  "§6.5 `:sample`: the best Dice match of the same kind among the nodes that
   resolve in this revision, at or above `min-confidence`."
  [store rev-id old placed min-confidence]
  (let [host (:host store)
        n (node-of store old)
        t (comparable-text n)]
    (when t
      (->> (for [cand (sort (keys placed))
                 :let [c (node-of store cand)
                       ct (when (same-shape? n c) (comparable-text c))]
                 :when (and ct (not= cand old))
                 :let [d (dice host t ct)]
                 :when (<= min-confidence d)]
             {:old old :candidate cand :method :sample :confidence d
              :evidence {:dice d}})
           (sort-by (juxt (comp - :confidence) :candidate))
           first))))

(defn- rank [ps] (vec (sort-by (juxt (comp - :confidence) :candidate) ps)))

(defn proposals
  "Reconciliation proposals for the orphans of `rev-id` (docs/v2/02 §6.5).
   opts: {:base rev-id (default: the single parent), :min-confidence 0.6,
   :all? false}. With `:all?` every method that found a candidate is returned;
   otherwise only the best proposal per orphan. Ordered by descending
   confidence and, on a tie, by ascending candidate id."
  ([store rev-id] (proposals store rev-id {}))
  ([store rev-id {:keys [min-confidence all?] :as opts}]
   (let [base (base-of store rev-id opts)
         mc (or min-confidence default-min-confidence)
         placed (anchor/placements store rev-id)
         os (orphans store rev-id)]
     (rank
      (mapcat (fn [[old edges]]
                (let [found (keep identity [(by-position store rev-id base old)
                                            (by-fingerprint store rev-id old placed)
                                            (by-sample store rev-id old placed mc)])
                      found (map #(assoc % :edges (vec (sort edges))) found)]
                  (if all? found (take 1 (rank found)))))
              (sort-by key os))))))

(defn accept-plan
  "The TransactionPlan that accepts `proposal` (docs/v2/02 §6.5): a single
   :add-edge of the supersedes edge, with the accepting actor as its evidence.
   The kernel applies nothing; the actor commits this plan."
  [store rev-id {:keys [old candidate]} {:keys [actor timestamp]}]
  (when-not (revision/revision store rev-id)
    (err/raise :anchor/unknown-revision (str "unknown revision " rev-id) {:revision rev-id}))
  (doseq [id [old candidate]]
    (when-not (revision/get-object store id)
      (err/raise :reconcile/unknown-node (str "unknown node " id) {:node id})))
  {:plan/version 1 :base rev-id :actor actor :engines {} :timestamp timestamp
   :ops [{:op :add-edge
          :edge {:type :supersedes :from candidate :to old :evidence {:actor actor}}}]})
