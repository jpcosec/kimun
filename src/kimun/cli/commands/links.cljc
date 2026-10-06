(ns kimun.cli.commands.links
  "`docs recover` and `docs compose` (parity with sldb v1 `LinkCLI`): the pure
   links core `kimun.surface.links` run against a real store and real files.
   The root document is a tracked name or a file path; a link target resolves,
   in v1's precedence, to a tracked document name, else a file beside the
   document holding the link, else a file under the project root (the parent of
   the `.kimun` directory — never the process cwd). Tracked documents are read
   from the store, untracked files from disk. Every command is
   `(fn [args opts ctx] envelope)`; with no store at all, file paths still work."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [kimun.cli.commands.direct :as direct]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.links :as links]
            [sldb.kernel.err :as err]))

(def spec
  "Options added by `docs recover` and `docs compose` (babashka.cli spec).
   `:output` is the definition `direct` already uses."
  (merge (select-keys direct/spec [:output])
         {:depth {:ref "N" :desc "recover: follow resolved links N levels deep (default 1)"}
          :links-only {:desc "recover: print only the sorted distinct targets" :coerce :boolean}
          :include-transclusions {:desc "recover: accepted and ignored, as in v1" :coerce :boolean}}))

(def default-predicates
  "Predicate -> 5W1H axis of a fresh v1 store (`sldb.store.predicates`); kimun
   has no store-level predicate registry yet, so these are the axes reported."
  {"is_solved_by" "HOW" "implements" "HOW" "mathematically_proves" "WHY"
   "explains_failure_of" "WHY" "grounded_by" "PROVENANCE" "restricts" "WHEN_WHERE"
   "defines" "WHAT"})

(defn- usage! [msg data] (err/raise :cli/usage msg (merge {:group "docs"} data)))

(defn- absolute [cwd p] (str (fs/normalize (fs/absolutize (fs/path cwd p)))))

(defn- file? [p] (try (fs/regular-file? p) (catch Exception _ false)))

