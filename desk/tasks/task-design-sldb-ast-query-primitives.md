---
id: task-design-sldb-ast-query-primitives
status: active
references:
- desk/drawer/tasks/task-design-sldb-ast-query-primitives.md
depends_on:
- task-define-sldb-addressability-model
pills:
- pill-007-sldb-text-layer-vs-kgdb-graph-layer
- pill-010-active-board-slice-execution-map
- pill-011-addressability-task-execution-context
- pill-012-query-task-execution-context
files:
- docs/architecture/sldb-text-layer-vs-kgdb-graph-layer.md
- src/sldb/cli/commands/query.py
- src/sldb/store/query_engine/structural.py
- src/sldb/store/query_engine/structural_queries.py
- src/sldb/core/ast.py
routine: routine-task-design-sldb-ast-query-primitives
checklists:
- checklist-task-design-sldb-ast-query-primitives-execution-ready
- checklist-task-design-sldb-ast-query-primitives-testing-ready
- checklist-task-design-sldb-ast-query-primitives-closeout-ready
current_node: checklist-task-design-sldb-ast-query-primitives-execution-ready
history: []
tags:
- workspace:desk
- artifact:task
- source:drawer
---

# Design SLDB AST query primitives

## Rationale

_Explain why this task exists or the business driver behind it._

This task should consume the addressability model rather than inventing selector semantics in isolation. It defines what a first public structural query surface should ask and return inside the text layer.

## Goal

_Describe the concrete result this task must produce._

Define the first public query primitives that should operate directly on SLDB document structure rather than on downstream graph semantics.

## Scope

_State what is in scope and what is out of scope._

Cover questions such as section lookup, field ownership, block addressing, section-body retrieval, and document-local structural search. Distinguish these from graph-native traversal and inference that belong in KGDB.

## Implementation Path

_Outline the expected implementation route or affected surface._

Use the addressability contract as the upstream input, then define the first structural query primitives that operate on document-local structure without leaking into graph traversal or inference.

## Validation

_List the checks required before this task can close._

- pytest

## Done When

_Name the observable condition that makes the task complete._

Promoted work is completed, validated, and closed with a commit.
