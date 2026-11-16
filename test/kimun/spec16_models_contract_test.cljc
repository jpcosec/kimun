(ns kimun.spec16-models-contract-test
  "Contract checks for M1 (Spec 16 — docs/v2/16-modelos-como-subgrafos.md):
   anchoring vs. generation separation, EDN selector schema & R3 continuity,
   DAG inheritance semantics (make-hierarchy/derive/isa?), the R3 strict-extraction
   discrepancies, and explicit open-decision boundaries without resolving D0."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kimun.surface.models.subgraph :as sg]))

(defn- repo-path
  "Resolve a repository-relative path inside the active working directory."
  [& parts]
  (str (apply fs/path (System/getProperty "user.dir") parts)))

(defn- read-edn-rel
  "Load and parse an EDN file relative to the active repository root."
  [& parts]
  (edn/read-string (slurp (apply repo-path parts))))

(def ^:private expected-capabilities
  "The six R3 selector capabilities C1 through C6 required by Spec 16 §3.3."
  [:C1 :C2 :C3 :C4 :C5 :C6])

(def ^:private r3-selector-files
  "Measured R3 EDN selector descriptors preserved under runs/research/2026-10-08/R3-selectors/edn/."
  ["AtomDoc.edn" "ChecklistDoc.edn" "TaskDoc.edn"])

(deftest spec16-fixture-schema-and-open-decisions-explicit
  (let [fx (read-edn-rel "test" "fixtures" "models" "spec16_examples.edn")
        open-decs (:open-decisions fx)
        caps (:capabilities fx)
        models (:models fx)]
    (is (= 1 (:schema-version fx)))
    (is (= "M1" (:task fx)))
    (is (= :spec-draft (:status fx)))
    (is (= :in-progress (get-in fx [:implementation :status])))
    (is (= [:M1a] (get-in fx [:implementation :slices])))
    (is (true? (get-in fx [:implementation :code-changes])))
    (is (= #{:K1e} (set (get-in fx [:implementation :blocked-by]))))

    (testing "Open decisions are declared explicitly and D0 prescribes no ID scheme"
      (doseq [k [:A1.1 :F10]]
        (is (= :open (get-in open-decs [k :status])) (str "Decision " k " must remain :open")))
      ;; D0 is no longer the owner's to answer: the council derived its S–M contract (14 §21)
      (is (= :derived (get-in open-decs [:D0 :status])))
      (is (false? (get-in open-decs [:D0 :spec-prescribes-id-scheme]))))

    (testing "All R3 selector capabilities C1-C6 are represented"
      (is (= expected-capabilities (mapv :id caps)))
      (doseq [cid [:C3 :C4 :C5 :C6]]
        (let [cap (some #(when (= cid (:id %)) %) caps)]
          (is (= :treesitter (:substrate cap))
              (str cid " relies on tree-sitter substrate")))))

    (testing "Models cleanly separate :anchoring and :generation plus authority metadata"
      (doseq [m models]
        (testing (:model/name m)
          (is (string? (:model/name m)))
          (is (= 2 (:model/version m)))
          (is (vector? (:base m)))
          (is (contains? #{:source :generated} (:document-role m)))
          (is (string? (:contract/digest m)))
          (is (contains? #{:treesitter :sldb-md/blocks} (get-in m [:substrate :parser])))
          (is (map? (:anchoring m)))
          (is (vector? (get-in m [:anchoring :fields])))
          (is (vector? (get-in m [:anchoring :selector])))
          (is (map? (get-in m [:generation :projections])))
          (is (pos? (count (get-in m [:generation :projections]))))
          (if (= :source (:document-role m))
            (do
              (is (= :disk-file (:authority m)))
              (is (pos? (count (get-in m [:anchoring :selector])))))
            (do
              (is (= :instance-data (:authority m)))
              (is (= :F10 (:requires-open-decision m)))
              (is (= :F-W09 (:negative-control m)))
              (is (empty? (get-in m [:anchoring :selector])))))))
      (let [checklist (some #(when (= "ChecklistDoc" (:model/name %)) %) models)]
        (is (>= (count (get-in checklist [:generation :projections])) 2)
            "ChecklistDoc demonstrates multiple generation projections from one anchoring model")))))

(deftest spec16-hierarchy-derive-isa-and-selector-composition
  (let [fx (read-edn-rel "test" "fixtures" "models" "spec16_examples.edn")
        models (:models fx)
        by-name (sg/index-models models)
        h (sg/hierarchy models)]
    (testing "Base DAG is acyclic and every :base reference resolves"
      (doseq [{:keys [model/name base]} models]
        (doseq [p base]
          (is (contains? by-name p) (str name " references missing base " p)))
        (is (vector? (sg/ancestors-of by-name name)))))
    (testing "Clojure derive/isa? inheritance works for transitive subclasses"
      (is (sg/isa-model? h "ResumenTecnicoDoc" "ResumenDoc"))
      (is (sg/isa-model? h "ResumenTecnicoDoc" "PrimitiveDoc"))
      (is (sg/isa-model? h "ChecklistDoc" "PrimitiveDoc"))
      (is (sg/isa-model? h "ResumenDigestView" "ResumenDoc"))
      (is (not (sg/isa-model? h "ResumenDoc" "ResumenTecnicoDoc"))))
    (testing "Anchoring selector composition appends parent steps and reindexes :outer-index deterministically"
      (let [checklist-steps (sg/compose-selector by-name "ChecklistDoc")
            resumen-steps (sg/compose-selector by-name "ResumenDoc")
            resumen-tec-steps (sg/compose-selector by-name "ResumenTecnicoDoc")]
        (is (= [0 1 2 3 4] (mapv :outer-index checklist-steps)))
        (is (= ["front_matter" "heading" "heading" "paragraph" "bullet_list"]
               (mapv :outer-type checklist-steps)))
        (is (= (vec (range 8)) (mapv :outer-index resumen-tec-steps)))
        (is (= "^\\QSummary\\E$"
               (get-in resumen-steps [2 :regex])
               (get-in resumen-tec-steps [2 :regex])))
        (is (= "^\\QComplexity\\E$" (get-in resumen-tec-steps [4 :regex])))
        (is (= "^\\QTradeoffs\\E$" (get-in resumen-tec-steps [6 :regex])))))
    (testing "Generation projections compose independently from anchoring selectors"
      (let [projs (sg/compose-projections by-name "ResumenTecnicoDoc")]
        (is (= #{:markdown/default :summary/technical} (set (keys projs))))
        (is (= ["id" "title" "summary" "complexity" "tradeoffs"]
               (get-in projs [:summary/technical :fields])))))))

(deftest r3-research-edn-selectors-and-discrepancy-contract
  (let [fx (read-edn-rel "test" "fixtures" "models" "spec16_examples.edn")
        summary (:r3-measured-summary fx)
        discrepancies (:r3-discrepancy-cases fx)]
    (testing "R3 benchmark figures match runs/research/2026-10-08/R3-report.md"
      (is (= 428 (:corpus-docs summary)))
      (is (= 428 (apply + (vals (:desk-model-counts summary)))))
      (is (= {:matched 428 :total 428 :pct 100.0 :time-ms 100 :p50-ms 0 :rss-mb 89}
             (get-in summary [:candidates :edn])))
      (is (= 423 (get-in summary [:candidates :treesitter :matched])))
      (is (= 423 (get-in summary [:candidates :datalog :matched])))
      (is (= 543 (get-in summary [:baseline :total-docs])))
      (is (= 5 (apply + (map :count discrepancies))))
      (let [by-id (into {} (map (juxt :id identity) discrepancies))]
        (is (= :missing (get-in by-id [:atom-missing-answer :strict-expected])))
        (is (= 3 (count (get-in by-id [:atom-missing-answer :docs]))))
        (is (= "" (get-in by-id [:task-empty-done-when :strict-expected])))
        (is (= 2 (count (get-in by-id [:task-empty-done-when :docs]))))))

    (testing "Existing R3 EDN selector files follow the step shape adopted by Spec 16"
      (doseq [fname r3-selector-files]
        (let [desc (read-edn-rel "runs" "research" "2026-10-08" "R3-selectors" "edn" fname)]
          (is (map? desc) fname)
          (is (string? (:model/name desc)) fname)
          (is (integer? (:model/version desc)) fname)
          (is (vector? (:selector desc)) fname)
          (is (pos? (count (:selector desc))) fname)
          (doseq [step (:selector desc)]
            (is (map? step))
            (is (integer? (:outer-index step)) fname)
            (is (string? (:outer-type step)) fname)
            (is (keyword? (:handler step)) fname)))))))

(deftest spec16-document-contains-required-sections-and-links
  (let [spec-doc (slurp (repo-path "docs" "v2" "16-modelos-como-subgrafos.md"))
        readme-doc (slurp (repo-path "docs" "v2" "README.md"))
        models-doc (slurp (repo-path "docs" "v2" "07-modelos-como-nodos.md"))
        backlog-doc (slurp (repo-path "docs" "v2" "12-ecosistema-y-pendientes.md"))]
    (doseq [heading ["## 1." "## 2." "## 3." "## 4." "## 5." "## 6." "## 7."]]
      (is (str/includes? spec-doc heading) (str "Missing section " heading " in spec 16")))
    (doseq [needle ["428" "100 ms" "D0" "A1.1" "F10" "14 §18" "14 §20" "isa?"]]
      (is (str/includes? spec-doc needle) (str "Spec 16 must mention " needle)))
    (is (str/includes? readme-doc "16-modelos-como-subgrafos.md"))
    (is (str/includes? models-doc "16-modelos-como-subgrafos.md"))
    (is (str/includes? backlog-doc "16-modelos-como-subgrafos.md"))))
