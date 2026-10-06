;; One row of the scaling table for an existing gen_store project.
;; Usage: bb scripts/bench/scale_point.clj <dir> [k]
;; Prints: revisions, open_ms (store/open in this process, median of 5 fresh backends),
;; commit_ms (median of k one-document commits through create-plan + commit!, each on the
;; store value the previous one returned), bytes_per_commit (object files created + log
;; growth, mean over the k commits; heads.edn is replaced, not added) and heads_edn bytes.
;; Writes k documents into the store and their files into <dir> (bench-extra-<i>.md).
(require '[babashka.fs :as fs] '[clojure.string :as str]
         '[kimun.cli.store :as cli-store] '[kimun.surface.docs :as tdocs]
         '[sldb.host.default :as host] '[sldb.kernel.ports :as ports] '[sldb.kernel.store :as store])

(defn- median [xs] (nth (sort xs) (quot (count xs) 2)))
(defn- files [d] (into {} (map (fn [f] [(str (fs/file-name f)) (fs/size f)])) (fs/list-dir d)))

(let [[dir k-str] *command-line-args*
      k (parse-long (or k-str "21"))
      sdir (str (fs/path (fs/absolutize dir) ".kimun"))
      odir (str (fs/path sdir "objects"))
      log (str (fs/path sdir "log.edn"))
      revisions (count (str/split-lines (slurp log)))
      open-ms (median (for [_ (range 5)]
                        (let [b (cli-store/backend sdir) t0 (System/nanoTime)]
                          (store/open b host/host)
                          (/ (- (System/nanoTime) t0) 1e6))))
      {:keys [backend store]} (cli-store/open! sdir)
      actor (->> (:capabilities (store/read-descriptor backend)) keys (remove #(str/starts-with? % "kimun/")) sort first)
      ulid #(ports/ulid (:ids host/host))
      before (files odir) log0 (fs/size log)
      payload (fn [i] {"title" (str "Extra " i) "area" "kernel" "status" "draft" "owner" "ana" "priority" "1" "tags" ["a" "b"]
                       "overview" "One paragraph." "details" "Two paragraph." "decisions" "Three." "references" "None."})
      times (loop [i 0 s store acc []]
              (if (= i k)
                acc
                (let [nm (str "bench-extra-" i)
                      t0 (System/nanoTime)
                      {:keys [plan text]} (tdocs/create-plan host/host s "BenchNote" nm (payload i)
                                                        {:doc (ulid) :tree-id (ulid) :path (str nm ".md") :actor actor :timestamp "2026-10-06T00:00:00.000Z"})
                      r (store/commit! backend s plan)
                      _ (spit (str (fs/path (fs/absolutize dir) (str nm ".md"))) text)]
                  (recur (inc i) (:store r) (conj acc (/ (- (System/nanoTime) t0) 1e6))))))
      after (files odir)
      new-bytes (reduce + (for [[f n] after :when (not (contains? before f))] n))]
  (println (format "revisions %d open_ms %.1f commit_ms %.1f bytes_per_commit %.0f objects_per_commit %.1f heads_edn %d"
                   revisions open-ms (median times) (double (/ (+ new-bytes (- (fs/size log) log0)) k))
                   (double (/ (- (count after) (count before)) k)) (fs/size (fs/path sdir "heads.edn")))))
