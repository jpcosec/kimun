(ns kimun.cli.commands.serve
  "The `kimun serve` group (docs/v2/06 §5): one HTTP API over the commands
   (`kimun.surface.http`). `serve` has no subcommand (`serve` or `serve start`);
   it blocks until the process is interrupted, so it never returns an envelope
   in normal use. The bearer token comes from `--token`, else `KIMUN_SERVE_TOKEN`."
  (:require [clojure.string :as str]
            [kimun.cli.out :as out]
            [kimun.surface.http :as http]
            [kimun.kernel.err :as err]))

(def spec
  "Options of the `serve` group (babashka.cli spec, merged by `kimun.cli.main`)."
  {:port {:ref "N" :desc "TCP port (default 8787; 0 picks a free one)"}
   :host {:ref "HOST" :desc "Address to bind (default 127.0.0.1)"}
   :token {:ref "TOKEN" :desc "Require `Authorization: Bearer TOKEN` (else $KIMUN_SERVE_TOKEN; none = open)"}
   :cors {:desc "Send CORS headers and answer the OPTIONS preflight" :coerce :boolean}})

(defn- parse-port [v]
  (if (nil? v)
    8787
    (let [n (try (#?(:clj Long/parseLong :cljs js/parseInt) (str/trim (str v))) (catch #?(:clj Exception :cljs :default) _ nil))]
      (if (and (integer? n) (<= 0 n 65535))
        (long n)
        (err/raise :cli/usage (str "--port needs an integer 0-65535, got `" v "`") {:option "port" :value v})))))

(defn start
  "`serve [start] [--port N] [--host H] [--token T] [--cors]`: starts the server
   for the store of `opts`/`ctx`, prints one banner line to stderr-free stdout
   and blocks until interrupted."
  [_args opts {:keys [env cwd]}]
  (let [port (parse-port (:port opts))
        token (http/resolve-token (:token opts) env)
        server (http/start! {:store (:store opts) :token token :cwd cwd :env env
                             :host (:host opts) :port port :cors? (boolean (:cors opts))})]
    (println (str "kimun serve on http://" (:host server) ":" (:port server)
                  " (store=" (or (:store opts) "auto") ", auth=" (if token "on (bearer token required)" "off") ")"))
    (flush)
    (try @(promise)
         (finally (http/stop! server)))
    (out/ok "serve" {:data {:host (:host server) :port (:port server)}})))

(def commands
  "Subcommand name → command fn `(fn [args opts ctx] envelope)`."
  {"start" start})

(defn dispatch
  "Runs `serve`: `sub` nil or \"start\"; anything else is the failure `cli/usage`."
  [sub args opts ctx]
  (if (or (nil? sub) (= "start" sub))
    (start (vec args) opts ctx)
    (out/failure :cli/usage (str "unknown subcommand `serve " sub "`; use `serve [start]`")
                 {:group "serve" :subcommand sub :known ["start"]} 6)))
