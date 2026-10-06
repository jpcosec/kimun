(ns kimun.surface.docs
  "Typed documents: a Markdown document bound to a model (docs/v2/06 §5, 07 §5).

   Representation in the kernel, all written in ONE TransactionPlan:
   - a `:document` tree named after the document, built by
     `sldb.surface.markdown` from the source text, whose root block carries
     `:attrs {:doc <ulid>}` — one root node per document, its identity;
   - the source text verbatim, the sign `{:kind :opaque :content {:format
     \"markdown/source\" :blob text}}`, tied by a `:derived` edge root→source
     (engine `kimun.surface.docs.source`). The typed payload is always
     `extract(descriptor, source)`: the canonical renderer escapes and re-flows
     text, so the tree is structure, never the input of extraction;
   - the model, by a `:reference` edge root→descriptor (`:ref-hash` = the
     descriptor node id, origin = the actor). The kernel re-anchors it to the
     successor when the model is redefined (02 §6.1), so it says *which model*;
   - a `:derived` edge root→descriptor (engine `kimun.surface.models`) saying
     *which contract the document was extracted under*. The kernel invalidates
     it when the descriptor is superseded: a document without it is
     `:superseded` until it is updated or backfilled;
   - the file on disk, when there is one: an `:external` sign `{:locator {:kind
     :file :path <relative to the project root>} :sample :fingerprint
     \"<alg>:<hex>\"}` (02 §6.4) tied by a `:reference` edge root→external.
   Untracking removes those edges; the tree stays in history and is hidden."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [kimun.surface.models.extract :as extract]
            [kimun.surface.models.render :as render]
            [kimun.surface.models.store :as mstore]
            [sldb.kernel.err :as err]
            [sldb.kernel.node :as node]
            [sldb.kernel.ports :as ports]
            [sldb.kernel.tree :as tree]
            [sldb.surface.markdown.ast :as ast]
            [sldb.surface.markdown.plan :as md]))

(def source-format "Format of the opaque sign holding a document's source text." "markdown/source")
(def meta-key "Key of a plan's `:engines` under which a command records its journal metadata (an EDN string)." "kimun/meta")

;; ---------------------------------------------------------------- hashing

(defn- json-str [s]
  (str "\"" (apply str (map (fn [c] (case c \" "\\\"" \\ "\\\\" \newline "\\n" \return "\\r" \tab "\\t" \backspace "\\b" \formfeed "\\f"
                                       (if (< (int c) 32) (format "\\u%04x" (int c)) (str c)))) s)) "\""))

