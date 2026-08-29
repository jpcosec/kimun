---
# board-xxx
id: board-001
# Affected workspace or domain
scope: desk
# List of task-xxx paths
tasks:
- desk/tasks/task-implement-v2-first-slice-node-pool-trees-revisions-persistence.md
# List of pill-xxx paths
pills:
- desk/contexts/pills.md
- desk/contexts/pill-planning-contracts.md
# List of ritual-xxx paths
rituals:
- desk/rituals/execution.md
- desk/rituals/testing.md
- desk/rituals/closeout.md
# e.g., system:sldb, workspace:desk
tags:
- workspace:desk
- system:sldb
---

# sldb-refactor-worktree Board

## Purpose

_Explain what this board routes and why it exists._



## Notes

_Add short operational notes about the current routed set._

- `desk/drawer/features/feature-clojure-kernel-foundation-first-slice.md`
- `desk/drawer/features/feature-lisp-control-and-data-surface-first-slice.md`
- `desk/drawer/features/feature-markdown-roundtrip-first-slice.md`

## Task Details

_Generated from the task references above._

- Implement v2 first slice: node pool, trees, revisions, persistence [draft] - A pure .cljc kernel where content-addressed nodes (S/M/G classes) live in one pool, trees are ordered ownership-edge indexes with lazy per-tree Merkle roots, TransactionPlans (EDN) are validated and applied into immutable revisions with supersedes edges and CAS heads, and an append-only log plus content-addressed store on disk reproduces identical state and hashes after reload.
