---
id: task-004
domain: models/docs/pandoc
status: done
priority: p1
depends_on: []
created: "2026-05-26"
---

# Validate SLDB support for Pandoc fenced CV documents

## Objective

Determine whether SLDB can model, track, edit, and round-trip CV-style Markdown that uses Pandoc fenced div blocks for structural sections such as jobs and education without forcing a format redesign.

## Reference

- `docs/faq.md`
- `src/sldb/models/structured_doc.py`
- `src/sldb/runtime/validation.py`
- `src/sldb/examples/reference_bundle/`

## What To Fix

There is an open integration question about whether SLDB can preserve fenced-div document structure that an external review UI already parses semantically.

Without a concrete example and validation path, downstream adopters cannot tell whether SLDB fits their current document contracts.

## How To Do It

Create the smallest credible fenced-div CV example and test whether extraction, tracking, editing, and round-trip validation preserve the structural information that matters.

If the answer is yes, document the recommended modelling pattern.

If the answer is no or partial, document the exact limitation and the smallest required compromise.

## Validation

- create or capture a minimal CV sample using Pandoc fenced div blocks
- test extraction and render/round-trip behavior against that sample
- verify whether block attributes and nesting survive the intended workflow
- document the result in repo docs or a focused guidance note

## Done When

The repo contains a concrete yes/no answer on Pandoc fenced-div CV support, backed by a minimal example and an explicit recommended pattern or limitation statement.
