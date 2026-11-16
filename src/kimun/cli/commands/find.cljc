(ns kimun.cli.commands.find
  "The `kimun find` command (docs/v2/06 §3; v1 `sldb find` and `sldb legacy
   find`), over the pure engine `kimun.surface.index.search`.

   `find TERM [--in physical|semantic|all] [--type KIND[,KIND]] [--global]
   [--regex] [--fuzzy] [--where EXPR] [--select k1,k2] [--limit N]`. `find`
   has no subcommand: the first positional is the TERM (`\"\"` matches
   everything; an address such as `st.{Model+}` or `gse.topic.x` is resolved as
   one). Results are ordered by store name, then tree id; `--limit` cuts
   after ordering and `--select` projects after that. An empty result is the
   failure `find/empty` (exit 1); a bad `--where` is `query/bad-where` and a
   malformed address `query/bad-address` (exit 3), raised before anything is
   printed; a bad option value is `cli/usage`. Reading never writes: the index
   comes from `cache/federated!`, nothing under the store directory changes."
  (:require [clojure.string :as str]
            [kimun.cli.args :as cli-args]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.index.cache :as cache]
            [kimun.surface.index.search :as search]
            [kimun.surface.index.where :as where]
            [kimun.kernel.err :as err]))

(def spec
  "Options added by `find` (babashka.cli spec); v1's names."
  {:in {:ref "LAYER" :desc "find: physical, semantic or all (default all; v1's `both` is accepted)"}
   :type {:ref "KIND[,KIND]" :desc "find: only results of these kinds: store, model, doc, section, field, tag"}
   :global {:desc "find: search the linked stores too" :coerce :boolean}
   :regex {:desc "find: TERM is a regular expression" :coerce :boolean}
   :fuzzy {:desc "find: approximate match (Dice >= 0.7)" :coerce :boolean}
   :where {:ref "EXPR" :desc "find: keep the results satisfying the predicate"}
   :select {:ref "K1,K2" :desc "find: keep only these keys of each result"}
   :limit {:ref "N" :desc "find: at most N results, after ordering"}})

(defn- csv [v] (vec (remove str/blank? (map str/trim (str/split (str v) #",")))))

(defn- parse-in [v]
  (let [k (if (nil? v) :all (keyword (if (= "both" (str v)) "all" (str v))))]
    (if (search/layers k) k
        (cli-args/usage! "find" (str "`--in` needs physical, semantic or all, got `" v "`") {:option "in" :value (str v)}))))

(defn- parse-kinds [v]
  (when (some? v)
    (let [ks (set (map keyword (csv v)))]
      (when (or (empty? ks) (not-every? search/kinds ks))
        (cli-args/usage! "find" (str "`--type` needs one or more of " (str/join ", " (sort (map name search/kinds))) ", got `" v "`")
                {:option "type" :value (str v)}))
      ks)))

(defn- parse-limit [v]
  (cond (nil? v) nil
        (and (integer? v) (not (neg? v))) v
        (and (string? v) (re-matches #"\d+" v)) (parse-long v)
        :else (cli-args/usage! "find" (str "`--limit` needs a non-negative integer, got `" v "`") {:option "limit" :value (str v)})))

(defn- parse-select [v]
  (when (some? v)
    (let [ks (csv v)]
      (when (empty? ks) (cli-args/usage! "find" "`--select` needs one or more keys" {:option "select" :value (str v)}))
      (mapv keyword ks))))

(defn- flag? [v] (boolean (and v (not= "false" (str v)))))

(defn find-command
  "`find TERM …`: data is the vector of results `[{:store :tree :name :model
   :path :kind :matches …}]` (see `kimun.surface.index.search/search`),
   ordered by store name then tree id, cut by `--limit` and projected by
   `--select`. The `--where` expression and the option values are checked
   before the store is opened."
  [term opts {:keys [env cwd]}]
  (when-not (string? term) (cli-args/usage! "find" "`find` needs a TERM (use \"\" to match everything)" {}))
  (let [ast (when (some? (:where opts)) (where/parse (str (:where opts))))
        in (parse-in (:in opts)) kinds (parse-kinds (:type opts))
        limit (parse-limit (:limit opts)) select (parse-select (:select opts))
        regex? (flag? (:regex opts)) fuzzy? (flag? (:fuzzy opts))
        _ (when (and regex? fuzzy?) (cli-args/usage! "find" "`--regex` and `--fuzzy` exclude each other" {:option "regex"}))
        o (cli-store/open! (cli-store/resolve-store opts env cwd))
        found (search/search (cache/federated! o) term
                             {:in in :kinds kinds :regex? regex? :fuzzy? fuzzy? :where ast :global? (flag? (:global opts))})]
    (when (empty? found)
      (err/raise :find/empty (str "nothing found for " (pr-str term))
                 (cond-> {:term term :in (name in) :global (flag? (:global opts))}
                   (:where opts) (assoc :where (str (:where opts)))
                   kinds (assoc :type (vec (sort (map name kinds)))))))
    (out/ok "find" {:store {:name (get-in o [:descriptor :name]) :path (:path o)}
                    :revision (get-in o [:store :head])
                    :data (cond->> found
                            limit (take limit)
                            select (map #(select-keys % select))
                            true vec)})))

(def commands
  "`find` has no subcommands: the only entry is the command itself."
  {"find" find-command})

(defn dispatch
  "Runs `find`: `sub` is the TERM (the first positional), `args` the
   positionals after it (none is accepted), `opts` the parsed options."
  [sub args opts ctx]
  (if (seq args)
    (out/failure :cli/usage (str "`find` takes one TERM, got extra arguments: " (str/join " " args))
                 {:group "find" :extra (vec args)} 6)
    (find-command sub opts ctx)))
