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
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.models.descriptor :as descriptor]
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
   :required {:desc "The field is required (models field-add)" :coerce :boolean}})

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

(defn- commit-define! [descriptor opts ctx command]
  (let [{:keys [backend store] d :descriptor path :path} (open-store opts ctx)
        _ (edit/check-template! (descriptor/validate! descriptor))
        r (mstore/define-plan host/host store descriptor {:actor (:actor opts) :timestamp (docs/timestamp)
                               :tree-id (ports/ulid (:ids host/host))})
        applied (when (:plan r) (store/commit! backend store (:plan r)))]
    (out/ok command {:store (store-ref d path)
                     :revision (if applied (:revision-id applied) (:head store))
                     :data (-> (dissoc r :plan) (assoc :unchanged? (boolean (:unchanged r))) (dissoc :unchanged))})))

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
  "`models list`: `[{:name :version :id :fields n}]` in definition order."
  [_args opts ctx]
  (let [{:keys [store] d :descriptor path :path} (open-store opts ctx)]
    (out/ok "models list" {:store (store-ref d path) :revision (:head store)
                           :data (mapv (fn [m] {:name (:name m) :version (:version m) :id (:id m)
                                                :fields (count (:fields (:descriptor m)))})
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
   `:state`). Data `{:ok :models [{:name :ok :errors [..]}]}`; any error is
   the failure `models/invalid` (exit 3) carrying the same report."
  [args opts {:keys [env cwd] :as ctx}]
  (let [dir (cli-store/resolve-store opts env cwd)
        opened (when (and dir (cli-store/store? dir)) (open-store opts ctx))
        defined (if opened (mstore/models (:store opened)) [])
        known (set (map :name defined))
        targets (cond (:file opts) [(read-descriptor-file (:file opts) cwd)]
                      (first args) [(:descriptor (mstore/model (:store (or opened (open-store opts ctx))) (first args)))]
                      :else (mapv :descriptor defined))
        report (mapv (fn [d] (let [errs (problems d (conj known (:model/name d)))]
                               {:name (:model/name d) :ok (empty? errs) :errors errs}))
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
