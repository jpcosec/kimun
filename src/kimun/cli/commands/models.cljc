(ns kimun.cli.commands.models
  "The `kimun models` group (docs/v2/07 §7): `define`, `list`, `show`,
   `check`, `history`, `field-add` and `template`. A model is data — an EDN
   descriptor stored as a node of the `models` tree — so editing a model is
   transforming its descriptor and defining it again; a draft is simply a
   descriptor file that has not been defined. Also the helpers the direct
   commands share: reading a descriptor from a file, a Python class or the store."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
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
            [sldb.host.default :as host]
            [sldb.kernel.err :as err]
            [sldb.kernel.ports :as ports]
            [sldb.kernel.store :as store]))

(def spec
  "Options of the `models` group (babashka.cli spec, merged by `kimun.cli.main`)."
  {:file {:alias :f :ref "FILE" :desc "Descriptor (.edn) or template file"}
   :from-python {:ref "MOD:CLASS" :desc "Export a v1 StructuredNLDoc class as the descriptor"}
   :pythonpath {:ref "PATH" :desc "Extra PYTHONPATH for --from-python"}
   :field {:ref "NAME" :desc "Field name (models field-add)"}
   :type {:ref "TYPE" :desc "Field type: string|int|bool|text|yaml or EDN such as [:list :string]"}
   :default {:ref "VALUE" :desc "Field default (models field-add)"}
   :required {:desc "The field is required (models field-add)" :coerce :boolean}
   :dry-run {:desc "Report the documents a redefinition would rewrite, without writing" :coerce :boolean}
   :backfill {:desc "Rewrite the model's documents under the new contract" :coerce :boolean}})

(defn- store-ref [descriptor path] {:name (:name descriptor) :path path})

(defn read-descriptor-file
  "The descriptor in EDN file `path` (relative to `cwd`); raises
   `:models/no-file` (exit 1) or `:models/invalid` (exit 3, unreadable EDN)."
  [path cwd]
  (let [p (fs/absolutize (fs/path cwd path))]
    (when-not (fs/regular-file? p) (err/raise :models/no-file (str "no such file: " p) {:path (str p)}))
    (try (edn/read-string (slurp (str p)))
         (catch Exception e (err/raise :models/invalid (str "descriptor file is not EDN: " (ex-message e)) {:path (str p)})))))

(defn- python-dir [env]
  (or (get env "KIMUN_PYTHON")
      (when-let [r (io/resource "VERSION")]
        (when (= "file" (.getProtocol r))
          (str (fs/path (fs/parent (fs/parent (fs/path (.getPath r)))) "python"))))))

(defn from-python
  "The descriptor of v1 class `ref` (`module:Class`), exported by
   `python3 -m kimun.models export` (docs/v2/06 §9) with `pythonpath` and the
   exporter's directory (`$KIMUN_PYTHON`, else `python/` beside `resources/`)
   on PYTHONPATH. Raises `:models/python-export` (exit 3) with stderr."
  [ref {:keys [pythonpath env cwd]}]
  (let [path (str/join ":" (remove str/blank? [(python-dir env) pythonpath (get env "PYTHONPATH")]))
        r (process/shell {:out :string :err :string :continue true :dir (str cwd) :extra-env {"PYTHONPATH" path}}
                         "python3" "-m" "kimun.models" "export" ref)]
    (when-not (zero? (:exit r))
      (err/raise :models/python-export (str "could not export " ref ": " (last (str/split-lines (str/trim (:err r))))) {:ref ref}))
    (edn/read-string (:out r))))

(defn- open-store [opts {:keys [env cwd]}]
  (cli-store/open! (cli-store/resolve-store opts env cwd)))

(defn resolve-model
  "The descriptor named by `token`: an existing `.edn` file, a `module:Class`
   reference (Python exporter), or the name of a model in the discoverable
   store. Raises `:models/missing` (exit 1) when it is none of them."
  [token opts {:keys [env cwd] :as ctx}]
  (cond
    (nil? token) (err/raise :cli/usage "a model is required: --model X.edn | NAME | module:Class" {})
    (str/ends-with? token ".edn") (read-descriptor-file token cwd)
    (str/includes? token ":") (from-python token {:pythonpath (:pythonpath opts) :env env :cwd cwd})
    :else (let [dir (cli-store/resolve-store opts env cwd)]
            (if (and dir (cli-store/store? dir))
              (:descriptor (mstore/model (:store (open-store opts ctx)) token))
              (err/raise :models/missing (str "no model `" token "`: not an .edn file, not module:Class, and no store to look it up in") {:model token})))))

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
           (let [payload (try (let [p (extract/extract (or current descriptor) (:source doc))]
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
                                                    {:actor (:actor opts) :timestamp (docs/timestamp) :operation "models backfill"})
                  r (when plan (store/commit! backend (:store acc) plan))
                  w (when (and r path)
                      (try (spit (str (fs/path root path)) text) nil
                           (catch Exception e [{:type "docs/file-not-written" :message (str "could not write " path ": " (ex-message e))}])))]
              (cond-> acc r (assoc :store (:store r) :revision (:revision-id r)) w (update :warnings into w))))
          {:store store :revision (:head store) :warnings []}
          todo))

