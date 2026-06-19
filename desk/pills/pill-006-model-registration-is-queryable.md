---
id: pill-006-model-registration-is-queryable
tags:
- system:sldb
- system:deskops
- topic:models
- topic:query
---

# Model registration must be queryable

## What

Registered model state should be inspectable through explicit SLDB commands and APIs rather than by forcing downstream tools to read store files directly.

## Why

Deskops and operators need to detect missing, stale, or incompatible model registrations without coupling to raw store layout.

## When

Apply this pill when changing model registration, list/check surfaces, or recovery tooling.

## Where

- `src/sldb/cli/commands/models.py`
- store model indexes
- deskops recovery and health integrations

## How

Expose model registration state through supported CLI/API surfaces and preserve enough metadata for downstream consumers to reason about compatibility.

## How Not

Do not require callers to scrape YAML indexes directly or infer registration state from incidental filesystem details.
