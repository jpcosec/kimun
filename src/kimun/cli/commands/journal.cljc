(ns kimun.cli.commands.journal
  "The `kimun journal` group: `show` and `verify`. The journal is a read-only
   view over the log and the revisions (`kimun.surface.journal`); nothing is
   written. Every command is `(fn [args opts ctx] envelope)`, `ctx` being
   `{:env env :cwd cwd}`."
  (:require [clojure.string :as str]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.journal :as journal]
            [sldb.kernel.err :as err]
            [sldb.kernel.store :as store]))

(defn- store-ref [descriptor path] {:name (:name descriptor) :path path})

(defn- parse-limit
  "The `--limit` value (string or number) as a positive integer, or nil when
   absent. Raises `:cli/usage` otherwise."
  [v]
  (when (some? v)
    (let [n (if (number? v)
              v
              (try (#?(:clj Long/parseLong :cljs js/parseInt) (str/trim (str v)))
                   (catch #?(:clj Exception :cljs :default) _ nil)))]
      (if (and (integer? n) (pos? n))
        (long n)
        (err/raise :cli/usage (str "--limit needs a positive integer, got `" v "`") {:option "limit" :value v})))))

(defn show
  "`journal show [--limit N] [--by ACTOR] [--tree NAME|ID]`: the journal
   entries newest first (data: the vector of entries of
   `kimun.surface.journal/entries`)."
  [_args opts {:keys [env cwd]}]
  (let [limit (parse-limit (:limit opts))
        {:keys [backend descriptor store path]} (cli-store/open! (cli-store/resolve-store opts env cwd))
        es (journal/entries store (store/read-log backend)
                            {:actor (:by opts) :tree (:tree opts) :limit limit})]
    (out/ok "journal show" {:store (store-ref descriptor path)
                            :revision (:head store)
                            :data es})))

(defn- corrupt [path message data]
  (out/failure :store/corrupt message (assoc data :path path) 5))

(defn verify
  "`journal verify`: checks the revision DAG (`kimun.surface.journal/verify`)
   and the hash of every reachable object. Data `{:revisions :checked :objects
   :bad}`; any problem, including a log that no longer replays, is the failure
   `store/corrupt` (exit 5) carrying `:bad`."
  [_args opts {:keys [env cwd]}]
  (let [dir (cli-store/resolve-store opts env cwd)
        _ (when (or (nil? dir) (not (cli-store/store? dir))) (cli-store/open! dir))
        opened (try (cli-store/open! dir)
                    (catch #?(:clj Exception :cljs :default) e e))]
    (if (instance? #?(:clj Throwable :cljs js/Error) opened)
      (if (= :cli/no-store (:type (ex-data opened)))
        (throw opened)
        (corrupt (str dir) (str "the log does not replay in " dir ": " (ex-message opened))
                 {:bad [{:reason :replay-failed :message (ex-message opened)}]}))
      (let [{:keys [backend descriptor store path]} opened
            txs (store/read-log backend)
            r (journal/verify store txs)
            o (store/verify backend store)
            bad (into (:bad r) (:bad o))]
        (if (empty? bad)
          (out/ok "journal verify" {:store (store-ref descriptor path)
                                    :revision (:head store)
                                    :data {:revisions (:revisions r) :checked (:checked r)
                                           :objects (:checked o) :bad []}})
          (corrupt path (str (count bad) " problem(s) in the journal of " path)
                   {:revisions (:revisions r) :checked (:checked r) :objects (:checked o) :bad bad}))))))

(def spec
  "Options of the `journal` group (babashka.cli spec, merged by `kimun.cli.main`).
   `--actor` is global (the actor recorded in transactions), so the filter is `--by`."
  {:limit {:ref "N" :desc "Show only the N newest entries (journal show)"}
   :by {:ref "ACTOR" :desc "Keep entries recorded by ACTOR (journal show)"}
   :tree {:ref "NAME|ID" :desc "Keep entries that touch this tree (journal show)"}})

(def commands
  "Subcommand name → command fn `(fn [args opts ctx] envelope)`."
  {"show" show "verify" verify})

(defn dispatch
  "Runs subcommand `sub` of the `journal` group; an unknown or missing
   subcommand is the failure `cli/usage`."
  [sub args opts ctx]
  (if-let [f (get commands sub)]
    (f (vec args) opts ctx)
    (out/failure :cli/usage
                 (str (if sub (str "unknown subcommand `journal " sub "`") "missing subcommand for `journal`")
                      "; expected one of: " (str/join ", " (sort (keys commands))))
                 {:group "journal" :subcommand sub :known (vec (sort (keys commands)))}
                 6)))
