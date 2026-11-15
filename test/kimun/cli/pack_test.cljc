(ns kimun.cli.pack-test
  "`kimun stores pack` and `stores unpack` end to end (K4, first cut): packing
   moves the small loose objects into a pack and removes them, unpacking writes
   them back, and every read the CLI offers answers the same before and after.
   docs/v2/06 §3, docs/v2/10 §11.4."
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
  (let [p (str (ktu/temp-dir "kimun-cli-pack-"))]
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

(defn- everything
  "The read side of the CLI, as data, for before/after comparison."
  [p]
  {:docs (data p "docs" "list")
   :show (data p "docs" "show" "a")
   :journal (data p "journal" "show")
   :check (data p "stores" "check")
   :verify (data p "stores" "verify")})

(deftest pack-then-unpack-changes-no-answer
  (let [p (project)
        before (everything p)
        r (run p "stores" "pack")]
    (is (true? (:ok r)) (pr-str r))
    (is (nil? (:exit r)))
    (is (= "stores pack" (:command r)))
    (let [d (:data r)]
      (is (pos? (:packed d)))
      (is (string? (:pack d)))
      (is (zero? (:kept-loose d)))
      (is (= [] (:skipped-damaged d)))
      (is (= 1 (:packs d)))
      (is (pos? (:checked d))))
    (is (fs/exists? (file p ".kimun" "packs")) "the pack layout is there")
    (is (= before (everything p)) "packing changes no answer of the CLI")
    (let [u (run p "stores" "unpack")]
      (is (true? (:ok u)) (pr-str u))
      (is (= "stores unpack" (:command u)))
      (is (= 1 (get-in u [:data :packs])))
      (is (pos? (get-in u [:data :unpacked])))
      (is (empty? (fs/glob (file p ".kimun" "packs") "*.pack")))
      (is (= before (everything p)) "unpacking changes no answer of the CLI"))))

(deftest a-second-pack-with-nothing-new-packs-nothing
  (let [p (project)]
    (is (true? (:ok (run p "stores" "pack"))))
    (let [d (data p "stores" "pack")]
      (is (zero? (:packed d)))
      (is (nil? (:pack d)))
      (is (= 0 (:kept-loose d))))))

(deftest max-bytes-is-validated-and-respected
  (let [p (project)]
    (doseq [bad ["0" "-1" "much" "16777217"]]
      (let [e (run p "stores" "pack" "--max-bytes" bad)]
        (is (= ["cli/usage" 6] [(get-in e [:error :type]) (:exit e)]) (str "--max-bytes " bad))))
    (let [d (data p "stores" "pack" "--max-bytes" "1")]
      (is (zero? (:packed d)))
      (is (nil? (:pack d))))
    (let [d (data p "stores" "pack" "--max-bytes" "20000")]
      (is (pos? (:packed d)))
      (is (:ok (run p "stores" "verify"))))))

(defn- store-snapshot
  "Every file under the store with its bytes and last-modified time."
  [p]
  (into (sorted-map)
        (map (fn [f] [(str (fs/relativize (file p ".kimun") f))
                      [(str (fs/last-modified-time f)) (count (slurp (str f)))]])
             (filter fs/regular-file? (fs/glob (file p ".kimun") "**" {:hidden true})))))

(deftest reading-commands-never-write-the-index
  (let [p (project)
        _ (is (true? (:ok (run p "stores" "pack"))))
        idx (first (fs/glob (file p ".kimun" "packs") "*.idx"))]
    (is (some? idx))
    (fs/delete idx)
    (let [before (store-snapshot p)
          sense (run p "stores" "sense" "--once")
          unreach (run p "stores" "unreachable")]
      (is (true? (:ok sense)) (pr-str sense))
      (is (true? (:ok unreach)) (pr-str unreach))
      (is (empty? (fs/glob (file p ".kimun" "packs") "*.idx")) "no index was written")
      (is (= before (store-snapshot p)) "`.kimun/` is byte-for-byte the same")
      (testing "the explicit repair is `stores pack`"
        (let [d (data p "stores" "pack")]
          (is (= 1 (:indexes-repaired d)))
          (is (zero? (:packed d)))
          (is (= 1 (count (fs/glob (file p ".kimun" "packs") "*.idx")))))))))

(deftest packs-are-a-format-4-layout
  (let [p (project)
        store-edn (file p ".kimun" "store.edn")
        text (slurp store-edn)
        three (clojure.string/replace text ":format-version 4" ":format-version 3")]
    (spit store-edn three)
    (doseq [sub ["pack" "unpack"]]
      (let [e (run p "stores" sub)]
        (is (some? (:error e)) (str sub " on format 3 must fail"))
        (is (= 5 (:exit e)))
        (is (= "store/error" (get-in e [:error :type])))))
    (spit store-edn text)
    (is (:ok (run p "stores" "pack")))))

(deftest packing-keeps-the-store-verifiable-with-a-commit-in-between
  (let [p (project)
        before (everything p)]
    (is (true? (:ok (run p "stores" "pack"))))
    (spit (file p "notes" "b.md") "# B\n\nfour\n")
    (is (true? (:ok (run p "docs" "update" "notes/b.md" "--name" "b"))))
    (is (:ok (run p "stores" "verify")))
    (is (:ok (run p "stores" "check")))
    (is (not= before (everything p)) "the edit is visible")
    (is (:ok (run p "stores" "unpack")))
    (is (:ok (run p "stores" "verify")))))
