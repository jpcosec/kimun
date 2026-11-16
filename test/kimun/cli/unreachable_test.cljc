(ns kimun.cli.unreachable-test
  "`kimun stores unreachable` end to end (K5, first cut): a read-only report of
   the stored objects the heads do not reach, by shape and with their bytes,
   that never changes the store."
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.surface.markdown.ast :as ast]
            [kimun.test-util :as ktu]))

(def env {"USER" "tester"})

(defn- run [cwd & argv] (binding [ast/*parser* "native"] (:envelope (main/run argv env cwd))))
(defn- data [cwd & argv] (:data (apply run cwd argv)))
(defn- file [p & parts] (str (apply fs/path p parts)))

(defn- project
  "A store with two generic documents and two later edits of one of them."
  []
  (let [p (str (ktu/temp-dir "kimun-cli-unreachable-"))]
    (is (true? (:ok (run p "stores" "init" "--name" "p"))))
    (fs/create-dirs (file p "notes"))
    (spit (file p "notes" "a.md") "# A\n\none\n")
    (spit (file p "notes" "b.md") "# B\n\none\n")
    (is (true? (:ok (run p "docs" "ingest" "notes/a.md"))))
    (is (true? (:ok (run p "docs" "ingest" "notes/b.md"))))
    (doseq [text ["two" "three"]]
      (spit (file p "notes" "a.md") (str "# A\n\n" text "\n"))
      (is (true? (:ok (run p "docs" "update" "notes/a.md" "--name" "a")))))
    p))

(defn- snapshot [dir]
  (into (sorted-map)
        (map (fn [f] [(str (fs/relativize dir f)) (slurp (str f))]))
        (filter fs/regular-file? (fs/glob dir "**" {:hidden true}))))

(deftest the-report-counts-what-the-heads-do-not-reach
  (let [p (project)
        r (run p "stores" "unreachable")
        d (:data r)
        check (data p "stores" "check")]
    (is (true? (:ok r)) (pr-str r))
    (is (nil? (:exit r)))
    (is (= "stores unreachable" (:command r)))
    (is (re-find #"not known to be safe to remove" (:note d)) "the answer says of itself that it is not a list of garbage")
    (is (re-find #"nothing was written or removed" (:note d)))
    (is (= (:objects check) (:stored d)) "the same objects `stores check` counts")
    (is (= (:stored d) (+ (:reached d) (:unreachable d))))
    (is (pos? (:unreachable d)) "three later commits replaced the heads map and route nodes")
    (is (= (:unreachable d) (get-in d [:candidates :count])))
    (is (= [] (:damaged d)))
    (is (= [] (:lost-revisions d)))
    (is (true? (:stable? d)))
    (is (= [] (:warnings r)))
    (testing "by shape, with bytes that add up"
      (let [by (get-in d [:candidates :by-class])]
        (is (contains? by "route-node"))
        (is (some #{"mmap-leaf" "mmap-branch"} (keys by)))
        (is (= (get-in d [:candidates :count]) (reduce + (map :count (vals by)))))
        (is (= (get-in d [:candidates :bytes]) (reduce + (map :bytes (vals by)))))
        (is (pos? (get-in d [:candidates :bytes])))))
    (testing "the tops are fewer than the candidates and among them"
      (is (pos? (get-in d [:roots :count])))
      (is (<= (get-in d [:roots :count]) (get-in d [:candidates :count]))))
    (is (nil? (:ids d)) "ids only on request")))

(deftest ids-are-listed-on-request-and-are-the-files
  (let [p (project)
        d (data p "stores" "unreachable" "--ids")
        ids (get-in d [:ids :candidates])]
    (is (= (get-in d [:candidates :count]) (count ids)))
    (is (= (get-in d [:candidates :bytes]) (reduce + (map :bytes ids))))
    (is (= (get-in d [:roots :count]) (count (filter :root? ids))))
    (is (every? #(fs/exists? (file p ".kimun" "objects" (:id %))) ids))
    (is (every? #(= (:bytes %) (fs/size (file p ".kimun" "objects" (:id %)))) ids))))

(deftest the-command-changes-nothing
  (let [p (project)
        before (snapshot (file p ".kimun"))]
    (run p "stores" "unreachable")
    (run p "stores" "unreachable" "--ids")
    (is (= before (snapshot (file p ".kimun"))) "every file under the store is byte-identical")
    (is (true? (:ok (run p "stores" "verify"))))))

(deftest a-new-store-has-nothing-unreachable
  (let [p (str (ktu/temp-dir "kimun-cli-unreachable-"))]
    (run p "stores" "init" "--name" "p")
    (is (= {:stored 0 :reached 0 :unreachable 0}
           (select-keys (data p "stores" "unreachable") [:stored :reached :unreachable])))))

(deftest without-a-store-it-fails-as-every-command-does
  (let [p (str (ktu/temp-dir "kimun-cli-unreachable-"))
        r (run p "stores" "unreachable")]
    (is (false? (:ok r)))
    (is (= 5 (:exit r)))
    (is (= "cli/no-store" (get-in r [:error :type])))))
