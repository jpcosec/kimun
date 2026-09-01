---
id: task-external-oracle-for-canonical-bytes
status: active
summary: ''
tags:
- workspace:desk
- artifact:task
- source:drawer
routine: routine-task-external-oracle-for-canonical-bytes
current_node: checklist-task-external-oracle-for-canonical-bytes-execution-ready
history: []
references:
- desk/drawer/tasks/task-external-oracle-for-canonical-bytes.md
depends_on: []
pills: []
files: []
checklists:
- checklist-task-external-oracle-for-canonical-bytes-execution-ready
- checklist-task-external-oracle-for-canonical-bytes-testing-ready
- checklist-task-external-oracle-for-canonical-bytes-closeout-ready
task_type: ''
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# External oracle for canonical-bytes

## Rationale

_Explain why this task exists or the business driver behind it._

Not provided.

## Goal

_Describe the concrete result this task must produce._

Triage and resolve the inbox message promoted from `desk/inbox/20260829-174023-suggestion-external-oracle-for-canonical-bytes.md`.

## Scope

_State what is in scope and what is out of scope._

A second, independent implementation of canonical-bytes (Python script scripts/canon_oracle.py) computing SHA-256 over the same fixture inputs so conformity is not circular; compare against test/fixtures/nodes.edn and trees.edn ids.

## Implementation Path

_Outline the expected implementation route or affected surface._

Promoted from desk/drawer/tasks/task-external-oracle-for-canonical-bytes.md.

## Validation

_List the checks required before this task can close._

- bb lint
- bb test
- bb oracle

## Done When

_Name the observable condition that makes the task complete._

Promoted work is completed, validated, and closed with a commit.
