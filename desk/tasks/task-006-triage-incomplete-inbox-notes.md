---
id: task-006
domain: desk/inbox/ux
status: done
priority: p2
depends_on: []
created: "2026-05-26"
---

# Triage incomplete repo-targeted inbox notes

## Objective

Close the loop on malformed or content-free inbox notes so the desk backlog does not accumulate entries that cannot be acted on.

## Reference

- `src/sldb/cli/commands/inbox.py`
- `desk/STANDARDS.md`
- `desk/SPEC.md`

## What To Fix

The inbox currently contains at least one open note with no actionable content beyond its title.

That leaves an ambiguous backlog item that should either be normalized into a real task or rejected earlier by the inbox capture flow.

## How To Do It

Define the minimum content standard for repo-targeted inbox entries and decide whether empty notes should be blocked, flagged, or auto-marked for manual triage.

If the CLI currently permits content-free notes, tighten that behavior or improve the follow-up workflow so they do not remain open indefinitely.

## Validation

- inspect the current inbox capture behavior for empty or near-empty messages
- define the expected outcome for malformed notes
- add or update docs or command behavior to reflect that rule

## Done When

The repo has an explicit rule for incomplete inbox notes and no longer relies on silent open placeholders as backlog items.
