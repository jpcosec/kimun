(ns kimun.surface.mcp
  "The MCP surface of kimun (docs/v2/06, section `mcp`): a Model Context Protocol
   server over stdio, as pure data and functions. Like the HTTP surface it is a
   table over the commands and re-implements none: a *tool* is data
   `{:name :description :inputSchema :argv (fn [arguments] argv) :read-only bool}`;
   `tools/call` validates the arguments against the schema, turns them into the
   argv of the CLI and calls the injected `run` (`(fn [argv env cwd] {:envelope e})`,
   `kimun.cli.main/run`: injected by the command, never required here).

   Messages are JSON-RPC 2.0, one per line. `handle-line` maps a line to the line
   to answer, or nil when nothing is answered (a notification never is)."
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [kimun.cli.out :as out]))

(def supported-versions
  "Protocol versions the server answers to as asked; others get `default-version`."
  #{"2024-11-05" "2025-03-26" "2025-06-18"})

(def default-version
  "Protocol version answered when the client asks for one not in `supported-versions`."
  "2024-11-05")

;; ------------------------------------------------------------------ the table

(def ^:private files-prop
  {:type "array" :items {:type "string"}
   :description "Declaration files (relative to the working directory); none: the *.clj under kimun/"})

(defn- obj
  "A JSON Schema object with `props`, `required` names and no extra properties."
  [props & [required]]
  (cond-> {:type "object" :properties props :additionalProperties false}
    (seq required) (assoc :required (vec required))))

(defn- files-of [a] (vec (get a "files")))

(def tools
  "The tools, as data. Only `kimun_declare_add` and `kimun_declare_generate` write."
  [{:name "kimun_declare_read" :read-only true
    :description "Evaluate declaration files as one project and list the propositions they produce."
    :inputSchema (obj {:files files-prop})
    :argv (fn [a] (into ["declare" "read"] (files-of a)))}
   {:name "kimun_declare_anchors" :read-only true
    :description "Resolve the (at ...) anchors of the declarations against the files; with `since` (a commit) git says what changed."
    :inputSchema (obj {:files files-prop :since {:type "string" :description "Commit to compare from"}})
    :argv (fn [a] (cond-> (into ["declare" "anchors"] (files-of a))
                    (get a "since") (into ["--since" (get a "since")])))}
   {:name "kimun_declare_terms" :read-only true
    :description "Every term of the declarations with how many propositions name it and its dotted ancestors."
    :inputSchema (obj {:files files-prop})
    :argv (fn [a] (into ["declare" "terms"] (files-of a)))}
   {:name "kimun_declare_about" :read-only true
    :description "The propositions where `term` (or a more specific one) appears; or, with `path`, what is known of a project file. Give exactly one of term or path."
    :inputSchema (obj {:term {:type "string" :description "A term, e.g. UI.view"}
                       :path {:type "string" :description "A project file, relative to the working directory"}
                       :exact {:type "boolean" :description "With term: only the term itself, not the more specific ones"}
                       :files files-prop})
    :check (fn [a] (when-not (= 1 (count (filter #(contains? a %) ["term" "path"])))
                     "give exactly one of `term` or `path`"))
    :argv (fn [a] (cond-> (if (contains? a "term")
                            (into ["declare" "about" (get a "term")] (files-of a))
                            (into ["declare" "about" "--path" (get a "path")] (files-of a)))
                    (true? (get a "exact")) (conj "--exact")))}
   {:name "kimun_declare_members" :read-only true
    :description "The project files a (files PATTERN) node reaches, expanded now."
    :inputSchema (obj {:pattern {:type "string" :description "A file pattern, relative to the project root"}} ["pattern"])
    :argv (fn [a] ["declare" "members" (get a "pattern")])}
   {:name "kimun_changed" :read-only true
    :description "What git says changed since the pointer (or `since`), and the state of the marks on those files."
    :inputSchema (obj {:since {:type "string" :description "Commit to compare from"}})
    :argv (fn [a] (cond-> ["stores" "changed"] (get a "since") (into ["--since" (get a "since")])))}
   {:name "kimun_seen" :read-only true
    :description "The pointer \"looked up to this commit\" of the store."
    :inputSchema (obj {})
    :argv (fn [_] ["stores" "seen"])}
   {:name "kimun_health" :read-only true
    :description "The verdict of the store: ok, degraded or needs-human."
    :inputSchema (obj {})
    :argv (fn [_] ["stores" "health"])}
   {:name "kimun_declare_generate_check" :read-only true
    :description "Run the recipes of the generated documents ((generated path recipe) in the declarations) and say which files are missing or differ from what their recipe gives today; writes nothing. Fails (exit 3) when any differs."
    :inputSchema (obj {})
    :argv (fn [_] ["declare" "generate" "--check"])}
   {:name "kimun_declare_generate" :read-only false
    :description "WRITES: run the recipes of the generated documents and write each declared file (atomically; never under kimun/, .git/ or .kimun/); says created, updated or unchanged. If any recipe fails nothing is written."
    :inputSchema (obj {})
    :argv (fn [_] ["declare" "generate"])}
   {:name "kimun_declare_add" :read-only false
    :description "WRITES: append one form to a declaration file if the set still evaluates and the form is new; `context` creates the file with (ns CONTEXT)."
    :inputSchema (obj {:to {:type "string" :description "Declaration file (relative to the working directory)"}
                       :form {:type "string" :description "One Clojure form, e.g. (isa x Y)"}
                       :context {:type "string" :description "Context name, to create the file when it does not exist"}}
                      ["to" "form"])
    :argv (fn [a] (cond-> ["declare" "add" "--to" (get a "to") (get a "form")]
                    (get a "context") (into ["--context" (get a "context")])))}])

(defn- tool-named [name] (first (filter #(= name (:name %)) tools)))

(defn- listed
  "The `tools/list` entry of a table entry."
  [{:keys [name description inputSchema read-only]}]
  {:name name :description description :inputSchema inputSchema
   :annotations {:readOnlyHint (boolean read-only)}})

;; ---------------------------------------------------------------- validation

(defn- type-ok? [{:keys [type items]} v]
  (case type
    "string" (string? v)
    "boolean" (boolean? v)
    "array" (and (sequential? v) (every? #(type-ok? items %) v))
    false))

(defn- option-like
  "The first string among `arguments` (loose, or inside an array) that starts with `-`, else nil:
   the CLI would take it as an option the schema does not declare."
  [arguments]
  (->> (vals arguments) (mapcat #(if (sequential? %) % [%]))
       (filter #(and (string? %) (str/starts-with? % "-"))) first))

(defn- invalid-arguments
  "A message when `arguments` do not fit the schema of `tool` (not an object, a name
   outside it, a missing required one, a wrong type, or the tool's own `:check`), else nil."
  [{:keys [inputSchema check]} arguments]
  (let [{:keys [properties required]} inputSchema
        known (set (map name (keys properties)))]
    (cond
      (not (map? arguments)) "`arguments` must be an object"
      :else (or (some #(when-not (known %) (str "unknown argument `" % "`")) (keys arguments))
                (some #(when-not (contains? arguments %) (str "missing argument `" % "`")) required)
                (some (fn [[k v]] (when-not (type-ok? (get properties (keyword k)) v)
                                    (str "argument `" k "` has the wrong type")))
                      arguments)
                (when-let [v (option-like arguments)]
                  (str "value " (pr-str v) " starts with `-` and would be read as an option"))
                (when check (check arguments))))))

;; ----------------------------------------------------------------- JSON-RPC

(defn- result [id r] {"jsonrpc" "2.0" "id" id "result" r})

(defn- rpc-error [id code message] {"jsonrpc" "2.0" "id" id "error" {"code" code "message" message}})

(defn- call-tool
  "The result of `tools/call` for a valid call: the envelope as JSON text, `isError` when it failed."
  [tool arguments {:keys [run env cwd]}]
  (let [envelope (:envelope (run ((:argv tool) arguments) (or env {}) (str cwd)))]
    {"content" [{"type" "text" "text" (out/emit envelope "json")}]
     "isError" (not (:ok envelope))}))

(defn- dispatch-request
  "The response of request `method` with `params` for `id`; `ctx` is `{:run :env :cwd :version}`."
  [id method params ctx]
  (case method
    "initialize"
    (if-not (map? params)
      (rpc-error id -32602 "initialize needs params")
      (let [asked (get params "protocolVersion")]
        (result id {"protocolVersion" (if (contains? supported-versions asked) asked default-version)
                    "capabilities" {"tools" {}}
                    "serverInfo" {"name" "kimun" "version" (str (or (:version ctx) "unknown"))}})))

    "ping" (result id {})

    "tools/list" (result id {"tools" (mapv listed tools)})

    "tools/call"
    (let [name (when (map? params) (get params "name"))
          tool (when (string? name) (tool-named name))
          arguments (if (and (map? params) (contains? params "arguments")) (get params "arguments") {})]
      (cond
        (not (map? params)) (rpc-error id -32602 "tools/call needs params")
        (nil? tool) (rpc-error id -32602 (str "unknown tool: " (pr-str name)))
        :else (if-let [msg (invalid-arguments tool arguments)]
                (rpc-error id -32602 (str name ": " msg))
                (result id (call-tool tool arguments ctx)))))

    (rpc-error id -32601 (str "method not found: " method))))

(defn handle-message
  "The response (a map with string keys) to the parsed JSON-RPC message `msg`, or nil
   when none is due: notifications (no `id`) and responses from the client are never answered."
  [msg ctx]
  (cond
    (not (map? msg)) (rpc-error nil -32600 "invalid request: a single JSON object is expected")
    (not (contains? msg "method"))
    (when (and (contains? msg "id") (not (or (contains? msg "result") (contains? msg "error"))))
      (rpc-error (get msg "id") -32600 "invalid request: no method"))
    (not (string? (get msg "method"))) (when (contains? msg "id") (rpc-error (get msg "id") -32600 "invalid request: method must be a string"))
    (not (contains? msg "id")) nil
    :else (dispatch-request (get msg "id") (get msg "method") (get msg "params") ctx)))

(defn handle-line
  "The line to answer for the input `line` (one JSON-RPC message), or nil when nothing is
   answered. Invalid JSON is the error -32700. Never throws on a message: an exception
   in a tool is a -32603 response (for a request) and nil (for a notification)."
  [line ctx]
  (let [parsed (try {:msg (json/parse-string line)} (catch #?(:clj Exception :cljs :default) _ {:error true}))]
    (when-let [response (if (:error parsed)
                          (rpc-error nil -32700 "parse error")
                          (try (handle-message (:msg parsed) ctx)
                               (catch #?(:clj Throwable :cljs :default) e
                                 (when (and (map? (:msg parsed)) (contains? (:msg parsed) "id"))
                                   (rpc-error (get (:msg parsed) "id") -32603 (str "internal error: " (ex-message e)))))))]
      (json/generate-string response))))

