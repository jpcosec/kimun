---
id: canonical-ast
title: Canonical AST
five_wh_one_plus: what
tags:
- system:sldb
- domain:model.ast-core
provenance: desk/drawer/features/feature-sldb-explicit-target-architecture.md
---

# Canonical AST

## Answer

The canonical AST is the source of truth and the shared substrate for the refactored SLDB system.

## Supporting points

- Markdown is an importer/exporter and editing surface rather than the sovereign representation.
- CLI workflows, future visual UX, importers, emitters, and infrastructure all depend on this shared substrate.
- Store-like behavior is infrastructure over the AST rather than the conceptual center.

## Related atoms

### Supports

- [supports:: [[rust-patterns]]]
- [supports:: [[rust-testing]]]

### Constrains

- [constrains:: [[rust-code-linting]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.

### Supports
- [supports:: [[reversible-document-family]]]
