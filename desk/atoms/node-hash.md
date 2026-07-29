---
id: node-hash
title: Node Hash
five_wh_one_plus: what
tags:
- system:sldb
- domain:store.hashing
provenance: desk/drawer/features/feature-canonical-ast-design-current-state.md
---

# Node Hash

## Answer

A node hash is the structural fingerprint used to support integrity, caching, and incremental recomputation.

## Supporting points

- Structural hashing is part of the target architecture from the start rather than a later optimization.
- Node and subtree hashes support Merkle behavior and fine-grained invalidation.
- Hashing makes projections and infrastructure recomputation depend on canonical structure rather than file timestamps alone.
## Related atoms

### Depends on

- [depends_on:: [[node]]]
- [depends_on:: [[provenance-record]]]

### Supports

- [supports:: [[store-integrity-checks]]]
- [supports:: [[projection]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.
