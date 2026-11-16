(ns kimun.surface.http.guard
  "The security checks of the HTTP surface, in one place (docs/v2/06 §serve):
   what `serve` may be started with (`check-config!`), what a request may name
   (`check-inputs`: files confined to the project, document names without path
   separators) and what a request may come from (`check-origin`, `check-host`,
   `check-content-type`). Every check returns nil when the request passes, or a
   failure envelope (`kimun.cli.out`) the handler answers; none touches the
   disk except the symlink resolution of `confine`, which only reads."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [kimun.cli.out :as out]
            [kimun.kernel.err :as err]))

;; ------------------------------------------------------------------ startup

(defn loopback-host?
  "True when `host` (a bind address or a name) is loopback: `localhost`, `::1`
   (bracketed or not) or any `127.x.y.z`."
  [host]
  (let [h (str/lower-case (str/trim (str host)))
        h (if (and (str/starts-with? h "[") (str/ends-with? h "]")) (subs h 1 (dec (count h))) h)]
    (boolean (or (= h "localhost") (= h "::1")
                 (when-let [[_ & octets] (re-matches #"127\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})" h)]
                   (every? #(<= (parse-long %) 255) octets))))))

(defn check-config!
  "Raises the usage failure `cli/usage` when `config` (`:host :token :cors?`)
   cannot be served safely: a token that is present but empty; a host that is
   not loopback without a token; `:cors?` without a token. Returns `config`."
  [{:keys [host token cors?] :as config}]
  (let [host (or host "127.0.0.1")]
    (when (and (some? token) (not (and (string? token) (seq token))))
      (err/raise :cli/usage "the token is empty; give `--token TOKEN` (or $KIMUN_SERVE_TOKEN) a value, or leave both unset for loopback use" {:option "token"}))
    (when (and (nil? token) (not (loopback-host? host)))
      (err/raise :cli/usage (str "--host " host " is not loopback and serve has no token: set `--token TOKEN` or $KIMUN_SERVE_TOKEN, or bind 127.0.0.1")
                 {:option "host" :value host}))
    (when (and cors? (nil? token))
      (err/raise :cli/usage "--cors needs a token: set `--token TOKEN` or $KIMUN_SERVE_TOKEN, or drop --cors" {:option "cors"}))
    config))

;; ------------------------------------------------------------------ inputs

(defn- outside [parameter value why]
  (out/failure :serve/path-outside-project
               (str "`" parameter "` must name something inside the served project (" why ")")
               {:parameter (name parameter) :value value} 6))

(def ^:private protected-segments
  "Directories inside the project that a request may never name: the store and git."
  #{".kimun" ".git"})

(defn- segments [p] (remove #{"" "."} (str/split p #"[/\\]+")))

(defn confine
  "Nil when `p` names a place inside `root` (an existing directory): relative,
   no `..`, no `.git`/`.kimun` segment, and — after following every symlink of
   the existing part of the path, a dangling one included — still under the
   real path of `root`. Otherwise the reason (a string)."
  [root p]
  (let [segs (segments p)]
    (cond
      (str/includes? p "\u0000") "NUL byte"
      (or (str/starts-with? p "/") (str/starts-with? p "\\") (str/starts-with? p "~")
          (re-find #"^[A-Za-z]:" p)) "absolute path"
      (some #{".."} segs) "`..` segment"
      (some #(contains? protected-segments (str/lower-case %)) segs) "protected directory"
      :else
      (let [real-root (str (fs/real-path root))
            inside? (fn [x] (let [s (str x)] (or (= s real-root) (str/starts-with? s (str real-root java.io.File/separator)))))]
        (loop [cur (fs/path real-root) [s & more] segs]
          (cond
            (nil? s) nil
            :else
            (let [nxt (fs/path cur s)]
              (if (fs/exists? nxt {:nofollow-links true})
                (let [real (try (fs/real-path nxt) (catch Exception _ nil))]
                  (cond (nil? real) "dangling symlink"
                        (not (inside? real)) "symlink leaves the project"
                        :else (recur real more)))
                nil))))))))

(defn project-root
  "The directory the server serves: the real path of `cwd` (default \".\")."
  [cwd]
  (str (fs/real-path (fs/absolutize (or cwd ".")))))

(defn- walk
  "Every `[key value]` of the maps nested in `x` (maps and vectors)."
  [x]
  (cond (map? x) (mapcat (fn [[k v]] (cons [k v] (walk v))) x)
        (sequential? x) (mapcat walk x)
        :else nil))

(def ^:private name-keys
  "Body keys that name a document or model; on a write route they may end up as a file name."
  #{:name :id :doc :model})

(defn check-inputs
  "The failure for the first input of `request` (parsed `:json` and
   `:query-params`) that escapes the project, else nil. Path-valued keys are
   those the route lists in `:paths`; on a `:write?` route the name keys
   (`:name :id :doc :model`, at any depth of the body) must not carry a path
   separator. `root` is the project directory the server runs in."
  [route request root]
  (let [params (merge (:query-params request) (:json request))
        path-fail (some (fn [k] (let [v (get params k)]
                                  (when (and (string? v) (not (str/blank? v)))
                                    (when-let [why (confine root v)] (outside k v why)))))
                        (:paths route))
        name-fail (when (:write? route)
                    (some (fn [[k v]]
                            (when (and (contains? name-keys k) (string? v)
                                       (or (re-find #"[/\\\u0000]" v) (#{"." ".."} v)))
                              (outside k v "a name cannot contain path separators")))
                          (concat (walk (:json request)) (seq (:query-params request)))))]
    (or path-fail name-fail)))

;; ------------------------------------------------------------------ request origin

(defn- hostport
  "`[host port]` of a Host header or an origin authority (`host`, `host:80`,
   `[::1]:80`); port is a long or nil."
  [s]
  (when (string? s)
    (let [s (str/trim s)]
      (if-let [[_ h p] (re-matches #"(\[[^\]]*\])(?::(\d+))?" s)]
        [h (some-> p parse-long)]
        (let [[_ h p] (re-matches #"([^:]*)(?::(\d+))?" s)]
          (when h [h (some-> p parse-long)]))))))

(defn- own-authority?
  "True when `authority` is a loopback host on `port` (nil port = unknown, not compared)."
  [authority port default-port]
  (when-let [[h p] (hostport authority)]
    (and (loopback-host? h) (or (nil? port) (= (or p default-port) port)))))

(defn check-host
  "Failure when the request carries a `Host` that is not a loopback name or IP
   on the server's `port` (DNS rebinding); nil when it matches or there is no
   `Host`. Only meaningful for a server without a token."
  [request port]
  (let [h (get-in request [:headers "host"])]
    (when (and h (not (own-authority? h port 80)))
      (out/failure :serve/host-not-allowed "Host is not a loopback name on the port of this server"
                   {:host h} 7))))

(defn check-origin
  "Failure when the request carries an `Origin` other than the server's own
   (`http://` + a loopback authority on `port`); nil otherwise. A request
   without `Origin` (curl, scripts, same-origin GETs) passes."
  [request port]
  (let [o (get-in request [:headers "origin"])]
    (when (and o (not (when-let [[_ auth] (re-matches #"(?i)http://([^/]+)" (str/trim o))]
                        (own-authority? auth port 80))))
      (out/failure :serve/origin-not-allowed "cross-origin requests are not accepted (start with --cors and a token to allow them)"
                   {:origin o} 7))))

(defn json-content-type?
  "True when `content-type` is `application/json` (parameters ignored)."
  [content-type]
  (boolean (and (string? content-type)
                (= "application/json" (str/lower-case (str/trim (first (str/split content-type #";"))))))))
