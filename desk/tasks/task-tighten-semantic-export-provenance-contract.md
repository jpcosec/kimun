---
id: task-tighten-semantic-export-provenance-contract
status: active
references:
- desk/drawer/tasks/task-tighten-semantic-export-provenance-contract.md
depends_on:
- task-define-sldb-addressability-model
- task-design-sldb-ast-query-primitives
pills:
- pill-007-sldb-text-layer-vs-kgdb-graph-layer
- pill-010-active-board-slice-execution-map
- pill-011-addressability-task-execution-context
- pill-014-semantic-export-task-execution-context
files:
- docs/architecture/sldb-text-layer-vs-kgdb-graph-layer.md
- docs/architecture/semantic-export-boundary.md
- src/sldb/store/export.py
- src/sldb/core/ast.py
- src/sldb/store/query_engine/structural.py
routine: routine-task-tighten-semantic-export-provenance-contract
checklists:
- checklist-task-tighten-semantic-export-provenance-contract-execution-ready
- checklist-task-tighten-semantic-export-provenance-contract-testing-ready
- checklist-task-tighten-semantic-export-provenance-contract-closeout-ready
current_node: checklist-task-tighten-semantic-export-provenance-contract-execution-ready
history: []
tags:
- workspace:desk
- artifact:task
- source:drawer
---

# Tighten semantic export provenance contract

## Rationale

_Explain why this task exists or the business driver behind it._

Export provenance must preserve the source-side structural references defined by the addressability model without collapsing them into downstream graph identity. This task turns that requirement into an explicit handoff contract.

## Goal

_Describe the concrete result this task must produce._

Clarify what provenance and structure SLDB must preserve when exporting graph-ready knowledge to downstream systems such as KGDB.

## Scope

_State what is in scope and what is out of scope._

Cover source document identity, section/field provenance where relevant, semantic tags, model identity, and hash/extraction lineage. Make the handoff explicit enough that KGDB can reason globally without losing the chain back to authored textual truth.

## Implementation Path

_Outline the expected implementation route or affected surface._

Use the board's addressability and text-vs-graph boundary contracts to define what provenance survives export and how downstream systems can trace graph artifacts back to authored textual units.

## Validation

_List the checks required before this task can close._

- pytest

## Done When

_Name the observable condition that makes the task complete._

Promoted work is completed, validated, and closed with a commit.
