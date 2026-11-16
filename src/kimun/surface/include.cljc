(ns kimun.surface.include
  "The include file of a kimun project (docs/v2/13): the kimun.include.edn a
   repo keeps at its root declaring what of the kimun world it versions in git.
   Pure: parsing and validation, glob matching, the plan of declarations over a
   working tree, and the diff of that plan against git and the store. All I/O —
   reading the file, listing the tree, running git, opening the store — lives
   in kimun.cli.commands.include."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [kimun.kernel.err :as err]))

(def finding-kinds
  "The kinds `diff` reports, always present (empty when clean)."
  [:declared-not-tracked
   :tracked-undeclared
   :declared-missing
   :store-undeclared
   :declared-not-in-store
   :store-tracked])

(defn normalize-path
  "`p` with `\\` rewritten to `/`, the separator every glob and finding uses."
  [p]
  (str/replace (str p) "\\" "/"))

(defn- invalid! [msg data] (err/raise :include/invalid msg (or data {})))

(defn- file-stem
  "The name of `path` as the manifest has it: its base file name with the
   extension dropped (`desk/atoms/cache.md` → `cache`)."
  [path]
  (let [base (last (str/split (normalize-path path) #"/"))]
    (if-let [i (str/last-index-of base ".")] (subs base 0 i) base)))

(defn- regex-escape [c]
  (if (contains? #{\\ \. \[ \] \{ \} \( \) \* \+ \? \^ \$ \|} c) (str "\\" c) (str c)))

(defn glob->regex
  "The regular expression (a string) matching exactly the paths a glob matches:
   `**` spans path separators (`**/` also matches none), `*` and `?` stay inside
   one path segment."
  [glob]
  (let [cs (vec glob) n (count cs)]
    (loop [i 0 out []]
      (if (>= i n)
        (str "\\A" (apply str out) "\\z")
        (let [c (nth cs i)]
          (cond
            (and (= c \*) (< (inc i) n) (= \* (nth cs (inc i))))
            (if (and (< (+ i 2) n) (= \/ (nth cs (+ i 2))))
              (recur (+ i 3) (conj out "(?:.*/)?"))
              (recur (+ i 2) (conj out ".*")))
            (= c \*) (recur (inc i) (conj out "[^/]*"))
            (= c \?) (recur (inc i) (conj out "[^/]"))
            :else (recur (inc i) (conj out (regex-escape c)))))))))

(defn compile-glob
  "The compiled `Pattern` of `glob` (see `glob->regex`), for callers that
   match one glob many times."
  [glob]
  (re-pattern (glob->regex glob)))

(def ^:private allowed-keys
  "The only keys a kimun.include.edn may carry."
  #{:include/version :store :models :documents :force :exclude})

(defn- glob-string? [x] (and (string? x) (not (str/blank? x))))

(defn parse
  "Parses and validates the text of a kimun.include.edn, returning the
   normalised config (`:force` a set, `:exclude` a vector). Raises
   `:include/invalid` (exit 3) for anything the format does not allow."
  [text]
  (let [cfg (try (edn/read-string text)
                 (catch Exception e (invalid! (str "kimun.include.edn is not EDN: " (ex-message e)) {})))]
    (when-not (map? cfg) (invalid! "kimun.include.edn must be an EDN map" {}))
    (let [unknown (sort (remove allowed-keys (keys cfg)))]
      (when (seq unknown)
        (invalid! (str "unknown key(s): " (str/join ", " (map str unknown))) {:unknown (vec unknown)})))
    (when-not (= 1 (:include/version cfg))
      (invalid! (str ":include/version must be 1, got " (pr-str (:include/version cfg))) {:version (:include/version cfg)}))
    (let [store (:store cfg)]
      (when-not (and (map? store) (glob-string? (:path store)) (contains? #{:ignore :track} (:git store)))
        (invalid! ":store must be {:path \"...\" :git :ignore|:track}" (select-keys (or store {}) [:path :git]))))
    (when-not (and (sequential? (:models cfg)) (every? glob-string? (:models cfg)))
      (invalid! ":models must be a vector of glob strings" {}))
    (when-not (and (sequential? (:documents cfg)) (every? map? (:documents cfg)))
      (invalid! ":documents must be a vector of rules" {}))
    (doseq [r (:documents cfg)]
      (when-not (and (glob-string? (:glob r)) (glob-string? (:model r)) (or (nil? (:force r)) (boolean? (:force r))))
        (invalid! "each :documents rule must be {:glob \"...\" :model \"...\"} with an optional boolean :force" {:rule r})))
    (when-not (or (nil? (:force cfg)) (and (coll? (:force cfg)) (every? glob-string? (:force cfg))))
      (invalid! ":force must be a set (or vector) of paths" {}))
    (when-not (or (nil? (:exclude cfg)) (and (coll? (:exclude cfg)) (every? glob-string? (:exclude cfg))))
      (invalid! ":exclude must be a vector of globs" {}))
    (-> cfg
        (update :models #(vec (map normalize-path %)))
        (update :documents #(vec (map (fn [r] (update r :glob normalize-path)) %)))
        (update :force #(set (map normalize-path (or % []))))
        (update :exclude #(vec (map normalize-path (or % [])))))))

(defn managed-globs
  "Every glob of `cfg` that declares a file: the model descriptors and the
   document rules."
  [cfg]
  (vec (concat (:models cfg) (map :glob (:documents cfg)))))

(defn- base-first
  "`paths` ordered so a model descriptor whose `:base` names another descriptor
   comes after it; ties break alphabetically. `bases` maps a path to the set of
   base model names (the caller reads the descriptors). Raises
   `:include/invalid` on a cycle."
  [paths bases]
  (let [names (set (map file-stem paths))
        deps (fn [p] (set (filter names (get bases p #{}))))]
    (loop [done #{} pending (vec (sort paths)) out []]
      (if (empty? pending)
        out
        (let [ready (vec (filter #(every? done (deps %)) pending))]
          (when (empty? ready)
            (invalid! (str "model base cycle among: " (str/join ", " (sort pending))) {}))
          (recur (into done (map file-stem ready)) (vec (remove (set ready) pending)) (into out ready)))))))

(defn plan
  "The declarations of `cfg` over the working tree `files` (paths):
   `{:models [path]` in base-first order (`opts`'s `:model-bases` maps a model
   path to its base model names) `:documents [{:path :model :name :force}]}`.
   Documents keep the order of `files`; a rule claims a file when its glob
   matches first, and `:exclude` globs claim nothing. `:name` is the file stem.
   Raises `:include/conflict` (exit 4) when two declared documents share a
   name."
  ([cfg files] (plan cfg files {}))
  ([cfg files {:keys [model-bases] :or {model-bases {}}}]
   (let [files (vec (distinct (sort (map normalize-path files))))
         models-rx (map compile-glob (:models cfg))
         model-paths (vec (filter (fn [f] (some #(re-matches % f) models-rx)) files))
         ordered (base-first model-paths model-bases)
         exclude-rx (map compile-glob (:exclude cfg))
         excluded? (fn [path] (some #(re-matches % path) exclude-rx))
         rules (map (fn [r] (update r :glob compile-glob)) (:documents cfg))
         docs (vec (for [f files
                         :when (not (excluded? f))
                         :let [rule (first (filter #(re-matches (:glob %) f) rules))]
                         :when rule]
                     {:path f :model (:model rule) :name (file-stem f)
                      :force (or (true? (:force rule)) (contains? (:force cfg) f))}))
         collisions (into (sorted-map)
                          (for [[n ds] (group-by :name docs) :when (> (count ds) 1)]
                            [n (mapv :path ds)]))]
     (when (seq collisions)
       (err/raise :include/conflict
                  (str "document name collision: " (str/join ", " (keys collisions)))
                  {:collisions collisions}))
     {:models ordered :documents docs})))

(defn diff
  "Findings between `cfg`'s plan `p` and the inputs `{:tracked #{path}
   :files [path] :store-documents #{path}}`: `:tracked` are the git-tracked
   paths, `:files` the working-tree paths, `:store-documents` the paths of the
   store's documents. Returns `{:ok? bool :findings {kind [path]}}`, every kind
   of `finding-kinds` always present. `kinds` limits which kinds are computed.
   Paths matched by an `:exclude` glob are explicitly not kimun documents, so a
   tracked excluded path is no finding: `:tracked-undeclared` only reports a
   path that git tracks, falls under a managed glob and is neither declared nor
   excluded."
  ([cfg p inputs] (diff cfg p inputs finding-kinds))
  ([cfg p {:keys [tracked files store-documents]} kinds]
   (let [tracked (set (map normalize-path tracked))
         files (set (map normalize-path files))
         store-docs (set (map normalize-path (remove nil? store-documents)))
         model-paths (set (map normalize-path (:models p)))
         doc-paths (set (map (comp normalize-path :path) (:documents p)))
         declared (into model-paths doc-paths)
         excluded? (fn [path] (some #(re-matches % path) (map compile-glob (:exclude cfg))))
         managed (map compile-glob (managed-globs cfg))
         store-path (normalize-path (get-in cfg [:store :path]))
         under-store? (fn [path] (or (= path store-path) (str/starts-with? path (str store-path "/"))))
         has? (set kinds)
         raw {:declared-not-tracked (sort (remove tracked declared))
              :tracked-undeclared (sort (for [path tracked
                                              :when (and (not (declared path))
                                                         (not (excluded? path))
                                                         (some #(re-matches % path) managed))]
                                          path))
              :declared-missing (sort (remove files declared))
              :store-undeclared (sort (remove declared store-docs))
              :declared-not-in-store (sort (remove store-docs doc-paths))
              :store-tracked (if (= :ignore (get-in cfg [:store :git]))
                               (sort (filter under-store? tracked))
                               [])}
         findings (into {} (map (fn [k] [k (if (has? k) (vec (get raw k)) [])]) finding-kinds))]
     {:ok? (every? empty? (vals findings)) :findings findings})))
