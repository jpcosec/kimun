---
kind: suggestion
sender_project: deskops
created_at: 2026-06-14T00:00:01
status: open
---

# Stabilize `models add` error output

`deskops init` calls `sldb models add` to register deskops models in the global store. When model registration fails (invalid model ref, missing dependencies, store path wrong), the error output is unpredictable.

## Scope

- Non-existent model reference should produce a clear error: `Model 'FooBar' not found. Available models: ...`
- Store-not-found should say which path was tried
- Duplicate registration should be a no-op (exit 0) with an "already registered" info line
- Exit codes: registered (0), already-exists (0), invalid-ref (1+), store-error (2+)

## Motivation

First-time `deskops init` users see raw Python tracebacks when model registration fails. SLDB should own clean CLI error output for its own commands.

## Existing pills

- `desk/pills/pill-001-sldb-vs-deskops-boundary.md`

## Done When

- `sldb models add nonexistent.Model --store <path>` says "not found" and lists available models, exits non-zero
- `sldb models add deskops.models.TaskDoc --store <path>` twice: second exits 0 with info message
- No raw traceback reaches stderr in any of the above
