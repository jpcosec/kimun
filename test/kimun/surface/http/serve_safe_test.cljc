(ns kimun.surface.http.serve-safe-test
  "`serve` is safe by default (task serve-seguro; audit vectors in
   runs/subagents/2026-11-15-S1-serve-security/vectors.md): routes confined to
   the project, a token outside loopback, and Host/Origin/Content-Type checks
   without a token. Every refusal is checked against the disk: nothing is
   created or changed, inside or outside the project."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.commands.serve :as serve]
            [kimun.surface.http :as h]
            [kimun.surface.http.guard :as guard]
            [kimun.surface.http.support :as s :refer [req with-server]]
            [kimun.test-util :as ktu])
  (:import (java.net Socket)))

(def ^:private book-edn
  "A minimal model so that `docs create` has something to create."
  (pr-str {:model/name "PlainBook" :base [] :semantics []
           :fields [{:name "title" :type :string :required true :description "Title."}]
           :template "# ⸢rev•title⸥\n"}))

(defn- setup [proj]
  (spit (str (fs/path proj "plain.edn")) book-edn)
  (s/run proj "models" "define" "-f" "plain.edn")
  (spit (str (fs/path proj "loose.md")) "# Loose\n"))

(defn- tree
  "Every file under `dir` (hidden ones and the store included) as path -> bytes."
  [dir]
  (into {} (for [f (file-seq (fs/file dir)) :when (.isFile f)]
             [(str (fs/relativize dir (.getPath f))) (vec (fs/read-all-bytes f))])))

(defn- refused
  "Runs `(post)` against a server over a project and an outside dir holding a
   secret; returns `{:r response :same? bool}` — `same?` is true when neither
   tree changed."
  ([f] (refused (fn [_ _] nil) f))
  ([prep f]
   (let [outside (str (ktu/temp-dir "kimun-outside-"))]
     (spit (str (fs/path outside "secret.md")) "# Secret\n")
     (with-server setup
       (fn [base proj]
         (let [prepared (prep proj outside)
               before [(tree proj) (tree outside)]
               r (f base prepared outside)]
           {:r r :same? (= before [(tree proj) (tree outside)]) :outside outside}))))))

(defn- outside-error? [r]
  (and (= 400 (:status r)) (= "serve/path-outside-project" (get-in r [:json :error :type]))))

(defn- create-body [path] {:model "PlainBook" :name "n" :payload {:title "T"} :path path})

;; ------------------------------------------------------------ 1. confinement

(deftest vectors-2a-2b-create-path-outside-is-refused
  (doseq [mk [(fn [_ o] (str o "/created-abs.md"))          ; 2a absolute
              (fn [_ _] "../created-dots.md")                 ; 2b ..
              (fn [_ _] "sub/../../x.md")
              (fn [_ _] ".kimun/store.edn")                   ; the store is not a place to write
              (fn [_ _] ".git/hooks/pre-commit")]]
    (let [{:keys [r same?]} (refused (fn [base proj o] (req :post (str base "/docs/create") (create-body (mk proj o)))))]
      (is (outside-error? r) (pr-str (:json r)))
      (is same? "nothing created or changed, inside or outside"))))

