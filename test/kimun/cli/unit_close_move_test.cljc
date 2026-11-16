(ns kimun.cli.unit-close-move-test
  "K1f: closing a unit and moving it to another document are DECLARATIONS
   (docs/v2/14 §21 rules 3, 4 and 6). `docs unmark` closes a field unit, `docs
   mark OTHER --unit ID --from NAME` moves a unit with its id in one revision;
   both are one revision, a rejection leaves the head where it
   was, and the unit survives a re-anchor and a rebuild. kimun never decides
   that a unit closed or moved. No test compares empty values."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.host.default :as host]
            [kimun.surface.markdown.ast :as ast]
            [kimun.surface.markdown.plan :as md]))

(def ^:dynamic *p* nil)
(def env {"USER" "tester"})

(defn- run-in [p & argv]
  (binding [ast/*parser* "native"]
    (:envelope (main/run (vec argv) env p))))
(defn- run [& argv] (apply run-in *p* argv))
(defn- file [& parts] (str (apply fs/path *p* parts)))

(def ticket (pr-str {:model/name "TicketDoc" :model/version 1 :base []
                     :fields [{:name "title" :type :string :required true :description "Title."}
                              {:name "status" :type :string :required true :description "Status."}]
                     :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n"}))
(def note (pr-str {:model/name "NoteDoc" :model/version 1 :base []
                   :fields [{:name "title" :type :string :required true :description "Title."}
                            {:name "note" :type :string :required false :description "Optional note."}]
                   :template "# ⸢rev•title⸥\n\nNote: ⸢rev•note⸥\n"}))

(defn- setup!
  "A project at `p`: TicketDoc documents `a` and `b`, NoteDoc `n` and the
   generic documents `g1` and `g2`."
  [p]
  (fs/create-dirs (str p "/docs"))
  (spit (str p "/ticket.edn") ticket)
  (spit (str p "/note.edn") note)
  (spit (str p "/docs/a.md") "# T\n\nStatus: open\n")
  (spit (str p "/docs/b.md") "# U\n\nStatus: new\n")
  (spit (str p "/docs/n.md") "# N\n\nNote: hi\n")
  (spit (str p "/docs/g1.md") "# G1\n\nAlfa.\n\nBeta.\n")
  (spit (str p "/docs/g2.md") "# G2\n\nGamma.\n\nDelta.\n")
  (doseq [argv [["stores" "init" "--name" "p"] ["models" "define" "-f" "ticket.edn"] ["models" "define" "-f" "note.edn"]
                ["docs" "track" "TicketDoc" "docs/a.md" "--name" "a"] ["docs" "track" "TicketDoc" "docs/b.md" "--name" "b"]
                ["docs" "track" "NoteDoc" "docs/n.md" "--name" "n"]
                ["docs" "ingest" "docs/g1.md"] ["docs" "ingest" "docs/g2.md"]]]
    (is (true? (:ok (apply run-in p argv))) (pr-str argv))))

(use-fixtures :each
  (fn [f]
    (let [p (str (fs/create-temp-dir {:prefix "kimun-close-move-"}))]
      (try (binding [*p* p] (setup! p) (f)) (finally (fs/delete-tree p))))))

(defn- head [] (:head (:store (cli-store/open! (file ".kimun")))))
(defn- revisions [] (count (:revisions (:store (cli-store/open! (file ".kimun"))))))
(defn- shown [name] (:data (run "docs" "show" name)))
(defn- units [name] (into {} (map (fn [u] [(:field u) (:id u)]) (:units (shown name)))))
(defn- marks [name] (get-in (run "docs" "marks" name) [:data :marks]))
(defn- paragraph-locator [name n]
  (let [ls (->> (:blocks (:data (run "docs" "anchors" name)))
                (filter #(= :paragraph (:type (:locator %)))) (mapv :locator))]
    (is (< n (count ls)) "the paragraph to anchor exists")
    (nth ls n)))
(defn- lstr [l] (pr-str l))

(deftest closing-a-present-field-is-declared-and-its-replacement-gets-a-new-id
  (let [u0 (units "a") old (get u0 "status")]
    (is (= #{"title" "status"} (set (keys u0))))
    (testing "a field unit is not closed without a reason"
      (let [h (head) r (run "docs" "unmark" "a" old)]
        (is (false? (:ok r)))
        (is (= h (head)))))
    (let [r (run "docs" "unmark" "a" old "--reason" "the status means something else now")]
      (is (true? (:ok r)) (pr-str r))
      (is (= #{"title"} (set (keys (units "a")))) "the closed unit left the descriptor"))
    (spit (file "docs" "a.md") "# T\n\nStatus: closed\n")
    (is (true? (:ok (run "docs" "update" "a"))))
    (let [u1 (units "a")]
      (is (= #{"title" "status"} (set (keys u1))))
      (is (string? (get u1 "status")))
      (is (not= old (get u1 "status")) "the replacement is a NEW unit")
      (is (= (get u0 "title") (get u1 "title")) "the other unit is untouched"))))

(defn- include-project!
  "Rewrites `p` as an include project over the generated documents and builds it."
  [p inc! git!]
  (fs/create-dirs (str p "/models")) (fs/create-dirs (str p "/docs"))
  (spit (str p "/docs/a.md") "# T\n\nStatus: open\n")
  (spit (str p "/docs/b.md") "# U\n\nStatus: new\n")
  (spit (str p "/docs/n.md") "# N\n\nNote: hi\n")
  (spit (str p "/models/TicketDoc.edn") ticket)
  (spit (str p "/models/NoteDoc.edn") note)
  (spit (str p "/kimun.include.edn")
        (str "{:include/version 1 :store {:path \".kimun\" :git :ignore}\n"
             " :models [\"models/*.edn\"]\n"
             " :documents [{:glob \"docs/a.md\" :model \"TicketDoc\"} {:glob \"docs/b.md\" :model \"TicketDoc\"} {:glob \"docs/n.md\" :model \"NoteDoc\" :force true}]}\n"))
  (git! "init" "-q") (git! "config" "user.email" "t@e") (git! "config" "user.name" "t")
  (git! "add" "-A") (git! "commit" "-qm" "init")
  (is (true? (:ok (inc!))) "the project builds"))

(defn- with-include-project [f]
  (let [p (str (fs/create-temp-dir {:prefix "kimun-close-inc-"}))
        git! (fn [& args] (apply process/shell {:dir p :out :string :err :string :continue true} "git" args))
        inc! (fn [& argv] (:envelope (binding [ast/*parser* "native"]
                                       (main/run (into ["include" "build"] argv) env p))))
        run* (fn [& argv] (apply run-in p argv))]
    (try (include-project! p inc! git!) (f p inc! git! run*) (finally (fs/delete-tree p)))))

(deftest closing-an-absent-field-removes-it-and-a-rebuild-does-not-bring-it-back
  (with-include-project
    (fn [p inc! git! run*]
      (let [u (fn [] (into {} (map (fn [x] [(:field x) (:id x)]) (:units (:data (run* "docs" "show" "n"))))))
            u0 (u) old (get u0 "note")]
        (is (string? old))
        (spit (str p "/docs/n.md") "# N\n")
        (is (true? (:ok (run* "docs" "update" "n"))))
        (is (= old (get (u) "note")) "the absence did not end the unit")
        (is (= :missing (->> (:units (:data (run* "docs" "show" "n"))) (filter #(= "note" (:field %))) first :status)))
        (is (true? (:ok (run* "docs" "unmark" "n" old "--reason" "the note was dropped on purpose"))))
        (is (= #{"title"} (set (keys (u)))) "the absent unit is gone")
        (git! "add" "-A") (git! "commit" "-qm" "note dropped")
        (is (true? (:ok (inc! "--rebuild"))))
        (is (= #{"title"} (set (keys (u)))))
        (is (not (some #{old} (vals (u)))) "the closed id does not come back")
        (is (= (get u0 "title") (get (u) "title")) "the rebuild carries the others")))))

(deftest moving-a-mark-keeps-its-id-in-one-revision-and-says-what-happened
  (let [m (run "docs" "mark" "g1" "--locator" (lstr (paragraph-locator "g1" 0)) "--label" "alfa")
        id (get-in m [:data :id])
        before (revisions)
        dest (paragraph-locator "g2" 1)]
    (is (true? (:ok m)) (pr-str m))
    (is (string? id))
    (is (= 1 (count (marks "g1"))))
    (let [r (run "docs" "mark" "g2" "--unit" id "--from" "g1" "--locator" (lstr dest) "--reason" "the paragraph moved to g2")]
      (is (true? (:ok r)) (pr-str r))
      (is (= (inc before) (revisions)) "ONE revision touches both documents")
      (is (empty? (marks "g1")) "it left the origin")
      (is (= [id] (mapv :id (marks "g2"))) "same id in the destination")
      (is (= :anchored (:status (first (marks "g2")))))
      (is (= "alfa" (:label (first (marks "g2")))) "the label travels")
      (is (= {:unit id :from "g1" :to "g2" :locator dest} (select-keys (:moved (:data r)) [:unit :from :to :locator]))))))

(deftest moving-a-field-unit-needs-the-destination-field-to-have-none
  (let [ua (units "a") ub (units "b") id (get ua "status")]
    (is (every? string? [id (get ub "status")]))
    (testing "the destination field already has a unit: units are not fused"
      (let [h (head) r (run "docs" "mark" "b" "--unit" id "--from" "a" "--field" "status" "--reason" "x")]
        (is (false? (:ok r)))
        (is (= "docs/exists" (get-in r [:error :type] (:error r))) (pr-str r))
        (is (= h (head)))))
    (is (true? (:ok (run "docs" "unmark" "b" (get ub "status") "--reason" "replaced by the one of a"))))
    (let [before (revisions)
          r (run "docs" "mark" "b" "--unit" id "--from" "a" "--field" "status" "--reason" "a's status is b's")]
      (is (true? (:ok r)) (pr-str r))
      (is (= (inc before) (revisions)) "one revision")
      (is (= id (get (units "b") "status")) "the unit keeps its id in b")
      (is (nil? (get (units "a") "status")) "and left a")
      (is (= (get ua "title") (get (units "a") "title")))
      (is (= {:unit id :from "a" :to "b" :field "status"} (select-keys (:moved (:data r)) [:unit :from :to :field]))))))

(deftest rejected-moves-and-closures-leave-the-head-where-it-was
  (let [ma (get-in (run "docs" "mark" "g1" "--locator" (lstr (paragraph-locator "g1" 0))) [:data :id])
        _ (is (string? ma))
        taken (paragraph-locator "g2" 0)
        _ (is (true? (:ok (run "docs" "mark" "g2" "--locator" (lstr taken)))))
        free (paragraph-locator "g2" 1)
        ua (units "a")
        attempts
        [["no reason" ["docs" "mark" "g2" "--unit" ma "--from" "g1" "--locator" (lstr free)] "cli/usage"]
         ["locator already taken in the destination" ["docs" "mark" "g2" "--unit" ma "--from" "g1" "--locator" (lstr taken) "--reason" "r"] "docs/exists"]
         ["unit not in the origin" ["docs" "mark" "g2" "--unit" "01NOPE" "--from" "g1" "--locator" (lstr free) "--reason" "r"] "docs/missing"]
         ["a locator and a field" ["docs" "mark" "b" "--unit" (get ua "status") "--from" "a" "--locator" (lstr free) "--field" "status" "--reason" "r"] "cli/usage"]
         ["field not in the destination payload" ["docs" "mark" "n" "--unit" (get ua "status") "--from" "a" "--field" "nope" "--reason" "r"] "docs/not-anchored"]
         ["a field unit to a generic document" ["docs" "mark" "g2" "--unit" (get ua "status") "--from" "a" "--field" "status" "--reason" "r"] "docs/not-anchored"]
         ["the same document" ["docs" "mark" "g1" "--unit" ma "--from" "g1" "--locator" (lstr free) "--reason" "r"] "cli/usage"]
         ["unknown unit on unmark" ["docs" "unmark" "a" "01NOPE" "--reason" "r"] "docs/missing"]]]
    (doseq [[what argv type] attempts
            :let [h (head) r (apply run argv)]]
      (testing what
        (is (false? (:ok r)) (pr-str r))
        (is (= type (some-> (or (get-in r [:error :type]) (:error r)) str (str/replace #"^:" ""))) (pr-str r))
        (is (= h (head)) "nothing was written")))
    (is (= [ma] (mapv :id (marks "g1"))) "the unit is still where it was")))

(deftest a-moved-unit-survives-a-reanchor-and-a-rebuild
  (with-include-project
    (fn [p inc! git! run*]
      (let [ua (into {} (map (fn [x] [(:field x) (:id x)]) (:units (:data (run* "docs" "show" "a")))))
            loc (fn [name n] (->> (:blocks (:data (run* "docs" "anchors" name)))
                                  (filter #(= :paragraph (:type (:locator %)))) (mapv :locator) (#(nth % n))))
            mid (get-in (run* "docs" "mark" "a" "--locator" (pr-str (loc "a" 0))) [:data :id])
            _ (is (string? mid))
            mv (run* "docs" "mark" "b" "--unit" mid "--from" "a" "--locator" (pr-str (loc "b" 0)) "--reason" "moved")
            _ (is (true? (:ok mv)) (pr-str mv))
            bm (fn [] (mapv :id (:marks (:data (run* "docs" "show" "b")))))
            ub (fn [] (into {} (map (fn [x] [(:field x) (:id x)]) (:units (:data (run* "docs" "show" "b"))))))]
        (is (= [mid] (bm)))
        (is (= 2 (count ua)))
        (fs/move (str p "/docs/b.md") (str p "/b.md"))
        (is (true? (:ok (run* "docs" "reanchor" "b" "b.md" "--reason" "moved file"))))
        (is (= [mid] (bm)) "a re-anchor keeps the moved unit")
        (spit (str p "/kimun.include.edn")
              (str "{:include/version 1 :store {:path \".kimun\" :git :ignore}\n"
                   " :models [\"models/*.edn\"]\n"
                   " :documents [{:glob \"docs/a.md\" :model \"TicketDoc\"} {:glob \"b.md\" :model \"TicketDoc\"} {:glob \"docs/n.md\" :model \"NoteDoc\" :force true}]}\n"))
        (git! "add" "-A") (git! "commit" "-qm" "moved")
        (let [u-before (ub) b (inc! "--rebuild")]
          (is (true? (:ok b)) (pr-str b))
          (is (= 1 (get-in b [:data :identities :marks-carried])))
          (is (= [mid] (bm)) "the rebuild carries the moved id")
          (is (= u-before (ub))))))))

(deftest units-are-not-a-fingerprint-and-reading-writes-nothing
  (let [a (ast/parse host/host "# T\n\nUno.\n")
        build (fn [units marks]
                (md/ast->tree host/host (assoc-in a [:attrs :doc] "01EEEEEEEEEEEEEEEEEEEEEEEE")
                              {:tree-id "01FFFFFFFFFFFFFFFFFFFFFFFF" :name "n" :class "C" :content-hash "h"
                               :units units :marks marks}))
        bare (build nil nil)
        full (build {"f" "01AAAAAAAAAAAAAAAAAAAAAAAA"} {"m" {:kind :block :locator [] :hash "x"}})]
    (is (some? (:units (:descriptor full))) "there are units to compare")
    (is (= (:merkle-root bare) (:merkle-root full)))
    (is (= (:objects bare) (:objects full))))
  (let [mid (get-in (run "docs" "mark" "g1" "--locator" (lstr (paragraph-locator "g1" 0))) [:data :id])
        _ (is (string? mid))
        mv (run "docs" "mark" "g2" "--unit" mid "--from" "g1" "--locator" (lstr (paragraph-locator "g2" 0)) "--reason" "moved")
        _ (is (true? (:ok mv)) (pr-str mv))
        sha (fn [] (into {} (map (fn [f]
                                   (let [md (java.security.MessageDigest/getInstance "SHA-256")]
                                     [(str f) (apply str (map #(format "%02x" (bit-and 0xff %))
                                                              (.digest md (java.nio.file.Files/readAllBytes (fs/path f)))))]))
                                 (filter fs/regular-file? (fs/glob (file ".kimun") "**" {:hidden true})))))
        s0 (sha) h0 (head)]
    (is (pos? (count s0)))
    (is (= [mid] (mapv :id (marks "g2"))) "the moved unit is there to be read")
    (doseq [n ["a" "b" "g1" "g2"]] (run "docs" "marks" n) (run "docs" "show" n))
    (is (= h0 (head)))
    (is (= s0 (sha)) "reading writes no file of the store")))
