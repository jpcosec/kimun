---
id: task-define-sldb-addressability-model
status: active
references:
- desk/drawer/tasks/task-define-sldb-addressability-model.md
depends_on: []
pills:
- pill-007-sldb-text-layer-vs-kgdb-graph-layer
- pill-010-active-board-slice-execution-map
- pill-011-addressability-task-execution-context
files:
- docs/architecture/sldb-text-layer-vs-kgdb-graph-layer.md
- docs/architecture/semantic-export-boundary.md
- docs/atoms/structured-text.atom.md
- src/sldb/core/ast.py
- src/sldb/store/query_engine/structural.py
- src/sldb/store/export.py
routine: routine-task-define-sldb-addressability-model
checklists:
- checklist-task-define-sldb-addressability-model-execution-ready
- checklist-task-define-sldb-addressability-model-testing-ready
- checklist-task-define-sldb-addressability-model-closeout-ready
current_node: checklist-task-define-sldb-addressability-model-execution-ready
history: []
tags:
- workspace:desk
- artifact:task
- source:drawer
---

# Define SLDB addressability model

## Rationale

_Explain why this task exists or the business driver behind it._

Addressability is the upstream contract for the entire active board slice. Query primitives, composition modes, and export provenance all need a shared answer for what document units can be named and how those names remain stable or derivable.

## Goal

_Describe the concrete result this task must produce._

Define how meaningful document units in SLDB receive stable or derivable addresses so they can be queried, updated, composed, and exported with provenance.

## Scope

_State what is in scope and what is out of scope._

Cover document, section, subsection, field, list item, table row, and other meaningful textual units. Clarify which addresses are canonical, which are derived, and how address stability should behave across normal document edits.

## Implementation Path

_Outline the expected implementation route or affected surface._

Design the addressability contract first and materialize it in durable docs before downstream query/composition/export tasks harden their own assumptions. This task should define canonical versus derived addresses, covered unit types, and edit-stability expectations.

## Validation

_List the checks required before this task can close._

- pytest

## Done When

_Name the observable condition that makes the task complete._

Promoted work is completed, validated, and closed with a commit.
