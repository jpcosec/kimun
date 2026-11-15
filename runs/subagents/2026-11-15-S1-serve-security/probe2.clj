;; S1 probes, second batch (follow-ups). Run like probe.clj.
(load-file "/tmp/kimun-s1sec-wt/runs/subagents/2026-11-15-S1-serve-security/probe-head.clj")
(require '[babashka.process :as proc])
(println "== V2b/V8b read-back of an outside file through track (content that fits the model)")
(let [p (project) outside (str (ktu/temp-dir "s1probe-outside3-")) s (start p {}) url (str "http://127.0.0.1:" (:port s))]
  (try
    (spit (str outside "/secret.md") "# TopSecretOutsideTitle\n")
    (show "track abs outside file" (json-post url "/docs/track" {:model "SimpleBook" :path (str outside "/secret.md") :name "stolen"}))
    (let [r (call :get (str url "/render?doc=stolen"))]
      (println "  /render body contains outside text?" (str/includes? (:body r) "TopSecretOutsideTitle")))
    (let [r (call :get (str url "/document?id=stolen"))]
      (println "  /document body contains outside text?" (str/includes? (:body r) "TopSecretOutsideTitle")))
    ;; symlink inside project -> outside
    (fs/create-sym-link (str p "/link.md") (str outside "/secret.md"))
    (show "track path=link.md (symlink to outside)" (json-post url "/docs/track" {:model "SimpleBook" :path "link.md" :name "viasym"}))
    (let [r (call :get (str url "/render?doc=viasym"))]
      (println "  /render viasym contains outside text?" (str/includes? (:body r) "TopSecretOutsideTitle")))
    (fs/create-sym-link (str p "/link2.md") (str outside "/secret.md"))
    (let [r (run p "docs" "ingest" "link2.md" "--name" "symingest")]
      (println "  CLI docs ingest link2.md (symlink) ok?" (:ok r) (get-in r [:error :type])))
    (let [r (run p "docs" "render" "symingest")]
      (println "  CLI docs render symingest contains outside text?" (str/includes? (pr-str r) "TopSecretOutsideTitle")))
    (finally (h/stop! s))))
(println "== V7b CSRF-style write: did the text/plain cross-origin POST land?")
(let [p (project) s (start p {}) url (str "http://127.0.0.1:" (:port s))]
  (try
    (show "POST text/plain Origin evil.example" (call :post (str url "/docs/ingest") :headers {"Origin" "http://evil.example" "Content-Type" "text/plain"} :body (json/generate-string {:name "csrf" :markdown "# CSRF\n"})))
    (println "  docs list has csrf?" (str/includes? (pr-str (run p "docs" "list")) "csrf"))
    (finally (h/stop! s))))
(println "== V1c ingest cost vs size (60 s client timeout), and write lock held")
(let [p (project) s (start p {}) url (str "http://127.0.0.1:" (:port s))]
  (try
    (doseq [kb [50 100 200 400]]
      (show (str "single-line " kb " KB") (json-post url "/docs/ingest" {:name (str "g" kb) :markdown (str "# H\n\n" (str/join (repeat (* kb 1024) \a)) "\n")})))
    (finally (h/stop! s))))
(println "== V3b/V4b CLI banners (child process killed after the banner line)")
(defn banner [envm & args]
  (let [p (project) w "/tmp/kimun-s1sec-wt"
        pr (proc/process (into ["bb" "-cp" (str w "/src:" w "/resources") "-m" "kimun.cli.main"] args) {:dir p :extra-env envm :err :inherit})
        line (try (.readLine (java.io.BufferedReader. (java.io.InputStreamReader. (:out pr)))) (finally (proc/destroy-tree pr)))]
    (println " " (pr-str envm) (pr-str (vec args)) "->" line)))
(banner {} "serve" "--port" "0" "--host" "0.0.0.0")
(banner {} "serve" "--port" "0" "--token" "")
(banner {"KIMUN_SERVE_TOKEN" "realtok"} "serve" "--port" "0")
(banner {"KIMUN_SERVE_TOKEN" "realtok"} "serve" "--port" "0" "--token" "")
(banner {} "serve" "--port" "0" "--host" "not a host")
