---
# unclear | suggestion
kind: suggestion
# e.g., other_repo
sender_project: deskops
# e.g., target_repo
# ISO 8601 timestamp
created_at: '2026-06-14T00:00:02'
# open | closed
status: open
# project identity that acknowledged the note
# ISO 8601 timestamp, set when acknowledged
---

# Add store health-check API

_Describe the incoming message with enough evidence to triage._

## Scope

- `sldb stores check --path <dir>` — validate that `.sldb/` has all required index files, documents index is loadable, models index is loadable, store index is readable
- Exit 0 if healthy, non-zero with specific diagnostics if not
- Optionally expose a Python function: `check_store(path: Path) -> StoreHealth` with structured result
- Clear messages for: missing index, corrupt index, missing document dir, permission error

## Motivation

Deskops health/recovery commands need to detect broken stores without guessing. SLDB is the only authority on what a valid store looks like.

## Existing pills

- `desk/pills/pill-001-sldb-vs-deskops-boundary.md`

## Done When

- `sldb stores check` on a healthy store exits 0 with a summary line
- Missing/corrupt index produces clear diagnostic per broken item
- Python API returns structured health data (not just exit codes)