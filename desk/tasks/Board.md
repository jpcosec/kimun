# Task Board

## Current State Summary

The repo now has a clearer onboarding surface, a CLI FAQ flow, inbox routing anchored to the active project, and architecture diagrams that distinguish SLDB infrastructure from the proposed `deskops` workflow layer.

The next meaningful desk work is to consolidate open repo work into first-class tasks, close the gap between ad hoc notes and executable backlog items, and keep generic product guidance separate from repo-local delivery tracking.

## Active

| Task | Domain | Focus |
|------|--------|-------|
| task-007 | cli/store/ux | Add first-class docs list and stores list discovery surfaces |
| task-008 | cli/store/ux | Fall back to global store for read-only commands from uninitialized locations |

The local SLDB backlog is mostly closed for the current slice, but two follow-up CLI discovery tasks remain: extend the successful `models list` pattern to docs and stores, and make uninitialized-folder behavior softer for read-only commands without weakening local-init rules for writes.

## Recently Closed

| Task | Domain | Outcome |
|------|--------|---------|
| task-002 | opsys/docs/ux | Exported the reporting and UX CLI testing guides into `deskops/docs/` and left forwarding references in `sldb` |
| task-006 | desk/inbox/ux | Rejected title-only placeholder inbox notes and documented the minimum actionable note rule |
| task-005 | integration/store/workflow | Added a minimal Postulator-style integration guide with store, tracking, and ownership recommendations |
| task-004 | models/docs/pandoc | Proved shallow `title + body` support for Pandoc fenced div CV documents and added a runnable example |
| task-003 | cli/store/ux | Added `models list`, improved missing-store guidance around local versus global scope, and updated onboarding docs/help |
| task-001 | cli/docs/desk | Added FAQ, explore, inbox, spec2viz onboarding diagrams, and a grounded `deskops` boundary proposal |

## Working Rules

1. Each active task should be completable from the task file, listed pills, and referenced repo surfaces.
2. If the task is ambiguous, improve the task or create a pill before implementing.
3. Prefer thin generic behavior in SLDB and push workflow-specific behavior toward `deskops`.
4. Close the loop with validation commands, store rebuilds when tracked docs change, and explicit board updates.
