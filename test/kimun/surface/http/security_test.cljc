(ns kimun.surface.http.security-test
  "What docs/v2/06 §3 `serve` already specifies about access, pinned against a
   real server on 127.0.0.1: the bearer token, the 405 by method, CORS only
   under `--cors` and the loopback bind by default. Vectors the spec does not
   cover (huge bodies, hostile `path`s, ...) are measured in
   runs/subagents/2026-11-15-S1-serve-security/vectors.md, not fixed here."
  (:require [babashka.fs :as fs]
            [babashka.http-client :as http]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.surface.http :as h]
            [kimun.surface.http.support :refer [req with-server]]))

(def ^:private token "s3cret-token-9f2")

(defn- authed
  "GET/POST `url` with the raw `Authorization` header value `auth` (nil: none)."
  [method url auth]
  (let [r (http/request
           (cond-> {:method method :uri url :throw false}
             auth (assoc :headers {"Authorization" auth})))]
    (:status r)))

(defn- tree-listing
  "Every file under `dir` with its size, to see that nothing was written."
  [dir]
  (into (sorted-map) (for [f (file-seq (fs/file dir)) :when (.isFile f)] [(str f) [(.length f) (.lastModified f)]])))

(deftest unauthenticated-write-is-401-and-leaks-nothing
  (with-server {:token token} (fn [_])
    (fn [base proj]
      (let [before (tree-listing proj)
            r (req :post (str base "/docs/ingest") {:name "x" :markdown "# X\n"})]
        (is (= 401 (:status r)))
        (is (= "Bearer" (get-in r [:headers "www-authenticate"])))
        (is (not (str/includes? (:body r) token)) "the body never echoes the token")
        (is (not (str/includes? (:body r) (str proj))) "the body never names the store path")
        (is (= before (tree-listing proj)) "nothing was written under the project")))))

(deftest only-the-bearer-scheme-with-the-exact-token-passes
  (with-server {:token token} (fn [_])
    (fn [base _]
      (let [url (str base "/health")]
        (is (= 200 (authed :get url (str "Bearer " token))))
        (is (= 401 (authed :get url (str "Basic " token))) "another scheme")
        (is (= 401 (authed :get url token)) "no scheme")
        (is (= 401 (authed :get url (str "Bearer " token "x"))) "longer token")
        (is (= 401 (authed :get url (str "Bearer " (subs token 1)))) "shorter token")
        (is (= 401 (authed :get url nil)) "no header")))))

(deftest a-wrong-method-is-405-with-allow
  (with-server {} (fn [_])
    (fn [base _]
      (let [r (req :post (str base "/health") {})]
        (is (= 405 (:status r)))
        (is (= "GET" (get-in r [:headers "allow"]))))
      (is (= "POST" (get-in (req :get (str base "/save")) [:headers "allow"])))
      (doseq [m [:put :delete :patch]]
        (is (= 405 (:status (req m (str base "/health")))) (name m))))))

(deftest cors-preflight-exists-only-with-the-flag
  (with-server {} (fn [_])
    (fn [base _]
      (let [r (req :options (str base "/health"))]
        (is (= 404 (:status r)))
        (is (nil? (get-in r [:headers "access-control-allow-origin"])))
        (is (nil? (get-in (req :get (str base "/health")) [:headers "access-control-allow-origin"]))))))
  (with-server {:cors? true :token token} (fn [_])
    (fn [base _]
      (let [r (req :options (str base "/health"))]
        (is (= 204 (:status r)))
        (is (str/includes? (get-in r [:headers "access-control-allow-headers"]) "Authorization")))
      (is (= "*" (get-in (req :get (str base "/health")) [:headers "access-control-allow-origin"]))
          "even the 401 carries CORS headers so a browser can read it"))))

(deftest the-default-bind-is-loopback-only
  (let [server (h/start! {:store nil :cwd "." :env {} :token nil})
        local (.getHostAddress (java.net.InetAddress/getLocalHost))
        others (when-not (str/starts-with? local "127.") [local])]
    (try
      (is (= "127.0.0.1" (:host server)))
      (is (= 404 (authed :get (str "http://127.0.0.1:" (:port server) "/nope") nil)))
      (doseq [ip others]
        (is (thrown? Exception (authed :get (str "http://" ip ":" (:port server) "/nope") nil))
            (str ip " must not reach the server")))
      (finally (h/stop! server)))))
