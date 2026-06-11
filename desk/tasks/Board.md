# Task Board

## Current State Summary

The repo now has open desk work promoted from issue notes into first-class task documents.

The active work now splits between an SLDB template capability and a desk boundary cleanup that can consume SLDB's generic cross-store write/model resolution support.

## Active

| ID | Domain | Task | Priority | Depends On | Pills |
|----|--------|------|----------|------------|-------|
| `task-sldb-table-marker` | `sldb/templates` | Add reversible table template marker | `p1` | - | `desk/pills/pill-003-template-marker-roundtrip-contract.md` |
| `task-migrate-desk-context-to-deskops` | `desk/boundary` | Migrate reusable desk context toward deskops | `p2` | - | `desk/pills/pill-001-sldb-vs-deskops-boundary.md` |

## Recently Closed

- `task-sldb-cross-store-write-and-model-resolution`: linked store aliases now work as document destinations, and linked store namespaces can provide models for generic document writes.

## Working Rules

1. Each active task should be completable from the task file, listed pills, and referenced repo surfaces.
2. If the task is ambiguous, improve the task or create a pill before implementing.
3. Prefer thin generic behavior in SLDB and push workflow-specific behavior toward `deskops`.
4. Close the loop with validation commands, store rebuilds when tracked docs change, and explicit board updates.
