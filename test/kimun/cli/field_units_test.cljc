(ns kimun.cli.field-units-test
  "K1c: field units of a typed document. A unit is minted the first time its
   field is registered, persisted in the tree descriptor (`:units`, store, not
   cache), kept while the field is still selected (a change of value is a
   mutation of the same unit) and kept — as `:missing` — when the field stops
   being extracted. It is identity, never a fingerprint (docs/v2/14 §20, §21)."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.host.default]
            [kimun.surface.markdown.plan]
            [kimun.surface.docs :as tdocs]
            [kimun.test-util :as ktu]))

(def ^:dynamic *p* nil)
(def env {"USER" "tester"})

(defn- run-in [p & argv]
  (binding [kimun.surface.markdown.ast/*parser* "native"]
    (:envelope (main/run (vec argv) env p))))
(defn- run [& argv] (apply run-in *p* argv))
(defn- file [& parts] (str (apply fs/path *p* parts)))

(defn- ticket
  ([fields] (ticket fields (str "# ⸢rev•" (first (map :name fields)) "⸥\n\nStatus: ⸢rev•status⸥\n")))
  ([fields template]
   (pr-str {:model/name "TicketDoc" :model/version 1 :base []
            :fields (vec (for [f fields]
                           (merge {:type :string :required true :description (str f ".")} f)))
            :template template})))

(defn- setup!
  "The project at `p`: a store with TicketDoc (title, status) and the tracked
   document `a` (docs/a.md)."
  [p]
  (fs/create-dirs (str p "/docs"))
  (spit (str p "/ticket.edn") (ticket [{:name "title"} {:name "status"}]))
  (spit (str p "/docs/a.md") "# T\n\nStatus: open\n")
  (is (true? (:ok (run-in p "stores" "init" "--name" "p"))))
  (is (true? (:ok (run-in p "models" "define" "-f" "ticket.edn"))))
  (is (true? (:ok (run-in p "docs" "track" "TicketDoc" "docs/a.md" "--name" "a")))))

(use-fixtures :each
  (fn [f]
    (let [p (str (ktu/temp-dir "kimun-units-"))]
      (try (binding [*p* p] (setup! p) (f)) (finally (fs/delete-tree p))))))

(defn- shown [name] (:data (run "docs" "show" name)))
(defn- units [name] (into {} (map (fn [u] [(:field u) (:id u)]) (:units (shown name)))))
(defn- statuses [name] (into {} (map (fn [u] [(:field u) (:status u)]) (:units (shown name)))))
(defn- doc-of [name]
  (let [s (:store (cli-store/open! (file ".kimun")))]
    (tdocs/find-document s name)))

(deftest units-are-minted-when-tracking-and-survive-an-edit
  (let [u0 (units "a")]
    (is (= #{"title" "status"} (set (keys u0))))
    (is (every? string? (vals u0)))
    (is (not= (get u0 "title") (get u0 "status")) "one id per field")
    (is (= {"title" :anchored "status" :anchored} (statuses "a")))
    (spit (file "docs" "a.md") "# T2\n\nStatus: closed\n")
    (is (true? (:ok (run "docs" "update" "a"))))
    (is (= u0 (units "a")) "a change of value is a mutation of the same unit")
    (is (= {"title" :anchored "status" :anchored} (statuses "a")))))

(deftest two-documents-of-one-model-have-their-own-unit-ids
  (spit (file "docs" "b.md") "# T\n\nStatus: open\n")
  (is (true? (:ok (run "docs" "track" "TicketDoc" "docs/b.md" "--name" "b"))))
  (let [a (units "a") b (units "b")]
    (is (= #{"title" "status"} (set (keys a))) "there are units to compare")
    (is (= (set (keys a)) (set (keys b))))
    (is (empty? (clojure.set/intersection (set (vals a)) (set (vals b))))
        "two marks are two units")))

(deftest an-absent-field-keeps-its-unit
  ;; end to end: an OPTIONAL field is in the file, is tracked (a unit is minted),
  ;; then the text stops bringing it and the extraction omits the key, so the
  ;; unit is `:missing` with the same id; when the text brings it back, the same
  ;; unit is `:anchored` again. (Redefining a model to drop a field is not the
  ;; way: a class is immutable.)
  (let [p (str (ktu/temp-dir "kimun-units-missing-"))
        ticket* (pr-str {:model/name "NoteDoc" :model/version 1 :base []
                         :fields [{:name "title" :type :string :required true :description "Title."}
                                  {:name "note" :type :string :required false :description "Optional note."}]
                         :template "# ⸢rev•title⸥\n\nNote: ⸢rev•note⸥\n"})]
    (try
      (fs/create-dirs (str p "/docs"))
      (spit (str p "/note.edn") ticket*)
      (spit (str p "/docs/n.md") "# T\n\nNote: hi\n")
      (is (true? (:ok (run-in p "stores" "init" "--name" "q"))))
      (is (true? (:ok (run-in p "models" "define" "-f" "note.edn"))))
      (is (true? (:ok (run-in p "docs" "track" "NoteDoc" "docs/n.md" "--name" "n"))))
      (let [u0 (into {} (map (fn [u] [(:field u) (:id u)]))
                     (:units (:data (run-in p "docs" "show" "n"))))]
        (is (= #{"title" "note"} (set (keys u0))))
        (spit (str p "/docs/n.md") "# T\n")
        (is (true? (:ok (run-in p "docs" "update" "n"))))
        (let [shown (into {} (map (fn [u] [(:field u) [(:id u) (:status u)]]))
                          (:units (:data (run-in p "docs" "show" "n"))))]
          (is (= (get u0 "note") (first (get shown "note"))) "the same id stays")
          (is (= :missing (second (get shown "note"))) "the absence does not end the unit")
          (is (= :anchored (second (get shown "title")))))
        (spit (str p "/docs/n.md") "# T\n\nNote: back\n")
        (is (true? (:ok (run-in p "docs" "update" "n"))))
        (let [back (into {} (map (fn [u] [(:field u) [(:id u) (:status u)]]))
                         (:units (:data (run-in p "docs" "show" "n"))))]
          (is (= u0 (into {} (map (fn [[k [id _]]] [k id]) back)))
              "the same units, with the same ids")
          (is (= :anchored (second (get back "note"))))))
      (finally (fs/delete-tree p)))))

(deftest an-inline-document-has-no-units-and-is-not-rewritten-for-it
  ;; units live in the registry descriptor of a by-reference document; an
  ;; inline one has none (declared limit) and must still read as unchanged
  (spit (file "docs" "i.md") "# I\n\nStatus: open\n")
  (is (true? (:ok (run "docs" "track" "TicketDoc" "docs/i.md" "--name" "i" "--inline"))))
  (is (= [] (:units (shown "i"))))
  (is (true? (get-in (run "docs" "update" "i") [:data :unchanged?]))))

(deftest reanchoring-keeps-the-units
  (let [u0 (units "a")]
    (is (= 2 (count u0)) "there are units to keep")
    (fs/create-dirs (file "b"))
    (fs/move (file "docs" "a.md") (file "b" "a.md"))
    (is (true? (:ok (run "docs" "reanchor" "a" "b/a.md" "--reason" "moved"))))
    (is (= u0 (units "a")))))

(deftest a-rebuild-carries-the-field-units
  (let [p (str (ktu/temp-dir "kimun-units-inc-"))
        git! (fn [& args] (apply process/shell {:dir p :out :string :err :string :continue true} "git" args))
        inc! (fn [& argv] (:envelope (binding [kimun.surface.markdown.ast/*parser* "native"]
                                       (main/run (into ["include" "build"] argv) env p))))]
    (try
      (git! "init" "-q") (git! "config" "user.email" "t@e") (git! "config" "user.name" "t")
      (fs/create-dirs (str p "/models")) (fs/create-dirs (str p "/docs"))
      (spit (str p "/models/TicketDoc.edn") (ticket [{:name "title"} {:name "status"}]))
      (spit (str p "/docs/a.md") "# T\n\nStatus: open\n")
      (spit (str p "/kimun.include.edn")
            (str "{:include/version 1 :store {:path \".kimun\" :git :ignore}\n"
                 " :models [\"models/*.edn\"]\n"
                 " :documents [{:glob \"docs/**/*.md\" :model \"TicketDoc\"}]}\n"))
      (git! "add" "-A") (git! "commit" "-qm" "init")
      (is (true? (:ok (inc!))) "the first build")
      (let [before (into {}
                         (let [s (:store (cli-store/open! (str p "/.kimun")))]
                           (:units (first (tdocs/documents s)))))
            b (inc! "--rebuild")
            after (into {}
                        (let [s (:store (cli-store/open! (str p "/.kimun")))]
                          (:units (first (tdocs/documents s)))))]
        (is (true? (:ok b)) (pr-str b))
        (is (= before after) "the rebuild carries the same field units")
        (is (= 2 (get-in b [:data :identities :units-carried]))))
      (finally (fs/delete-tree p)))))

