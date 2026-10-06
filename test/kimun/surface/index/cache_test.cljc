(ns kimun.surface.index.cache-test
  "The index cache `.kimun/index/<head>.edn`: hits, every way it can be
   wrong (absent, stale, corrupt), that a read never writes, what `rebuild!`
   writes and reports, and the federated view. Each ported test names its v1
   function in a comment."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.surface.index.build :as build]
            [kimun.surface.index.cache :as cache]
            [kimun.surface.models.store :as mstore]
            [sldb.host.default :as host]
            [sldb.kernel.store :as store]))

(def env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- opened [p] (cli-store/open! (cli-store/resolve-store {} env p)))

(defn- ticket [fields template]
  {:model/name "TicketDoc" :model/version 1 :base [] :semantics {"type" ["ticket"]}
   :fields (into [{:name "title" :type :string :required true :description "Title."}] fields)
   :template template})

(defn- project
  "A store named `nm` with TicketDoc and the documents login and logout."
  [nm]
  (let [p (str (fs/create-temp-dir {:prefix "kimun-cache-"}))]
    (run p "stores" "init" "--name" nm)
    (spit (str (fs/path p "ticket.edn")) (pr-str (ticket [] "# ⸢rev•title⸥\n")))
    (is (true? (:ok (run p "models" "define" "-f" "ticket.edn"))))
    (is (true? (:ok (run p "docs" "create" "TicketDoc" "login" "--set" "title=Broken login"))))
    (is (true? (:ok (run p "docs" "create" "TicketDoc" "logout" "--set" "title=Slow logout"))))
    p))

(defn- snapshot
  "Every file under the store directory of `p` with its bytes."
  [p]
  (into (sorted-map)
        (map (fn [f] [(str f) (vec (fs/read-all-bytes f))]))
        (filter fs/regular-file? (fs/glob (fs/path p ".kimun") "**" {:hidden true}))))

(defn- cache-file [p] (let [o (opened p)] (cache/cache-path (:path o) (:head (:store o)))))

(deftest a-read-never-writes
  (let [p (project "p") before (snapshot p) o (opened p)]
    (is (= {:source :built :reason :no-cache} (dissoc (cache/lookup! o) :index)))
    (is (= (build/build (:store o)) (cache/load! o)))
    (cache/federated! o)
    (is (= before (snapshot p)) "no file under .kimun/ was created or modified")
    (is (not (fs/exists? (fs/path p ".kimun" "index"))))
    (testing "nor with a cache present, valid or not"
      (cache/rebuild! o)
      (let [with-cache (snapshot p)]
        (cache/load! (opened p))
        (spit (cache-file p) "{:broken")
        (let [corrupt (snapshot p)]
          (cache/load! (opened p))
          (is (= corrupt (snapshot p)) "a corrupt cache is not repaired by a read"))
        (is (= (set (keys with-cache)) (set (keys (snapshot p)))))))
    (testing "a bare kernel store has no directory: always built"
      (is (= :no-dir (:reason (cache/lookup! (:store o)))))
      (is (= (build/build (:store o)) (cache/load! (:store o)))))))

(deftest rebuild-writes-the-cache-and-a-read-hits-it
  (let [p (project "p") o (opened p) head (:head (:store o))
        report (cache/rebuild! o)
        path (str (fs/path p ".kimun" "index" (str head ".edn")))]
    (is (fs/regular-file? path))
    (is (= path (cache-file p)))
    (is (= (str (fs/path "index" (str head ".edn"))) (:cache report)) "relative to the store directory")
    (is (= (build/build (:store o)) (:index report) (edn/read-string (slurp path))))
    (is (= "*\n" (slurp (str (fs/path p ".kimun" "index" ".gitignore")))) "derived, kept out of version control")
    (is (= {:source :cache} (dissoc (cache/lookup! (opened p)) :index)))
    (is (= (build/build (:store o)) (cache/load! (opened p))))
    (is (true? (:ok (run p "stores" "check"))) "the cache directory is not noise for the store")
    (is (true? (:ok (run p "stores" "verify"))))))

