(ns kimun.cli.stores-test
  "The `stores` group end to end on temporary directories: init layout,
   duplicate init, check/verify/show, corruption, and store discovery
   precedence (docs/v2/06 §B)."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [sldb.kernel.store :as store]))

(def env {"USER" "tester"})

(defn- tmp [] (str (fs/create-temp-dir {:prefix "kimun-cli-"})))

(defn- run
  ([cwd & argv] (:envelope (main/run argv env cwd))))

(defn- run-env [env cwd & argv] (:envelope (main/run argv env cwd)))

(deftest init-creates-the-store-layout
  (let [proj (tmp)
        e (run proj "stores" "init")
        dir (str (fs/path proj ".kimun"))
        descriptor (edn/read-string (slurp (str (fs/path dir "store.edn"))))]
    (is (true? (:ok e)) (pr-str e))
    (is (= "stores init" (:command e)))
    (is (fs/exists? (fs/path dir "store.edn")))
    (is (fs/directory? (fs/path dir "objects")))
    (is (= dir (get-in e [:data :path])))
    (is (= (fs/file-name proj) (:name descriptor)) "default name = project directory name")
    (is (= (:name descriptor) (get-in e [:data :name])))
    (is (= [] (:links descriptor)))
    (is (= :all (get-in descriptor [:capabilities "human/tester"])) "the actor capability")
    (is (= :all (get-in descriptor [:capabilities "kimun/derive"])))
    (is (= "human/tester" (get-in e [:data :actor])))
    (testing "--name and --actor are honoured"
      (let [proj2 (tmp)
            e2 (run proj2 "stores" "init" "--name" "named" "--actor" "agent/x")
            d2 (edn/read-string (slurp (str (fs/path proj2 ".kimun" "store.edn"))))]
        (is (= "named" (:name d2)))
        (is (= :all (get-in d2 [:capabilities "agent/x"])))
        (is (= "named" (get-in e2 [:store :name])))))))

(deftest second-init-is-store-exists
  (let [proj (tmp)
        _ (run proj "stores" "init")
        e (run proj "stores" "init")]
    (is (false? (:ok e)))
    (is (= 5 (:exit e)))
    (is (= "cli/store-exists" (get-in e [:error :type])))
    (is (str/includes? (get-in e [:error :message]) ".kimun"))))

(deftest check-without-a-store-is-no-store
  (let [empty-dir (tmp)
        e (run empty-dir "stores" "check" "--store" empty-dir)]
    (is (false? (:ok e)))
    (is (= 5 (:exit e)))
    (is (= "cli/no-store" (get-in e [:error :type])))
    (is (str/includes? (get-in e [:error :message]) empty-dir))
    (is (= empty-dir (get-in e [:error :data :path])))
    (testing "without --store and nothing to discover"
      (let [e (run-env {"USER" "t"} (tmp) "stores" "check")]
        (is (= "cli/no-store" (get-in e [:error :type])))
        (is (= 5 (:exit e)))))))

(deftest check-after-init-reports-an-empty-store
  (let [proj (tmp)
        _ (run proj "stores" "init" "--name" "s")
        e (run proj "stores" "check")]
    (is (true? (:ok e)) (pr-str e))
    (is (= 0 (get-in e [:data :revisions])))
    (is (= 0 (get-in e [:data :trees])))
    (is (= 0 (get-in e [:data :objects])))
    (is (nil? (:revision e)))
    (is (= :sha-256 (get-in e [:data :hash-alg])))
    (is (= [] (get-in e [:data :links])))
    (is (= "s" (get-in e [:store :name])))
    (is (= (str (fs/path proj ".kimun")) (get-in e [:store :path])))))

(deftest show-reads-descriptor-and-heads-without-replay
  (let [proj (tmp)
        _ (run proj "stores" "init" "--name" "s")
        e (run proj "stores" "show")]
    (is (true? (:ok e)) (pr-str e))
    (is (= "s" (get-in e [:data :name])))
    (is (= :all (get-in e [:data :capabilities "human/tester"])))
    (is (nil? (get-in e [:data :head])))
    (is (= [] (get-in e [:data :links])))))

(defn- commit-one!
  "One real transaction (a text node) in the store at `dir`; returns the apply result."
  [dir]
  (let [{:keys [backend store]} (cli-store/open! dir)]
    (store/commit! backend store
                   {:plan/version 1 :base (:head store) :actor "human/tester" :engines {}
                    :timestamp "2026-09-06T12:00:00.000Z"
                    :ops [{:op :add-node :node {:class :sign :kind :text :content {:text "hola"}} :as :t}]})))

(deftest verify-ok-and-after-corruption
  (let [proj (tmp)
        _ (run proj "stores" "init")
        dir (str (fs/path proj ".kimun"))
        ok (run proj "stores" "verify")]
    (is (true? (:ok ok)) (pr-str ok))
    (is (>= (get-in ok [:data :checked]) 0))
    (is (= [] (get-in ok [:data :bad])))
    (let [r (commit-one! dir)
          after (run proj "stores" "verify")
          chk (run proj "stores" "check")]
      (is (true? (:ok after)) (pr-str after))
      (is (pos? (get-in after [:data :checked])))
      (is (= (:revision-id r) (:revision after)) "the envelope revision is the head")
      (is (= 1 (get-in chk [:data :revisions])))
      (testing "overwriting one object file makes verify fail with exit 5"
        (let [victim (first (sort (store/reachable (:store r))))]
          (spit (str (fs/path dir "objects" victim)) "tampered")
          (let [bad (run proj "stores" "verify")]
            (is (false? (:ok bad)))
            (is (= 5 (:exit bad)))
            (is (= "store/corrupt" (get-in bad [:error :type])))
            (is (seq (get-in bad [:error :data :bad])))
            (is (= victim (:id (first (get-in bad [:error :data :bad])))))))))))

(deftest discovery-walks-up-from-a-nested-directory
  (let [proj (tmp)
        nested (str (fs/create-dirs (fs/path proj "a" "b" "c")))
        _ (run proj "stores" "init" "--name" "root")
        e (run nested "stores" "check")]
    (is (true? (:ok e)) (pr-str e))
    (is (= "root" (get-in e [:store :name])))
    (is (= (str (fs/path proj ".kimun")) (get-in e [:store :path])))
    (is (= (str (fs/path proj ".kimun")) (cli-store/discover nested)))))

(deftest explicit-store-wins-over-discovery
  (let [proj-a (tmp) proj-b (tmp)
        _ (run proj-a "stores" "init" "--name" "a")
        _ (run proj-b "stores" "init" "--name" "b")
        by-project (run proj-a "stores" "check" "--store" proj-b)
        by-dir (run proj-a "stores" "check" "--store" (str (fs/path proj-b ".kimun")))]
    (is (= "b" (get-in by-project [:store :name])) "--store accepts the project directory")
    (is (= "b" (get-in by-dir [:store :name])) "--store accepts the .kimun directory")
    (is (= (str (fs/path proj-b ".kimun")) (get-in by-dir [:store :path])))))

(deftest env-store-wins-over-walk-up-but-not-over-flag
  (let [proj-a (tmp) proj-b (tmp) proj-c (tmp)
        _ (run proj-a "stores" "init" "--name" "a")
        _ (run proj-b "stores" "init" "--name" "b")
        _ (run proj-c "stores" "init" "--name" "c")
        env' (assoc env "KIMUN_STORE" proj-b)
        by-env (run-env env' proj-a "stores" "check")
        by-flag (run-env env' proj-a "stores" "check" "--store" proj-c)]
    (is (= "b" (get-in by-env [:store :name])))
    (is (= "c" (get-in by-flag [:store :name])))
    (is (= (str (fs/path proj-b ".kimun"))
           (cli-store/resolve-store {} env' proj-a)))))

(deftest unknown-subcommand-lists-the-known-ones
  (let [e (run (tmp) "stores" "nope")]
    (is (= 6 (:exit e)))
    (is (= "cli/usage" (get-in e [:error :type])))
    (is (= ["check" "health" "init" "link" "links" "reconcile" "show" "unlink" "update" "verify"] (get-in e [:error :data :known])))))

;; ------------------------------------------------------------------ federation

(defn- init-in
  "Creates a project dir `name` under `root` with a store of the same name; returns the project dir."
  [root nm]
  (let [proj (str (fs/create-dirs (fs/path root nm)))]
    (run proj "stores" "init" "--name" nm)
    proj))

(defn- descriptor-of [proj]
  (edn/read-string (slurp (str (fs/path proj ".kimun" "store.edn")))))

(deftest link-stores-a-relative-path
  ;; v1: test_store_add_links_other_store, test_link_store_records_link_and_refuses_duplicates
  (let [root (tmp)
        a (init-in root "a")
        _b (init-in root "b")
        e (run a "stores" "link" "bee" "../b")]
    (is (true? (:ok e)) (pr-str e))
    (is (= "stores link" (:command e)))
    (is (= {:name "bee" :path "../../b/.kimun"} (get-in e [:data :link])))
    (is (= [{:name "bee" :path "../../b/.kimun"}] (:links (descriptor-of a))))
    (is (str/includes? (slurp (str (fs/path a ".kimun" "store.edn")))
                       ":links [{:name \"bee\", :path \"../../b/.kimun\"}]"))
    (is (= (str (fs/path root "b" ".kimun")) (get-in e [:data :resolved])))
    (testing "by .kimun directory, absolute"
      (let [c (init-in root "c")
            e2 (run a "stores" "link" "cee" (str (fs/path c ".kimun")))]
        (is (true? (:ok e2)) (pr-str e2))
        (is (= "../../c/.kimun" (get-in e2 [:data :link :path])))))
    (testing "no stored path is absolute"
      (is (not-any? #(fs/absolute? (:path %)) (:links (descriptor-of a)))))
    (testing "check and show print the links"
      (is (= 2 (count (get-in (run a "stores" "check") [:data :links]))))
      (is (= 2 (count (get-in (run a "stores" "show") [:data :links])))))))

(deftest link-rejects-duplicates-self-bad-names-and-non-stores
  ;; v1: test_store_add_fails_if_already_linked, test_store_add_fails_on_invalid_path,
  ;;     test_link_store_refuses_a_directory_without_store
  (let [root (tmp)
        a (init-in root "a")
        b (init-in root "b")
        _ (run a "stores" "link" "bee" b)
        before (slurp (str (fs/path a ".kimun" "store.edn")))]
    (testing "duplicate name"
      (let [e (run a "stores" "link" "bee" b)]
        (is (false? (:ok e)))
        (is (= "store/link-exists" (get-in e [:error :type])))
        (is (= 5 (:exit e)))))
    (testing "self link, by project and by .kimun"
      (doseq [p [a (str (fs/path a ".kimun")) "."]]
        (let [e (run a "stores" "link" "me" p)]
          (is (= "store/self-link" (get-in e [:error :type])) (pr-str e))
          (is (= 5 (:exit e))))))
    (testing "target without a store"
      (let [nothing (str (fs/create-dirs (fs/path root "nothing")))
            e (run a "stores" "link" "x" nothing)
            e2 (run a "stores" "link" "y" (str (fs/path root "nonexistent")))]
        (is (= "store/not-a-store" (get-in e [:error :type])))
        (is (= 5 (:exit e)))
        (is (= "store/not-a-store" (get-in e2 [:error :type])))))
    (testing "invalid names"
      (doseq [n ["" "a/b" " x" ".x"]]
        (let [e (run a "stores" "link" n b)]
          (is (= "store/invalid-link-name" (get-in e [:error :type])) (pr-str n))
          (is (= 5 (:exit e))))))
    (testing "wrong arity is usage"
      (is (= 6 (:exit (run a "stores" "link" "only-name"))))
      (is (= 6 (:exit (run a "stores" "unlink")))))
    (is (= before (slurp (str (fs/path a ".kimun" "store.edn")))) "failures leave store.edn untouched")))

(deftest unlink-removes-a-link
  (let [root (tmp)
        a (init-in root "a")
        b (init-in root "b")
        _ (run a "stores" "link" "bee" b)
        e (run a "stores" "unlink" "bee")]
    (is (true? (:ok e)) (pr-str e))
    (is (= "bee" (get-in e [:data :unlinked :name])))
    (is (= [] (:links (descriptor-of a))))
    (let [again (run a "stores" "unlink" "bee")]
      (is (= "store/missing" (get-in again [:error :type])))
      (is (= 1 (:exit again)))
      (is (= [] (get-in again [:error :data :known]))))))

(deftest links-and-check-report-a-missing-target-as-a-warning
  (let [root (tmp)
        a (init-in root "a")
        b (init-in root "b")
        _ (run a "stores" "link" "bee" b)
        ok (run a "stores" "links")]
    (is (= [:ok] (mapv :status (get-in ok [:data :links]))))
    (is (= [] (:warnings ok)))
    (is (= [] (:warnings (run a "stores" "check"))))
    (fs/delete-tree (fs/path b ".kimun"))
    (doseq [cmd ["links" "check"]]
      (let [e (run a "stores" cmd)]
        (is (true? (:ok e)) (pr-str e))
        (is (= [:missing] (mapv :status (get-in e [:data :links]))))
        (is (= 1 (count (:warnings e))))
        (is (= "link/missing" (get-in e [:warnings 0 :type])))
        (is (str/includes? (get-in e [:warnings 0 :message]) "bee"))))
    (testing "a directory without store.edn"
      (fs/create-dirs (fs/path b ".kimun"))
      (is (= [:not-a-store] (mapv :status (get-in (run a "stores" "links") [:data :links])))))))

(deftest hand-edited-absolute-links-are-rejected-on-open
  (let [root (tmp)
        a (init-in root "a")
        b (init-in root "b")
        f (str (fs/path a ".kimun" "store.edn"))]
    (spit f (pr-str (assoc (descriptor-of a) :links [{:name "bee" :path (str (fs/path b ".kimun"))}])))
    (let [e (run a "stores" "check")]
      (is (false? (:ok e)))
      (is (= "store/invalid-links" (get-in e [:error :type])))
      (is (= 5 (:exit e))))))

(deftest cycles-do-not-loop
  (let [root (tmp)
        a (init-in root "a")
        b (init-in root "b")
        _ (run a "stores" "link" "bee" b)
        _ (run b "stores" "link" "ay" a)
        da (str (fs/path a ".kimun"))]
    (is (true? (:ok (run a "stores" "check"))))
    (is (true? (:ok (run b "stores" "links"))))
    (testing "direct links only by default"
      (is (= ["bee"] (mapv :name (cli-store/linked-stores da)))))
    (testing "transitive terminates and never returns the origin"
      (is (= ["bee"] (mapv :name (cli-store/linked-stores da {:transitive? true})))))
    (testing "a chain is followed transitively, named by path"
      (let [c (init-in root "c")
            _ (run b "stores" "link" "cee" c)]
        (is (= ["bee"] (mapv :name (cli-store/linked-stores da))))
        (is (= ["bee" "bee/cee"] (mapv :name (cli-store/linked-stores da {:transitive? true}))))
        (is (every? #(some? (get-in % [:open :store])) (cli-store/linked-stores da)))))))

(deftest linked-stores-skips-unreachable-targets
  (let [root (tmp)
        a (init-in root "a")
        b (init-in root "b")
        c (init-in root "c")
        _ (run a "stores" "link" "bee" b)
        _ (run a "stores" "link" "cee" c)
        _ (fs/delete-tree (fs/path b ".kimun"))]
    (is (= ["cee"] (mapv :name (cli-store/linked-stores (str (fs/path a ".kimun"))))))))

(deftest moving-both-stores-together-keeps-the-link-valid
  (let [root (tmp)
        a (init-in root "a")
        b (init-in root "b")
        _ (run a "stores" "link" "bee" b)
        moved (str (fs/path (tmp) "elsewhere"))
        _ (fs/move root moved)
        e (run (str (fs/path moved "a")) "stores" "links")]
    (is (true? (:ok e)) (pr-str e))
    (is (= [:ok] (mapv :status (get-in e [:data :links]))))
    (is (= (str (fs/path moved "b" ".kimun")) (get-in e [:data :links 0 :resolved])))
    (is (= [] (:warnings e)))))

;; ------------------------------------------------------------ --store NAME

(def ^:private ticket-model
  (pr-str {:model/name "TicketDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}]
           :template "# ⸢rev•title⸥\n"}))

(deftest store-alias-is-resolved-from-an-ancestor-registry
  ;; v1: test_store_alias_is_resolved_from_an_ancestor_registry
  (let [root (tmp)
        parent (init-in root "parent")
        child (init-in parent "child")
        remote (init-in root "remote")
        _ (is (true? (:ok (run parent "stores" "link" "remote" remote))))
        nested (str (fs/create-dirs (fs/path child "src")))
        e (run nested "stores" "show" "--store" "remote")]
    (is (true? (:ok e)) (pr-str e))
    (is (= "remote" (get-in e [:data :name])) "reaches the linked store, not the child's own")
    (is (= (str (fs/path remote ".kimun")) (get-in e [:data :path])))
    (testing "no absolute path is stored for the link"
      (is (not-any? #(fs/absolute? (:path %)) (:links (descriptor-of parent)))))))

(deftest store-alias-registered-in-the-current-store-resolves
  (let [root (tmp)
        a (init-in root "a")
        _b (init-in root "b")
        _ (run a "stores" "link" "bee" "../b")
        e (run a "stores" "show" "--store" "bee")]
    (is (true? (:ok e)) (pr-str e))
    (is (= "b" (get-in e [:data :name])))
    (testing "the nearest registry wins"
      (let [near (init-in a "near")
            _ (run near "stores" "link" "bee" "../../a")
            e2 (run near "stores" "show" "--store" "bee")]
        (is (= "a" (get-in e2 [:data :name])))))))

(deftest existing-path-wins-over-a-link-of-the-same-name
  (let [root (tmp)
        a (init-in root "a")
        _b (init-in root "b")
        _ (run a "stores" "link" "shadow" "../b")
        local (init-in a "shadow")
        e (run a "stores" "show" "--store" (str (fs/path local)))]
    (is (= "shadow" (get-in e [:data :name])))
    (testing "a relative existing path (cwd-relative) is a path, not a link"
      (let [cwd (System/getProperty "user.dir")
            rel (str (fs/relativize cwd (fs/path local)))
            e2 (run a "stores" "show" "--store" rel)]
        (is (= "shadow" (get-in e2 [:data :name])) (pr-str e2))))))

(deftest unknown-store-name-is-no-store-and-says-it-is-not-a-link
  (let [root (tmp)
        a (init-in root "a")
        e (run a "stores" "show" "--store" "nope")]
    (is (false? (:ok e)))
    (is (= 5 (:exit e)))
    (is (= "cli/no-store" (get-in e [:error :type])))
    (is (str/includes? (get-in e [:error :message]) "not a registered link"))
    (is (= "nope" (get-in e [:error :data :name])))
    (testing "a dangling link is reported, not silently skipped"
      (let [d (init-in root "d")
            _ (run a "stores" "link" "dd" "../d")
            _ (fs/delete-tree (fs/path d ".kimun"))
            e2 (run a "stores" "show" "--store" "dd")]
        (is (= "cli/no-store" (get-in e2 [:error :type])))
        (is (str/includes? (get-in e2 [:error :message]) "dd"))))))

;; v1: test_docs_create_accepts_linked_store_alias. PARTIAL: `--store <alias>` now reaches
;; the linked store (asserted here); the second half of the v1 promise (the file lands in
;; the TARGET project, `target/desk/inbox/x.md`) is deferred: `docs create` writes relative
;; to the cwd (stored path "../central/req-123.md"), it needs docs.cljc to root file
;; writes/paths at the resolved store's project directory.
;; v1: test_docs_create_uses_model_from_linked_store_namespace is deferred too: it needs the
;; model looked up in a third store linked from the current one (federated model lookup).
(deftest docs-create-through-a-linked-store-alias-tracks-in-the-target
  (let [root (tmp)
        central (init-in root "central")
        target (init-in root "target")
        _ (spit (str (fs/path target "ticket.edn")) ticket-model)
        _ (is (true? (:ok (run target "models" "define" "-f" "ticket.edn"))))
        _ (run central "stores" "link" "target" "../target")
        e (run central "docs" "create" "TicketDoc" "req-123" "--set" "title=Need review" "--store" "target")]
    (is (true? (:ok e)) (pr-str e))
    (is (= "target" (get-in e [:store :name])))
    (is (= ["req-123"] (mapv :name (get-in (run target "docs" "list") [:data]))))
    (is (empty? (get-in (run central "docs" "list") [:data])) "nothing tracked in the central store")))
