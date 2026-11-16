(ns kimun.cli.commands.fields
  "The `kimun fields` group (docs/v2/06 §5; v1 `sldb fields`): `get`, `set`,
   `unset`, `append`, `clean`, `clear`, `schema` and `query` over the fields
   of tracked typed documents, with v1's names as aliases (`show` → `get`,
   `create` → `set`, `update` → `set` of an existing field, `remove` →
   `unset`). A document field is addressed as `DOC PATH` (`DOC` a name, a
   tree id or `Model:doc`) or by v1's single token `docs/[Model/]doc/field/…`;
   a model field as `models/Model[/field]`. A mutation reads the payload,
   changes the field (`kimun.surface.fields`), validates and renders it with
   the model, commits the new source as a succession of the document and
   rewrites its file. Every command is `(fn [args opts ctx] envelope)`."
  (:require [babashka.fs :as fs]
            [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [kimun.cli.commands.docs :as docs]
            [kimun.cli.compose :as compose]
            [kimun.cli.args :as cli-args]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.fields :as fields]
            [kimun.surface.index.where :as where]
            [kimun.surface.models.noise :as noise]
            [kimun.surface.models.store :as mstore]
            [kimun.host.default :as host]
            [kimun.kernel.err :as err]
            [kimun.kernel.store :as store]))

(def usage
  "Usage lines of the group, for the CLI help."
  ["kimun fields get DOC PATH                      value of a document field, with its owning section (v1: fields show docs/DOC/PATH)"
   "kimun fields schema MODEL [FIELD]              field schema of a model (v1: fields show models/MODEL[/FIELD])"
   "kimun fields query FIELD [--where EXPR] [--model M]   a field path across the tracked documents"
   "kimun fields set DOC PATH VALUE                set a field, creating it if absent (v1: fields create; fields update requires it to exist)"
   "kimun fields unset DOC PATH                    remove a field or a list element (v1: fields remove)"
   "kimun fields append DOC PATH VALUE             append VALUE to a list field"
   "kimun fields clean DOC PATH [--dedupe] [--drop-empty]   drop duplicate and/or empty items of a list field"
   "kimun fields clear DOC PATH                    empty a list field"])

(defn- usage! [sub message]
  (cli-args/usage! "fields" sub (str "`fields " sub "` " message) {}))



(defn- flag
  "A boolean option that may arrive as a string; anything else is `:cli/usage`."
  [opts k sub]
  (let [v (get opts k)]
    (cond (or (nil? v) (false? v) (= "false" v)) false
          (or (true? v) (= "true" v)) true
          :else (usage! sub (str "--" (name k) " takes no value, got `" v "`")))))