(deftest a-stale-cache-is-rebuilt
  (let [p (project "p") old (cache-file p)]
    (cache/rebuild! (opened p))
    (is (true? (:ok (run p "docs" "create" "TicketDoc" "signup" "--set" "title=Signup"))))
    (let [o (opened p) r (cache/lookup! o)]
      (is (not= old (cache-file p)) "the head moved")
      (is (= {:source :built :reason :no-cache} (dissoc r :index)))
      (is (= (build/build (:store o)) (:index r)))
      (is (contains? (:by-name (:index r)) "signup"))
      (testing "even when the old index sits under the new head's name"
        (fs/copy old (cache-file p))
        (let [r2 (cache/lookup! (opened p))]
          (is (= {:source :built :reason :stale} (dissoc r2 :index)))
          (is (contains? (:by-name (:index r2)) "signup"))))
      (testing "or has another format version"
        (spit (cache-file p) (pr-str (assoc (build/build (:store o)) :version -1)))
        (is (= :stale (:reason (cache/lookup! (opened p))))))
      (testing "rebuild! replaces it and prunes the caches of other heads"
        (let [report (cache/rebuild! (opened p))]
          (is (= 1 (:pruned report)))
          (is (not (fs/exists? old)))
          (is (= {:source :cache} (dissoc (cache/lookup! (opened p)) :index))))))))

(deftest a-corrupt-cache-never-fails-a-read
  (let [p (project "p") o (opened p) expected (build/build (:store o))]
    (cache/rebuild! o)
    (doseq [[what text] [["garbage" "\u0000\u0001 not edn ((("]
                         ["truncated" (let [t (slurp (cache-file p))] (subs t 0 (quot (count t) 2)))]
                         ["empty" ""]
                         ["another value" "[1 2 3]"]
                         ["unknown tag" "#object[x 1]"]]]
      (spit (cache-file p) text)
      (let [r (cache/lookup! (opened p))]
        (is (= :built (:source r)) what)
        (is (contains? #{:corrupt :stale} (:reason r)) what)
        (is (= expected (:index r)) what)))
    (testing "a directory where the file should be"
      (fs/delete (cache-file p))
      (fs/create-dirs (cache-file p))
      (is (= expected (cache/load! (opened p)))))))

(deftest an-empty-store-has-no-cache-file
  (let [p (str (fs/create-temp-dir {:prefix "kimun-cache-"}))]
    (run p "stores" "init" "--name" "p")
    (let [o (opened p) head (:head (:store o)) report (cache/rebuild! o)]
      (is (= 0 (:documents report) (:models report)))
      (is (true? (:complete report)))
      (if head
        (is (= {:source :cache} (dissoc (cache/lookup! (opened p)) :index)))
        (do (is (nil? (:cache report)))
            (is (= :no-head (:reason (cache/lookup! (opened p))))))))))

;; v1: test_update_store_indexes_reports_a_complete_refresh
(deftest rebuild-reports-a-complete-refresh
  (let [p (project "p") o (opened p) report (cache/rebuild! o)]
    (is (true? (:complete report)))
    (is (= [] (:skipped report)))
    (is (= (:head (:store o)) (:head report)))
    (is (= 2 (:documents report)))
    (is (= 1 (:models report)))
    (is (pos? (:tags report)) "v1 semantic_index.docs_processed: the tags the documents carry")
    (is (pos? (:bytes report)))
    (is (not (fs/absolute? (:cache report))) "v1 reported the absolute store_path; kimun reports relative to the store")))

;; v1: test_update_store_indexes_skips_a_missing_document (API half; the CLI half is in kimun.cli.index-test)
(deftest rebuild-skips-a-document-that-no-longer-extracts
  (let [p (project "p")]
    (testing "v1 skipped a document whose file was gone; in kimun the source lives in the store, so nothing is skipped"
      (fs/delete (fs/path p "login.md"))
      (let [report (cache/rebuild! (opened p))]
        (is (= [] (:skipped report)))
        (is (true? (:complete report)))))
    (testing "what is skipped is a document its model's contract no longer reads"
      (let [{:keys [backend] s :store} (opened p)
            r (mstore/define-plan host/host s (ticket [{:name "owner" :type :string :required true :description "Owner."}]
                                                      "# ⸢rev•title⸥\n\nOwner: ⸢rev•owner⸥\n")
                                  {:actor "human/tester" :timestamp "2026-01-01T00:00:00.000Z"})]
        (store/commit! backend s (:plan r)))
      (let [report (cache/rebuild! (opened p))]
        (is (= ["login" "logout"] (:skipped report)))
        (is (false? (:complete report)))
        (is (= 2 (:documents report)) "still indexed, without payload")
        (is (= {:source :cache} (dissoc (cache/lookup! (opened p)) :index)) "and the cache it wrote is valid")))))

(deftest the-federated-view-lists-the-store-then-its-links
  (let [a (project "alpha") b (project "beta")]
    (is (true? (:ok (run a "stores" "link" "b" b))))
    (let [view (cache/federated! (opened a))]
      (is (= ["alpha" "b"] (mapv :store view)) "own name first, then link names in link order")
      (is (= (build/build (:store (opened a))) (:index (first view))))
      (is (= (build/build (:store (opened b))) (:index (second view)))))
    (is (= ["beta"] (mapv :store (cache/federated! (opened b)))))))
