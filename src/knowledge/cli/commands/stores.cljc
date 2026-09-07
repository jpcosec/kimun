(ns knowledge.cli.commands.stores
  "The `knowledge stores` group (docs/v2/06 §B): `init`, `check`, `verify`
   and `show`. Every command is `(fn [opts ctx] envelope)` where `ctx` is
   `{:env env :cwd cwd}`; `dispatch` routes a subcommand name."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [knowledge.cli.out :as out]
            [knowledge.cli.store :as cli-store]
            [sldb.kernel.store :as store]))

(defn- store-ref [descriptor path] {:name (:name descriptor) :path path})

(defn init
  "`stores init`: creates the store at `--store` (if given) or `<cwd>/.knowledge`
   with `--name` and `--actor`. Data: `{:path :name :actor :capabilities}`."
  [opts {:keys [cwd]}]
  (let [dir (if (:store opts)
              (cli-store/normalize-store-path (:store opts))
              (str (fs/path (fs/absolutize cwd) cli-store/dirname)))
        r (cli-store/init! dir {:name (:name opts) :actor (:actor opts)})]
    (out/ok "stores init" {:store (store-ref (:descriptor r) (:path r))
                           :revision nil
                           :data (select-keys r [:path :name :actor :capabilities])})))

(defn check
  "`stores check`: opens the store (replaying the log) and reports
   `{:name :path :hash-alg :revisions :trees :objects :head :links}`."
  [opts {:keys [env cwd]}]
  (let [{:keys [descriptor store path]} (cli-store/open! (cli-store/resolve-store opts env cwd))]
    (out/ok "stores check" {:store (store-ref descriptor path)
                            :revision (:head store)
                            :data {:name (:name descriptor)
                                   :path path
                                   :hash-alg (:hash-alg descriptor)
                                   :revisions (count (:revisions store))
                                   :trees (count (:trees store))
                                   :objects (count (:objects store))
                                   :head (:head store)
                                   :links (vec (or (:links descriptor) []))}})))

(defn verify
  "`stores verify`: recomputes the hash of every reachable object
   (`sldb.kernel.store/verify`). Data `{:checked :bad}`; a mismatch is the
   failure `store/corrupt` (exit 5) carrying `:bad`."
  [opts {:keys [env cwd]}]
  (let [{:keys [backend descriptor store path]} (cli-store/open! (cli-store/resolve-store opts env cwd))
        {:keys [ok? checked bad]} (store/verify backend store)]
    (if ok?
      (out/ok "stores verify" {:store (store-ref descriptor path)
                               :revision (:head store)
                               :data {:checked checked :bad bad}})
      (out/failure :store/corrupt
                   (str (count bad) " object(s) do not hash to their name in " path)
                   {:checked checked :bad bad :path path}
                   5))))

(defn show
  "`stores show`: descriptor and heads without replaying the log:
   `{:name :path :hash-alg :links :head :capabilities}`."
  [opts {:keys [env cwd]}]
  (let [dir (cli-store/resolve-store opts env cwd)]
    (when (or (nil? dir) (not (cli-store/store? dir)))
      (cli-store/open! dir))
    (let [b (cli-store/backend dir)
          descriptor (store/read-descriptor b)
          heads (store/read-heads b)]
      (out/ok "stores show" {:store (store-ref descriptor (str dir))
                             :revision (:head heads)
                             :data {:name (:name descriptor)
                                    :path (str dir)
                                    :hash-alg (:hash-alg descriptor)
                                    :links (vec (or (:links descriptor) []))
                                    :head (:head heads)
                                    :capabilities (:capabilities descriptor)}}))))

(def commands
  "Subcommand name → command fn."
  {"init" init "check" check "verify" verify "show" show})

(defn dispatch
  "Runs subcommand `sub` of the `stores` group; an unknown or missing
   subcommand is the failure `cli/usage` listing the known ones."
  [sub opts ctx]
  (if-let [f (get commands sub)]
    (f opts ctx)
    (out/failure :cli/usage
                 (str "unknown subcommand `stores " sub "`; expected one of: " (str/join ", " (sort (keys commands))))
                 {:group "stores" :subcommand sub :known (vec (sort (keys commands)))}
                 6)))
