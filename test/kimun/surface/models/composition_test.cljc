(ns kimun.surface.models.composition-test
  "Model compositions (v1 `__compositions__`): the ports of sldb v1's
   tests/test_composition.py and tests/api/test_composition_ref_root.py. The
   referenced documents live in an in-memory map `{path text}`; the ref loader
   resolves a relative ref against a caller-given root, never against a cwd."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [kimun.surface.models.render :as render]))

(defn- field [n] {:name n :type :string :required true})

(def ^:private task-doc
  {:model/name "ComposedTaskDoc" :model/version 1 :fields (mapv field ["title" "status" "goal"])
   :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥\n\n## Goal\n\n⸢rev•goal⸥"})

(def ^:private step-doc
  {:model/name "ComposedStepDoc" :model/version 1 :fields (mapv field ["title" "action" "outcome"])
   :template "# ⸢rev•title⸥\n\n## Action\n\n⸢rev•action⸥\n\n## Outcome\n\n⸢rev•outcome⸥"})

(def ^:private board-doc
  {:model/name "ComposedBoardDoc" :model/version 1
   :fields [(field "title") {:name "tasks" :type [:list :string] :required false :default []}]
   :compositions {"task_summaries" {"source_field" "tasks" "model" "composition_models:ComposedTaskDoc"
                                    "template" "- {title} [{status}] - {goal}"}}
   :template "# ⸢rev•title⸥\n\n## Tasks\n\n- ⸢rev,list•tasks⸥\n\n## Task Details\n\n⸢render•task_summaries⸥"})

(def ^:private ritual-doc
  {:model/name "ComposedRitualDoc" :model/version 1
   :fields [(field "title") {:name "steps" :type [:list :string] :required false :default []}]
   :compositions {"step_details" {"source_field" "steps" "model" "composition_models:ComposedStepDoc"
                                  "template" "1. {title}: {action} -> {outcome}"}}
   :template "# ⸢rev•title⸥\n\n## Steps\n\n1. ⸢rev,list•steps⸥\n\n## Step Details\n\n⸢render•step_details⸥"})

;; tests/api/test_composition_ref_root.py: Task and Board
(def ^:private api-task
  {:model/name "Task" :model/version 1 :fields (mapv field ["title" "status"])
   :template "# ⸢rev•title⸥\n\nStatus: ⸢rev•status⸥"})

(def ^:private api-board
  {:model/name "Board" :model/version 1
   :fields [(field "title") {:name "tasks" :type [:list :string] :required false :default []}]
   :compositions {"summary" {"source_field" "tasks" "model" "m:Task" "template" "- {title} [{status}]"}}
   :template "# ⸢rev•title⸥\n\n## Tasks\n\n- ⸢rev,list•tasks⸥\n\n## Summary\n\n⸢render•summary⸥"})

(def ^:private models
  {"composition_models:ComposedTaskDoc" task-doc "composition_models:ComposedStepDoc" step-doc "m:Task" api-task})

(defn- ctx
  "A render context over in-memory `files`; relative refs resolve against
   `root` when given and against nothing otherwise (no cwd in the engine)."
  [files root]
  {:ref-model models
   :ref-text (fn [ref] (cond (str/starts-with? ref "/") (get files ref)
                             root (get files (str root "/" ref))))})

(def ^:private task-a
  (render/render task-doc {"title" "Improve model editing" "status" "active"
                           "goal" "Support safer contract editing from the CLI."}))

;; v1: tests/test_composition.py::test_board_doc_renders_composed_task_summaries
(deftest board-doc-renders-composed-task-summaries
  (let [out (render/render board-doc {"title" "Composition Board" "tasks" ["/tmp/p/task-a.md"]}
                           (ctx {"/tmp/p/task-a.md" task-a} nil))]
    (is (str/includes? out "## Task Details"))
    (is (str/includes? out "- Improve model editing [active] - Support safer contract editing from the CLI."))))

;; v1: tests/test_composition.py::test_composition_skips_invalid_referenced_documents
(deftest composition-skips-invalid-referenced-documents
  (let [out (render/render board-doc {"title" "Composition Board" "tasks" ["/tmp/p/task-invalid.md"]}
                           (ctx {"/tmp/p/task-invalid.md" "# Missing Status\n\n## Goal\n\nIncomplete."} nil))]
    (is (str/includes? out "## Task Details"))
    (is (not (str/includes? out "Missing Status")))))

;; v1: tests/test_composition.py::test_ritual_doc_renders_composed_step_details
(deftest ritual-doc-renders-composed-step-details
  (let [step (render/render step-doc {"title" "Validate draft" "action" "Run model validation against tracked docs."
                                      "outcome" "A promotable contract draft."})
        out (render/render ritual-doc {"title" "Composition Ritual" "steps" ["/tmp/p/step-a.md"]}
                           (ctx {"/tmp/p/step-a.md" step} nil))]
    (is (str/includes? out "## Step Details"))
    (is (str/includes? out "1. Validate draft: Run model validation against tracked docs. -> A promotable contract draft."))))

;; v1: tests/test_composition.py::test_composition_refs_resolve_against_the_root_not_the_cwd
(deftest composition-refs-resolve-against-the-root-not-the-cwd
  (let [files {"/tmp/p/task-a.md" task-a}
        payload {"title" "Composition Board" "tasks" ["task-a.md"]}
        from-root (render/render board-doc payload (ctx files "/tmp/p"))
        from-cwd (render/render board-doc payload (ctx files nil))]
    (is (str/includes? from-root "Support safer contract editing from the CLI."))
    (is (not (str/includes? from-cwd "- Improve model editing")))
    (is (str/includes? from-cwd "## Task Details"))
    (is (= from-cwd (render/render board-doc payload)) "no context at all renders the composed field empty")))

(def ^:private world {"/proj/task-a.md" (render/render api-task {"title" "Ship the CLI" "status" "active"})})
(def ^:private summary "- Ship the CLI [active]")

;; v1: tests/api/test_composition_ref_root.py::test_create_document_composes_against_the_project_root
;; (engine-level: the text `create_document` writes is this render with the project root)
(deftest create-document-composes-against-the-project-root
  (is (str/includes? (render/render api-board {"title" "Board" "tasks" ["task-a.md"]} (ctx world "/proj")) summary)))

;; v1: tests/api/test_composition_ref_root.py::test_save_document_payload_composes_against_the_project_root
;; (engine-level: re-rendering a changed payload keeps composing against the root)
(deftest save-document-payload-composes-against-the-project-root
  (let [text (render/render api-board {"title" "Renamed" "tasks" ["task-a.md"]} (ctx world "/proj"))]
    (is (str/includes? text "# Renamed"))
    (is (str/includes? text summary))))

;; v1: tests/api/test_composition_ref_root.py::test_document_segments_compose_against_the_project_root
;; (engine-level render half; the segments half lives in segments_test)
(deftest document-render-composes-against-the-project-root
  (let [payload {"title" "Board" "tasks" ["task-a.md" "`task-a.md`" "missing.md"]}
        text (render/render api-board payload (ctx world "/proj"))]
    (is (str/ends-with? text (str "## Summary\n\n" summary "\n" summary)) "backticks stripped, missing ref skipped")
    (is (= text (render/render (-> api-board (dissoc :compositions) (assoc :py/compositions (:compositions api-board)))
                               payload (ctx world "/proj")))
        "the key v1's exporter writes, :py/compositions, is read too")))
