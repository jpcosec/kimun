# Task Board

## Current State Summary

The repo now has a clearer onboarding surface, a CLI FAQ flow, inbox routing anchored to the active project, and architecture diagrams that distinguish SLDB infrastructure from the proposed `deskops` workflow layer.

The next meaningful desk work is to consolidate open repo work into first-class tasks, close the gap between ad hoc notes and executable backlog items, and keep generic product guidance separate from repo-local delivery tracking.

## Active

No active tasks are routed on this board right now.

## Done

| ID | Domain | Task | Resolved At |
|----|--------|------|-------------|
| 002 | sldb/cli | Fix docs create treating inline JSON as path | 2026-06-07 |
| 001 | sldb/testing | Fix stale pycache breaking store lock test | 2026-06-06 |

## Working Rules

1. Each active task should be completable from the task file, listed pills, and referenced repo surfaces.
2. If the task is ambiguous, improve the task or create a pill before implementing.
3. Prefer thin generic behavior in SLDB and push workflow-specific behavior toward `deskops`.
4. Close the loop with validation commands, store rebuilds when tracked docs change, and explicit board updates.
