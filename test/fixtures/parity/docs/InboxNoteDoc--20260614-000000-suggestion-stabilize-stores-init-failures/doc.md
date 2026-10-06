---
kind: suggestion
sender_project: deskops
created_at: 2026-06-14T00:00:00
status: open
---

# Stabilize `stores init` failure handling

`deskops init` shells out to `sldb stores init` for local `.sldb/` setup. When the subprocess fails, deskops can't distinguish between "store already exists" (no-op), "permission denied" (environment), and "corrupt target path" (precondition).

## Scope

- `stores init` should be idempotent: if `.sldb/` exists, report it clearly and exit 0
- Partial init failures (created dirs but no index) should roll back or leave a detectable partial state
- Exit codes should distinguish: already-exists (0), created (0), partial-failure (1+), precondition-error (2+)
- Stderr output for failures should be actionable, not raw tracebacks

## Motivation

Deskops needs to report safe, clear output to first-time users. Currently a bare `sldb stores init` failure propagates as a cryptic subprocess error.

## Existing pills

- `desk/pills/pill-001-sldb-vs-deskops-boundary.md` — SLDB owns store contracts, deskops owns workflow

## Done When

- `sldb stores init` run twice in the same directory: first exits 0 creates store, second exits 0 says "already exists"
- Forced init failure (readonly parent dir) exits non-zero with actionable stderr and no partial `.sldb/` left behind
- Deskops `init` can read the exit code and report the right user message
