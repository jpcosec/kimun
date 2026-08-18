---
id: task-design-sldb-ast-query-primitives
status: active
summary: ''
tags:
- workspace:desk
- artifact:task
- source:drawer
routine: routine-task-design-sldb-ast-query-primitives
current_node: completed
history: []
references:
- desk/drawer/tasks/task-design-sldb-ast-query-primitives.md
depends_on: []
pills: []
files: []
checklists:
- checklist-task-design-sldb-ast-query-primitives-execution-ready
- checklist-task-design-sldb-ast-query-primitives-testing-ready
- checklist-task-design-sldb-ast-query-primitives-closeout-ready
task_type: ''
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# Design SLDB AST query primitives

## Rationale

_Explain why this task exists or the business driver behind it._

Not provided.

## Goal

_Describe the concrete result this task must produce._

_Describe the concrete result this task must produce._

Define the first public query primitives that should operate directly on SLDB document structure rather than on downstream graph semantics.

## Scope

_State what is in scope and what is out of scope._

_State what is in scope and what is out of scope._

Cover questions such as section lookup, field ownership, block addressing, section-body retrieval, and document-local structural search. Distinguish these from graph-native traversal and inference that belong in KGDB.

## Implementation Path

_Outline the expected implementation route or affected surface._

Promoted from desk/drawer/tasks/task-design-sldb-ast-query-primitives.md.

## Validation

_List the checks required before this task can close._

- pytest

## Done When

_Name the observable condition that makes the task complete._

Promoted work is completed, validated, and closed with a commit.
