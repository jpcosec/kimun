---
id: graph-store
title: Graph store
five_wh_one_plus: what
tags:
- system:sldb
- layer:runtime
- topic:store
provenance: docs/architecture/target-system-overview.md
---

# Graph store

## Answer

The graph store is the local `.sldb/` database that persists canonical AST nodes, canonical edges, anchors, relation payloads, and derived indexes.

## Supporting points

- It replaces the YAML-index-first internal design.
- It keeps the store local and project-scoped.
- It preserves the store role while changing the internal representation.

## Related atoms

### Depends on

- [depends_on:: [[store-infrastructure]]]
- [depends_on:: [[rust-core]]]

### Supports

- [supports:: [[ast-persistence]]]
- [supports:: [[relation-ast]]]
- [supports:: [[derived-index]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.
