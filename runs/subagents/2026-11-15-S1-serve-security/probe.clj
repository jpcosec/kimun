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
        r (try (http/request (cond-> {:method m :uri url :throw false :timeout 60000 :headers (or headers {})} body (assoc :body body)))
               (catch Exception e {:status (str "EXC " (.getSimpleName (class e)) ": " (.getMessage e)) :body ""}))]
    (assoc r :ms (quot (- (System/nanoTime) t0) 1000000))))
(defn show [label r] (println (format "  %-52s -> %s (%d ms) %s" label (:status r) (:ms r) (subs (str (:body r)) 0 (min 160 (count (str (:body r))))))))
(defn json-post [url path m & [headers]] (call :post (str url path) :body (json/generate-string m) :headers (merge {"Content-Type" "application/json"} headers)))

(println "== V1 huge body (no token), /docs/ingest")
(let [p (project) s (start p {}) url (str "http://127.0.0.1:" (:port s))]
  (try
    (doseq [kb [100 1000 7000 9000]]
      (let [md (str "# H\n\n" (str/join (repeat (* kb 1024) \a)) "\n")]
        (show (str "ingest markdown ~" kb " KB") (json-post url "/docs/ingest" {:name (str "big" kb) :markdown md}))))
    (finally (h/stop! s))))
(println "== V1b huge body WITHOUT auth against a token server (is it read before the 401?)")
(let [p (project) s (start p {:token "tok"}) url (str "http://127.0.0.1:" (:port s))]
  (try
    (let [md (str/join (repeat (* 7 1024 1024) \a))]
      (show "7 MB no auth" (json-post url "/docs/ingest" {:name "x" :markdown md}))
      (show "9 MB no auth" (json-post url "/docs/ingest" {:name "x" :markdown (str md "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" (str/join (repeat (* 2 1024 1024) \a)))})))
    (finally (h/stop! s))))

(println "== V2 hostile path in write routes (outside dir = fresh temp dir)")
(let [p (project) outside (str (ktu/temp-dir "s1probe-outside-")) s (start p {}) url (str "http://127.0.0.1:" (:port s))
      payload {:title "Pwn"}]
  (println "  outside:" outside "project:" p)
  (try
    (spit (str outside "/secret.md") "# TopSecretOutsideTitle\n")
    (show "create path=<outside>/created-abs.md" (json-post url "/docs/create" {:model "SimpleBook" :name "abs" :payload payload :path (str outside "/created-abs.md")}))
    (println "    exists outside?" (fs/exists? (str outside "/created-abs.md")))
    (let [rel (str (fs/relativize p (str outside "/created-dots.md")))]
      (show (str "create path=" rel) (json-post url "/docs/create" {:model "SimpleBook" :name "dots" :payload payload :path rel}))
      (println "    exists outside?" (fs/exists? (str outside "/created-dots.md"))))
    (show "track path=<outside>/secret.md (abs)" (json-post url "/docs/track" {:model "SimpleBook" :path (str outside "/secret.md") :name "stolen"}))
    (show "GET /render?doc=stolen (read-back of outside file)" (call :get (str url "/render?doc=stolen")))
    (show "GET /document?id=stolen" (call :get (str url "/document?id=stolen")))
    (let [rel (str (fs/relativize p (str outside "/secret.md")))]
      (show (str "track path=" rel) (json-post url "/docs/track" {:model "SimpleBook" :path rel :name "stolen2"})))
    (show "save create id=../../x (name traversal)" (json-post url "/save" {:changes [{:id (str "../" (fs/file-name outside) "/viasave") :action "create" :model "SimpleBook" :payload payload}]}))
    (println "    files in outside:" (sort (map fs/file-name (fs/list-dir outside))))
    (println "    files in project:" (sort (map fs/file-name (fs/list-dir p))))
    (finally (h/stop! s))))

