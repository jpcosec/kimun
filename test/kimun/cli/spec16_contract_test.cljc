(ns kimun.cli.spec16-contract-test
  "K6 for the selectors of spec 16 (M1h, docs/v2/10 §11.1, 16 §3.3): the reading
   contract of a document of a spec 16 class records the substrate, the profile,
   the ancestors and a digest of the COMPOSED selector, so a change of
   the selector (own or inherited) is `contract/changed` and a change of the file
   is `source/*`. The contract of v1 and generic documents does not move. One
   function per case, on temporary projects; the cases are in the plan M1h."
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing]]
            [kimun.cli.main :as main]
            [kimun.cli.store :as cli-store]
            [kimun.host.default :as host]
            [kimun.host.store-cache :as store-cache]
            [kimun.kernel.revision :as revision]
            [kimun.kernel.store :as kstore]
            [kimun.surface.docs :as tdocs]
            [kimun.surface.markdown.ast :as ast]
            [kimun.surface.models.store :as mstore]
            [kimun.surface.parse-contract :as pc]
            [kimun.test-util :as ktu]))

(def ^:private env {"USER" "tester"})
(defn- run [cwd & argv] (:envelope (main/run argv env cwd)))
(defn- file [p & parts] (str (apply fs/path p parts)))
(defn- open-store [p] (:store (cli-store/open! (file p ".kimun"))))

(defn- base-class [regex declared-digest]
  (cond-> {:model/name "BaseDoc" :model/version 1 :base [] :document-role :source
           :substrate {:parser :sldb-md/blocks :grammar "markdown"}
           :generation {:projections {}}
           :anchoring {:fields [{:name "title" :type :string :required true :description "Title."}]
                       :selector [{:outer-index 0 :outer-type "heading" :outer-tag "h1" :handler :text
                                   :props ["title"] :regex regex}]}}
    declared-digest (assoc :contract/digest declared-digest)))

(defn- report-class [regex]
  {:model/name "ReportDoc" :model/version 1 :base ["BaseDoc"] :document-role :source
   :substrate {:parser :sldb-md/blocks :grammar "markdown"}
   :generation {:projections {}}
   :anchoring {:fields [{:name "summary" :type :string :required true :description "Summary."}]
               :selector [{:outer-index 0 :outer-type "heading" :outer-tag "h2" :handler :text :anchor true
                           :props [] :regex "^\\QSummary\\E$"}
                          {:outer-index 1 :outer-type "paragraph" :outer-tag "p" :handler :text
                           :props ["summary"] :regex regex :capture :section-body}]}})

(defn- define! [p d]
  (let [f (file p (str (:model/name d) ".edn"))]
    (spit f (pr-str d))
    (let [e (run p "models" "define" "-f" f)] (is (true? (:ok e)) (pr-str e)) e)))

(def ^:private text "# Hello\n\n## Summary\n\nThe summary.\n")

