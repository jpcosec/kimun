(ns kimun.cli.commands.stores
  "The `kimun stores` group (docs/v2/06 §B): `init`, `check`, `health`,
   `update`, `reconcile`, `verify`, `upgrade`, `show`, and the federation commands `link`, `unlink` and `links`. Every
   command is `(fn [args opts ctx] envelope)` where `args` are the positional
   tokens after the subcommand and `ctx` is `{:env env :cwd cwd}`; `dispatch`
   routes a subcommand name."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [kimun.cli.commands.docs :as docs]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.diagnostics :as diag]
            [kimun.surface.docs :as tdocs]
            [kimun.host.default :as host]
            [kimun.kernel.err :as err]
            [kimun.kernel.store :as store]
            [kimun.kernel.upgrade :as upgrade]))

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

(defn- file-reader
  "`(fn [relative-path] text-or-nil)` over the project that owns store `path`."
  [path]
  (let [root (fs/parent (fs/absolutize path))]
    (fn [rel] (let [f (fs/path root rel)] (when (fs/regular-file? f) (slurp (str f)))))))

(defn- diagnose
  "The `kimun.surface.diagnostics/report` of an opened store (`open!` result),
   with the kernel's hash verification handed in."
  [{:keys [backend store path]}]
  (diag/report store (file-reader path) {:verify (store/verify backend store)}))

(def ^:private warning-type
  {:reformatted "docs/file-modified" :edited "docs/file-modified" :missing "docs/file-missing"
   :contract-moved "docs/superseded" :unextractable "docs/unextractable" :noise "docs/structural-noise"
   :orphan "docs/orphan" :corrupt "store/corrupt"})

(defn- finding-warning [f]
  (assoc (select-keys f [:category :model :document :path :action :undeclared])
         :type (warning-type (:category f)) :message (:message f)))

