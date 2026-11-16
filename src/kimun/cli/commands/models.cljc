(ns kimun.cli.commands.models
  "The `kimun models` group (docs/v2/07 §7): `define`, `list`, `show`,
   `check`, `history`, `field-add` and `template`. A model is data — an EDN
   descriptor stored as a node of the `models` tree — so editing a model is
   transforming its descriptor and defining it again; a draft is simply a
   descriptor file that has not been defined. Also the helpers the direct
   commands share: reading a descriptor from a file or the store."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [kimun.cli.commands.docs :as docs]
            [kimun.cli.compose :as compose]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.models.descriptor :as descriptor]
            [kimun.surface.models.extract :as extract]
            [kimun.surface.models.render :as render]
            [kimun.surface.models.edit :as edit]
            [kimun.surface.models.store :as mstore]
            [kimun.surface.models.subgraph :as subgraph]
            [kimun.host.default :as host]
            [kimun.kernel.err :as err]
            [kimun.kernel.ports :as ports]
            [kimun.kernel.store :as store]))

(def spec
  "Options of the `models` group (babashka.cli spec, merged by `kimun.cli.main`)."
  {:file {:alias :f :ref "FILE" :desc "Descriptor (.edn) or template file"}
   :field {:ref "NAME" :desc "Field name (models field-add)"}
   :type {:ref "TYPE" :desc "Field type: string|int|bool|text|yaml or EDN such as [:list :string]"}
   :default {:ref "VALUE" :desc "Field default (models field-add)"}
   :description {:ref "TEXT" :desc "Field description (models field-add, required)"}
   :required {:desc "The field is required (models field-add)" :coerce :boolean}
   :isa {:ref "BASE" :desc "models list: only the models that are BASE or inherit from it (spec 16 `:base`, docs/v2/16 §4.1)"}
   :dry-run {:desc "Report the documents a redefinition would rewrite, without writing" :coerce :boolean}
   :backfill {:desc "Rewrite the model's documents under the new contract" :coerce :boolean}})


(defn read-descriptor-file
  "The descriptor in EDN file `path` (relative to `cwd`); raises
   `:models/no-file` (exit 1) or `:models/invalid` (exit 3, unreadable EDN)."
  [path cwd]
  (let [p (fs/absolutize (fs/path cwd path))]
    (when-not (fs/regular-file? p) (err/raise :models/no-file (str "no such file: " p) {:path (str p)}))
    (try (edn/read-string (slurp (str p)))
         (catch Exception e (err/raise :models/invalid (str "descriptor file is not EDN: " (ex-message e)) {:path (str p)})))))

(defn- open-store [opts {:keys [env cwd]}]
  (cli-store/open! (cli-store/resolve-store opts env cwd)))

(defn resolve-model
  "The descriptor named by `token`: an existing `.edn` file or the name of a model in the discoverable
   store. Raises `:models/missing` (exit 1) when it is none of them."
  [token opts {:keys [env cwd] :as ctx}]
  (cond
    (nil? token) (err/raise :cli/usage "a model is required: --model X.edn | NAME" {})
    (str/ends-with? token ".edn") (read-descriptor-file token cwd)
    :else (let [dir (cli-store/resolve-store opts env cwd)]
            (if (and dir (cli-store/store? dir))
              (:descriptor (mstore/model (:store (cli-store/open-for opts ctx)) token))
              (err/raise :models/missing (str "no model `" token "`: not an .edn file and no store to look it up in") {:model token})))))

