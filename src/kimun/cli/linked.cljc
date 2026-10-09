(ns kimun.cli.linked
  "Models and stores reached through links (docs/v2/06 §2), for `docs
   create|track`: `--store ALIAS` picks a linked store as the one written to,
   and a model may be named `ALIAS:Model` or live, unqualified, in a single
   linked store.

   There are no model mirrors: the descriptor is read from the store that owns
   it at the moment of the command. What the local store keeps is the BINDING
   — the owner's descriptor node itself, appended to the local `models` tree
   byte for byte (`:model/version` included, so it is the same
   content-addressed node id in both stores), in a plan whose journal metadata
   (`kimun.surface.docs/meta-key`) records `{:operation \"models import\"
   :address \"ALIAS:Model\" :origin {:alias :store :id :version}}`. From then on
   the document is an ordinary typed document of the local store: reads,
   updates and `stores check` never resolve the link again, and a broken link
   breaks nothing already written. A later change of the owner's model is not
   propagated: binding again while the local contract differs is
   `:models/conflict`, and the way forward is an explicit `models define`."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [kimun.cli.store :as cli-store]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.models.store :as mstore]
            [kimun.kernel.err :as err]))

(defn- canonical [dir] (str (fs/canonicalize dir)))

(defn- store-dir? [dir] (boolean (and dir (cli-store/store? dir))))

(defn target
  "The store a `docs` write goes to: `{:opened o :alias a :home dir}`. `o` is
   the `kimun.cli.store/open!` map; `:home` is the store the invocation runs
   in (`$KIMUN_STORE`, else discovered from `cwd`), whose links give aliases
   their meaning, or nil. `--store` is a path as everywhere else; when it is
   not a store path and names a link of the home store (v1 `docs create
   --store ALIAS`), that linked store is opened and `:alias` is the name.
   Otherwise `:alias` is nil and a wrong `--store` raises `:cli/no-store` as
   before."
  [opts env cwd]
  (let [dir (cli-store/resolve-store opts env cwd)
        token (:store opts)
        home (let [h (cli-store/resolve-store (dissoc opts :store) env cwd)] (when (store-dir? h) h))
        ;; `resolve-store` also resolves a link NAME to the linked store, so the
        ;; alias is told apart here: a token that is not itself a store path.
        as-path (when token (cli-store/normalize-store-path (str (fs/path cwd token))))
        linked (when (and token home (not (store-dir? as-path)))
                 (first (filter #(= token (:name %)) (cli-store/linked-stores home))))]
    (if linked
      {:opened (:open linked) :alias token :home home}
      {:opened (cli-store/open! dir) :alias nil :home home})))

(defn- reachable
  "The linked stores consulted for models: the direct links of the home store,
   then those of the target store, as `kimun.cli.store/linked-stores` gives them."
  [target-dir home]
  (vec (mapcat cli-store/linked-stores (distinct (remove nil? [home target-dir])))))

(defn- foreign [l m]
  {:name (:name m) :descriptor (:descriptor m) :id (:id m)
   :origin {:alias (:name l) :store (get-in l [:open :descriptor :name]) :id (:id m) :version (:version m)}})

(defn- local [m] {:name (:name m) :descriptor (:descriptor m) :id (:id m) :origin nil})

(defn- model-in [l model-name] (mstore/find-model (get-in l [:open :store]) model-name))

(defn resolve-model
  "The model `token` names for a document of the opened store `opened` (an
   `open!` map), as `{:name :descriptor :id :origin}` — `:origin` nil for a
   model of the store itself, else `{:alias :store :id :version}` of the linked
   store that owns it. `home` is `target`'s `:home`.
   - `ALIAS:Model`: the model of the store linked as ALIAS (links of the home
     store first, then of the target store). Raises `:docs/unknown-alias`
     (exit 3; data `:alias :known`) when no reachable link has that name and
     `:models/missing` (exit 1) when that store has no such model.
   - `Model`: the store's own model when it has one (local wins). Otherwise
     the linked stores are searched: exactly one owner (or several holding
     the very same node) binds; several different ones raise
     `:docs/ambiguous` (exit 2; data `:model :stores`) asking for
     `ALIAS:Model`; none raises `:models/missing` (exit 1)."
  [{:keys [store path]} home token]
  (let [[alias model-name] (if (str/includes? token ":") (str/split token #":" 2) [nil token])
        self (canonical path)
        links (reachable path home)
        mine (mstore/find-model store model-name)]
    (if alias
      (let [l (or (first (filter #(= alias (:name %)) links))
                  (err/raise :docs/unknown-alias
                             (str "no linked store `" alias "`: link it with `stores link " alias " PATH`"
                                  (when (seq links) (str " (linked: " (str/join ", " (distinct (map :name links))) ")")))
                             {:alias alias :model model-name :known (vec (distinct (map :name links)))}))
            own? (= self (canonical (:link-path l)))
            m (or (if own? mine (model-in l model-name))
                  (err/raise :models/missing (str "no model `" model-name "` in the linked store `" alias "`")
                             {:model model-name :alias alias}))]
        (if own? (local m) (foreign l m)))
      (if mine
        (local mine)
        (let [found (for [l links :when (not= self (canonical (:link-path l)))
                          :let [m (model-in l model-name)] :when m] (foreign l m))
              ids (distinct (map :id found))]
          (case (count ids)
            0 (err/raise :models/missing
                         (str "no model `" model-name "` in the store"
                              (when (seq links) " nor in its linked stores") ": define it with `models define`")
                         {:model model-name :known (mapv :name (mstore/models store))})
            1 (first found)
            (err/raise :docs/ambiguous
                       (str "model `" model-name "` is defined differently in " (count ids) " linked stores ("
                            (str/join ", " (distinct (map #(get-in % [:origin :alias]) found)))
                            "); qualify it as ALIAS:" model-name)
                       {:model model-name :stores (vec (distinct (map #(get-in % [:origin :alias]) found)))})))))))

(defn import-plan
  "The TransactionPlan that binds the foreign model `m` (a `resolve-model`
   result) in `store`, or nil when there is nothing to write: `m` is local, or
   the store already holds that node or the same contract under that name.
   The plan is `kimun.surface.models.store/define-plan`'s with the owner's
   node put in verbatim (same node id as in the owning store) and the origin
   in the journal metadata (see the namespace docstring). `opts` is
   `define-plan`'s: `{:actor :timestamp :tree-id}`. Raises `:models/conflict`
   (exit 4) when the store holds another contract under that name."
  [host store {:keys [name descriptor id origin]} opts]
  (let [mine (mstore/find-model store name)
        contract #(dissoc % :model/version)]
    (cond
      (nil? origin) nil
      (and mine (or (= id (:id mine)) (= (contract descriptor) (contract (:descriptor mine))))) nil
      mine (err/raise :models/conflict
                      (str "model `" name "` of the linked store `" (:alias origin) "` differs from the `" name
                           "` this store already holds: use the local one, or redefine it with `models define`")
                      {:model name :alias (:alias origin) :local (:id mine) :foreign id})
      :else (-> (:plan (mstore/define-plan host store descriptor opts))
                (update :ops (fn [ops] (mapv #(if (= :m (:as %)) (assoc % :node (mstore/descriptor-node descriptor)) %) ops)))
                (update :engines assoc tdocs/meta-key
                        (pr-str {:operation "models import" :address (str (:alias origin) ":" name) :origin origin :fields {}}))))))
