---
id: pill-004-store-init-idempotency
tags:
- system:sldb
- system:deskops
- topic:store
- topic:idempotency
---

# `stores init` must be idempotent and fail safely

## What

`sldb stores init` must behave safely when called repeatedly and must fail without leaving a misleading partial store behind.

## Why

`deskops init` and direct users both depend on predictable bootstrap behavior. Ambiguous init failures make higher-level workflow tooling unreliable.

## When

Apply this pill when touching `stores init`, local bootstrap flows, or error handling around store creation.

## Where

- `src/sldb/cli/commands/stores.py`
- store creation helpers
- deskops bootstrap integration points

## How

Treat existing valid stores as success, emit actionable failures for real precondition problems, and preserve a detectable clean state when creation fails.

## How Not

Do not leave half-created `.sldb/` trees behind, and do not return raw traceback-style output for common bootstrap failures.
