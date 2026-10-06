;; Equivalence run: migrates a real sldb v1 store into a temporary kimun store
;; and compares, document by document, with what v1 itself recorded
;; (scripts/parity/record_v1_store.py). Writes runs/parity/migrate-<date>.{edn,md}.
;;
;;   PYTHONDONTWRITEBYTECODE=1 bb scripts/parity/compare_store.clj \
;;     [--v1 PROJECT] [--recorded DIR] [--out runs/parity] [--work DIR] [--no-perf]
;;
;; The v1 project is never written: the migration runs in a temporary project
;; whose entries are symlinks to the originals, with its own real `.kimun/`.
(require '[babashka.cli :as cli] '[babashka.fs :as fs] '[babashka.process :as p] '[cheshire.core :as json]
         '[clojure.pprint :as pp] '[clojure.string :as str]
         '[kimun.cli.commands.migrate :as migrate] '[kimun.cli.store :as cli-store] '[kimun.surface.docs :as tdocs]
         '[sldb.host.default :as host] '[sldb.kernel.store :as store])

(def opts (cli/parse-opts *command-line-args* {:coerce {:no-perf :boolean}}))
(def v1 (str (fs/absolutize (or (:v1 opts) "/home/jp/proyectos/hum-ecosystem/tools/sldb"))))
(def recorded (str (or (:recorded opts) "/tmp/kimun-parity/v1-store")))
(def out-dir (str (or (:out opts) "runs/parity")))
(def repo (str (fs/absolutize ".")))
(when-not (System/getenv "PYTHONDONTWRITEBYTECODE")
  (println "set PYTHONDONTWRITEBYTECODE=1: the model export imports the v1 project's Python and must not write __pycache__ into it")
  (System/exit 2))

