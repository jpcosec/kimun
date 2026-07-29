---
id: projection
title: Projection
five_wh_one_plus: what
tags:
- system:sldb
- domain:runtime.projections
provenance: desk/drawer/features/feature-sldb-explicit-target-architecture.md
---

# Projection

## Answer

A projection is any derived output or view produced from the canonical AST without becoming the source of truth.

## Supporting points

- Markdown materialization is one projection among others, not the definition of the document.
- Graph exports, search views, editor views, and runtime artifacts belong to this derived layer.
- Projection keeps canonical existence separate from emission and presentation.

## Related atoms

### Supports

- [supports:: [[rust-testing]]]

### Constrains

- [constrains:: [[rust-code-linting]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.
