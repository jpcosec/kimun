---
id: task-node-host-parity-for-the-v2-kernel
status: active
summary: ''
tags:
- workspace:desk
- artifact:task
- source:drawer
routine: routine-task-node-host-parity-for-the-v2-kernel
current_node: checklist-task-node-host-parity-for-the-v2-kernel-execution-ready
history: []
references:
- desk/drawer/tasks/task-node-host-parity-for-the-v2-kernel.md
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

Triage and resolve the inbox message promoted from `desk/inbox/20260829-174021-suggestion-node-host-parity-for-the-v2-kernel.md`.

## Scope

_State what is in scope and what is out of scope._

Run the whole bb suite on ClojureScript/Node (nbb or shadow-cljs): implement sldb.host.* cljs branches (hash via crypto, NFC via String.normalize, ulid via crypto.randomBytes, fs-store via fs), prove the golden fixtures yield identical ids on both hosts. Deferred from the first slice (docs/v2/02 section 8.1).

## Implementation Path

_Outline the expected implementation route or affected surface._

Promoted from desk/drawer/tasks/task-node-host-parity-for-the-v2-kernel.md.

## Validation

_List the checks required before this task can close._

- bb lint
- bb test

## Done When

_Name the observable condition that makes the task complete._

Promoted work is completed, validated, and closed with a commit.
