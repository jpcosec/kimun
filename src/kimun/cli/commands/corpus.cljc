(ns kimun.cli.commands.corpus
  "The `kimun corpus` group: v1's `GET /corpus/rank` (`sldb.cli.serve.corpus_routes`)
   as a CLI, over the pure engine `kimun.surface.index.corpus`.
   `corpus rank QUERY… [--k N] [--threshold X] [--among ID,ID] [--fields]
   [--refresh]` ranks documents (or, with `--fields`, document fields) by
   similarity to QUERY, best first. `corpus refresh` (re)builds the derived
   index of the store and its linked stores. Reading never writes: v1's
   index-on-read is not reproduced. Without `--refresh`, a store whose index for
   the current head was never built is the explicit error `index/not-indexed`
   (exit 5; v1's HTTP 409 `not_indexed`), not an empty list. An empty `hits`
   always means 'no matches'. Every command is `(fn [args opts ctx] envelope)`."
  (:require             [clojure.string :as str]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.index.cache :as cache]
            [kimun.surface.index.corpus :as corpus]
            [kimun.kernel.err :as err]))

(def spec
  "Options added by the `corpus` group (babashka.cli spec); v1's `RankRequest` names."
  {:k {:ref "N" :desc "rank: at most N hits (default 5)"}
   :threshold {:ref "X" :desc "rank: keep hits scoring at least X (default 0.0)"}
   :among {:ref "ID,ID" :desc "rank: only these ids (a document id admits all its fields)"}
   :fields {:desc "rank: rank document fields instead of whole documents" :coerce :boolean}
   :refresh {:desc "rank: rebuild the index first instead of failing when it is missing" :coerce :boolean}})

(def default-k "Hits returned when `--k` is absent (v1 `RankRequest.k`)." 5)

(defn- usage! [msg data] (err/raise :cli/usage msg (merge {:group "corpus"} data)))

;; ---------------------------------------------------------------- the one door to the index

(defn- index-of
  "`{:index idx :indexed? bool}` of the opened store `o`. With `refresh?` the
   index is rebuilt and written (`cache/rebuild!`); otherwise it is only read
   (`cache/lookup!`, writes nothing) and `:indexed?` says whether it came from
   a valid cache of the current head. A store without revisions has nothing to
   index and counts as indexed."
  [o refresh?]
  (if refresh?
    {:index (:index (cache/rebuild! o)) :indexed? true}
    (let [{:keys [index source reason]} (cache/lookup! o)]
      {:index index :indexed? (or (= :cache source) (= :no-head reason))})))

;; ---------------------------------------------------------------- options

(defn- parse-k [v]
  (cond (nil? v) default-k
        (and (integer? v) (pos? v)) v
        (and (string? v) (re-matches #"\d+" v) (pos? (parse-long v))) (parse-long v)
        :else (usage! (str "`--k` needs a positive integer, got `" v "`") {:option "k" :value (str v)})))

(defn- parse-threshold [v]
  (cond (nil? v) 0.0
        (number? v) (double v)
        (and (string? v) (re-matches #"[+-]?\d+(\.\d+)?([eE][+-]?\d+)?" v)) (parse-double v)
        :else (usage! (str "`--threshold` needs a number, got `" v "`") {:option "threshold" :value (str v)})))

(defn- parse-among [v]
  (when (string? v) (not-empty (vec (remove str/blank? (map str/trim (str/split v #",")))))))

(defn- query-of [args]
  (let [q (str/trim (str/join " " args))]
    (when (str/blank? q) (usage! "`corpus rank` needs a QUERY" {:subcommand "rank"}))
    q))

;; ---------------------------------------------------------------- the federated view

(defn- opened [opts {:keys [env cwd]}]
  (let [o (cli-store/open! (cli-store/resolve-store opts env cwd))]
    {:o o :linked (cli-store/linked-stores (:path o))}))

(defn- stores-of
  "`[{:store name :open o}]`, the current store first (its own descriptor name), then the linked ones."
  [{:keys [o linked]}]
  (into [{:store (get-in o [:descriptor :name]) :open o}]
        (map (fn [{:keys [name open]}] {:store name :open open})) linked))

(defn- view
  "The federated view `[{:store :index}]`; unless `refresh?`, every store must already be indexed."
  [stores refresh?]
  (let [found (mapv #(assoc % :found (index-of (:open %) refresh?)) stores)
        missing (vec (remove #(get-in % [:found :indexed?]) found))]
    (when (seq missing)
      (err/raise :index/not-indexed
                 (str "corpus no indexado: run `kimun corpus refresh` (or pass --refresh); not indexed: "
                      (str/join ", " (map :store missing)))
                 {:not-indexed (mapv :store missing)}))
    (mapv (fn [{:keys [store found]}] {:store store :index (:index found)}) found)))

(defn- store-ref [{:keys [descriptor path]}] {:name (:name descriptor) :path path})

;; ---------------------------------------------------------------- commands

(defn rank
  "`corpus rank QUERY… [--k N] [--threshold X] [--among ID,ID] [--fields] [--refresh]`.
   Data `{:query :fields :hits [{:id :model :name :score :payload :tags …}]}`;
   with `--fields` each hit is `{:id :document-id :field :model :name :score :text :payload :tags}`."
  [args opts ctx]
  (let [q (query-of args) k (parse-k (:k opts)) threshold (parse-threshold (:threshold opts))
        among (parse-among (:among opts)) fields? (boolean (:fields opts))
        w (opened opts ctx) v (view (stores-of w) (boolean (:refresh opts)))
        c (if fields? (corpus/entries v) (corpus/document-entries v))
        hits (corpus/rank c q {:k k :threshold threshold :among among})]
    (out/ok "corpus rank" {:store (store-ref (:o w)) :revision (get-in w [:o :store :head])
                           :data {:query q :fields fields? :hits hits}})))

(defn refresh
  "`corpus refresh`: (re)builds the derived index of the store and its linked
   stores. Data `{:stores [{:store :head :documents :fields}]}` (counts of the
   document and field corpus of each store)."
  [_args opts ctx]
  (let [w (opened opts ctx) v (view (stores-of w) true)]
    (out/ok "corpus refresh"
            {:store (store-ref (:o w)) :revision (get-in w [:o :store :head])
             :data {:stores (mapv (fn [{:keys [store index]}]
                                    {:store store :head (:head index)
                                     :documents (count (corpus/document-entries index))
                                     :fields (count (corpus/entries index))})
                                  v)}})))

(def commands
  "Subcommand name → command fn `(fn [args opts ctx] envelope)`."
  {"rank" rank "refresh" refresh})

(defn dispatch
  "Runs subcommand `sub` of the `corpus` group with the positional tokens that
   follow it; an unknown or missing subcommand is the failure `cli/usage`."
  [sub args opts ctx]
  (if-let [f (get commands sub)]
    (f (vec args) opts ctx)
    (out/failure :cli/usage
                 (str (if sub (str "unknown subcommand `corpus " sub "`") "missing subcommand for `corpus`")
                      "; expected one of: " (str/join ", " (sort (keys commands))))
                 {:group "corpus" :subcommand sub :known (vec (sort (keys commands)))}
                 6)))
