(ns kimun.surface.index.address
  "v1's address surface over the index (docs/v2/06 §3), pure: `address?`
   routes a TERM, `parse` turns an address into a closed AST,
   `resolve-address` answers what the address denotes and `list-address`
   what hangs one level below it (v1 `ls`). Nothing here reads a store: the
   input is the federated view `[{:store name :index idx} …]`, current store
   first.

   Grammar (v1 `store/query_engine`, plus the unbraced form of 06 §3):

     address := [store ':'] st | se | gse
     st      := 'st'                                   models of the store
              | 'st.' store                            models of that store
              | 'st.' [store '.'] model ['.' doc ['.' field]]
     model   := '{' Name ['+'] '}' | '{*}' | Name ['+'] | '*'   (bare only after a store)
     doc     := one dotted segment, an fnmatch glob (`*` `?` `[seq]`)
     field   := a dotted path into the payload (`tasks.0.title`: keys into
                maps, integers into lists), or an fnmatch glob over the
                top-level field names when it carries `*`, `?` or `[`
     se      := 'se' | 'se.' tag          tag may be a glob: `*` one segment, `**` any run
     gse     := 'gse.' tag | 'gse.' tag '.se.{' store '}'

   `{Name}` is the exact model; `{Name+}` the model and every model with
   `Name` in its lineage, registered or not; `{*}` every model. The store is
   `local` (or absent) for the current one, else a name of the view; v1's
   `B:st.{M}` prefix and 06's `st.B.M` name the same thing. v1 has no list
   or alternation form.

   The line between an error and an empty answer: a string that is not in
   the address space at all (`address?` false, v1's `\"invalid\"`) resolves
   to `[]`, as do well-formed addresses naming a store, model, document,
   field or tag that does not exist; a string that claims the address space
   and breaks its grammar (`st.{Memo`, `se..x`, `gse`) raises
   `:query/bad-address` with `:address` and `:position`."
  (:require [clojure.string :as str]
            [kimun.surface.semantic :as sem]
            [kimun.kernel.err :as err]))

;; ---- recognising and parsing ----

(def ^:private prefix-re #"([A-Za-z0-9_.\-]+):(st(?:\..*)?)")

(defn address?
  "True when the string `s` claims the address space, so `find TERM` routes
   it here instead of searching text: no whitespace, and it is `st`, `se` or
   `gse`, starts with `st.`, `se.` or `gse.`, or is a `<store>:st…` prefixed
   form. Tighter than v1's `startswith(\"st\"|\"se\"|\"gse\")`, which took
   `status` for an address. It does not promise that `parse` succeeds."
  [s]
  (boolean (and (string? s) (not (re-find #"\s" s))
                (or (re-matches #"(?:st|se|gse)(?:\..*)?" s) (re-matches prefix-re s)))))

(defn- bad [s pos msg]
  (err/raise :query/bad-address (str msg " at position " pos " of `" s "`") {:address s :position pos}))

(defn- segment-end [s i] (or (str/index-of s "." i) (count s)))

(defn- model-at
  "`[name family? next-index]` of the model written at `i`; `bare?` allows the unbraced form."
  [s i bare?]
  (let [braced? (= \{ (get s i))
        close (when braced? (str/index-of s "}" i))
        end (if braced? (some-> close inc) (segment-end s i))
        _ (cond (and (not braced?) (not bare?)) (bad s i "expected {Model}")
                (nil? end) (bad s i "unclosed {"))
        inner (if braced? (subs s (inc i) close) (subs s i end))
        family? (and (str/ends-with? inner "+") (not= inner "+"))
        nm (cond-> inner family? (subs 0 (dec (count inner))))]
    (when (or (str/blank? nm) (re-find #"[{}+\s]" nm) (and (not braced?) (str/includes? nm ":")))
      (bad s (if braced? (inc i) i) "expected a model name"))
    [nm (boolean family?) end]))

(defn- parse-rest
  "The `.doc[.field]` tail starting at `i` (the index after the model), merged into `ast`."
  [s i ast]
  (if (= i (count s))
    ast
    (let [_ (when (not= \. (get s i)) (bad s i "expected `.` or the end"))
          j (segment-end s (inc i)) doc (subs s (inc i) j)
          field (when (< j (count s)) (subs s (inc j)))]
      (when (or (str/blank? doc) (re-find #"[{}]" doc)) (bad s (inc i) "expected a document name"))
      (when field
        (when-let [k (cond (or (str/blank? field) (str/starts-with? field ".")) 0
                           (str/includes? field "..") (inc (str/index-of field ".."))
                           (str/ends-with? field ".") (count field))]
          (bad s (+ j 1 k) "expected a field name")))
      (cond-> (assoc ast :doc doc) field (assoc :field field)))))

(defn- parse-st [s off prefixed]
  (let [i (+ off 3) root {:space :st :store prefixed}]
    (cond
      (= (count s) (+ off 2)) root
      (= i (count s)) (bad s i "expected a store or {Model}")
      (= \{ (get s i)) (let [[m f? j] (model-at s i false)] (parse-rest s j (assoc root :model m :family? f?)))
      :else
      (let [j (segment-end s i) store (subs s i j)]
        (cond
          (not (re-matches #"[A-Za-z0-9_\-]+" store)) (bad s i "expected a store name or {Model}")
          prefixed (bad s i "the store is named twice")
          (= j (count s)) (assoc root :store store)
          (= (inc j) (count s)) (bad s (inc j) "expected a model")
          :else (let [[m f? k] (model-at s (inc j) true)] (parse-rest s k (assoc root :store store :model m :family? f?))))))))

(defn- tag-at
  "The tag written from `i` to the end of `s`, checked segment by segment."
  [s i]
  (let [tag (subs s i)]
    (when-let [k (cond (or (str/blank? tag) (str/starts-with? tag ".")) 0
                       (str/includes? tag "..") (inc (str/index-of tag ".."))
                       (str/ends-with? tag ".") (count tag)
                       :else (some-> (re-find #"[{}]" tag) (->> (str/index-of tag))))]
      (bad s (+ i k) "expected a tag segment"))
    tag))

(defn parse
  "The closed AST of the address `s`, or `:query/bad-address` (ex-data
   `{:address s :position n}`, `n` the 0-based index of the offending
   character; 0 when `s` is not an address at all). Every node is a map
   with `:space`:

     {:space :st :store S}                                 `st`, `st.B`, `B:st`: the models of a store
     {:space :st :store S :model M :family? bool}          `st.{M}`, `st.{M+}`, `st.B.M`: its documents
     {… :doc GLOB}                                         `st.{M}.memo*`: documents by name
     {… :doc GLOB :field PATH-OR-GLOB}                     `st.{M}.memo1.status`: fields
     {:space :se :tag nil}                                 `se`: the root tags
     {:space :se :tag PATTERN}                             `se.type.note`, `se.type.*`
     {:space :gse :tag PATTERN}                            `gse.topic.x`: every store of the view
     {:space :gse :tag TAG :bridge STORE}                  `gse.topic.x.se.{B}`: B's tags that mean it

   `S` is nil for the current store (also what `local` parses to), `M` is a
   model name or `\"*\"`."
  [s]
  (let [[_ pre body] (when (string? s) (re-matches prefix-re s))
        off (if pre (inc (count pre)) 0)
        pre (when (and pre (not= pre "local")) pre)]
    (cond
      (or (not (string? s)) (not (address? s))) (bad (str s) 0 "not an address (st…, se…, gse…)")
      (or body (re-matches #"st(\..*)?" s))
      (let [ast (parse-st s off pre)] (cond-> ast (= "local" (:store ast)) (assoc :store nil)))
      (= s "se") {:space :se :tag nil}
      (str/starts-with? s "se.") {:space :se :tag (tag-at s 3)}
      (= s "gse") (bad s 3 "gse needs a tag")
      :else (if-let [[_ tag store] (re-matches #"gse\.(.+)\.se\.\{([^{}]+)\}" s)]
              {:space :gse :tag (do (tag-at (str "gse." tag) 4) tag) :bridge store}
              {:space :gse :tag (tag-at s 4)}))))

;; ---- resolving ----

(defn- fnmatch?
  "Python `fnmatch` over `s`: `*` any run, `?` one character, `[seq]`/`[!seq]` a class."
  [pattern s]
  (let [re (loop [i 0 out ""]
             (if (>= i (count pattern))
               out
               (let [c (get pattern i) close (when (= c \[) (str/index-of pattern "]" (+ i 2)))]
                 (cond
                   (= c \*) (recur (inc i) (str out ".*"))
                   (= c \?) (recur (inc i) (str out "."))
                   close (let [body (subs pattern (inc i) close) neg? (str/starts-with? body "!")]
                           (recur (inc close) (str out "[" (when neg? "^") (str/replace (cond-> body neg? (subs 1)) #"[\\\[\]^]" "\\\\$0") "]")))
                   :else (recur (inc i) (str out (str/replace (str c) #"[^A-Za-z0-9_]" "\\\\$0")))))))]
    (boolean (re-matches (re-pattern re) s))))

(defn- glob? [s] (boolean (re-find #"[*?\[]" s)))

(defn- stores-of
  "`[[current? entry] …]` of the view entries the store name `store` denotes (nil and `local`: the current one)."
  [view store]
  (keep-indexed (fn [i e] (when (if (or (nil? store) (= "local" store)) (zero? i) (= store (:store e))) [(zero? i) e])) view))

(defn- docs-of [idx] (sort-by :tree (vals (:docs idx))))

(defn- in-model? [{:keys [model family?]} doc]
  (or (= "*" model) (= model (:model doc)) (boolean (and family? (some #{model} (:bases doc))))))

(defn- scope-text [{:keys [model family?]}] (str "st.{" model (when family? "+") "}"))

(defn- doc-result [store doc address]
  {:store store :tree (:tree doc) :name (:name doc) :model (:model doc) :path (:path doc) :kind :doc :address address})

(defn- payload-fields
  "`[[name value] …]` of the top-level fields of `doc`, by name."
  [doc]
  (sort-by first (map (fn [[k v]] [(if (keyword? k) (name k) (str k)) v]) (:payload doc))))

(defn- walk-path
  "The value at the dotted `path` of `payload`, or `::none` when it names nothing."
  [payload path]
  (reduce (fn [v part]
            (cond
              (and (map? v) (contains? v part)) (get v part)
              (and (map? v) (contains? v (keyword part))) (get v (keyword part))
              (and (sequential? v) (re-matches #"\d+" part) (< (parse-long part) (count v))) (nth v (parse-long part))
              :else (reduced ::none)))
          payload (str/split path #"\.")))

(defn- field-results [base doc field]
  (for [[k v] (if (glob? field)
                (filter #(fnmatch? field (first %)) (payload-fields doc))
                (let [v (walk-path (:payload doc) field)] (when (not= ::none v) [[field v]])))]
    (assoc base :kind :field :field k :value v :address (str (:address base) "." k))))

(defn- model-results [store prefix idx]
  (for [m (sort (keys (:models idx)))]
    {:store store :tree nil :name m :model m :path nil :kind :model :address (str prefix "st.{" m "}")}))

(defn- st-results [{:keys [store model doc field] :as ast} view]
  (for [[current? {s :store idx :index}] (stores-of view store)
        :let [prefix (if current? "" (str s ":"))]
        r (if (nil? model)
            (model-results s prefix idx)
            (for [d (docs-of idx) :when (and (in-model? ast d) (or (nil? doc) (fnmatch? doc (:name d))))
                  :let [base (cond-> (doc-result s d (str prefix (scope-text ast) "." (:name d)))
                               doc (assoc :payload (:payload d)))]
                  r (if field (field-results (dissoc base :payload) d field) [base])]
              r))]
    r))

(defn- tag-result [store tag shown]
  {:store store :tree nil :name shown :model nil :path nil :kind :tag :tag tag :address (str "se." tag)})

(defn- se-results [{:keys [tag]} view]
  (let [{s :store idx :index} (first view) dag (:tag-dag idx)]
    (if (nil? tag)
      (map #(tag-result s % %) (sem/roots dag))
      (for [d (docs-of idx) :when (sem/in-scope? dag tag (:tags d))]
        (doc-result s d (str "st.{" (:model d) "}." (:name d)))))))

(defn- equivalences
  "`{tag #{tag}}` declared in `idx`: its `:equivalences` and those of its `:tag-dag`."
  [idx]
  (reduce (fn [m [k vs]] (update m k (fnil into #{}) vs)) {} (concat (:equivalences idx) (get-in idx [:tag-dag :equivalences]))))

(defn- global-tags
  "The global tags a document carrying `tags` answers to (v1 `_has_global_tag`):
   what it reaches in its own DAG when the store is the current one, plus what
   those tags were declared equivalent to, in either direction."
  [idx current? tags]
  (let [local (sem/reach (:tag-dag idx) tags) eq (equivalences idx)]
    (-> (if current? local #{})
        (into (mapcat eq) local)
        (into (keep (fn [[k vs]] (when (some local vs) k))) eq))))

(defn- gse-results [{:keys [tag bridge]} view]
  (if bridge
    (for [[_ {s :store idx :index}] (stores-of view bridge)
          t (sort (keep (fn [[k vs]] (when (contains? vs tag) k)) (equivalences idx)))]
      (tag-result s t t))
    (for [[i {s :store idx :index}] (map-indexed vector view)
          d (docs-of idx)
          :let [reached (global-tags idx (zero? i) (:tags d))]
          :when (if (str/includes? tag "*") (some #(sem/matches? % tag) reached) (contains? reached tag))]
      (doc-result s d (str s ":st.{" (:model d) "}." (:name d))))))

(defn- ast-of
  "The AST of `a`: itself when already parsed, nil when the string is not an address."
  [a]
  (cond (map? a) a (address? a) (parse a)))

(defn resolve-address
  "What the address `a` (a string or a `parse` AST) denotes in `view`
   (`[{:store name :index idx} …]`, current store first): a vector of
   result maps, in view order then by tree id (models and tags by name,
   fields by name within their document). Every result carries
   `{:store :tree :name :model :path :kind :address}`:

     `st`, `st.B`            :kind :model  one per model of the store (`:tree`/`:path` nil)
     `st.{M}`, `st.{M+}`     :kind :doc    the documents of the model / of its family
     `st.{M}.memo*`          :kind :doc    those matching the glob, plus `:payload`
     `st.{M}.memo1.status`   :kind :field  plus `:field` (as addressed) and `:value`
     `se`                    :kind :tag    the root tags (`:tag`), current store
     `se.<tag>`              :kind :doc    current store, through `:tag-dag` (`sem/in-scope?`)
     `gse.<tag>`             :kind :doc    every store: the current one by what its documents
                                           reach, any store by its `:equivalences`
     `gse.<tag>.se.{B}`      :kind :tag    B's tags declared equivalent to the tag

   `:address` is v1's text for the result: the scope as written plus the
   name for `st` (`st.{Base+}.memo1`, `B:` prefixed outside the current
   store), `st.{Model}.name` for `se`, `<store>:st.{Model}.name` for `gse`.
   A string that is not an address, or an address naming nothing that
   exists, answers `[]`; a malformed address raises `:query/bad-address`."
  [a view]
  (let [ast (ast-of a)]
    (vec (case (:space ast)
           :st (st-results ast view)
           :se (se-results ast view)
           :gse (gse-results ast view)
           nil))))

(defn- description [idx model field]
  (let [fs (get-in idx [:models model :fields])
        f (if (map? fs) (or (get fs field) (get fs (keyword field))) (first (filter #(= field (some-> (:name %) name)) fs)))]
    (when (map? f) (or (:description f) (get f "description")))))

(defn list-address
  "v1's `ls`: what hangs one level below the address `a` in `view`, as
   `resolve-address` result maps. `st` lists models, `st.{M}` documents,
   `st.{M}.doc` the top-level fields of each matching document (`:kind
   :field` with `:value` and the model's `:description`, nil when it has
   none), `se` the root tags, `se.<tag>` the child tags (`:name` relative to
   the parent) or, when it has none, the documents carrying exactly that
   tag; `se.<glob>` the tags matching the glob. Everything else lists as it
   resolves. Errors and empties as in `resolve-address`."
  [a view]
  (let [{:keys [space tag doc field] :as ast} (ast-of a)
        {s :store idx :index} (first view) dag (:tag-dag idx)]
    (cond
      (and (= :st space) doc (not field))
      (vec (for [r (resolve-address ast view) :let [i (:index (first (filter #(= (:store r) (:store %)) view)))]
                 [k v] (payload-fields r)]
             (-> (dissoc r :payload)
                 (assoc :kind :field :field k :value v :description (description i (:model r) k) :address (str (:address r) "." k)))))
      (and (= :se space) tag (str/includes? tag "*")) (mapv #(tag-result s % %) (sem/glob-tags dag tag))
      (and (= :se space) tag)
      (if-let [kids (seq (sem/children dag tag))]
        (vec (sort-by :name (map #(tag-result s % (sem/relative % tag)) kids)))
        (vec (for [d (docs-of idx) :when (some #{tag} (:tags d))] (doc-result s d (str "st.{" (:model d) "}." (:name d))))))
      :else (resolve-address ast view))))
