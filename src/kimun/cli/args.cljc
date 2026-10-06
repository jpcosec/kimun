(ns kimun.cli.args
  "Command-line parsing of the `kimun` CLI over babashka.cli (docs/v2/06
   §B): global options, positional tokens, and rejection of unknown options as
   usage errors."
  (:require [babashka.cli :as cli]
            [sldb.kernel.err :as err]))

(def formats
  "Accepted values of `--format`."
  #{"json" "edn" "text"})

(def spec
  "babashka.cli spec of the global options; each command group adds its own
   through the `extra` argument of `parse` (`kimun.cli.main/group-specs`)."
  {:store   {:ref "PATH" :desc "Store directory (.kimun or its project dir)"}
   :format  {:ref "FMT" :desc "Output format: json | edn | text" :default "json" :validate formats}
   :actor   {:ref "NAME" :desc "Actor recorded in transactions (default human/$USER)"}
   :debug   {:desc "Print the stacktrace of an unexpected error to stderr" :coerce :boolean}
   :help    {:desc "Show usage" :coerce :boolean}
   :version {:desc "Show version" :coerce :boolean}})

(defn default-actor
  "`human/<USER>` from the environment map `env`, or `human/unknown`."
  [env]
  (let [user (get env "USER")]
    (if (and (string? user) (seq user)) (str "human/" user) "human/unknown")))

(defn- require-string! [opts k]
  (let [v (get opts k)]
    (when (and (contains? opts k) (not (string? v)))
      (err/raise :cli/usage (str "option --" (name k) " needs a value") {:option k}))))

(defn- value-options
  "Keys of `spec` that take a value: everything not coerced to a boolean or a
   collection."
  [spec]
  (for [[k v] spec :when (not (contains? #{:boolean :booleans} (:coerce v))) :when (not (vector? (:coerce v)))] k))

(defn parse
  "Parses `argv` into `{:args [tokens…] :opts {…}}` against the global `spec`
   merged with `extra` (the option specs of the command groups). Unknown
   options raise the babashka.cli ex-info (`:type :org.babashka/cli`); a
   value-taking option without a value raises `:cli/usage`. `:actor` defaults
   from `env` (`default-actor`), `:format` to \"json\"."
  ([argv] (parse argv {} {}))
  ([argv env] (parse argv env {}))
  ([argv env extra]
   (let [full (into {} (map (fn [[k v]] [k (if (contains? v :coerce) v (assoc v :coerce :string))]))
                    (merge extra spec))
         {:keys [args opts]} (try (cli/parse-args (vec argv) {:spec full :restrict true})
                                  (catch clojure.lang.ExceptionInfo e
                                    (let [d (ex-data e)]
                                      (if (and (= :org.babashka/cli (:type d))
                                               (some-> (ex-message e) (.startsWith "Missing value")))
                                        (err/raise :cli/usage (str "option --" (name (:option d)) " needs a value")
                                                   {:option (:option d)})
                                        (throw e)))))
         opts (or opts {})]
     (doseq [k (value-options full)] (require-string! opts k))
     {:args (vec (or args []))
      :opts (merge {:format "json" :actor (default-actor env)} opts)})))
