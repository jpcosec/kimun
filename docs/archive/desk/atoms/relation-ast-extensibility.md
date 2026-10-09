---
layer: core
id: relation-ast-extensibility
title: RelationAST and extensibility
five_wh_one_plus: how
tags:
- system:sldb
- domain:implementation-clojure-core
provenance: raw/source/architecture/target-system-overview.md
---

# RelationAST and extensibility

Extensibility resides entirely in the Clojure environment. The `RelationASTs` payloads serve as the foundational structure that is materialized and indexed to generate semantic graphs, the store, and tags. This allows extending relationships with strongly typed data structures within Clojure.

## Related atoms

### Specializes
- [specializes:: [[relation-ast]]]

### Supports
- [supports:: [[clojure-core]]]
