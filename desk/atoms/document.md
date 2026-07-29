---
id: document
title: Document
five_wh_one_plus: what
tags:
- system:sldb
- layer:document-model
- topic:documents
provenance: desk/drawer/features/feature-canonical-ast-design-current-state.md
---

# Document

## Answer

A document is a first-class canonical unit that owns structured content and participates in projections and lineage.

## Supporting points

- The AST represents documents directly rather than only files or rendered text.
- Document-level identity, provenance, hashing, and projection depend on documents being explicit units.
- CLI and future visual tooling both need inspectable document-level entities.
## Related atoms

### Depends on

- [depends_on:: [[canonical-ast]]]
- [depends_on:: [[canonical-identity]]]
- [depends_on:: [[tree-spine]]]

### Supports

- [supports:: [[node]]]
- [supports:: [[tracked-document-identity]]]
- [supports:: [[projection]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.
