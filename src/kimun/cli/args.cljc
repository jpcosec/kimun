(ns kimun.cli.args
  "Command-line parsing of the `kimun` CLI over babashka.cli (docs/v2/06
   §B): global options, positional tokens, and rejection of unknown options as
   usage errors."
  (:require [babashka.cli :as cli]
            [kimun.kernel.err :as err]))

(def formats
  "Accepted values of `--format`."
  #{"json" "edn" "text" "md" "yaml" "ast"})

(def spec
  "babashka.cli spec of the global options; each command group adds its own
   through the `extra` argument of `parse` (`kimun.cli.main/group-specs`)."
  {:store   {:ref "PATH" :desc "Store directory (.kimun or its project dir)"}
   :format  {:ref "FMT" :desc "Output format: json | edn | text | md | yaml | ast" :default "json" :validate formats}
   :actor   {:ref "NAME" :desc "Actor recorded in transactions (default human/$USER)"}
   :debug   {:desc "Print the stacktrace of an unexpected error to stderr" :coerce :boolean}
   :wait    {:desc "When another process is writing the store, wait for it instead of failing with store/busy" :coerce :boolean}
   :help    {:desc "Show usage" :coerce :boolean}
   :version {:desc "Show version" :coerce :boolean}})

(defn default-actor
  "`human/<USER>` from the environment map `env`, or `human/unknown`."
  [env]
  (let [user (get env "USER")]
    (if (and (string? user) (seq user)) (str "human/" user) "human/unknown")))

(defn usage!
  "Raises `:cli/usage` with `message`; the data carries the command `group`
   (and the `sub`command when given) plus `data`."
  ([group message data] (err/raise :cli/usage message (merge {:group group} data)))
  ([group sub message data] (err/raise :cli/usage message (merge {:group group :subcommand sub} data))))

(defn require-arg!
  "Positional argument `i` of `args`, or a `:cli/usage` error naming the
   `what` that `group` `sub` needs."
  [group args i what sub]
  (or (get args i) (usage! group sub (str "`" group " " sub "` needs " what) {})))

(defn- require-string! [opts k]
  (let [v (get opts k)]
    (when (and (contains? opts k) (not (string? v)))
      (err/raise :cli/usage (str "option --" (name k) " needs a value") {:option k}))))

(defn- value-options
  "Keys of `spec` that take a value: everything not coerced to a boolean or a
   collection."
  [spec]
  (for [[k v] spec :when (not (contains? #{:boolean :booleans} (:coerce v))) :when (not (vector? (:coerce v)))] k))

(defn- parse-once
  "`babashka.cli/parse-args` of `argv` against `full`; a missing value is a `:cli/usage` error."
  [argv full]
  (try (cli/parse-args (vec argv) {:spec full :restrict true})
       (catch clojure.lang.ExceptionInfo e
         (let [d (ex-data e)]
           (if (and (= :org.babashka/cli (:type d))
                    (some-> (ex-message e) (.startsWith "Missing value")))
             (err/raise :cli/usage (str "option --" (name (:option d)) " needs a value")
                        {:option (:option d)})
             (throw e))))))

(defn- option-like? [t] (boolean (re-find #"^--?[A-Za-z]" (str t))))

(defn- common-suffix-length [a b]
  (count (take-while true? (map = (reverse a) (reverse b)))))

(defn- parse-all
  "Like `parse-once`, but options may follow positionals that follow an option.
   babashka.cli stops parsing options at the first positional token that comes
   after an option (`--to F FORM --format edn` leaves `--format edn` among the
   positionals, verbatim); the unparsed tail is what ends both `argv` and the
   positionals, so it is parsed again until nothing option-like is left in it.
   Not applied when `argv` has a `--` terminator."
  [argv full]
  (let [argv (vec argv)
        {:keys [args opts]} (parse-once argv full)
        args (vec (or args []))
        k (common-suffix-length argv args)
        tail (subvec args (- (count args) k))]
    (if (and (pos? k) (< k (count argv)) (not-any? #{"--"} argv) (some option-like? tail))
      (let [r (parse-all tail full)]
        {:args (into (subvec args 0 (- (count args) k)) (:args r))
         :opts (merge opts (:opts r))})
      {:args args :opts (or opts {})})))

(defn parse
  "Parses `argv` into `{:args [tokens…] :opts {…}}` against the global `spec`
   merged with `extra` (the option specs of the command groups). Unknown
   options raise the babashka.cli ex-info (`:type :org.babashka/cli`); a
   value-taking option without a value raises `:cli/usage`. Options may come
   after positional arguments, also after `--opt value POSITIONAL` (see
   `parse-all`). `:actor` defaults from `env` (`default-actor`), `:format` to
   \"json\"."
  ([argv] (parse argv {} {}))
  ([argv env] (parse argv env {}))
  ([argv env extra]
   (let [full (into {} (map (fn [[k v]] [k (-> (if (contains? v :coerce) v (assoc v :coerce :string))
                                                (dissoc :default))]))
                    (merge extra spec))
         {:keys [args opts]} (parse-all argv full)]
     (doseq [k (value-options full)] (require-string! opts k))
     {:args (vec args)
      :opts (merge {:format "json" :actor (default-actor env)} opts)})))