(println "== V8 symlink leaving the project: CLI docs ingest / docs track and HTTP track")
(let [p (project) outside (str (ktu/temp-dir "s1probe-outside2-"))]
  (spit (str outside "/secret.md") "# SymlinkSecret\n\nbody-from-outside\n")
  (fs/create-sym-link (str p "/link.md") (str outside "/secret.md"))
  (let [r (run p "docs" "ingest" "link.md" "--name" "viaingest")]
    (println "  CLI docs ingest link.md ->" (:ok r) (get-in r [:error :type]) (get-in r [:data :path]) (some-> r :data keys)))
  (let [r (run p "docs" "show" "viaingest")] (println "  docs show viaingest content-contains-outside-text?" (str/includes? (pr-str r) "body-from-outside")))
  (let [s (start p {}) url (str "http://127.0.0.1:" (:port s))]
    (try (show "HTTP track path=link.md (symlink)" (json-post url "/docs/track" {:model "SimpleBook" :path "link.md" :name "viatrack"}))
         (show "GET /render?doc=viatrack" (call :get (str url "/render?doc=viatrack")))
         (finally (h/stop! s)))))

(println "  getLocalHost =" (.getHostAddress (java.net.InetAddress/getLocalHost)))
(println "== V3 --host 0.0.0.0 without token (in-process)")
(let [p (project) s (start p {:host "0.0.0.0"}) ip (.getHostAddress (java.net.InetAddress/getLocalHost))]
  (try
    (println "  server host:" (:host s) " local address:" ip)
    (show (str "GET http://" ip ":port/health, no token") (call :get (str "http://" ip ":" (:port s) "/health")))
    (show "POST ingest via non-loopback address, no token" (json-post (str "http://" ip ":" (:port s)) "/docs/ingest" {:name "remote" :markdown "# R\n"}))
    (finally (h/stop! s))))

(println "== V4 empty token")
(println "  resolve-token \"\" {}                      =" (pr-str (h/resolve-token "" {})))
(println "  resolve-token \"\" {KIMUN_SERVE_TOKEN tok}  =" (pr-str (h/resolve-token "" {"KIMUN_SERVE_TOKEN" "tok"})) "(flag \"\" beats env)")
(println "  resolve-token nil {KIMUN_SERVE_TOKEN \"\"}  =" (pr-str (h/resolve-token nil {"KIMUN_SERVE_TOKEN" ""})))
(println "  resolve-token \"  \" {}                    =" (pr-str (h/resolve-token "  " {})) "(whitespace-only is a token)")

(println "== V5 malformed Authorization (token = tok)")
(let [p (project) s (start p {:token "tok"}) url (str "http://127.0.0.1:" (:port s))]
  (try
    (doseq [v ["Bearer tok" "bearer tok" "BEARER tok" "Bearer  tok" "Bearer tok " "Bearer" "Bearer " "Basic tok" "tok" "Bearer tok,Bearer x" "Bearer tök" (str "Bearer " (apply str (repeat 70000 "a")))]]
      (show (str "Authorization: " (if (> (count v) 40) (str "Bearer <70000 x a>") (pr-str v))) (call :get (str url "/health") :headers {"Authorization" v})))
    (finally (h/stop! s))))

(println "== V7 CORS off: cross-origin 'simple' requests and Host header")
(let [p (project) s (start p {}) url (str "http://127.0.0.1:" (:port s))]
  (try
    (show "OPTIONS preflight, no --cors" (call :options (str url "/docs/ingest") :headers {"Origin" "http://evil.example"}))
    (show "POST text/plain from Origin evil.example (no preflight)"
          (call :post (str url "/docs/ingest") :headers {"Origin" "http://evil.example" "Content-Type" "text/plain"} :body (json/generate-string {:name "csrf" :markdown "# CSRF\n"})))
    (println "    document written?" (some? (:ok (run p "docs" "show" "csrf"))) (:ok (run p "docs" "show" "csrf")))
    (let [sock (java.net.Socket. "127.0.0.1" (int (:port s)))
          _ (.write (.getOutputStream sock) (.getBytes "GET /health HTTP/1.1\r\nHost: evil.example\r\nConnection: close\r\n\r\n"))
          resp (slurp (.getInputStream sock))]
      (println "  raw GET /health with Host: evil.example (DNS rebinding) ->" (first (str/split-lines resp))))
    (finally (h/stop! s))))
