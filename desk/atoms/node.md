---
id: node
title: Node
five_wh_one_plus: what
tags:
- system:sldb
- layer:document-model
- topic:nodes
provenance: desk/drawer/features/feature-canonical-ast-design-current-state.md
---

# Node

## Answer

A node is the primary owned structural unit inside the canonical AST.

## Supporting points

- Nodes carry kind/subtype, payload, attributes, and source/origin span information.
- Nodes are the units addressed by links, anchors, provenance, and hashes.
- Nodes sit on the tree spine while also participating in graph-capable side relations.
## Related atoms

### Depends on

- [depends_on:: [[canonical-ast]]]
- [depends_on:: [[canonical-identity]]]
- [depends_on:: [[tree-spine]]]

### Supports

- [supports:: [[field-binding]]]
- [supports:: [[link-reference]]]
- [supports:: [[external-anchor]]]
- [supports:: [[node-hash]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.
