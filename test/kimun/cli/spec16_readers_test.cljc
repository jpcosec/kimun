(ns kimun.cli.spec16-readers-test
  "Every reader of the CLI and of the HTTP surface over a store with a spec 16
   class (with a base) and a tracked document of it, intact and `:superseded`
   (the base redefined): none may answer `cli/unexpected` or a 500, and what
   cannot be served says `spec 16` (M1g left these paths unproven). The matrix
   of the run is in runs/subagents/2026-11-15-spec16-lectores/matrix.md."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [babashka.process :as process]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.test-util :as ktu]
            [kimun.cli.spec16-docs-test :as base]
            [kimun.surface.http :as h]
            [kimun.surface.http.support :as s]))

(def ^:private run @#'base/run)
(def ^:private file @#'base/file)
(def ^:private define! @#'base/define!)
(def ^:private project @#'base/project)
(def ^:private track! @#'base/track!)

(defn- fixture
  "A project with the class pair and the document `r`; `:superseded` redefines the base after."
  [state]
  (let [p (project)]
    (spit (file p "d.json") "{\"title\":\"a\",\"summary\":\"b\"}")
    (track! p "ReportDoc" "r" base/text)
    (when (= state :superseded) (define! p (@#'base/base-class "^(\\w+)$")))
    p))

(def ^:private readers
  "Commands that must answer (ok or a declared failure), never `cli/unexpected`."
  [["fields" "schema" "ReportDoc"] ["fields" "schema" "ReportDoc" "summary"] ["fields" "query" "title"]
   ["models" "list"] ["models" "show" "ReportDoc"] ["models" "history" "ReportDoc"] ["models" "check"]
   ["docs" "compose" "r"] ["docs" "recover" "r"] ["docs" "render" "r"] ["docs" "anchors" "r"]
   ["edges" "show"] ["edges" "nodes"] ["graph" "list"] ["graph" "export"] ["semantic" "show"] ["corpus" "refresh"]
   ["stores" "update"] ["stores" "sense" "--once"]])

(def ^:private spec16-refusals
  "Commands that cannot serve a spec 16 class (no template, no renderer: M2): a declared error that says so."
  [["models" "field-add" "ReportDoc" "--field" "x" "--type" "string" "--description" "d"]
   ["models" "field-remove" "ReportDoc" "--field" "summary"]
   ["models" "template" "ReportDoc"]
   ["extract" "ReportDoc" "--input" "r.md"] ["render" "ReportDoc" "--data" "d.json"] ["validate" "ReportDoc" "--input" "r.md"]])

(deftest no-reader-breaks-or-stays-silent-on-a-spec16-store
  (doseq [state [:intact :superseded]]
    (let [p (fixture state)]
      (testing (str state ": the readers answer")
        (doseq [c readers :let [e (apply run p c)]]
          (is (not= "cli/unexpected" (get-in e [:error :type])) (pr-str state c e))))
      (testing (str state ": what a class cannot serve is refused saying spec 16")
        (doseq [c spec16-refusals :let [e (apply run p c)]]
          (is (= "cli/usage" (get-in e [:error :type])) (pr-str state c e))
          (is (re-find #"spec 16" (str (get-in e [:error :message]))) (pr-str state c))))
      (testing (str state ": the class definition was not changed by the refused editors")
        (is (= ["ReportDoc"] (->> (:data (run p "models" "list")) (filter #(= "ReportDoc" (:name %))) (map :name))))
        (is (= 1 (:version (first (filter #(= "ReportDoc" (:name %)) (:data (run p "models" "list")))))))))))

(deftest fields-schema-reads-the-composed-fields-of-a-class
  (let [p (fixture :intact)
        names #(mapv :name (get-in % [:data :fields]))]
    (is (= ["title" "summary"] (names (run p "fields" "schema" "ReportDoc"))) "inherited field first, then own")
    (is (= ["title"] (names (run p "fields" "schema" "BaseDoc"))))
    (let [row (:data (run p "fields" "schema" "ReportDoc" "title"))]
      (is (= "title" (:field row)) (pr-str row))
      (is (true? (:required row))))))

(deftest stores-update-re-anchors-a-superseded-spec16-document-without-rewriting-its-file
  ;; 10 §11.1: a moved contract (parser, grammar, extractor or CLASS) with the
  ;; source intact is the safe repair of `stores update`. There is no renderer
  ;; for a spec 16 class (M2), so the file is read again under the class in
  ;; force and is never rewritten; what it anchors may change, in a revision.
  ;; Whether that needs a decision is an open question.
  (let [p (fixture :superseded)
        text0 (slurp (file p "r.md"))
        before (:data (run p "docs" "show" "r"))
        e (run p "stores" "update")
        after (:data (run p "docs" "show" "r"))]
    (is (= :superseded (:state before)) "there is something to repair")
    (is (true? (:ok e)) (pr-str e))
    (is (= :intact (:state after)) "re-anchored under the class in force")
    (is (not= (:ancestors before) (:ancestors after)) "the recorded ancestors moved")
    (is (= text0 (slurp (file p "r.md"))) "the file is not rewritten")))

(deftest http-reads-a-spec16-class-and-refuses-the-segments
  (doseq [state [:intact :superseded]]
    (s/with-server
      (fn [p] (spit (file p "x") "")
        (define! p (@#'base/base-class "^(.*?)$")) (define! p (@#'base/report-class))
        (track! p "ReportDoc" "r" base/text)
        (when (= state :superseded) (define! p (@#'base/base-class "^(\\w+)$"))))
      (fn [url _p]
        (let [get* #(s/req :get (str url %))
              models (:models (:data (:json (get* "/schema"))))
              report (first (filter #(= "ReportDoc" (:model %)) models))]
          (is (= ["title" "summary"] (mapv :name (:fields report))) (str state " /schema composes the fields"))
          (doseq [path ["/document/segments?id=r" "/render?model=ReportDoc"]
                  :let [r (get* path)]]
            (is (<= 400 (:status r) 499) (str state " " path " " (:status r)))
            (is (re-find #"spec 16" (str (:message (:error (:json r))) (:message (:json r)))) (str path (:body r))))
          (let [r (s/req :post (str url "/extract/segments") {:model "ReportDoc" :markdown base/text})]
            (is (<= 400 (:status r) 499) (str state (:body r)))
            (is (re-find #"spec 16" (:body r))))
          (is (= 200 (:status (get* "/document?id=r"))))
          (is (= 200 (:status (get* "/graph")))))))))

(deftest include-build-tracks-a-spec16-document-and-a-rebuild-carries-it
  ;; before: `include build` refused the document as "structural noise" (the v1 template check)
  (let [d (str (ktu/temp-dir "kimun-s16-include-"))
        git! (fn [& a] (apply process/shell {:out :string :err :string :dir d} "git" a))
        w! (fn [rel text] (let [f (fs/path d rel)] (some-> (fs/parent f) fs/create-dirs) (spit (str f) text)))
        cli (fn [& a] (:envelope (main/run (vec a) {"USER" "tester"} d)))]
    (git! "init" "-q") (git! "config" "user.email" "t@e") (git! "config" "user.name" "t")
    (w! "models/BaseDoc.edn" (pr-str (@#'base/base-class "^(.*?)$")))
    (w! "models/ReportDoc.edn" (pr-str (@#'base/report-class)))
    (w! "docs/r.md" base/text)
    (w! "kimun.include.edn" (str "{:include/version 1 :store {:path \".kimun\" :git :ignore} :models [\"models/*.edn\"]"
                                 " :documents [{:glob \"docs/**/*.md\" :model \"ReportDoc\"}]}"))
    (git! "add" "-A") (git! "commit" "-qm" "init")
    (let [b (cli "include" "build") shown (:data (cli "docs" "show" "r"))]
      (is (true? (:ok b)) (pr-str b))
      (is (= 1 (get-in b [:data :documents])))
      (is (= {"title" "Hello" "summary" "The summary."} (:payload shown)))
      (is (= 1 (count (:ancestors shown))) "the class and its ancestors are recorded")
      (is (true? (:ok (cli "include" "check")))))
    (let [rb (cli "include" "build" "--rebuild") shown (:data (cli "docs" "show" "r"))]
      (is (true? (:ok rb)) (pr-str rb))
      (is (= 1 (get-in rb [:data :identities :carried])) "the document identity is carried")
      (is (= 2 (get-in rb [:data :identities :units-carried])) "own and inherited field units are carried")
      (is (= :intact (:state shown)))
      (is (= 1 (count (:ancestors shown)))))))
