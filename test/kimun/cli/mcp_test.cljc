(ns kimun.cli.mcp-test
  "`kimun mcp` (docs/v2/06, section `mcp`): the pure handler of `kimun.surface.mcp`
   (methods, errors, the tool table and its argv) and the real process over stdio."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.surface.mcp :as mcp]
            [kimun.test-util :as ktu]))

(defn- ctx-with
  "A ctx whose `run` records its argv in the returned atom and answers `envelope`."
  [envelope]
  (let [calls (atom [])]
    [calls {:run (fn [argv env cwd] (swap! calls conj {:argv argv :env env :cwd cwd}) {:envelope envelope})
            :env {"E" "1"} :cwd "/work" :version "9.9"}]))

(defn- ask [ctx msg] (some-> (mcp/handle-line (json/generate-string msg) ctx) json/parse-string))

(def ok-envelope {:ok true :command "x" :data {:a 1}})

(deftest methods-and-ids
  (let [[_ ctx] (ctx-with ok-envelope)]
    (testing "initialize echoes a known protocol version, else gives the default; ids keep their type"
      (let [r (ask ctx {"jsonrpc" "2.0" "id" 7 "method" "initialize" "params" {"protocolVersion" "2025-03-26"}})]
        (is (= 7 (get r "id")))
        (is (= {"protocolVersion" "2025-03-26" "capabilities" {"tools" {}}
                "serverInfo" {"name" "kimun" "version" "9.9"}}
               (get r "result"))))
      (let [r (ask ctx {"jsonrpc" "2.0" "id" "s" "method" "initialize" "params" {"protocolVersion" "1999-01-01"}})]
        (is (= "s" (get r "id")))
        (is (= "2024-11-05" (get-in r ["result" "protocolVersion"])))))
    (testing "ping"
      (is (= {} (get (ask ctx {"jsonrpc" "2.0" "id" 1 "method" "ping"}) "result"))))
    (testing "a notification is never answered, known or unknown, with or without params"
      (is (nil? (mcp/handle-line "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}" ctx)))
      (is (nil? (mcp/handle-line "{\"jsonrpc\":\"2.0\",\"method\":\"nope\"}" ctx)))
      (is (nil? (mcp/handle-line "{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"zz\"}}" ctx))))
    (testing "errors"
      (is (= -32601 (get-in (ask ctx {"jsonrpc" "2.0" "id" 2 "method" "resources/list"}) ["error" "code"])))
      (let [r (json/parse-string (mcp/handle-line "{not json" ctx))]
        (is (= -32700 (get-in r ["error" "code"])))
        (is (contains? r "id") "id is null, present")
        (is (nil? (get r "id"))))
      (is (= -32602 (get-in (ask ctx {"jsonrpc" "2.0" "id" 3 "method" "tools/call"}) ["error" "code"]))))))

