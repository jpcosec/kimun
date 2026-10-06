(ns kimun.cli.commands.edges
  "The `kimun edges` group (v1 `sldb edges`): `show`, `check`, `node`,
   `nodes`, `relate`, `unrelate`, `types`, `type-define`, `init` and
   `rebuild`. The graph is the derived view `kimun.surface.edges` computes from
   the opened store at its head — nothing is indexed on disk, so `init` and
   `rebuild` only report. Authored relations and relation types are written as
   `TransactionPlan`s through `sldb.kernel.store/commit!`. Predicate links
   found in document text count as edges unless `--no-links` is given. Every
   command is `(fn [args opts ctx] envelope)`."
  (:require [clojure.string :as str]
            [kimun.cli.commands.docs :as cdocs]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.edges :as edges]
            [sldb.host.default :as host]
            [sldb.kernel.err :as err]
            [sldb.kernel.ports :as ports]
            [sldb.kernel.store :as store]))

(defn- open [opts {:keys [env cwd]}] (cli-store/open! (cli-store/resolve-store opts env cwd)))
(defn- store-ref [{:keys [descriptor path]}] {:name (:name descriptor) :path path})

(defn- usage! [sub message data]
  (err/raise :cli/usage message (merge {:group "edges" :subcommand sub} data)))

(defn- require-arg! [args i what sub]
  (or (get args i) (usage! sub (str "`edges " sub "` needs " what) {})))

(defn- text-opt
  "Option `k` as a non-blank string or nil; anything else is `cli/usage`."
  [opts k sub]
  (let [v (get opts k)]
    (cond (nil? v) nil
          (and (string? v) (not (str/blank? v))) v
          :else (usage! sub (str "option --" (name k) " needs a value") {:option k}))))

(defn- require-opt! [opts k sub]
  (or (text-opt opts k sub) (usage! sub (str "`edges " sub "` needs --" (name k)) {:option k})))

(defn- flag
  "Boolean option `k`: true/false or the strings \"true\"/\"false\"."
  [opts k sub]
  (let [v (get opts k)]
    (cond (or (nil? v) (false? v) (= "false" v)) false
          (or (true? v) (= "true" v)) true
          :else (usage! sub (str "option --" (name k) " is a flag, got `" v "`") {:option k :value v}))))

(defn- choice [opts k allowed sub]
  (when-let [v (text-opt opts k sub)]
    (if (contains? allowed v)
      v
      (usage! sub (str "option --" (name k) " must be one of " (str/join "|" (sort allowed)) ", got `" v "`") {:option k :value v}))))

