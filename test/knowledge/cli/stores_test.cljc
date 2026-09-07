(ns knowledge.cli.stores-test
  "The `stores` group end to end on temporary directories: init layout,
   duplicate init, check/verify/show, corruption, and store discovery
   precedence (docs/v2/06 §B)."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [babashka.fs :as fs]
            [knowledge.cli.main :as main]
            [knowledge.cli.store :as cli-store]
            [sldb.kernel.store :as store]))

(def env {"USER" "tester"})

(defn- tmp [] (str (fs/create-temp-dir {:prefix "knowledge-cli-"})))

(defn- run
  ([cwd & argv] (:envelope (main/run argv env cwd))))

(defn- run-env [env cwd & argv] (:envelope (main/run argv env cwd)))

(deftest init-creates-the-store-layout
  (let [proj (tmp)
        e (run proj "stores" "init")
        dir (str (fs/path proj ".knowledge"))
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
    (is (= :all (get-in descriptor [:capabilities "knowledge/derive"])))
    (is (= "human/tester" (get-in e [:data :actor])))
    (testing "--name and --actor are honoured"
      (let [proj2 (tmp)
            e2 (run proj2 "stores" "init" "--name" "named" "--actor" "agent/x")
            d2 (edn/read-string (slurp (str (fs/path proj2 ".knowledge" "store.edn"))))]
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
    (is (str/includes? (get-in e [:error :message]) ".knowledge"))))

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
    (is (= (str (fs/path proj ".knowledge")) (get-in e [:store :path])))))

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
        dir (str (fs/path proj ".knowledge"))
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
    (is (= (str (fs/path proj ".knowledge")) (get-in e [:store :path])))
    (is (= (str (fs/path proj ".knowledge")) (cli-store/discover nested)))))

(deftest explicit-store-wins-over-discovery
  (let [proj-a (tmp) proj-b (tmp)
        _ (run proj-a "stores" "init" "--name" "a")
        _ (run proj-b "stores" "init" "--name" "b")
        by-project (run proj-a "stores" "check" "--store" proj-b)
        by-dir (run proj-a "stores" "check" "--store" (str (fs/path proj-b ".knowledge")))]
    (is (= "b" (get-in by-project [:store :name])) "--store accepts the project directory")
    (is (= "b" (get-in by-dir [:store :name])) "--store accepts the .knowledge directory")
    (is (= (str (fs/path proj-b ".knowledge")) (get-in by-dir [:store :path])))))

(deftest env-store-wins-over-walk-up-but-not-over-flag
  (let [proj-a (tmp) proj-b (tmp) proj-c (tmp)
        _ (run proj-a "stores" "init" "--name" "a")
        _ (run proj-b "stores" "init" "--name" "b")
        _ (run proj-c "stores" "init" "--name" "c")
        env' (assoc env "KNOWLEDGE_STORE" proj-b)
        by-env (run-env env' proj-a "stores" "check")
        by-flag (run-env env' proj-a "stores" "check" "--store" proj-c)]
    (is (= "b" (get-in by-env [:store :name])))
    (is (= "c" (get-in by-flag [:store :name])))
    (is (= (str (fs/path proj-b ".knowledge"))
           (cli-store/resolve-store {} env' proj-a)))))

(deftest unknown-subcommand-lists-the-known-ones
  (let [e (run (tmp) "stores" "nope")]
    (is (= 6 (:exit e)))
    (is (= "cli/usage" (get-in e [:error :type])))
    (is (= ["check" "init" "show" "verify"] (get-in e [:error :data :known])))))