(defn- commit-define!
  "Defines `descriptor` (a new version when the contract moved). Over a model
   that has documents: a document lacking a required field without default
   rejects the define; `--dry-run` reports `:backfill` (the documents whose
   text the new contract changes) without writing; `--backfill` rewrites them;
   without it they stay as they are, reported `:superseded` until updated."
  [descriptor opts ctx command]
  (let [{:keys [backend store] d :descriptor path :path} (open-store opts ctx)
        _ (edit/check-template! (descriptor/validate! descriptor))
        todo (filterv #(not= (:text %) (:source %)) (rewrites store descriptor (compose/context store path)))
        r (mstore/define-plan host/host store descriptor {:actor (:actor opts) :timestamp (docs/timestamp)
                               :tree-id (ports/ulid (:ids host/host))})
        data (-> (dissoc r :plan) (assoc :unchanged? (boolean (:unchanged r)) :backfill (mapv :name todo)) (dissoc :unchanged))]
    (if (:dry-run opts)
      (out/ok command {:store (store-ref d path) :revision (:head store) :data (assoc data :dry-run? true)})
      (let [applied (when (:plan r) (store/commit! backend store (:plan r)))
            store' (if applied (:store applied) store)
            done (if (:backfill opts)
                   (backfill! backend store' todo opts (str (fs/parent (fs/absolutize path))))
                   {:store store' :revision (:head store') :warnings []})]
        (out/ok command {:store (store-ref d path) :revision (:revision done) :warnings (:warnings done)
                         :data (assoc data :backfilled (if (:backfill opts) (mapv :name todo) []))})))))

(defn define
  "`models define -f X.edn | --from-python mod:Class [--pythonpath P]`: a new
   name is version 1; an existing name becomes version + 1 by `:replace`
   (`supersedes`); the same contract again changes nothing. Data:
   `{:name :version :id :created? :unchanged?}`."
  [_args opts {:keys [env cwd] :as ctx}]
  (let [d (cond (:file opts) (read-descriptor-file (:file opts) cwd)
                (:from-python opts) (from-python (:from-python opts) {:pythonpath (:pythonpath opts) :env env :cwd cwd})
                :else (err/raise :cli/usage "`models define` needs -f X.edn or --from-python module:Class" {:group "models" :subcommand "define"}))]
    (commit-define! d opts ctx "models define")))

(defn list-models
  "`models list`: `[{:name :version :id :fields n :documents n :not-intact
   n}]` in definition order (`:not-intact` counts documents not yet extracted
   under the current contract)."
  [_args opts ctx]
  (let [{:keys [store] d :descriptor path :path} (open-store opts ctx)]
    (out/ok "models list" {:store (store-ref d path) :revision (:head store)
                           :data (mapv (fn [m] {:name (:name m) :version (:version m) :id (:id m)
                                                :fields (count (:fields (:descriptor m)))
                                                :documents (count (tdocs/of-model store (:name m)))
                                                :not-intact (count (remove #(= :intact (:state %)) (tdocs/of-model store (:name m))))})
                                       (mstore/models store))})))

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
                         (:fields descriptor))
                    ["" "## Template" "" "````markdown" (:template descriptor) "````" "" "## History" ""]
                    (map (fn [h] (str "- v" (:version h) " · " (:actor h) " · " (:timestamp h) " · `" (:revision h) "`")) history)
                    [""])))

(defn- need-name [args sub]
  (or (first args) (err/raise :cli/usage (str "`models " sub "` needs a model NAME") {:group "models" :subcommand sub})))

(defn show
  "`models show NAME [--format md]`: `{:name :version :id :descriptor :history
   :markdown}`; `--format md` prints the projection itself."
  [args opts ctx]
  (let [{:keys [store] d :descriptor path :path} (open-store opts ctx)
        m (mstore/model store (need-name args "show"))
        h (mstore/history store (:name m))]
    (out/ok "models show" {:store (store-ref d path) :revision (:head store)
                           :data (assoc (dissoc m :position) :history h :markdown (projection m h))})))

(defn history
  "`models history NAME`: the versions newest first,
   `[{:version :id :revision :actor :timestamp}]`."
  [args opts ctx]
  (let [{:keys [store] d :descriptor path :path} (open-store opts ctx)]
    (out/ok "models history" {:store (store-ref d path) :revision (:head store)
                              :data (mstore/history store (need-name args "history"))})))

(defn- problems [descriptor known]
  (try (edit/check-template! (descriptor/validate! descriptor {:known-models known})) []
       (catch clojure.lang.ExceptionInfo e [(ex-message e)])))

(defn check
  "`models check [NAME] [-f DRAFT.edn]`: validates the draft file, the named
   model, or every model of the store (shape, types, defaults, bases defined,
   `:state`) and, in a store, every document of each model against that
   contract (a draft is checked against the documents of the model it would
   replace). Data `{:ok :models [{:name :ok :errors [..] (:documents n)}]}`; any error is
   the failure `models/invalid` (exit 3) carrying the same report."
  [args opts {:keys [env cwd] :as ctx}]
  (let [dir (cli-store/resolve-store opts env cwd)
        opened (when (and dir (cli-store/store? dir)) (open-store opts ctx))
        defined (if opened (mstore/models (:store opened)) [])
        known (set (map :name defined))
        targets (cond (:file opts) [(read-descriptor-file (:file opts) cwd)]
                      (first args) [(:descriptor (mstore/model (:store (or opened (open-store opts ctx))) (first args)))]
                      :else (mapv :descriptor defined))
        docs-of (fn [d] (if opened (tdocs/of-model (:store opened) (:model/name d)) []))
        doc-errors (fn [d] (vec (for [doc (docs-of d)
                                      :let [why (try (extract/extract d (:source doc)) nil
                                                     (catch clojure.lang.ExceptionInfo e (ex-message e)))]
                                      :when why]
                                  (str "document `" (:name doc) "`: " why))))
        report (mapv (fn [d] (let [errs (into (vec (problems d (conj known (:model/name d)))) (doc-errors d))]
                               (cond-> {:name (:model/name d) :ok (empty? errs) :errors errs}
                                 (seq (docs-of d)) (assoc :documents (count (docs-of d))))))
                     targets)]
    (if (every? :ok report)
      (out/ok "models check" {:store (when opened (store-ref (:descriptor opened) (:path opened)))
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

(defn field-add
  "`models field-add NAME --field F --type T [--default D] [--required]`:
   appends a field and its template section (`optrev` with a default or when
   not required, else `rev`) and defines the new version. Raises
   `:models/field-exists` (exit 3) for a duplicate."
  [args opts ctx]
  (let [name (need-name args "field-add")
        _ (when-not (and (:field opts) (:type opts))
            (err/raise :cli/usage "`models field-add` needs --field NAME and --type TYPE" {:group "models" :subcommand "field-add"}))
        d (:descriptor (mstore/model (:store (open-store opts ctx)) name))
        type (parse-type (:type opts))
        field (cond-> {:name (:field opts) :type type :required (boolean (:required opts)) :description ""}
                (contains? opts :default) (assoc :default (parse-default type (:default opts))))]
    (commit-define! (edit/add-field d field) opts ctx "models field-add")))

(defn field-remove
  "`models field-remove NAME --field F`: drops the field and its template
   lines, defining the new version. `:models/missing` (exit 1) when absent."
  [args opts ctx]
  (let [name (need-name args "field-remove")
        _ (when-not (:field opts) (err/raise :cli/usage "`models field-remove` needs --field NAME" {:group "models" :subcommand "field-remove"}))
        d (:descriptor (mstore/model (:store (open-store opts ctx)) name))]
    (commit-define! (edit/remove-field d (:field opts)) opts ctx "models field-remove")))

(defn template
  "`models template NAME [-f FILE]`: without `-f` the template text (data is
   the string); with `-f` the file becomes the template of a new version."
  [args opts {:keys [cwd] :as ctx}]
  (let [name (need-name args "template")
        {:keys [store] sd :descriptor path :path} (open-store opts ctx)
        d (:descriptor (mstore/model store name))]
    (if-let [f (:file opts)]
      (let [p (fs/absolutize (fs/path cwd f))]
        (when-not (fs/regular-file? p) (err/raise :models/no-file (str "no such file: " p) {:path (str p)}))
        (commit-define! (edit/set-template d (slurp (str p))) opts ctx "models template"))
      (out/ok "models template" {:store (store-ref sd path) :revision (:head store) :data (:template d)}))))

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