(defn- rewrites
  "For each document of the model `descriptor` redefines: `{:name :path :text
   :source}` with `:text` its Markdown under the new contract. The payload is
   read under the contract the store holds NOW (the one the document was
   written with) and only then rendered with the new one — extracting an old
   text with a new template misaligns every section after the first change.
   `ctx` is the composition context (`kimun.cli.compose/context`), so a
   composed field is re-expanded against the project root. Raises `:models/invalid` when a document lacks a required field without default."
  [store descriptor ctx]
  (let [current (:descriptor (mstore/find-model store (:model/name descriptor)))
        known (set (map :name (:fields current)))
        added (remove #(or (known (:name %)) (contains? % :default) (not (:required %))) (:fields descriptor))]
    (vec (for [doc (tdocs/of-model store (:model/name descriptor))]
           (let [payload (try (let [p (extract/extract (or (tdocs/contract-descriptor store doc) current descriptor) (:source doc))]
                                (when-let [f (first (filter #(contains? #{nil ""} (get p (:name %))) added))]
                                  (throw (ex-info (str "no value for `" (:name f) "`") {:field (:name f)})))
                                (descriptor/finalize descriptor p))
                              (catch clojure.lang.ExceptionInfo e
                                (err/raise :models/invalid
                                           (str "document `" (:name doc) "` lacks required field"
                                                (when-let [f (:field (ex-data e))] (str " `" f "`"))
                                                ": give the field a default or fix the document first (" (ex-message e) ")")
                                           {:model (:model/name descriptor) :document (:name doc) :field (:field (ex-data e))})))]
             {:name (:name doc) :path (:path doc) :source (:source doc)
              :text (str (render/render descriptor payload ctx) "\n")})))))

(defn- backfill!
  "Commits one `docs update` per document in `todo` (rewriting its file) on top
   of `store`; returns `{:store :revision :warnings}`."
  [backend store todo opts root]
  (reduce (fn [acc {:keys [name path text]}]
            (let [{:keys [plan]} (tdocs/update-plan host/host (:store acc) name text
                                                    {:mint (fn [] (ports/ulid (:ids host/host)))
                                                     :actor (:actor opts) :timestamp (docs/timestamp) :operation "models backfill"})
                  r (when plan (store/commit! backend (:store acc) plan))
                  w (when (and r path)
                      (try (spit (str (fs/path root path)) text) nil
                           (catch Exception e [{:type "docs/file-not-written" :message (str "could not write " path ": " (ex-message e))}])))]
              (cond-> acc r (assoc :store (:store r) :revision (:revision-id r)) w (update :warnings into w))))
          {:store store :revision (:head store) :warnings []}
          todo))

(defn- validate-model!
  "Validates `descriptor` as v1 (and its template) or spec 16 (`mstore/validate-descriptor!`)."
  [descriptor]
  (mstore/validate-descriptor! descriptor)
  (when-not (mstore/spec16? descriptor) (edit/check-template! descriptor)))

(defn- commit-define!
  "Defines `descriptor` (a new version when the contract moved). Over a model
   that has documents: a document lacking a required field without default
   rejects the define; `--dry-run` reports `:backfill` (the documents whose
   text the new contract changes) without writing; `--backfill` rewrites them;
   without it they stay as they are, reported `:superseded` until updated."
  [descriptor opts ctx command]
  (let [{:keys [backend store] d :descriptor path :path} (cli-store/open-for opts ctx)
        _ (validate-model! descriptor)
        ;; documents are rewritten only for v1 models: a spec 16 class has no template to render (M1g)
        v1? (and (not (mstore/spec16? descriptor))
                 (not (some-> (mstore/find-model store (:model/name descriptor)) :descriptor mstore/spec16?)))
        todo (if v1?
               (filterv #(not= (:text %) (:source %)) (rewrites store descriptor (compose/context store path)))
               [])
        r (mstore/define-plan host/host store descriptor {:actor (:actor opts) :timestamp (docs/timestamp)
                               :tree-id (ports/ulid (:ids host/host))})
        data (-> (dissoc r :plan) (assoc :unchanged? (boolean (:unchanged r)) :backfill (mapv :name todo)) (dissoc :unchanged))]
    (if (:dry-run opts)
      (out/ok command {:store (cli-store/store-ref d path) :revision (:head store) :data (assoc data :dry-run? true)})
      (let [applied (when (:plan r) (store/commit! backend store (:plan r)))
            store' (if applied (:store applied) store)
            done (if (:backfill opts)
                   (backfill! backend store' todo opts (str (fs/parent (fs/absolutize path))))
                   {:store store' :revision (:head store') :warnings []})]
        (out/ok command {:store (cli-store/store-ref d path) :revision (:revision done) :warnings (:warnings done)
                         :data (assoc data :backfilled (if (:backfill opts) (mapv :name todo) []))})))))

(defn define
  "`models define -f X.edn`: a new
   name is version 1; an existing name becomes version + 1 by `:replace`
   (`supersedes`); the same contract again changes nothing. Data:
   `{:name :version :id :created? :unchanged?}`."
  [_args opts {:keys [env cwd] :as ctx}]
  (let [d (cond (:file opts) (read-descriptor-file (:file opts) cwd)
                :else (err/raise :cli/usage "`models define` needs -f X.edn" {:group "models" :subcommand "define"}))]
    (commit-define! d opts ctx "models define")))

(defn- field-count [d] (count (if (mstore/spec16? d) (get-in d [:anchoring :fields]) (:fields d))))

(defn- isa-names
  "Names of the models of `defined` that are `base` or inherit from it through
   `:base` (`subgraph/hierarchy`: derive/isa?, docs/v2/16 §4.1). Bases that no
   model defines are ignored here (`models check` reports them); a cycle raises
   `:models/invalid`. Raises `:models/missing` when `base` is not defined."
  [defined base]
  (let [known (set (map :name defined))
        _ (when-not (known base) (err/raise :models/missing (str "no model named `" base "`") {:model base :known (vec (sort known))}))
        h (subgraph/hierarchy (mapv (fn [m] (update (:descriptor m) :base #(filterv known %))) defined))]
    (set (filter #(subgraph/isa-model? h % base) known))))

(defn list-models
  "`models list [--isa BASE]`: `[{:name :version :id :fields n :documents n
   :not-intact n}]` in definition order (`:not-intact` counts documents not yet
   extracted under the current contract). `:fields` counts own fields (spec 16:
   `:anchoring :fields`). With `--isa BASE` only BASE and the models that
   inherit from it, transitively."
  [_args opts ctx]
  (let [{:keys [store] d :descriptor path :path} (cli-store/open-for opts ctx)
        all (mstore/models store)
        keep? (if-let [b (:isa opts)] (isa-names all b) (constantly true))]
    (out/ok "models list" {:store (cli-store/store-ref d path) :revision (:head store)
                           :data (mapv (fn [m] {:name (:name m) :version (:version m) :id (:id m)
                                                :fields (field-count (:descriptor m))
                                                :documents (count (tdocs/of-model store (:name m)))
                                                :not-intact (count (remove #(= :intact (:state %)) (tdocs/of-model store (:name m))))})
                                       (filter #(keep? (:name %)) all))})))

(defn projection
  "The auditable Markdown projection of a model (07 §7): fields with type and
   default, bases, the template with its markers, and the version history."
  [{:keys [name version id descriptor]} history]
  (str/join "\n"
            (concat [(str "# " name " (v" version ")") "" (str "- id: `" id "`")
                     (str "- bases: " (if (seq (:base descriptor)) (str/join ", " (:base descriptor)) "none")) ""
                     "## Fields" "" "| field | type | required | default | description |" "| --- | --- | --- | --- | --- |"]
                    (map (fn [f] (str "| " (:name f) " | `" (pr-str (:type f)) "` | " (if (:required f) "yes" "no") " | "
                                      (if (contains? f :default) (str "`" (pr-str (:default f)) "`") "") " | " (or (:description f) "") " |"))
                         (if (mstore/spec16? descriptor) (get-in descriptor [:anchoring :fields]) (:fields descriptor)))
                    (if (mstore/spec16? descriptor)
                      ["" "## Selector (spec 16)" "" "```edn" (pr-str (get-in descriptor [:anchoring :selector])) "```" "" "## History" ""]
                      ["" "## Template" "" "````markdown" (:template descriptor) "````" "" "## History" ""])
                    (map (fn [h] (str "- v" (:version h) " · " (:actor h) " · " (:timestamp h) " · `" (:revision h) "`")) history)
                    [""])))

(defn- need-name [args sub]
  (or (first args) (err/raise :cli/usage (str "`models " sub "` needs a model NAME") {:group "models" :subcommand sub})))

(defn show
  "`models show NAME [--format md]`: `{:name :version :id :descriptor :history
   :markdown}`; `--format md` prints the projection itself."
  [args opts ctx]
  (let [{:keys [store] d :descriptor path :path} (cli-store/open-for opts ctx)
        m (mstore/model store (need-name args "show"))
        h (mstore/history store (:name m))]
    (out/ok "models show" {:store (cli-store/store-ref d path) :revision (:head store)
                           :data (assoc (dissoc m :position) :history h :markdown (projection m h))})))

(defn history
  "`models history NAME`: the versions newest first,
   `[{:version :id :revision :actor :timestamp}]`."
  [args opts ctx]
  (let [{:keys [store] d :descriptor path :path} (cli-store/open-for opts ctx)]
    (out/ok "models history" {:store (cli-store/store-ref d path) :revision (:head store)
                              :data (mstore/history store (need-name args "history"))})))

(defn- problems
  "Messages of what is wrong with `descriptor` given the `defined` models of the
   store (entries with `:descriptor`). v1: shape, template, bases defined. Spec 16:
   shape, F10, bases defined, no cycle through `:base`, no weakened inherited field."
  [descriptor defined]
  (let [known (conj (set (map :name defined)) (:model/name descriptor))]
    (try
      (if (mstore/spec16? descriptor)
        (let [_ (mstore/validate-descriptor! descriptor)
              idx (subgraph/index-models (conj (filterv #(not= (:model/name descriptor) (:model/name %)) (map :descriptor defined))
                                               (update descriptor :model/version #(or % 1))))]
          (subgraph/check-composition! idx (:model/name descriptor))
          [])
        (do (edit/check-template! (descriptor/validate! descriptor {:known-models known})) []))
      (catch clojure.lang.ExceptionInfo e [(ex-message e)]))))

(defn check
  "`models check [NAME] [-f DRAFT.edn]`: validates the draft file, the named
   model, or every model of the store (shape, types, defaults, bases defined,
   `:state`) and, in a store, every document of each model against that
   contract (a draft is checked against the documents of the model it would
   replace). Data `{:ok :models [{:name :ok :errors [..] (:documents n)}]}`; any error is
   the failure `models/invalid` (exit 3) carrying the same report."
  [args opts {:keys [env cwd] :as ctx}]
  (let [dir (cli-store/resolve-store opts env cwd)
        opened (when (and dir (cli-store/store? dir)) (cli-store/open-for opts ctx))
        defined (if opened (mstore/models (:store opened)) [])
        targets (cond (:file opts) [(read-descriptor-file (:file opts) cwd)]
                      (first args) [(:descriptor (mstore/model (:store (or opened (cli-store/open-for opts ctx))) (first args)))]
                      :else (mapv :descriptor defined))
        docs-of (fn [d] (if opened (tdocs/of-model (:store opened) (:model/name d)) []))
        doc-errors (fn [d] (vec (for [doc (if (mstore/spec16? d) [] (docs-of d))
                                      :let [why (try (extract/extract d (:source doc)) nil
                                                     (catch clojure.lang.ExceptionInfo e (ex-message e)))]
                                      :when why]
                                  (str "document `" (:name doc) "`: " why))))
        report (mapv (fn [d] (let [errs (into (vec (problems d defined)) (doc-errors d))]
                               (cond-> {:name (:model/name d) :ok (empty? errs) :errors errs}
                                 (seq (docs-of d)) (assoc :documents (count (docs-of d))))))
                     targets)]
    (if (every? :ok report)
      (out/ok "models check" {:store (when opened (cli-store/store-ref (:descriptor opened) (:path opened)))
                              :revision (:head (:store opened)) :data {:ok true :models report}})
      (out/failure :models/invalid (str/join "; " (mapcat :errors report)) {:ok false :models report} 3))))

(defn parse-type
  "A field type from its CLI spelling: `string`, `int`, … or an EDN vector."
  [s]
  (let [t (if (str/starts-with? (str/trim s) "[") (edn/read-string s) (keyword (str/replace s #"^:" "")))]
    (if (descriptor/type-valid? t) t (err/raise :models/invalid (str "unknown field type " s) {:type s}))))

(defn- parse-default [type v]
  (cond (contains? #{:string :text} type) v
        (and (vector? type) (= :enum (first type))) v
        :else (edn/read-string v)))

(defn- refuse-spec16!
  "Raises `:cli/usage` when `d` is a spec 16 class: it has no `:template` and its
   fields live in `:anchoring`, so the v1 editors of the template do not apply
   (the class is redefined whole with `models define -f`)."
  [d command]
  (when (and d (mstore/spec16? d))
    (err/raise :cli/usage
               (str "`" command "` edits the v1 template of a model, and `" (:model/name d)
                    "` is a spec 16 class (no template; fields and selector live in :anchoring): redefine it with `models define -f`")
               {:group "models" :subcommand command :model (:model/name d)})))

