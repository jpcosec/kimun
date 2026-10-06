(ns kimun.cli.commands.stores
  "The `kimun stores` group (docs/v2/06 §B): `init`, `check`, `verify`,
   `show`, and the federation commands `link`, `unlink` and `links`. Every
   command is `(fn [args opts ctx] envelope)` where `args` are the positional
   tokens after the subcommand and `ctx` is `{:env env :cwd cwd}`; `dispatch`
   routes a subcommand name."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.docs :as tdocs]
            [sldb.kernel.err :as err]
            [sldb.kernel.store :as store]))

(defn- store-ref [descriptor path] {:name (:name descriptor) :path path})

(defn- link-warning [{:keys [name status resolved]}]
  {:type (str "link/" (clojure.core/name status))
   :message (str "link " name " -> " resolved
                 (if (= :missing status) ": target does not exist" ": target is not a kimun store"))})

(defn init
  "`stores init`: creates the store at `--store` (if given) or `<cwd>/.kimun`
   with `--name` and `--actor`. Data: `{:path :name :actor :capabilities}`."
  [_args opts {:keys [cwd]}]
  (let [dir (if (:store opts)
              (cli-store/normalize-store-path (:store opts))
              (str (fs/path (fs/absolutize cwd) cli-store/dirname)))
        r (cli-store/init! dir {:name (:name opts) :actor (:actor opts)})]
    (out/ok "stores init" {:store (store-ref (:descriptor r) (:path r))
                           :revision nil
                           :data (select-keys r [:path :name :actor :capabilities])})))

(defn check
  "`stores check`: opens the store (replaying the log) and reports
   `{:name :path :hash-alg :revisions :trees :objects :head :links}` where
   `:links` is the `kimun.cli.store/links` report; a link whose target is
   missing or not a store is a warning `{:type \"link/missing\" :message …}`
   (the command stays `:ok`). When the store tracks typed documents, `:documents
   {:tracked n :findings [{:name :model :state :file}]}` lists those whose file
   was modified or is missing, or whose model was redefined, each also a warning."
  [_args opts {:keys [env cwd]}]
  (let [{:keys [descriptor store path]} (cli-store/open! (cli-store/resolve-store opts env cwd))
        link-report (cli-store/links path)
        root (fs/parent (fs/absolutize path))
        typed (tdocs/documents store)
        sync (into {} (map (juxt :name :status))
                   (tdocs/sync-report store #(let [f (fs/path root %)] (when (fs/regular-file? f) (slurp (str f))))))
        doc-report (vec (for [d typed
                              :let [file (get sync (:name d) :none)]
                              :when (or (not= :intact (:state d)) (contains? #{:modified :missing} file))]
                          {:name (:name d) :model (:model d) :state (:state d) :file file}))]
    (out/ok "stores check" {:store (store-ref descriptor path)
                            :revision (:head store)
                            :data (cond-> {:name (:name descriptor)
                                   :path path
                                   :hash-alg (:hash-alg descriptor)
                                   :revisions (count (:revisions store))
                                   :trees (count (:trees store))
                                   :objects (count (:objects store))
                                   :head (:head store)
                                   :links link-report}
                                  (seq typed) (assoc :documents {:tracked (count typed) :findings doc-report}))
                            :warnings (into (mapv link-warning (remove #(= :ok (:status %)) link-report))
                                            (map (fn [d]
                                                   {:type (if (= :intact (:state d)) (str "docs/file-" (name (:file d))) (str "docs/" (name (:state d))))
                                                    :message (str "document `" (:name d) "` (" (:model d) "): "
                                                                  (if (= :intact (:state d))
                                                                    (str "its file is " (name (:file d)) " since the last commit; run `docs update " (:name d) "`")
                                                                    (str "its model was redefined; run `docs update " (:name d) "` or redefine with --backfill")))}))
                                            doc-report)})))

(defn verify
  "`stores verify`: recomputes the hash of every reachable object
   (`sldb.kernel.store/verify`). Data `{:checked :bad}`; a mismatch is the
   failure `store/corrupt` (exit 5) carrying `:bad`."
  [_args opts {:keys [env cwd]}]
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
  [_args opts {:keys [env cwd]}]
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

(defn- need-args
  "Raises the usage failure when `args` has not exactly `n` tokens."
  [command usage args n]
  (when (not= n (count args))
    (err/raise :cli/usage (str "usage: kimun stores " usage) {:command command})))

(defn link
  "`stores link NAME PATH`: links the current store to the store at PATH (a
   `.kimun` or a project directory) by NAME, persisting a path relative to
   this store. Data: `{:link {:name :path} :resolved}`."
  [args opts {:keys [env cwd]}]
  (need-args "stores link" "link NAME PATH" args 2)
  (let [dir (cli-store/resolve-store opts env cwd)
        {:keys [path descriptor store]} (cli-store/open! dir)
        l (cli-store/link! path (first args) (second args) cwd)]
    (out/ok "stores link" {:store (store-ref descriptor path)
                           :revision (:head store)
                           :data {:link l
                                  :resolved (:resolved (first (filter #(= (:name l) (:name %)) (cli-store/links path))))}})))

(defn unlink
  "`stores unlink NAME`: removes the link NAME. Data: `{:unlinked {:name :path}}`."
  [args opts {:keys [env cwd]}]
  (need-args "stores unlink" "unlink NAME" args 1)
  (let [{:keys [path descriptor store]} (cli-store/open! (cli-store/resolve-store opts env cwd))]
    (out/ok "stores unlink" {:store (store-ref descriptor path)
                             :revision (:head store)
                             :data {:unlinked (cli-store/unlink! path (first args))}})))

(defn links
  "`stores links`: lists the links with their status. Data:
   `{:links [{:name :path :resolved :status}]}`; broken links are warnings."
  [_args opts {:keys [env cwd]}]
  (let [{:keys [path descriptor store]} (cli-store/open! (cli-store/resolve-store opts env cwd))
        report (cli-store/links path)]
    (out/ok "stores links" {:store (store-ref descriptor path)
                            :revision (:head store)
                            :data {:links report}
                            :warnings (mapv link-warning (remove #(= :ok (:status %)) report))})))

(def spec
  "Options of the `stores` group (babashka.cli spec, merged by `kimun.cli.main`)."
  {:name {:ref "NAME" :desc "Store name (stores init)"}})

(def commands
  "Subcommand name → command fn."
  {"init" init "check" check "verify" verify "show" show
   "link" link "unlink" unlink "links" links})

(defn dispatch
  "Runs subcommand `sub` of the `stores` group (`args` are the positional tokens);
   an unknown or missing subcommand is the failure `cli/usage` listing the
   known ones."
  [sub args opts ctx]
  (if-let [f (get commands sub)]
    (f (vec args) opts ctx)
    (out/failure :cli/usage
                 (str (if sub (str "unknown subcommand `stores " sub "`") "missing subcommand for `stores`")
                      "; expected one of: " (str/join ", " (sort (keys commands))))
                 {:group "stores" :subcommand sub :known (vec (sort (keys commands)))}
                 6)))
