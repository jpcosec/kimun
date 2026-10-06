(ns kimun.surface.http
  "The HTTP surface of kimun (docs/v2/06 §5, row `serve`): one Ring-style
   handler over `kimun.cli.main/run`, so there is a single HTTP API over the
   `commands.*` and none re-implements a command.

   A *route* is data: `{:method :get|:post :path \"/stores/check\" :write? bool
   :handler (fn [request ctx] response)}`. `routes` is the table; `handler`
   builds the dispatcher from any table, so adding a route is one more entry
   and never touches the dispatcher. A path is literal segments, `:param`
   segments (bound in `:path-params`) and an optional final `*` (the rest of
   the path, `:path-params {:* \"a/b\"}`).

   A route handler receives the Ring request plus `:path-params`,
   `:query-params` (keyword keys, string values) and `:json` (the parsed body
   object of a POST with a body, keyword keys), and the ctx
   `{:store :token :cwd :env :cors? :run :lock}`. It returns either an
   *envelope* (`kimun.cli.out`), which the server serialises as JSON under the
   status given by `status-of`, or a Ring response map (it has `:status`),
   passed through. Anything that escapes is a failure envelope."
  (:require [cheshire.core :as json]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [kimun.cli.errors :as errors]
            [kimun.cli.out :as out]
            #?(:clj [org.httpkit.server :as hk])))

;; ---------------------------------------------------------------- envelope -> status

(defn status-of
  "The HTTP status of an envelope, in ONE place. Success is 200; a failure maps
   its `:exit` code (docs/v2/06 §3): 1 missing 404; 2 ambiguous 409 (more than
   one candidate is a conflict the client resolves by naming one; 300 is for
   content negotiation, not for JSON APIs); 3 rejected 422 (well-formed but not
   acceptable); 4 conflict 409; 5 store 500; 6 usage 400; 7 capability 403;
   70 and anything unknown 500."
  [envelope]
  (if (:ok envelope)
    200
    (get {1 404, 2 409, 3 422, 4 409, 5 500, 6 400, 7 403, 70 500} (:exit envelope) 500)))

;; ------------------------------------------------------------------ plumbing

(def ^:private json-type "application/json; charset=utf-8")

(def env-var
  "Environment variable that configures the bearer token when `--token` is
   absent. It replaces sldb v1's `SLDB_SERVE_TOKEN`."
  "KIMUN_SERVE_TOKEN")

(defn resolve-token
  "The token: the flag wins over `env` (a map) `KIMUN_SERVE_TOKEN`; empty or
   missing means no auth (nil)."
  [flag env]
  (let [t (if (some? flag) flag (get env env-var))]
    (when (and (string? t) (seq t)) t)))

(defn- constant-time=
  "Equality of two strings in time independent of where they differ."
  [a b]
  #?(:clj (java.security.MessageDigest/isEqual (.getBytes ^String a "UTF-8") (.getBytes ^String b "UTF-8"))
     :cljs (= a b)))

(defn- authorized?
  "True when no token is configured or the request presents `Bearer <token>`."
  [token request]
  (or (nil? token)
      (let [h (or (get-in request [:headers "authorization"]) "")
            presented (if (str/starts-with? h "Bearer ") (subs h 7) "")]
        (constant-time= presented token))))

(defn- cors-headers
  "v1's CORS headers (any origin, GET/POST/OPTIONS); `Authorization` is added to
   the allowed headers, otherwise a browser could never send the token."
  [cors?]
  (when cors?
    {"Access-Control-Allow-Origin" "*"
     "Access-Control-Allow-Methods" "GET, POST, OPTIONS"
     "Access-Control-Allow-Headers" "Content-Type, Authorization"}))

(defn- respond
  "A Ring response: `status`, JSON body of `envelope`, content type and CORS."
  [status envelope cors? & [headers]]
  {:status status
   :headers (merge {"Content-Type" json-type} (cors-headers cors?) headers)
   :body (json/generate-string envelope)})

(defn- fail [type message data exit] (out/failure type message data exit))

(defn- body-text [body]
  (cond (nil? body) "" (string? body) body :else #?(:clj (slurp body) :cljs "")))

(defn- decode [s] #?(:clj (java.net.URLDecoder/decode ^String s "UTF-8") :cljs s))

(defn- parse-query
  "`a=1&b=x%20y` as `{:a \"1\" :b \"x y\"}`; the last repetition of a key wins."
  [qs]
  (into {}
        (for [pair (str/split (or qs "") #"&") :when (seq pair)
              :let [[k v] (str/split pair #"=" 2)]]
          [(keyword (decode k)) (decode (or v ""))])))

;; ------------------------------------------------------------------ routing

(defn- match-path
  "Path params when `pattern` matches `path` (both strings), else nil."
  [pattern path]
  (let [ps (str/split pattern #"/") xs (str/split path #"/")]
    (loop [ps ps xs xs acc {}]
      (cond
        (and (empty? ps) (empty? xs)) acc
        (empty? ps) nil
        (= "*" (first ps)) (when (empty? (rest ps)) (assoc acc :* (str/join "/" xs)))
        (empty? xs) nil
        (str/starts-with? (first ps) ":") (recur (rest ps) (rest xs) (assoc acc (keyword (subs (first ps) 1)) (decode (first xs))))
        (= (first ps) (first xs)) (recur (rest ps) (rest xs) acc)
        :else nil))))

(defn- specificity [{:keys [path]}]
  (count (filter #(or (= "*" %) (str/starts-with? % ":")) (str/split path #"/"))))

(defn- find-route
  "`{:route r :params p}` for the best route of `routes` for `method`/`path`,
   or `{:allowed #{methods}}` when only other methods match (405), or nil."
  [routes method path]
  (let [hits (keep (fn [r] (when-let [p (match-path (:path r) path)] {:route r :params p})) routes)
        by-m (filter #(= method (get-in % [:route :method])) hits)]
    (cond (seq by-m) (first (sort-by #(specificity (:route %)) by-m))
          (seq hits) {:allowed (set (map #(get-in % [:route :method]) hits))})))

;; ---------------------------------------------------------- command routes

(defn run-command
  "Runs `argv` through the CLI of ctx (`:run`, default `kimun.cli.main/run`)
   with `--store` and `--format json` appended; returns its envelope."
  [ctx argv]
  (let [run (or (:run ctx) #?(:clj (requiring-resolve 'kimun.cli.main/run)))
        argv (cond-> (vec argv) (:store ctx) (into ["--store" (str (:store ctx))]) true (into ["--format" "json"]))]
    (:envelope (run argv (or (:env ctx) {}) (str (or (:cwd ctx) "."))))))

(defn command-route
  "A route handler that runs a CLI command. `tokens` is the fixed command, e.g.
   `[\"journal\" \"show\"]`. Options: `:query` `{param \"--flag\"}` forwards
   those query/path parameters as flags when present; `:positional` `[param …]`
   appends those parameters as positional arguments; `:required` `[param …]`
   rejects a request lacking one with a usage failure (400); `:argv-fn`
   `(fn [request])` returning extra argv, for anything else. The result is the
   command's own envelope."
  [tokens & {:keys [query positional required argv-fn]}]
  (fn [request ctx]
    (let [params (merge (:query-params request) (:path-params request))
          missing (first (remove #(contains? params %) required))]
      (if missing
        (fail :serve/missing-parameter (str "Missing parameter: " (name missing)) {:parameter (name missing)} 6)
        (run-command ctx (-> (vec tokens)
                             (into (keep #(get params %) positional))
                             (into (mapcat (fn [[k flag]] (when (contains? params k) [flag (get params k)])) query))
                             (into (when argv-fn (argv-fn request)))))))))

(defn- health
  "`/health`: the store's verdict (`stores check`) as `{:status :findings :action}`;
   200 even when degraded, as in v1."
  [_request ctx]
  (let [e (run-command ctx ["stores" "check"])]
    (out/ok "health"
            {:store (:store e) :revision (:revision e)
             :data (if (:ok e)
                     {:status "ok" :findings [] :action nil}
                     {:status "degraded"
                      :findings [{:kind (get-in e [:error :type]) :message (get-in e [:error :message])}]
                      :action "run: kimun stores verify"})})))

(defn- ingest
  "`POST /docs/ingest` `{name, markdown}`: writes the markdown to a temporary
   file and runs `docs ingest FILE --name NAME`."
  [request ctx]
  (let [{:keys [name markdown]} (:json request)]
    (if-not (and (string? name) (seq name) (string? markdown))
      (fail :serve/bad-body "Expected {name: str, markdown: str}." {} 6)
      (let [dir (fs/create-temp-dir {:prefix "kimun-serve-"})
            file (str (fs/path dir "document.md"))]
        (try (spit file markdown)
             (run-command ctx ["docs" "ingest" file "--name" name])
             (finally (fs/delete-tree dir)))))))

(def routes
  "The routing table (a vector of route entries; see the namespace docstring).
   To expose a new command, add one entry here or pass your own vector to
   `handler`/`start!`."
  [{:method :get :path "/health" :handler health}
   {:method :get :path "/stores" :handler (command-route ["stores" "show"])}
   {:method :get :path "/stores/check" :handler (command-route ["stores" "check"])}
   {:method :get :path "/stores/links" :handler (command-route ["stores" "links"])}
   {:method :get :path "/docs" :handler (command-route ["docs" "list"])}
   {:method :get :path "/docs/render"
    :handler (command-route ["docs" "render"] :positional [:name] :required [:name])}
   {:method :get :path "/journal"
    :handler (command-route ["journal" "show"] :query {:limit "--limit" :by "--by" :tree "--tree"})}
   {:method :get :path "/journal/verify" :handler (command-route ["journal" "verify"])}
   {:method :post :path "/docs/ingest" :write? true :handler ingest}])

;; ------------------------------------------------------------------ handler

(defn- parse-json-body
  "The JSON object of a POST body: nil for an empty body, else a map (keyword
   keys); anything else raises via the returned failure envelope."
  [request]
  (let [txt (body-text (:body request))]
    (if (str/blank? txt)
      {:json nil}
      (let [v (try (json/parse-string txt true) (catch #?(:clj Exception :cljs :default) _ ::bad))]
        (if (map? v)
          {:json v}
          {:failure (fail :serve/bad-json "Expected a JSON object body" {} 6)})))))

(defn- call-route [{:keys [route params]} request ctx]
  (let [{:keys [json failure]} (if (= :post (:request-method request)) (parse-json-body request) {})
        request (assoc request :path-params params :json json
                       :query-params (parse-query (:query-string request)))
        run (fn [] (errors/wrap #((:handler route) request ctx)))
        result (cond failure failure
                     (:write? route) (locking (:lock ctx) (run))
                     :else (run))]
    (if (and (map? result) (contains? result :status))
      (update result :headers #(merge {"Content-Type" json-type} (cors-headers (:cors? ctx)) %))
      (respond (status-of result) result (:cors? ctx)))))

(defn handler
  "The Ring handler for `routes` (a vector of route entries) and config
   `{:store path :token string-or-nil :cwd dir :env {} :cors? bool :run fn}`
   (`:run` defaults to `kimun.cli.main/run`). Order: OPTIONS preflight (exempt
   from auth; 204 with CORS headers, 404 when CORS is off), then auth (401 for
   every GET/POST without the bearer token, before any routing), then the
   route (404 unknown path, 405 known path with another method, 400 invalid
   JSON body)."
  [routes config]
  (let [ctx (assoc config :lock (Object.))
        cors? (:cors? config)]
    (fn [request]
      (let [method (:request-method request)
            path (or (:uri request) "/")]
        (cond
          (= :options method)
          (if cors?
            {:status 204 :headers (merge {"Content-Type" json-type} (cors-headers true)) :body ""}
            (respond 404 (fail :serve/cors-disabled "CORS disabled" {} 1) false))

          (not (authorized? (:token config) request))
          (respond 401 (fail :serve/unauthorized "unauthorized" {} 6) cors?
                   {"WWW-Authenticate" "Bearer"})

          (not (#{:get :post} method))
          (respond 405 (fail :serve/method-not-allowed "Method not allowed" {:method (name method)} 6) cors?)

          :else
          (let [found (find-route routes method path)]
            (cond
              (:route found) (call-route found request ctx)
              (:allowed found) (respond 405 (fail :serve/method-not-allowed "Method not allowed" {:method (name method)} 6) cors?
                                        {"Allow" (str/join ", " (sort (map #(str/upper-case (name %)) (:allowed found))))})
              :else (respond 404 (fail :serve/unknown-route (str "Unknown route: " path) {:path path} 1) cors?))))))))

;; ------------------------------------------------------------------- server

(defn start!
  "Starts an http-kit server for `routes` (default `routes`) with `config`
   (see `handler`) plus `:host` (default \"127.0.0.1\") and `:port` (default 0 =
   a free port). Returns `{:host :port :stop!}` with the actual port; call
   `(stop! server)` or `((:stop! server))`."
  ([config] (start! routes config))
  ([routes config]
   #?(:clj (let [host (or (:host config) "127.0.0.1")
                 srv (hk/run-server (handler routes config) {:ip host :port (or (:port config) 0) :legacy-return-value? false})]
             {:host host :port (hk/server-port srv) :stop! (fn [] (hk/server-stop! srv {:timeout 100}))})
      :cljs (throw (ex-info "serve needs the JVM" {})))))

(defn stop!
  "Stops a server returned by `start!`."
  [server]
  ((:stop! server)))
