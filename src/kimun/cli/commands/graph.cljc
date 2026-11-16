(ns kimun.cli.commands.graph
  "The `kimun graph` group (v1 `sldb graph`): `get`, `list`, `query`,
   `neighborhood`, `path`, `cycles`, `order`, `components`, `central`,
   `isolated`, `similar`, `snapshot save|load`, `export` and `ingest-sldb`.
   Every read is the pure engine (`kimun.graph.*`) over one graph: the
   view derived from the opened store (`kimun.graph.edges`, predicate links
   included unless `--no-links`) or, with `--snapshot FILE`, a saved snapshot —
   same answer either way (`kimun.graph.store`). Nothing is written
   but the file `snapshot save`, `export -o` or `ingest-sldb -o` names; paths
   in the output are never absolute. Every command is
   `(fn [args opts ctx] envelope)`."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.graph.analysis :as a]
            [kimun.graph.io :as io]
            [kimun.graph.model :as m]
            [kimun.graph.query :as q]
            [kimun.graph.store :as gs]
            [kimun.graph.traversal :as t]
            [kimun.kernel.err :as err]))

;; ---------------------------------------------------------------- options

(defn- usage! [message data] (err/raise :cli/usage message (assoc data :group "graph")))

(defn- require-arg! [args i what sub]
  (or (get args i) (usage! (str "`graph " sub "` needs " what) {:subcommand sub})))

(defn- int-opt
  "Option `k` as an integer >= `least` (`default` when absent)."
  [opts k default least]
  (let [v (get opts k) n (cond (nil? v) default (integer? v) v :else (parse-long (str v)))]
    (if (and n (>= n least))
      n
      (usage! (str "option --" (name k) " needs an integer >= " least ", got " (pr-str v)) {:option k :value v}))))

