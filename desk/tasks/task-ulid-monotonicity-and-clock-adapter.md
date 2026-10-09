---
id: task-ulid-monotonicity-and-clock-adapter
status: active
summary: 'Place time behind the Clock port in kimun.kernel.ports; ULID monotonicity reconsidered under docs/v2/14 §21.'
tags:
- workspace:desk
- artifact:task
- source:audit
routine: routine-task-ulid-monotonicity-and-clock-adapter
current_node: checklist-task-ulid-monotonicity-and-clock-adapter-execution-ready
history: []
references:
- src/kimun/kernel/ports.cljc
- docs/v2/14-juego-del-lenguaje.md
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

Time sits behind the Clock port, and whether tree ids need monotonic ULIDs is decided.

## Scope

_State what is in scope and what is out of scope._

ULIDs are time-prefixed but not monotonic within the same millisecond; decide whether tree ids need monotonic ULIDs. Move time behind a Clock port in `kimun.kernel.ports` (decision 20 renames `sldb.kernel.*` to `kimun.kernel.*`). Reconsider under `docs/v2/14 §21` and decision 46: ids are structural (S) / semantic (M, G), so the role of ULID minting may change.

## Implementation Path

_Outline the expected implementation route or affected surface._

`src/kimun/kernel/ports.cljc` and its host implementations.

## Validation

_List the checks required before this task can close._

- bb lint
- bb test

## Done When

_Name the observable condition that makes the task complete._

No ambient clock read remains in the kernel (only through the port), and the ULID monotonicity decision is recorded.
