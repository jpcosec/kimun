(ns kimun.surface.http.support
  "Shared helpers of the HTTP route tests: a temp project with a store, a real
   server on port 0 and JSON requests against it."
  (:require [kimun.test-util :as ktu] [babashka.fs :as fs]
            [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]
            [kimun.cli.main :as main]
            [kimun.surface.http :as h]))

(def env "Environment of the CLI runs." {"USER" "tester"})

(def ticket-edn
  "Path of the fixture model `TicketDoc` (title, status, tags, body)."
  (str (fs/absolutize "test/fixtures/models/ticket.edn")))

(defn run
  "Runs the CLI `argv` in `cwd`; returns its envelope."
  [cwd & argv]
  (:envelope (main/run argv env cwd)))

(defn project
  "A temp project directory with an initialised store; returns its path."
  []
  (let [proj (str (ktu/temp-dir "kimun-serve-test-"))]
    (run proj "stores" "init" "--name" "p")
    proj))

(defn with-server
  "Calls `(f base-url proj)` with a server over a project prepared by
   `(setup proj)` (default: just a store); always stops the server."
  ([f] (with-server {} (fn [_]) f))
  ([setup f] (with-server {} setup f))
  ([config setup f]
   (let [proj (project)
         _ (setup proj)
         server (h/start! (merge {:store proj :cwd proj :env env :token nil} config))]
     (try (f (str "http://127.0.0.1:" (:port server)) proj)
          (finally (h/stop! server))))))

(defn req
  "An HTTP request; returns the response with the parsed JSON body as `:json`.
   `body` is a Clojure map sent as JSON."
  ([method url] (req method url nil))
  ([method url body]
   (let [r (http/request (cond-> {:method method :uri url :throw false}
                           body (-> (assoc :body (if (string? body) body (json/generate-string body)))
                                    (assoc-in [:headers "Content-Type"] "application/json"))))]
     (assoc r :json (when-not (str/blank? (:body r)) (try (json/parse-string (:body r) true) (catch Exception _ nil)))))))

(defn req*
  "An HTTP request with explicit `headers` (a map) and a raw string `body`;
   returns the response with the parsed JSON body as `:json`."
  ([method url headers] (req* method url headers nil))
  ([method url headers body]
   (let [r (http/request (cond-> {:method method :uri url :throw false :headers headers}
                           body (assoc :body body)))]
     (assoc r :json (when-not (str/blank? (:body r)) (try (json/parse-string (:body r) true) (catch Exception _ nil)))))))