(defn py-json
  "`v` serialised exactly as Python's `json.dumps(v, sort_keys=True,
   ensure_ascii=False)` does — the text v1 hashes into `content_hash`."
  [v]
  (cond (nil? v) "null" (true? v) "true" (false? v) "false"
        (string? v) (json-str v) (keyword? v) (json-str (name v))
        (map? v) (str "{" (str/join ", " (map (fn [[k x]] (str (json-str (if (keyword? k) (name k) (str k))) ": " (py-json x)))
                                              (sort-by #(let [k (key %)] (if (keyword? k) (name k) (str k))) v))) "}")
        (sequential? v) (str "[" (str/join ", " (map py-json v)) "]")
        :else (str v)))

(defn text-hash "Hex digest of `text` (v1 `source_hash`)." [host text] (ports/digest-str (ports/hasher host) text))
(defn content-hash "Hex digest of the payload's canonical JSON (v1 `content_hash` / `version`)." [host payload] (text-hash host (py-json payload)))
(defn field-hashes "`{field hash}`: one content hash per payload field." [host payload] (into {} (map (fn [[k v]] [k (content-hash host v)])) payload))
(defn fingerprint "`<alg>:<hex>` of `text` (02 §6.4)." [host text] (str (name (ports/algorithm (ports/hasher host))) ":" (text-hash host text)))

;; ---------------------------------------------------------------- nodes and edges

(defn- root-node [host doc-id] (node/make host :sign :block {:format :markdown :type :document :attrs {:doc doc-id}}))
(defn- source-node [text] {:class :sign :kind :opaque :content {:format source-format :blob text}})
(defn- external-node [host path text]
  {:class :sign :kind :external
   :content {:locator {:kind :file :path path} :sample (subs text 0 (min 80 (count text))) :fingerprint (fingerprint host text)}})
(defn- id-of [host n] (:id (node/make host (:class n) (:kind n) (:content n))))

(defn- edges-from [store root]
  (for [eid (:edges store) :let [e (get-in store [:objects eid])] :when (= root (:from e))] e))

(defn- target [store e] (get-in store [:objects (:to e)]))
(defn- model-node? [n] (= mstore/blob-format (get-in n [:content :format])))

(defn- describe
  "The document map of the `:document` tree `[tid t]`, or nil when its root is
   not a typed-document root or it has been untracked."
  [store current [tid t]]
  (let [root (tree/root t) rn (get-in store [:objects root]) es (edges-from store root)]
    (when (and (get-in rn [:content :attrs :doc]) (seq es))
      (let [by (fn [pred] (filter #(pred (target store %)) es))
            model-ids (set (map :to (by model-node?)))
            entry (first (filter #(contains? model-ids (:id %)) current))
            extracted (set (map :to (filter #(= :derived (:type %)) (by model-node?))))
            src (some #(when (= source-format (get-in (target store %) [:content :format])) (target store %)) es)
            ext (some #(when (= :external (:kind (target store %))) (target store %)) es)
            any (some #(target store %) (by model-node?))]
        {:name (get-in t [:descriptor :name]) :tree tid :root root :doc (get-in rn [:content :attrs :doc])
         :model (or (:name entry) (some-> any :content :blob edn/read-string :model/name))
         :model-version (:version entry) :model-id (:id entry)
         :state (cond (nil? entry) :orphan (contains? extracted (:id entry)) :intact :else :superseded)
         :path (get-in ext [:content :locator :path]) :fingerprint (get-in ext [:content :fingerprint])
         :source (get-in src [:content :blob]) :edges (mapv :id es)}))))

(defn documents
  "Every tracked typed document of `store`, by name: `{:name :tree :root :doc
   :model :model-version :model-id :state :path :fingerprint :source :edges}`.
   `:state` is `:intact` (extracted under the current contract), `:superseded`
   (the model was redefined since) or `:orphan` (its model is gone)."
  [store]
  (let [current (mstore/models store)]
    (->> (:trees store) (filter #(= :document (get-in (val %) [:descriptor :kind])))
         (keep #(describe store current %)) (sort-by (juxt :name :tree)) vec)))

(defn hidden-trees
  "Ids of `:document` trees that are typed-document roots no longer tracked
   (untracked documents stay in history; listings skip them)."
  [store]
  (let [tracked (set (map :tree (documents store)))]
    (set (for [[tid t] (:trees store)
               :when (and (get-in store [:objects (tree/root t) :content :attrs :doc]) (not (tracked tid)))] tid))))

(defn find-document "The tracked document named or identified by `token`, or nil." [store token]
  (first (filter #(or (= token (:name %)) (= token (:tree %))) (documents store))))

(defn payload-of
  "`{:payload p}` of document `doc` under its model's current contract, or
   `{:error message}` when its source no longer extracts under it."
  [store doc]
  (let [d (some->> (:model doc) (mstore/find-model store) :descriptor)]
    (if-not d
      {:error (str "model `" (:model doc) "` is not defined")}
      (try {:payload (extract/extract d (:source doc))}
           (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) e {:error (ex-message e)})))))

(defn document
  "The tracked document `token` (name or tree id) with `:payload` (or
   `:error`), `:source-hash`, `:content-hash` and `:field-hashes`. Raises
   `:docs/missing` (exit 1)."
  [store token]
  (let [doc (or (find-document store token)
                (err/raise :docs/missing (str "no document named or identified by `" token "`") {:document token}))
        host (:host store) {:keys [payload] :as r} (payload-of store doc)]
    (cond-> (merge doc r {:source-hash (text-hash host (:source doc))})
      payload (assoc :content-hash (content-hash host payload) :field-hashes (field-hashes host payload)))))

(defn of-model "Tracked documents bound to model `name`." [store name] (filterv #(= name (:model %)) (documents store)))

;; ---------------------------------------------------------------- plans

(defn- changed-fields
  "`{path {:before v :after v}}` between two payloads; nested maps give dotted paths."
  [before after]
  (let [walk (fn walk [prefix a b]
               (if (and (map? a) (map? b))
                 (mapcat (fn [k] (walk (if prefix (str prefix "." k) (str k)) (get a k) (get b k))) (sort (distinct (concat (keys a) (keys b)))))
                 (when (not= a b) [[prefix {:before a :after b}]])))]
    (into {} (mapcat (fn [k] (walk (str k) (get before k) (get after k))) (sort (distinct (concat (keys before) (keys after))))))))

(defn- meta-engines [m] {"kimun.surface.docs" "1" meta-key (pr-str m)})

(defn- binding-ops
  "Ops tying `root` to the model, the source and (when `path`) the file,
   removing the edges of `old` (a document map) that no longer hold."
  [host store root {:keys [model-id text path actor]} old]
  (let [src (source-node text) sid (id-of host src)
        ext (when path (external-node host path text)) xid (when ext (id-of host ext))
        wanted (cond-> [{:type :reference :from root :to model-id :evidence {:ref-hash model-id :actor actor}}
                        {:type :derived :from root :to model-id :evidence {:ref-hash model-id :engine "kimun.surface.models" :version "1"}}
                        {:type :derived :from root :to sid :evidence {:ref-hash sid :engine "kimun.surface.docs.source" :version "1"}}]
                 ext (conj {:type :reference :from root :to xid :evidence {:ref-hash xid :actor actor}}))
        keep-to (set (map :to wanted))
        stale (for [eid (:edges old) :let [e (get-in store [:objects eid])]
                    :when (and (not (keep-to (:to e))) (not (model-node? (target store e))))] eid)]
    (-> [{:op :add-node :node src}]
        (cond-> ext (conj {:op :add-node :node ext}))
        (into (map (fn [eid] {:op :remove-edge :edge eid}) stale))
        (into (map (fn [e] {:op :add-edge :edge e}) wanted)))))

(defn- require-model [store model]
  (or (mstore/find-model store model)
      (err/raise :models/missing (str "no model `" model "` in the store: define it with `models define`") {:model model})))

(defn track-plan
  "Plan that tracks source `text` as document `name` of model `model`.
   `opts`: `:doc` and `:tree-id` (fresh ULIDs), `:path` (relative, or nil),
   `:actor`, `:timestamp`, `:operation` (journal label, default \"docs track\").
   Returns `{:plan :name :tree :payload}`. Raises `:docs/exists` when the name
   is tracked, `:models/missing`, and the extraction errors of the model."
  [host store model name text {:keys [doc tree-id path actor timestamp operation]}]
  (when (find-document store name)
    (err/raise :docs/exists (str "a document named `" name "` already exists") {:document name}))
  (let [m (require-model store model)
        payload (extract/extract (:descriptor m) text)
        root (:id (root-node host doc))
        a (assoc-in (ast/parse host text) [:attrs :doc] doc)
        base (md/ast->plan host a {:tree-id tree-id :name name :actor actor :timestamp timestamp :base (:head store)})]
    {:name name :tree tree-id :payload payload
     :plan (-> base
               (assoc :engines (meta-engines {:operation (or operation "docs track") :address (str model ":" name)
                                              :fields (changed-fields {} payload)}))
               (update :ops into (binding-ops host store root {:model-id (:id m) :text text :path path :actor actor} nil)))}))

(defn create-plan
  "Plan that creates document `name` of `model` from `payload` (rendered with
   the model's template, then tracked). Returns `track-plan`'s map plus `:text`."
  [host store model name payload opts]
  (let [m (require-model store model)
        text (str (render/render (:descriptor m) payload) "\n")]
    (assoc (track-plan host store model name text (assoc opts :operation (or (:operation opts) "docs create"))) :text text)))

(defn update-plan
  "Plan that replaces the source of tracked document `token` with `text`
   (re-extracted under the model's current contract, which makes it `:intact`
   again). `opts`: `:path` (default: the recorded one), `:actor`, `:timestamp`,
   `:operation`. Returns `{:plan :name :tree :payload :before :unchanged?}`;
   `:plan` is nil when neither the text, the path nor the contract moved."
  [host store token text {:keys [path actor timestamp operation]}]
  (let [doc (document store token)
        m (require-model store (:model doc))
        payload (extract/extract (:descriptor m) text)
        path (or path (:path doc))
        same? (and (= text (:source doc)) (= path (:path doc)) (= :intact (:state doc)))
        a (assoc-in (ast/parse host text) [:attrs :doc] (:doc doc))
        base (when-not same? (md/ast->update-plan host store (:tree doc) a {:actor actor :timestamp timestamp :base (:head store)}))]
    {:name (:name doc) :tree (:tree doc) :payload payload :before (:payload doc) :unchanged? same?
     :plan (when base
             (-> base
                 (assoc :engines (meta-engines {:operation (or operation "docs update") :address (str (:model doc) ":" (:name doc))
                                                :fields (changed-fields (or (:payload doc) {}) payload)}))
                 (update :ops into (binding-ops host store (:root doc) {:model-id (:id m) :text text :path path :actor actor} doc))))}))

(defn untrack-plan
  "Plan that stops tracking document `token`: its edges are removed, its tree
   stays in history. Returns `{:plan :name :tree :path}`."
  [store token {:keys [actor timestamp operation]}]
  (let [doc (document store token)]
    {:name (:name doc) :tree (:tree doc) :path (:path doc)
     :plan {:plan/version 1 :base (:head store) :actor actor :timestamp timestamp
            :engines (meta-engines {:operation (or operation "docs untrack") :address (str (:model doc) ":" (:name doc)) :fields {}})
            :ops (mapv (fn [eid] {:op :remove-edge :edge eid}) (:edges doc))}}))

(defn sync-report
  "`[{:name :path :status}]` for tracked documents with a file, where
   `read-file` is `(fn [relative-path] text-or-nil)`: `:ok`, `:modified`
   (fingerprint differs from the recorded one) or `:missing`."
  [store read-file]
  (let [host (:host store)]
    (vec (for [d (documents store) :when (:path d)
               :let [text (read-file (:path d))]]
           {:name (:name d) :path (:path d)
            :status (cond (nil? text) :missing (= (:fingerprint d) (fingerprint host text)) :ok :else :modified)}))))
