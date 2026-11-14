(ns kimun.surface.docs
  "Typed documents: a Markdown document bound to a model (docs/v2/06 §5, 07 §5).

   Representation in the kernel, all written in ONE TransactionPlan:
   - a `:document` tree named after the document, built by
     `kimun.surface.markdown` from the source text, whose root block carries
     `:attrs {:doc <ulid>}` — one root node per document, its identity;
   - the source text is NOT stored (formats 3–4, docs/v2/14 §10): the file is the
     authority and the text is read from it by the external anchor. The typed
     payload is always `extract(descriptor, source)`: the canonical renderer
     escapes and re-flows text, so the tree is structure, never the input of
     extraction. The `:contract` (the id of the descriptor the document was
     extracted under) lives in the evidence of the anchor edge, so model
     succession leaves it in place and the recorded contract keeps answering
     until the document is updated;
   - the model, by a `:reference` edge root→descriptor (`:ref-hash` = the
     descriptor node id, origin = the actor, and `:contract` when the document
     has no file). The kernel re-anchors it to the successor when the model is
     redefined (02 §6.1), so it says *which model*;
   - a `:derived` edge root→descriptor (engine `kimun.surface.models`) saying
     *which contract the document was extracted under*. The kernel invalidates
     it when the descriptor is superseded: a document without it is
     `:superseded` until it is updated or backfilled;
   - the file on disk, when there is one: an `:external` sign `{:locator {:kind
     :file :path <relative to the project root>} :sample :fingerprint
     \"<alg>:<hex>\"}` (02 §6.4) tied by a `:reference` edge root→external
     whose evidence records `:contract` and `:parse-contract`, the whole
     contract the document was parsed and extracted under with its digest
     (`kimun.surface.parse-contract`, K6). A document written before K6 has no
     `:parse-contract`; it gains one the next time it is written.
   Reading an old store that still holds the verbatim source sign
   (`{markdown/source blob}`) is supported: its text wins over the file.
   Untracking removes those edges; the tree stays in history and is hidden.

   Names (K8, docs/v2/06 §3 «Nombres de documento»): a document's `:name` is a
   short alias and several documents may share it when each has its own file.
   `:qualified` is the path of its file (the anchor's locator, normalised),
   or the name when it has none. Neither is the identity of the document: the
   tree id is the stable reference, and the qualified name follows the
   locator. `find-document` resolves a tree id, a short name or a qualified
   name, and raises `:docs/ambiguous` with the candidates when a short name
   means more than one document; it never picks one.

   Documents without a model (K3): in a format-4, by-reference store a file
   ingested without a model is registered like a typed one — an external
   anchor, the class `generic/md` and the root hash, its tree as cache — and
   not persisted block by block. It is not a typed document (`documents` does
   not list it): `generic-documents` does. Its root node carries `:doc`, its
   own, like a typed document's."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [kimun.host.store-cache :as store-cache]
            [kimun.surface.parse-contract :as parse-contract]
            [kimun.surface.models.extract :as extract]
            [kimun.surface.models.render :as render]
            [kimun.surface.models.store :as mstore]
            [kimun.kernel.canon :as canon]
            [kimun.kernel.edge :as edge]
            [kimun.kernel.err :as err]
            [kimun.kernel.node :as node]
            [kimun.kernel.ports :as ports]
            [kimun.kernel.revision :as revision]
            [kimun.kernel.route :as route]
            [kimun.kernel.store :as kstore]
            [kimun.kernel.tree :as tree]
            [kimun.surface.markdown.ast :as ast]
            [kimun.surface.markdown.plan :as md]))

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
(defn- external-node [host path text]
  {:class :sign :kind :external
   :content {:locator {:kind :file :path path} :sample (subs text 0 (min 80 (count text))) :fingerprint (fingerprint host text)}})
(defn- id-of [host n] (:id (node/make host (:class n) (:kind n) (:content n))))

