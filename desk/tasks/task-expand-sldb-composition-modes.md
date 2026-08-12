---
id: task-expand-sldb-composition-modes
status: active
references:
- desk/drawer/tasks/task-expand-sldb-composition-modes.md
depends_on:
- task-define-sldb-addressability-model
- task-design-sldb-ast-query-primitives
pills:
- pill-007-sldb-text-layer-vs-kgdb-graph-layer
- pill-010-active-board-slice-execution-map
- pill-011-addressability-task-execution-context
- pill-012-query-task-execution-context
- pill-013-composition-task-execution-context
files:
- docs/architecture/sldb-text-layer-vs-kgdb-graph-layer.md
- src/sldb/models/structured_doc.py
- src/sldb/links.py
- tests/test_composition.py
routine: routine-task-expand-sldb-composition-modes
checklists:
- checklist-task-expand-sldb-composition-modes-execution-ready
- checklist-task-expand-sldb-composition-modes-testing-ready
- checklist-task-expand-sldb-composition-modes-closeout-ready
current_node: checklist-task-expand-sldb-composition-modes-execution-ready
history: []
tags:
- workspace:desk
- artifact:task
- source:drawer
---

# Expand SLDB composition modes

## Rationale

_Explain why this task exists or the business driver behind it._

Composition design should reuse the same addressability and query assumptions as the rest of this board. The result should stay text-first and produce readable outputs rather than graph-native reasoning behavior.

## Goal

_Describe the concrete result this task must produce._

Design a broader composition model for SLDB so composition covers more than current transclusion and render-time child summarization.

## Scope

_State what is in scope and what is out of scope._

Consider transclusion composition, summary composition, sectional composition, and query-driven semantic composition while preserving authored locality and readable textual outputs.

## Implementation Path

_Outline the expected implementation route or affected surface._

After the addressability and query contracts are clear, define broader text-first composition modes that consume those contracts instead of inventing a separate reference model.

## Validation

_List the checks required before this task can close._

- pytest

## Done When

_Name the observable condition that makes the task complete._

Promoted work is completed, validated, and closed with a commit.
