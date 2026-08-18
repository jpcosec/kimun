---
id: task-ejecutar-refactor-base-del-n-cleo-sldb
status: active
summary: ''
tags:
- workspace:desk
- artifact:task
- source:drawer
routine: routine-task-ejecutar-refactor-base-del-n-cleo-sldb
current_node: completed
history: []
references:
- desk/drawer/tasks/task-ejecutar-refactor-base-del-núcleo-sldb.md
depends_on: []
pills: []
files: []
checklists:
- checklist-task-ejecutar-refactor-base-del-n-cleo-sldb-execution-ready
- checklist-task-ejecutar-refactor-base-del-n-cleo-sldb-testing-ready
- checklist-task-ejecutar-refactor-base-del-n-cleo-sldb-closeout-ready
task_type: ''
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# Ejecutar refactor base del núcleo SLDB

## Rationale

_Explain why this task exists or the business driver behind it._

Not provided.

## Goal

_Describe the concrete result this task must produce._

_Describe the concrete result this task must produce._

Implementar los primeros cortes estructurales: romper ciclos, extraer servicios de dominio y adelgazar el CLI.

## Scope

_State what is in scope and what is out of scope._

_State what is in scope and what is out of scope._

Romper ciclos de import, sacar lógica de dominio fuera de cli, reorganizar módulos y preparar separación de subsistemas. Sin reintroducir legacy en runtime.

## Implementation Path

_Outline the expected implementation route or affected surface._

Promoted from desk/drawer/tasks/task-ejecutar-refactor-base-del-núcleo-sldb.md.

## Validation

_List the checks required before this task can close._

- pytest

## Done When

_Name the observable condition that makes the task complete._

Promoted work is completed, validated, and closed with a commit.
