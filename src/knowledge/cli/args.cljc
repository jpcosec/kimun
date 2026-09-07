(ns knowledge.cli.args
  "Command-line parsing of the `knowledge` CLI over babashka.cli (docs/v2/06
   §B): global options, positional tokens, and rejection of unknown options as
   usage errors."
  (:require [babashka.cli :as cli]
            [sldb.kernel.err :as err]))

(def formats
  "Accepted values of `--format`."
  #{"json" "edn" "text"})

(def spec
  "babashka.cli spec of the global options."
  {:store   {:ref "PATH" :desc "Store directory (.knowledge or its project dir)"}
   :format  {:ref "FMT" :desc "Output format: json | edn | text" :default "json" :validate formats}
   :actor   {:ref "NAME" :desc "Actor recorded in transactions (default human/$USER)"}
   :name    {:ref "NAME" :desc "Store name (stores init)"}
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

(defn parse
  "Parses `argv` into `{:args [tokens…] :opts {…}}`. Unknown options raise the
   babashka.cli ex-info (`:type :org.babashka/cli`); a value-taking option
   without a value raises `:cli/usage`. `:actor` defaults from `env`
   (`default-actor`), `:format` to \"json\"."
  ([argv] (parse argv {}))
  ([argv env]
   (let [{:keys [args opts]} (cli/parse-args (vec argv) {:spec spec :restrict true})
         opts (or opts {})]
     (doseq [k [:store :actor :name :format]] (require-string! opts k))
     {:args (vec (or args []))
      :opts (merge {:format "json" :actor (default-actor env)} opts)})))
