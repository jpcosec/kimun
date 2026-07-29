---
id: rust-core
title: Rust core
five_wh_one_plus: what
tags:
- system:sldb
- domain:implementation.rust-core
provenance: desk/drawer/features/feature-sldb-explicit-target-architecture.md
---

# Rust core

## Answer

The Rust core is the implementation substrate for the canonical AST, structural invariants, hashing, and graph-capable low-level operations.

## Supporting points

- Rust carries the strongest invariants of the refactor.
- It should own the canonical structure and adjacent infrastructure, not only performance-sensitive helpers.
- It is the extensible base for future projections and adapters.

## Related atoms

### Depends on

- [depends_on:: [[canonical-ast]]]
- [depends_on:: [[rust-patterns]]]

### Supports

- [supports:: [[node]]]
- [supports:: [[tree-spine]]]
- [supports:: [[field-binding]]]
- [supports:: [[node-hash]]]
- [supports:: [[projection]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.

### Supports
- [supports:: [[merkle-index]]]
- [supports:: [[dependency-index]]]
