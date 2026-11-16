(ns kimun.surface.semantic
  "The semantic layer (v1 `sldb.store.semantic*`, `export.py`): the tags of a
   document, the tag index, the tag DAG with its walks and equivalences (the
   semantic export lives in `kimun.graph.export`). Pure: every function takes values (descriptors, document
   maps, an opened store) and returns values or TransactionPlans.

   Persistence (replaces v1's `semantic_dag.yaml`): a tag is the `:symbol/:term`
   node `{:name tag :lang \"sldb/tag\"}`; a declared relation is a `:semantic`
   edge between two terms whose evidence `:context` is the id of the
   `:fact/:context` node naming the relation (`parent-context`,
   `equivalent-context`). Parents implied by a dotted name are derived, never
   stored."
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [kimun.surface.docs :as docs]
            [kimun.surface.models.store :as mstore]
            [kimun.kernel.err :as err]
            [kimun.kernel.node :as node]))

(def root-semantics
  "Semantics every model inherits (v1 `StructuredNLDoc.__semantics__`)."
  {"representation" ["markdown"] "source" ["document" "markdown"]})

(def tag-lang "`:lang` of the term node of a tag." "sldb/tag")
(def parent-context "Name of the context node under which `A is a kind of B` is asserted." "sldb/semantic/parent")
(def equivalent-context "Name of the context node under which `A means B` is asserted." "sldb/semantic/equivalent")

;; ---- tags of a document ----

(defn- plain [x] (if (keyword? x) (name x) (str x)))
(defn- parts [v] (if (sequential? v) (map plain v) [(plain v)]))

(defn- flatten-semantics [semantics]
  (->> semantics
       (mapcat (fn [[k v]]
                 (cond (string? v) [(str (plain k) "." v)]
                       (sequential? v) (when (seq v) [(str/join "." (cons (plain k) (parts v)))])
                       (map? v) (map (fn [[ck cv]] (str/join "." (concat [(plain k) (plain ck)] (parts cv)))) v)
                       :else [])))
       (remove str/blank?)))

