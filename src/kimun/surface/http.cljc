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
            [clojure.string :as str]
            [kimun.cli.errors :as errors]
            [kimun.cli.out :as out]
            [kimun.surface.http.guard :as guard]
            [kimun.surface.http.routes :as routes]
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
  "The token: the flag wins over `env` (a map) `KIMUN_SERVE_TOKEN`; neither
   given means no token (nil). A value that is given but empty (`--token \"\"`,
   `KIMUN_SERVE_TOKEN=`) is the usage failure `cli/usage`, never \"auth off\"."
  [flag env]
  (let [t (if (some? flag) flag (get env env-var))]
    (cond (nil? t) nil
          (and (string? t) (seq t)) t
          :else (throw (ex-info "the token is empty; give `--token TOKEN` (or $KIMUN_SERVE_TOKEN) a value, or leave both unset for loopback use"
                                {:type :cli/usage :option "token"})))))

(defn- constant-time=
  "Equality of two strings in time independent of where they differ and of
   their lengths (the SHA-256 digests are compared)."
  [a b]
  #?(:clj (let [sha (fn [^String x] (.digest (java.security.MessageDigest/getInstance "SHA-256") (.getBytes x "UTF-8")))]
            (java.security.MessageDigest/isEqual (sha a) (sha b)))
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

(def routes
  "The routing table (a vector of route entries; see the namespace docstring),
   defined in `kimun.surface.http.routes`. To expose a new command, add one
   entry there or pass your own vector to `handler`/`start!`."
  routes/routes)

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
  (let [post? (= :post (:request-method request))
        request (cond-> request post? (assoc :body (body-text (:body request)))) ; a stream is read once
        bad-type? (and post? (not (str/blank? (body-text (:body request))))
                       (not (guard/json-content-type? (get-in request [:headers "content-type"]))))
        {:keys [json failure]} (if (and post? (not bad-type?)) (parse-json-body request) {})
        request (assoc request :path-params params :json json
                       :query-params (parse-query (:query-string request)))
        refused (when-not (or failure bad-type?) (guard/check-inputs route request (guard/project-root (:cwd ctx))))
        run (fn [] (errors/wrap #((:handler route) request ctx)))
        result (cond bad-type? (fail :serve/unsupported-media-type "A request body must be sent as Content-Type: application/json" {} 6)
                     failure failure
                     refused refused
                     (:write? route) (locking (:lock ctx) (run))
                     :else (run))]
    (cond (and (map? result) (contains? result :status))
          (update result :headers #(merge {"Content-Type" json-type} (cors-headers (:cors? ctx)) %))
          bad-type? (respond 415 result (:cors? ctx))
          :else (respond (status-of result) result (:cors? ctx)))))

(defn handler
  "The Ring handler for `routes` (a vector of route entries) and config
   `{:store path :token string-or-nil :cwd dir :env {} :cors? bool :run fn
   :port n :bound-port deref-or-nil}` (`:run` defaults to `kimun.cli.main/run`;
   the port the server listens on is `:port`, else `@:bound-port`, else not
   compared). Order: OPTIONS preflight (exempt from auth; 204 with CORS
   headers, 404 when CORS is off), then without a token the `Host` check (403
   unless a loopback name on the server's port), then the `Origin` check (403
   for any `Origin` that is not the server's own, on every path but `/health`,
   unless `:cors?`, which `start!` only allows with a token), then auth (401
   for every GET/POST without the bearer token, before any routing), then the
   route (404 unknown path, 405 known path with another method, 415 a POST body
   that is not `application/json`, 400 invalid JSON body or an input that
   leaves the project, `serve/path-outside-project`)."
  [routes config]
  (let [ctx (assoc config :lock (Object.))
        cors? (:cors? config)
        port #(let [p (:port config)] (if (and (integer? p) (pos? p)) p (some-> (:bound-port config) deref)))
        forbidden (fn [e] (respond (status-of e) e cors?))]
    (fn [request]
      (let [method (:request-method request)
            path (or (:uri request) "/")]
        (cond
          (= :options method)
          (if cors?
            {:status 204 :headers (merge {"Content-Type" json-type} (cors-headers true)) :body ""}
            (respond 404 (fail :serve/cors-disabled "CORS disabled" {} 1) false))

          (and (nil? (:token config)) (guard/check-host request (port)))
          (forbidden (guard/check-host request (port)))

          (and (not= "/health" path) (not (and cors? (:token config))) (guard/check-origin request (port)))
          (forbidden (guard/check-origin request (port)))

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
   `(stop! server)` or `((:stop! server))`. Raises the usage failure
   `cli/usage` (`guard/check-config!`) for a config it will not serve: an empty
   token, a non-loopback `:host` or `:cors?` without a token."
  ([config] (start! routes config))
  ([routes config]
   #?(:clj (let [host (or (:host config) "127.0.0.1")
                 _ (guard/check-config! config)
                 bound (atom nil)
                 srv (hk/run-server (handler routes (assoc config :bound-port bound))
                                    {:ip host :port (or (:port config) 0) :legacy-return-value? false})]
             (reset! bound (hk/server-port srv))
             {:host host :port (hk/server-port srv) :stop! (fn [] (hk/server-stop! srv {:timeout 100}))})
      :cljs (throw (ex-info "serve needs the JVM" {})))))

(defn stop!
  "Stops a server returned by `start!`."
  [server]
  ((:stop! server)))
