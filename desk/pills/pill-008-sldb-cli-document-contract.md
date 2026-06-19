---
id: pill-008-sldb-cli-document-contract
tags:
- system:sldb
- topic:cli
- topic:documents
- language:python
---

# Keep SLDB CLI output tied to document contracts

## What

CLI handlers should present document-model state faithfully instead of inventing alternate semantics at the presentation layer.

## Why

Crashes around FAQ lookup, inbox serialization, stores, fields, and help text often come from mismatches between document shape and command presentation.

## When

Apply this pill to tasks that touch FAQ, inbox, stores, fields, docs, help/version output, query warnings, or template-marker detection.

## Where

- `src/sldb/cli/parser.py`
- `src/sldb/cli/main.py`
- `src/sldb/cli/commands/`
- relevant tests and document models

## How

Keep document models authoritative. Validate from realistic caller locations when path handling matters, and make JSON/help output reflect the actual contract instead of ad hoc shortcuts.

## How Not

Do not paper over parsing or validation problems with broad exception handlers, and do not make output prettier by breaking machine-readable semantics.