(defn- csv [opts k sub]
  (when-let [v (text-opt opts k sub)] (vec (remove str/blank? (map str/trim (str/split v #","))))))

(defn- index-opts [opts sub]
  (let [x (:exclude-tag opts)]
    {:links? (not (flag opts :no-links sub))
     :exclude-tags (cond (nil? x) [] (string? x) [x] (and (sequential? x) (every? string? x)) (vec x)
                         :else (usage! sub "option --exclude-tag needs a value" {:option :exclude-tag}))}))

(defn- load-index [opts ctx sub]
  (let [o (open opts ctx)] (assoc o :index (edges/index (:store o) (index-opts opts sub)))))

(defn- known! [g id]
  (let [nid (edges/as-node-id id)]
    (if (get-in g [:nodes nid]) nid (err/raise :edges/missing (str "no node '" nid "' in the graph") {:node nid}))))

(defn- edge-row
  "An edge in v1's wire shape; an authored relation carries its anchor state."
  [{:keys [from to type metadata]}]
  (cond-> {:source from :target to :relation type :metadata metadata}
    (= "authored" (get metadata "origin")) (assoc :anchor (get metadata "anchor"))))

(defn- relation-row [{:keys [edge relation actor state from to]}]
  {:edge edge :relation relation :actor actor :state (name state)
   :from (update from :state name) :to (update to :state name)})

(defn- doc-token
  "A document endpoint as `kimun.surface.edges` plans take it: the `Model:name`
   of an `sldb://document/…` id, anything else unchanged."
  [id]
  (let [p "sldb://document/"] (if (str/starts-with? id p) (subs id (count p)) id)))

(defn show
  "`edges show [ID | --from ID | --to ID] [--relation R] [--no-links]`: the
   edges leaving ID (`--from`, or the bare ID as in v1), arriving at it
   (`--to`), or all of them. Data: `{:from :to :relation :links :count :edges
   [{:source :target :relation :metadata [:anchor]}] :authored […]}` —
   `:authored` lists the authored relations in scope with their anchor state,
   including those an untracked endpoint took out of the graph. An unknown
   node is `edges/missing`."
  [args opts ctx]
  (let [{s :store i :index :as o} (load-index opts ctx "show") g (:graph i)
        from (or (text-opt opts :from "show") (get args 0)) to (text-opt opts :to "show") rel (text-opt opts :relation "show")
        _ (when (and from to) (usage! "show" "`edges show` takes --from or --to, not both" {}))
        from (some->> from (known! g)) to (some->> to (known! g))
        es (cond from (edges/edges-from g from rel) to (edges/edges-to g to rel)
                 :else (filterv #(or (nil? rel) (= rel (:type %))) (:edges g)))
        end? (fn [id a] (or (nil? id) (= id (some-> (:document a) edges/doc-id))))
        authored (filterv #(and (or (nil? rel) (= rel (:relation %))) (end? from (:from %)) (end? to (:to %))) (:authored i))]
    (out/ok "edges show"
            {:store (store-ref o) :revision (:head s)
             :data {:from from :to to :relation rel :links (boolean (:links? (index-opts opts "show")))
                    :count (count es) :edges (mapv edge-row es) :authored (mapv relation-row authored)}})))

(defn- counts [i] {:nodes (count (get-in i [:graph :nodes])) :edges (count (get-in i [:graph :edges])) :authored (count (:authored i))})

(defn check
  "`edges check [--exclude-tag T]… [--no-links]`: every edge against its
   relation type, plus the anchor state of the authored relations. Clean, the
   data is `{:ok true :errors [] :violations [] :anchors [] :counts}`;
   otherwise the failure `edges/invalid` (exit 3; v1 exited 1) carries the same
   report: `:errors` (v1's sentences), `:violations` (`{:kind :message …}`)
   and `:anchors` (the authored relations that are not intact)."
  [_args opts ctx]
  (let [{s :store i :index :as o} (load-index opts ctx "check")
        r (edges/check i)
        report {:ok (boolean (and (:ok r) (empty? (:anchors r)))) :errors (:errors r)
                :violations (mapv #(cond-> (update % :kind name) (map? (:edge %)) (update :edge edge-row)) (:violations r))
                :anchors (mapv relation-row (:anchors r)) :counts (counts i) :revision (:head s)}]
    (if (:ok report)
      (out/ok "edges check" {:store (store-ref o) :revision (:head s) :data (dissoc report :revision)})
      (err/raise :edges/invalid
                 (str (count (:errors report)) " error(s), " (count (:anchors report)) " authored relation(s) not intact")
                 report))))

(defn node
  "`edges node ID`: one node with its facets. Data: `{:id :type :semantics
   :facets :out :in}` (`:out`/`:in` count its edges); `edges/missing` when the
   graph has no such node."
  [args opts ctx]
  (let [{s :store i :index :as o} (load-index opts ctx "node") g (:graph i)
        id (known! g (require-arg! args 0 "a node ID" "node"))]
    (out/ok "edges node"
            {:store (store-ref o) :revision (:head s)
             :data (assoc (edges/node g id) :out (count (edges/edges-from g id)) :in (count (edges/edges-to g id)))})))

(defn nodes
  "`edges nodes [--type T]`: the nodes of type T in full (`edges node` shape),
   or without `--type` every node as `{:id :type}`. Data: `{:type :count
   :types {type count} :nodes […]}`."
  [_args opts ctx]
  (let [{s :store i :index :as o} (load-index opts ctx "nodes") g (:graph i)
        t (text-opt opts :type "nodes")
        ns' (if t (edges/nodes-of-type g t) (mapv (fn [[id n]] {:id id :type (:type n)}) (sort-by key (:nodes g))))]
    (out/ok "edges nodes"
            {:store (store-ref o) :revision (:head s)
             :data {:type t :count (count ns') :types (into (sorted-map) (frequencies (map :type (vals (:nodes g))))) :nodes ns'}})))

(defn- commit
  "Builds a plan with `build` over the opened store and commits it when it
   changes anything: `{:opened :store :revision :changed}`."
  [build arg opts ctx]
  (let [{:keys [backend] s :store :as o} (open opts ctx)
        {:keys [changed plan]} (build host/host s arg {:actor (:actor opts) :timestamp (cdocs/timestamp)
                                                       :tree-id (ports/ulid (:ids host/host))})
        r (when changed (store/commit! backend s plan))]
    {:opened o :store (if r (:store r) s) :revision (if r (:revision-id r) (:head s)) :changed (boolean changed)}))

(defn- relation-args [args opts sub]
  {:from (doc-token (require-arg! args 0 "a FROM document" sub)) :to (doc-token (require-arg! args 1 "a TO document" sub))
   :relation (require-opt! opts :relation sub)})

(defn- names?
  "Whether `token` (`Model:name` or a bare name) names the document `export-id`."
  [token export-id]
  (boolean (and export-id (or (= token export-id) (= token (last (str/split export-id #":" 2)))))))

(defn relate
  "`edges relate FROM TO --relation R`: authors relation R between two tracked
   documents (`Model:name`, a name or `sldb://document/…`) as a kernel edge.
   Data: `{:changed :relation :from :to :anchor}`; an undefined relation type
   or an untracked document is `edges/missing`."
  [args opts ctx]
  (let [{:keys [from to relation] :as a} (relation-args args opts "relate")
        {:keys [opened changed revision] s :store} (commit edges/relate-plan a opts ctx)
        hit (first (filter #(and (= relation (:relation %)) (names? from (get-in % [:from :document])) (names? to (get-in % [:to :document])))
                           (edges/authored s)))]
    (out/ok "edges relate"
            {:store (store-ref opened) :revision revision
             :data {:changed changed :relation relation :from (or (get-in hit [:from :document]) from)
                    :to (or (get-in hit [:to :document]) to) :anchor (some-> (:state hit) name)}})))

(defn unrelate
  "`edges unrelate FROM TO --relation R`: removes that authored relation; an
   endpoint may be the root node id `edges show` reports for an orphan. Data:
   `{:changed :relation :from :to}`; `edges/missing` when none stands."
  [args opts ctx]
  (let [a (relation-args args opts "unrelate")
        {:keys [opened changed revision]} (commit edges/unrelate-plan a opts ctx)]
    (out/ok "edges unrelate" {:store (store-ref opened) :revision revision :data (assoc a :changed changed)})))

(defn- all-types [s] (merge edges/builtin-relation-types (edges/stored-relation-types s)))

(defn types
  "`edges types`: the relation types, builtin and defined. Data: `{:count
   :types [{\"name\" \"direction\" \"cardinality\" \"axis\" \"source_types\"
   \"target_types\" \"condition\" \"description\" \"builtin\"}]}` by name."
  [_args opts ctx]
  (let [o (open opts ctx) s (:store o) ts (mapv #(assoc % "builtin" (boolean (get % "builtin"))) (vals (all-types s)))]
    (out/ok "edges types" {:store (store-ref o) :revision (:head s) :data {:count (count ts) :types ts}})))

(defn type-define
  "`edges type-define NAME [--direction directed|undirected] [--cardinality
   one_to_one|one_to_many|many_to_one|many_to_many] [--axis A] [--source-types
   a,b] [--target-types a,b] [--condition C] [--description D]`: defines or
   redefines a relation type. Data: `{:changed :type}`; a builtin name is
   `edges/conflict`, a bad name `edges/invalid`, a bad option `cli/usage`."
  [args opts ctx]
  (let [sub "type-define" n (require-arg! args 0 "a relation type NAME" sub)
        t (into {:name n}
                (remove (comp nil? val))
                {:direction (choice opts :direction #{"directed" "undirected"} sub)
                 :cardinality (choice opts :cardinality #{"one_to_one" "one_to_many" "many_to_one" "many_to_many"} sub)
                 :axis (some-> (text-opt opts :axis sub) str/upper-case)
                 :source-types (csv opts :source-types sub) :target-types (csv opts :target-types sub)
                 :condition (text-opt opts :condition sub) :description (text-opt opts :description sub)})
        {:keys [opened changed revision] s :store} (commit edges/define-relation-type-plan t opts ctx)]
    (out/ok "edges type-define"
            {:store (store-ref opened) :revision revision
             :data {:changed changed :type (get (edges/stored-relation-types s) n)}})))

(defn- materialised? [s]
  (boolean (some (fn [[_ t]] (let [d (:descriptor t)] (and (= :taxonomy (:kind d)) (= edges/tree-name (:name d))))) (:trees s))))

(defn- derived
  "The envelope of a command that has nothing to write because the graph is derived."
  [command message opts ctx]
  (let [{s :store i :index :as o} (load-index opts ctx (last (str/split command #" ")))]
    (out/ok command
            {:store (store-ref o) :revision (:head s)
             :data (merge {:changed false :derived true :message message :problems (count (:problems i))
                           :relations-tree (materialised? s) :predicates (count (edges/predicates s))
                           :relation-types (count (all-types s))}
                          (counts i))})))

(defn init
  "`edges init`: a reported no-op. v1 registered its relation models here; in
   kimun the builtin relation types and the default predicates are readable
   from any store, and the `relations` tree is created by the first write
   (`predicates add`, `edges type-define`). Data as `edges rebuild`."
  [_args opts ctx]
  (derived "edges init"
           "nothing to initialise: builtin relation types and default predicates are always readable; the relations tree is created by the first write"
           opts ctx))

(defn rebuild
  "`edges rebuild`: a reported no-op. The graph is derived from the store at
   its head on every read; there is no index to refresh. Data: `{:changed
   false :derived true :message :nodes :edges :authored :problems
   :relations-tree :predicates :relation-types}`, the counts at this revision."
  [_args opts ctx]
  (derived "edges rebuild" "nothing to rebuild: the graph is derived from the store at its head on every read" opts ctx))

(def spec
  "Options of the `edges` group (babashka.cli spec, merged by `kimun.cli.main`)."
  {:from {:ref "ID" :desc "Edges leaving this node: sldb://… or Model:name (edges show)"}
   :to {:ref "ID" :desc "Edges arriving at this node (edges show)"}
   :relation {:ref "R" :desc "Relation name (edges show, relate, unrelate)"}
   :type {:ref "T" :desc "Node type (edges nodes)"}
   :no-links {:desc "Leave out the predicate links found in document text" :coerce :boolean}
   :exclude-tag {:ref "TAG" :desc "Documents carrying TAG contribute only their tags (repeatable)" :coerce [:string]}
   :direction {:ref "D" :desc "directed | undirected (edges type-define)"}
   :cardinality {:ref "C" :desc "one_to_one | one_to_many | many_to_one | many_to_many (edges type-define)"}
   :axis {:ref "A" :desc "Axis of a relation type or predicate"}
   :source-types {:ref "A,B" :desc "Node types allowed as source (edges type-define)"}
   :target-types {:ref "A,B" :desc "Node types allowed as target (edges type-define)"}
   :condition {:ref "TEXT" :desc "Condition of a relation type (edges type-define)"}
   :description {:ref "TEXT" :desc "Description of a relation type or predicate"}})

(def commands
  "Subcommand name → command fn `(fn [args opts ctx] envelope)`."
  {"show" show "check" check "node" node "nodes" nodes "relate" relate "unrelate" unrelate
   "types" types "type-define" type-define "init" init "rebuild" rebuild})

(defn dispatch
  "Runs subcommand `sub` of the `edges` group with the positional tokens that
   follow it; an unknown or missing subcommand is the failure `cli/usage`."
  [sub args opts ctx]
  (if-let [f (get commands sub)]
    (f (vec args) opts ctx)
    (out/failure :cli/usage
                 (str (if sub (str "unknown subcommand `edges " sub "`") "missing subcommand for `edges`")
                      "; expected one of: " (str/join ", " (sort (keys commands))))
                 {:group "edges" :subcommand sub :known (vec (sort (keys commands)))}
                 6)))