(defn field-add
  "`models field-add NAME --field F --type T --description TEXT [--default D]
   [--required]`: appends a field and its template section (`optrev` with a
   default or when not required, else `rev`) and defines the new version.
   Raises `:models/field-exists` (exit 3) for a duplicate and `:cli/usage`
   (exit 6) without a non-empty `--description`."
  [args opts ctx]
  (let [name (need-name args "field-add")
        _ (when-not (and (:field opts) (:type opts) (not (str/blank? (str (:description opts)))))
            (err/raise :cli/usage "`models field-add` needs --field NAME, --type TYPE and --description TEXT" {:group "models" :subcommand "field-add"}))
        d (:descriptor (mstore/model (:store (cli-store/open-for opts ctx)) name))
        _ (refuse-spec16! d "field-add")
        type (parse-type (:type opts))
        field (cond-> {:name (:field opts) :type type :required (boolean (:required opts)) :description (:description opts)}
                (contains? opts :default) (assoc :default (parse-default type (:default opts))))]
    (commit-define! (edit/add-field d field) opts ctx "models field-add")))

(defn field-remove
  "`models field-remove NAME --field F`: drops the field and its template
   lines, defining the new version. `:models/missing` (exit 1) when absent."
  [args opts ctx]
  (let [name (need-name args "field-remove")
        _ (when-not (:field opts) (err/raise :cli/usage "`models field-remove` needs --field NAME" {:group "models" :subcommand "field-remove"}))
        d (:descriptor (mstore/model (:store (cli-store/open-for opts ctx)) name))
        _ (refuse-spec16! d "field-remove")]
    (commit-define! (edit/remove-field d (:field opts)) opts ctx "models field-remove")))

(defn template
  "`models template NAME [-f FILE]`: without `-f` the template text (data is
   the string); with `-f` the file becomes the template of a new version."
  [args opts {:keys [cwd] :as ctx}]
  (let [name (need-name args "template")
        {:keys [store] sd :descriptor path :path} (cli-store/open-for opts ctx)
        d (:descriptor (mstore/model store name))
        _ (refuse-spec16! d "template")]
    (if-let [f (:file opts)]
      (let [p (fs/absolutize (fs/path cwd f))]
        (when-not (fs/regular-file? p) (err/raise :models/no-file (str "no such file: " p) {:path (str p)}))
        (commit-define! (edit/set-template d (slurp (str p))) opts ctx "models template"))
      (out/ok "models template" {:store (cli-store/store-ref sd path) :revision (:head store) :data (:template d)}))))

(def commands
  "Subcommand name → command fn `(fn [args opts ctx] envelope)`."
  {"define" define "list" list-models "show" show "check" check "history" history
   "field-add" field-add "field-remove" field-remove "template" template})

(defn dispatch
  "Runs subcommand `sub` of the `models` group; an unknown or missing
   subcommand is the failure `cli/usage` listing the known ones."
  [sub args opts ctx]
  (if-let [f (get commands sub)]
    (f (vec args) opts ctx)
    (out/failure :cli/usage
                 (str (if sub (str "unknown subcommand `models " sub "`") "missing subcommand for `models`")
                      "; expected one of: " (str/join ", " (sort (keys commands))))
                 {:group "models" :subcommand sub :known (vec (sort (keys commands)))}
                 6)))
