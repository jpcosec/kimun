---
id: task-ulid-monotonicity-and-clock-adapter
status: active
summary: ''
tags:
- workspace:desk
- artifact:task
- source:drawer
routine: routine-task-ulid-monotonicity-and-clock-adapter
current_node: checklist-task-ulid-monotonicity-and-clock-adapter-execution-ready
history: []
references:
- desk/drawer/tasks/task-ulid-monotonicity-and-clock-adapter.md
depends_on: []
pills: []
files: []
checklists:
- checklist-task-ulid-monotonicity-and-clock-adapter-execution-ready
- checklist-task-ulid-monotonicity-and-clock-adapter-testing-ready
- checklist-task-ulid-monotonicity-and-clock-adapter-closeout-ready
task_type: ''
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# ULID monotonicity and clock adapter

## Rationale

_Explain why this task exists or the business driver behind it._

Not provided.

## Goal

_Describe the concrete result this task must produce._

Triage and resolve the inbox message promoted from `desk/inbox/20260829-174025-suggestion-ulid-monotonicity-and-clock-adapter.md`.

## Scope

_State what is in scope and what is out of scope._

ULIDs are time-prefixed but not monotonic within the same millisecond; decide whether tree ids need monotonic ULIDs and move time behind a Clock port in sldb.kernel.ports.

## Implementation Path

_Outline the expected implementation route or affected surface._

Promoted from desk/drawer/tasks/task-ulid-monotonicity-and-clock-adapter.md.

## Validation

_List the checks required before this task can close._

- bb lint
- bb test

## Done When

_Name the observable condition that makes the task complete._

Promoted work is completed, validated, and closed with a commit.