(defn- depth-of
  "The `--depth` value as a positive integer (default 1); anything else is `:cli/usage`."
  [opts]
  (let [v (:depth opts)
        n (cond (nil? v) 1
                (integer? v) v
                (and (string? v) (re-matches #"\d+" v)) (parse-long v)
                :else nil)]
    (if (and n (pos? n))
      n
      (usage! (str "`--depth` needs a positive integer, got `" v "`") {:subcommand "recover" :depth (str v)}))))

(defn- project
  "`{:root :by-name :by-path}`: the project root, the tracked documents' absolute
   paths by name, and their stored sources by absolute path. Without a store the
   root is `cwd` and nothing is tracked."
  [opts {:keys [env cwd]}]
  (if-let [dir (cli-store/resolve-store opts env cwd)]
    (let [{:keys [store]} (cli-store/open! dir)
          root (str (fs/parent (fs/absolutize dir)))
          docs (map #(assoc % :abs (absolute root (:path %))) (tdocs/documents store))]
      {:root root
       :by-name (into {} (map (juxt :name :abs)) docs)
       :by-path (into {} (map (juxt :abs :source)) docs)})
    {:root (str (fs/absolutize cwd)) :by-name {} :by-path {}}))

(defn- lookup-fn
  "Precedence of v1: tracked name, then beside the linking document, then under
   the project root. Only the first hit is returned."
  [{:keys [root by-name]}]
  (fn [{:keys [target from]}]
    (let [beside (try (absolute (fs/parent from) target) (catch Exception _ nil))
          under  (try (absolute root target) (catch Exception _ nil))]
      (cond (contains? by-name target) [{:path (get by-name target) :source :store}]
            (and beside (file? beside)) [{:path beside :source :path}]
            (and under (file? under)) [{:path under :source :path}]
            :else []))))

(defn- content-fn [{:keys [by-path]}]
  (fn [path] (if (contains? by-path path) (get by-path path) (slurp path))))

(defn- root-document
  "The absolute path of the root document `ref`: an existing file (relative to
   `cwd`), else a tracked name; otherwise `:links/missing`."
  [ref {:keys [by-name]} cwd]
  (let [f (absolute cwd ref)]
    (cond (file? f) f
          (contains? by-name ref) (get by-name ref)
          :else (err/raise :links/missing (str "no tracked document or file named `" ref "`") {:document ref}))))

(defn- setup [sub args opts {:keys [cwd] :as ctx}]
  (let [ref (or (first args) (usage! (str "`docs " sub "` needs a document NAME or file path") {:subcommand sub}))
        proj (project opts ctx)]
    {:path (root-document ref proj cwd) :proj proj}))

(defn- result
  "`ok` when nothing is unresolved, else the failure `links/missing` (exit 1, as
   v1) whose error data is the whole `data`."
  [command {:keys [descriptor]} data unresolved ok-data]
  (if (seq unresolved)
    (out/failure :links/missing (str (count unresolved) " unresolved link(s): " (str/join ", " unresolved)) data 1)
    (out/ok command {:store (when descriptor {:name (:name descriptor)}) :data ok-data})))

(defn- link-line [{:keys [kind target resolved predicate w5h1-type]}]
  (let [k (str/replace (name kind) "-" "_") ok (if resolved "ok" "FAIL")]
    (if predicate
      (str k ": [" predicate "]" (when w5h1-type (str "[" w5h1-type "]")) " " target " [" ok "]")
      (str k ": " target " [" ok "]"))))

(defn recover
  "`docs recover DOC [--depth N] [--links-only]`: the links of DOC (a tracked
   name or a file path) and where they resolve. Data: the core's report
   `{:root :path :links [{:target :kind :resolved :path :source :predicate
   :w5h1-type …}] :unresolved [target …]}`; with `--format text` one
   `kind: target [ok|FAIL]` line per link (v1); with `--links-only` the sorted
   distinct targets. As in v1, unresolved links make the command fail with
   `links/missing` (exit 1), the report in the error data."
  [args opts ctx]
  (let [d (depth-of opts)
        {:keys [path proj]} (setup "recover" args opts ctx)
        report (links/recover-links path {:lookup (lookup-fn proj) :content (content-fn proj) :depth d
                                          :predicate-types default-predicates})
        targets (vec (sort (distinct (map :target (:links report)))))
        shaped (cond (:links-only opts) targets
                     (= "text" (:format opts)) (mapv link-line (:links report))
                     :else report)]
    (result "docs recover" {} (if (:links-only opts) {:unresolved (:unresolved report) :targets targets} report)
            (:unresolved report) shaped)))

(defn- write-output! [cwd o markdown]
  (let [f (absolute cwd o)]
    (some-> (fs/parent f) fs/create-dirs)
    (spit f (str markdown "\n"))
    f))

(defn compose
  "`docs compose DOC [-o FILE|-]`: DOC with every resolved `![[transclusion]]`
   replaced by its composed Markdown. Data: the core's result `{:root :path
   :markdown :transclusions [target …] :unresolved [target …]}`; with `--format
   text|md`, or `-o -`, the Markdown itself as a string; with `-o FILE` the
   Markdown is written there (plus a newline) and the data is the result with
   `:output` in place of `:markdown`. An unresolved transclusion fails with
   `links/missing` (exit 1, as v1) after the output was produced."
  [args opts {:keys [cwd] :as ctx}]
  (let [{:keys [path proj]} (setup "compose" args opts ctx)
        res (links/compose-document path {:lookup (lookup-fn proj) :content (content-fn proj)})
        o (:output opts)
        file (when (and o (not= "-" o)) (write-output! cwd o (:markdown res)))
        shaped (cond file (-> res (dissoc :markdown) (assoc :output file))
                     (or (= "-" o) (contains? #{"text" "md"} (:format opts))) (:markdown res)
                     :else res)]
    (result "docs compose" {} res (:unresolved res) shaped)))

(def commands
  "Subcommand name → command fn, to be merged into the `docs` group."
  {"recover" recover "compose" compose})