(defn- edges-by-from
  "`{from-node-id [edge …]}` over the active edges of `store` (one pass), each
   vector in ascending edge id (the order `edges-from` gives for one node)."
  [store]
  (group-by :from (keep #(let [e (get-in store [:objects %])] (when (:from e) e)) (sort (:edges store)))))

(defn- edges-from
  "`{root [edge …]}` for the active edges leaving each of `roots`, in ascending
   edge id: the entries of `edges-by-from` for those nodes, read through the
   kernel's incidence index instead of the whole edge set."
  [store roots]
  (into {} (map (fn [r] [r (filterv #(= r (:from %)) (map #(get-in store [:objects %]) (revision/edges-touching store r)))]))
        roots))

(defn- target [store e] (get-in store [:objects (:to e)]))
(defn- model-node? [n] (= mstore/blob-format (get-in n [:content :format])))
(defn- stored-source?
  "True for the verbatim source sign of a format-2 store (no longer written)."
  [n] (= source-format (get-in n [:content :format])))
(defn- source-or-file?
  "True for the two signs a document owns besides its model binding: its
   file on disk (or its legacy stored source text)."
  [n]
  (or (stored-source? n) (= :external (:kind n))))

(defn- anchor-edge
  "The edge that carries the document's `:contract`: the `:reference` to the
   file when there is one, else the `:reference` to the model."
  [store es]
  (or (some #(when (= :external (:kind (target store %))) %) es)
      (some #(when (and (= :reference (:type %)) (model-node? (target store %))) %) es)))

(defn- source-of
  "The document's source text — the text it was tracked with: the verbatim
   sign of a format-2 store when it is still there, else the file the anchor
   points to while its fingerprint still matches what was recorded (the file
   IS the tracked text then), else the canonical rendering of the document
   tree (the tracked structure, when the file was edited outside the store).
   The file is never trusted past its fingerprint: an unrecorded edit must
   not change what the store answers until `docs update` reconciles it
   (docs/v2/14 §2). `:read-file` is a `(fn [relative-path] text-or-nil)`."
  [host store tid es path recorded-fp]
  (or (some #(when (stored-source? (target store %)) (get-in (target store %) [:content :blob])) es)
      (let [ft (when (and path (:read-file store)) ((:read-file store) path))]
        (if (and ft (some? recorded-fp) (= recorded-fp (fingerprint host ft)))
          ft
          (or (try (md/store->markdown host store tid)
                   (catch #?(:clj Exception :cljs :default) _ nil))
              ft)))))
(defn qualified-path
  "`p` as a qualified name: `\\` as `/`, leading `./` removed, NFC. Nil for nil."
  [host p]
  (some->> p route/normalize-path (canon/normalize host)))

(def generic-class
  "Class of a document without a model (docs/v2/14 §18.1, 10 §11): the
   structure the Markdown profile gives, whole. Only the Markdown profile
   exists; a per-store granularity and other file types wait for spec 16."
  "generic/md")

(defn generic-class?
  "True when `class` (the `:class` of a tree descriptor) is a generic class."
  [class]
  (and (string? class) (str/starts-with? class "generic/")))

(defn- owned-edge?
  "True for an edge this surface wrote from a document root: to its model, its
   source or its file. Any other edge leaving the root — an authored relation,
   a link — belongs to whoever wrote it and is never removed here."
  [store e]
  (let [n (target store e)] (or (model-node? n) (source-or-file? n))))

(defn- describe
  "The document map of the `:document` tree `[tid t]`, or nil when its root is
   not a typed-document root or it has been untracked. `:contract-id` is the
   descriptor the document was extracted under, read from the `:contract` of
   its source edge: it survives model succession and is what `payload-of`
   reads."
  [store current by-from [tid t]]
  (let [root (tree/root t) rn (get-in store [:objects root]) es (get by-from root)
        d (:descriptor t)]
    ;; a document of the generic class (K3) has no model: it is not a typed document
    (when (and (get-in rn [:content :attrs :doc]) (seq es) (not (generic-class? (:class d))))
      (let [by (fn [pred] (filter #(pred (target store %)) es))
            model-ids (set (map :to (by model-node?)))
            entry (first (filter #(contains? model-ids (:id %)) current))
            extracted (set (map :to (filter #(= :derived (:type %)) (by model-node?))))
            ext-edge (some #(when (= :external (:kind (target store %))) %) es)
            ext (target store ext-edge)
            any (some #(target store %) (by model-node?))
            path (get-in ext [:content :locator :path])]
        {:name (get-in t [:descriptor :name]) :tree tid :root root :doc (get-in rn [:content :attrs :doc])
         :model (or (:name entry) (some-> any :content :blob edn/read-string :model/name))
         :model-version (:version entry) :model-id (:id entry)
         :contract-id (get-in (anchor-edge store es) [:evidence :contract])
         :parse-contract (get-in (anchor-edge store es) [:evidence :parse-contract])
         :inline (boolean (some #(stored-source? (target store %)) es))
         :recorded-content-hash (or (get-in (anchor-edge store es) [:evidence :content-hash])
                                   (get-in d [:content-hash]))
         :recorded-field-hashes (get-in (anchor-edge store es) [:evidence :field-hashes])
         :class (or (get-in d [:class]) (get-in (anchor-edge store es) [:evidence :contract]))
         :cached? (true? (get-in d [:cached?]))
         :state (cond (nil? entry) :orphan (contains? extracted (:id entry)) :intact :else :superseded)
         :path path :qualified (or (qualified-path (:host store) path) (get-in t [:descriptor :name]))
         :fingerprint (get-in ext [:content :fingerprint])
         :source (source-of (:host store) store tid es path (get-in ext [:content :fingerprint])) :edges (mapv :id es)}))))

(defn documents
  "Every tracked typed document of `store`, by name: `{:name :qualified :tree
   :root :doc :model :model-version :model-id :state :path :fingerprint :source
   :edges}`.
   `:state` is `:intact` (extracted under the current contract), `:superseded`
   (the model was redefined since) or `:orphan` (its model is gone)."
  [store]
  ;; `describe` reads only the descriptor of a tree (its name and root node): no tree object is loaded
  (let [current (mstore/models store) by-from (edges-by-from store)]
    (->> (revision/tree-descriptors store) (filter #(= :document (:kind (second %))))
         (keep (fn [[tid d]] (describe store current by-from [tid {:descriptor d}]))) (sort-by (juxt :name :tree)) vec)))

(declare describe-generic generic-documents)

(defn hidden-trees
  "Ids of `:document` trees that are document roots no longer tracked
   (untracked documents stay in history; listings skip them): typed ones, and
   documents of a generic class (K3) whose anchor was removed. A generic
   document that is still anchored is not hidden: it is listed with the
   untyped ones. `docs` is `(documents store)` when the caller already has it."
  ([store] (hidden-trees store (documents store)))
  ([store docs]
   (let [tracked (into (set (map :tree docs)) (map :tree) (generic-documents store))]
     (set (for [[tid d] (revision/tree-descriptors store)
                :when (and (get-in store [:objects (:root d) :content :attrs :doc]) (not (tracked tid)))] tid)))))

(defn candidates
  "The tracked documents `token` may mean as a tree id or a short name, typed
   or of a generic class (K3, `:generic true`), by name then tree id. It
   describes only the trees that can match (a document's `:name` is its tree's
   descriptor name and its `:tree` the tree id): every write resolves its
   document here, so it costs what the matching trees cost — the name index of
   the kernel, then the edges of those roots only."
  [store token]
  (let [cands (->> (cons token (revision/trees-named store token)) distinct
                   (keep (fn [tid] (when-let [d (revision/tree-descriptor store tid)]
                                     (when (= :document (:kind d)) [tid {:descriptor d}])))))]
    (if (empty? cands)
      []
      (let [current (mstore/models store) by-from (edges-from store (map (comp tree/root second) cands))]
        (->> cands
             (keep (fn [[tid t :as c]]
                     (or (describe store current by-from c)
                         (describe-generic store by-from [tid (:descriptor t)]))))
             (sort-by (juxt :name :tree)) vec)))))

(defn- candidate-data
  "What an ambiguity error says of a candidate; a generic one (K3) has no
   `:model` and names its `:class` instead."
  [d]
  (cond-> (select-keys d [:name :qualified :tree :path :model])
    (:generic d) (assoc :class (:class d))))

(defn- ambiguous!
  "Raises `:docs/ambiguous` (exit 2) for `token` with its `found` documents."
  [token found]
  (err/raise :docs/ambiguous
             (str (count found) " documents answer to `" token "`: "
                  (str/join ", " (map :qualified found))
                  " — name one by its qualified name or its tree id")
             {:document token :candidates (mapv candidate-data found)}))

(defn resolve-any
  "The tracked document identified by `token`, typed or of a generic class
   (`:generic true`), or nil. `token` is, in order: a tree id; a short name
   (`:name`); a qualified name (the path of the document's file). A short name
   shared by several documents — typed, generic or both — is not resolved: it
   raises `:docs/ambiguous` (exit 2) with `:candidates [{:name :qualified :tree
   :path :model}]` (`:class` instead of `:model` for a generic one). A qualified name is looked up only when nothing has
   that name and the token looks like a path (it has a `/` or a `.`); that
   lookup reads every document."
  [store token]
  (let [found (candidates store token)
        exact (filter #(= token (:tree %)) found)]
    (cond
      (seq exact) (first exact)
      (= 1 (count found)) (first found)
      (seq found) (ambiguous! token found)
      (and (string? token) (re-find #"[/.]" token))
      (let [q (qualified-path (:host store) token)
            by-path (filterv #(and (:path %) (= q (:qualified %)))
                             (concat (documents store) (generic-documents store)))]
        (case (count by-path) 0 nil 1 (first by-path) (ambiguous! token by-path)))
      :else nil)))

(defn find-document
  "The tracked **typed** document identified by `token` (see `resolve-any`),
   or nil — also when `token` names a document of a generic class. A short
   name shared with any other document, typed or generic, raises
   `:docs/ambiguous`: it never picks one."
  [store token]
  (let [d (resolve-any store token)]
    (when-not (:generic d) d)))

(defn refuse-collision!
  "Raises `:docs/exists` when a new document `name` with file `path` (nil for
   none) cannot live beside the tracked ones: another document has that name
   and one of the two has no file (nothing would tell them apart), or it has
   the same file. Two documents may share a short name when their files
   differ; the name is then an ambiguous alias."
  [store name path]
  (let [q (qualified-path (:host store) path)]
    (doseq [d (candidates store name) :when (= name (:name d))]
      (cond
        (or (nil? path) (nil? (:path d)))
        (err/raise :docs/exists (str "a document named `" name "` already exists") {:document name})
        (= q (:qualified d))
        (err/raise :docs/exists (str "a document named `" name "` already tracks " (:qualified d))
                   {:document name :path (:qualified d)})))))

(defn contract-descriptor
  "The descriptor node the document was last written with — the `:contract`
   recorded in the evidence of its source edge — or nil when it predates the
   record or the node is no longer in the CAS. Superseded descriptors stay in
   the CAS, so the old contract keeps answering until the document is updated."
  [store doc]
  (when-let [id (:contract-id doc)] (mstore/descriptor-by-id store id)))

(defn payload-of
  "`{:payload p}` of document `doc` extracted under the contract it was last
   written with (`contract-descriptor`, the model's current contract only when
   none was recorded), or `{:error message}` when its source no longer extracts
   under it."
  [store doc]
  (let [d (or (contract-descriptor store doc)
              (some->> (:model doc) (mstore/find-model store) :descriptor))]
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
    (cond-> (merge doc r {:source-hash (text-hash host (or (:source doc) ""))})
      payload (assoc :content-hash (content-hash host payload) :field-hashes (field-hashes host payload)))))

(defn of-model "Tracked documents bound to model `name`." [store name] (filterv #(= name (:model %)) (documents store)))

;; ---------------------------------------------------------------- resolver

(declare cache-write!)

(defn cached-tree?
  "True when tree `tid` was registered as cache (its tree objects and nodes are
   derived, docs/v2/14 §18.6)."
  [store tid]
  (true? (:cached? (revision/tree-descriptor store tid))))

(defn doc-tree
  "The committed tree of document `doc` through the ONE resolver (docs/v2/14
   §19): from the store when the tree is persisted (a document without file or
   `:inline`), from the cache otherwise, and rebuilt from the file (and written
   back to the cache) when the cache is absent. Returns `{:tree :stale?}`;
   `:stale?` is true when the file no longer matches the registered fingerprint,
   so the recomputed root may differ from the registered one (the document is
   out of date: `docs update NAME`). Nil when the tree cannot be obtained (no
   cache hit and no readable file)."
  [store doc]
  (let [host (:host store) tid (:tree doc)]
    (if-not (cached-tree? store tid)
      (when (revision/tree-descriptor store tid)
        (let [t (revision/load-tree store tid (:head store))]
          {:tree t :objects (when t (tree/objects t)) :stale? false}))
      (let [[_ merkle] (revision/tree-entry store tid)
            d (revision/tree-descriptor store tid)
            t (err/rescue-any (fn [_] nil) (revision/load-tree store tid (:head store)))
            ok? (and t (some? (tree/node-at t [])))
            rebuild (fn [text]
                      (let [a (assoc-in (ast/parse host text) [:attrs :doc] (:doc doc))
                            reg (assoc (md/ast->tree host a {:tree-id tid :name (:name doc) :class (:class d)
                                                             :content-hash (:content-hash d) :cached? true})
                                       :tree-id tid)]
                        (cache-write! store reg {:fingerprint (fingerprint host text) :class (:class d)})
                        {:tree (:tree reg) :objects (:objects reg) :nodes (:nodes reg)
                         :stale? (not= merkle (:merkle-root reg))}))]
        (if ok?
          {:tree t :objects (tree/objects t) :stale? false}
          (when-let [text (and (:path doc) (:read-file store) ((:read-file store) (:path doc)))]
            (rebuild text)))))))

(defn- with-tree
  "`store` with the resolved tree `r` (see `doc-tree`) in its views, so the
   Markdown/AST readers do not re-read a store whose object view may have
   memoised a miss before the cache was repopulated."
  [store {:keys [tree objects nodes]}]
  (-> store
      (assoc-in [:trees (:tree tree)] tree)
      (update :objects merge objects nodes)))

(defn document-markdown
  "Canonical Markdown of `doc` (a document map) or of the document named or
   identified by `token`, through `doc-tree`; nil when it cannot be resolved."
  [store doc-or-token]
  (let [doc (if (map? doc-or-token) doc-or-token (find-document store doc-or-token))
        r (and doc (doc-tree store doc))]
    (when-let [t (:tree r)]
      (md/store->markdown (:host store) (with-tree store r) (:tree t)))))

(defn document-ast
  "SLDB-MD AST of `doc` (a document map) or of the document named or identified
   by `token`, through `doc-tree`; nil when it cannot be resolved."
  [store doc-or-token]
  (let [doc (if (map? doc-or-token) doc-or-token (find-document store doc-or-token))
        r (and doc (doc-tree store doc))]
    (when-let [t (:tree r)]
      (md/store->ast (with-tree store r) (:tree t)))))

;; ---------------------------------------------------------------- plans

(defn changed-fields
  "`{path {:before v :after v}}` between two payloads; nested maps give dotted paths."
  [before after]
  (let [walk (fn walk [prefix a b]
               (if (and (map? a) (map? b))
                 (mapcat (fn [k] (walk (if prefix (str prefix "." k) (str k)) (get a k) (get b k))) (sort (distinct (concat (keys a) (keys b)))))
                 (when (not= a b) [[prefix {:before a :after b}]])))]
    (into {} (mapcat (fn [k] (walk (str k) (get before k) (get after k))) (sort (distinct (concat (keys before) (keys after))))))))

(defn- meta-engines [m] {"kimun.surface.docs" "1" meta-key (pr-str m)})

;; ---------------------------------------------------------------- registered tree (cache)

(defn- serialize-object
  "Canonical text of `obj` as it is written to the object cache: the same
   hashed form the kernel serialises to `objects/` (nodes lose :id/:address)."
  [host obj] (canon/canon-str host (kstore/hashed-form obj)))

(defn- cache-write!
  "Persists the derived objects of `reg` (its tree objects and its block/text/
   opaque nodes) and the per-document cache entry in `<store>/cache/` when the
   store has a directory (docs/v2/14 §18.6, §19). Returns `reg`. A store
   without `:store-dir` (in-memory, tests) writes nothing: the resolver
   recomputes from the file."
  [store reg {:keys [fingerprint class]}]
  (when-let [dir (:store-dir store)]
    (let [host (:host store)]
      (doseq [[id obj] (merge (:objects reg) (:nodes reg))]
        (store-cache/write-object! dir id (serialize-object host obj)))
      (when (and fingerprint class)
        (store-cache/write-entry! dir (store-cache/cache-key fingerprint class store-cache/parser-version)
                                  {:fingerprint fingerprint :class class :parser store-cache/parser-version
                                   :tree (:tree-id reg) :root-hash (:merkle-root reg)}))))
  reg)

(defn- registry
  "The registry of a by-reference document (docs/v2/14 §18.6): builds its tree
   in memory, writes the derived objects to the cache and returns `{:reg :ops}`
   where `:reg` carries the merkle root and `:ops` are the plan ops that
   register it — the root node, the external anchor, the model edges and the
   `:register-tree` op. No ownership edge and no block/text node enters the
   plan: that structure is recomputable from `archivo + clase`."
  [host store {:keys [doc tree-id name text path class content-hash actor]}]
  (let [a (assoc-in (ast/parse host text) [:attrs :doc] doc)
        reg (assoc (md/ast->tree host a {:tree-id tree-id :name name :class class :content-hash content-hash})
                   :tree-id tree-id)
        root (:root reg)
        ext (when path (external-node host path text))
        xid (when ext (id-of host ext))
        ext-ref (when ext {:type :reference :from root :to xid
                           :evidence {:ref-hash xid :actor actor :contract class
                                      :parse-contract (parse-contract/record host (parse-contract/environment) class)}})
        wanted (cond-> [{:type :reference :from root :to class :evidence {:ref-hash class :actor actor}}
                        {:type :derived :from root :to class
                         :evidence {:ref-hash class :engine "kimun.surface.models" :version "1"}}]
                 ext-ref (conj ext-ref))]
    {:reg reg
     :wanted wanted
     :ops (-> [{:op :add-node :node (root-node host doc) :as :root}]
              (cond-> ext (conj {:op :add-node :node ext :as :ext}))
              (conj {:op :register-tree :tree (dissoc (:descriptor reg) :tree)
                     :id tree-id :root :root :merkle-root (:merkle-root reg) :as :t})
              (into (map (fn [e] {:op :add-edge :edge e}) wanted)))}))

(defn- registry-stale
  "Ids of the owned edges of `old` that the wanted set of a registry plan does
   not keep: the edges to the file, source and model this surface wrote."
  [host store wanted root old]
  (let [wanted-ids (set (map #(:id (edge/make host %)) wanted))]
    (for [eid (:edges old) :let [e (get-in store [:objects eid])]
          :when (and (not (wanted-ids eid)) (source-or-file? (target store e)))] eid)))

(defn- registry-ops
  "Ops of a by-reference document: its registry plus the removal of the stale
   owned edges of `old` (an existing document map, or nil)."
  [host store opts old]
  (let [{:keys [reg wanted ops]} (registry host store opts)]
    {:reg reg
     :ops (into ops (map (fn [eid] {:op :remove-edge :edge eid})
                         (registry-stale host store wanted (:root reg) old)))}))

(defn- binding-ops
  "Ops tying `root` to the model and (when `path`) the file, removing the edges
   of `old` (a document map) that no longer hold. The anchor edge records
   `:contract` = `model-id` (the descriptor the text is extracted under),
   `:content-hash` and `:field-hashes` of the payload written: the `:reference`
   to the file when there is one, else the `:reference` to the model. When
   `inline?` the source sign is written and tied by a `:derived root→source`
   edge, so the document carries its own text (docs/v2/14 §10: `:content
   :inline`); by reference (default) no source sign is written and the text is
   read from the file."
  [host store root {:keys [model-id text path actor content-hash inline?]} old]
  (let [src (when inline? {:class :sign :kind :opaque :content {:format source-format :blob text}})
        sid (when src (id-of host src))
        ext (when path (external-node host path text)) xid (when ext (id-of host ext))
        ;; format 4: the extracted payload's content hash lives in the registry
        ;; descriptor of a cached document, not in the edge evidence; the field
        ;; hashes are gone (readers recompute them from the payload when needed)
        marks (cond-> {:contract model-id :parse-contract (parse-contract/record host (parse-contract/environment) model-id)}
                content-hash (assoc :content-hash content-hash))
        ext-ref (when ext {:type :reference :from root :to xid
                           :evidence (merge {:ref-hash xid :actor actor} marks)})
        wanted (cond-> [{:type :reference :from root :to model-id
                         :evidence (cond-> (merge {:ref-hash model-id :actor actor} marks) ext (dissoc :contract :parse-contract :content-hash :field-hashes))}
                        {:type :derived :from root :to model-id
                         :evidence {:ref-hash model-id :engine "kimun.surface.models" :version "1"}}]
                 ext-ref (conj ext-ref)
                 sid (conj {:type :derived :from root :to sid
                            :evidence {:ref-hash sid :engine "kimun.surface.docs" :version "1"}}))
        wanted-ids (set (map #(:id (edge/make host %)) wanted))
        stale (for [eid (:edges old) :let [e (get-in store [:objects eid])]
                    :when (and (not (wanted-ids eid)) (source-or-file? (target store e)))] eid)]
    (-> []
        (cond-> src (conj {:op :add-node :node src :as :src}))
        (cond-> ext (conj {:op :add-node :node ext :as :ext}))
        (into (map (fn [eid] {:op :remove-edge :edge eid}) stale))
        (into (map (fn [e] {:op :add-edge :edge e}) wanted)))))

(defn- require-model [store model]
  (or (mstore/find-model store model)
      (err/raise :models/missing (str "no model `" model "` in the store: define it with `models define`") {:model model})))

(defn move-text
  "The Markdown that moves `doc` to its model's current contract: `payload`
   (default: the document's recorded payload, read under the contract it was
   written with) rendered with the model's current descriptor. `context` is the
   composition context (`kimun.cli.compose/context`). This is how `docs update`
   and `stores update` migrate a `:superseded` document without re-reading its
   old text under the new template, which would misalign every section after the
   change."
  [store doc payload context]
  (let [m (require-model store (:model doc))]
    (str (render/render (:descriptor m) (or payload (:payload doc)) context) "\n")))

(defn track-plan
  "Plan that tracks source `text` as document `name` of model `model`.
   `opts`: `:doc` and `:tree-id` (fresh ULIDs), `:path` (relative, or nil),
   `:actor`, `:timestamp`, `:operation` (journal label, default \"docs track\").
   Returns `{:plan :name :tree :payload}`. Raises `:docs/exists` when the name
   cannot be shared (`refuse-collision!`), `:models/missing`, and the extraction errors of the model. With
   `:inline` the source text is stored in the store instead of read from the
   file (default: the store descriptor's `:content`). A document with a path
   (and not `:inline`) is registered as CACHE (docs/v2/14 §18.6): no ownership
   edge, no block/text node and no tree object enters the plan."
  [host store model name text {:keys [doc tree-id path actor timestamp operation inline]}]
  (refuse-collision! store name path)
  (let [m (require-model store model)
        payload (extract/extract (:descriptor m) text)
        ch (content-hash host payload)
        inline? (or inline (= :inline (:content store)))
        cached? (and path (not inline?) (not (revision/legacy? store)))
        engines (meta-engines {:operation (or operation "docs track") :address (str model ":" name)
                               :fields (changed-fields {} payload)})
        ops (if cached?
              (let [{:keys [reg ops]} (registry-ops host store {:doc doc :tree-id tree-id :name name :text text
                                                                 :path path :class (:id m) :content-hash ch :actor actor} nil)]
                (cache-write! store reg {:fingerprint (fingerprint host text) :class (:id m)})
                ops)
              (let [a (assoc-in (ast/parse host text) [:attrs :doc] doc)
                    root (:id (root-node host doc))
                    base (md/ast->plan host a {:tree-id tree-id :name name :actor actor :timestamp timestamp :base (:head store)})]
                (into (:ops base) (binding-ops host store root {:model-id (:id m) :text text :path path :actor actor
                                                                :content-hash ch :inline? inline?} nil))))]
    {:name name :tree tree-id :payload payload
     :plan {:plan/version 1 :base (:head store) :actor actor :timestamp timestamp :engines engines :ops (vec ops)}}))

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
   `:operation`, `:inline` (default: keep the document's mode). Returns `{:plan
   :name :tree :payload :before :unchanged?}`;
   `:plan` is nil when neither the text, the path nor the contract moved. A
   recorded parse contract that is no longer the one in force counts as moved
   (the document is re-registered under the current one), as does one whose
   digest does not verify; a document with no recorded parse contract is not
   rewritten for that alone."
  [host store token text {:keys [path actor timestamp operation inline]}]
  (let [doc (document store token)
        m (require-model store (:model doc))
        payload (extract/extract (:descriptor m) text)
        path (or path (:path doc))
        ch (content-hash host payload)
        inline? (or inline (:inline doc))
        cached? (and path (not inline?) (not (revision/legacy? store)))
        ;; with by-reference content the tracked text is the file: "unchanged" is the fingerprint
        ;; matching what the doc was written with, not the text read back from the file
        same? (and (= path (:path doc)) (= :intact (:state doc))
                   (contains? #{:same :unrecorded}
                              (:status (parse-contract/compare-recorded host (parse-contract/environment)
                                                                        (:parse-contract doc) (:id m))))
                   (if (:path doc)
                     (and (some? (:fingerprint doc)) (= (:fingerprint doc) (fingerprint host text)))
                     (= text (:source doc))))
        ;; the payload read is the one under the contract the document was
        ;; written with: the fields the new contract adds are not in it
        before (or (:payload doc) {})
        engines (meta-engines {:operation (or operation "docs update") :address (str (:model doc) ":" (:name doc))
                               :fields (changed-fields before payload)})]
    (if cached?
      (let [{:keys [reg ops]} (registry-ops host store {:doc (:doc doc) :tree-id (:tree doc) :name (:name doc)
                                                         :text text :path path :class (:id m) :content-hash ch :actor actor}
                                             doc)]
        (when-not same? (cache-write! store reg {:fingerprint (fingerprint host text) :class (:id m)}))
        {:name (:name doc) :tree (:tree doc) :payload payload :before (:payload doc) :unchanged? same?
         :plan (when-not same?
                 {:plan/version 1 :base (:head store) :actor actor :timestamp timestamp :engines engines :ops (vec ops)})})
      (let [a (assoc-in (ast/parse host text) [:attrs :doc] (:doc doc))
            base (when-not same? (md/ast->update-plan host store (:tree doc) a {:actor actor :timestamp timestamp :base (:head store)}))]
        {:name (:name doc) :tree (:tree doc) :payload payload :before (:payload doc) :unchanged? same?
         :plan (when base
                 (-> base
                     (assoc :engines engines)
                     (update :ops into (binding-ops host store (:root doc) {:model-id (:id m) :text text :path path :actor actor
                                                                            :content-hash ch :inline? inline?} doc))))}))))

;; ---------------------------------------------------------------- generic documents (K3)

(defn generic-registrable?
  "True when a document without a model is registered by reference in `store`
   (class `generic-class`, tree as cache): the store is not a format-1 one and
   its content mode is not `:inline` — the rule typed documents follow. Otherwise its tree is persisted, as before K3."
  [store]
  (and (not (revision/legacy? store)) (not= :inline (:content store))))

(defn- generic-ops
  "Ops registering the file `path` with text `text` as the generic document
   `doc`/`tree-id`: its root node, its external anchor (whose edge records the
   class as `:contract` and the parse contract), and the `:register-tree`. No
   ownership edge, no block/text node and no tree object enters the plan.
   `old-edges` are the edge ids of the document being replaced (or nil): the
   ones to a file that are no longer wanted are removed."
  [host store {:keys [doc tree-id name text path actor]} old-edges]
  (let [a (assoc-in (ast/parse host text) [:attrs :doc] doc)
        reg (assoc (md/ast->tree host a {:tree-id tree-id :name name :class generic-class}) :tree-id tree-id)
        root (:root reg)
        ext (external-node host path text)
        xid (id-of host ext)
        wanted {:type :reference :from root :to xid
                :evidence {:ref-hash xid :actor actor :contract generic-class
                           :parse-contract (parse-contract/record host (parse-contract/environment) generic-class)}}
        wanted-id (:id (edge/make host wanted))
        stale (for [eid old-edges :let [e (get-in store [:objects eid])]
                    :when (and (not= eid wanted-id) (source-or-file? (target store e)))] eid)]
    {:reg reg
     :ops (-> [{:op :add-node :node (root-node host doc) :as :root}
               {:op :add-node :node ext :as :ext}
               {:op :register-tree :tree (dissoc (:descriptor reg) :tree)
                :id tree-id :root :root :merkle-root (:merkle-root reg) :as :t}
               {:op :add-edge :edge wanted}]
              (into (map (fn [eid] {:op :remove-edge :edge eid}) stale)))}))

(defn- describe-generic
  "The map of the generic document of tree `[tid d]` (`d` its descriptor), or
   nil when the tree is not one: `{:name :qualified :tree :root :doc :class
   :generic :path :fingerprint :parse-contract :merkle-root :edges}`."
  [store by-from [tid d]]
  (when (and (= :document (:kind d)) (generic-class? (:class d)))
    (let [root (:root d)
          es (get by-from root)
          ext-edge (some #(when (= :external (:kind (target store %))) %) es)
          ext (target store ext-edge)
          path (get-in ext [:content :locator :path])]
      (when ext-edge
        {:name (:name d) :qualified (or (qualified-path (:host store) path) (:name d))
         :tree tid :root root :doc (get-in store [:objects root :content :attrs :doc])
         :class (:class d) :generic true
         :path path :fingerprint (get-in ext [:content :fingerprint])
         :parse-contract (get-in ext-edge [:evidence :parse-contract])
         :merkle-root (second (revision/tree-entry store tid))
         :edges (mapv :id es)}))))

(defn generic-documents
  "Every document of `store` registered under a generic class (K3), by name
   then tree id."
  [store]
  (let [by-from (edges-by-from store)]
    (->> (revision/tree-descriptors store)
         (keep #(describe-generic store by-from %))
         (sort-by (juxt :name :tree)) vec)))

(defn generic-document
  "The document of a generic class identified by `token` (a tree id, a name or
   the path of its file; see `resolve-any`), or nil — also when `token` names
   a typed document. A shared name is `:docs/ambiguous`."
  [store token]
  (let [d (resolve-any store token)]
    (when (:generic d) d)))

(defn refuse-anchored!
  "Raises `:docs/exists` when the file `path` is already the anchor of a
   tracked document, typed or generic, other than tree `except` (nil for
   none). One sensed path, one document: the route registry of K2a (cut C2)
   rejects a duplicate path, so a generic registration does not create one.
   This is uniqueness of the locator, not an identity."
  [store path except]
  (let [q (qualified-path (:host store) path)
        holder (some #(when (and (:path %) (= q (:qualified %)) (not= except (:tree %))) %)
                     (concat (generic-documents store) (documents store)))]
    (when holder
      (err/raise :docs/exists
                 (str "the file " q " is already tracked as document `" (:name holder) "`")
                 {:path q :document (:name holder) :tree (:tree holder)}))))

(defn generic-root
  "The merkle root the Markdown `text` would be registered with as generic
   document `doc`: computed in memory, nothing is written. Sensing compares it
   with the registered root to tell a reformatting from an edit."
  [host doc text]
  (:merkle-root (md/ast->tree host (assoc-in (ast/parse host text) [:attrs :doc] (:doc doc))
                              {:tree-id (:tree doc) :name (:name doc) :class (:class doc)})))

(defn generic-plan
  "Plan that registers the file `path` (relative to the project) with text
   `text` as a new document `name` of the generic class: `{:plan :name :tree
   :blocks}`. `opts`: `:doc` and `:tree-id` (fresh ULIDs), `:actor`,
   `:timestamp`. The derived tree is written to the cache. Raises
   `:docs/exists` when the file is already tracked (`refuse-anchored!`)."
  [host store name text path {:keys [doc tree-id actor timestamp]}]
  (refuse-anchored! store path nil)
  (let [{:keys [reg ops]} (generic-ops host store {:doc doc :tree-id tree-id :name name :text text
                                                   :path path :actor actor} nil)]
    (cache-write! store reg {:fingerprint (fingerprint host text) :class generic-class})
    {:name name :tree tree-id :blocks (count (tree/children-at (:tree reg) []))
     :plan {:plan/version 1 :base (:head store) :actor actor :timestamp timestamp
            :engines (meta-engines {:operation "docs ingest" :address (str generic-class ":" name) :fields {}})
            :ops (vec ops)}}))

(defn generic-update-plan
  "Plan that registers again the generic document `doc` (a map of
   `generic-documents`) from `text`, at `path` (default: its recorded one):
   `{:plan :name :tree :blocks :unchanged?}`. `:plan` is nil when neither the
   fingerprint, the path nor the parse contract moved. Raises `:docs/exists`
   when `path` is already tracked by another document."
  [host store doc text {:keys [path actor timestamp operation]}]
  (let [path (or path (:path doc))
        _ (refuse-anchored! store path (:tree doc))
        same? (and (= (qualified-path host path) (:qualified doc))
                   (= (:fingerprint doc) (fingerprint host text))
                   (contains? #{:same :unrecorded}
                              (:status (parse-contract/compare-recorded host (parse-contract/environment)
                                                                        (:parse-contract doc) generic-class))))
        {:keys [reg ops]} (generic-ops host store {:doc (:doc doc) :tree-id (:tree doc) :name (:name doc)
                                                   :text text :path path :actor actor}
                                       (:edges doc))]
    (when-not same? (cache-write! store reg {:fingerprint (fingerprint host text) :class generic-class}))
    {:name (:name doc) :tree (:tree doc) :unchanged? same?
     :blocks (count (tree/children-at (:tree reg) []))
     :plan (when-not same?
             {:plan/version 1 :base (:head store) :actor actor :timestamp timestamp
              :engines (meta-engines {:operation (or operation "docs update")
                                      :address (str generic-class ":" (:name doc)) :fields {}})
              :ops (vec ops)})}))

(defn untrack-plan
  "Plan that stops tracking document `token`: the edges this surface owns (to
   its model, source and file) are removed, its tree stays in history; edges
   others wrote from it stay and become orphans their owners can report. Returns `{:plan :name :tree :path}`.
   `token` names a typed document or, failing that, one registered under a
   generic class (K3): its anchor edge is removed, so its file is no longer
   watched and its path and name are free again. Raises `:docs/missing`."
  [store token {:keys [actor timestamp operation]}]
  (let [doc (or (resolve-any store token)
                (err/raise :docs/missing (str "no document named or identified by `" token "`") {:document token}))]
    {:name (:name doc) :tree (:tree doc) :path (:path doc)
     :plan {:plan/version 1 :base (:head store) :actor actor :timestamp timestamp
            :engines (meta-engines {:operation (or operation "docs untrack")
                                    :address (str (or (:model doc) (:class doc)) ":" (:name doc)) :fields {}})
            :ops (vec (for [eid (:edges doc) :when (owned-edge? store (get-in store [:objects eid]))]
                        {:op :remove-edge :edge eid}))}}))

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
