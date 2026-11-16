FILE:desk/tasks/task-finish-wip-leer-readers-by-class.md
---
id: task-finish-wip-leer-readers-by-class
status: draft
summary: ''
tags:
- workspace:desk
- artifact:task
current_node: ritual-execution
history: []
references: []
depends_on:
- task-toolchain-on-robocop2-and-suite-reproduced
pills: []
files: []
task_type: task.code
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# Finish wip-leer readers by class

## Rationale

_Explain why this task exists or the business driver behind it._

Owner criterion of done 2 (2026-11-16): ingest an advanced project such as deskops. This cut opens it (docs/v2/drafts/diseno-leer.md §5 cut 1).

## Goal

_Describe the concrete result this task must produce._

Branch wip/leer validated and fast-forwarded into main, with numbers measured on a copy of the real deskops desk.

## Scope

_State what is in scope and what is out of scope._

src/kimun/surface/reading.cljc, its tests, and the three existing tests that compare the whole :summary of declare read (cli/declare_test, cli/declare_project_test, cli/mcp_test).

## Implementation Path

_Outline the expected implementation route or affected surface._

Rebase on main; adjust the 3 tests; copy SLDB_1/deskops/desk to a temp dir and run readers for atoms, tasks and materialization contracts (draft in runs/subagents/2026-11-16-leer/deskops-readers/); REPORT; full suite. State in docs/v2/drafts/traspaso-2026-11-17.md §3.

## Validation

_List the checks required before this task can close._

- bb test

## Done When

_Name the observable condition that makes the task complete._

Full suite with pod green; files, propositions, errors and times on the real desk recorded in runs/subagents/.

FILE:instruccion
Preflight solo-TaskDoc de la tarea task-finish-wip-leer-readers-by-class: reformula el encargo tal como lo leería un ejecutor sin ningún otro contexto, lista ambigüedades con cita textual del input y da el DRY-RUN sin ejecutar nada. No ejecutes nada.

