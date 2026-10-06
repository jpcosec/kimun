(ns kimun.surface.http.routes
  "The routing table of the HTTP surface as data (see `kimun.surface.http`):
   one entry per route, each a thin handler over a CLI command
   (`views/command-route`) or over a composition in `kimun.surface.http.views`.
   Status overrides (where v1 answered another code than the command's exit
   maps to) are declared per route with `views/override`."
  (:require [clojure.string :as str]
            [kimun.cli.out :as out]
            [kimun.surface.http.views :as v :refer [command-route run-command fail param]]))

(defn- truthy? [s] (contains? #{"1" "true" "yes" "on"} (str/lower-case (str s))))

(defn- flag-argv
  "Argv adding each `flag` whose query parameter `k` is truthy."
  [& {:as flags}]
  (fn [request] (vec (for [[k flag] flags :when (truthy? (param request k))] flag))))

(defn- also
  "A handler that concatenates the argv of `argv-fns` onto command-route's."
  [& fs]
  (fn [request] (vec (mapcat #(% request) fs))))

(defn- overriding
  "`handler` with `statuses` (error-type string to HTTP status) applied by
   `views/override` to its result: the per-route adapter for the cases where
   v1 answered a status the command's exit code does not give."
  [statuses handler]
  (fn [request ctx] (v/override statuses (handler request ctx))))

(defn- not-indexed
  "v1's `{kind: not_indexed}` marker on the corpus 'no index' failure."
  [handler]
  (fn [request ctx]
    (let [e (handler request ctx)]
      (cond-> e (= "index/not-indexed" (some-> e :error :type str (str/replace #"^:" ""))) (assoc-in [:error :data :kind] "not_indexed")))))

(defn- ingest
  "`POST /docs/ingest` `{name, markdown}`: writes the markdown to a temporary
   file and runs `docs ingest FILE --name NAME`."
  [request ctx]
  (let [{:keys [name markdown]} (:json request)]
    (if-not (and (string? name) (seq name) (string? markdown))
      (fail :serve/bad-body "Expected {name: str, markdown: str}." {} 6)
      (v/with-temp-file "document.md" markdown #(run-command ctx ["docs" "ingest" % "--name" name])))))

(defn- create
  "`POST /docs` and `/docs/create` `{model, name, payload, path?}`: `docs
   create`. v1: missing name/model 400, unknown model 404, existing document
   409 (the command exits 3), invalid payload 422, existing file 409."
  [request ctx]
  (let [{:keys [model name payload path]} (:json request)]
    (cond (not (and (string? model) (seq model))) (v/missing-parameter :model)
          (not (and (string? name) (seq name))) (v/missing-parameter :name)
          (not (map? payload)) (v/missing-parameter :payload)
          :else (v/override {"docs/exists" 409}
                            (v/with-payload payload
                              #(run-command ctx (cond-> ["docs" "create" model name "--data" %] path (into ["--path" path]))))))))

(defn- track
  "`POST /docs/track` `{model, path, name?}`: `docs track MODEL FILE`."
  [request ctx]
  (let [{:keys [model path name]} (:json request)]
    (cond (not (and (string? model) (seq model))) (v/missing-parameter :model)
          (not (and (string? path) (seq path))) (v/missing-parameter :path)
          :else (run-command ctx (cond-> ["docs" "track" model path] name (into ["--name" name]))))))

(defn- untrack
  "`POST /docs/untrack` `{doc}`: `docs untrack NAME` (the file stays)."
  [request ctx]
  (let [{doc :doc} (:json request)]
    (if (and (string? doc) (seq doc)) (run-command ctx ["docs" "untrack" doc]) (v/missing-parameter :doc))))

(defn- document
  "`GET /document?id=`: the document map (`docs show`) with its AST (`docs show
   --ast`) as `:ir`; `/document/ir` answers only the `:ir`."
  [only-ir?]
  (fn [request ctx]
    (if-let [id (param request :id)]
      (let [s (run-command ctx ["docs" "show" id])
            a (when (:ok s) (run-command ctx ["docs" "show" id "--ast"]))]
        (cond (not (:ok s)) s
              (not (:ok a)) a
              :else (assoc s :data (if only-ir?
                                     {:id id :ir (get-in a [:data :ast])}
                                     (assoc (:data s) :id id :ir (get-in a [:data :ast]))))))
      (v/missing-parameter :id))))

(defn- fields
  "`GET /fields?doc=[&path=]`: `fields get DOC PATH` for a path, else the
   document's `{:doc :model :fields payload}`."
  [request ctx]
  (cond (not (param request :doc)) (v/missing-parameter :doc)
        (param request :path) (run-command ctx ["fields" "get" (param request :doc) (param request :path)])
        :else (let [s (run-command ctx ["docs" "show" (param request :doc)])]
                (if (:ok s)
                  (assoc s :data {:doc (get-in s [:data :name]) :model (get-in s [:data :model]) :fields (get-in s [:data :payload])})
                  s))))

(defn- extract
  "`GET /extract?doc=`: `{:doc :payload}`."
  [request ctx]
  (if-let [d (param request :doc)]
    (let [s (run-command ctx ["docs" "show" d])]
      (if (:ok s) (assoc s :data {:doc (get-in s [:data :name]) :payload (get-in s [:data :payload])}) s))
    (v/missing-parameter :doc)))

(defn- render
  "`GET /render?doc=` → `{:markdown}` (`docs render`); `?model=` → `{:template}`."
  [request ctx]
  (cond (param request :doc)
        (let [r (run-command ctx ["docs" "render" (param request :doc)])]
          (if (:ok r) (assoc r :data {:doc (param request :doc) :markdown (get-in r [:data :markdown] (:data r))}) r))
        (param request :model)
        (let [m (run-command ctx ["models" "show" (param request :model)])]
          (if (:ok m) (assoc m :data {:model (param request :model) :template (get-in m [:data :descriptor :template])}) m))
        :else (v/missing-parameter :doc)))

(defn- models-edit
  "A model-editing route over `tokens` with body `{model, <body-keys>}`: the
   body keys become flags (`--field` …); `model` is required."
  [tokens flags]
  (fn [request ctx]
    (let [b (:json request)]
      (if-not (and (string? (:model b)) (seq (:model b)))
        (v/missing-parameter :model)
        (let [missing (first (remove #(some? (get b (key %))) (filter (comp :required val) flags)))]
          (if missing
            (v/missing-parameter (key missing))
            (run-command ctx (into (conj (vec tokens) (:model b))
                                   (mapcat (fn [[k {:keys [flag bool?]}]]
                                             (when (some? (get b k)) (if bool? (when (get b k) [flag]) [flag (str (get b k))]))))
                                   flags))))))))

(defn- models-define
  "`POST /models/define` `{edn}`: `models define -f FILE` (the v2 promotion of a model draft)."
  [request ctx]
  (let [edn (:edn (:json request))]
    (if (string? edn) (v/with-temp-file "model.edn" edn #(run-command ctx ["models" "define" "-f" %])) (v/missing-parameter :edn))))

(defn- template-edit
  "`POST /models/template-edit` `{model, content}`: `models template NAME -f FILE`."
  [request ctx]
  (let [{:keys [model content]} (:json request)]
    (cond (not (and (string? model) (seq model))) (v/missing-parameter :model)
          (not (string? content)) (v/missing-parameter :content)
          :else (v/with-temp-file "template.md" content #(run-command ctx ["models" "template" model "-f" %])))))

(defn- health
  "`/health`: the store's verdict (`stores check`) as `{:status :findings :action}`;
   200 even when degraded, as in v1."
  [_request ctx]
  (let [e (run-command ctx ["stores" "check"])]
    (out/ok "health"
            {:store (:store e) :revision (:revision e)
             :data (if (:ok e)
                     {:status "ok" :findings [] :action nil}
                     {:status "degraded"
                      :findings [{:kind (get-in e [:error :type]) :message (get-in e [:error :message])}]
                      :action "run: kimun stores verify"})})))

(def routes
  "The routing table: a vector of `{:method :path :write? :handler}`."
  [{:method :get :path "/health" :handler health}
   {:method :get :path "/stores" :handler (command-route ["stores" "show"])}
   {:method :get :path "/stores/check" :handler (command-route ["stores" "check"])}
   {:method :get :path "/stores/links" :handler (command-route ["stores" "links"])}
   {:method :get :path "/schema" :handler v/schema}
   {:method :get :path "/graph" :handler v/graph}
   {:method :get :path "/lint" :handler v/lint}
   {:method :get :path "/ast" :handler v/ast}
   {:method :post :path "/save" :write? true :handler v/save}
   ;; documents
   {:method :get :path "/docs" :handler (command-route ["docs" "list"] :query {:model "--model"})}
   {:method :get :path "/docs/render" :handler (command-route ["docs" "render"] :positional [:name] :required [:name])}
   {:method :post :path "/docs/ingest" :write? true :handler ingest}
   {:method :post :path "/docs" :write? true :handler create}
   {:method :post :path "/docs/create" :write? true :handler create}
   {:method :post :path "/docs/track" :write? true :handler track}
   {:method :post :path "/docs/untrack" :write? true :handler untrack}
   {:method :get :path "/document" :handler (document false)}
   {:method :get :path "/document/ir" :handler (document true)}
   {:method :get :path "/document/segments" :handler v/document-segments}
   {:method :post :path "/extract/segments" :handler v/extract-segments}
   {:method :get :path "/fields" :handler fields}
   {:method :get :path "/extract" :handler extract}
   {:method :get :path "/render" :handler render}
   {:method :get :path "/sections" :handler (command-route ["sections" "list"] :positional [:doc] :required [:doc])}
   ;; find
   {:method :get :path "/find"
    :handler (command-route ["find"] :positional [:q] :required [:q]
                            :query {:in "--in" :type "--type" :where "--where" :select "--select" :limit "--limit"}
                            :argv-fn (flag-argv :fuzzy "--fuzzy" :regex "--regex" :global "--global"))}
   ;; edges
   {:method :get :path "/edges"
    :handler (command-route ["edges" "show"] :positional [:id]
                            :query {:from "--from" :to "--to" :relation "--relation"})}
   {:method :get :path "/edges/node" :handler (command-route ["edges" "node"] :positional [:id] :required [:id])}
   {:method :get :path "/edges/nodes" :handler (command-route ["edges" "nodes"] :query {:type "--type"})}
   ;; graph analysis
   {:method :get :path "/graph/cycles" :handler (command-route ["graph" "cycles"] :query {:limit "--limit" :relation "--relation"})}
   {:method :get :path "/graph/components" :handler (command-route ["graph" "components"] :query {:relation "--relation"})}
   {:method :get :path "/graph/central" :handler (command-route ["graph" "central"] :query {:limit "--limit" :metric "--metric" :type "--type"})}
   {:method :get :path "/graph/isolated" :handler (command-route ["graph" "isolated"] :query {:relation "--relation"})}
   {:method :get :path "/graph/neighborhood"
    :handler (command-route ["graph" "neighborhood"] :positional [:node] :required [:node]
                            :query {:depth "--depth" :direction "--direction" :relation "--relation"})}
   {:method :get :path "/graph/path"
    :handler (command-route ["graph" "path"] :positional [:source :target] :required [:source :target]
                            :query {:relation "--relation"}
                            :argv-fn (flag-argv :directed "--directed"))}
   ;; corpus
   {:method :get :path "/corpus/rank"
    :handler (->> (command-route ["corpus" "rank"] :positional [:q] :required [:q]
                            :query {:k "--k" :threshold "--threshold" :among "--among"}
                            :argv-fn (flag-argv :fields "--fields" :refresh "--refresh"))
                  not-indexed (overriding {"index/not-indexed" 409}))}
   {:method :get :path "/similar"
    :handler (command-route ["graph" "similar"] :positional [:id] :required [:id] :query {:limit "--limit" :relations "--relation" :types "--type"})}
   ;; models
   {:method :get :path "/models" :handler (command-route ["models" "list"])}
   {:method :get :path "/models/detail" :handler (command-route ["models" "show"] :positional [:model] :required [:model])}
   {:method :post :path "/models/fields-add" :write? true
    :handler (models-edit ["models" "field-add"] {:field_name {:flag "--field" :required true} :field_type {:flag "--type" :required true}
                                                  :default {:flag "--default"} :required {:flag "--required" :bool? true}})}
   {:method :post :path "/models/fields-remove" :write? true
    :handler (models-edit ["models" "field-remove"] {:field_name {:flag "--field" :required true}})}
   {:method :post :path "/models/validate"
    :handler (models-edit ["models" "check"] {})}
   {:method :post :path "/models/define" :write? true :handler models-define}
   {:method :post :path "/models/template-edit" :write? true :handler template-edit}
   ;; journal
   {:method :get :path "/journal" :handler (command-route ["journal" "show"] :query {:limit "--limit" :by "--by" :tree "--tree"})}
   {:method :get :path "/journal/verify" :handler (command-route ["journal" "verify"])}])
