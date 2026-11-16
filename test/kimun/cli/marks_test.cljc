(ns kimun.cli.marks-test
  "K1e from the CLI: structural units of a section or a block are DECLARED
   (`docs mark`), read as a derived state with candidates (`docs marks`),
   re-anchored and closed by declaration (`docs mark --unit`, `docs unmark`),
   and survive a re-anchor and a rebuild. kimun never decides that a unit moved,
   was split or is another one; no test here accepts an empty comparison
   (docs/v2/14 §20–§21)."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.host.default :as host]
            [kimun.kernel.canon :as canon]
            [kimun.surface.markdown.anchors :as anchors]
            [kimun.surface.markdown.ast :as ast]
            [kimun.surface.markdown.plan :as md]
            [kimun.surface.docs :as tdocs]
            [kimun.test-util :as ktu]))

(def ^:dynamic *p* nil)
(def env {"USER" "tester"})

(defn- run-in [p & argv]
  (binding [ast/*parser* "native"]
    (:envelope (main/run (vec argv) env p))))
(defn- run [& argv] (apply run-in *p* argv))
(defn- file [& parts] (str (apply fs/path *p* parts)))

(def plain "# Note\n\nUno.\n\nDos.\n\n## Otra\n\nTres.\n")

(defn- setup! [p]
  (fs/create-dirs (str p "/notes"))
  (spit (str p "/notes/plain.md") plain)
  (is (true? (:ok (run-in p "stores" "init" "--name" "p"))))
  (is (true? (:ok (run-in p "docs" "ingest" "notes/plain.md")))))

(use-fixtures :each
  (fn [f]
    (let [p (str (ktu/temp-dir "kimun-marks-cli-"))]
      (try (binding [*p* p] (setup! p) (f)) (finally (fs/delete-tree p))))))

(defn- head [] (:head (:store (cli-store/open! (file ".kimun")))))
(defn- marks [] (get-in (run "docs" "marks" "plain") [:data :marks]))
(defn- by-status [status] (filterv #(= status (:status %)) (marks)))
(defn- locator [text]
  (let [a (anchors/anchors host/host text)]
    (binding [ast/*parser* "native"]
      (let [paras (filterv #(= :paragraph (:type (:locator %))) (:blocks a))]
        (is (pos? (count paras)) "there are paragraphs to anchor")
        (mapv :locator paras)))))
(defn- section-locators [text]
  (binding [ast/*parser* "native"]
    (let [a (anchors/anchors host/host text)]
      (is (pos? (count (:sections a))))
      (mapv :locator (:sections a)))))
(defn- lstr [l] (pr-str l))

(deftest marking-a-section-and-a-paragraph
  (let [text (slurp (file "notes" "plain.md"))
        [p0 p1] (locator text)
        sec (second (section-locators text))]
    (let [m1 (run "docs" "mark" "plain" "--locator" (lstr sec))
          m2 (run "docs" "mark" "plain" "--locator" (lstr p0) "--label" "primer párrafo")]
      (is (true? (:ok m1)) (pr-str m1))
      (is (true? (:ok m2)) (pr-str m2))
      (let [ids (mapv #(get-in % [:data :id]) [m1 m2])]
        (is (every? string? ids))
        (is (not= (first ids) (second ids)))
        (is (= 2 (count (marks))))
        (is (every? #(= :anchored (:status %)) (marks)))
        (testing "editing another block leaves the marks anchored"
          (spit (file "notes" "plain.md") (str/replace text "Dos." "Dos, editado."))
          (is (true? (:ok (run "docs" "update" "notes/plain.md" "--name" "plain"))))
          (is (= ids (mapv :id (marks))))
          (is (= 2 (count (by-status :anchored)))))
        (testing "editing the marked paragraph shows :mutated, and a write registers the new hash"
          (spit (file "notes" "plain.md")
                (str/replace (slurp (file "notes" "plain.md")) "Uno." "Uno, editado."))
          (is (= [:mutated] (mapv :status (by-status :mutated))))
          (is (true? (:ok (run "docs" "update" "notes/plain.md" "--name" "plain"))))
          (is (= 2 (count (by-status :anchored))))
          (is (= ids (mapv :id (marks))))
          (testing "and the next edit is :mutated again: the hash advanced"
            (spit (file "notes" "plain.md")
                  (str/replace (slurp (file "notes" "plain.md")) "Uno, editado." "Uno, otra vez."))
            (is (= [:mutated] (mapv :status (by-status :mutated))))))
        (testing "the three rejections write nothing"
          (let [h (head)]
            (is (= "docs/anchor-unresolved"
                   (get-in (run "docs" "mark" "plain" "--locator" (lstr {:section sec :type :paragraph :n 99}))
                           [:error :type])))
            (is (= "docs/exists"
                   (get-in (run "docs" "mark" "plain" "--locator" (lstr sec)) [:error :type])))
            (is (= "cli/usage"
                   (get-in (run "docs" "mark" "plain" "--unit" (first ids) "--locator" (lstr p1))
                           [:error :type])))
            (is (= h (head)))))))))

(deftest inserting-shifts-and-kimun-does-not-infer
  (let [text (slurp (file "notes" "plain.md"))
        [p0] (locator text)
        m (run "docs" "mark" "plain" "--locator" (lstr p0))
        id (get-in m [:data :id])]
    (is (true? (:ok m)) (pr-str m))
    (spit (file "notes" "plain.md") (str/replace text "Uno." "Nuevo.\n\nUno."))
    (let [before (first (filter #(= id (:id %)) (marks)))]
      (is (= :ambiguous (:status before)))
      (is (= 2 (count (:candidates before))))
      (is (= #{:locator :hash} (set (map :reason (:candidates before)))))
      (testing "a normal write does not move it"
        (is (true? (:ok (run "docs" "update" "notes/plain.md" "--name" "plain"))))
        (is (= before (first (filter #(= id (:id %)) (marks))))))
      (testing "the actor declares the new anchor"
        (let [cand (first (filter #(= :hash (:reason %)) (:candidates before)))
              r (run "docs" "mark" "plain" "--unit" id "--locator" (lstr (:locator cand))
                     "--reason" "inserted a paragraph above")]
          (is (true? (:ok r)) (pr-str r))
          (is (= id (get-in r [:data :id])))
          (let [after (first (filter #(= id (:id %)) (marks)))]
            (is (= :anchored (:status after)))
            (is (= (:locator cand) (:locator after)))))))))

(deftest deleting-does-not-end-the-unit-and-closing-is-declared
  (let [text (slurp (file "notes" "plain.md"))
        [_ p1] (locator text)          ; the LAST paragraph of its type in the section
        _ (is (some? p1) "there is a second paragraph to mark")
        id (get-in (run "docs" "mark" "plain" "--locator" (lstr p1)) [:data :id])]
    (is (string? id))
    (spit (file "notes" "plain.md") (str/replace text "\n\nDos." ""))
    (is (= :missing (:status (first (filter #(= id (:id %)) (marks))))))
    (is (true? (:ok (run "docs" "update" "notes/plain.md" "--name" "plain"))))
    (is (= id (:id (first (filter #(= id (:id %)) (marks))))) "the id survives the write")
    (is (= :missing (:status (first (filter #(= id (:id %)) (marks))))))
    (let [u (run "docs" "unmark" "plain" id "--reason" "gone for good")]
      (is (true? (:ok u)) (pr-str u))
      (is (empty? (filter #(= id (:id %)) (marks)))))))

(deftest marks-survive-a-reanchor-and-a-rebuild
  (let [p (str (ktu/temp-dir "kimun-marks-inc-"))
        git! (fn [& args] (apply process/shell {:dir p :out :string :err :string :continue true} "git" args))
        inc! (fn [& argv] (:envelope (binding [ast/*parser* "native"]
                                       (main/run (into ["include" "build"] argv) env p))))
        ticket (pr-str {:model/name "TicketDoc" :model/version 1 :base []
                        :fields [{:name "title" :type :string :required true :description "Title."}
                                 {:name "status" :type :string :required true :description "Status."}]
                        :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"})]
    (try
      (git! "init" "-q") (git! "config" "user.email" "t@e") (git! "config" "user.name" "t")
      (fs/create-dirs (str p "/models")) (fs/create-dirs (str p "/docs"))
      (spit (str p "/models/TicketDoc.edn") ticket)
      (spit (str p "/docs/a.md") "# T\n\nStatus: open\n")
      (spit (str p "/kimun.include.edn")
            (str "{:include/version 1 :store {:path \".kimun\" :git :ignore}\n"
                 " :models [\"models/*.edn\"]\n"
                 " :documents [{:glob \"docs/**/*.md\" :model \"TicketDoc\"}]}\n"))
      (git! "add" "-A") (git! "commit" "-qm" "init")
      (is (true? (:ok (inc!))) "the store is built")
      (let [run* (fn [& argv] (:envelope (binding [ast/*parser* "native"]
                                          (main/run (vec argv) env p))))
            shown (fn [] (:data (run* "docs" "show" "a")))
            loc (->> (:blocks (:data (run* "docs" "anchors" "a")))
                     (filter #(= :paragraph (:type (:locator %)))) first :locator)
            _ (is (some? loc) "there is a paragraph to mark")
            m (run* "docs" "mark" "a" "--locator" (pr-str loc))
            id (get-in m [:data :id])]
        (is (true? (:ok m)) (pr-str m))
        (is (string? id))
        (is (= [id] (mapv :id (:marks (shown)))))
        (fs/move (str p "/docs/a.md") (str p "/a.md"))
        (is (true? (:ok (run* "docs" "reanchor" "a" "a.md" "--reason" "moved"))))
        (is (= [id] (mapv :id (:marks (shown)))) "the re-anchor keeps the mark")
        (spit (str p "/kimun.include.edn")
              (str "{:include/version 1 :store {:path \".kimun\" :git :ignore}\n"
                   " :models [\"models/*.edn\"]\n"
                   " :documents [{:glob \"a.md\" :model \"TicketDoc\"}]}\n"))
        (git! "add" "-A") (git! "commit" "-qm" "moved")
        (let [b (inc! "--rebuild")]
          (is (true? (:ok b)) (pr-str b))
          (is (= 1 (get-in b [:data :identities :marks-carried])))
          (is (= [id] (mapv :id (:marks (shown)))) "the rebuild carries the same mark id")))
      (finally (fs/delete-tree p)))))

(deftest marks-are-not-a-fingerprint-and-reading-writes-nothing
  (let [a (ast/parse host/host plain)
        build (fn [marks]
                (md/ast->tree host/host (assoc-in a [:attrs :doc] "01EEEEEEEEEEEEEEEEEEEEEEEE")
                              {:tree-id "01FFFFFFFFFFFFFFFFFFFFFFFF" :name "n" :class "C" :content-hash "h"
                               :marks (when marks {"m" {:kind :block :locator [] :hash "x"}})}))
        without (build nil)
        with (build true)]
    (is (= (:merkle-root without) (:merkle-root with)) ":marks do not enter the Merkle")
    (is (= (:root without) (:root with)))
    (is (= (:objects without) (:objects with)))
    (is (= (:nodes without) (:nodes with)))
    (is (= (:descriptor without) (dissoc (:descriptor with) :marks))))
  (let [route-of (fn []
                   (let [s (:store (cli-store/open! (file ".kimun")))]
                     (into {} (map (fn [[p e]] [p (select-keys e [:projection-hash :content-hash :fingerprint])])
                                   (:routes s)))))
        before (route-of)
        text (slurp (file "notes" "plain.md"))
        [p0] (locator text)]
    (is (= 1 (count before)) "the document has one route entry")
    (is (every? #(and (string? (:projection-hash %)) (not (str/blank? (:projection-hash %)))
                      (string? (:content-hash %)) (not (str/blank? (:content-hash %))))
                (vals before)))
    (is (true? (:ok (run "docs" "mark" "plain" "--locator" (lstr p0)))))
    (is (= before (route-of)) "marking does not move the route hashes")
    (let [sha (fn [] (into {} (map (fn [f]
                                     (let [md (java.security.MessageDigest/getInstance "SHA-256")]
                                       [(str f) (apply str (map #(format "%02x" (bit-and 0xff %))
                                                                (.digest md (java.nio.file.Files/readAllBytes (fs/path f)))))]))
                                   (filter fs/regular-file? (fs/glob (file ".kimun") "**" {:hidden true})))))
          s0 (sha) h0 (head)]
      (is (= 1 (count (marks))) "there is a mark to read")
      (run "docs" "marks" "plain")
      (is (= h0 (head)) "reading the marks does not move the head")
      (is (= s0 (sha)) "and writes no file of the store"))))
