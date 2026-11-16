FILE:desk/tasks/task-cache-of-readings-by-git-blob-hash.md
---
id: task-cache-of-readings-by-git-blob-hash
status: draft
summary: ''
tags:
- workspace:desk
- artifact:task
current_node: ritual-execution
history: []
references: []
depends_on:
- task-finish-wip-leer-readers-by-class
pills: []
files: []
task_type: task.code
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# Cache of readings by git blob hash

## Rationale

_Explain why this task exists or the business driver behind it._

diseno-leer.md §4: no cache in the first cut; decide with the number measured on the real desk.

## Goal

_Describe the concrete result this task must produce._

Readings of an unchanged file are not recomputed, if the measurement of the first cut asks for it.

## Scope

_State what is in scope and what is out of scope._

The compressed cache of diseno-declaraciones.md §6, keyed by the hash git already has.

## Implementation Path

_Outline the expected implementation route or affected surface._

Only if the wip/leer measurement shows the need; otherwise close as not needed with the number.

## Validation

_List the checks required before this task can close._

- 

## Done When

_Name the observable condition that makes the task complete._

Measured before and after, or closed with the measurement that makes it unnecessary.

FILE:instruccion
Preflight solo-TaskDoc de la tarea task-cache-of-readings-by-git-blob-hash: reformula el encargo tal como lo leería un ejecutor sin ningún otro contexto, lista ambigüedades con cita textual del input y da el DRY-RUN sin ejecutar nada. No ejecutes nada.

