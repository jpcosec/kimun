---
id: task-node-host-parity-for-the-v2-kernel
status: active
summary: 'Node/ClojureScript parity is deferred by decision 14 (drawer/UI); if retaken, use kimun.host.*.'
tags:
- workspace:desk
- artifact:task
- source:audit
routine: routine-task-node-host-parity-for-the-v2-kernel
current_node: checklist-task-node-host-parity-for-the-v2-kernel-execution-ready
history: []
references:
- docs/v2/05-estado.md
- docs/v2/02-sustrato-computacional.md
depends_on: []
pills: []
files: []
checklists:
- checklist-task-node-host-parity-for-the-v2-kernel-execution-ready
- checklist-task-node-host-parity-for-the-v2-kernel-testing-ready
- checklist-task-node-host-parity-for-the-v2-kernel-closeout-ready
task_type: ''
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# Node host parity for the v2 kernel

## Rationale

_Explain why this task exists or the business driver behind it._

Not provided.

## Goal

_Describe the concrete result this task must produce._

If the Node host is ever retaken, the whole suite runs on ClojureScript/Node and the golden fixtures yield identical ids on both hosts.

## Scope

_State what is in scope and what is out of scope._

Deferred by decision 14: Babashka is the definitive host and Node parity stays in the drawer (UI), so this task is not active work. If retaken: implement `kimun.host.*` cljs branches (hash via crypto, NFC via `String.normalize`, ulid via `crypto.randomBytes`, fs-store via `fs`) — decision 20 renames `sldb.host.*` to `kimun.host.*`.

## Implementation Path

_Outline the expected implementation route or affected surface._

`src/kimun/host/*.cljc` reader conditionals; the golden fixtures under `test/fixtures/`.

## Validation

_List the checks required before this task can close._

- bb lint
- bb test
- the suite on ClojureScript/Node (nbb or shadow-cljs) with identical ids

## Done When

_Name the observable condition that makes the task complete._

The golden fixtures produce identical ids on Babashka and Node.
