---
id: board-sldb-desk
scope: sldb-local-desk
tasks:
- desk/tasks/task-define-sldb-addressability-model.md
- desk/tasks/task-design-sldb-ast-query-primitives.md
- desk/tasks/task-expand-sldb-composition-modes.md
- desk/tasks/task-tighten-semantic-export-provenance-contract.md
pills:
- desk/pills/pill-001-sldb-vs-deskops-boundary.md
- desk/pills/pill-002-onboarding-surface-before-depth.md
- desk/pills/pill-003-template-marker-roundtrip-contract.md
- desk/pills/pill-004-store-init-idempotency.md
- desk/pills/pill-005-store-health-is-sldb-owned.md
- desk/pills/pill-006-model-registration-is-queryable.md
- desk/pills/pill-007-sldb-text-layer-vs-kgdb-graph-layer.md
- desk/pills/pill-008-sldb-cli-document-contract.md
- desk/pills/pill-009-cli-error-and-serialization-failure-mode.md
rituals:
- desk/rituals/execution.md
- desk/rituals/testing.md
- desk/rituals/closeout.md
tags:
- system:sldb
- system:deskops
- workspace:desk
- topic:routing
---

# Desk Board

## Purpose

_Explain what this board routes and why it exists._



## Notes

_Add short operational notes about the current routed set._

- Define SLDB addressability model [active] - Define how meaningful document units in SLDB receive stable or derivable addresses so they can be queried, updated, composed, and exported with provenance.
- Design SLDB AST query primitives [active] - Define the first public query primitives that should operate directly on SLDB document structure rather than on downstream graph semantics.
- Expand SLDB composition modes [active] - Design a broader composition model for SLDB so composition covers more than current transclusion and render-time child summarization.

## Task Details

_Generated from the task references above._

- Define SLDB addressability model [active] - Define how meaningful document units in SLDB receive stable or derivable addresses so they can be queried, updated, composed, and exported with provenance.
- Design SLDB AST query primitives [active] - Define the first public query primitives that should operate directly on SLDB document structure rather than on downstream graph semantics.
- Expand SLDB composition modes [active] - Design a broader composition model for SLDB so composition covers more than current transclusion and render-time child summarization.
- Tighten semantic export provenance contract [active] - Clarify what provenance and structure SLDB must preserve when exporting graph-ready knowledge to downstream systems such as KGDB.
