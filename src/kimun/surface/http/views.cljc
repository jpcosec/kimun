(ns kimun.surface.http.views
  "Shared pieces of the HTTP routes (`kimun.surface.http.routes`): running a
   CLI command for a request, opening the store once per request, returning an
   envelope under a status other than `status-of`'s, and the v1-shaped views
   (`/schema`, `/graph`, `/save`, `/lint`, `/ast`, segments) composed from
   commands and surface functions. Nothing here edits a command."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.docs :as docs]
            [kimun.surface.fields :as fields]
            [kimun.surface.models.segments :as segments]
            [kimun.surface.models.store :as mstore]
            [kimun.surface.semantic :as semantic]
            [kimun.host.default :as host]))

(defn fail
  "A failure envelope `{:ok false :error {:type :message :data} :exit n}`."
  [type message data exit]
  (out/failure type message data exit))

(defn run-command
  "Runs `argv` through the CLI of ctx (`:run`, default `kimun.cli.main/run`)
   with `--store` and `--format json` appended; returns its envelope."
  [ctx argv]
  (let [run (or (:run ctx) #?(:clj (requiring-resolve 'kimun.cli.main/run)))
        argv (cond-> (vec argv) (:store ctx) (into ["--store" (str (:store ctx))]) true (into ["--format" "json"]))]
    (:envelope (run argv (or (:env ctx) {}) (str (or (:cwd ctx) "."))))))

(defn command-route
  "A route handler that runs a CLI command. `tokens` is the fixed command, e.g.
   `[\"journal\" \"show\"]`. Options: `:query` `{param \"--flag\"}` forwards
   those query/path parameters as flags when present; `:positional` `[param …]`
   appends those parameters as positional arguments; `:required` `[param …]`
   rejects a request lacking one with a usage failure (400); `:argv-fn`
   `(fn [request])` returning extra argv, for anything else. The result is the
   command's own envelope."
  [tokens & {:keys [query positional required argv-fn]}]
  (fn [request ctx]
    (let [params (merge (:query-params request) (:path-params request))
          missing (first (remove #(contains? params %) required))]
      (if missing
        (fail :serve/missing-parameter (str "Missing parameter: " (name missing)) {:parameter (name missing)} 6)
        (run-command ctx (-> (vec tokens)
                             (into (keep #(get params %) positional))
                             (into (mapcat (fn [[k flag]] (when (contains? params k) [flag (get params k)])) query))
                             (into (when argv-fn (argv-fn request)))))))))

(defn param
  "Query/path parameter `k` of `request`, or nil."
  [request k]
  (get (merge (:query-params request) (:path-params request)) k))

(defn missing-parameter
  "The usage failure (400) naming parameter `k`."
  [k]
  (fail :serve/missing-parameter (str "Missing parameter: " (name k)) {:parameter (name k)} 6))

(defn bad-parameter
  "The usage failure (400) for parameter `k` with an invalid value."
  [k message]
  (fail :serve/bad-parameter (str "Invalid parameter " (name k) ": " message) {:parameter (name k)} 6))

(defn as-status
  "The Ring response of `envelope` under `status` (the route's override of
   `status-of`), for the cases where v1 answered another code."
  [status envelope]
  {:status status :headers {} :body (json/generate-string envelope)})

(defn- name-or-str [t] (if (keyword? t) (subs (str t) 1) (str t)))

(defn override
  "`envelope`, answered under `(statuses error-type)` when it is a failure
   whose type is in `statuses` (a map of error-type string to status); else
   `envelope` untouched (so `status-of` applies)."
  [statuses envelope]
  (if-let [s (and (not (:ok envelope)) (get statuses (some-> envelope :error :type name-or-str)))]
    (as-status s envelope)
    envelope))

(defn open-store
  "The opened store of `ctx` (`{:store :backend :descriptor :path}`), opened once."
  [ctx]
  (cli-store/open! (cli-store/resolve-store {:store (:store ctx)} (or (:env ctx) {}) (str (or (:cwd ctx) ".")))))

(defn with-temp-file
  "Writes `text` to a temp file called `fname`, calls `(f path)`, deletes it."
  [fname text f]
  (let [dir (fs/create-temp-dir {:prefix "kimun-serve-"})
        file (str (fs/path dir fname))]
    (try (spit file text) (f file) (finally (fs/delete-tree dir)))))

(defn with-payload
  "Calls `(f path)` with a temp JSON file holding `payload`."
  [payload f]
  (with-temp-file "payload.json" (json/generate-string payload) f))

;; ------------------------------------------------------------------- schema / graph

(defn- field-view [{:keys [name type required description default]}]
  {:name name :kind type :required (boolean required) :description description :default default})

(defn schema
  "`GET /schema`: every model with `{:id :model_ref :version :fields :containment
   :references :semantics :template}` — containment and references are what the
   descriptor carries from a v1 class (`:py/containment`, `:py/references`), empty
   when it declares none; a model whose entry cannot be built is
   listed in `unavailable` (`{:model :model_ref :reason}`) instead of failing."
  [_request ctx]
  (let [{:keys [store descriptor] p :path} (open-store ctx)
        views (for [m (mstore/models store)]
                (try (let [d (fields/with-schema-fields store (:descriptor m))]
                       {:ok {:id (:name m) :model (:name m) :model_ref (:name m) :version (:version m)
                             :fields (mapv field-view (:fields d)) :base (:base d)
                             :containment (or (:containment d) (:py/containment d) {}) :references (or (:references d) (:py/references d) [])
                             :semantics (get d :semantics []) :template (:template d) :state (:state d)}})
                     (catch #?(:clj Exception :cljs :default) e
                       {:unavailable {:model (:name m) :model_ref (:name m) :reason (ex-message e)}})))]
    (out/ok "schema" {:store {:name (:name descriptor) :path p} :revision (:head store)
                      :data {:models (vec (keep :ok views)) :unavailable (vec (keep :unavailable views))}})))

(defn graph
  "`GET /graph`: every tracked document as `{:id :model_name :path :version
   :payload :semantic_tags}` (`version` is the content hash); a document whose
   model cannot extract it is listed in `unavailable`."
  [_request ctx]
  (let [{:keys [store descriptor] p :path} (open-store ctx)
        tags (into {} (map (juxt :name :tags)) (semantic/document-tags store))
        views (for [d (docs/documents store)]
                (let [full (docs/document store (:name d))]
                  (if (:payload full)
                    {:ok {:id (:name d) :model_name (:model d) :path (:path d) :version (:content-hash full)
                          :payload (:payload full) :semantic_tags (vec (sort (get tags (:name d) [])))}}
                    {:unavailable {:model (:model d) :model_ref (:model d) :doc (:name d) :reason (:error full)}})))]
    (out/ok "graph" {:store {:name (:name descriptor) :path p} :revision (:head store)
                     :data {:documents (vec (keep :ok views)) :unavailable (vec (keep :unavailable views))}})))

;; ------------------------------------------------------------------------- save

(defn- shown [ctx name] (run-command ctx ["docs" "show" name]))

(defn- stale? [ctx {:keys [id expected]}]
  (when (some? expected)
    (let [e (shown ctx id)]
      (when (:ok e)
        (let [host-hash (:content-hash (:data e))
              want (if (string? expected) expected (docs/content-hash host/host expected))]
          (not= host-hash want))))))

(defn- apply-change [ctx {:keys [id action model payload]}]
  (case action
    "create" (override {"docs/exists" 409}
                       (with-payload payload #(run-command ctx ["docs" "create" (str model) (str id) "--data" %])))
    "update" (let [e (shown ctx id)]
               (if (:ok e)
                 (with-payload payload #(run-command ctx ["docs" "update" (str id) "--data" %]))
                 e))
    "delete" (run-command ctx ["docs" "delete" (str id) "--yes"])))

(def ^:private order {"create" 0 "update" 1 "delete" 2})

(defn save
  "`POST /save`. Batch: `{changes: [{id action model payload expected}]}`
   (create before update before delete; `expected` is the document's current
   `content-hash` or its payload; a stale one is 409 before anything is
   written). Single: `{doc payload}` (an update; unknown doc 404). A failure
   after some changes were applied is 500 `serve/partial-failure` with
   `completed`. Success data: `{:saved [ids] :doc id}`."
  [request ctx]
  (let [{:keys [changes doc payload] :as body} (:json request)]
    (cond
      (and (string? doc) (map? payload))
      (let [e (apply-change ctx {:id doc :action "update" :payload payload})]
        (if (:ok e) (assoc e :data (assoc (:data e) :saved [doc] :doc doc)) e))

      (not (and (sequential? changes) (every? #(and (map? %) (string? (:id %)) (contains? order (:action %))) changes)))
      (fail :serve/bad-body "Expected {changes: [{id, action: create|update|delete, ...}]} or {doc, payload}." {} 6)

      :else
      (let [changes (sort-by (comp order :action) changes)
            stale (first (filter #(and (#{"update" "delete"} (:action %)) (stale? ctx %)) changes))]
        (if stale
          (fail :serve/stale (str (:id stale) " changed in kimun; reload to avoid overwriting it.") {:id (:id stale)} 4)
          (loop [[c & more] changes done [] last-ok nil]
            (if-not c
              (assoc (or last-ok (out/ok "save" {:data {}})) :command "save" :data {:saved done})
              (let [e (apply-change ctx c)]
                (cond (:ok e) (recur more (conj done (:id c)) e)
                      (seq done) (fail :serve/partial-failure (get-in e [:error :message])
                                       {:completed done :failed (:id c) :cause (get-in e [:error :type])} 5)
                      :else e)))))))))

;; --------------------------------------------------------- lint / ast / segments

(defn lint
  "`GET /lint` (retired in v2; docs/v2/06 §5): the `stores check` verdict in
   v1's problem shape, `{:count :problems [{:kind :severity :doc :detail}]}`."
  [_request ctx]
  (let [e (run-command ctx ["stores" "check"])
        problems (if (:ok e)
                   []
                   [{:kind (name-or-str (get-in e [:error :type])) :severity "error" :doc nil
                     :detail (get-in e [:error :message])}])]
    (out/ok "lint" {:store (:store e) :revision (:revision e) :data {:count (count problems) :problems problems}})))

(defn ast
  "`GET /ast?doc=`: `{:name :model :payload :sections}` (`docs show` + `sections list`)."
  [request ctx]
  (if-let [d (param request :doc)]
    (let [s (shown ctx d)]
      (if-not (:ok s)
        s
        (let [sec (run-command ctx ["sections" "list" d])]
          (if-not (:ok sec)
            sec
            (assoc s :command "ast"
                   :data {:name (get-in s [:data :name]) :model (get-in s [:data :model])
                          :payload (get-in s [:data :payload]) :sections (:data sec)})))))
    (missing-parameter :doc)))

(defn- segment-failure [e]
  (let [t (some-> (ex-data e) :type name-or-str)]
    (fail (or (some-> (ex-data e) :type) :serve/segments) (ex-message e) (dissoc (ex-data e) :type)
          (cond (nil? t) 70 :else 3))))

(defn- refuse-spec16-segments
  "The `fail` for a spec 16 class: segments map fields onto the canonical render
   of the v1 template, which a class does not have (M2 brings the renderer)."
  [model]
  (fail :cli/usage (str "segments are built over the render of the v1 template, and `" (:model/name model)
                        "` is a spec 16 class (no template, no renderer yet: M2)")
        {:model (:model/name model)} 6))

(defn document-segments
  "`GET /document/segments?id=`: `segments/document-segments` over the
   document's file text and model."
  [request ctx]
  (if-let [id (param request :id)]
    (let [{:keys [store descriptor] p :path} (open-store ctx)
          d (docs/document store id)
          model (:descriptor (mstore/model store (:model d)))
          text (or (:source d) "")]
      (if (and model (mstore/spec16? model))
        (refuse-spec16-segments model)
        (try (out/ok "document segments"
                     {:store {:name (:name descriptor) :path p} :revision (:head store)
                      :data (segments/document-segments model text {:id id :doc (:name d) :path (:path d)})})
             (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e (segment-failure e)))))
    (missing-parameter :id)))

(defn extract-segments
  "`POST /extract/segments {model, markdown}`: `segments/extract-segments`."
  [request ctx]
  (let [{:keys [model markdown]} (:json request)]
    (if-not (and (string? model) (seq model) (string? markdown))
      (fail :serve/bad-body "Expected {model: str, markdown: str}." {} 6)
      (let [{:keys [store descriptor] p :path} (open-store ctx)
            m (mstore/model store model)]
        (if (mstore/spec16? (:descriptor m))
          (refuse-spec16-segments (:descriptor m))
          (try (out/ok "extract segments"
                       {:store {:name (:name descriptor) :path p} :revision (:head store)
                        :data (segments/extract-segments (:descriptor m) markdown)})
               (catch #?(:clj clojure.lang.ExceptionInfo :cljs :default) e (segment-failure e))))))))
