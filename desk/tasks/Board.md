# Task Board

## Current State Summary

The repo now has a clearer onboarding surface, a CLI FAQ flow, inbox routing anchored to the active project, and architecture diagrams that distinguish SLDB infrastructure from the proposed `deskops` workflow layer.

The next meaningful desk work is to consolidate open repo work into first-class tasks, close the gap between ad hoc notes and executable backlog items, and keep generic product guidance separate from repo-local delivery tracking.

## Active

| Task | Domain | Focus |
|------|--------|-------|
| task-004 | models/docs/pandoc | Validate SLDB support for Pandoc fenced CV documents |
| task-005 | integration/store/workflow | Define validation and workflow guidance for Postulator integration |
| task-006 | desk/inbox/ux | Triage incomplete repo-targeted inbox notes |
| task-002 | opsys/docs/ux | Export the locally drafted reporting and UX CLI testing guides into `opsys` |

The active desk slice is now centered on downstream integration questions, document-shape validation, inbox hygiene, and one cross-repo handoff task that moves generic operational guides into `opsys`.

## Recently Closed

| Task | Domain | Outcome |
|------|--------|---------|
| task-003 | cli/store/ux | Added `models list`, improved missing-store guidance around local versus global scope, and updated onboarding docs/help |
| task-001 | cli/docs/desk | Added FAQ, explore, inbox, spec2viz onboarding diagrams, and a grounded `deskops` boundary proposal |

## Working Rules

1. Each active task should be completable from the task file, listed pills, and referenced repo surfaces.
2. If the task is ambiguous, improve the task or create a pill before implementing.
3. Prefer thin generic behavior in SLDB and push workflow-specific behavior toward `deskops`.
4. Close the loop with validation commands, store rebuilds when tracked docs change, and explicit board updates.
