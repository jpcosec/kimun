---
layer: shared
id: hash-field
title: Hash field
five_wh_one_plus: what
tags:
- system:sldb
- domain:store-hashing
provenance: raw/source/architecture/target-system-overview.md
---

# Hash field

## Answer

A hash field is the node field that stores the hash value associated with a canonical unit for integrity and change detection.

## Supporting points

- Hash is modeled as node data, not as a separate semantic node.
- It should be computed by dedicated Clojure hashing logic.
- It supports integrity and anchoring workflows.

## Related atoms

### Depends on

- [depends_on:: [[atom-canonical-content-and-node-hashing]]]
- [depends_on:: [[clojure-core]]]

### Supports

- [supports:: [[source-document-hash]]]
- [supports:: [[merkle-index]]]
- [supports:: [[store-integrity-checks]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.