(defn- routes-of
  "The route entries of the store at `dir`, keyed by qualified path."
  [dir]
  (let [s (:store (cli-store/open! dir))]
    (:routes s)))

(deftest units-add-no-identity-to-any-hash
  (testing "surface: the same AST and ids, different :units, the same hashes"
    (let [host kimun.host.default/host
          ast {:type :document :attrs {:doc "01M7GHDVQ35RJHJE1ZWJM4H4FK"} :children []}
          build (fn [opts] (kimun.surface.markdown.plan/ast->tree host ast
                                                                  (merge {:tree-id "01M7GHDVQ35RJHJE1ZWJM4H4FK"
                                                                          :name "a" :class "C" :content-hash "h"}
                                                                         opts)))
          a (build {:units {"title" "01M7GHDVQ380BMKM0T25V42K69"}})
          b (build {:units {"title" "01M7GHDVQ380BMKM0T25V42K69" "note" "01M7GHDVQ380BMKM0T25V42K70"}})
          c (build {})]
      (is (= (:merkle-root a) (:merkle-root b) (:merkle-root c)) ":units do not enter the Merkle")
      (is (= (:root a) (:root b) (:root c)))
      (is (= (:objects a) (:objects b) (:objects c)))
      (is (= (:nodes a) (:nodes b) (:nodes c)))
      (is (= (:descriptor c) (dissoc (:descriptor a) :units))
          "a descriptor without :units is the one this surface produced before K1c")
      (is (= (:units (:descriptor b)) (merge (:units (:descriptor a)) {"note" "01M7GHDVQ380BMKM0T25V42K70"})))))
  (testing "CLI: two stores, different unit ids, the same route hashes and the same status"
    (let [t0 (doc-of "a")
          other (str (ktu/temp-dir "kimun-units-other-"))]
      (fs/create-dirs (str other "/docs"))
      (spit (str other "/ticket.edn") (ticket [{:name "title"} {:name "status"}]))
      (spit (str other "/docs/a.md") (slurp (file "docs" "a.md")))
      (is (true? (:ok (run-in other "stores" "init" "--name" "q"))))
      (is (true? (:ok (run-in other "models" "define" "-f" "ticket.edn"))))
      (is (true? (:ok (run-in other "docs" "track" "TicketDoc" "docs/a.md" "--name" "a"))))
      (let [r1 (routes-of (file ".kimun"))
            r2 (routes-of (str other "/.kimun"))
            other-doc (let [s (:store (cli-store/open! (str other "/.kimun")))]
                        (tdocs/find-document s "a"))]
        (is (= 1 (count r1))) (is (= 1 (count r2)))
        (let [e1 (val (first r1)) e2 (val (first r2))]
          (is (and (string? (:projection-hash e1)) (not (str/blank? (:projection-hash e1)))))
          (is (and (string? (:content-hash e1)) (not (str/blank? (:content-hash e1)))))
          (is (and (string? (:fingerprint e1)) (not (str/blank? (:fingerprint e1)))))
          (is (= (:projection-hash e1) (:projection-hash e2)))
          (is (= (:content-hash e1) (:content-hash e2)))
          (is (= (:fingerprint e1) (:fingerprint e2))))
        (is (not= (:units t0) (:units other-doc)) "different identities"))
      (fs/delete-tree other))))
