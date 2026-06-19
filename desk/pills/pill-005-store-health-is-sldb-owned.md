---
id: pill-005-store-health-is-sldb-owned
tags:
- system:sldb
- system:deskops
- topic:store
- topic:health
---

# Store health is owned by SLDB

## What

SLDB, not deskops, should define and expose what counts as a valid `.sldb/` store.

## Why

Only SLDB knows the internal store contracts. Duplicating validation logic downstream would create drift and inconsistent recovery behavior.

## When

Apply this pill when adding health checks, integrity commands, diagnostics, or recovery-related CLI/API surfaces.

## Where

- `src/sldb/store/`
- `src/sldb/cli/commands/stores.py`
- downstream deskops health surfaces that call into SLDB

## How

Add diagnostics and programmatic checks in SLDB, then let deskops consume those outputs rather than re-implementing the logic.

## How Not

Do not make deskops parse raw store internals or duplicate validity rules that belong in SLDB.