(defn- lineage
  "Names of every base of `descriptor`, nearest first: its `:base` in order,
   then the bases of those not yet named (an exported `:base` is already the
   whole linearisation, so nothing is appended)."
  [descriptor bases]
  (loop [todo (vec (:base descriptor)) seen []]
    (if-let [b (first todo)]
      (if (some #{b} seen)
        (recur (subvec todo 1) seen)
        (recur (into (subvec todo 1) (:base (get bases b))) (conj seen b)))
      seen)))

(defn model-tags
  "Sorted tags a model gives each of its documents (v1
   `flatten_model_semantics`): `root-semantics`, overridden key by key by the
   `:semantics` of each base (farthest first) and then by the model's own.
   `bases` is `{name descriptor}`; a base without a descriptor contributes nothing."
  [descriptor bases]
  (let [chain (concat (map #(:semantics (get bases %)) (reverse (lineage descriptor bases))) [(:semantics descriptor)])
        merged (reduce (fn [m s] (reduce (fn [m [k v]] (assoc m (plain k) v)) m s)) root-semantics chain)]
    (vec (sort (set (flatten-semantics merged))))))

(defn tags-of
  "Sorted semantic tags of `document` (a `kimun.surface.docs/document` map)
   under its model `descriptor` (v1 `collect_document_semantic_tags`): the
   model's tags plus every non-blank string of the payload lists
   `semantic_tags` and `tags`. A document whose payload does not extract has
   the model's tags only."
  [descriptor bases document]
  (let [payload (:payload document)
        own (for [f ["semantic_tags" "tags"] :let [v (or (get payload f) (get payload (keyword f)))]
                  :when (sequential? v) t v :when (and (string? t) (not (str/blank? t)))]
              (str/trim t))]
    (vec (sort (into (set (model-tags descriptor bases)) own)))))

(defn index
  "Tag index of `entries` (`[{:name :model :tags}]`):
   `{:by-tag {tag [\"Model:name\" …]} :by-doc {\"Model:name\" [tag …]}}`, all sorted."
  [entries]
  (let [id #(str (:model %) ":" (:name %))]
    {:by-doc (into (sorted-map) (map (fn [e] [(id e) (vec (sort (:tags e)))])) entries)
     :by-tag (reduce (fn [m e] (reduce #(update %1 %2 (fnil conj (sorted-set)) (id e)) m (:tags e))) (sorted-map) entries)}))

;; ---- the DAG: {:nodes {tag #{parent}} :equivalences {tag #{tag}}} ----

(defn prefix-edges
  "`[[parent child] …]` implied by the dots of `tag` (v1 `_prefix_edges`)."
  [tag]
  (let [ps (vec (remove str/blank? (str/split tag #"\.")))]
    (mapv (fn [i] [(str/join "." (subvec ps 0 i)) (str/join "." (subvec ps 0 (inc i)))]) (range 1 (count ps)))))

(defn dag-of
  "The DAG of `tags` (each tag, its dotted prefixes and the parent each name
   implies — v1 `sync_semantic_dag`) plus the `declared` relations
   `{:parents {child #{parent}} :equivalences {tag #{tag}}}`."
  ([tags] (dag-of tags nil))
  ([tags declared]
   (let [named (reduce (fn [m t] (reduce (fn [m [p c]] (-> m (update p #(or % (sorted-set))) (update c (fnil conj (sorted-set)) p)))
                                         (update m t #(or % (sorted-set))) (prefix-edges t)))
                       (sorted-map) tags)]
     {:nodes (reduce (fn [m [c ps]] (update m c (fnil into (sorted-set)) ps)) named (:parents declared))
      :equivalences (into (sorted-map) (map (fn [[k v]] [k (into (sorted-set) v)])) (:equivalences declared))})))

(defn parents "Sorted parents of `tag`." [dag tag] (vec (get-in dag [:nodes tag])))
(defn children "Sorted tags that declare `tag` as a parent." [dag tag] (vec (sort (keep (fn [[c ps]] (when (contains? ps tag) c)) (:nodes dag)))))
(defn roots "Sorted tags with no parent." [dag] (vec (keep (fn [[t ps]] (when (empty? ps) t)) (:nodes dag))))

(defn- walk [step tag]
  (loop [todo (vec (step tag)) seen (sorted-set)]
    (if-let [t (peek todo)]
      (if (seen t) (recur (pop todo) seen) (recur (into (pop todo) (step t)) (conj seen t)))
      (vec seen))))

(defn above "Every tag `tag` hangs from, at any depth, sorted." [dag tag] (walk #(parents dag %) tag))
(defn under "Every tag below `tag`, at any depth, sorted." [dag tag] (walk #(children dag %) tag))
(defn scope "What naming `tag` reaches: itself and everything below it." [dag tag] (into #{tag} (under dag tag)))
(defn reach "What a document carrying `tags` can be found by: the tags and everything above them." [dag tags] (into (set tags) (mapcat #(above dag %)) tags))

(defn equivalents
  "Sorted tags `tag` was declared to mean (v1 `equivalences[tag]`)."
  [dag tag]
  (vec (get-in dag [:equivalences tag])))

(defn relative
  "How a child reads under `prefix`: its trailing segment, or the whole tag
   when the prefix is not its dotted parent (a parent declared by hand)."
  [tag prefix]
  (if (and (seq prefix) (str/starts-with? tag (str prefix "."))) (subs tag (inc (count prefix))) tag))

(defn list-tags
  "v1's `se` / `se.<prefix>` listing: the roots when `prefix` is blank, else
   the children of `prefix`, each read `relative` to it, sorted."
  [dag prefix]
  (vec (sort (set (map #(relative % prefix) (if (str/blank? prefix) (roots dag) (children dag prefix)))))))

(defn matches?
  "True when `tag` matches the glob `pattern`: `*` is one dotted segment, `**` any run."
  [tag pattern]
  (let [q #(str "\\Q" % "\\E")
        re (->> (str/split pattern #"\*\*" -1) (map (fn [p] (str/join "[^.]+" (map q (str/split p #"\*" -1))))) (str/join ".*"))]
    (boolean (re-matches (re-pattern re) tag))))

(defn glob-tags "Sorted tags of the DAG matching the glob `pattern`." [dag pattern] (filterv #(matches? % pattern) (keys (:nodes dag))))

(defn query-scope
  "Tags a query for the plain `tag` matches: its `scope`, plus the scope of
   every tag declared equivalent to it, in either direction."
  [dag tag]
  (let [eq (into (set (equivalents dag tag)) (keep (fn [[k vs]] (when (contains? vs tag) k))) (:equivalences dag))]
    (into (scope dag tag) (mapcat #(scope dag %)) eq)))

(defn in-scope?
  "v1 `_in_scope`: a glob matches tag by tag; a plain tag names its subtree
   (and its equivalents)."
  [dag pattern tags]
  (if (str/includes? pattern "*")
    (boolean (some #(matches? % pattern) tags))
    (boolean (seq (set/intersection (query-scope dag pattern) (set tags))))))

(defn documents-for
  "Sorted `Model:name` ids of the `entries` (`index` input) in scope of `pattern`."
  [dag entries pattern]
  (vec (sort (keep #(when (in-scope? dag pattern (:tags %)) (str (:model %) ":" (:name %))) entries))))

;; ---- reading an opened store ----

(defn- term [tag] {:class :symbol :kind :term :content {:name tag :lang tag-lang}})
(defn- context [nm] {:class :fact :kind :context :content {:name nm}})
(defn- id-of [host n] (:id (node/make host (:class n) (:kind n) (:content n))))

(defn document-tags
  "`[{:name :model :path :tags}]` for every tracked document of `store` whose
   model is defined, by name."
  [store]
  (let [bases (into {} (map (juxt :name :descriptor)) (mstore/models store))]
    ;; the recorded contract, not the model's current one: the payload read by
    ;; `payload-of` is the one the document was last written with
    (vec (for [d (docs/documents store) :let [desc (or (docs/contract-descriptor store d) (get bases (:model d)))] :when desc]
           {:name (:name d) :model (:model d) :path (:path d)
            :tags (tags-of desc bases (merge d (docs/payload-of store d)))}))))

(defn declared
  "The relations written in `store`: `{:parents {child #{parent}}
   :equivalences {tag #{tag}} :edges {[relation from to] [edge-id …]}}`,
   `relation` being `:parent` or `:equivalent`."
  [store]
  (let [host (:host store) obj #(get-in store [:objects %])
        rel {(id-of host (context parent-context)) :parent (id-of host (context equivalent-context)) :equivalent}
        tag-of #(let [n (obj %)] (when (= tag-lang (get-in n [:content :lang])) (get-in n [:content :name])))]
    (reduce (fn [m eid]
              (let [e (obj eid) r (rel (get-in e [:evidence :context])) a (some-> (:from e) tag-of) b (some-> (:to e) tag-of)]
                (if (and (= :semantic (:type e)) r a b)
                  (-> m (update-in [(if (= r :parent) :parents :equivalences) a] (fnil conj (sorted-set)) b)
                      (update-in [:edges [r a b]] (fnil conj []) eid))
                  m)))
            {:parents {} :equivalences {} :edges {}} (:edges store))))

(defn dag
  "The semantic DAG of an opened `store`: the tags of its documents, the
   parents their names imply and the declared relations."
  [store]
  (dag-of (mapcat :tags (document-tags store)) (declared store)))

(defn show
  "One tag as the DAG sees it (v1 `semantic_tag`): `{:tag :parents :children
   :below :above :equivalents}`. Raises `:semantic/missing` for an unknown tag."
  [dag tag]
  (when-not (contains? (:nodes dag) tag)
    (err/raise :semantic/missing (str "Unknown semantic tag '" tag "': no document carries it.") {:tag tag}))
  {:tag tag :parents (parents dag tag) :children (children dag tag) :below (under dag tag)
   :above (above dag tag) :equivalents (equivalents dag tag)})

;; ---- plans ----

(defn- implied? [tag parent] (and (str/starts-with? tag (str parent ".")) (not (str/includes? (subs tag (inc (count parent))) "."))))

(defn- plan [store operation tag value {:keys [actor timestamp]} ops]
  {:plan/version 1 :base (:head store) :actor actor :timestamp timestamp :ops (vec ops)
   :engines {"kimun.surface.semantic" "1"
             docs/meta-key (pr-str {:operation operation :address (str "se." tag) :new-value value})}})

(defn- relation-ops [host a b ctx actor]
  (let [to (id-of host (term b)) c (context ctx)]
    [{:op :add-node :node (term a) :as :ta} {:op :add-node :node (term b) :as :tb} {:op :add-node :node c :as :ctx}
     {:op :add-edge :edge {:type :semantic :from (id-of host (term a)) :to to
                           :evidence {:ref-hash to :actor actor :context (id-of host c)}}}]))

(defn parent-add-plan
  "Plan declaring `child` a kind of `parent`: `{:changed true :plan p}`, or
   `{:changed false}` when the DAG already says so. `opts`: `:actor`,
   `:timestamp`. Raises `:semantic/missing` (either tag unknown),
   `:semantic/cycle` (itself, or `parent` is already a kind of `child`)."
  [host store child parent opts]
  (let [g (dag store)]
    (doseq [t [child parent]] (show g t))
    (when (= child parent) (err/raise :semantic/cycle (str "'" child "' cannot be a kind of itself.") {:tag child}))
    (when (some #{child} (above g parent))
      (err/raise :semantic/cycle (str "'" child "' cannot be a kind of '" parent "': '" parent "' is already a kind of '" child "'.") {:tag child :parent parent}))
    (if (some #{parent} (parents g child))
      {:changed false}
      {:changed true :plan (plan store "semantic parent-add" child parent opts (relation-ops host child parent parent-context (:actor opts)))})))

(defn parent-remove-plan
  "Plan undeclaring `parent` of `child` (same result shape). Raises
   `:semantic/missing` (unknown child) and `:semantic/implied` (the parent the
   tag's own name gives it cannot go)."
  [_host store child parent opts]
  (show (dag store) child)
  (when (implied? child parent)
    (err/raise :semantic/implied (str "'" parent "' is the parent '" child "' gets from its own name; rename the tag to change it.") {:tag child :parent parent}))
  (if-let [eids (seq (get-in (declared store) [:edges [:parent child parent]]))]
    {:changed true :plan (plan store "semantic parent-remove" child parent opts (map (fn [e] {:op :remove-edge :edge e}) eids))}
    {:changed false}))

(defn equivalent-add-plan
  "Plan declaring that tag `a` means tag `b` (same result shape). `a` must be
   a tag of the store (`:semantic/missing`); `b` may be foreign, as in v1."
  [host store a b opts]
  (show (dag store) a)
  (when (= a b) (err/raise :semantic/cycle (str "'" a "' already means itself.") {:tag a}))
  (if (seq (get-in (declared store) [:edges [:equivalent a b]]))
    {:changed false}
    {:changed true :plan (plan store "semantic equivalent-add" a b opts (relation-ops host a b equivalent-context (:actor opts)))}))
