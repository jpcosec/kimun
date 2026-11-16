(ns kimun.cli.commands.mcp
  "The `kimun mcp` group (docs/v2/06, section `mcp`): a Model Context Protocol server
   over stdio (`kimun.surface.mcp`). It reads one JSON-RPC message per line from stdin
   and writes one line per answer to stdout; nothing else goes to stdout (the command's
   own output and anything a tool prints are sent to stderr). At end of input it returns
   normally, and the envelope is not printed (it is marked `:streamed`)."
  (:require [clojure.string :as str]
            [kimun.cli.out :as out]
            [kimun.surface.mcp :as mcp]))

(def spec
  "Options of the `mcp` group: none."
  {})

(defn serve!
  "Serves stdin to stdout until end of input. `run` is `kimun.cli.main/run`, `version` the
   product's; the tools run with the working directory and environment of `ctx`."
  [run version {:keys [env cwd]}]
  (let [stdout *out*
        stdin (java.io.BufferedReader. (java.io.InputStreamReader. System/in "UTF-8"))
        ctx {:run run :env env :cwd cwd :version version}]
    (binding [*out* *err*]
      (loop []
        (when-let [line (.readLine stdin)]
          (when-not (str/blank? line)
            (when-let [response (mcp/handle-line line ctx)]
              (.write ^java.io.Writer stdout (str response "\n"))
              (.flush ^java.io.Writer stdout)))
          (recur))))
    (with-meta (out/ok "mcp" {:data {:stopped "end of input"}}) {:streamed true})))

(defn dispatch
  "Runs `mcp` (no subcommand) with the injected `run` and `version`; a subcommand is `cli/usage`."
  [run version sub _args _opts ctx]
  (if (nil? sub)
    (serve! run version ctx)
    (out/failure :cli/usage (str "`mcp` takes no subcommand, got `" sub "`")
                 {:group "mcp" :subcommand sub :known []} 6)))
