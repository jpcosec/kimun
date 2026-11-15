(ns kimun.surface.http.circuit-test
  "The circuit's two read-only observations over HTTP: `GET /stores/sense` and
   `GET /stores/health` return the CLI's own `:data` (docs/v2/06 §4: each route
   delegates to a command) and never write into the store or its cache."
  (:require [babashka.fs :as fs]
            [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.test :refer [deftest is]]
            [kimun.surface.http.support :as s])
  (:import (java.security MessageDigest)))

(defn- sha256 [path]
  (apply str (map #(format "%02x" %)
                  (.digest (MessageDigest/getInstance "SHA-256") (fs/read-all-bytes path)))))

(defn- tree-sha
  "Relative path and sha256 of every file under `<proj>/.kimun` (cache included)."
  [proj]
  (vec (for [f (sort (map str (fs/glob (str proj "/.kimun") "**" {:hidden true})))
             :when (fs/regular-file? f)]
         [(str (fs/relativize proj f)) (sha256 f)])))

(defn- tracked
  "Ingests `doc.md`, then rewrites it as `text`."
  [text]
  (fn [proj]
    (spit (str proj "/doc.md") "# T\n\npara one\n")
    (s/run proj "docs" "ingest" "doc.md")
    (spit (str proj "/doc.md") text)))

(defn- as-json [x] (json/parse-string (json/generate-string x) true))

(deftest sense-over-http-is-the-cli-data-and-sees-the-mutation
  (s/with-server (tracked "# T\n\npara two\n")
    (fn [base proj]
      (let [r (s/req :get (str base "/stores/sense"))
            cli (as-json (:data (s/run proj "stores" "sense")))]
        (is (= 200 (:status r)))
        (is (= "stores sense" (get-in r [:json :command])))
        (is (= ["source/mutated"] (map :type (get-in r [:json :data :signals]))))
        (is (= (dissoc cli :observed-at) (dissoc (get-in r [:json :data]) :observed-at)))))))

(deftest health-over-http-reports-degraded-with-its-next-step
  (s/with-server (tracked "# T\n\npara one\n\n\n")
    (fn [base proj]
      (let [r (s/req :get (str base "/stores/health"))
            cli (as-json (:data (s/run proj "stores" "health")))]
        (is (= 200 (:status r)))
        (is (= "degraded" (get-in r [:json :data :status])))
        (is (= "run: kimun docs update doc.md --name doc" (get-in r [:json :data :next])))
        (is (= cli (get-in r [:json :data])))))))

(deftest observing-over-http-writes-nothing
  (s/with-server (tracked "# T\n\npara two\n")
    (fn [base proj]
      (let [before (tree-sha proj)]
        (is (seq before))
        (is (= 200 (:status (s/req :get (str base "/stores/sense")))))
        (is (= 200 (:status (s/req :get (str base "/stores/health")))))
        (is (= before (tree-sha proj)))))))

(defn- status [method url & [token]]
  (:status (http/request (cond-> {:method method :uri url :throw false}
                           token (assoc :headers {"Authorization" (str "Bearer " token)})))))

(deftest the-circuit-routes-honor-the-token
  (s/with-server {:token "tok"} (fn [_])
    (fn [base _]
      (doseq [path ["/stores/sense" "/stores/health"]]
        (is (= 401 (status :get (str base path))) path)
        (is (= 200 (status :get (str base path) "tok")) path)))))

(deftest the-circuit-routes-are-get-only
  (s/with-server (fn [_])
    (fn [base _]
      (doseq [path ["/stores/sense" "/stores/health"]]
        (is (= 405 (status :post (str base path))) path)))))
