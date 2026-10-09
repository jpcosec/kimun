---
id: task-unreachable-objects-report-and-gc-groundwork
status: active
summary: 'Pending: unreachable-objects report + name index/GC, reshaped by the stable store (docs/v2/12 §1 (Kernel y store, puntos 1 y 6)).'
tags:
- workspace:desk
- artifact:task
- source:audit
routine: routine-task-unreachable-objects-report-and-gc-groundwork
current_node: checklist-task-unreachable-objects-report-and-gc-groundwork-execution-ready
history: []
references:
- docs/v2/12-ecosistema-y-pendientes.md
- docs/v2/14-juego-del-lenguaje.md
depends_on: []
pills: []
files: []
checklists:
- checklist-task-unreachable-objects-report-and-gc-groundwork-execution-ready
- checklist-task-unreachable-objects-report-and-gc-groundwork-testing-ready
- checklist-task-unreachable-objects-report-and-gc-groundwork-closeout-ready
task_type: ''
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# Unreachable-objects report and GC groundwork

## Rationale

_Explain why this task exists or the business driver behind it._

Not provided.

## Goal

_Describe the concrete result this task must produce._

A report of garbage candidates (unreachable objects) and the groundwork for the name index and GC.

## Scope

_State what is in scope and what is out of scope._

`verify` only checks reachable objects; add a report of unreachable objects as garbage candidates. Reshaped by the stable store (`docs/v2/12 §1` (Kernel y store, puntos 1 y 6); decision 45 / `docs/v2/14 §20`): the document tree and the build plan live in `<store>/cache/`, the plan leaves the CAS, and with Merkle-per-path the name index and GC change shape — do not ground this on the old `objects/` plus milestone 8. Blocked by the store-stable task (`docs/v2/12 §1`, punto 1).

## Implementation Path

_Outline the expected implementation route or affected surface._

`src/kimun/` store verification and cache; the derived tree under `<store>/cache/`.

## Validation

_List the checks required before this task can close._

- bb lint
- bb test
- a store with known garbage reports exactly the unreachable objects

## Done When

_Name the observable condition that makes the task complete._

The store reports its garbage candidates without deleting anything, under the stable-store layout.
