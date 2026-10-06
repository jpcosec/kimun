(ns kimun.cli.commands.semantic
  "The `kimun semantic` group (v1 `sldb semantic` and `stores
   semantic-export`): `show`, `tags`, `parent-add`, `parent-remove`,
   `equivalent-add` and `export`. Reads derive everything from the opened
   store; every write is a `TransactionPlan` of `kimun.surface.semantic`
   through `sldb.kernel.store/commit!` — there is no side file. Every command
   is `(fn [args opts ctx] envelope)`."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [kimun.cli.commands.docs :as cdocs]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.semantic :as sem]
            [sldb.host.default :as host]
            [sldb.kernel.err :as err]
            [sldb.kernel.store :as store]))

(defn- open [opts {:keys [env cwd]}] (cli-store/open! (cli-store/resolve-store opts env cwd)))
(defn- store-ref [{:keys [descriptor path]}] {:name (:name descriptor) :path path})

(defn- require-arg! [args i what sub]
  (or (get args i)
      (err/raise :cli/usage (str "`semantic " sub "` needs " what) {:group "semantic" :subcommand sub})))

(defn show
  "`semantic show [TAG]`: one tag as the DAG sees it (`{:tag :parents
   :children :below :above :equivalents :documents}`), or without TAG the
   whole DAG (`{:roots :nodes :equivalences}`)."
  [args opts ctx]
  (let [o (open opts ctx) s (:store o) g (sem/dag s)]
    (out/ok "semantic show"
            {:store (store-ref o) :revision (:head s)
             :data (if-let [tag (get args 0)]
                     (assoc (sem/show g tag) :documents (sem/documents-for g (sem/document-tags s) tag))
                     {:roots (sem/roots g)
                      :nodes (mapv (fn [[t ps]] {:tag t :parents (vec ps)}) (:nodes g))
                      :equivalences (into {} (map (fn [[k v]] [k (vec v)])) (:equivalences g))})})))

(defn tags
  "`semantic tags [PREFIX]`: v1's `se` / `se.PREFIX` listing — the root tags,
   or the children of PREFIX read relative to it; a PREFIX with `*` lists the
   tags matching the glob. Data: `{:prefix :tags :documents}`, `:documents`
   being the `Model:name` ids in scope of PREFIX."
  [args opts ctx]
  (let [o (open opts ctx) s (:store o) g (sem/dag s) prefix (get args 0 "")]
    (out/ok "semantic tags"
            {:store (store-ref o) :revision (:head s)
             :data {:prefix prefix
                    :tags (if (str/includes? prefix "*") (sem/glob-tags g prefix) (sem/list-tags g prefix))
                    :documents (if (str/blank? prefix) [] (sem/documents-for g (sem/document-tags s) prefix))}})))

(defn- write
  "Commits the plan `build` returns, when it changes anything."
  [command build a b opts ctx]
  (let [{:keys [backend] s :store :as o} (open opts ctx)
        {:keys [changed plan]} (build host/host s a b {:actor (:actor opts) :timestamp (cdocs/timestamp)})
        r (when changed (store/commit! backend s plan))]
    (out/ok command {:store (store-ref o) :revision (if r (:revision-id r) (:head s))
                     :data (assoc (sem/show (sem/dag (if r (:store r) s)) a) :changed (boolean changed))})))

(defn parent-add
  "`semantic parent-add CHILD PARENT`: declares CHILD a kind of PARENT. Data:
   the child as `show` gives it plus `:changed` (false: it already was)."
  [args opts ctx]
  (write "semantic parent-add" sem/parent-add-plan (require-arg! args 0 "a CHILD tag" "parent-add") (require-arg! args 1 "a PARENT tag" "parent-add") opts ctx))

(defn parent-remove
  "`semantic parent-remove CHILD PARENT`: undeclares it; the parent a tag's
   own name implies cannot be removed (`semantic/implied`)."
  [args opts ctx]
  (write "semantic parent-remove" sem/parent-remove-plan (require-arg! args 0 "a CHILD tag" "parent-remove") (require-arg! args 1 "a PARENT tag" "parent-remove") opts ctx))

(defn equivalent-add
  "`semantic equivalent-add A B`: declares that tag A means tag B."
  [args opts ctx]
  (write "semantic equivalent-add" sem/equivalent-add-plan (require-arg! args 0 "a tag A" "equivalent-add") (require-arg! args 1 "a tag B" "equivalent-add") opts ctx))

(defn- kimun-version [] (or (some-> (io/resource "VERSION") slurp str/trim) "0"))

(defn export
  "`semantic export [-o FILE]`: the `sldb_kgdb_semantic_export` v2 payload
   (producer kimun, relative paths). Data: the payload, or with `-o` the
   summary `{:path :contract :models :documents :tags}` of the file written."
  [args opts {:keys [cwd] :as ctx}]
  (let [o (open opts ctx) s (:store o)
        rel (str (fs/file-name (fs/absolutize (:path o))))
        payload (sem/export s {:generated-at (cdocs/timestamp) :version (kimun-version) :store-path rel
                               :command (into ["semantic" "export"] args)})
        file (or (:output opts) (:o opts))]
    (when file
      (let [f (fs/path cwd file)]
        (when-let [p (fs/parent f)] (fs/create-dirs p))
        (spit (str f) (str (json/generate-string payload {:pretty true}) "\n"))))
    (out/ok "semantic export"
            {:store (store-ref o) :revision (:head s)
             :data (if file
                     {:path file :contract (get payload "contract") :models (count (get payload "models"))
                      :documents (count (get payload "documents")) :tags (count (get-in payload ["semantic_dag" "nodes"]))}
                     payload)})))

(def spec
  "Options of the `semantic` group (babashka.cli spec, merged by `kimun.cli.main`)."
  {:output {:alias :o :ref "FILE" :desc "Write the export to FILE instead of the envelope (semantic export)"}})

(def commands
  "Subcommand name → command fn `(fn [args opts ctx] envelope)`."
  {"show" show "tags" tags "parent-add" parent-add "parent-remove" parent-remove
   "equivalent-add" equivalent-add "export" export})

(defn dispatch
  "Runs subcommand `sub` of the `semantic` group with the positional tokens
   that follow it; an unknown or missing subcommand is the failure `cli/usage`."
  [sub args opts ctx]
  (if-let [f (get commands sub)]
    (f (vec args) opts ctx)
    (out/failure :cli/usage
                 (str (if sub (str "unknown subcommand `semantic " sub "`") "missing subcommand for `semantic`")
                      "; expected one of: " (str/join ", " (sort (keys commands))))
                 {:group "semantic" :subcommand sub :known (vec (sort (keys commands)))}
                 6)))
