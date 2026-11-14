(ns kimun.surface.adapters
  "Pure planner of the surface adapters (docs/v2/11): adapter facts (protocol 1
   JSON, parsed to string-keyed maps) → planned documents → sync plan → drift
   report. Ports v1 `sldb.selfdoc` (python_materialize, materialize, projection,
   report, citations) without I/O: no files, no clock, and hashing only through
   the `:sha256` function the caller injects."
  (:require [clojure.string :as str]))

(def protocol "Adapter protocol version this planner understands." 1)

(def profile-models
  "Profile → names of the frozen models (resources/kimun/adapters/models) it writes."
  {"symbols" ["PythonSymbolDoc"] "cli" ["CliCommandDoc" "SurfaceDoc"]})

(def model-dirs
  "Model name → sub-directory of the output dir holding its documents."
  {"PythonSymbolDoc" "symbols" "CliCommandDoc" "commands" "SurfaceDoc" "surfaces"})

(def authored-defaults
  "Model name → authored fields of a NEW document (v1 `_defaults` plus the model
   default `tags: []`); CliCommandDoc also gets `usage` from the command."
  {"PythonSymbolDoc" {"purpose" "Not documented." "architecture" "Not documented." "tags" []}
   "CliCommandDoc" {"purpose" "Not documented." "how_it_works" "Not documented." "tags" []}
   "SurfaceDoc" {"purpose" "Not documented." "how_it_works" "Not documented." "tags" []}})

(def fact-keys
  "Keys every fact must carry (CONTRACT.md: always present, null/[] when n/a)."
  ["id" "kind" "name" "qualified_name" "path" "span" "source_hash" "signature" "synopsis" "doc" "relations" "attrs"])

;; ------------------------------------------------- Python-compatible JSON

