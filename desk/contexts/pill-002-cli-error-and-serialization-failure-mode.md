# Failure Mode: SLDB CLI errors and serialization drift

ID: pill-002

## What

SLDB CLI tasks must convert user-input failures into actionable diagnostics while preserving machine-readable output and document model semantics.

## Why

Current failures include raw file errors, datetime JSON crashes, confusing empty-string messages, and command output that depends on caller location. These failures saturate subagent context because the executor has to rediscover whether the bug is path handling, document parsing, or presentation.

## When

Apply to tasks involving `faq`, `inbox --format json`, `extract`, `render`, `validate`, `docs`, `fields`, `stores`, `query`, help, version, or empty input validation.

## Where

Primary owner surfaces:

- `tools/sldb/src/sldb/cli/main.py`
- `tools/sldb/src/sldb/cli/parser.py`
- `tools/sldb/src/sldb/cli/commands/`
- `tools/sldb/desk/models.py`
- SLDB tests for the affected command

## Required Reads

- Read the task file.
- Read this pill and `pill-001-sldb-cli-document-contract.md`.
- Read the command handler and parser paths named by the task.
- Read model code only when serialization or validation shape is involved.

## Execution Boundary

Fix user-facing CLI behavior without changing document semantics. Serialization fixes should normalize output at the presentation boundary, not mutate stored document meaning unless the task says so.

## Validation Contract

Run commands from both package root and an outside directory when path handling is involved. Verify exit code, stderr, no raw traceback, parseable JSON, and stable help/version output.

## How Not

Do not catch every exception as a user error. Do not stringify everything in storage just to make JSON output pass. Do not make help text list commands that are not implemented.

## Drift Signals

- JSON output works only because model data was degraded.
- Invalid user input exits 0.
- Help output and parser behavior disagree.
- Path defaults depend on the current working directory.

## Tags

- system:sldb
- topic:failure-mode
- topic:serialization