(deftest the-tool-table-is-listed-with-valid-schemas
  (let [[_ ctx] (ctx-with ok-envelope)
        listed (get-in (ask ctx {"jsonrpc" "2.0" "id" 1 "method" "tools/list"}) ["result" "tools"])]
    (is (= ["kimun_declare_read" "kimun_declare_anchors" "kimun_declare_terms" "kimun_declare_about"
            "kimun_declare_members" "kimun_changed" "kimun_seen" "kimun_health"
            "kimun_declare_generate_check" "kimun_declare_generate" "kimun_declare_add"]
           (mapv #(get % "name") listed)))
    (doseq [t listed]
      (is (string? (get t "description")))
      (is (= "object" (get-in t ["inputSchema" "type"])))
      (is (map? (get-in t ["inputSchema" "properties"])))
      (is (every? (set (keys (get-in t ["inputSchema" "properties"]))) (get-in t ["inputSchema" "required"])))
      (is (= (not (#{"kimun_declare_add" "kimun_declare_generate"} (get t "name"))) (get-in t ["annotations" "readOnlyHint"]))
          "only declare_add and declare_generate are not read-only"))))

(deftest each-tool-builds-its-argv
  (let [cases [["kimun_declare_read" {} ["declare" "read"]]
               ["kimun_declare_read" {"files" ["a.clj" "b.clj"]} ["declare" "read" "a.clj" "b.clj"]]
               ["kimun_declare_read" {"declared_only" true "files" ["a.clj"]} ["declare" "read" "a.clj" "--declared-only"]]
               ["kimun_declare_read" {"declared_only" false} ["declare" "read"]]
               ["kimun_declare_anchors" {"since" "abc"} ["declare" "anchors" "--since" "abc"]]
               ["kimun_declare_anchors" {} ["declare" "anchors"]]
               ["kimun_declare_terms" {} ["declare" "terms"]]
               ["kimun_declare_about" {"term" "UI.view" "exact" true} ["declare" "about" "UI.view" "--exact"]]
               ["kimun_declare_about" {"term" "UI" "exact" false "files" ["a.clj"]} ["declare" "about" "UI" "a.clj"]]
               ["kimun_declare_about" {"path" "src/x.clj"} ["declare" "about" "--path" "src/x.clj"]]
               ["kimun_declare_members" {"pattern" "src/**"} ["declare" "members" "src/**"]]
               ["kimun_changed" {"since" "HEAD~1"} ["stores" "changed" "--since" "HEAD~1"]]
               ["kimun_changed" {} ["stores" "changed"]]
               ["kimun_seen" {} ["stores" "seen"]]
               ["kimun_health" {} ["stores" "health"]]
               ["kimun_declare_generate_check" {} ["declare" "generate" "--check"]]
               ["kimun_declare_generate" {} ["declare" "generate"]]
               ["kimun_declare_add" {"to" "k/a.clj" "form" "(isa x Y)"} ["declare" "add" "--to" "k/a.clj" "(isa x Y)"]]
               ["kimun_declare_add" {"to" "k/a.clj" "form" "(isa x Y)" "context" "p.a"}
                ["declare" "add" "--to" "k/a.clj" "(isa x Y)" "--context" "p.a"]]]]
    (doseq [[tool args argv] cases]
      (let [[calls ctx] (ctx-with ok-envelope)
            r (ask ctx {"jsonrpc" "2.0" "id" 1 "method" "tools/call" "params" {"name" tool "arguments" args}})]
        (is (= [{:argv argv :env {"E" "1"} :cwd "/work"}] @calls) (str tool " " args))
        (is (false? (get-in r ["result" "isError"])))
        (is (= ok-envelope (-> r (get-in ["result" "content" 0 "text"]) (json/parse-string true)))
            "the envelope as it is, in a text content")
        (is (= "text" (get-in r ["result" "content" 0 "type"])))))))

(deftest a-failed-command-is-an-error-result-not-a-protocol-error
  (let [failed {:ok false :command "declare read" :error {:type "declare/missing" :message "nf"} :exit 3}
        [_ ctx] (ctx-with failed)
        r (ask ctx {"jsonrpc" "2.0" "id" 1 "method" "tools/call" "params" {"name" "kimun_declare_read" "arguments" {}}})]
    (is (nil? (get r "error")))
    (is (true? (get-in r ["result" "isError"])))
    (is (= "declare/missing" (get-in (json/parse-string (get-in r ["result" "content" 0 "text"])) ["error" "type"])))))

(deftest invalid-calls-never-reach-the-cli
  (let [[calls ctx] (ctx-with ok-envelope)
        code (fn [name args]
               (get-in (ask ctx {"jsonrpc" "2.0" "id" 1 "method" "tools/call" "params" {"name" name "arguments" args}})
                       ["error" "code"]))]
    (is (= -32602 (code "stores_check" {})) "a command outside the table")
    (is (= -32602 (code "stores check" {})) "a CLI command line is not a tool name")
    (is (= -32602 (code "declare read" {})) "nor is declare read")
    (is (= -32602 (code "kimun_health" {"argv" ["docs" "delete"]})) "an argument outside the schema")
    (is (= -32602 (code "kimun_declare_members" {})) "a missing required argument")
    (is (= -32602 (code "kimun_declare_read" {"files" "a.clj"})) "a wrong type")
    (is (= -32602 (code "kimun_declare_about" {})) "neither term nor path")
    (is (= -32602 (code "kimun_declare_about" {"term" "a" "path" "b"})) "both term and path")
    (is (= -32602 (code "kimun_declare_read" [])) "arguments not an object")
    (testing "a value that starts with - is never an option for the CLI"
      (doseq [[name args] [["kimun_declare_about" {"term" "--path"}]
                           ["kimun_declare_read" {"files" ["--since" "x"]}]
                           ["kimun_declare_add" {"to" "--context" "form" "(isa x Y)"}]
                           ["kimun_declare_add" {"to" "a.clj" "form" "-x"}]
                           ["kimun_declare_anchors" {"since" "--full"}]
                           ["kimun_declare_terms" {"files" ["-h"]}]
                           ["kimun_declare_about" {"path" "--exact"}]
                           ["kimun_declare_members" {"pattern" "--help"}]
                           ["kimun_changed" {"since" "-1"}]]]
        (let [r (ask ctx {"jsonrpc" "2.0" "id" 1 "method" "tools/call" "params" {"name" name "arguments" args}})]
          (is (= -32602 (get-in r ["error" "code"])) (str name args))
          (is (str/includes? (get-in r ["error" "message"]) "starts with `-`")))))
    (is (empty? @calls))))

;; ------------------------------------------------------------ the real process

(defn- exchange
  "Runs `bin/kimun mcp` in `dir`, writes `lines` to stdin, closes it; `{:out :err :exit}`."
  [dir lines]
  (let [bin (str (fs/absolutize "bin/kimun"))
        p (process/process {:dir dir :in (str (str/join "\n" lines) "\n") :out :string :err :string
                            :extra-env {"USER" "tester"}} bin "mcp")
        r @p]
    {:out (:out r) :err (:err r) :exit (:exit r)}))

(deftest the-process-speaks-json-rpc-over-stdio
  (let [dir (ktu/temp-dir "kimun-mcp-")
        file (str (fs/path dir "kimun" "a.clj"))
        _ (fs/create-dirs (fs/path dir "kimun"))
        _ (spit file "(ns p.a)\n(relation isa \"d\")\n(isa x Y)\n")
        call (fn [id name args] (json/generate-string {"jsonrpc" "2.0" "id" id "method" "tools/call" "params" {"name" name "arguments" args}}))
        before (slurp file)
        reads (exchange dir [(json/generate-string {"jsonrpc" "2.0" "id" 1 "method" "initialize" "params" {"protocolVersion" "2024-11-05"}})
                             "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"
                             "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"
                             (call 3 "kimun_declare_read" {})])
        lines (str/split-lines (:out reads))
        resp (mapv json/parse-string lines)]
    (is (= 0 (:exit reads)))
    (is (= [1 2 3] (mapv #(get % "id") resp)) "one line per request, none for the notification, nothing else")
    (is (= 11 (count (get-in resp [1 "result" "tools"]))))
    (is (= {"files" 1 "relations" 1 "propositions" 1}
           (-> (get-in resp [2 "result" "content" 0 "text"]) json/parse-string (get-in ["data" "summary"]))))
    (is (= before (slurp file)) "reading changed nothing")
    (let [added (exchange dir [(call 4 "kimun_declare_add" {"to" "kimun/a.clj" "form" "(isa z Y)"})])
          r (json/parse-string (first (str/split-lines (:out added))))]
      (is (= 0 (:exit added)))
      (is (= 1 (count (str/split-lines (:out added)))))
      (is (false? (get-in r ["result" "isError"])))
      (is (str/includes? (slurp file) "(isa z Y)") "add wrote"))))
