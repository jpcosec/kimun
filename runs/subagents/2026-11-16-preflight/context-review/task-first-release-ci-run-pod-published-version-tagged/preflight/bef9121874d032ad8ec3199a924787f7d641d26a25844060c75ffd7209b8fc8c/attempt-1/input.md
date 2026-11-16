FILE:desk/tasks/task-first-release-ci-run-pod-published-version-tagged.md
---
id: task-first-release-ci-run-pod-published-version-tagged
status: draft
summary: ''
tags:
- workspace:desk
- artifact:task
current_node: ritual-execution
history: []
references: []
depends_on: []
pills: []
files: []
task_type: task.code
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# First release CI run pod published version tagged

## Rationale

_Explain why this task exists or the business driver behind it._

Approved plan stage 6. The release CI was written and never executed; release.edn has :sha256 nil; only linux-amd64 was built and tested.

## Goal

_Describe the concrete result this task must produce._

An installable, tagged kimun.

## Scope

_State what is in scope and what is out of scope._

.github/workflows/, scripts/release.clj, release.edn, install docs.

## Implementation Path

_Outline the expected implementation route or affected surface._

Run the CI, publish the pod, pin its hash, test the other platforms, document install.

## Validation

_List the checks required before this task can close._

- 

## Done When

_Name the observable condition that makes the task complete._

The bundle installs on a clean machine and the tour runs.

FILE:instruccion
Preflight solo-TaskDoc de la tarea task-first-release-ci-run-pod-published-version-tagged: reformula el encargo tal como lo leería un ejecutor sin ningún otro contexto, lista ambigüedades con cita textual del input y da el DRY-RUN sin ejecutar nada. No ejecutes nada.

