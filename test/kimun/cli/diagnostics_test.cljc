(ns kimun.cli.diagnostics-test
  "`stores check`, `stores health`, `stores update` and `stores reconcile` end
   to end on temporary stores. Each test names in a comment the v1 function
   whose promise it carries."
  (:require [kimun.test-util :as ktu] [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [cheshire.core :as json]
            [kimun.cli.main :as main]
            [kimun.cli.out :as out]
            [kimun.cli.store :as cli-store]
            [kimun.host.fs-store :as fs-store]
            [kimun.kernel.mmap :as mmap]
            [kimun.kernel.revision :as rev]
            [kimun.kernel.store :as store]))

(def env {"USER" "tester"})
(defn- run
  "The envelope of a CLI run. An invalid store makes `stores check` the failure
   `store/invalid` (exit 5) whose error data is the whole report: it is lifted
   to `:data`/`:warnings` so an assertion reads one shape either way."
  [cwd & argv]
  (let [e (:envelope (main/run argv env cwd))]
    (if (= "store/invalid" (get-in e [:error :type]))
      (assoc e :data (get-in e [:error :data]) :warnings (get-in e [:error :data :warnings]))
      e)))
(defn- file [p & parts] (str (apply fs/path p parts)))

(def ticket
  (pr-str {:model/name "TicketDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "status" :type :string :required true :description "Status."}]
           :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"}))

(defn- project
  "A project with TicketDoc and the tracked document `login` (login.md)."
  []
  (let [p (str (ktu/temp-dir "kimun-diag-cli-"))]
    (run p "stores" "init" "--name" "p")
    (spit (file p "ticket.edn") ticket)
    (is (true? (:ok (run p "models" "define" "-f" "ticket.edn"))))
    (is (true? (:ok (run p "docs" "create" "TicketDoc" "login" "--set" "title=T" "--set" "status=open"))))
    p))

(deftest check-lists-every-model-and-document-when-clean
  ;; v1: test_check_lists_every_model_and_document_when_clean (the report-shape promise)
  (let [p (project)
        e (run p "stores" "check")
        r (get-in e [:data :documents])]
    (is (true? (:ok e)))
    (is (= [] (:warnings e)))
    (is (= [true 1 1 []] [(:valid r) (:checked r) (:tracked r) (:findings r)]))
    (is (str/starts-with? (:summary r) "PASS: store integrity"))
    (is (= [["TicketDoc" true :ok]] (mapv (juxt :model :contract-ok :category) (:models r))))
    (is (= [["login" "login.md" :ok true true]] (mapv (juxt :document :path :category :content-ok :fields-ok) (:documents r))))))

(deftest check-names-the-damaged-document-and-its-fix
  ;; v1: test_report_names_the_damaged_document_and_its_fix (v1 exits 1; here the envelope stays ok with `:valid false`, as test_store_check_data_mutation_fails was already ported)
  (let [p (project)]
    (spit (file p "login.md") "# T\n\nStatus: closed\n")
    (let [e (run p "stores" "check")
          w (first (:warnings e))]
      (is (false? (get-in e [:data :documents :valid])))
      (is (str/includes? (get-in e [:data :documents :summary]) "FAIL: store integrity"))
      (is (str/includes? (get-in e [:data :documents :summary]) "1 edited"))
      (is (= ["docs/file-modified" :edited "TicketDoc" "login" "login.md" "kimun docs update login"]
             ((juxt :type :category :model :document :path :action) w)) "the document is named, not just counted")
      (is (str/includes? (:message w) "login.md"))
      (is (str/includes? (:message w) "kimun docs update login") "the fix is stated in the message")
      (is (str/includes? (:message w) "decide first")))))

(deftest check-distinguishes-reformatting-from-damage
  ;; v1: test_report_distinguishes_reformatting_from_damage
  (let [p (project)]
    (spit (file p "login.md") "# T\n\nStatus: open\n\n\n")
    (let [e (run p "stores" "check")
          s (get-in e [:data :documents :summary])]
      (is (true? (:ok e)))
      (is (true? (get-in e [:data :documents :valid])) "an expected mutation passes")
      (is (str/includes? s "PASS: store integrity"))
      (is (str/includes? s "reformatted (ok)"))
      (is (not (str/includes? s "edited"))))))

(deftest check-reports-divergence-and-a-missing-file-together
  ;; v1: test_check_reports_file_divergence_and_missing_file
  (let [p (project)]
    (run p "docs" "create" "TicketDoc" "gone" "--set" "title=G" "--set" "status=open")
    (spit (file p "login.md") "# T\n\nStatus: closed\n")
    (fs/delete (file p "gone.md"))
    (let [r (get-in (run p "stores" "check") [:data :documents])
          by (into {} (map (juxt :document identity)) (:documents r))]
      (is (false? (:valid r)))
      (is (= [false :edited] [(get-in by ["login" :content-ok]) (get-in by ["login" :category])]))
      (is (= [false :missing] [(get-in by ["gone" :path-exists]) (get-in by ["gone" :category])]))
      (is (seq (get-in by ["gone" :message])))
      (is (= {:edited 1 :missing 1} (:counts r))))))

(deftest check-in-json-and-yaml
  ;; v1: test_json_carries_both_letter_and_semantic_keys (semantic keys), test_store_check_json_format,
  ;;     test_store_check_yaml_format
  (let [p (project)]
    (spit (file p "login.md") "# T\n\nStatus: closed\n")
    (let [e (run p "stores" "check")
          j (json/parse-string (out/emit e "json") true)
          d (first (get-in j [:data :documents :documents]))
          y (out/emit e "yaml")]
      (is (= ["edited" false false] [(:category d) (:content-ok d) (:fields-ok d)]))
      (is (str/includes? (:message d) "docs update"))
      (is (= 1 (get-in j [:data :documents :counts :edited])))
      (is (str/includes? y "valid: false"))
      (is (str/includes? y "models:"))
      (is (str/includes? y "category: edited")))))

(defn- damage!
  "Damages objects/<id> of the store in `dir`: `:altered` (other bytes, still
   EDN), `:unparseable` (not EDN) or `:deleted`."
  [dir id mode]
  (let [f (file dir "objects" id)]
    (case mode :altered (spit f "tampered") :unparseable (spit f "{{{") :deleted (fs/delete f))))

(defn- damaged-copy
  "A fresh copy of project `p0` whose object `id` is damaged; the project path."
  [p0 id mode]
  (let [p (str (ktu/temp-dir "kimun-diag-dmg-"))]
    (fs/copy-tree p0 p {:replace-existing true})
    (damage! (file p ".kimun") id mode)
    p))

(defn- raw-run [p & argv] (:envelope (main/run (vec argv) env p)))

(defn- many-commits
  "`project` plus 20 documents and two edits of `login`: 23+ revisions, one of
   them the head of no tree, an :incidence map big enough for a branch and a
   hub (the model node: more than `rev/hub-threshold` edges, a nested set)."
  []
  (let [p (project)]
    (doseq [i (range 20)]
      (is (true? (:ok (run p "docs" "create" "TicketDoc" (str "d" i) "--set" (str "title=T" i) "--set" "status=open")))))
    (is (true? (:ok (run p "fields" "set" "login" "status" "doing"))))
    (is (true? (:ok (run p "fields" "set" "login" "status" "done"))))
    p))

(deftest check-reports-each-kind-of-damaged-object
  ;; v1: test_tampered_hash_b — the stored signature no longer matches; the fix is named.
  ;; Replaces the random-victim `check-reports-a-tampered-object`: one victim per KIND of
  ;; reachable object, each altered, made unparseable and deleted.
  (let [p0 (many-commits)
        s (:store (cli-store/open! (file p0 ".kimun")))
        objs (:objects s)
        ;; format 4: the tree objects and nodes of a by-reference document live in
        ;; the cache, not in objects/ (docs/v2/14 §18.6); a victim must exist in
        ;; the CAS to be damageable and to be checked by `verify`
        ids (vec (filter #(fs/exists? (file p0 ".kimun" "objects" %)) (sort (store/reachable s))))
        rev (get objs (:head s))
        heads (set (cons (:head s) (vals (:heads s))))
        ;; the nested edge set of a hub: the model node, an endpoint of one edge per document
        hub-root (some (fn [[_ v]] (when (rev/hub-value? v) (:set v))) (mmap/entries #(get objs %) (:incidence rev)))
        hub-inner (first (mmap/child-ids (get objs hub-root)))
        ;; one object per kind: the generic branch and leaf are none of the objects named above
        ;; (ids vary per run, and a leaf of :edges can be the very leaf of a hub's set)
        ;; (the root of the per-tree heads is checked by open itself: see the table below)
        taken #{(:trees rev) (:edges rev) (:incidence rev) hub-root hub-inner (:heads-root s)}
        branch (first (filter #(and (mmap/branch? (get objs %)) (not (taken %)) (some (complement taken) (mmap/child-ids (get objs %)))) ids))
        tree-obj (first (filter #(let [o (get objs %)] (and (map? o) (:node o) (contains? o :children))) ids))
        older (first (remove heads (keys (:revisions s))))
        victims {:node (:node (get objs tree-obj)) :tree tree-obj
                 :mmap-branch branch :mmap-leaf (first (remove taken (mmap/child-ids (get objs branch))))
                 :hub-set-root hub-root :hub-set-inner hub-inner
                 :trees-root (:trees rev) :edges-root (:edges rev) :incidence-root (:incidence rev)
                 :older-revision older
                 ;; the head of another tree: format 2 reads it through the heads mmap on demand, so verify (not open) checks it
                 :tree-head (first (remove #{(:head s)} (vals (:heads s))))}]
    (is (every? string? (vals victims)) "the store has one object of every kind")
    (is (= (count victims) (count (set (vals victims)))) "eleven different objects")
    (is (and (mmap/branch? (get objs hub-root)) (> (mmap/node-count (get objs hub-root)) rev/hub-threshold)) "the hub set is a nested mmap")
    (doseq [[kind id] victims
            [mode reason] [[:altered :hash-mismatch] [:unparseable :hash-mismatch] [:deleted :missing]]]
      (testing (str kind " " mode)
        (let [p (damaged-copy p0 id mode)
              bad [{:id id :reason reason}]
              {:keys [backend store]} (cli-store/open! (file p ".kimun"))   ; open reads only the head Revisions
              check (raw-run p "stores" "check")
              sverify (raw-run p "stores" "verify")
              jverify (raw-run p "journal" "verify")]
          (is (= {:ok? false :bad bad} (select-keys (store/verify backend store) [:ok? :bad])))
          (is (= {:ok? false :bad bad} (select-keys (store/verify-full backend (:host store)) [:ok? :bad])))
          (is (= ["store/invalid" 5] [(get-in check [:error :type]) (out/exit-code check)]))
          (is (= bad (get-in check [:error :data :documents :findings 0 :bad])))
          (is (= ["store/corrupt" "kimun stores verify"]
                 ((juxt :type :action) (first (get-in check [:error :data :warnings])))))
          (is (= ["store/corrupt" 5 bad] [(get-in sverify [:error :type]) (out/exit-code sverify) (get-in sverify [:error :data :bad])]))
          (is (= ["store/corrupt" 5 bad] [(get-in jverify [:error :type]) (out/exit-code jverify) (get-in jverify [:error :data :bad])]))
          (is (= :needs-human (get-in (run p "stores" "health") [:data :status]))))))
    (doseq [[what id] [["head Revision" (:head s)] ["root of the per-tree heads" (:heads-root s)]]
            mode [:altered :unparseable :deleted]]
      (testing (str what " " mode ": open itself refuses, every command reports it, nothing raises")
        (let [p (damaged-copy p0 id mode)
              reason (if (= :deleted mode) :missing :hash-mismatch)
              opened (try (cli-store/open! (file p ".kimun")) (catch Exception e (ex-data e)))
              full (store/verify-full (fs-store/backend (file p ".kimun")) (:host s))]
          (is (= [:store/corrupt-head id reason] ((juxt :type :id :reason) opened)))
          (is (= {:ok? false :bad [{:id id :reason reason}]} (select-keys full [:ok? :bad])))   ; the replay does not need it
          (doseq [argv [["stores" "check"] ["stores" "verify"] ["journal" "verify"] ["stores" "health"]]]
            (let [e (apply raw-run p argv)]
              (is (false? (:ok e)) (str argv))
              (is (= 5 (out/exit-code e)) (str argv))
              (is (str/includes? (pr-str (:error e)) id) (str argv)))))))))

(deftest check-reports-a-tampered-object-whichever-it-is
  ;; the random-victim property the table above replaces as the main test: any reachable object but
  ;; the two that open itself checks (the head Revision and the root of the per-tree heads, see the table)
  (let [p0 (project)
        s (:store (cli-store/open! (file p0 ".kimun")))
        heads #{(:head s) (:heads-root s)}
        ;; the empty mmap is a constant the loader resolves without the CAS: deleting
        ;; it is not damage (kimun.kernel.revision/loader), so it is no victim here
        empty-map (:root (mmap/empty (:host s)))
        candidates (vec (filter #(fs/exists? (file p0 ".kimun" "objects" %))
                                (remove (conj heads empty-map) (sort (store/reachable s)))))]
    (doseq [id (take 6 (shuffle candidates))]
      (let [p (damaged-copy p0 id (rand-nth [:altered :unparseable :deleted]))
            e (run p "stores" "check")
            w (first (:warnings e))]
        (is (false? (get-in e [:data :documents :valid])) id)
        (is (= [id] (mapv :id (get-in e [:data :documents :findings 0 :bad]))) id)
        (is (= ["store/corrupt" "kimun stores verify"] [(:type w) (:action w)]) id)))))

(deftest health-names-the-document-the-category-and-the-command
  ;; v1: test_health_names_what_to_look_at_and_what_to_run, test_health_demands_a_human_when_a_field_was_edited (CLI)
  (let [p (project)]
    (is (= :ok (get-in (run p "stores" "health") [:data :status])))
    (spit (file p "login.md") "# T\n\nStatus: closed\n")
    (let [h (run p "stores" "health")]
      (is (true? (:ok h)))
      (is (= :needs-human (get-in h [:data :status])))
      (is (= [{:category :edited :model "TicketDoc" :document "login" :path "login.md"
               :action "kimun docs update login" :needs-human true}]
             (get-in h [:data :findings])))
      (is (= "inspect: login.md" (get-in h [:data :next]))))
    (is (true? (:ok (run p "docs" "update" "login"))) "the named command repairs it")
    (is (= {:status :ok :findings [] :actions [] :next nil}
           (dissoc (:data (run p "stores" "health")) :summary)))))

(deftest update-repairs-what-needs-no-decision
  ;; v1: test_store_is_valid_again_after_update
  (let [p (project)]
    (run p "docs" "create" "TicketDoc" "other" "--set" "title=O" "--set" "status=open")
    (is (true? (:ok (run p "models" "field-add" "TicketDoc" "--field" "priority" "--type" "int" "--description" "Field int." "--default" "5"))))
    (is (= :degraded (get-in (run p "stores" "health") [:data :status])))
    (testing "an edited file is left for a human, the rest is repaired"
      (spit (file p "other.md") "# O\n\nStatus: closed\n")
      (let [u (run p "stores" "update")]
        (is (true? (:ok u)) (pr-str u))
        (is (= ["login"] (get-in u [:data :updated])))
        (is (= [{:document "other" :category :edited :action "kimun docs update other"}] (get-in u [:data :skipped])))
        (is (= :needs-human (get-in u [:data :status])))))
    (is (true? (:ok (run p "docs" "update" "other"))))
    (let [chk (run p "stores" "check")]
      (is (true? (get-in chk [:data :documents :valid])) (pr-str (:warnings chk)))
      (is (= [] (:warnings chk))))
    (is (= {:updated [] :skipped [] :status :ok} (:data (run p "stores" "update"))) "nothing to do is not an error")))

(deftest update-composes-one-revision-for-every-document-it-repairs
  ;; S1b step 2: instead of one transaction per repaired document, all the
  ;; `update-plan`s computed in memory compose into ONE revision, and the
  ;; journal keeps what each document changed (`:documents`).
  (let [p (project)
        _ (is (true? (:ok (run p "docs" "create" "TicketDoc" "a" "--set" "title=A" "--set" "status=open"))))
        _ (is (true? (:ok (run p "docs" "create" "TicketDoc" "b" "--set" "title=B" "--set" "status=open"))))
        _ (is (true? (:ok (run p "models" "field-add" "TicketDoc" "--field" "priority" "--type" "int" "--description" "Field int." "--default" "5"))))
        revisions-before (count (:data (run p "journal" "show")))
        u (run p "stores" "update")]
    (is (true? (:ok u)) (pr-str u))
    (is (= #{"login" "a" "b"} (set (get-in u [:data :updated]))))
    (is (= 1 (- (count (:data (run p "journal" "show"))) revisions-before)) "one revision for all three repairs")
    (let [e (first (:data (run p "journal" "show")))]
      (is (= "stores update" (:operation e)))
      (is (= #{"TicketDoc:login" "TicketDoc:a" "TicketDoc:b"} (set (map :address (:documents e)))))
      (is (every? #(contains? % :fields) (:documents e)) "each document carries the fields it changed"))))

(deftest reconcile-reports-and-applies-only-when-requested
  ;; v1: test_reconcile_reports_and_applies_only_when_requested
  (let [base (str (ktu/temp-dir "kimun-reconcile-"))
        catalog (file base "catalog") one (file base "projects" "one") gone (file base "gone")]
    (doseq [d [catalog one gone]] (fs/create-dirs d) (run d "stores" "init"))
    (run catalog "stores" "link" "gone" gone)
    (fs/delete-tree gone)
    (let [one-store (str (fs/canonicalize (file one ".kimun")))
          r (:data (run catalog "stores" "reconcile" (file base "projects")))]
      (is (= [one-store] (:discovered r)))
      (is (= [one-store] (:missing r)))
      (is (= ["gone"] (mapv :name (:stale r))))
      (is (false? (:applied r)))
      (is (= ["gone"] (mapv :name (get-in (run catalog "stores" "links") [:data :links]))) "nothing written without --apply")
      (is (true? (:applied (:data (run catalog "stores" "reconcile" (file base "projects") "--apply")))))
      (let [links (get-in (run catalog "stores" "links") [:data :links])]
        (is (= [["one" :ok]] (mapv (juxt :name :status) links)))
        (is (= one-store (str (fs/canonicalize (:resolved (first links))))))))))

(deftest check-reports-noise-already-tracked-without-blocking
  ;; v1: test_stores_check_reports_noise_already_tracked_without_blocking,
  ;;     test_stores_check_prints_every_noisy_heading_not_just_a_count
  (let [p (str (ktu/temp-dir "kimun-diag-noise-"))]
    (run p "stores" "init" "--name" "p")
    (spit (file p "note.edn")
          (pr-str {:model/name "NoteDoc" :model/version 1 :base []
                   :fields [{:name "title" :type :string :required true :description "Note title."}
                            {:name "body" :type :string :required true :description "Body."}]
                   :template "# ⸢rev•title⸥\n\n## Answer\n\n⸢rev•body⸥"}))
    (is (true? (:ok (run p "models" "define" "-f" "note.edn"))))
    (spit (file p "noisy.md") "# Alpha\n\n## Answer\n\nBody text\n\n## Entidades\n\n- one\n\n## Medicion 1\n\n- two\n")
    (is (= "docs/structural-noise" (get-in (run p "docs" "track" "NoteDoc" "noisy.md") [:error :type])))
    (is (true? (:ok (run p "docs" "track" "NoteDoc" "noisy.md" "--force"))))
    (let [e (run p "stores" "check")
          f (first (get-in e [:data :documents :findings]))
          w (first (:warnings e))]
      (is (true? (:ok e)) "the report does not block")
      (is (= [:noise "noisy" "NoteDoc" ["## Entidades" "## Medicion 1"]] ((juxt :category :document :model :undeclared) f)))
      (is (true? (:needs-human f)))
      (is (= "docs/structural-noise" (:type w)))
      (is (= ["## Entidades" "## Medicion 1"] (:undeclared w)))
      (is (every? #(str/includes? (:message w) %) ["structural noise" "noisy" "NoteDoc" "## Entidades" "## Medicion 1"])
          "every noisy heading is named, not just counted")
      (is (str/includes? (get-in e [:data :documents :summary]) "1 noisy")))))

(deftest check-reports-a-stored-object-even-when-the-cache-holds-a-copy
  ;; Format 4 keeps a document's tree in <store>/cache/ and the files backend
  ;; reads the cache as a fallback; an object the store must hold (here the
  ;; document root, referenced by edges) can also be in the cache. Deleting it
  ;; from objects/ must still be reported: verify reads the store, never the cache.
  (let [p0 (project)
        objs (set (map str (fs/list-dir (file p0 ".kimun" "objects"))))
        cached (->> (fs/list-dir (file p0 ".kimun" "cache" "objects")) (map fs/file-name) set)
        both (sort (filter #(contains? cached %) (map #(str (fs/file-name %)) objs)))]
    (is (seq both) "precondition: some stored object also lives in the cache")
    (let [id (first both)
          p (damaged-copy p0 id :deleted)
          e (run p "stores" "check")]
      (is (false? (get-in e [:data :documents :valid])) id)
      (is (= [id] (mapv :id (get-in e [:data :documents :findings 0 :bad]))) id))))
