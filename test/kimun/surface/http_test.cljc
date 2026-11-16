(ns kimun.surface.http-test
  "The HTTP surface (`kimun.surface.http`): real requests against a server on a
   free port over a temporary store, plus direct calls of the handler (a Ring
   request in, a response map out). Ports of the server-level promises of sldb
   v1's test_serve_auth.py, test_serve_stores_check.py and test_serve.py; the
   feature routes v1 tests cover are listed as deferred in the porting report."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.commands.serve :as serve]
            [kimun.cli.main :as main]
            [kimun.surface.http :as h]))

(def ^:private env {"USER" "tester"})
(def ^:private token "s3cret-token")

(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))

(defn- project
  "A temp project with a store and one document `doc` (\"# T\\n\\npara one\")."
  []
  (let [proj (str (ktu/temp-dir "kimun-serve-test-"))]
    (run proj "stores" "init" "--name" "p")
    (spit (str (fs/path proj "doc.md")) "# T\n\npara one\n")
    (run proj "docs" "ingest" "doc.md")
    proj))

(defn- with-server
  "Calls `(f base-url proj)` against a running server; always stops it."
  [config f]
  (let [proj (project)
        server (h/start! (merge {:store proj :cwd proj :env env :token nil} config))]
    (try (f (str "http://127.0.0.1:" (:port server)) proj)
         (finally (h/stop! server)))))

(defn- req
  ([method url] (req method url nil nil))
  ([method url tok body]
   (let [r (http/request (cond-> {:method method :uri url :throw false}
                           tok (assoc :headers {"Authorization" (str "Bearer " tok)})
                           body (-> (assoc :body body) (assoc-in [:headers "Content-Type"] "application/json"))))]
     (assoc r :json (when-not (str/blank? (:body r)) (try (json/parse-string (:body r) true) (catch Exception _ nil)))))))