(deftest vector-2c-and-8-track-outside-or-through-a-symlink-is-refused
  (doseq [mk [(fn [_ o] (str o "/secret.md"))                 ; absolute
              (fn [_ _] "../anything.md")
              (fn [proj o] (fs/create-sym-link (fs/path proj "link.md") (fs/path o "secret.md")) "link.md")        ; 8 file symlink
              (fn [proj o] (fs/create-sym-link (fs/path proj "dir") (fs/path o)) "dir/secret.md")                 ; dir symlink
              (fn [proj o] (fs/create-sym-link (fs/path proj "dangling.md") (fs/path o "nope.md")) "dangling.md")]]
    (let [{:keys [r same?]} (refused mk (fn [base p _]
                                          (req :post (str base "/docs/track") {:model "PlainBook" :path p :name "stolen"})))]
      (is (outside-error? r) (pr-str (:json r)))
      (is (every? #(not (str/includes? (or (:body r) "") %)) ["Secret"]) "the outside text is not echoed")
      (is same?))))

(deftest a-symlink-directory-cannot-be-written-through
  (let [{:keys [r outside]} (refused (fn [proj o] (fs/create-sym-link (fs/path proj "out") (fs/path o)))
                                     (fn [base _ _] (req :post (str base "/docs/create") (create-body "out/new.md"))))]
    (is (outside-error? r))
    (is (not (fs/exists? (fs/path outside "new.md"))) "nothing written through the link")))

(deftest vector-2d-save-names-with-separators-are-refused
  (doseq [id ["../viasave" "a/b" "..\\x" ".."]]
    (let [{:keys [r same?]} (refused (fn [base _ _]
                                       (req :post (str base "/save")
                                            {:changes [{:id id :action "create" :model "PlainBook" :payload {:title "T"}}]})))]
      (is (outside-error? r) id)
      (is same? id)))
  (testing "names are checked on every write route, not only /save"
    (let [{:keys [r same?]} (refused (fn [base _ _] (req :post (str base "/docs/ingest") {:name "../x" :markdown "# x\n"})))]
      (is (outside-error? r)) (is same?))))

(deftest legitimate-paths-inside-the-project-still-work
  (with-server setup
    (fn [base proj]
      (let [r (req :post (str base "/docs/create") (create-body "sub/dir/ok.md"))]
        (is (= 200 (:status r)) (pr-str (:json r)))
        (is (fs/exists? (fs/path proj "sub/dir/ok.md"))))
      (is (= 200 (:status (req :post (str base "/docs/track") {:model "PlainBook" :path "loose.md"}))))
      (is (= 200 (:status (req :post (str base "/save") {:changes [{:id "viasave" :action "create" :model "PlainBook" :payload {:title "T"}}]}))))
      (is (fs/exists? (fs/path proj "viasave.md"))))))

(deftest confine-resolves-against-the-real-root
  (let [root (str (ktu/temp-dir "kimun-root-"))
        out (str (ktu/temp-dir "kimun-out-"))]
    (fs/create-sym-link (fs/path root "l") (fs/path out))
    (is (nil? (guard/confine root "a/b.md")))
    (is (some? (guard/confine root "l/x.md")))
    (is (some? (guard/confine root "/etc/passwd")))
    (is (some? (guard/confine root "a/../../b")))
    (is (some? (guard/confine root ".GIT/config")))
    (is (nil? (guard/confine (str root "/l/../") "ok.md")) "the root itself is whatever the server was started in")))

;; ------------------------------------------------------------ 2. startup

(deftest startup-needs-a-token-outside-loopback-and-for-cors
  (doseq [host ["0.0.0.0" "192.168.1.5" "::" "example.org" "128.0.0.1"]]
    (is (= :cli/usage (:type (ex-data (try (guard/check-config! {:host host}) (catch Exception e e))))) host))
  (doseq [host [nil "127.0.0.1" "127.9.9.9" "localhost" "LOCALHOST" "::1" "[::1]"]]
    (is (= {:host host} (guard/check-config! {:host host})) (str host)))
  (is (= {:host "0.0.0.0" :token "t"} (guard/check-config! {:host "0.0.0.0" :token "t"})) "a token lifts the host rule")
  (is (= :cli/usage (:type (ex-data (try (guard/check-config! {:cors? true}) (catch Exception e e))))) "--cors without a token")
  (is (= {:cors? true :token "t"} (guard/check-config! {:cors? true :token "t"})))
  (is (= :cli/usage (:type (ex-data (try (guard/check-config! {:token ""}) (catch Exception e e))))) "empty token"))

(defn- dispatch-failure
  "The `ex-data` of `serve/dispatch` with `opts`/`env`, or `::started` when it
   did not refuse (it would block serving: it runs in a future, bounded)."
  [opts env]
  (let [f (future (try (serve/dispatch nil [] opts {:env env :cwd "."}) (catch Exception e e)))
        r (deref f 4000 ::started)]
    (if (instance? Throwable r) {:data (ex-data r) :message (ex-message r)} {:data r})))

(deftest the-serve-command-refuses-before-opening-any-socket
  ;; 192.0.2.1 (TEST-NET-1) is no local address: a server that wrongly started would fail to bind
  ;; instead of listening on an interface
  (let [{:keys [data message]} (dispatch-failure {:host "192.0.2.1" :port "0"} {})]
    (is (= :cli/usage (:type data)))
    (is (str/includes? message "--token") "the message says how to fix it"))
  (is (= :cli/usage (:type (:data (dispatch-failure {:token "" :port "0"} {})))))
  (is (= :cli/usage (:type (:data (dispatch-failure {:token "" :port "0"} {"KIMUN_SERVE_TOKEN" "real"})))) "an empty flag does not fall back to the env")
  (is (= :cli/usage (:type (:data (dispatch-failure {:port "0"} {"KIMUN_SERVE_TOKEN" ""})))))
  (is (= :cli/usage (:type (:data (dispatch-failure {:cors true :port "0"} {}))))))

(deftest loopback-names
  (doseq [h ["127.0.0.1" "127.255.255.255" "localhost" "::1" "[::1]"]] (is (guard/loopback-host? h) h))
  (doseq [h ["0.0.0.0" "127.0.0.256" "127.0.0" "10.0.0.1" "localhost.evil.com" "127.0.0.1.evil.com" "" nil]]
    (is (not (guard/loopback-host? h)) (str h))))

(deftest token-comparison-is-exact-and-length-blind
  (let [eq @#'h/constant-time=]
    (is (true? (eq "s3cret" "s3cret")))
    (is (true? (eq "señal-ñ" "señal-ñ")))
    (is (false? (eq "s3cret" "s3creT")))
    (is (false? (eq "s3cret" "s3cre")))
    (is (false? (eq "s3cret" "s3cretx")))
    (is (false? (eq "" "x")))))

;; ------------------------------------------------------------ 3. Host / Origin / Content-Type

(defn- raw
  "One HTTP/1.1 request over a plain socket (the JDK client cannot set `Host`).
   Returns `{:status n :body s}`."
  [port {:keys [method path headers body] :or {method "GET" path "/"}}]
  (with-open [sock (Socket. "127.0.0.1" (int port))]
    (let [out (.getOutputStream sock)
          bs (when body (.getBytes ^String body "UTF-8"))]
      (.write out (.getBytes (str method " " path " HTTP/1.1\r\n"
                                  (str/join (for [[k v] headers] (str k ": " v "\r\n")))
                                  (when bs (str "Content-Length: " (count bs) "\r\n"))
                                  "Connection: close\r\n\r\n") "UTF-8"))
      (when bs (.write out bs))
      (.flush out)
      (let [resp (slurp (.getInputStream sock))
            [head body] (str/split resp #"\r\n\r\n" 2)]
        {:status (parse-long (second (re-find #"^HTTP/1.1 (\d+)" head))) :body body}))))

(defn- port-of [base] (parse-long (re-find #"\d+$" base)))

(deftest vector-7c-a-foreign-host-is-refused-without-a-token
  (with-server setup
    (fn [base proj]
      (let [port (port-of base)
            ingest (fn [host] (raw port {:method "POST" :path "/docs/ingest" :body "{\"name\":\"rb\",\"markdown\":\"# x\\n\"}"
                                         :headers {"Host" host "Content-Type" "application/json"}}))
            before (tree proj)]
        (doseq [host ["evil.example" (str "evil.example:" port) "localhost.evil.com" (str "127.0.0.1:" (inc port)) "127.0.0.1"]]
          (let [r (ingest host)]
            (is (= 403 (:status r)) host)
            (is (str/includes? (:body r) "serve/host-not-allowed") host)))
        (is (= before (tree proj)) "no document written")
        (doseq [host [(str "127.0.0.1:" port) (str "localhost:" port) (str "[::1]:" port)]]
          (is (= 200 (:status (raw port {:path "/health" :headers {"Host" host}}))) host))))))

(defn- http-post [base origin]
  (s/req* :post (str base "/docs/ingest") {"Origin" origin} "{\"name\":\"csrf\",\"markdown\":\"# x\\n\"}"))

(deftest vector-7b-a-foreign-origin-is-refused-for-reads-and-writes
  (with-server setup
    (fn [base proj]
      (let [port (port-of base)
            evil {"Origin" "http://evil.example"}
            before (tree proj)]
        (doseq [o ["http://evil.example" "null" (str "http://127.0.0.1:" (inc port)) "https://127.0.0.1" "http://127.0.0.1.evil.com"]]
          (let [post (http-post base o)
                get (:status (s/req* :get (str base "/stores") {"Origin" o}))]
            (is (= 403 (:status post)) o)
            (is (= "serve/origin-not-allowed" (get-in post [:json :error :type])) o)
            (is (= 403 get) o)))
        (is (= before (tree proj)) "the cross-origin write did not happen")
        (testing "own origin, no origin and /health pass"
          (is (= 200 (:status (s/req* :get (str base "/stores") {"Origin" (str "http://127.0.0.1:" port)}))))
          (is (= 200 (:status (s/req* :get (str base "/stores") {"Origin" (str "http://localhost:" port)}))))
          (is (= 200 (:status (s/req* :get (str base "/stores") {}))))
          (is (= 200 (:status (s/req* :get (str base "/health") evil)))))))))

(deftest vector-7b-a-simple-form-post-is-refused-by-content-type
  (with-server setup
    (fn [base proj]
      (let [before (tree proj)
            r (s/req* :post (str base "/docs/ingest") {"Content-Type" "text/plain"} "{\"name\":\"csrf\",\"markdown\":\"# x\\n\"}")]
        (is (= 415 (:status r)))
        (is (= "serve/unsupported-media-type" (get-in r [:json :error :type])))
        (is (= 415 (:status (s/req* :post (str base "/docs/ingest") {"Content-Type" "application/x-www-form-urlencoded"} "name=a&markdown=b"))))
        (is (= 415 (:status (s/req* :post (str base "/docs/ingest") {} "{\"name\":\"csrf\",\"markdown\":\"# x\\n\"}"))) "no content type")
        (is (= before (tree proj)))
        (is (= 200 (:status (s/req* :post (str base "/docs/ingest") {"Content-Type" "Application/JSON; charset=utf-8"} "{\"name\":\"ok\",\"markdown\":\"# x\\n\"}"))))))))

(deftest with-a-token-and-cors-the-origin-rule-relaxes-only-as-cors-did
  (with-server {:token "tok" :cors? true} setup
    (fn [base _]
      (let [auth {"Authorization" "Bearer tok" "Origin" "http://app.example"}]
        (is (= 200 (:status (s/req* :get (str base "/stores") auth))) "cors + token: a foreign origin with the token passes")
        (is (= 401 (:status (s/req* :get (str base "/stores") (dissoc auth "Authorization")))) "...and without it is still 401"))))
  (with-server {:token "tok"} setup
    (fn [base _]
      (is (= 403 (:status (s/req* :get (str base "/stores") {"Authorization" "Bearer tok" "Origin" "http://app.example"})))
          "token without --cors: still no foreign origin")
      (is (= 200 (:status (s/req* :get (str base "/stores") {"Authorization" "Bearer tok"})))))))
