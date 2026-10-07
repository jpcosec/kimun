(ns kimun.cli.migrate-test
  "`kimun migrate --from-v1`: (a) a hand-made v1-layout store
   (test/fixtures/migrate: EDN descriptors stand in for the Python export
   through the injectable `:exporter`) checks buckets, idempotency and relative
   paths; (b) when sldb v1's own store is on the machine, a frozen sample of
   its documents (test/fixtures/parity/migrate: payloads recorded by
   scripts/parity/record_v1_store.py, descriptors exported once with
   `models/from-python`) is migrated and every payload must equal v1's; a
   document may differ only through an entry in EXPECTED-DIFFS.edn."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.commands.migrate :as cmd]
            [kimun.cli.errors :as errors]
            [kimun.cli.main]
            [kimun.cli.store :as cli-store]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.migrate :as migrate]))

(def fixture "test/fixtures/migrate")
(def frozen "test/fixtures/parity/migrate")
(def v1-project "/home/jp/proyectos/hum-ecosystem/tools/sldb")

(defn- exporter [dir]
  (fn [ref] (let [f (fs/path dir (str (last (str/split ref #":")) ".edn"))]
              (if (and (fs/exists? f) (not (str/includes? ref "gone")))
                (dissoc (edn/read-string (slurp (str f))) :fixture/model-ref)
                (throw (ex-info (str "ModuleNotFoundError: " ref) {}))))))

(defn- project []
  (let [p (str (fs/create-temp-dir {:prefix "kimun-migrate-"}))]
    (fs/copy-tree (fs/path fixture "project") p)
    p))

(defn- run [p opts & args]
  (errors/wrap #(cmd/dispatch (first args) (rest args) (merge {:from-v1 true :actor "tester"} opts)
                              {:env {} :cwd p :exporter (exporter (fs/path fixture "descriptors"))})))

(defn- buckets [e k] (into {} (map (juxt :name #(or (:reason %) (:bucket %)))) (get-in e [:data :items k])))

(deftest plan-is-pure-and-relative
  (let [dir (fs/path fixture "project" "sldb-v1")
        plan (migrate/read-v1 {:read-file #(let [f (fs/path dir %)] (when (fs/regular-file? f) (slurp (str f))))
                               :list-dir #(let [d (fs/path dir %)] (if (fs/directory? d) (map (comp str fs/file-name) (fs/list-dir d)) []))})]
    (is (= ["NoteDoc" "TicketDoc" "TaskDoc" "GoneDoc" "LegacyDoc"] (mapv :name (:models plan))))
    (is (= "fx.gone:GoneDoc" (:model-ref (nth (:models plan) 3))))
    (is (= 15 (count (:documents plan))))
    (is (= [{:name "sibling" :path "../sibling"}] (:links plan)))
    (is (not-any? #(some-> (:path %) fs/absolute?) (:documents plan)))
    (is (= 2 (count (:documents (migrate/select plan {:only "TaskDoc"})))))
    (is (= 4 (count (:documents (migrate/select plan {:limit 4})))))
    (is (thrown? clojure.lang.ExceptionInfo (migrate/read-v1 {:read-file (constantly nil) :list-dir (constantly [])})))
    (is (thrown? clojure.lang.ExceptionInfo (migrate/report {:documents [{:name "x" :bucket :failed}]})))))

(deftest fixture-store-lands-in-buckets
  (let [p (project) e (run p {} "sldb-v1")]
    (is (:ok e))
    (is (= {"NoteDoc" :migrated "TicketDoc" :migrated "TaskDoc" :migrated
            "GoneDoc" :model-not-importable "LegacyDoc" :descriptor-rejected} (buckets e :models)))
    (is (= {"note-a" :migrated "note-b" :migrated "note-c" :migrated "note-d" :migrated "ticket-1" :migrated "ticket-2" :migrated
            "task-1" :migrated "task-2" :migrated "note-missing" :file-missing "zz-broken" :entry-unreadable
            "ticket-bad" :no-extract "ticket-loose" :no-round-trip
            "gone-1" :model-unavailable "legacy-1" :model-unavailable "stray-1" :model-unavailable} (buckets e :documents)))
    (is (= {:total 15 :migrated 8 :skipped 0 :failed 7} (select-keys (get-in e [:data :documents]) [:total :migrated :skipped :failed])))
    (is (= {:entry-unreadable 1 :file-missing 1 :model-unavailable 3 :no-extract 1 :no-round-trip 1} (get-in e [:data :documents :failed-by-reason])))
    (is (= {"sibling" :no-kimun-store} (buckets e :links)))
    (is (= "sldb-v1" (get-in e [:data :from])))
    (testing "paths are relative to the project root, in the report and in the store"
      (let [docs (tdocs/documents (:store (cli-store/open! (str (fs/path p ".kimun")))))]
        (is (= 8 (count docs)))
        (is (= "notes/a.md" (:path (first (filter #(= "note-a" (:name %)) docs)))))
        (is (not-any? #(fs/absolute? (:path %)) docs))
        (is (not (str/includes? (pr-str (:data e)) p)))))
    (testing "re-running skips what is there; --force takes the document that does not round-trip"
      (let [e2 (run p {} "sldb-v1") e3 (run p {:force true} "sldb-v1")]
        (is (= {:migrated 0 :skipped 8 :failed 7} (select-keys (get-in e2 [:data :documents]) [:migrated :skipped :failed])))
        (is (= {:migrated 0 :skipped 3 :failed 2} (select-keys (get-in e2 [:data :models]) [:migrated :skipped :failed])))
        (is (= {:migrated 1 :skipped 8 :failed 6} (select-keys (get-in e3 [:data :documents]) [:migrated :skipped :failed])))
        (is (= :migrated ((buckets e3 :documents) "ticket-loose")))))
    (testing "a changed file is migrated again, alone"
      (spit (str (fs/path p "notes" "a.md")) "# Note a\n\nA new body.\n")
      (let [e4 (run p {} "sldb-v1")
            d (tdocs/document (:store (cli-store/open! (str (fs/path p ".kimun")))) "note-a")]
        (is (= {:migrated 1 :skipped 8 :failed 6} (select-keys (get-in e4 [:data :documents]) [:migrated :skipped :failed])))
        (is (= :updated (:change (first (filter #(= "note-a" (:name %)) (get-in e4 [:data :items :documents]))))))
        (is (= "A new body." (get (:payload d) "body" (get (:payload d) :body))))))
    (testing "a sibling project with a kimun store is linked, relatively"
      (cli-store/init! (str (fs/path (fs/parent p) "sibling" ".kimun")) {:actor "tester"})
      (let [e5 (run p {} "sldb-v1")]
        (is (= {"sibling" :migrated} (buckets e5 :links)))
        (is (= [{:name "sibling" :path "../../sibling/.kimun"}] (:links (:descriptor (cli-store/open! (str (fs/path p ".kimun")))))))
        (is (= {"sibling" :skipped} (buckets (run p {} "sldb-v1") :links))))
      (fs/delete-tree (fs/path (fs/parent p) "sibling")))))

(deftest dry-run-only-limit-and-failures
  (let [p (project)]
    (let [e (run p {:dry-run true} "sldb-v1")]
      (is (and (:ok e) (get-in e [:data :dry-run?])))
      (is (= 8 (get-in e [:data :documents :migrated])))
      (is (not (fs/exists? (fs/path p ".kimun")))))
    (let [e (run p {:only "TaskDoc" :limit "1"} "sldb-v1")]
      (is (= [1 1] [(get-in e [:data :models :migrated]) (get-in e [:data :documents :migrated])])))
    (let [e (run p {:only "GoneDoc"} "sldb-v1")]
      (is (false? (:ok e)))
      (is (= ["migrate/nothing-migrated" 5] [(get-in e [:error :type]) (:exit e)]))
      (is (= {:model-unavailable 1} (get-in e [:error :data :documents :failed-by-reason]))))
    (let [e (run p {} "notes")]
      (is (= ["migrate/not-v1-store" 5] [(get-in e [:error :type]) (:exit e)])))
    (is (= "cli/usage" (get-in (errors/wrap #(cmd/dispatch nil [] {} {:env {} :cwd p})) [:error :type])))))

(deftest real-store-sample-extracts-as-in-v1
  (if-not (fs/exists? (fs/path v1-project ".sldb" "core" "store_index.yaml"))
    (println "SKIPPED kimun.cli.migrate-test/real-store-sample-extracts-as-in-v1:" (str v1-project "/.sldb") "is not on this machine")
    (let [p (str (fs/create-temp-dir {:prefix "kimun-migrate-real-"}))
          recs (for [f (sort (fs/glob (fs/path frozen "documents") "*/*.json"))] (json/parse-string (slurp (str f))))
          models (sort (distinct (map #(get % "model") recs)))
          core (fs/path p "sldb-v1" "core")]
      (doseq [e (fs/list-dir v1-project) :when (not (#{".git" ".kimun" ".venv"} (str (fs/file-name e))))]
        (fs/create-sym-link (fs/path p (fs/file-name e)) e))
      (fs/create-dirs core)
      (spit (str (fs/path core "store_index.yaml"))
            (str "stores: []\nmodels:\n" (str/join (for [m models] (str "- name: " m "\n  model_ref: frozen:" m "\n")))))
      (doseq [r recs :let [d (fs/path core "documents" (get r "model"))]]
        (fs/create-dirs d)
        (spit (str (fs/path d (str (get r "name") ".yaml"))) (json/generate-string (select-keys r ["name" "path"]))))
      (let [e (errors/wrap #(cmd/dispatch "sldb-v1" [] {:from-v1 true :force true :actor "parity"}
                                          {:env {} :cwd p :exporter (exporter (fs/path frozen "descriptors"))}))
            store (:store (cli-store/open! (str (fs/path p ".kimun"))))
            docs (into {} (map (juxt :name identity)) (tdocs/documents store))
            host (:host store)
            expected (edn/read-string (slurp (str (fs/path frozen "EXPECTED-DIFFS.edn"))))
            fresh (filter #(when-let [d (docs (get % "name"))] (= (get % "source_hash_live") (tdocs/text-hash host (:source d)))) recs)
            diffs (for [r fresh :let [d (docs (get r "name")) got (:payload (tdocs/payload-of store d))]
                        :when (or (not= (get r "payload") (json/parse-string (json/generate-string got)))
                                  (not= (get r "content_hash_live") (tdocs/content-hash host got)))]
                    (get r "name"))]
        (is (:ok e))
        (is (<= 10 (count models)))
        (is (= (count models) (get-in e [:data :models :migrated])))
        (is (= (count recs) (get-in e [:data :documents :migrated])) (pr-str (get-in e [:data :documents :failed-by-reason])))
        (is (not-any? #(fs/absolute? (:path %)) (vals docs)))
        (is (= (set (filter (set (map #(get % "name") fresh)) (keys expected))) (set diffs))
            "payload and content-hash equal v1's for every unchanged document, except exactly those in EXPECTED-DIFFS.edn")
        (is (<= 40 (count fresh)) "most of the frozen sample must still be comparable; re-freeze when the v1 files moved on")
        (when (< (count fresh) (count recs))
          (println "NOTE kimun.cli.migrate-test: " (- (count recs) (count fresh)) "of" (count recs) "frozen documents changed in v1 since the freeze and were not compared"))))))

(deftest migrate-is-reachable-from-the-cli
  ;; regression: the group existed but was never registered in kimun.cli.main
  (let [e (:envelope (kimun.cli.main/run ["migrate" "--from-v1" "/nonexistent-kimun-v1"] {"USER" "tester"}
                                         (str (babashka.fs/create-temp-dir {:prefix "kimun-mig-cli-"}))))]
    (is (not= "org.babashka/cli" (get-in e [:error :type])) (pr-str e))
    (is (= "migrate/not-v1-store" (get-in e [:error :type])) (pr-str e))))