(deftest port-zero-picks-a-free-port
  (with-server {} (fn [base _] (is (re-find #":\d{4,5}$" base)) (is (not (str/ends-with? base ":0"))))))

(deftest health-reports-the-store-verdict
  ;; v1: test_serve_endpoints_use_real_store (the /health assertion)
  (with-server {} (fn [base _]
                    (let [r (req :get (str base "/health"))]
                      (is (= 200 (:status r)))
                      (is (= {:status "ok" :findings [] :action nil} (get-in r [:json :data])))
                      (is (true? (get-in r [:json :ok])))))))

(deftest health-degraded-without-a-store
  (let [empty-dir (str (ktu/temp-dir))
        server (h/start! {:store (str empty-dir "/nope") :cwd empty-dir :env env})]
    (try (let [r (req :get (str "http://127.0.0.1:" (:port server) "/health"))]
           (is (= 200 (:status r)))
           (is (= "degraded" (get-in r [:json :data :status]))))
         (finally (h/stop! server)))))

(deftest unknown-route-is-404-with-the-error-envelope
  ;; v1: dispatch -> {"ok": false, "error": "Unknown route: …"}, 404
  (with-server {} (fn [base _]
                    (let [r (req :get (str base "/nope"))]
                      (is (= 404 (:status r)))
                      (is (= {:ok false :error {:type "serve/unknown-route" :message "Unknown route: /nope" :data {:path "/nope"}} :exit 1}
                             (:json r)))))))

(deftest content-type-is-json
  (with-server {} (fn [base _]
                    (is (= "application/json; charset=utf-8" (get-in (req :get (str base "/stores")) [:headers "content-type"])))
                    (is (= "application/json; charset=utf-8" (get-in (req :get (str base "/nope")) [:headers "content-type"]))))))

(deftest method-not-allowed-is-405
  ;; v1: any method other than GET/POST -> 405 "Method not allowed"; here also a wrong method on a known path.
  (with-server {} (fn [base _]
                    (let [r (req :post (str base "/health"))]
                      (is (= 405 (:status r)))
                      (is (= "GET" (get-in r [:headers "allow"])))
                      (is (= "serve/method-not-allowed" (get-in r [:json :error :type]))))
                    (is (= 405 (:status (req :put (str base "/health"))))))))

(deftest bad-json-body-is-400
  ;; v1: test_extract_segments_rejects_a_malformed_body_with_400 (server-level part: a non-object / invalid body)
  (with-server {} (fn [base _]
                    (doseq [b ["{not json" "[1,2]" "\"s\""]]
                      (let [r (req :post (str base "/docs/ingest") nil b)]
                        (is (= 400 (:status r)) b)
                        (is (= "serve/bad-json" (get-in r [:json :error :type]))))))))

(deftest malformed-write-body-is-400-and-store-untouched
  ;; v1: test_extract_segments_rejects_a_malformed_body_with_400 (every malformed payload -> 400, one message)
  (with-server {} (fn [base _]
                    (doseq [b [{} {:name "x"} {:markdown "# T"} {:name 7 :markdown "# T"} {:name "" :markdown "# T"} {:name "x" :markdown nil}]]
                      (let [r (req :post (str base "/docs/ingest") nil (json/generate-string b))]
                        (is (= 400 (:status r)) (pr-str b))
                        (is (= "Expected {name: str, markdown: str}." (get-in r [:json :error :message])))))
                    (is (= 1 (count (get-in (req :get (str base "/docs")) [:json :data])))))))

(deftest stores-describes-the-current-store
  ;; v1: test_stores_describe_current_store
  (with-server {} (fn [base _]
                    (let [r (req :get (str base "/stores"))]
                      (is (= 200 (:status r)))
                      (is (= "stores show" (get-in r [:json :command])))
                      (is (true? (get-in r [:json :ok])))))))

(deftest stores-check-and-links
  ;; v1: test_check_lists_every_model_and_document_when_clean (the clean-store promise: 200, ok)
  (with-server {} (fn [base _]
                    (let [c (req :get (str base "/stores/check"))
                          l (req :get (str base "/stores/links"))]
                      (is (= [200 true "stores check"] [(:status c) (get-in c [:json :ok]) (get-in c [:json :command])]))
                      (is (= [200 "stores links"] [(:status l) (get-in l [:json :command])]))))))

(deftest stores-check-reports-a-broken-store-as-5xx
  ;; v1: test_stores_check_reports_mismatched_hash_c — divergence of the store is reported, never a 200 ok
  (with-server {} (fn [base proj]
                    (spit (str (fs/path proj ".kimun" "log.edn")) "garbage")
                    (let [r (req :get (str base "/stores/check"))]
                      (is (false? (get-in r [:json :ok])))
                      (is (= 500 (:status r)))))))

(deftest docs-list-render-and-ingest
  (with-server {} (fn [base _]
                    (is (= ["doc"] (map :name (get-in (req :get (str base "/docs")) [:json :data]))))
                    (let [r (req :get (str base "/docs/render?name=doc"))]
                      (is (= 200 (:status r)))
                      (is (str/includes? (str (get-in r [:json :data])) "para one")))
                    (is (= 400 (:status (req :get (str base "/docs/render")))))
                    (is (= 404 (:status (req :get (str base "/docs/render?name=nope")))))
                    (let [w (req :post (str base "/docs/ingest") nil (json/generate-string {:name "second" :markdown "# S\n\nhello\n"}))]
                      (is (= 200 (:status w)) (:body w))
                      (is (= "docs ingest" (get-in w [:json :command]))))
                    (is (= #{"doc" "second"} (set (map :name (get-in (req :get (str base "/docs")) [:json :data]))))))))

;; ------------------------------------------------------------------ auth

(deftest serve-without-token-stays-open
  ;; v1: test_serve_without_token_stays_open
  (with-server {} (fn [base _]
                    (is (= 200 (:status (req :get (str base "/health")))))
                    (is (= 200 (:status (req :get (str base "/stores")))))
                    (is (= 200 (:status (req :post (str base "/docs/ingest") nil (json/generate-string {:name "o" :markdown "# O\n"}))))))))

(deftest serve-with-token-requires-the-bearer-token
  ;; v1: test_serve_with_token_requires_the_bearer_token
  (with-server {:token token} (fn [base _]
                                (let [r (req :get (str base "/health"))]
                                  (is (= 401 (:status r)))
                                  (is (= {:ok false :error {:type "serve/unauthorized" :message "unauthorized" :data {}} :exit 6} (:json r))))
                                (is (= 401 (:status (req :get (str base "/health") "wrong" nil))))
                                (is (= 200 (:status (req :get (str base "/health") token nil))))
                                (is (= 200 (:status (req :get (str base "/stores") token nil)))))))

(deftest token-blocks-writes-and-leaves-the-store-untouched
  ;; v1: test_serve_token_blocks_writes_and_leaves_the_store_untouched
  (with-server {:token token}
    (fn [base _]
      (let [body (json/generate-string {:name "hacked" :markdown "# H\n"})]
        (is (= 401 (:status (req :post (str base "/docs/ingest") nil body))))
        (is (= ["doc"] (map :name (get-in (req :get (str base "/docs") token nil) [:json :data]))))
        (is (= 200 (:status (req :post (str base "/docs/ingest") token body))))))))

(deftest options-preflight-is-exempt-from-auth
  ;; v1: test_options_preflight_is_exempt_from_auth
  (with-server {:token token :cors? true}
    (fn [base _]
      (let [r (req :options (str base "/docs/ingest"))]
        (is (= 204 (:status r)))
        (is (= "*" (get-in r [:headers "access-control-allow-origin"])))))))

(deftest env-var-configures-the-token-when-the-flag-is-absent
  ;; v1: test_env_var_configures_the_token_when_the_flag_is_absent (SLDB_SERVE_TOKEN -> KIMUN_SERVE_TOKEN)
  (is (= "KIMUN_SERVE_TOKEN" h/env-var))
  (is (nil? (h/resolve-token nil {})))
  (is (= token (h/resolve-token nil {"KIMUN_SERVE_TOKEN" token})))
  (is (= "flag-wins" (h/resolve-token "flag-wins" {"KIMUN_SERVE_TOKEN" token})))
  (is (nil? (h/resolve-token nil {"KIMUN_SERVE_TOKEN" ""}))))

;; ------------------------------------------------------------------ CORS

(deftest cors-headers-when-enabled-and-404-preflight-when-not
  ;; v1: handle_options / handle_cors_headers
  (with-server {:cors? true}
    (fn [base _]
      (let [r (req :get (str base "/health"))]
        (is (= "*" (get-in r [:headers "access-control-allow-origin"])))
        (is (= "GET, POST, OPTIONS" (get-in r [:headers "access-control-allow-methods"])))
        (is (str/includes? (get-in r [:headers "access-control-allow-headers"]) "Authorization")))))
  (with-server {}
    (fn [base _]
      (is (nil? (get-in (req :get (str base "/health")) [:headers "access-control-allow-origin"])))
      (let [r (req :options (str base "/health"))]
        (is (= 404 (:status r)))
        (is (= "CORS disabled" (get-in r [:json :error :message])))))))

;; ------------------------------------------------------------ handler, no socket

(defn- stub-run [argv _env _cwd] {:envelope {:ok true :command "stub" :data {:argv argv}}})

(defn- call [routes config request]
  (let [resp ((h/handler routes config) request)]
    (assoc resp :json (when (seq (:body resp)) (json/parse-string (:body resp) true)))))

(deftest status-mapping
  (is (= 200 (h/status-of {:ok true})))
  (is (= {1 404 2 409 3 422 4 409 5 500 6 400 7 403 70 500 99 500}
         (into {} (for [e [1 2 3 4 5 6 7 70 99]] [e (h/status-of {:ok false :exit e})])))))

(deftest a-new-route-is-one-table-entry
  (let [rs (conj h/routes
                 {:method :get :path "/things/:id" :handler (fn [r _] {:ok true :data {:id (get-in r [:path-params :id])}})}
                 {:method :get :path "/files/*" :handler (fn [r _] {:ok true :data (get-in r [:path-params :*])})}
                 {:method :get :path "/boom" :handler (fn [_ _] (throw (ex-info "bad" {})))}
                 {:method :get :path "/teapot" :handler (fn [_ _] {:status 418 :body "{}"})})]
    (is (= {:id "a b"} (:data (:json (call rs {} {:request-method :get :uri "/things/a%20b"})))))
    (is (= "x/y" (:data (:json (call rs {} {:request-method :get :uri "/files/x/y"})))))
    (is (= 500 (:status (call rs {} {:request-method :get :uri "/boom"}))))
    (is (= 418 (:status (call rs {} {:request-method :get :uri "/teapot"}))))))

(deftest command-route-builds-argv-from-the-request
  (let [cfg {:store "/s" :run stub-run :env {} :cwd "."}
        argv #(get-in (call h/routes cfg %) [:json :data :argv])]
    (is (= ["graph" "cycles" "--limit" "3" "--store" "/s" "--format" "json"]
           (argv {:request-method :get :uri "/graph/cycles" :query-string "limit=3"})))
    (is (= ["docs" "render" "my doc" "--store" "/s" "--format" "json"]
           (argv {:request-method :get :uri "/docs/render" :query-string "name=my%20doc"})))
    (is (= ["docs" "ingest"] (take 2 (argv {:request-method :post :uri "/docs/ingest" :body "{\"name\":\"n\",\"markdown\":\"# t\"}"}))))))

(deftest handler-auth-and-errors-without-a-socket
  (let [cfg {:run stub-run :token token}]
    (is (= 401 (:status (call h/routes cfg {:request-method :get :uri "/nope"}))) "auth runs before routing")
    (is (= 404 (:status (call h/routes cfg {:request-method :get :uri "/nope" :headers {"authorization" (str "Bearer " token)}}))))
    (is (= 401 (:status (call h/routes cfg {:request-method :get :uri "/stores" :headers {"authorization" token}}))) "scheme required")
    (is (= 204 (:status (call h/routes (assoc cfg :cors? true) {:request-method :options :uri "/stores"}))))
    (is (= 405 (:status (call h/routes {} {:request-method :delete :uri "/stores"}))))
    (is (= 400 (:status (call h/routes {:run stub-run} {:request-method :post :uri "/docs/ingest" :body "nope"}))))))

;; ------------------------------------------------------------ the serve group

(deftest serve-group-usage-failures
  (let [ctx {:env env :cwd "."}]
    (is (= 6 (:exit (serve/dispatch "stop" [] {} ctx))))
    (is (= "cli/usage" (get-in (serve/dispatch "stop" [] {} ctx) [:error :type])))
    (doseq [p ["x" "-1" "70000" "1.5"]]
      (is (= :cli/usage (:type (ex-data (try (serve/dispatch nil [] {:port p} ctx) (catch Exception e e))))) p))
    (is (= #{:port :host :token :cors} (set (keys serve/spec))))))
