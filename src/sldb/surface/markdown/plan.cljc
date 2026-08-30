(ns sldb.surface.markdown.plan
  "AST ⇄ kernel (docs/v2/04 §8): ast->plan builds the TransactionPlan that
   creates a :document tree of positions with one pool node per distinct
   block/text/opaque, and store->ast rebuilds the AST from a store's tree."
  (:require [sldb.kernel.node :as node]
            [sldb.kernel.err :as err]
            [sldb.kernel.revision :as revision]
            [sldb.kernel.tree :as tree]
            [sldb.surface.markdown.ast :as ast]
            [sldb.surface.markdown.render :as render]))

(def ^:private text-bearing #{:heading :paragraph :code})

(defn- block-node [{:keys [type attrs format blob]}]
  (if (= type :opaque)
    {:class :sign :kind :opaque :content {:format format :blob blob}}
    {:class :sign :kind :block :content {:format :markdown :type type :attrs (or attrs {})}}))

(defn- text-node [text] {:class :sign :kind :text :content {:text text}})

(defn- not-a-document [why data]
  (err/raise :markdown/not-a-document (str "store->ast: " why) data))

(defn- collect
  "Walks the AST producing add-node ops (one alias per distinct node id) and
   ownership positions [parent-path child-alias order] in document order."
  [host ast]
  (let [state (atom {:ids {} :ops [] :edges []})
        alias-for! (fn [n]
                     (let [id (:id (node/make host (:class n) (:kind n) (:content n)))]
                       (or (get-in @state [:ids id])
                           (let [a (keyword (str "n" (count (:ids @state))))]
                             (swap! state #(-> % (assoc-in [:ids id] a)
                                               (update :ops conj {:op :add-node :node n :as a})))
                             a))))
        walk (fn walk [b path]
               (let [kids (cond-> []
                            (contains? text-bearing (:type b)) (conj {:leaf (:text b)})
                            (:children b) (into (:children b)))]
                 (doseq [[i c] (map-indexed vector kids)]
                   (let [ca (alias-for! (if (:leaf c) (text-node (:leaf c)) (block-node c)))]
                     (swap! state update :edges conj [path ca i])
                     (when-not (:leaf c) (walk c (conj path i)))))))
        root (alias-for! (block-node ast))
        _ (walk ast [])]
    (assoc @state :root root)))

(defn ast->plan
  "TransactionPlan creating a :document tree for `ast` (docs/v2/04 §8)."
  [host ast {:keys [tree-id name actor timestamp base] :or {name "" actor "surface/markdown"}}]
  (let [{:keys [ops edges root]} (collect host ast)]
    {:plan/version 1 :base base :actor actor :engines {"sldb.surface.markdown" "1"} :timestamp timestamp
     :ops (-> ops
              (conj (cond-> {:op :new-tree :tree {:kind :document :name name} :root root :as :t}
                      tree-id (assoc :id tree-id)))
              (into (map (fn [[parent to order]]
                           {:op :add-edge :edge {:type :ownership :tree :t :parent parent :to to :order order}})
                         edges)))}))

(defn markdown->plan
  "parse + ast->plan."
  [host text opts]
  (ast->plan host (ast/parse host text) opts))

(defn ast->update-plan
  "TransactionPlan that re-ingests `ast` into the document tree `tree-id` that
   already exists in `store` (docs/v2/04 §8): the nodes it needs, one
   `:detach [0]` per current child of the root — repeated, because the siblings
   shift — and the ownership positions of the new AST. It emits no `:replace`,
   and therefore records no succession: that is exactly what an edit made
   outside the kernel looks like (docs/v2/02 §6). Raises
   `:markdown/not-a-document` for an unknown tree and `:markdown/root-changed`
   when the new AST's root is not the tree's root node."
  [host store tree-id ast {:keys [actor timestamp base] :or {actor "surface/markdown"}}]
  (let [t (or (get-in store [:trees tree-id])
              (not-a-document "unknown tree" {:tree tree-id}))
        {:keys [ops edges]} (collect host ast)
        root-id (:id (node/make host :sign :block (:content (block-node ast))))
        _ (when (not= root-id (tree/root t))
            (err/raise :markdown/root-changed
                       "ast->update-plan: the document root node changed; re-rooting would need a :replace, and a :replace records a succession the surface may not invent"
                       {:tree tree-id :was (tree/root t) :now root-id}))
        n (count (tree/children-at t []))]
    {:plan/version 1 :base base :actor actor
     :engines {"sldb.surface.markdown" "1"} :timestamp timestamp
     :ops (-> ops
              (into (repeat n {:op :detach :tree tree-id :at [0]}))
              (into (map (fn [[parent to order]]
                           {:op :add-edge :edge {:type :ownership :tree tree-id
                                                 :parent parent :to to :order order}})
                         edges)))}))

(defn markdown->update-plan
  "parse + ast->update-plan."
  [host store tree-id text opts]
  (ast->update-plan host store tree-id (ast/parse host text) opts))

(defn store->ast
  "AST of the :document tree `tree-id` in `store` (docs/v2/04 §8), walking positions."
  [store tree-id]
  (let [t (or (get-in store [:trees tree-id]) (not-a-document "unknown tree" {:tree tree-id}))
        obj (fn [id] (or (revision/get-object store id) (not-a-document "missing node" {:id id})))
        build (fn build [pos path]
                (let [n (obj (:node pos)) c (:content n) kids (:children pos)]
                  (case (:kind n)
                    :opaque (do (when (seq kids) (not-a-document "opaque node with children" {:path path}))
                                {:type :opaque :format (:format c) :blob (:blob c)})
                    :block (do (when (not= :markdown (:format c)) (not-a-document "block is not markdown" {:path path}))
                               (let [type (:type c)]
                                 (if (contains? text-bearing type)
                                   (let [[tp & more] kids tn (when tp (obj (:node tp)))]
                                     (when (or (nil? tp) more (not= :text (:kind tn)) (seq (:children tp)))
                                       (not-a-document "text-bearing block needs exactly one :text child" {:path path}))
                                     {:type type :attrs (:attrs c) :text (get-in tn [:content :text])})
                                   (let [children (vec (map-indexed (fn [i k] (build k (conj path i))) kids))]
                                     (when (and (= type :list) (some #(not= :item (:type %)) children))
                                       (not-a-document "list child is not an item" {:path path}))
                                     (cond-> {:type type :attrs (:attrs c)}
                                       (not= type :thematic-break) (assoc :children children))))))
                    (not-a-document "node is neither block nor opaque" {:path path :kind (:kind n)}))))
        doc (build (:root t) [])]
    (when (not= :document (:type doc)) (not-a-document "root is not a :document block" {:root (tree/root t)}))
    doc))

(defn store->markdown
  "Canonical Markdown of the document tree `tree-id`."
  [host store tree-id]
  (render/render host (store->ast store tree-id)))