(defn- project
  "A project with BaseDoc and ReportDoc and the tracked document `r`."
  []
  (binding [ast/*parser* "native"]
    (let [p (str (ktu/temp-dir "kimun-m1h-"))]
      (run p "stores" "init" "--name" "p")
      (define! p (base-class "^(.*?)$" nil))
      (define! p (report-class "^(.*?)$"))
      (spit (file p "r.md") text)
      (is (true? (:ok (run p "docs" "track" "ReportDoc" "r.md" "--name" "r"))))
      p)))

(defn- recorded [p] (:parse-contract (tdocs/find-document (open-store p) "r")))

(defn- signals
  "`stores sense` signals as `[type content-ok contract-ok changed]`."
  [p]
  (binding [ast/*parser* "native"]
    (mapv (fn [s] [(:type s) (get-in s [:detail :content-ok]) (get-in s [:detail :contract-ok])
                   (get-in s [:detail :contract :changed])])
          (get-in (run p "stores" "sense") [:data :signals]))))

(deftest the-contract-of-v1-and-generic-documents-is-byte-for-byte-the-old-one
  ;; GUARD: passes with and without the change. The expected values were computed on `main`
  ;; BEFORE the change (native parser, parser-version 1); they are the only claim made here.
  (binding [ast/*parser* "native"]
    (is (= {:contract/version 1 :parser ["kimun.surface.markdown/native" "1"] :grammar "builtin"
            :normalization "nfc" :extractor ["kimun.surface.models" "1"] :class "class-a"
            :digest "sha-256:c263d6ab5357de523bc5de02f848442195bf3bfd374b3af090d0f5c28ac4e742"}
           (pc/record host/host (pc/environment) "class-a")))
    (is (= "sha-256:516e55e2a1008cc0f3edd5a958de201c0638bae67014bf28b86145c39e27d077"
           (:digest (pc/record host/host (pc/environment) "generic/md"))))
    (testing "a v1 class tracked end to end records exactly those components, and senses clean"
      (let [p (str (ktu/temp-dir "kimun-m1h-v1-"))]
        (run p "stores" "init" "--name" "p")
        (spit (file p "t.edn") (pr-str {:model/name "TicketDoc" :model/version 1 :base []
                                         :fields [{:name "title" :type :string :required true :description "T."}]
                                         :template "# ⸢rev•title⸥\n"}))
        (run p "models" "define" "-f" "t.edn")
        (spit (file p "t.md") "# T\n")
        (run p "docs" "track" "TicketDoc" "t.md" "--name" "t")
        (let [r (:parse-contract (tdocs/find-document (open-store p) "t"))]
          (is (= (set (conj pc/components :digest)) (set (keys r))))
          (is (= 1 (:contract/version r))))
        (is (= [] (signals p)))))))

(deftest a-spec16-document-records-substrate-profile-ancestors-and-selector
  (let [p (project) r (recorded p) store (open-store p)
        base (:id (mstore/find-model store "BaseDoc"))]
    (is (= 2 (:contract/version r)))
    (is (= "sldb-md/blocks:markdown" (:substrate r)))
    (is (= [base] (:ancestors r)))
    (is (re-matches #"sha-256:[0-9a-f]{64}" (:selector r)))
    (is (pc/consistent? host/host r))
    (is (= [] (signals p)) "recorded and in force agree")))

(deftest editing-a-step-of-the-own-selector-is-a-contract-change-not-a-source-change
  (let [p (project) before (recorded p)]
    (define! p (report-class "^(.+?)$"))
    (is (= [[:contract/changed true false [:class :selector]]] (signals p))
        "the file is intact; the class and its composed selector moved")
    (is (= before (recorded p)) "sensing writes nothing")
    (testing "the repair needs no decision: stores update, and then nothing is reported"
      (binding [ast/*parser* "native"]
        (is (true? (:ok (run p "stores" "update")))))
      (is (= [] (signals p)))
      (is (not= (:selector before) (:selector (recorded p)))))))

(deftest editing-an-inherited-step-is-the-same-single-contract-change
  (let [p (project) before (recorded p)]
    (define! p (base-class "^(.+?)$" nil))
    (let [sigs (signals p)]
      (is (= [[:contract/changed true false [:ancestors :selector]]] sigs)
          "one signal, naming the inherited selector; the class itself did not move"))
    (is (= :superseded (:state (tdocs/find-document (open-store p) "r")))
        "the state of M1g and the signal are one fact, not two")
    (binding [ast/*parser* "native"] (run p "stores" "update"))
    (is (= [] (signals p)))
    (is (not= (:ancestors before) (:ancestors (recorded p))))))

(deftest editing-the-file-is-a-source-change-not-a-contract-change
  (let [p (project)]
    (spit (file p "r.md") "# Hello\n\n## Summary\n\nAnother summary.\n")
    (is (= [[:source/mutated false true []]] (signals p)))))

(deftest a-tree-sitter-substrate-without-the-pod-is-parser-unavailable
  (binding [ast/*parser* "native"]
    (let [rd (pc/reading {:substrate {:parser :treesitter :grammar "markdown"} :ancestors []
                          :selector-text "s"})
          r (pc/record host/host (pc/environment) "c" rd)
          no-pod (assoc (pc/environment) :pod? false)
          cmp (fn [e] (select-keys (pc/compare-recorded host/host e r "c" rd) [:status :changed]))]
      (is (= "treesitter:markdown" (:substrate r)))
      (is (= {:status :parser-unavailable :changed []} (cmp no-pod)))
      (is (= {:status :same :changed []} (cmp (assoc (pc/environment) :pod? true)))
          "the same record with the pod is just the same contract"))))

(defn- register-bad-root! [p doc-map bad-root]
  (let [{:keys [backend store]} (cli-store/open! (file p ".kimun"))
        desc (dissoc (revision/tree-descriptor store (:tree doc-map)) :tree)]
    (kstore/commit! backend store
                    {:plan/version 1 :base (:head store) :actor "human/tester" :engines {}
                     :timestamp "2026-01-01T00:00:00.000Z"
                     :ops [{:op :register-tree :id (:tree doc-map) :tree desc :root (:root doc-map) :merkle-root bad-root}]})))

(deftest rebuild-mismatch-works-for-a-spec16-class
  (let [p (project)
        doc (tdocs/find-document (open-store p) "r")
        good (:merkle-root doc)
        bad "sha-256:0000000000000000000000000000000000000000000000000000000000000000"]
    (is (= 2 (:contract/version (:parse-contract doc))) "the document under test is read under a spec 16 contract")
    (register-bad-root! p doc bad)
    (fs/delete-tree (fs/path p ".kimun" store-cache/dirname "objects"))
    (fs/delete-tree (fs/path p ".kimun" store-cache/dirname "docs"))
    (let [s (first (get-in (binding [ast/*parser* "native"] (run p "stores" "sense")) [:data :signals]))]
      (is (= [:contract/rebuild-mismatch :rebuild-mismatch bad good]
             [(:type s) (:category s) (get-in s [:detail :registered-root]) (get-in s [:detail :rebuilt-root])])))
    (binding [ast/*parser* "native"]
      (is (true? (:ok (run p "docs" "update" "r")))))
    (is (= [] (signals p)))))

(deftest a-declared-contract-digest-is-ignored-and-recomputed
  (let [p (str (ktu/temp-dir "kimun-m1h-d-"))]
    (run p "stores" "init" "--name" "p")
    (define! p (base-class "^(.*?)$" "sha256:declared-by-the-descriptor"))
    (spit (file p "b.md") "# Hello\n")
    (binding [ast/*parser* "native"]
      (is (true? (:ok (run p "docs" "track" "BaseDoc" "b.md" "--name" "b")))))
    (let [store (open-store p)
          r (:parse-contract (tdocs/find-document store "b"))
          id (:id (mstore/find-model store "BaseDoc"))]
      (is (= "sha256:declared-by-the-descriptor" (:contract/digest (:descriptor (mstore/find-model store "BaseDoc")))))
      (is (= 2 (:contract/version r)) "a spec 16 class records the version 2 contract")
      (is (not= "sha256:declared-by-the-descriptor" (:digest r)))
      (is (re-matches #"sha-256:[0-9a-f]{64}" (:digest r)))
      (is (pc/consistent? host/host r))
      (binding [ast/*parser* "native"]
        (is (= :same (:status (pc/compare-recorded host/host (pc/environment) r id (tdocs/current-reading store id))))
            "recomputed from the composed selector, the record is the one in force")))
    (testing "two classes that differ only in the declared digest read alike"
      (define! p (assoc (base-class "^(.*?)$" "sha256:another") :model/name "OtherDoc"))
      (let [store (open-store p)]
        (is (= (:selector-text (tdocs/current-reading store (:id (mstore/find-model store "BaseDoc"))))
               (:selector-text (tdocs/current-reading store (:id (mstore/find-model store "OtherDoc")))))
            "the text the selector digest is taken of does not contain the declared digest"))
      (is (= [] (signals p))))))