(defn- code [c] #?(:clj (int c) :cljs (.charCodeAt c 0)))

(defn- hex4 [n]
  (let [digits "0123456789abcdef"]
    (apply str "\\u" (map #(nth digits (bit-and (bit-shift-right n %) 15)) [12 8 4 0]))))

(defn- json-str [s ascii?]
  (str "\"" (apply str (map (fn [c]
                              (let [n (code c)]
                                (case n 34 "\\\"" 92 "\\\\" 10 "\\n" 13 "\\r" 9 "\\t" 8 "\\b" 12 "\\f"
                                      (if (or (< n 32) (and ascii? (> n 126))) (hex4 n) (str c)))))
                            s)) "\""))

(defn py-json
  "`v` as Python `json.dumps` writes it. opts: `:ascii?` (ensure_ascii, escapes
   non-ASCII as lowercase \\uXXXX UTF-16 units), `:sort?` (sort_keys; otherwise
   map order, so pass array-maps), `:indent` n (pretty form, as `indent=n`)."
  ([v opts] (py-json v opts 0))
  ([v {:keys [ascii? sort? indent] :as opts} depth]
   (let [pad (fn [d] (apply str (repeat (* (or indent 0) d) " ")))
         wrap (fn [open close items]
                (cond (empty? items) (str open close)
                      indent (str open "\n" (str/join ",\n" (map #(str (pad (inc depth)) %) items)) "\n" (pad depth) close)
                      :else (str open (str/join ", " items) close)))]
     (cond (nil? v) "null" (true? v) "true" (false? v) "false"
           (string? v) (json-str v ascii?)
           (map? v) (wrap "{" "}" (map (fn [[k x]] (str (json-str (str k) ascii?) ": " (py-json x opts (inc depth))))
                                       (if sort? (sort-by (comp str key) v) v)))
           (sequential? v) (wrap "[" "]" (map #(py-json % opts (inc depth)) v))
           :else (str v)))))

;; ------------------------------------------------------------- validate

(defn- problem [code message fact] {:code code :message message :fact fact})

(defn- relative-posix? [p]
  (and (string? p) (seq p) (not (str/starts-with? p "/")) (not (str/includes? p "\\"))
       (not (re-find #"^[A-Za-z]:" p)) (not-any? #{".." ""} (str/split p #"/" -1))))

(defn- span-ok? [s]
  (or (nil? s)
      (and (map? s) (= #{"start_line" "end_line"} (set (keys s)))
           (every? #(and (integer? %) (pos? %)) (vals s)) (<= (s "start_line") (s "end_line")))))

(defn- relations-ok? [rs]
  (and (sequential? rs)
       (every? #(and (map? %) (string? (% "type")) (seq (% "type")) (string? (% "target"))) rs)))

(defn- fact-problems [f]
  (let [id (when (map? f) (f "id"))
        missing (when (map? f) (remove #(contains? f %) fact-keys))]
    (cond
      (not (map? f)) [(problem :adapters/fact "a fact must be a JSON object" nil)]
      (seq missing) [(problem :adapters/missing-key (str "fact lacks required keys: " (str/join ", " missing)) id)]
      :else
      (cond-> []
        (not (and (string? id) (seq id))) (conj (problem :adapters/empty-id "fact id must be a non-empty string" id))
        (not (every? #(string? (f %)) ["kind" "name" "qualified_name"]))
        (conj (problem :adapters/fact "kind, name and qualified_name must be strings" id))
        (not (or (nil? (f "path")) (relative-posix? (f "path"))))
        (conj (problem :adapters/path (str "path must be relative with POSIX separators: " (f "path")) id))
        (not (span-ok? (f "span"))) (conj (problem :adapters/span "span must be null or {start_line,end_line} with 1 <= start <= end" id))
        (not (or (nil? (f "source_hash")) (and (string? (f "source_hash")) (re-matches #"sha256:[0-9a-f]{64}" (f "source_hash")))))
        (conj (problem :adapters/source-hash "source_hash must be null or \"sha256:<64 hex>\"" id))
        (not (relations-ok? (f "relations"))) (conj (problem :adapters/relations "relations must be a list of {type,target} strings" id))
        (not (map? (f "attrs"))) (conj (problem :adapters/attrs "attrs must be an object" id))))))

(defn validate
  "Contract violations of a parsed adapter `output`, `[]` when conformant:
   `[{:code kw :message str :fact id-or-nil}]`. Codes: :adapters/protocol,
   /profile, /facts, /fact, /missing-key, /empty-id, /duplicate-id, /path,
   /span, /source-hash, /relations, /attrs."
  [output]
  (let [facts (when (map? output) (output "facts"))]
    (cond
      (not (map? output)) [(problem :adapters/protocol "adapter output must be a JSON object" nil)]
      (not= protocol (output "protocol")) [(problem :adapters/protocol (str "unsupported protocol: " (pr-str (output "protocol"))) nil)]
      (not (contains? profile-models (output "profile"))) [(problem :adapters/profile (str "unknown profile: " (pr-str (output "profile"))) nil)]
      (not (sequential? facts)) [(problem :adapters/facts "facts must be a list" nil)]
      :else
      (let [dups (for [[id n] (frequencies (keep #(when (map? %) (% "id")) facts)) :when (and (> n 1) (string? id) (seq id))] id)]
        (into (vec (mapcat fact-problems facts))
              (map #(problem :adapters/duplicate-id (str "duplicate fact id: " %) %) (sort dups)))))))

;; ------------------------------------------------------------- symbols

(defn provenance-prefix
  "Provenance tag of a symbols adapter: v1's `python-ast` for `python-symbols`,
   the adapter name itself for every other adapter."
  [adapter]
  (if (= adapter "python-symbols") "python-ast" adapter))

(defn- module-of
  "The enclosing module fact of `fact`, following contained-in relations."
  [by-id fact]
  (loop [f fact seen #{}]
    (cond (nil? f) nil
          (= "module" (f "kind")) f
          (seen (f "id")) nil
          :else (recur (by-id (some #(when (= "contained-in" (% "type")) (% "target")) (f "relations")))
                       (conj seen (f "id"))))))

(defn v1-symbol-record
  "The v1 `PythonSymbol` record of a symbol `fact` and its `module` fact (v1
   field names). imports = the module fact's `attrs.imports` list when it has
   one (python-symbols: v1's exact sorted list; its `imports` relations also
   carry resolved targets), else its `imports` relations in emitted order."
  [language fact module]
  (let [m (module "qualified_name") q (fact "qualified_name")]
    {"id" (str language ":" m ":" q)
     "kind" (get (fact "attrs") "native_kind" (fact "kind"))
     "module" m "qualname" q "path" (fact "path")
     "line_start" (get (fact "span") "start_line") "line_end" (get (fact "span") "end_line")
     "signature" (fact "signature") "docstring" (fact "doc")
     "source_sha256" (some-> (fact "source_hash") (str/replace-first "sha256:" ""))
     "imports" (let [declared (get (module "attrs") "imports")]
                 (if (sequential? declared)
                   (vec declared)
                   (vec (keep #(when (= "imports" (% "type")) (% "target")) (module "relations")))))}))

(defn v1-provenance
  "v1 `python_materialize._fields` provenance, byte-exact:
   `<prefix>:<record id>; contract-sha256:<sha256 of json.dumps(record, sort_keys=True)>`
   (`sha256`: string → hex of its UTF-8 bytes)."
  [prefix record sha256]
  (str prefix ":" (record "id") "; contract-sha256:" (sha256 (py-json record {:ascii? true :sort? true}))))

(defn- lower [s] #?(:clj (.toLowerCase ^String s java.util.Locale/ROOT) :cljs (str/lower-case s)))

(defn- safe-id [s]
  (-> (lower s) (str/replace #"[^a-z0-9_]+" "-") (str/replace #"^-+|-+$" "")))

(defn- symbol-doc [record {:keys [language system output spec prefix sha256]} fact-id]
  (let [id (str language "-" system "-" (safe-id (str (record "module") "-" (record "qualname"))))]
    {:name id :model "PythonSymbolDoc" :path (str output "/symbols/" id ".md") :fact fact-id
     :fields {"id" id "system" system "module" (record "module") "qualname" (record "qualname")
              "kind" (record "kind") "source_path" (record "path")
              "source_span" (str (record "line_start") ":" (record "line_end"))
              "source_sha256" (record "source_sha256")
              "signature" (or (not-empty (record "signature")) "Not applicable.")
              "docstring" (or (not-empty (record "docstring")) "Not documented.")
              "imports" (py-json (record "imports") {:ascii? true})
              "architecture_spec" spec
              "provenance" (v1-provenance prefix record sha256)}
     :defaults (authored-defaults "PythonSymbolDoc")}))

(defn- plan-symbols [output ctx]
  (let [facts (output "facts") by-id (into {} (map (juxt #(% "id") identity)) facts)]
    (reduce (fn [acc f]
              (let [m (module-of by-id f)]
                (cond
                  (= "module" (f "kind")) acc
                  (nil? m) (update acc :problems conj (problem :adapters/no-module "symbol fact has no enclosing module fact" (f "id")))
                  (some nil? [(f "path") (f "span") (f "source_hash")])
                  (update acc :problems conj (problem :adapters/missing-source "symbol fact needs path, span and source_hash" (f "id")))
                  :else (update acc :documents conj (symbol-doc (v1-symbol-record (:language ctx) f m) ctx (f "id"))))))
            {:documents [] :problems []} facts)))

;; ----------------------------------------------------------------- cli

(def argument-keys
  "v1 `ArgumentRecord` fields, in declaration order (the order `arguments` JSON prints)."
  ["names" "required" "default" "choices" "nargs" "value_type" "action" "const" "help"])

(defn- argument-record [attrs] (apply array-map (mapcat (fn [k] [k (get attrs k)]) argument-keys)))

(defn v1-command-record
  "The v1 `CommandRecord` of a command/group `fact` (string keys). Arguments
   come from `attrs.arguments` (local + inherited, v1 order); without it, from
   the argument facts contained in the command (`own`, local only)."
  [fact own]
  (let [attrs (fact "attrs")]
    {"path" (vec (or (attrs "path") (str/split (fact "id") #" ")))
     "synopsis" (or (fact "synopsis") "") "description" (or (fact "doc") "") "usage" (or (fact "signature") "")
     "group" (= "group" (fact "kind")) "hidden" (boolean (attrs "hidden"))
     "arguments" (mapv argument-record (or (attrs "arguments") (map #(% "attrs") own)))
     "exclusive_groups" (mapv #(array-map "required" (boolean (% "required")) "arguments" (vec (% "arguments")))
                              (attrs "exclusive_groups"))}))

(defn cli-provenance
  "v1 `projection.provenance`: `parser:<factory>#<path>; contract-sha256:<sha256
   of json.dumps(record, sort_keys=True, ensure_ascii=False)>`."
  [record factory sha256]
  (str "parser:" factory "#" (str/join " " (record "path")) "; contract-sha256:" (sha256 (py-json record {:sort? true}))))

(defn- command-doc [record {:keys [system output factory sha256]} fact-id]
  (let [id (str "cmd-" system "-" (str/join "-" (record "path")))
        data (array-map "arguments" (record "arguments") "exclusive_groups" (record "exclusive_groups"))]
    {:name id :model "CliCommandDoc" :path (str output "/commands/" id ".md") :fact fact-id
     :fields {"id" id "system" system "command_path" (str/join " " (record "path"))
              "synopsis" (or (not-empty (record "synopsis")) "No synopsis declared.")
              "arguments" (str "```json\n" (py-json data {:indent 2}) "\n```")
              "provenance" (cli-provenance record factory sha256)}
     :defaults (assoc (authored-defaults "CliCommandDoc") "usage" (record "usage"))}))

(defn- surface-doc [record records {:keys [system output factory sha256]} fact-id]
  (let [root (first (record "path")) id (str "surface-" system "-" root)
        leaves (for [r records :when (and (= root (first (r "path"))) (not (r "group")))] (str/join " " (r "path")))]
    {:name id :model "SurfaceDoc" :path (str output "/surfaces/" id ".md") :fact fact-id
     :fields {"id" id "system" system "surface" root
              "commands" (or (not-empty (str/join "\n" (map #(str "- " system " " %) leaves))) "No leaf commands.")
              "provenance" (cli-provenance record factory sha256)}
     :defaults (authored-defaults "SurfaceDoc")}))

(defn- plan-cli [output ctx]
  (let [facts (output "facts")
        own (group-by (fn [f] (some #(when (= "contained-in" (% "type")) (% "target")) (f "relations")))
                      (filter #(= "argument" (% "kind")) facts))
        pairs (for [f facts :when (#{"command" "group"} (f "kind"))] [(f "id") (v1-command-record f (own (f "id")))])
        records (map second pairs)]
    {:problems []
     :documents (vec (mapcat (fn [[id r]]
                               (cond-> []
                                 (not (r "group")) (conj (command-doc r ctx id))
                                 (= 1 (count (r "path"))) (conj (surface-doc r records ctx id))))
                             pairs))}))

;; ---------------------------------------------------------------- plan

(defn- output-ok? [o] (and (relative-posix? o) (not (str/ends-with? o "/"))))

(defn- collisions [profile documents]
  (for [[path ds] (group-by :path documents) :when (> (count ds) 1)]
    (assoc (problem :adapters/collision
                    (str (if (= profile "cli") "Command names" "Python symbols") " collide when converted to document IDs: "
                         (:name (first ds)) " <- " (str/join ", " (map :fact ds)))
                    (:fact (first ds)))
           :facts (mapv :fact ds))))

(defn plan-documents
  "Plans the documents of a parsed adapter `output`:
   `{:documents [PLANNED] :problems [..]}`; a non-empty `:problems` means the
   caller must refuse to write (documents are still returned for inspection).
   opts: `:system` (required, `[a-z][a-z0-9_-]*`), `:output` (store-relative
   dir, default \"knowledge\"), `:factory` (cli), `:architecture-spec` (default
   \"Not declared.\"), `:language` (overrides the output's), `:sha256`
   (required: fn string → lowercase hex sha256 of its UTF-8 bytes).
   PLANNED = `{:name :model :path :fact :fields :defaults}`."
  [output {:keys [system factory architecture-spec language sha256] :as opts}]
  (let [out-dir (or (:output opts) "knowledge")
        violations (validate output)
        profile (when (map? output) (output "profile"))
        ctx {:system system :output out-dir :factory (or factory "") :sha256 sha256
             :spec (or architecture-spec "Not declared.")
             :language (or language (when (map? output) (output "language")) "python")
             :prefix (provenance-prefix (when (map? output) (output "adapter")))}
        early (cond-> violations
                (not (and (string? system) (re-matches #"[a-z][a-z0-9_-]*" system)))
                (conj (problem :adapters/invalid-system "system must be a lowercase identifier without path separators" nil))
                (not (output-ok? out-dir))
                (conj (problem :adapters/invalid-output "output must be a relative directory inside the store, without `..` or a trailing slash" nil))
                (not (ifn? sha256)) (conj (problem :adapters/no-digest "plan-documents needs a :sha256 function" nil)))]
    (if (seq early)
      {:documents [] :problems (vec early)}
      (let [{:keys [documents problems]} (if (= profile "cli") (plan-cli output ctx) (plan-symbols output ctx))]
        {:documents documents :problems (into (vec problems) (sort-by :message (collisions profile documents)))}))))

(defn merge-payload
  "Full payload of `planned` over `existing` (a tracked payload or nil): v1
   `{**existing, **fields}` — parser-owned fields overwritten, authored ones
   kept; nil ⇒ the authored defaults of a new document."
  [planned existing]
  (merge (or existing (:defaults planned)) (:fields planned)))

(defn owned?
  "True when `tracked` (`{:name :model :path :payload}`) was generated by the
   adapter+system of `scope` (`{:adapter :profile :language :system :output}`):
   its model belongs to the profile, its path is `<output>/<model dir>/…`, its
   payload `system` is the scope's, its name carries the generated prefix
   (`<language>-<system>-`, `cmd-<system>-`, `surface-<system>-`) and its
   provenance starts with the adapter's tag (`python-ast:` / `<adapter>:` for
   symbols, `parser:` for cli)."
  [{:keys [adapter profile language system output]} {:keys [name model path payload]}]
  (let [prov (str (get payload "provenance"))
        name-prefix (case model "PythonSymbolDoc" (str (or language "python") "-" system "-")
                          "CliCommandDoc" (str "cmd-" system "-") "SurfaceDoc" (str "surface-" system "-") nil)]
    (boolean
     (and (some #{model} (profile-models profile))
          (str/starts-with? (str path) (str (or output "knowledge") "/" (model-dirs model) "/"))
          (= system (get payload "system"))
          (str/starts-with? (str name) name-prefix)
          (str/starts-with? prov (if (= profile "cli") "parser:" (str (provenance-prefix adapter) ":")))))))

(defn sync-plan
  "Partitions `planned` against the `tracked` documents this adapter owns:
   `{:create :update :unchanged :untrack}`, each sorted by `:name`. create /
   update / unchanged entries are PLANNED + `:payload` (merged, full); update
   also has `:changed` (parser-owned fields whose tracked value differs, v1
   `changed`); untrack = tracked entries no planned document names."
  [planned tracked]
  (let [by-name (into {} (map (juxt :name identity)) tracked)
        names (set (map :name planned))
        entries (for [p planned
                      :let [t (by-name (:name p)) existing (:payload t)
                            e (assoc p :payload (merge-payload p existing))]]
                  (cond (nil? t) [:create e]
                        :else (let [changed (vec (sort (for [[k v] (:fields p) :when (not= v (get existing k))] k)))]
                                (if (seq changed) [:update (assoc e :changed changed)] [:unchanged e]))))
        pick (fn [k] (vec (sort-by :name (for [[kind e] entries :when (= kind k)] e))))]
    {:create (pick :create) :update (pick :update) :unchanged (pick :unchanged)
     :untrack (vec (sort-by :name (remove #(names (:name %)) tracked)))}))

(defn drift-report
  "What `check` reports for a sync `plan`: `{:drift? bool :documents
   [{:name :path :model :status \"missing\"|\"changed\"|\"orphan\" :fields [..]}]}`
   sorted by name; `:fields` lists the stale parser-owned fields of a changed doc."
  [plan]
  (let [row (fn [status fields e] {:name (:name e) :path (:path e) :model (:model e) :status status :fields (vec (fields e))})
        docs (vec (sort-by :name (concat (map #(row "missing" (constantly []) %) (:create plan))
                                         (map #(row "changed" :changed %) (:update plan))
                                         (map #(row "orphan" (constantly []) %) (:untrack plan)))))]
    {:drift? (boolean (seq docs)) :documents docs}))

(defn v1-report
  "v1 `DocumentationReport` view of a sync `plan` (string keys, paths as
   values): documents, missing, changed, removed and `ok`."
  [plan]
  (let [paths (fn [k] (mapv :path (k plan)))]
    {"documents" (count (mapcat plan [:create :update :unchanged]))
     "missing" (paths :create) "changed" (paths :update) "removed" (paths :untrack)
     "ok" (not (:drift? (drift-report plan)))}))

(defn cites
  "v1 `citations.cites`: the sorted distinct spec numbers a docstring cites
   (`spec 07`, `Spec 12a`)."
  [docstring]
  (vec (sort (set (map second (re-seq #"(?i)\bspec\s+(\d{2}[a-z]?)\b" (or docstring "")))))))

(defn- stringify-data
  "Normalizes map keys to strings recursively for uniform node comparison."
  [x]
  (cond
    (map? x) (into {} (map (fn [[k v]] [(if (keyword? k) (name k) (str k)) (stringify-data v)])) x)
    (sequential? x) (mapv stringify-data x)
    :else x))

(defn- valid-node-id? [x] (and (string? x) (not (str/blank? x))))

(defn source-node
  "Canonical `{:id str :source map :ast map}` view of a source node (planned
   or tracked `PythonSymbolDoc`, adapter fact, or v1 snapshot/graph node); nil
   when `node` is not a valid source node."
  [node]
  (when (map? node)
    (let [m (stringify-data node)
          fields (or (get m "fields") (get m "payload"))
          identity-map (get m "identity")
          has-schema? (or (map? identity-map)
                          (contains? m "schema")
                          (map? (get m "source"))
                          (map? (get m "ast")))]
      (cond
        (and (map? fields)
             (or (= "PythonSymbolDoc" (get m "model"))
                 (contains? fields "source_sha256")))
        (let [id (or (get fields "id") (get m "name"))]
          (when (valid-node-id? id)
            {:id id
             :source {"path" (get fields "source_path")
                      "sha256" (get fields "source_sha256")}
             :ast {"kind" (get fields "kind")
                   "module" (get fields "module")
                   "name" (get fields "qualname")
                   "span" (get fields "source_span")}}))

        has-schema?
        (let [id (or (when (map? identity-map) (get identity-map "node_id"))
                     (get m "id")
                     (get m "name"))
              schema (if (contains? m "schema") (get m "schema") m)]
          (when (and (valid-node-id? id) (map? schema))
            {:id id
             :source (let [s (get schema "source")] (if (map? s) s {}))
             :ast (let [a (get schema "ast")] (if (map? a) a {}))}))

        (and (valid-node-id? (get m "id"))
             (or (contains? m "source_hash") (contains? m "qualified_name")))
        (let [sp (get m "span")]
          {:id (get m "id")
           :source {"path" (get m "path")
                    "sha256" (some-> (get m "source_hash") (str/replace-first #"^sha256:" ""))}
           :ast {"kind" (get m "kind")
                 "name" (get m "qualified_name")
                 "span" (when (map? sp) (str (get sp "start_line") ":" (get sp "end_line")))}})))))

(defn- extract-source-nodes
  "Extracts `[ok? by-id]` from a snapshot (`{:nodes ...}` / `{\"nodes\" ...}`)
   or sequential collection of nodes / `PythonSymbolDoc` documents."
  [x]
  (let [raw (cond
              (sequential? x) x
              (and (map? x) (contains? x "nodes")) (get x "nodes")
              (and (map? x) (contains? x :nodes)) (:nodes x)
              :else ::invalid)]
    (if (or (= ::invalid raw) (not (sequential? raw)))
      [false nil]
      (let [symbol-only (filter (fn [el]
                                  (let [m (when (map? el) (or (get el :model) (get el "model")))]
                                    (or (nil? m) (= "PythonSymbolDoc" m))))
                                raw)
            parsed (mapv source-node symbol-only)
            by-id (reduce (fn [acc n]
                            (cond
                              (nil? n) (reduced ::invalid)
                              (contains? acc (:id n)) (reduced ::invalid)
                              :else (assoc acc (:id n) (select-keys n [:source :ast]))))
                          {} parsed)]
        (if (= ::invalid by-id)
          [false nil]
          [true by-id])))))

(defn source-graph-check
  "Verifies that recorded `actual` source nodes match scanned `expected` nodes
   without missing, stale, or extra nodes (v1 `kgdb_sync.graph_is_current` /
   `source_graph_file.graph_state`). Returns `{:current? bool :status kw
   :missing [ids] :stale [ids] :orphan [ids]}` where `:status` is `:missing`
   when `actual` is nil, `:unreadable` when malformed, `:current` on exact
   match, and `:stale` otherwise."
  [expected actual]
  (let [[exp-ok? exp-map] (extract-source-nodes expected)
        exp-ids (if exp-ok? (vec (sort (keys exp-map))) [])]
    (cond
      (not exp-ok?)
      {:current? false :status :unreadable :missing [] :stale [] :orphan []}

      (nil? actual)
      {:current? false :status :missing :missing exp-ids :stale [] :orphan []}

      :else
      (let [[act-ok? act-map] (extract-source-nodes actual)]
        (if (not act-ok?)
          {:current? false :status :unreadable :missing exp-ids :stale [] :orphan []}
          (let [missing (vec (sort (remove #(contains? act-map %) (keys exp-map))))
                stale (vec (sort (filter (fn [id]
                                           (and (contains? act-map id)
                                                (not= (get exp-map id) (get act-map id))))
                                         (keys exp-map))))
                orphan (vec (sort (remove #(contains? exp-map %) (keys act-map))))
                current? (and (empty? missing) (empty? stale) (empty? orphan))]
            {:current? current?
             :status (if current? :current :stale)
             :missing missing
             :stale stale
             :orphan orphan}))))))

(defn source-graph-current?
  "True iff `actual` source nodes match `expected` with no missing, stale, or
   extra nodes (v1 `kgdb_sync.graph_is_current`)."
  [expected actual]
  (boolean (:current? (source-graph-check expected actual)))) 