(defn check
  "`stores check`: opens the store (replaying the log) and reports
   `{:name :path :hash-alg :revisions :trees :objects :head :links}` where
   `:links` is the `kimun.cli.store/links` report; a link whose target is
   missing or not a store is a warning `{:type \"link/missing\" :message …}`
   (the command stays `:ok`). When the store tracks typed documents (or is
   corrupt), `:documents` is the `kimun.surface.diagnostics/report` plus
   `:tracked`: `{:tracked :valid :summary :checked :counts :models :documents
   :findings}`; every finding is also a warning `{:type :message :category
   :model :document :path :action}` naming the command that repairs it. A
   store that is not valid (anything beyond `:ok` and `:reformatted`) is the
   failure `store/invalid`, exit 5, carrying the same data plus `:warnings`."
  [_args opts {:keys [env cwd]}]
  (let [{:keys [descriptor store path] :as opened} (cli-store/open! (cli-store/resolve-store opts env cwd))
        link-report (cli-store/links path)
        report (diagnose opened)]
    (let [data (cond-> {:name (:name descriptor)
                        :path path
                        :hash-alg (:hash-alg descriptor)
                        ;; nil when damage (reported in :documents) makes the view unreadable
                        :revisions (err/rescue-any (fn [_] nil) (count (:revisions store)))
                        :trees (err/rescue-any (fn [_] nil) (count (:trees store)))
                        :objects (err/rescue-any (fn [_] nil) (count (:objects store)))
                        :head (:head store)
                        :links link-report}
                 (or (seq (:documents report)) (seq (:findings report)))
                 (assoc :documents (assoc report :tracked (count (:documents report)))))
          warnings (into (mapv link-warning (remove #(= :ok (:status %)) link-report))
                         (map finding-warning) (:findings report))]
      (if (false? (:valid report))
        ;; an invalid store is a failure a script can see (v1 exited non-zero);
        ;; the whole report travels in the error data, warnings included
        (out/failure :store/invalid (:summary report) (assoc data :warnings warnings) 5)
        (out/ok "stores check" {:store (store-ref descriptor path)
                                :revision (:head store)
                                :data data
                                :warnings warnings})))))

(defn health
  "`stores health`: the verdict of the store (`kimun.surface.diagnostics/health`):
   `{:status :summary :findings :actions :next}` with `:status` `:ok`,
   `:degraded` (a command repairs it: `:next` is `run: …`) or `:needs-human`
   (`:next` is `inspect: …`). Each finding is also a warning."
  [_args opts {:keys [env cwd]}]
  (let [{:keys [descriptor store path] :as opened} (cli-store/open! (cli-store/resolve-store opts env cwd))
        report (diagnose opened)]
    (out/ok "stores health" {:store (store-ref descriptor path)
                             :revision (:head store)
                             :data (assoc (diag/health report) :summary (:summary report))
                             :warnings (mapv finding-warning (:findings report))})))

(defn update-store
  "`stores update`: applies every repair that needs no decision — re-reads the
   documents whose file was only reformatted and re-extracts those whose
   model's contract moved (v1 `stores update` recomputed hashes; here a hash
   is never stored apart from its content, so updating means committing the
   new source). Findings that need a human are left alone and listed. Data:
   `{:updated [name] :skipped [{:document :category :action}] :status}`."
  [_args opts {:keys [env cwd]}]
  (let [{:keys [backend descriptor path] :as opened} (cli-store/open! (cli-store/resolve-store opts env cwd))
        read-file (file-reader path)
        findings (:findings (diagnose opened))
        final (reduce (fn [s f]
                        (let [doc (tdocs/find-document s (:document f))
                              text (or (some-> (:path doc) read-file) (:source doc))
                              {:keys [plan]} (tdocs/update-plan host/host s (:document f) text
                                                                {:actor (:actor opts) :timestamp (docs/timestamp) :operation "stores update"})]
                          (if plan (:store (store/commit! backend s plan)) s)))
                      (:store opened)
                      (filter (comp diag/safe :category) findings))
        after (diag/report final read-file)]
    (out/ok "stores update" {:store (store-ref descriptor path)
                             :revision (:head final)
                             :data {:updated (mapv :document (filter (comp diag/safe :category) findings))
                                    :skipped (mapv #(select-keys % [:document :category :action]) (remove (comp diag/safe :category) findings))
                                    :status (:status (diag/health after))}
                             :warnings (mapv finding-warning (:findings after))})))

(defn- canonical [p] (str (fs/canonicalize p)))

(defn reconcile
  "`stores reconcile [ROOT] [--apply]`: compares the stores found under ROOT
   (default: this store's project directory) with this store's links (v1
   `reconcile_catalog`). Data: `{:root :discovered [path] :missing [path]
   :stale [{:name :resolved}] :applied}` — `:missing` are stores found but
   not linked, `:stale` links whose target is gone. Nothing is written
   without `--apply`, which links each missing store under its project
   directory's name and unlinks each stale link."
  [args opts {:keys [env cwd]}]
  (let [{:keys [descriptor store path]} (cli-store/open! (cli-store/resolve-store opts env cwd))
        root (if-let [r (first args)] (str (fs/absolutize (fs/path cwd r))) (str (fs/parent (fs/absolutize path))))
        found (->> (fs/glob root "**store.edn" {:hidden true})
                   (map fs/parent)
                   (filter #(= cli-store/dirname (str (fs/file-name %))))
                   (map canonical) (remove #{(canonical path)}) sort vec)
        linked (cli-store/links path)
        registered (set (map (comp canonical :resolved) (filter #(= :ok (:status %)) linked)))
        missing (vec (remove registered found))
        stale (mapv #(select-keys % [:name :resolved]) (remove #(= :ok (:status %)) linked))]
    (when (:apply opts)
      (doseq [s stale] (cli-store/unlink! path (:name s)))
      (doseq [m missing] (cli-store/link! path (str (fs/file-name (fs/parent m))) m cwd)))
    (out/ok "stores reconcile" {:store (store-ref descriptor path)
                                :revision (:head store)
                                :data {:root root :discovered found :missing missing :stale stale :applied (boolean (:apply opts))}})))

(defn verify
  "`stores verify`: the full verification (`kimun.kernel.store/verify-full`):
   replays the whole log (every transaction and revision id, final heads
   against heads.edn) and recomputes the hash of every reachable object; a log
   that does not replay is `:bad [{:reason :replay-failed}]`. (`stores check`
   runs only the hash check, on the store as opened from its heads.) Data `{:checked :bad}`, plus `:uncommitted-tail`
   when the log ends with a line the heads do not record; a mismatch is the
   failure `store/corrupt` (exit 5) carrying `:bad`."
  [_args opts {:keys [env cwd]}]
  (let [{:keys [backend descriptor store path]} (cli-store/open! (cli-store/resolve-store opts env cwd))
        {:keys [ok? checked bad uncommitted-tail]} (store/verify-full backend (:host store))]
    (if ok?
      (out/ok "stores verify" {:store (store-ref descriptor path)
                               :revision (:head store)
                               :data (cond-> {:checked checked :bad bad}
                                       ;; a writer died mid-commit: reported, not a failure; the next commit discards it
                                       uncommitted-tail (assoc :uncommitted-tail (select-keys uncommitted-tail [:id :revision :torn-line])))})
      (out/failure :store/corrupt
                   (if-let [f (first (filter #(= :replay-failed (:reason %)) bad))]
                     (str "the log does not replay in " path ": " (:message f))
                     (str (count bad) " problem(s) in " path " (objects that do not hash to their name, or a log that does not end at the heads)"))
                   {:checked checked :bad bad :path path}
                   5))))

(def ^:private layout
  "Entries of a store directory that the upgrade rebuilds, or drops: the
   derived index is keyed by revision ids that the upgrade replaces
   (`rebuild-indexes` writes it again)."
  #{"objects" "log.edn" "heads.edn" "store.edn" "lock" "index"})

(defn- stamp [] (str/replace (docs/timestamp) #"[^0-9]" ""))

(defn upgrade-store
  "`stores upgrade`: rewrites a format-1 store as a format-2 one
   (`kimun.kernel.upgrade/upgrade!`: every transaction re-committed in log order
   through the format-2 engine, then checked and fully verified). The new store
   is built in the sibling directory `<store>.upgrade-<stamp>`; the original is
   untouched until the new one verifies, then it is renamed to
   `<store>.format-1-<stamp>` (a backup the user may delete) and the new one
   takes its place. Files of the store directory other than the layout are
   carried over (not `index/`, derived and keyed by the old revision ids).
   `upgrade-format-1.edn` in the new store maps every old revision and
   transaction id to its new id. A format-2 store is left as is
   (`:upgraded false`). When a writer commits to the original meanwhile the
   upgrade is abandoned with `store/stale` (exit 4). Data: `{:upgraded
   :format-version :path}` plus, when upgraded, `{:transactions :head-before
   :backup :map :dropped-tail :checked :seconds}`."
  [_args opts {:keys [env cwd]}]
  (let [dir0 (cli-store/resolve-store opts env cwd)
        _ (when (or (nil? dir0) (not (cli-store/store? dir0))) (cli-store/open! dir0))
        dir (str (fs/absolutize dir0))
        b (cli-store/backend dir)
        descriptor (store/read-descriptor b)
        heads-file (str (fs/path dir "heads.edn"))
        heads-text #(when (fs/exists? heads-file) (slurp heads-file))]
    (if (= 2 (:format-version descriptor 1))
      (out/ok "stores upgrade" {:store (store-ref descriptor dir)
                                :revision (:head (store/read-heads b))
                                :data {:upgraded false :format-version 2 :path dir}})
      (let [t (stamp)
            tmp (str dir ".upgrade-" t)
            backup (str dir ".format-1-" t)
            before (heads-text)
            head-before (:head (store/read-heads b))
            t0 (System/nanoTime)
            r (try (upgrade/upgrade! b (cli-store/backend tmp) host/host)
                   (catch Exception e (fs/delete-tree tmp) (throw e)))]
        (when (not= before (heads-text))
          (fs/delete-tree tmp)
          (err/raise :store/stale (str "the store at " dir " changed while it was being upgraded; run the upgrade again") {:path dir}))
        (doseq [f (fs/list-dir dir)
                :let [n (str (fs/file-name f))]
                :when (not (or (layout n) (str/includes? n ".tmp-")))]
          (if (fs/directory? f) (fs/copy-tree f (fs/path tmp n)) (fs/copy f (fs/path tmp n))))
        (spit (str (fs/path tmp "upgrade-format-1.edn"))
              (pr-str {:from-format 1 :to-format 2 :head-before head-before :head (:head r)
                       :revisions (into (sorted-map) (:revisions r)) :transaction-ids (into (sorted-map) (:transaction-ids r))}))
        (fs/move dir backup {:atomic-move true})
        (fs/move tmp dir {:atomic-move true})
        (out/ok "stores upgrade" {:store (store-ref descriptor dir)
                                  :revision (:head r)
                                  :data {:upgraded true :format-version 2 :path dir
                                         :transactions (:transactions r)
                                         :head-before head-before
                                         :backup backup
                                         :map (str (fs/path dir "upgrade-format-1.edn"))
                                         :dropped-tail (some-> (:dropped-tail r) (select-keys [:id :revision :torn-line]))
                                         :checked (get-in r [:verify :checked])
                                         :seconds (/ (Math/round (/ (- (System/nanoTime) t0) 1e7)) 100.0)}})))))

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
  {:name {:ref "NAME" :desc "Store name (stores init)"}
   :apply {:desc "Write what the comparison found (stores reconcile)" :coerce :boolean}})

(def commands
  "Subcommand name → command fn."
  {"init" init "check" check "verify" verify "upgrade" upgrade-store "show" show
   "link" link "unlink" unlink "links" links
   "health" health "update" update-store "reconcile" reconcile})

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
