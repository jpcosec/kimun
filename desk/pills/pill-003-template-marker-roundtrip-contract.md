---
id: pill-003-template-marker-roundtrip-contract
tags:
- system:sldb
- topic:templates
- topic:roundtrip
- language:python
---

# Keep template markers reversible at the document boundary

## What

Template marker features should preserve the reversible SLDB document contract unless a task explicitly defines a lossy representation.

## Why

Rendered values that cannot extract back into the same structured payload break the core promise that structured Markdown remains a safe source artifact.

## When

Apply this pill when changing marker families, render/extract behavior, standalone markers, or template invariant logic.

## Where

- `src/sldb/core/template_extractor.py`
- `src/sldb/core/data_extractor.py`
- text/list/yaml handlers
- marker roundtrip tests

## How

Treat marker changes as bidirectional contract changes. Validate render and extract together and add focused tests for any new marker behavior.

## How Not

Do not make rendering look nicer by silently weakening extraction fidelity or by introducing marker semantics that cannot roundtrip safely.
