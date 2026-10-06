;; Synthetic kimun store generator for the kernel scaling benchmark.
;; Usage (from the worktree root):  bb -cp src:resources scripts/bench/gen_store.clj <dir> <n-docs>
;; (plain `bb scripts/bench/gen_store.clj ...` also works: bb.edn :paths carry the classpath.)
;; Creates a fresh store at <dir>/.kimun with ONE model (BenchNote) and N typed documents,
;; one commit per document, reusing the store value each commit returns (no reopen).
;; Only public store/commit functions of the CLI layer are used.
(require '[babashka.fs :as fs]
         '[babashka.process]
         '[clojure.string :as str]
         '[kimun.cli.commands.docs :as docs]
         '[kimun.cli.store :as cli-store]
         '[kimun.surface.docs :as tdocs]
         '[kimun.surface.models.store :as mstore]
         '[sldb.host.default :as host]
         '[sldb.kernel.ports :as ports]
         '[sldb.kernel.store :as store])

(def model
  {:model/name "BenchNote" :model/version 1 :base []
   :fields [{:name "title" :type :string :required true :description "Title."}
            {:name "area" :type :string :required true :description "Area."}
            {:name "status" :type :string :required true :description "Status."}
            {:name "owner" :type :string :required true :description "Owner."}
            {:name "priority" :type :string :required true :description "Priority."}
            {:name "tags" :type [:list :string] :required true :description "Tags."}
            {:name "overview" :type :string :required true :description "Overview paragraph."}
            {:name "details" :type :string :required true :description "Details paragraph."}
            {:name "decisions" :type :string :required true :description "Decisions paragraph."}
            {:name "references" :type :string :required true :description "References paragraph with [[links]]."}]
   :template (str "---\narea: ⸢rev•area⸥\nstatus: ⸢rev•status⸥\nowner: ⸢rev•owner⸥\npriority: ⸢rev•priority⸥\n---\n\n# ⸢rev•title⸥\n\n"
                  "## Overview\n\n⸢rev•overview⸥\n\n## Details\n\n⸢rev•details⸥\n\n"
                  "## Decisions\n\n⸢rev•decisions⸥\n\n## Tags\n\n- ⸢rev,list•tags⸥\n\n## References\n\n⸢rev•references⸥\n")})

(def words (vec (str/split "store node tree edge commit revision schema model field index replay snapshot object hash log engine surface graph query render section anchor policy token reader writer plan batch merge branch window cursor ledger digest" #" ")))
(defn- w [r n] (str/join " " (repeatedly n #(words (.nextInt r (count words))))))
(defn- sentence [r] (let [s (w r (+ 8 (.nextInt r 8)))] (str (str/upper-case (subs s 0 1)) (subs s 1) ".")))
(defn- para [r] (str/join " " (repeatedly (+ 2 (.nextInt r 3)) #(sentence r))))
(defn- dname [i] (format "note-%05d" i))

(defn- payload [r i]
  (let [k (if (zero? i) 0 (inc (.nextInt r (min 3 i))))
        refs (distinct (repeatedly k #(dname (.nextInt r i))))]
    {"title" (str "Note " i ": " (w r 3))
     "area" (["kernel" "surface" "cli" "docs" "index"] (.nextInt r 5))
     "status" (["draft" "review" "done"] (.nextInt r 3))
     "owner" (["ana" "luis" "mia" "tomas"] (.nextInt r 4))
     "priority" (str (.nextInt r 5))
     "tags" (vec (distinct (repeatedly 3 #(words (.nextInt r (count words))))))
     "overview" (para r) "details" (para r) "decisions" (para r)
     "references" (str (para r) (when (seq refs) (str " See " (str/join ", " (map #(str "[[" % "]]") refs)) ".")))}))

(defn- du-sb [dir] (-> (babashka.process/shell {:out :string} "du" "-sb" (str dir)) :out (str/split #"\s+") first))

(let [[dir n-str] *command-line-args*
      n (parse-long (or n-str "0"))
      _ (when-not (and dir (pos? n)) (println "usage: gen_store.clj <dir> <n-docs>") (System/exit 2))
      dir (str (fs/absolutize dir))
      sdir (str (fs/path dir ".kimun"))
      actor (str "human/" (or (System/getenv "USER") "bench"))
      ts (fn [i] (format "2026-01-01T%02d:%02d:%02d.000Z" (mod (quot i 3600) 24) (mod (quot i 60) 60) (mod i 60)))
      _ (fs/create-dirs dir)
      _ (cli-store/init! sdir {:name "bench" :actor actor})
      {:keys [backend store]} (cli-store/open! sdir)
      ulid #(ports/ulid (:ids host/host))
      def-r (mstore/define-plan host/host store model {:actor actor :timestamp (ts 0) :tree-id (ulid)})
      store (:store (store/commit! backend store (:plan def-r)))
      t0 (System/nanoTime)]
  (loop [i 0 store store t-last t0]
    (if (= i n)
      (let [total (/ (- (System/nanoTime) t0) 1e9)
            lines (count (str/split-lines (slurp (str (fs/path sdir "log.edn")))))
            objs (count (filter fs/regular-file? (file-seq (fs/file (str (fs/path sdir "objects"))))))]
        (println (format "total_seconds %.1f" total))
        (println "log_lines" lines)
        (println "objects" objs)
        (println "bytes_kimun" (du-sb sdir))
        (println "first_doc" (dname 0) "field" "owner"))
      (let [name (dname i)
            {:keys [plan text]} (tdocs/create-plan host/host store "BenchNote" name (payload (java.util.Random. (+ 1000 i)) i)
                                              {:doc (ulid) :tree-id (ulid) :path (str name ".md") :actor actor :timestamp (ts (inc i))})
            res (store/commit! backend store plan)
            _ (spit (str (fs/path dir (str name ".md"))) text)
            done (inc i)
            now (System/nanoTime)]
        (when (zero? (mod done 100))
          (println (format "docs %d elapsed_s %.1f ms_per_commit_last100 %.0f" done (/ (- now t0) 1e9) (/ (- now t-last) 1e6 100))))
        (recur done (:store res) (if (zero? (mod done 100)) now t-last))))))
