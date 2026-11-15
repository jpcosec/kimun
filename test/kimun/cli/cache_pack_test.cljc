(ns kimun.cli.cache-pack-test
  "K4b: the derived cache packs (`cache/packs`) are cache, not store. Nothing of
   the store's inventory or verification sees them, every read from them is
   verified by hash, a cache pack a reader cannot load is ignored in memory
   (no write, no failure), and `stores pack --cache` is the explicit repair
   (docs/v2/10 §11.5)."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.host.store-cache :as store-cache]
            [kimun.host.fs-store :as fs-store]
            [kimun.kernel.store :as kstore]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.markdown.ast :as ast]
            [kimun.test-util :as ktu]))

(def env {"USER" "tester"})

(defn- run [cwd & argv] (binding [ast/*parser* "native"] (:envelope (main/run argv env cwd))))
(defn- file [p & parts] (str (apply fs/path p parts)))

(def ticket
  (pr-str {:model/name "TicketDoc" :model/version 1 :base []
           :fields [{:name "title" :type :string :required true :description "Title."}
                    {:name "status" :type :string :required true :description "Status."}]
           :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"}))

(def ticket-text "# T\n\nStatus: open\n")
(def plain-text "# Note\n\nPara one.\n\n- a\n- b\n")

(defn- project
  "A store with the typed document `tickets/login.md` (TicketDoc) and the
   generic document `notes/plain.md` (ingested)."
  []
  (let [p (str (ktu/temp-dir "kimun-cache-verified-"))]
    (is (true? (:ok (run p "stores" "init" "--name" "p"))))
    (spit (file p "ticket.edn") ticket)
    (is (true? (:ok (run p "models" "define" "-f" "ticket.edn"))))
    (fs/create-dirs (file p "tickets")) (fs/create-dirs (file p "notes"))
    (spit (file p "tickets" "login.md") ticket-text)
    (spit (file p "notes" "plain.md") plain-text)
    (is (true? (:ok (run p "docs" "track" "TicketDoc" "tickets/login.md"))))
    (is (true? (:ok (run p "docs" "ingest" "notes/plain.md"))))
    p))

(defn- opened [p] (:store (cli-store/open! (file p ".kimun"))))

(defn- cache-dir [p] (store-cache/objects-dir (file p ".kimun")))

(defn- cached-objects
  "`{id {:text … :value …}}` for the `:kind :text` cache objects under `p`."
  [p]
  (into {}
        (keep (fn [f]
                (let [id (str (fs/file-name f))
                      t (slurp (str f))]
                  (try (when (= :text (:kind (edn/read-string t))) [id {:text t :value (edn/read-string t)}])
                       (catch Exception _ nil)))))
        (fs/list-dir (cache-dir p))))

(defn- aligned-text-object
  "The cache object of the `:kind :text` nodes that carries `snippet`."
  [p snippet]
  (->> (cached-objects p)
       (filter (fn [[_ {:keys [value]}]] (clojure.string/includes? (str (get-in value [:content :text])) snippet)))
       first))

(defn- cache-packs-dir [p] (file p ".kimun" "cache" "packs"))
(defn- pack-names [p] (sort (map #(str (fs/file-name %)) (fs/glob (cache-packs-dir p) "*.pack"))))
(defn- pack-of [p nm] (file (cache-packs-dir p) nm))
(defn- idx-of [p nm] (file (cache-packs-dir p) (str (subs nm 0 (- (count nm) 5)) ".idx")))
(defn- cache-objects [p]
  (let [d (store-cache/objects-dir (file p ".kimun"))]
    (if (fs/exists? d) (mapv #(str (fs/file-name %)) (fs/list-dir d)) [])))
(defn- snapshot
  "Every file under `.kimun/` with its bytes (as text) and last-modified time."
  [p]
  (into (sorted-map)
        (map (fn [f] [(str (fs/relativize (file p ".kimun") f))
                      [(str (fs/last-modified-time f)) (count (slurp (str f)))]])
             (filter fs/regular-file? (fs/glob (file p ".kimun") "**" {:hidden true})))))

(defn- verify [p]
  (let [s (opened p)]
    (kimun.kernel.store/verify (:backend (cli-store/open! (file p ".kimun"))) s)))

(deftest the-packed-cache-is-deletable
  (let [p (project)
        _ (is (true? (:ok (run p "stores" "pack" "--cache"))))
        before (get-in (run p "docs" "render" "plain") [:data])]
    (is (= plain-text before))
    (fs/delete-tree (file p ".kimun" "cache"))
    (is (= plain-text (get-in (run p "docs" "render" "plain") [:data])))
    (is (= "open" (get-in (run p "docs" "show" "login") [:data :payload "status"])))
    (is (:ok (run p "stores" "verify")))))

(deftest verify-and-object-ids-exclude-the-cache
  (let [p (project)
        s (opened p)
        ids (set (kimun.kernel.store/object-ids (:backend (cli-store/open! (file p ".kimun")))))
        inv (kimun.kernel.store/unreachable (:backend (cli-store/open! (file p ".kimun"))) s)
        checked (get-in (run p "stores" "verify") [:data :checked])]
    (is (true? (:ok (run p "stores" "pack" "--cache"))))
    (is (= ids (set (kimun.kernel.store/object-ids (:backend (cli-store/open! (file p ".kimun")))))))
    (is (= checked (get-in (run p "stores" "verify") [:data :checked])))
    (is (= (select-keys inv [:stored :reached :unreachable])
           (select-keys (kimun.kernel.store/unreachable (:backend (cli-store/open! (file p ".kimun")))
                                                        (opened p))
                        [:stored :reached :unreachable])))
    (testing "and a damaged cache pack does not break verify"
      (let [nm (first (pack-names p))
            f (pack-of p nm)
            arr (java.nio.file.Files/readAllBytes (java.nio.file.Paths/get f (make-array String 0)))]
        (aset-byte arr 60 (unchecked-byte 0))
        (spit f (String. arr "ISO-8859-1"))
        (is (:ok (run p "stores" "verify")))))))

(deftest corruption-is-cold-cache
  (let [p (project)
        _ (is (true? (:ok (run p "stores" "pack" "--cache"))))
        nm (first (pack-names p))
        f (pack-of p nm)
        arr (java.nio.file.Files/readAllBytes (java.nio.file.Paths/get f (make-array String 0)))]
    (testing "a changed byte with the same length (idx and footer intact) reads from the file"
      (aset-byte arr 60 (unchecked-byte (bit-xor 1 (aget arr 60))))
      (spit f (String. arr "ISO-8859-1"))
      (is (= plain-text (get-in (run p "docs" "render" "plain") [:data])))
      (is (= "open" (get-in (run p "docs" "show" "login") [:data :payload "status"]))))
    (testing "a truncated pack is ignored, not an error, and check says so"
      (spit f (String. (java.util.Arrays/copyOfRange arr 0 40) "ISO-8859-1"))
      (is (= plain-text (get-in (run p "docs" "render" "plain") [:data])))
      (let [c (run p "stores" "check")]
        (is (true? (:ok c)) (pr-str c))
        (is (nil? (:exit c)) "a cache pack never makes the check fail")
        (is (some #(= "cache/corrupt-pack" (:type %)) (:warnings c)))))))

(deftest reading-is-read-only
  (let [p (project)
        _ (is (true? (:ok (run p "stores" "pack" "--cache"))))
        nm (first (pack-names p))
        ;; one cache pack without .idx and one truncated: both cold, never written
        _ (fs/delete (idx-of p nm))
        _ (is (= 1 (count (pack-names p))))
        before (snapshot p)
        _ (run p "stores" "sense" "--once")
        _ (run p "stores" "verify")
        _ (run p "stores" "unreachable")]
    (is (= before (snapshot p)) "`.kimun/` is identical, bytes and mtimes")
    (let [packs-before (snapshot p)]
      (is (= plain-text (get-in (run p "docs" "render" "plain") [:data])))
      (is (= (into {} (filter (fn [[k _]] (clojure.string/starts-with? k "cache/packs")))
                   packs-before)
             (into {} (filter (fn [[k _]] (clojure.string/starts-with? k "cache/packs")))
                   (snapshot p)))
          "the cache pack files are untouched by a render"))))

(deftest the-command-packs-the-cache-and-repairs-it
  (let [p (project)
        [id {:keys [text]}] (aligned-text-object p "Para one")]
    (is (some? id) "a loose cache object exists before packing")
    (let [r (:data (run p "stores" "pack" "--cache"))]
      (is (= "cache" (:scope r)))
      (is (pos? (:packed r)))
      (is (pos? (:packs r)))
      (is (empty? (cache-objects p)) "no loose cache objects remain"))
    (let [r (:data (run p "stores" "pack" "--cache"))]
      (is (zero? (:packed r)) "a second run has nothing to pack"))
    (testing "a loose cache object that does not hash to its name is dropped"
      (spit (file (store-cache/objects-dir (file p ".kimun")) id) "{:broken true}")
      (let [r (:data (run p "stores" "pack" "--cache"))]
        (is (= 1 (:dropped-damaged r)))
        (is (not (fs/exists? (file (store-cache/objects-dir (file p ".kimun")) id))))))
    (testing "a loose duplicate of a packed object is deleted without a new pack"
      (spit (file (store-cache/objects-dir (file p ".kimun")) id) text)
      (let [r (:data (run p "stores" "pack" "--cache"))]
        (is (= 1 (:deduplicated r)))
        (is (zero? (:packed r)))))
    (testing "a corrupt cache pack is dropped"
      (let [nm (first (pack-names p))
            f (pack-of p nm)]
        (spit f "KMNPACK1garbage")
        (let [r (:data (run p "stores" "pack" "--cache"))]
          (is (= 1 (:dropped-packs r))))))))

(deftest a-sound-cache-pack-without-index-is-repaired-not-dropped
  (let [p (project)
        _ (is (true? (:ok (run p "stores" "pack" "--cache"))))
        nm (first (pack-names p))
        idx (idx-of p nm)]
    (fs/delete idx)
    (let [r (:data (run p "stores" "pack" "--cache"))]
      (is (= 1 (:indexes-repaired r)) "the reader's ignore is not a reason to drop it")
      (is (zero? (:dropped-packs r)))
      (is (= 1 (count (pack-names p))) "the pack is still there")
      (is (fs/exists? idx) "and its index was written")
      (is (= plain-text (get-in (run p "docs" "render" "plain") [:data]))))))

(deftest the-two-scopes-are-separate
  (let [p (project)
        cache-before (snapshot p)
        r (:data (run p "stores" "pack"))]
    (is (= "store" (:scope r)))
    (is (= (into {} (filter (fn [[k _]] (clojure.string/starts-with? k "cache/"))) cache-before)
           (into {} (filter (fn [[k _]] (clojure.string/starts-with? k "cache/"))) (snapshot p)))
        "a store pack does not touch cache/")
    (let [store-files (fn []
                        (into {}
                              (map (fn [f] [(str (fs/file-name f)) (count (slurp (str f)))])
                                   (fs/glob (file p ".kimun" "objects") "*"))))
          packs (fn [] (mapv #(str (fs/file-name %)) (fs/glob (file p ".kimun" "packs") "*")))
          objs (store-files) pk (packs)
          _ (is (true? (:ok (run p "stores" "pack" "--cache"))))
          r2 (:data (run p "stores" "pack" "--cache"))]
      (is (= "cache" (:scope r2)))
      (is (= objs (store-files)) "objects/ untouched by the cache pack")
      (is (= pk (packs)) "packs/ untouched by the cache pack"))))
