(ns kimun.cli.commands.predicates
  "The `kimun predicates` group (v1 `sldb predicates`): `add`, `list`,
   `show`, `validate` and `remove` over the predicate definitions document
   links use (`[name:: [[target]]]`). v1 kept them in the store index YAML;
   here they are nodes of the `relations` tree, read by
   `kimun.surface.edges/predicates` (v1's defaults while none was written) and
   written as `TransactionPlan`s through `kimun.kernel.store/commit!`. Every
   command is `(fn [args opts ctx] envelope)`."
  (:require [clojure.string :as str]
            [kimun.cli.commands.docs :as cdocs]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.edges :as edges]
            [kimun.host.default :as host]
            [kimun.kernel.err :as err]
            [kimun.kernel.ports :as ports]
            [kimun.kernel.store :as store]))

(defn- open [opts {:keys [env cwd]}] (cli-store/open! (cli-store/resolve-store opts env cwd)))
(defn- store-ref [{:keys [descriptor path]}] {:name (:name descriptor) :path path})

(defn- usage! [sub message data]
  (err/raise :cli/usage message (merge {:group "predicates" :subcommand sub} data)))

(defn- require-arg! [args i what sub]
  (or (get args i) (usage! sub (str "`predicates " sub "` needs " what) {})))

(defn- text-opt
  "Option `k` as a string or nil; a flag without a value is `cli/usage`."
  [opts k sub]
  (let [v (get opts k)]
    (if (or (nil? v) (string? v)) v (usage! sub (str "option --" (name k) " needs a value") {:option k}))))

(defn- commit
  "Commits the plan `build` returns for `arg`: `{:opened :store :revision :changed}`."
  [build arg opts ctx]
  (let [{:keys [backend] s :store :as o} (open opts ctx)
        {:keys [changed plan]} (build host/host s arg {:actor (:actor opts) :timestamp (cdocs/timestamp)
                                                       :tree-id (ports/ulid (:ids host/host))})
        r (when changed (store/commit! backend s plan))]
    {:opened o :store (if r (:store r) s) :revision (if r (:revision-id r) (:head s)) :changed (boolean changed)}))

(defn add
  "`predicates add NAME --axis A [--description D]`: registers a predicate;
   the axis is upper-cased as in v1. Data: `{:changed :predicate {:name :axis
   :description}}`. A duplicate is `edges/conflict`, a bad name or axis
   `edges/invalid`, a missing `--axis` `cli/usage`."
  [args opts ctx]
  (let [n (require-arg! args 0 "a predicate NAME" "add")
        axis (or (text-opt opts :axis "add") (usage! "add" "`predicates add` needs --axis" {:option :axis}))
        p {:name n :axis (str/upper-case axis) :description (or (text-opt opts :description "add") "")}
        {:keys [opened changed revision] s :store} (commit edges/add-predicate-plan p opts ctx)]
    (out/ok "predicates add" {:store (store-ref opened) :revision revision
                              :data {:changed changed :predicate (edges/predicate s n)}})))

(defn list-predicates
  "`predicates list`: every predicate by name. Data: `{:count :predicates
   [{:name :axis :description}]}`."
  [_args opts ctx]
  (let [o (open opts ctx) s (:store o) ps (edges/predicates s)]
    (out/ok "predicates list" {:store (store-ref o) :revision (:head s) :data {:count (count ps) :predicates ps}})))

(defn show
  "`predicates show NAME`: one predicate, `{:name :axis :description}`;
   `edges/missing` when it is not registered."
  [args opts ctx]
  (let [o (open opts ctx) s (:store o)]
    (out/ok "predicates show" {:store (store-ref o) :revision (:head s)
                               :data (edges/predicate s (require-arg! args 0 "a predicate NAME" "show"))})))

(defn- problems
  "v1's `validate_predicates`: each entry's own problems, then a sentence for
   every name seen before."
  [entries]
  (:errors (reduce (fn [{:keys [seen] :as acc} e]
                     (-> acc
                         (update :errors into (edges/validate-predicate e))
                         (cond-> (seen (:name e)) (update :errors conj (str "Duplicate predicate '" (:name e) "'.")))
                         (update :seen conj (:name e))))
                   {:seen #{} :errors []} entries)))

(defn validate
  "`predicates validate [NAME]`: checks the registered predicates (or only
   NAME) as v1 did — names and axes of letters, digits, `_`, `-`, and no name
   twice. Valid: data `{:valid true :errors [] :checked n}`. Otherwise the
   failure `edges/invalid` (exit 3; v1 exited 1) with `{:valid false :errors
   […] :checked n}`. An unregistered NAME is `edges/missing`."
  [args opts ctx]
  (let [o (open opts ctx) s (:store o) n (get args 0)
        entries (cond->> (edges/predicates s) n (filterv #(= n (:name %))))
        _ (when (and n (empty? entries)) (edges/predicate s n))
        errors (problems entries)]
    (if (empty? errors)
      (out/ok "predicates validate" {:store (store-ref o) :revision (:head s)
                                     :data {:valid true :errors [] :checked (count entries)}})
      (err/raise :edges/invalid (str "predicates invalid: " (str/join " " errors))
                 {:valid false :errors errors :checked (count entries) :revision (:head s)}))))

(defn remove-predicate
  "`predicates remove NAME`: unregisters a predicate. Data: `{:changed
   :removed NAME :count}`; `edges/missing` when it is not registered."
  [args opts ctx]
  (let [n (require-arg! args 0 "a predicate NAME" "remove")
        {:keys [opened changed revision] s :store} (commit edges/remove-predicate-plan n opts ctx)]
    (out/ok "predicates remove" {:store (store-ref opened) :revision revision
                                 :data {:changed changed :removed n :count (count (edges/predicates s))}})))

(def spec
  "Options of the `predicates` group (babashka.cli spec, merged by `kimun.cli.main`)."
  {:axis {:ref "A" :desc "Axis of a relation type or predicate"}
   :description {:ref "TEXT" :desc "Description of a relation type or predicate"}})

(def commands
  "Subcommand name → command fn `(fn [args opts ctx] envelope)`."
  {"add" add "list" list-predicates "show" show "validate" validate "remove" remove-predicate})

(defn dispatch
  "Runs subcommand `sub` of the `predicates` group with the positional tokens
   that follow it; an unknown or missing subcommand is the failure `cli/usage`."
  [sub args opts ctx]
  (if-let [f (get commands sub)]
    (f (vec args) opts ctx)
    (out/failure :cli/usage
                 (str (if sub (str "unknown subcommand `predicates " sub "`") "missing subcommand for `predicates`")
                      "; expected one of: " (str/join ", " (sort (keys commands))))
                 {:group "predicates" :subcommand sub :known (vec (sort (keys commands)))}
                 6)))
