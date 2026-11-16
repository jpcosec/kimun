(ns kimun.cli.spec16-docs-test
  "Typed documents of a spec 16 class (M1g, docs/v2/16 §4.2-§4.3): `docs track`
   and `docs update` extract with the COMPOSED selector of the class (own +
   inherited by `:base`), and the document
   records the exact revision of its class and of its ancestors. The pinning of
   the ancestors is a team inference (docs/v2/06 §5, 16 §4.3). One function per
   case, on temporary projects."
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.host.default :as host]
            [kimun.kernel.revision :as revision]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.models.store :as mstore]
            [kimun.test-util :as ktu]))

(def ^:private env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- file [p & parts] (str (apply fs/path p parts)))
(defn- open-store [p] (:store (cli-store/open! (file p ".kimun"))))

(defn- base-class
  "Parent: the h1 is the `title`. `regex` is what the title captures."
  [regex]
  {:model/name "BaseDoc" :model/version 1 :base [] :document-role :source
   :substrate {:parser :sldb-md/blocks :grammar "markdown"}
   :generation {:projections {}}
   :anchoring {:fields [{:name "title" :type :string :required true :description "Title."}]
               :selector [{:outer-index 0 :outer-type "heading" :outer-tag "h1" :handler :text
                           :props ["title"] :regex regex}]}})

(defn- report-class
  "Child of BaseDoc: adds a `## Summary` section whose body is the `summary`."
  []
  (let [nm "ReportDoc"]
   {:model/name nm :model/version 1 :base ["BaseDoc"] :document-role :source
    :substrate {:parser :sldb-md/blocks :grammar "markdown"}
    :generation {:projections {}}
    :anchoring {:fields [{:name "summary" :type :string :required true :description "Summary."}]
                :selector [{:outer-index 0 :outer-type "heading" :outer-tag "h2" :handler :text :anchor true
                            :props [] :regex "^\\QSummary\\E$"}
                           {:outer-index 1 :outer-type "paragraph" :outer-tag "p" :handler :text
                            :props ["summary"] :regex "^(.*?)$" :capture :section-body}]}}))

(defn- define! [p d]
  (let [f (file p (str (:model/name d) ".edn"))]
    (spit f (pr-str d))
    (let [e (run p "models" "define" "-f" f)] (is (true? (:ok e)) (pr-str e)) e)))

(defn- project
  "A project with the two spec 16 classes defined."
  []
  (let [p (str (ktu/temp-dir "kimun-m1g-"))]
    (run p "stores" "init" "--name" "p")
    (define! p (base-class "^(.*?)$"))
    (define! p (report-class))
    p))

(def ^:private text "# Hello\n\n## Summary\n\nThe summary.\n")

(defn- track! [p model name text]
  (spit (file p (str name ".md")) text)
  (let [e (run p "docs" "track" model (str name ".md") "--name" name)] (is (true? (:ok e)) (pr-str e)) e))

(defn- show [p name] (:data (run p "docs" "show" name)))