(def work (str (or (:work opts) (fs/create-temp-dir {:prefix "kimun-migrate-parity-"}))))
(fs/create-dirs work)
(doseq [e (fs/list-dir v1 (fn [x] (not (#{".git" ".kimun" ".venv"} (str (fs/file-name x))))))
        :let [l (fs/path work (fs/file-name e))] :when (not (fs/exists? l {:nofollow-links true}))]
  (fs/create-sym-link l e))
(def kdir (str (fs/path work ".kimun")))
(def ctx {:env (into {} (System/getenv)) :cwd work})
(defn pass [label o]
  (let [e (migrate/dispatch nil [] (merge {:from-v1 true :store kdir :actor "parity"} o) ctx) d (:data e)]
    (println label (select-keys (:documents d) [:migrated :skipped :failed :failed-by-reason]) (str (:elapsed-ms d) " ms"))
    {:label label :ok (:ok e) :elapsed-ms (:elapsed-ms d) :models (:models d) :documents (:documents d) :links (:links d)
     :per-model (:per-model d) :items (:items d)}))

(def p1 (pass "pass 1 (strict)" {}))
(def p2 (pass "pass 2 (--force)" {:force true}))
(def p3 (pass "pass 3 (re-run)" {:force true}))

;; ---------------------------------------------------------------- comparison
(defn norm [x] (json/parse-string (json/generate-string x)))
(defn squash [x] (clojure.walk/postwalk #(if (string? %) (str/replace (str/trim %) #"\s+" " ") %) x))
(defn kind-of [x] (cond (string? x) :string (number? x) :number (boolean? x) :boolean (nil? x) :null (map? x) :map (sequential? x) :list :else :other))
(defn excerpt [x] (let [s (pr-str x)] (if (> (count s) 160) (str (subs s 0 160) "…") s)))
(defn field-cause [a b]
  (cond (= ::none a) :field-only-in-kimun (= ::none b) :field-only-in-v1
        (not= (kind-of a) (kind-of b)) (keyword (str "type-" (name (kind-of a)) "-vs-" (name (kind-of b))))
        (= (squash a) (squash b)) :whitespace-only
        :else (keyword (str "value-" (name (kind-of a))))))
(defn payload-diffs [want got]
  (if (and (map? want) (map? got))
    (for [k (sort (distinct (concat (keys want) (keys got)))) :let [a (get want k ::none) b (get got k ::none)] :when (not= a b)]
      {:field k :cause (field-cause a b) :v1 (when (not= ::none a) (excerpt a)) :kimun (when (not= ::none b) (excerpt b))})
    [{:field nil :cause :not-a-mapping :v1 (excerpt want) :kimun (excerpt got)}]))

(def opened (cli-store/open! kdir))
(def kstore (:store opened))
(def kdocs (into {} (map (juxt :name identity)) (tdocs/documents kstore)))
(def records (for [f (sort (fs/glob recorded "*/*.json"))] (json/parse-string (slurp (str f)))))
(def rows
  (vec (for [r records
             :let [nm (get r "name") model (get r "model") k (get kdocs nm) v1-err (get r "error")
                   res (when k (tdocs/payload-of kstore k))
                   want (get r "payload") got (some-> (:payload res) norm)
                   h (when (:payload res) (tdocs/content-hash host/host (:payload res)))
                   diffs (when (and want got (not= want got)) (vec (payload-diffs want got)))
                   stale? (and (get r "source_hash_live") (not= (get r "source_hash") (get r "source_hash_live")))]]
         {:name nm :model model
          :status (cond (nil? k) :not-migrated v1-err :v1-does-not-extract (:error res) :kimun-does-not-extract diffs :payload-differs :else :payload-equal)
          :diffs diffs :kimun-error (:error res)
          :hash-live (cond (or (nil? h) (nil? (get r "content_hash_live"))) :n-a (= h (get r "content_hash_live")) :equal diffs :differs-with-payload :else :differs-same-payload)
          :hash-stored (cond (nil? h) :n-a (= h (get r "content_hash")) :equal stale? :differs-stale-v1-entry diffs :differs-with-payload :else :differs-v1-entry-outdated)
          :source-hash (cond (nil? k) :n-a (= (tdocs/text-hash host/host (:source k)) (get r "source_hash_live")) :equal :else :differs)
          :path-relative? (when k (and (some? (:path k)) (not (fs/absolute? (:path k)))))})))

(defn freq [k xs] (into (sorted-map) (frequencies (map k xs))))
(def causes
  (->> (for [r rows d (:diffs r)] (assoc d :model (:model r) :name (:name r)))
       (group-by :cause)
       (map (fn [[c ds]] [c {:fields (count ds) :documents (count (distinct (map :name ds))) :models (vec (sort (distinct (map :model ds)))) :example (first ds)}]))
       (into (sorted-map))))
(def failed-items (filter #(= :failed (:bucket %)) (get-in p2 [:items :documents])))

;; ---------------------------------------------------------------- performance
(defn timed [& argv]
  (let [t (System/nanoTime) r (apply p/shell {:out :string :err :string :continue true :dir work} (str (fs/path repo "bin" "kimun")) argv)]
    {:command (str/join " " argv) :exit (:exit r) :ms (quot (- (System/nanoTime) t) 1000000)}))
(defn ms [f] (let [t (System/nanoTime)] (f) (/ (- (System/nanoTime) t) 1e6)))
(def one (:name (first (filter #(= :payload-equal (:status %)) rows))))
(def perf
  (when-not (:no-perf opts)
    (let [objs (fs/list-dir (fs/path kdir "objects")) b (cli-store/backend kdir)]
      {:store {:kimun-bytes (reduce + (map fs/size (filter fs/regular-file? (file-seq (fs/file kdir)))))
               :objects (count objs) :log-lines (count (str/split-lines (slurp (str (fs/path kdir "log.edn")))))
               :log-bytes (fs/size (fs/path kdir "log.edn"))}
       :migration {:strict-ms (:elapsed-ms p1) :strict-docs (get-in p1 [:documents :migrated])
                   :docs-per-second (when (pos? (:elapsed-ms p1)) (/ (Math/round (/ (* 1000.0 (get-in p1 [:documents :migrated])) (:elapsed-ms p1) 0.1)) 10.0))
                   :force-pass-ms (:elapsed-ms p2) :rerun-ms (:elapsed-ms p3)}
       :commands (mapv #(apply timed %) [["--version"] ["stores" "check"] ["docs" "list"] ["docs" "show" one] ["stores" "verify"]])
       :profile-ms {:bb-startup-and-load (ms #(p/shell {:out :string :dir repo} "bb" "-e" "(require 'kimun.cli.main)"))
                    :read-log-file (ms #(slurp (str (fs/path kdir "log.edn"))))
                    :read-all-objects (ms #(doseq [o objs] (slurp (str o))))
                    :kernel-open-replay (ms #(store/open b host/host))
                    :documents-listing (ms #(tdocs/documents kstore))
                    :extract-every-document (ms #(doseq [d (vals kdocs)] (tdocs/payload-of kstore d)))}})))

;; ---------------------------------------------------------------- report
(def date (str (java.time.LocalDate/now)))
(def report
  {:date date :v1-store ".sldb (sldb v1 own store)" :recorded-documents (count rows)
   :models (dissoc (:models p1) :total) :model-failures (vec (filter #(= :failed (:bucket %)) (get-in p1 [:items :models])))
   :passes (mapv #(select-keys % [:label :ok :elapsed-ms :models :documents :links]) [p1 p2 p3])
   :documents {:strict (:documents p1) :after-force (:documents p2) :rerun (:documents p3)
               :not-migrated (mapv #(select-keys % [:name :model :reason :message]) failed-items)}
   :comparison {:status (freq :status rows) :content-hash-vs-v1-live (freq :hash-live rows) :content-hash-vs-v1-stored (freq :hash-stored rows)
                :source-hash-vs-v1-live (freq :source-hash rows) :paths-relative (freq :path-relative? rows)
                :causes causes
                :kimun-errors (vec (take 20 (keep #(when (:kimun-error %) (select-keys % [:name :model :kimun-error])) rows)))}
   :per-model (into (sorted-map) (map (fn [[m rs]] [m (merge {:documents (count rs)} (freq :status rs) {:strict (select-keys (get (:per-model p1) m) [:migrated :failed])})]) (group-by :model rows)))
   :differences (vec (keep #(when (:diffs %) (select-keys % [:name :model :diffs :hash-live])) rows))
   :performance perf})

(defn table [header rows] (str/join "\n" (concat [(str "| " (str/join " | " header) " |") (str "|" (str/join "|" (repeat (count header) "---")) "|")]
                                                 (map #(str "| " (str/join " | " %) " |") rows))))
(def md
  (str "# Migración del store real de sldb v1 a kimun — " date "\n\n"
       "Generado por `scripts/parity/compare_store.clj` sobre lo grabado por `scripts/parity/record_v1_store.py`.\n\n## Totales\n\n"
       (table ["" "total" "migrated" "skipped" "failed" "failed by reason"]
              (concat [["models" (get-in p1 [:models :total]) (get-in p1 [:models :migrated]) (get-in p1 [:models :skipped]) (get-in p1 [:models :failed]) (pr-str (get-in p1 [:models :failed-by-reason]))]]
                      (for [x [p1 p2 p3] :let [d (:documents x)]] [(str "documents, " (:label x)) (:total d) (:migrated d) (:skipped d) (:failed d) (pr-str (:failed-by-reason d))])))
       "\n\n## Comparación con v1 (" (count rows) " documentos grabados)\n\n"
       (table ["medida" "resultado"] (for [k [:status :content-hash-vs-v1-live :content-hash-vs-v1-stored :source-hash-vs-v1-live :paths-relative]] [(name k) (pr-str (get-in report [:comparison k]))]))
       "\n\n## Por modelo\n\n"
       (table ["model" "docs" "strict migrated" "strict failed" "payload equal" "payload differs" "other"]
              (for [[m r] (:per-model report)] [m (:documents r) (get-in r [:strict :migrated] 0) (get-in r [:strict :failed] 0) (:payload-equal r 0) (:payload-differs r 0)
                                                (pr-str (dissoc r :documents :strict :payload-equal :payload-differs))]))
       "\n\n## Diferencias de payload por causa\n\n"
       (if (empty? causes) "Ninguna.\n"
           (str/join "\n" (for [[c {:keys [fields documents models example]}] causes]
                            (str "- **" (name c) "** — " fields " campos en " documents " documentos (" (str/join ", " models) "). Ejemplo: `" (:model example) "/" (:name example) "` campo `" (:field example) "`\n  - v1: `" (:v1 example) "`\n  - kimun: `" (:kimun example) "`"))))
       "\n\n## Documentos no migrados tras --force\n\n"
       (if (empty? failed-items) "Ninguno.\n" (str/join "\n" (for [[r is] (group-by :reason failed-items)] (str "- **" (name r) "** — " (count is) ". Ejemplo: `" (:model (first is)) "/" (:name (first is)) "`: " (:message (first is))))))
       (when perf
         (str "\n\n## Rendimiento\n\n" (table ["medida" "valor"] (concat (map (fn [[k v]] [(name k) v]) (merge (:store perf) (:migration perf)))
                                                                           (map (fn [c] [(str "`kimun " (:command c) "` (exit " (:exit c) ")") (str (:ms c) " ms")]) (:commands perf))
                                                                           (map (fn [[k v]] [(str "profile: " (name k)) (str (Math/round (double v)) " ms")]) (:profile-ms perf))))))
       "\n"))
(fs/create-dirs out-dir)
(spit (str (fs/path out-dir (str "migrate-" date ".edn"))) (with-out-str (pp/pprint report)))
(spit (str (fs/path out-dir (str "migrate-" date ".md"))) md)
(println "work dir:" work)
(println (str/join "\n" (take 60 (str/split-lines md))))
