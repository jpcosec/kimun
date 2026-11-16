FILE:desk/tasks/task-retire-the-previous-store-once-declarations-replace-it.md
---
id: task-retire-the-previous-store-once-declarations-replace-it
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
task_type: task.refactor
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# Retire the previous store once declarations replace it

## Rationale

_Explain why this task exists or the business driver behind it._

Approved plan stages 1-2 (docs/v2/drafts/plan-producto.md): delete what redoes git and the transactional registry, only when its replacement runs.

## Goal

_Describe the concrete result this task must produce._

Path tree, per-file fingerprint, revisions, log, plans, replay and include's identity-registry are gone, with their tests.

## Scope

_State what is in scope and what is out of scope._

Also the explicit and compressed caches, and declare add through rewrite-clj.

## Implementation Path

_Outline the expected implementation route or affected surface._

Nothing is deleted before its replacement runs; then it goes whole.

## Validation

_List the checks required before this task can close._

- 

## Done When

_Name the observable condition that makes the task complete._

Full suite with pod green after each deletion.

FILE:instruccion
Preflight solo-TaskDoc de la tarea task-retire-the-previous-store-once-declarations-replace-it: reformula el encargo tal como lo leería un ejecutor sin ningún otro contexto, lista ambigüedades con cita textual del input y da el DRY-RUN sin ejecutar nada. No ejecutes nada.