(deftest a-document-extracts-own-and-inherited-fields
  (let [p (project) e (track! p "ReportDoc" "r" text)]
    (is (= {"title" "Hello" "summary" "The summary."} (get-in e [:data :payload])) "own field + inherited field")
    (is (= :intact (get-in e [:data :state])))
    (testing "both fields of the composed selector receive a unit (K1c)"
      (is (= #{"title" "summary"} (set (map :field (:units (show p "r")))))))))

(deftest a-missing-required-section-is-reported-missing
  (let [p (project)]
    (spit (file p "a.md") "# Hello\n\nSome body without its section.\n")
    (let [e (run p "docs" "track" "ReportDoc" "a.md" "--name" "a")]
      (is (false? (:ok e)))
      (is (= "validate/missing" (get-in e [:error :type]))))))

(deftest editing-the-parent-does-not-change-an-anchored-child-silently
  (let [p (project)
        _ (track! p "ReportDoc" "r" text)
        before (show p "r")
        ;; the parent now captures only the first word of the title
        _ (define! p (base-class "^(\\w+) World$"))
        _ (spit (file p "x.md") "# Hello World\n\n## Summary\n\nS.\n")
        control (track! p "ReportDoc" "x" "# Hello World\n\n## Summary\n\nS.\n")
        after (show p "r")]
    (is (= "Hello" (get-in before [:payload "title"])))
    (is (= "Hello" (get-in control [:data :payload "title"])) "control: a new document is anchored under the new parent")
    (is (= (:payload before) (:payload after)) "the anchored payload did not move")
    (is (= (:ancestors before) (:ancestors after)) "it still reads under the recorded ancestors")
    (is (= :intact (:state before)))
    (is (= :superseded (:state after)))
    (is (not= (:ancestors after) (:ancestors (show p "x"))) "the control is pinned to the new parent")))

(deftest an-update-after-redefining-the-parent-reanchors-the-child
  (let [p (project)
        _ (track! p "ReportDoc" "r" text)
        old (show p "r")
        _ (define! p (base-class "^(\\w+) World$"))
        _ (spit (file p "r.md") "# Hello World\n\n## Summary\n\nThe summary.\n")
        e (run p "docs" "update" "r")
        now (show p "r")]
    (is (true? (:ok e)) (pr-str e))
    (is (= "Hello" (get-in now [:payload "title"])) "the new parent's regex now reads the title")
    (is (= :intact (:state now)))
    (is (= 1 (count (:ancestors now))))
    (is (not= (:ancestors old) (:ancestors now)) "pinned to the new revision of the parent")
    (is (= (:class old) (:class now)) "the class itself did not change")))

(deftest an-unselected-paragraph-moves-neither-the-hash-nor-the-route
  (let [p (project)
        _ (track! p "ReportDoc" "r" text)
        route (fn [] (get-in (open-store p) [:routes "r.md" :content-hash]))
        h0 (:content-hash (show p "r")) r0 (route)
        _ (spit (file p "r.md") "# Hello\n\n*an aside the class never selects*\n\n## Summary\n\nThe summary.\n")
        _ (run p "docs" "update" "r")
        h1 (:content-hash (show p "r")) r1 (route)
        _ (spit (file p "r.md") "# Hello\n\n## Summary\n\nChanged.\n")
        _ (run p "docs" "update" "r")]
    (is (and (string? h0) (string? r0)) "there is a hash and a route hash to compare")
    (is (= h0 h1))
    (is (= r0 r1))
    (is (not= h0 (:content-hash (show p "r"))) "control: a selected edit does move the hash")))

(deftest a-v1-document-is-untouched-and-answers-as-before
  ;; a guard: the v1 path must not move because spec 16 classes now live beside it. (Sensing a spec 16
  ;; DOCUMENT is not covered here: `diagnostics` does not read it yet, see the report.)
  (let [p (project)
        _ (spit (file p "t.edn") (pr-str {:model/name "TicketDoc" :model/version 1 :base []
                                          :fields [{:name "title" :type :string :required true :description "T."}]
                                          :template "# ⸢rev•title⸥\n"}))
        _ (is (true? (:ok (run p "models" "define" "-f" (file p "t.edn")))))
        _ (spit (file p "t.md") "# T\n")
        _ (is (true? (:ok (run p "docs" "track" "TicketDoc" "t.md" "--name" "t"))))
        t (show p "t")
        sensed (run p "stores" "sense" "--once")]
    (is (= 1 (get-in sensed [:data :tracked])) (pr-str sensed))
    (is (= [] (get-in sensed [:data :signals])) "no signal for the v1 document")
    (is (= :intact (:state t)))
    (is (not-any? #(contains? t %) [:class :ancestors :spec16]) "a v1 document answers with the keys it always had")
    (is (= "# T\n" (slurp (file p "t.md"))) "the file is not rewritten")))

(deftest show-reports-the-exact-class-of-a-spec16-document
  (let [p (project) _ (track! p "ReportDoc" "r" text)
        s (show p "r")
        store (open-store p)
        report (:id (first (filter #(= "ReportDoc" (:name %)) (mstore/models store))))
        base (:id (first (filter #(= "BaseDoc" (:name %)) (mstore/models store))))]
    (is (string? (:class s)))
    (is (= report (:class s)) "the address of the descriptor the document was anchored with")
    (is (= [base] (:ancestors s)))
    (is (some? (revision/get-object store (:class s))) "and that revision is in the store")))

(deftest readers-and-sensors-answer-on-a-store-with-a-spec16-document
  (let [p (project) _ (track! p "ReportDoc" "r" text)
        unexpected? (fn [e] (= "cli/unexpected" (get-in e [:error :type])))
        reads [["stores" "sense" "--once"] ["stores" "health"] ["stores" "check"] ["stores" "verify"] ["stores" "update"]
               ["find" "Hello"] ["fields" "get" "r" "title"] ["sections" "list" "r"] ["docs" "anchors" "r"]
               ["docs" "marks" "r"] ["docs" "list"] ["docs" "show" "r"] ["models" "check"]]
        refused [["fields" "set" "r" "title" "\"X\""] ["sections" "fields" "r" "summary"]
                 ["docs" "create" "ReportDoc" "z" "--set" "title=a"]]]
    (testing "the readers and sensors answer"
      (doseq [c reads :let [e (apply run p c)]]
        (is (not (unexpected? e)) (pr-str c e))))
    (testing "what is not supported is refused with a declared error, writing nothing"
      (doseq [c refused :let [e (apply run p c)]]
        (is (= "cli/usage" (get-in e [:error :type])) (pr-str c e))
        (is (re-find #"spec 16" (str (get-in e [:error :message]))) (pr-str c))))
    (testing "at rest: nothing to sense, the store is healthy"
      (is (= [] (get-in (run p "stores" "sense" "--once") [:data :signals])))
      (is (= :ok (get-in (run p "stores" "health") [:data :status]))))
    (testing "editing a selected field is `source/mutated` with the field that changed"
      (spit (file p "r.md") "# Changed\n\n## Summary\n\nThe summary.\n")
      (let [sig (first (get-in (run p "stores" "sense" "--once") [:data :signals]))]
        (is (= :source/mutated (:type sig)) (pr-str sig))
        (is (= ["title"] (get-in sig [:detail :fields])))))))
