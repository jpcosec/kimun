(ns kimun.cli.commands.direct
  "The direct-mode commands `extract`, `render` and `validate` (docs/v2/06
   §5, 07 §6): model-first operations that need no store. The model is
   `--model X.edn`, the name of a model of the discoverable store, or a v1
   `module:Class` (Python exporter); it may also be the first positional
   token, as in v1 (`kimun extract module:Class --input doc.md`). An absent
   `rev` field is the typed error `validate/missing`, never a silent empty."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [kimun.cli.commands.models :as models]
            [kimun.cli.out :as out]
            [kimun.surface.models.extract :as extract]
            [kimun.surface.models.store :as mstore]
            [kimun.surface.models.edit :as edit]
            [kimun.surface.models.render :as render]
            [kimun.surface.models.validate :as validate]
            [kimun.kernel.err :as err]))

(def spec
  "Options of the direct commands (babashka.cli spec, merged by `kimun.cli.main`)."
  {:model {:alias :m :ref "MODEL" :desc "X.edn | model NAME in the store | module:Class"}
   :input {:alias :i :ref "FILE" :desc "Markdown file to extract or validate"}
   :data {:alias :d :ref "FILE" :desc "Payload file (.json, .yaml, .yml) to render or validate"}
   :output {:alias :o :ref "FILE" :desc "Write the result to FILE (`-` = the envelope only)"}
   :mode {:ref "MODE" :desc "validate: render-validity (default) | strict-roundtrip"}
   :allow-empty {:desc "Accept an empty value for a `rev` field, as v1 did" :coerce :boolean}})

(defn- file! [path cwd what]
  (let [p (fs/absolutize (fs/path cwd path))]
    (when-not (fs/regular-file? p) (err/raise :docs/no-file (str "no such " what " file: " p) {:path (str p)}))
    (str p)))

(defn- read-data [path cwd]
  (let [p (file! path cwd "data")
        text (slurp p)
        v (try (if (str/ends-with? p ".json") (json/parse-string text) (yaml/parse-string text :keywords false))
               (catch Exception e (err/raise :validate/bad-data (str "cannot read payload " p ": " (ex-message e)) {:path p})))]
    (when-not (map? v) (err/raise :validate/bad-data (str "payload " p " must be a mapping of field → value") {:path p}))
    (walk/postwalk #(cond (map? %) (into {} %) (seq? %) (vec %) :else %) v)))

(defn- extract-strict
  "The payload of `text` under `d`; unless `--allow-empty`, a `rev` field
   that came out nil or empty raises `:validate/missing` naming it (07 §6)."
  [d text opts]
  (let [payload (extract/extract d text)
        empty (vec (filter #(contains? #{nil ""} (get payload %)) (edit/rev-fields d)))]
    (when (and (seq empty) (not (:allow-empty opts)))
      (err/raise :validate/missing (str "the document has no value for required field(s): " (str/join ", " empty))
                 {:model (:model/name d) :fields empty}))
    payload))

(defn- model-of
  "The descriptor of the command. A spec 16 class is refused (`:cli/usage`): these
   commands extract and render by the v1 template, which a class does not have."
  [sub opts ctx]
  (let [d (models/resolve-model (or (:model opts) sub) opts ctx)]
    (when (mstore/spec16? d)
      (err/raise :cli/usage (str "`extract`, `render` and `validate` work through the v1 template, and `" (:model/name d)
                                 "` is a spec 16 class (no template, no renderer yet: M2): track the file with `docs track` and read it with `docs show`")
                 {:model (:model/name d)}))
    d))

(defn- write-out [opts cwd text]
  (let [o (:output opts)]
    (when (and o (not= "-" o))
      (let [p (str (fs/absolutize (fs/path cwd o)))] (spit p text) p))))

(defn extract-cmd
  "`extract [MODEL] --input doc.md [-o FILE]`: data `{:model :payload}` (plus
   `:output` when the payload was written, as JSON or — by extension — YAML)."
  [sub opts {:keys [cwd] :as ctx}]
  (let [d (model-of sub opts ctx)
        input (or (:input opts) (err/raise :cli/usage "`extract` needs --input FILE" {:command "extract"}))
        payload (extract-strict d (slurp (file! input cwd "input")) opts)
        o (:output opts)
        written (write-out opts cwd (if (and o (re-find #"\.ya?ml$" o))
                                      (yaml/generate-string payload :dumper-options {:flow-style :block})
                                      (json/generate-string payload {:pretty true})))]
    (out/ok "extract" {:data (cond-> {:model (:model/name d) :payload payload} written (assoc :output written))})))

(defn render-cmd
  "`render [MODEL] --data payload.json|yaml [-o FILE]`: data is the Markdown
   (so `--format text` prints it); with `-o FILE` it is written and data is
   `{:model :output}`."
  [sub opts {:keys [cwd] :as ctx}]
  (let [d (model-of sub opts ctx)
        data (or (:data opts) (err/raise :cli/usage "`render` needs --data FILE" {:command "render"}))
        md (render/render d (read-data data cwd))
        written (write-out opts cwd (str md "\n"))]
    (out/ok "render" {:data (if written {:model (:model/name d) :output written} md)})))

(defn validate-cmd
  "`validate [MODEL] (--input doc.md | --data FILE) [--mode M]`: data
   `{:ok true :mode :errors []}`; a document that does not survive the
   roundtrip is the failure `validate/invalid` (exit 3) carrying `:errors`."
  [sub opts {:keys [cwd] :as ctx}]
  (let [d (model-of sub opts ctx)
        mode (case (:mode opts) (nil "render-validity") :render-validity "strict-roundtrip" :strict-roundtrip
                   (err/raise :cli/usage "--mode must be render-validity or strict-roundtrip" {:mode (:mode opts)}))
        _ (when (:input opts) (extract-strict d (slurp (file! (:input opts) cwd "input")) opts))
        input (cond (:input opts) {:markdown (slurp (file! (:input opts) cwd "input"))}
                    (:data opts) {:data (read-data (:data opts) cwd)}
                    :else (err/raise :cli/usage "`validate` needs --input FILE or --data FILE" {:command "validate"}))
        r (validate/validate d input mode)]
    (if (:ok r)
      (out/ok "validate" {:data {:ok true :model (:model/name d) :mode mode :errors []}})
      (out/failure :validate/invalid (str "document does not round-trip under " (:model/name d))
                   {:model (:model/name d) :mode mode :errors (:errors r)} 3))))

(defn dispatcher
  "The group dispatch fn of direct command `command` (\"extract\", \"render\"
   or \"validate\"): its `sub` is the first positional token (the model, when
   `--model` is absent)."
  [command]
  (let [f ({"extract" extract-cmd "render" render-cmd "validate" validate-cmd} command)]
    (fn [sub _args opts ctx] (f sub opts ctx))))
