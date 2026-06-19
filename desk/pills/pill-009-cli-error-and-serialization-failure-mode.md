---
id: pill-009-cli-error-and-serialization-failure-mode
tags:
- system:sldb
- topic:failure-mode
- topic:serialization
- language:python
---

# SLDB CLI errors must stay actionable and machine-safe

## What

User-input failures should become actionable diagnostics while machine-readable outputs and document semantics remain intact.

## Why

Raw file errors, datetime serialization crashes, confusing empty-input messages, and location-dependent output create avoidable operator confusion and downstream automation drift.

## When

Apply this pill when changing FAQ, inbox JSON, extract/render/validate, docs, fields, stores, query, help, version, or empty-input validation behavior.

## Where

- `src/sldb/cli/main.py`
- `src/sldb/cli/parser.py`
- `src/sldb/cli/commands/`
- serialization-sensitive tests

## How

Normalize output only at the presentation boundary, verify exit codes and stderr, and test both human-facing and machine-facing command behavior where relevant.

## How Not

Do not catch every exception as a user error, degrade stored data just to satisfy JSON output, or let help text and parser behavior drift apart.
