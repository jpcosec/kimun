# Pattern: keep SLDB CLI output tied to document contracts

ID: pill-001

## What

This pill gives a fresh subagent the minimum context needed to execute an SLDB CLI/document task without loading the whole semantic document system.

## Why

SLDB owns semantic Markdown documents. Crashes around FAQ lookup, inbox serialization, stores, fields, and help text usually come from mismatches between document shape and command presentation.

## When

Apply to tasks that touch `faq`, `inbox`, stores, fields, docs, help/version output, query warnings, or template-marker detection.

## Where

Primary owner files:

- `tools/sldb/src/sldb/cli/parser.py`
- `tools/sldb/src/sldb/cli/main.py`
- `tools/sldb/src/sldb/cli/commands/`
- `tools/sldb/desk/models.py`
- `tools/sldb/tests/` if present for the affected command

## Required Reads

- Read the assigned task file.
- Read this pill.
- Read the exact command handler named in task `## Location`.
- Read model definitions only when the task mentions validation, fields, stores, docs, or serialization.

## Execution Boundary

Keep document models authoritative. CLI handlers may validate, serialize, and present model data, but should not invent alternate document semantics. Prefer specific error handling for user input failures and let real programming errors stay debuggable.

## How

Validate from outside the package root where path handling matters. Confirm JSON output is serializable, warnings are signal-rich, and help text exposes the real command surface.

## Validation Contract

Check path handling from another working directory, empty-string arguments, JSON serialization, help topic discoverability, and non-zero exits for invalid user inputs where applicable.

## How Not

Do not paper over document parsing problems with broad exception handlers. Do not make output prettier by breaking machine-readable formats. Do not make package-root-relative paths depend on the caller's current directory.

## Drift Signals

- The executor changes document model semantics to fix presentation.
- The executor handles all exceptions as user errors.
- The executor validates only from the package root when the task concerns path handling.
- The executor makes JSON output human-friendly but no longer machine-parseable.

## Tags

- system:sldb
- topic:cli
- topic:documents