(defn- choice-opt [opts k allowed default]
  (let [v (get opts k default)]
    (if (some #{v} allowed)
      v
      (usage! (str "option --" (name k) " must be one of " (str/join ", " allowed) ", got " (pr-str v))
              {:option k :value v :allowed (vec allowed)}))))

(defn- many
  "A repeatable option as a vector of strings (comma lists split), or nil."
  [v]
  (let [xs (->> (if (coll? v) v [v]) (remove nil?) (mapcat #(str/split (str %) #",")) (map str/trim) (remove str/blank?) vec)]
    (when (seq xs) xs)))

(defn- relations-of
  "What the analyses walk: `--relation` (repeatable), every relation with
   `--all-relations`, else nil — the authored ones, as in v1."
  [opts]
  (if (:all-relations opts) m/all-relations (many (:relation opts))))

(defn- shown
  "`file` as the output shows it: never absolute — relative to `cwd` when under
   it, else its file name."
  [cwd file]
  (let [p (fs/path file)]
    (cond
      (not (fs/absolute? p)) (str file)
      (fs/starts-with? p (fs/absolutize cwd)) (str (fs/relativize (fs/absolutize cwd) p))
      :else (str (fs/file-name p)))))

(defn- read-json-file [cwd file kind]
  (let [f (fs/path cwd file)]
    (when-not (fs/regular-file? f)
      (err/raise :graph/missing (str "No " kind " file " (shown cwd file)) {:path (shown cwd file)}))
    (try (io/read-json (slurp (str f)))
         (catch #?(:clj Exception :cljs :default) _
           (err/raise :graph/invalid (str (shown cwd file) " is not valid JSON") {:path (shown cwd file)})))))

(defn- write-json-file! [cwd file data]
  (let [f (fs/path cwd file)]
    (when-let [p (fs/parent f)] (fs/create-dirs p))
    (spit (str f) (str (io/write-json data) "\n"))
    (shown cwd file)))

(defn- linked-of
  "The reachable linked stores of opened store `o` as `[{:name :store}]` for
   the federated graph (`kimun.graph.store/from-store`): their opened
   kernel stores under the link names `kimun.cli.store/linked-stores` gives
   them, with its cycle guard. Empty with `--local`."
  [o opts]
  (if (:local opts)
    []
    (mapv (fn [l] {:name (:name l) :store (get-in l [:open :store])})
          (cli-store/linked-stores (:path o) {:transitive? true}))))

(defn- source
  "The graph a command reads: `{:graph g}` from `--snapshot FILE`, else
   `{:graph g :store {:name :path} :revision head}` from the opened store."
  [opts {:keys [env cwd]}]
  (if-let [file (:snapshot opts)]
    {:graph (gs/from-snapshot (read-json-file cwd file "snapshot"))}
    (let [{:keys [descriptor path] s :store :as o} (cli-store/open! (cli-store/resolve-store opts env cwd))]
      {:graph (gs/from-store s {:links? (not (:no-links opts)) :exclude-tags (or (many (:exclude-tag opts)) [])
                                :linked (linked-of o opts)})
       :store {:name (:name descriptor) :path path} :revision (:head s)})))

(defn- answer [command src data] (out/ok command (assoc (select-keys src [:store :revision]) :data data)))

(defn- scored [k pairs] (mapv (fn [[id v]] {:id id k v}) pairs))

;; ---------------------------------------------------------------- reads

(defn get-node
  "`graph get ID`: one node, `{:id :type :semantics :facets :edges}` (`:edges`
   leave it). ID is an `sldb://` id or a `Model:name`; unknown is `graph/missing`."
  [args opts ctx]
  (let [src (source opts ctx)]
    (answer "graph get" src (gs/node (:graph src) (require-arg! args 0 "a node ID" "get")))))

(defn list-nodes
  "`graph list [--type T]`: `{:count :nodes [id]}`, sorted; `--type` (repeatable)
   keeps the nodes of those types."
  [_ opts ctx]
  (let [src (source opts ctx) ids (gs/ids (:graph src) (many (:type opts)))]
    (answer "graph list" src {:count (count ids) :nodes ids})))

(defn query
  "`graph query -f FILE.json`: runs a structured query (the engine's JSON
   language). Data `{:count :nodes [{:id :type :semantics :facets :edges}]}`;
   a malformed query is `graph/bad-query`."
  [_ opts {:keys [cwd] :as ctx}]
  (let [file (or (:file opts) (:f opts) (usage! "`graph query` needs -f FILE.json" {:subcommand "query"}))
        data (read-json-file cwd file "query")
        src (source opts ctx)
        nodes (q/execute (:graph src) data)]
    (answer "graph query" src {:count (count nodes) :nodes nodes})))

(defn neighborhood
  "`graph neighborhood ID [--depth N] [--direction both|incoming|outgoing]
   [--relation R]`: `{:node :depth :direction :nodes [id]}` — the ids within N
   hops (default 1), the start included, sorted."
  [args opts ctx]
  (let [depth (int-opt opts :depth 1 0)
        direction (choice-opt opts :direction ["both" "incoming" "outgoing"] "both")
        src (source opts ctx) g (:graph src)
        id (gs/node-id g (require-arg! args 0 "a node ID" "neighborhood"))
        found (t/neighborhood-by-direction g [id] depth direction :types (or (many (:relation opts)) []))]
    (answer "graph neighborhood" src {:node id :depth depth :direction direction :nodes (vec (sort (conj (set found) id)))})))

(defn path
  "`graph path FROM TO [--directed] [--relation R | --all-relations]`: the
   shortest route, `{:from :to :length :path [id]}`. No route is the failure
   `graph/empty` (\"No path between …\", exit 1); an unknown node `graph/missing`."
  [args opts ctx]
  (let [a0 (require-arg! args 0 "FROM and TO node ids" "path") b0 (require-arg! args 1 "FROM and TO node ids" "path")
        src (source opts ctx) g (:graph src)
        from (gs/node-id g a0) to (gs/node-id g b0)
        route (a/path-between g from to :relations (relations-of opts) :directed (boolean (:directed opts)))]
    (when-not route
      (err/raise :graph/empty (str "No path between " from " and " to) {:from from :to to}))
    (answer "graph path" src {:from from :to to :length (dec (count route)) :path route})))

(defn cycles
  "`graph cycles [--limit N] [--relation R | --all-relations]`: `{:count :cycles [[id]]}`."
  [_ opts ctx]
  (let [limit (int-opt opts :limit 10 1) src (source opts ctx)
        found (a/cycles (:graph src) :relations (relations-of opts) :limit limit)]
    (answer "graph cycles" src {:count (count found) :cycles (mapv vec found)})))

(defn order
  "`graph order [--relation R | --all-relations]`: `{:order [id] :layers [[id]]}`,
   every node after what it points at. A cycle is the failure `graph/cycle`."
  [_ opts ctx]
  (let [src (source opts ctx) layers (a/layers (:graph src) :relations (relations-of opts))]
    (answer "graph order" src {:order (vec (apply concat layers)) :layers (mapv vec layers)})))

(defn components
  "`graph components [--type T] [--relation R | --all-relations]`:
   `{:count :components [[id]]}`, largest first."
  [_ opts ctx]
  (let [src (source opts ctx)
        found (a/components (:graph src) :relations (relations-of opts) :node-types (many (:type opts)))]
    (answer "graph components" src {:count (count found) :components (mapv vec found)})))

(defn central
  "`graph central [--metric pagerank|betweenness|degree|in_degree|out_degree]
   [--limit N] [--type T] [--relation R | --all-relations]`: `{:metric :nodes
   [{:id :score}]}`, best first (default pagerank, 20). `--kind` is v1's name
   for `--metric`."
  [_ opts ctx]
  (let [metric (choice-opt (if (:metric opts) opts (assoc opts :metric (:kind opts "pagerank"))) :metric a/centrality-kinds "pagerank")
        limit (int-opt opts :limit 20 1) src (source opts ctx)]
    (answer "graph central" src
            {:metric metric
             :nodes (scored :score (a/central (:graph src) :kind metric :relations (relations-of opts)
                                              :node-types (many (:type opts)) :limit limit))})))

(defn isolated
  "`graph isolated [--type T] [--relation R | --all-relations]`: `{:count
   :nodes [id]}` — the nodes no edge of the walked relations touches."
  [_ opts ctx]
  (let [src (source opts ctx)
        ids (a/isolated (:graph src) :relations (relations-of opts) :node-types (many (:type opts)))]
    (answer "graph isolated" src {:count (count ids) :nodes ids})))

(defn similar
  "`graph similar ID [--limit N] [--type T] [--relation R]`: `{:node :nodes
   [{:id :shared}]}` — the nodes sharing targets with ID along the relations
   (default `tagged_as`), most shared first (default 10)."
  [args opts ctx]
  (let [limit (int-opt opts :limit 10 1) src (source opts ctx) g (:graph src)
        id (gs/node-id g (require-arg! args 0 "a node ID" "similar"))]
    (answer "graph similar" src
            {:node id
             :nodes (scored :shared (a/similar-to g id :relations (or (many (:relation opts)) ["tagged_as"])
                                                  :limit limit :node-types (many (:type opts))))})))

;; ---------------------------------------------------------------- snapshots

(defn export
  "`graph export [-o FILE]`: the node-link snapshot of the graph (`{\"directed\"
   \"multigraph\" \"graph\" \"nodes\" \"links\"}`), or with `-o` the summary
   `{:path :nodes :edges :types :relations}` of the file written."
  [_ opts {:keys [cwd] :as ctx}]
  (let [src (source opts ctx) g (:graph src) file (or (:output opts) (:o opts))]
    (answer "graph export" src
            (if file (assoc (gs/summary g) :path (write-json-file! cwd file (gs/snapshot g))) (gs/snapshot g)))))

(defn snapshot
  "`graph snapshot save FILE`: writes the node-link snapshot of the graph to
   FILE. `graph snapshot load FILE`: reads a node-link file, a native snapshot
   or a semantic export and reports it; no store is opened. Data of both:
   `{:path :nodes :edges :types :relations}`."
  [args opts {:keys [cwd] :as ctx}]
  (let [action (get args 0)
        file #(or (get args 1) (:output opts) (:input opts) (usage! (str "`graph snapshot " action "` needs a FILE") {:subcommand "snapshot"}))]
    (case action
      "save" (let [f (file) src (source opts ctx) g (:graph src)]
               (answer "graph snapshot save" src (assoc (gs/summary g) :path (write-json-file! cwd f (gs/snapshot g)))))
      "load" (let [f (file) g (gs/from-snapshot (read-json-file cwd f "snapshot"))]
               (answer "graph snapshot load" {} (assoc (gs/summary g) :path (shown cwd f))))
      (usage! "`graph snapshot` needs `save FILE` or `load FILE`" {:subcommand "snapshot" :action action}))))

(defn ingest-sldb
  "`graph ingest-sldb FILE [-o OUT]`: converts a semantic export (contract
   version 1 or 2) into the node-link snapshot — the envelope's data, or with
   `-o` written to OUT with the summary `{:path :contract :nodes :edges :types
   :relations}`. No store is opened; a wrong contract is `graph/bad-export`."
  [args opts {:keys [cwd]}]
  (let [in (or (get args 0) (:input opts) (usage! "`graph ingest-sldb` needs the export FILE" {:subcommand "ingest-sldb"}))
        payload (read-json-file cwd in "semantic export")
        _ (when-not (and (map? payload) (contains? payload "contract"))
            (err/raise :graph/bad-export (str (shown cwd in) " is not a semantic export") {:path (shown cwd in)}))
        g (gs/from-semantic-export payload)
        file (or (:output opts) (:o opts))]
    (answer "graph ingest-sldb" {}
            (if file
              (assoc (gs/summary g) :path (write-json-file! cwd file (gs/snapshot g)) :contract (get payload "contract"))
              (gs/snapshot g)))))

;; ---------------------------------------------------------------- group

(def spec
  "Options of the `graph` group (babashka.cli spec, merged by `kimun.cli.main`).
   `:file`, `:output`, `:type`, `:depth` and `:limit` are names other groups
   declare too, with the same shape; the descriptions here cover both uses."
  {:file {:alias :f :ref "FILE" :desc "Descriptor/template file (models) or JSON query file (graph query)"}
   :output {:alias :o :ref "FILE" :desc "Write the result to FILE instead of the envelope (graph export, graph ingest-sldb)"}
   :input {:alias :i :ref "FILE" :desc "Input file (direct commands; graph snapshot load, graph ingest-sldb)"}
   :type {:ref "TYPE" :desc "Field type (models field-add) or node type to keep (graph list|components|central|isolated|similar)"}
   :depth {:ref "N" :desc "Hops to follow (docs recover; graph neighborhood, default 1)"}
   :limit {:ref "N" :desc "How many results (journal show; graph cycles|central|similar)"}
   :relation {:ref "R" :desc "Relation to walk (repeatable; default: the authored ones)" :coerce [:string]}
   :all-relations {:desc "Walk every relation, derived structure included" :coerce :boolean}
   :direction {:ref "DIR" :desc "graph neighborhood: both (default) | incoming | outgoing"}
   :directed {:desc "graph path: follow edges only the way they point" :coerce :boolean}
   :metric {:ref "KIND" :desc "graph central: pagerank (default) | betweenness | degree | in_degree | out_degree"}
   :kind {:ref "KIND" :desc "v1's name for --metric"}
   :snapshot {:ref "FILE" :desc "Read the graph from this snapshot file instead of the store"}
   :no-links {:desc "Leave the predicate links found in document text out of the graph" :coerce :boolean}
   :local {:desc "Leave the linked stores out of the graph (edges show|node|nodes; graph *)" :coerce :boolean}
   :exclude-tag {:ref "TAG" :desc "Leave out documents carrying this tag (repeatable)" :coerce [:string]}})

(def commands
  "Subcommand name → command fn `(fn [args opts ctx] envelope)`."
  {"get" get-node "list" list-nodes "query" query "neighborhood" neighborhood "path" path
   "cycles" cycles "order" order "components" components "central" central "isolated" isolated
   "similar" similar "snapshot" snapshot "export" export "ingest-sldb" ingest-sldb})

(defn dispatch
  "Runs subcommand `sub` of the `graph` group with the positional tokens that
   follow it; an unknown or missing subcommand is the failure `cli/usage`."
  [sub args opts ctx]
  (if-let [f (get commands sub)]
    (f (vec args) opts ctx)
    (out/failure :cli/usage
                 (str (if sub (str "unknown subcommand `graph " sub "`") "missing subcommand for `graph`")
                      "; expected one of: " (str/join ", " (sort (keys commands))))
                 {:group "graph" :subcommand sub :known (vec (sort (keys commands)))}
                 6)))
