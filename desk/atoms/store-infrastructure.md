---
id: store-infrastructure
title: Store infrastructure
five_wh_one_plus: what
tags:
- system:sldb
- domain:store.graph
provenance: desk/drawer/features/feature-sldb-explicit-target-architecture.md
---

# Store infrastructure

## Answer

Store infrastructure is the metadata, index, integrity, and runtime workspace built around canonical AST documents rather than the conceptual center of the system.

## Supporting points

- The store survives as infrastructure rather than sovereign model.
- It should hold registrations, tracked state, integrity artifacts, and rebuildable indexes.
- Its purpose is operational support around canonical content.

## Related atoms

### Depends on

- [depends_on:: [[canonical-ast]]]
- [depends_on:: [[projection]]]
- [depends_on:: [[node-hash]]]

### Supports

- [supports:: [[what-a-store-is]]]
- [supports:: [[store-integrity-checks]]]
- [supports:: [[tracked-document-identity]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.
