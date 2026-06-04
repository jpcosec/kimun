# Task Board

## Current State Summary

The repo now has a clearer onboarding surface, a CLI FAQ flow, inbox routing anchored to the active project, and architecture diagrams that distinguish SLDB infrastructure from the proposed `deskops` workflow layer.

The next meaningful desk work is to consolidate open repo work into first-class tasks, close the gap between ad hoc notes and executable backlog items, and keep generic product guidance separate from repo-local delivery tracking.

## Active

| ID | Domain | Task | Priority | Depends On |
|----|--------|------|----------|------------|
| 001 | contract | Define KGDB semantic export payload | p0 | none |
| 002 | cli | Implement semantic export command | p1 | 001 |
| 003 | docs | Document semantic export boundary | p1 | 002 |

## Ready For Closeout

| ID | Domain | Task | Evidence |
|----|--------|------|----------|
| 001 | contract | Define KGDB semantic export payload | schema validation passed; focused tests 20 passed |

## Working Rules

1. Each active task should be completable from the task file, listed pills, and referenced repo surfaces.
2. If the task is ambiguous, improve the task or create a pill before implementing.
3. Prefer thin generic behavior in SLDB and push workflow-specific behavior toward `deskops`.
4. Close the loop with validation commands, store rebuilds when tracked docs change, and explicit board updates.
