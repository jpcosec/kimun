(ns kimun.surface.index.search
  "`find` as a pure function over a federated view `[{:store name :index idx}]`
   (docs/v2/06 §3; v1 `sldb.api.search` + `sldb.api.records`). No I/O.

   `records` turns a view into v1's search records — one per store, model,
   document, document section and payload field — and `search` matches a TERM
   against them: the physical layer (names, paths, section titles, field
   values, document text) and/or the semantic layer (tags, everything they
   reach through the DAG, their equivalents). A TERM that is an address
   (`kimun.surface.index.address`) is resolved as one instead. Results are
   `{:store :tree :name :model :path :kind :matches …}` ordered by store name,
   then tree id, then kind (sections in document order, the rest by name)."
  (:require [clojure.string :as str]
            [kimun.surface.index.address :as address]
            [kimun.surface.index.where :as where]
            [kimun.surface.models.segments :as segments]
            [kimun.surface.semantic :as sem]
            [kimun.host.default :as host]
            [kimun.kernel.err :as err]
            [kimun.surface.index.corpus :as corpus]))

(def kinds "The record kinds `find` knows, as `--type` names them." #{:store :model :doc :section :field :tag})
(def layers "The values of `--in`." #{:physical :semantic :all})
(def fuzzy-threshold "Dice coefficient from which `--fuzzy` accepts a value (06 §3)." 0.7)
(def ^:private plural-terms {"stores" :store "models" :model "docs" :doc "sections" :section "fields" :field})
(def ^:private kind-rank {:store 0 :model 1 :tag 2 :doc 3 :section 4 :field 5})

;; ---------------------------------------------------------------- records

(defn- flat
  "`[[dotted-path value] …]` of the leaves of `payload` (v1 `flatten_payload`)."
  [prefix v]
  (cond (map? v) (if (empty? v) [[prefix v]] (mapcat (fn [[k x]] (flat (if prefix (str prefix "." (name k)) (name k)) x)) (sort-by (comp name key) v)))
        (sequential? v) (if (empty? v) [[prefix v]] (mapcat (fn [i x] (flat (str prefix "." i) x)) (range) v))
        :else [[prefix v]]))

(defn- reach
  "Tags `tags` answers to: themselves, everything above them and the equivalents of all of those."
  [idx tags]
  (let [up (sem/reach (:tag-dag idx) tags)]
    (vec (sort (into up (mapcat #(get (:equivalences idx) %)) up)))))

(defn- with-breadcrumbs
  "`secs` (`segments/sections` maps) with `:breadcrumbs`: the titles from the outermost heading down to its own."
  [secs]
  (let [title (into {} (map (juxt :path :title)) secs)]
    (mapv (fn [s] (let [parts (str/split (:path s) #"/")]
                    (assoc s :breadcrumbs (mapv #(get title (str/join "/" (take % parts)) (:title s)) (range 1 (inc (count parts)))))))
          secs)))

(defn- doc-sections
  "The rendered sections of `doc` with `:breadcrumbs`."
  [doc]
  (with-breadcrumbs (if (string? (:source doc)) (segments/sections (:source doc)) [])))

(defn- owners
  "`{field section-path}` for `doc`: over its rendered sections, else the model's template sections."
  [idx doc secs]
  (let [m (get-in idx [:models (:model doc)])
        rendered (when (and (:template m) (seq secs)) (segments/field-owners m (mapv #(dissoc % :breadcrumbs) secs)))]
    (if (seq rendered) rendered (get-in idx [:sections (:model doc) :owners] {}))))

(defn- doc-records [store idx doc]
  (let [base {:store store :tree (:tree doc) :model (:model doc) :doc (:name doc)}
        sem-tags (reach idx (:tags doc))
        secs (doc-sections doc)
        own (owners idx doc secs)
        top (fn [f] (first (str/split f #"\.")))]
    (concat
     [(assoc base :kind :doc :name (:name doc) :path (:path doc) :tags (vec (:tags doc))
             :physical [(:name doc) (:path doc) (str (:model doc) "/" (:name doc)) (:source doc)] :semantic sem-tags :entry doc)]
     (for [s secs :let [about (into (vec (:breadcrumbs s)) (:tags doc))]]
       (assoc base :kind :section :name (:slug s) :title (:title s) :path (str (:path doc) "#" (:path s)) :section (:path s)
              :level (:level s) :line (:line_start s) :breadcrumbs (:breadcrumbs s) :about about
              :physical [(:title s) (:slug s) (:path s) (:path doc)] :semantic (into sem-tags about) :entry doc))
     (for [[f v] (when (map? (:payload doc)) (flat nil (:payload doc)))]
       (assoc base :kind :field :name f :field f :value v :path (str (:path doc) ":" f) :owning-section (get own f (get own (top f)))
              :physical [f (:name doc) (:path doc) (:model doc) (when (string? v) v)] :semantic sem-tags :entry doc)))))

(defn- generic-records
  "The records of the generic document `g` (a `:generics` entry of the index,
   no model): a `:doc` (its name, path and the text before the first heading)
   and a `:section` per heading carrying the section's own text as `:body`
   (what `matches-of` reads for the text of a document without fields)."
  [store g]
  (let [base {:store store :tree (:tree g) :model nil :class (:class g) :doc (:name g)}
        entry {:tree (:tree g) :doc (:doc g) :name (:name g) :model nil :bases [] :tags [] :path (:path g) :class (:class g)}
        secs (with-breadcrumbs (:sections g))]
    (cons (assoc base :kind :doc :name (:name g) :path (:path g) :tags []
                 :physical [(:name g) (:path g) (str (:class g) "/" (:name g))] :semantic [] :entry entry
                 :body (:preamble g) :body-line 1)
          (for [s secs]
            (assoc base :kind :section :name (:slug s) :title (:title s) :path (str (:path g) "#" (:path s)) :section (:path s)
                   :level (:level s) :line (:line_start s) :breadcrumbs (:breadcrumbs s) :about (:breadcrumbs s)
                   :physical [(:title s) (:slug s) (:path s) (:path g)] :semantic (:breadcrumbs s) :entry entry
                   :body (:text s) :body-line (inc (:line_start s)))))))

(defn records
  "The search records of `view` (v1 `iter_search_records`), unordered: per
   store one `:store` record, one `:model` per model and, per document, one
   `:doc`, one `:section` per heading of its text and one `:field` per payload
   leaf. A document without model (`:generics` of the index, K3) gives a `:doc`
   and its `:section`s with `:model nil`, `:class` and, in `:body`, the text of
   the section (of the `:doc`: the text before its first heading). Besides the result keys each carries `:physical` and `:semantic`
   (the strings TERM is matched against) and `:entry` (its index document)."
  [view]
  (vec (mapcat (fn [{:keys [store index]}]
                 (concat [{:kind :store :store store :tree nil :name store :model nil :path nil :physical [store] :semantic []}]
                         (for [[n m] (:models index)]
                           {:kind :model :store store :tree nil :name n :model n :path nil :physical [n] :semantic (reach index (:tags m))})
                         (mapcat #(doc-records store index %) (vals (:docs index)))
                         (mapcat #(generic-records store %) (vals (:generics index)))))
               view)))

;; ---------------------------------------------------------------- matching

(defn- matcher
  "`(fn [value] bool)` for `term` under the flags (v1 `_matches_term`)."
  [term {:keys [regex? fuzzy?]}]
  (cond regex? (let [re (try (re-pattern term)
                             (catch #?(:clj Exception :cljs :default) e
                               (err/raise :query/bad-regex (str "bad --regex term " (pr-str term) ": " (ex-message e)) {:term term})))]
                 #(boolean (re-find re %)))
        fuzzy? (let [t (str/lower-case term)]
                 (fn [v] (let [v (str/lower-case v)]
                           (boolean (or (str/includes? v t)
                                        (some #(>= (corpus/dice host/host t %) fuzzy-threshold) (cons v (str/split v #"\s+"))))))))
        :else #(str/includes? % term)))

(defn- clip [v] (if (> (count v) 120) (str (subs v 0 120) "…") v))

(defn- body-match
  "`{:in :physical :value line :line n}` for the first line of `record`'s `:body` that `match?` accepts, or nil."
  [record match?]
  (when-let [body (:body record)]
    (some (fn [[i l]] (when (and (not= "" l) (match? l)) {:in :physical :value (clip (str/trim l)) :line (+ i (:body-line record 1))}))
          (map-indexed vector (str/split body #"\r\n|\n|\r")))))

(defn- matches-of [record match? in]
  (vec (concat
        (for [[layer k] [[:physical :physical] [:semantic :semantic]]
              :when (or (= in :all) (= in layer))
              v (distinct (get record k)) :when (and (string? v) (not= "" v) (match? v))]
          {:in layer :value (clip v)})
        (when (#{:all :physical} in) (some-> (body-match record match?) vector)))))

(defn- where-doc
  "What `--where` sees for `record` (v1 `RecordFilters`): the document itself
   for a `:doc`; for a `:field` or `:section` the document with the record's
   own attributes as payload; nil (always passes) for the other kinds."
  [{:keys [kind entry] :as r}]
  (case kind
    :doc entry
    :field (assoc entry :payload {"value" (:value r) "doc" (:doc r) "model" (:model r) "field" (:field r) "path" (:path r)
                                  "owning_section" (or (:owning-section r) "")})
    :section (assoc entry :payload {"title" (:title r) "slug" (:name r) "path" (:section r) "level" (:level r) "doc" (:doc r)
                                    "about" (:about r) "breadcrumbs" (:breadcrumbs r) "semantic_tags" (vec (:tags entry))})
    nil))

(defn- passes? [ast record]
  (or (nil? ast) (let [d (where-doc record)] (or (nil? d) (boolean (where/matches? ast d))))))

(defn- public [record] (dissoc record :physical :semantic :entry :body :body-line))

(defn- order [results]
  (vec (sort-by (juxt #(str (:store %)) #(str (:tree %)) #(kind-rank (:kind %) 9) #(:line % 0) #(str (:name %))) results)))

;; ---------------------------------------------------------------- addresses

(defn warnings
  "The warnings `search` implies for `view` (as `search` reads it: only its
   first store unless `global?`): one `find/unreadable-document` per generic
   document whose text could not be read, which is therefore not searchable
   by text. `[{:type :store :document :path :message}]`."
  [view global?]
  (vec (for [{:keys [store index]} (if global? view (take 1 view))
             g (sort-by :tree (vals (:generics index))) :when (:error g)]
         {:type "find/unreadable-document" :store store :document (:name g) :path (:path g) :message (:error g)})))

(defn address-term?
  "Whether TERM is searched as an address: `address/address?` says so and it
   is not the bare word `st`, `se` or `gse` (those stay plain text)."
  [term]
  (boolean (and (string? term) (address/address? term) (not (contains? #{"st" "se" "gse"} term)))))

(defn- by-address
  "The records `term` resolves to in `view` (lane B's `resolve-address`), each with its index document as `:entry`."
  [term view]
  (let [idx (into {} (map (juxt :store :index)) view)]
    (for [r (address/resolve-address term view)
          :let [entry (get-in idx [(:store r) :docs (:tree r)])]]
      (cond-> (assoc r :matches [{:in :address :value (:address r)}] :entry entry)
        (and entry (#{:doc :field} (:kind r))) (assoc :doc (:name entry))
        (= :field (:kind r)) (assoc :name (:field r) :path (str (:path r) ":" (:field r))
                                    :owning-section (let [own (owners (get idx (:store r)) entry (doc-sections entry))]
                                                      (get own (:field r) (get own (first (str/split (str (:field r)) #"\."))))))))))

;; ---------------------------------------------------------------- search

(defn search
  "The results of searching `term` in `view`, ordered by store name, then tree
   id, then kind (store, model, tag, doc, section, field) and name. `opts`:
   `:in` `:physical`|`:semantic`|`:all` (default `:all`); `:kinds` a set of
   `kinds` or nil; `:regex?`; `:fuzzy?` (Dice ≥ `fuzzy-threshold` against a
   value or one of its words, or containment, case-insensitive); `:where` an
   AST of `kimun.surface.index.where/parse`; `:global?` — without it only the
   first (current) store of the view is searched. An address TERM
   (`address-term?`) resolves over the whole view, since an address names its
   own store; a malformed one raises `:query/bad-address`. As in v1, TERM
   `stores|models|docs|sections|fields` lists every record of that kind and an
   empty TERM matches every record. Each result is `{:store :tree :name :model
   :path :kind :matches [{:in layer :value s}] …}` plus, by kind, `:doc`,
   `:field :value :owning-section`, `:title :section :level :line :breadcrumbs
   :about`, `:tags`, `:class` (documents and sections without model: `:model`
   is nil; a match in their text is `{:in :physical :value line :line n}`), or the keys of the address resolver (`:address`)."
  [view term {:keys [in kinds where global?] :as opts}]
  (let [in (or in :all)
        kind-ok? #(or (empty? kinds) (contains? kinds (:kind %)))
        found (if (address-term? term)
                (filter kind-ok? (by-address term view))
                (let [rs (records (if global? view (vec (take 1 view))))]
                  (cond (plural-terms term) (map #(assoc % :matches []) (filter #(= (plural-terms term) (:kind %)) rs))
                        (= "" (str term)) (map #(assoc % :matches []) (filter kind-ok? rs))
                        :else (let [match? (matcher term opts)]
                                (for [r (filter kind-ok? rs) :let [ms (matches-of r match? in)] :when (seq ms)]
                                  (assoc r :matches ms))))))]
    (order (map public (filter #(passes? where %) found)))))