(defn- plain [v]
  (walk/postwalk #(cond (map? %) (into {} (map (fn [[k x]] [(if (keyword? k) (name k) (str k)) x])) %) (seq? %) (vec %) :else %) v))

(defn- parse-value
  "The VALUE token read as JSON/YAML (v1 `parse_data_value`): `5`, `\"Pending\"`, `[a, b]`, `{k: v}`."
  [raw sub]
  (try (plain (yaml/parse-string raw :keywords false))
       (catch Exception e (usage! sub (str "cannot read VALUE `" raw "` as JSON or YAML: " (ex-message e))))))

(defn- v1-target
  "v1 `resolve_doc_target` for `docs/[Model/]doc/field/…`: `[doc path]`."
  [store token sub]
  (let [joined (str/replace (subs token (count "docs/")) #"^/+|/+$" "")
        hits (for [d (tdocs/documents store)
                   prefix [(str (:model d) "/" (:name d) "/") (str (:name d) "/")]
                   :when (str/starts-with? (str joined "/") prefix)]
               [d (subs (str joined "/") (count prefix))])
        hits (or (seq (remove (comp str/blank? second) hits)) hits)]
    (case (count (distinct (map (comp :tree first) hits)))
      0 (err/raise :docs/missing (str "no tracked document for field target `" token "`") {:target token})
      1 (let [[d path] (first hits)]
          (when (str/blank? path) (usage! sub (str "needs a field path after the document in `" token "`")))
          [d (str/join "." (fields/split-path path))])
      (err/raise :fields/ambiguous (str "field target `" token "` names several documents; use `Model/doc`")
                 {:target token :documents (mapv (fn [[d _]] (str (:model d) ":" (:name d))) hits)}))))

(defn- target
  "`{:doc :path :more}` for the positional tokens of a document-field command."
  [store args sub]
  (let [a (or (get args 0) (usage! sub "needs a document and a field path: DOC PATH (or docs/DOC/PATH)"))]
    (if (str/starts-with? a "docs/")
      (let [[d path] (v1-target store a sub)] {:doc d :path path :more (subvec args 1)})
      (let [[m n] (str/split a #":" 2)
            token (if (and n (tdocs/find-document store n) (= m (:model (tdocs/find-document store n)))) n a)
            path (or (get args 1) (usage! sub "needs a field PATH after the document"))]
        {:doc (tdocs/document store token) :path (str/join "." (fields/split-path path)) :more (subvec args 2)}))))

(defn- readable
  "The document with its payload and its model descriptor: `[doc descriptor]`.
   A document whose source no longer extracts is `:fields/unreadable` (exit 3)."
  [store doc]
  (let [full (tdocs/document store (:tree doc))
        d (or (tdocs/contract-descriptor store full) (:descriptor (mstore/model store (:model full))))]
    (when-not (map? (:payload full))
      (err/raise :fields/unreadable (str "`" (:name full) "` does not extract under " (:model full) ": " (:error full) "; fix it with `docs update`")
                 {:document (:name full) :model (:model full)}))
    [full d]))

(defn- address [doc] (str (:model doc) ":" (:name doc)))

(defn schema
  "`fields schema MODEL [FIELD]`: the field schema of a model
   (`kimun.surface.fields/schema`)."
  [args opts ctx]
  (let [{:keys [descriptor store] store-path :path} (cli-store/open-for opts ctx)
        model (or (get args 0) (usage! "schema" "needs a MODEL"))
        d (fields/with-schema-fields store (:descriptor (mstore/model store model)))]
    (out/ok "fields schema" {:store (cli-store/store-ref descriptor store-path) :revision (:head store)
                             :data (if-let [f (get args 1)] (fields/schema d (str/replace f "/" ".")) (fields/schema d))})))

(defn get-field
  "`fields get DOC PATH` (v1 `fields show docs/DOC/PATH`): `{:doc :model
   :address :field :path :value :owning_section}`. An absent path is
   `fields/missing` (exit 1). `models/MODEL[/FIELD]` is `fields schema`."
  [args opts ctx]
  (if (str/starts-with? (str (get args 0)) "models/")
    (schema (str/split (subs (get args 0) (count "models/")) #"/" 2) opts ctx)
    (let [{:keys [descriptor store] store-path :path} (cli-store/open-for opts ctx)
          {:keys [doc path]} (target store args "get")
          [full d] (readable store doc)]
      (out/ok "fields get" {:store (cli-store/store-ref descriptor store-path) :revision (:head store)
                            :data {:doc (:name full) :model (:model full) :address (address full)
                                   :field (first (fields/split-path path)) :path path
                                   :value (fields/value-at (:payload full) path)
                                   :owning_section (fields/owning-section (fields/owning-sections d (:source full)) path)}}))))

(defn query
  "`fields query FIELD [--where EXPR] [--model M]`: `{:field :results
   [{:model :doc :path :field :value :owning_section}]}` for every tracked
   document carrying the field path (v1 `fields query`); no hit is an empty
   `:results`, not a failure."
  [args opts ctx]
  (let [field (or (get args 0) (usage! "query" "needs a FIELD path"))
        {:keys [descriptor store] store-path :path} (cli-store/open-for opts ctx)
        ast (some-> (:where opts) where/parse)]
    (out/ok "fields query" {:store (cli-store/store-ref descriptor store-path) :revision (:head store)
                            :data {:field field
                                   :results (fields/query store (str/join "." (fields/split-path field))
                                                          {:model (:model opts) :where ast})}})))

(defn- refuse-noise! [d doc text]
  (let [r (noise/structural-noise d text)]
    (when (noise/noisy? r)
      (err/raise :docs/structural-noise
                 (str "structural noise in `" (:name doc) "`: " (:model doc) " does not declare " (noise/describe r)
                      "; a heading inside a field value is a field the model is missing")
                 {:document (:name doc) :model (:model doc) :undeclared (mapv :raw (:headings r))}))))

(defn- mutation
  "The command of mutation `op` named `sub` on the command line. Data:
   `{:doc :model :address :operation :field :path :before :after :unchanged?
   :owning_section :file}`; the journal entry of the write carries
   `:operation` (`fields <op>`), `:address` (`Model:doc`) and `:fields`."
  [op sub {:keys [value? create?]}]
  (fn [args opts ctx]
    (let [{:keys [backend descriptor store] store-path :path :as opened} (cli-store/open-for opts ctx)
          {:keys [doc path more]} (target store args sub)
          raw (when value? (or (get more 0) (usage! sub "needs a VALUE (JSON or YAML)")))
          mopts (cond-> {:create? create? :dedupe (flag opts :dedupe sub) :drop-empty (flag opts :drop-empty sub)}
                  value? (assoc :raw raw :value (parse-value raw sub)))
          [full d] (readable store doc)
          _ (when (mstore/spec16? d)
              (err/raise :cli/usage (str "`fields " sub "` renders the document, and a spec 16 class (" (:model full)
                                         ") has no renderer yet (M2): edit the file and use `docs update`")
                         {:group "fields" :subcommand sub :model (:model full)}))
          m (fields/mutate d (:payload full) op path mopts)
          command (:operation m)
          base {:doc (:name full) :model (:model full) :address (address full) :file (:path full)}
          result (fn [text] (merge base (select-keys m [:operation :field :path :before :after])
                                   {:owning_section (fields/owning-section (fields/owning-sections d text) path)}))]
      (if (:unchanged? m)
        (let [root (str (fs/parent (fs/absolutize store-path)))
              text (:source full)
              ;; The store may already hold the value while the file does not (a
              ;; previous write failed after the commit): repair it as `docs update`.
              stale? (and (:path full)
                          (not= text (try (slurp (str (fs/path root (:path full)))) (catch Exception _ nil))))]
          (out/ok command {:store (cli-store/store-ref descriptor store-path) :revision (:head store)
                           :data (assoc (result text) :unchanged? true)
                           :warnings (if stale? (cli-store/write-file! root (:path full) text) [])}))
        (let [current (or (:descriptor (mstore/model store (:model full))) d)
              {:keys [text]} (fields/render-checked current (:payload m) (compose/context opened) (:field m))
              _ (refuse-noise! current full text)
              {:keys [plan]} (tdocs/update-plan host/host store (:name full) text
                                                {:actor (:actor opts) :timestamp (docs/timestamp) :operation command})
              r (if plan (store/commit! backend store plan) {:revision-id (:head store)})
              root (str (fs/parent (fs/absolutize store-path)))]
          (out/ok command {:store (cli-store/store-ref descriptor store-path) :revision (:revision-id r)
                           :data (assoc (result text) :unchanged? (nil? plan))
                           :warnings (if (and plan (:path full)) (cli-store/write-file! root (:path full) text) [])}))))))

(def spec
  "Options of the `fields` group (babashka.cli spec, merged by `kimun.cli.main`)."
  {:where {:ref "EXPR" :desc "Keep the documents matching the expression (fields query)"}
   :model {:ref "NAME" :desc "Only documents of this model (fields query)"}
   :dedupe {:desc "Drop repeated items, keeping the first (fields clean)" :coerce :boolean}
   :drop-empty {:desc "Drop empty items: null, \"\", [], {} (fields clean)" :coerce :boolean}})

(def commands
  "Subcommand name → command fn `(fn [args opts ctx] envelope)`; v1's names
   (`show`, `create`, `update`, `remove`) sit beside the ones of 06 §5."
  (let [set-field (mutation :set "set" {:value? true :create? true})
        unset (mutation :unset "unset" {})]
    {"get" get-field "show" get-field "schema" schema "query" query
     "set" set-field "create" set-field "update" (mutation :set "update" {:value? true :create? false})
     "unset" unset "remove" unset
     "append" (mutation :append "append" {:value? true})
     "clean" (mutation :clean "clean" {})
     "clear" (mutation :clear "clear" {})}))

(defn dispatch
  "Runs subcommand `sub` of the `fields` group with the positional tokens that
   follow it; an unknown or missing subcommand is the failure `cli/usage`."
  [sub args opts ctx]
  (if-let [f (get commands sub)]
    (f (vec args) opts ctx)
    (out/failure :cli/usage
                 (str (if sub (str "unknown subcommand `fields " sub "`") "missing subcommand for `fields`")
                      "; expected one of: " (str/join ", " (sort (keys commands))))
                 {:group "fields" :subcommand sub :known (vec (sort (keys commands)))}
                 6)))
