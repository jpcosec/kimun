;; S1 probes. Run: bb -cp src:test:resources probe.clj  (servers on 127.0.0.1:0 over temp projects, all under /tmp)
(require '[babashka.fs :as fs] '[babashka.http-client :as http] '[cheshire.core :as json]
         '[clojure.string :as str] '[kimun.surface.http :as h] '[kimun.test-util :as ktu]
         '[kimun.cli.main :as main])
(def env {"USER" "tester"})
(defn run [cwd & argv] (:envelope (main/run argv env cwd)))
(def book "{:model/name \"SimpleBook\" :base [] :semantics [\"book\"]\n :fields [{:name \"title\" :type :string :required true :description \"Title.\"}]\n :template \"# ⸢rev•title⸥\\n\"}")
(defn project []
  (let [p (str (ktu/temp-dir "s1probe-proj-"))]
    (run p "stores" "init" "--name" "p")
    (spit (str p "/model.edn") book)
    (run p "models" "define" "-f" (str p "/model.edn"))
    p))
(defn start [p cfg] (h/start! (merge {:store p :cwd p :env env :token nil} cfg)))
(defn call [m url & {:keys [headers body]}]
  (let [t0 (System/nanoTime)
        r (try (http/request (cond-> {:method m :uri url :throw false :headers (or headers {})} body (assoc :body body)))
               (catch Exception e {:status (str "EXC " (.getSimpleName (class e)) ": " (.getMessage e)) :body ""}))]
    (assoc r :ms (quot (- (System/nanoTime) t0) 1000000))))
(defn show [label r] (println (format "  %-52s -> %s (%d ms) %s" label (:status r) (:ms r) (subs (str (:body r)) 0 (min 160 (count (str (:body r))))))))
(defn json-post [url path m & [headers]] (call :post (str url path) :body (json/generate-string m) :headers (merge {"Content-Type" "application/json"} headers)))

