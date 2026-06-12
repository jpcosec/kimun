# Task Board

## Current State Summary

The repo now has one open extraction bug task and one blocked desk boundary task after landing the generic SLDB table marker and cross-store write/model resolution support.

The active SLDB-owned work is to fix the skipped `optrev` block extraction test. The desk boundary cleanup remains blocked by the current dirty `deskops` worktree and by canonical `deskops` task/board/pill models that do not yet match this repo's existing desk documents.

## Active

| ID | Domain | Task | Priority | Depends On | Pills |
|----|--------|------|----------|------------|-------|
| `task-fix-optrev-block-extraction` | `sldb/extraction` | Fix optrev block extraction | `p1` | - | - |
| `task-migrate-desk-context-to-deskops` | `desk/boundary` | Migrate reusable desk context toward deskops | `p2` | blocked on compatible canonical `deskops` models | `desk/pills/pill-001-sldb-vs-deskops-boundary.md` |

## Recently Closed

- `task-sldb-cross-store-write-and-model-resolution`: linked store aliases now work as document destinations, and linked store namespaces can provide models for generic document writes.
- `task-sldb-table-marker`: SLDB templates now support reversible `rev,table[...]` markers for Markdown table roundtrips.

## Working Rules

1. Each active task should be completable from the task file, listed pills, and referenced repo surfaces.
2. If the task is ambiguous, improve the task or create a pill before implementing.
3. Prefer thin generic behavior in SLDB and push workflow-specific behavior toward `deskops`.
4. Close the loop with validation commands, store rebuilds when tracked docs change, and explicit board updates.
