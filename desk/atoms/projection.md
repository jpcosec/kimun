---
layer: core
id: projection
title: Projection
five_wh_one_plus: what
tags:
- system:sldb
- domain:runtime.projections
provenance: reasoning.md
---

# Projection

## Answer

A projection is a deterministic derived view computed from a canonical revision under an explicit projection specification and engine version.

## Supporting points

- Structural ASTs, semantic graphs, renders, embeddings, logic views, Markdown surfaces, and Lisp forms are all projections.
- Projections never become the primary authority for the document; they are cached or persisted as derived artifacts.
- A projection result must remain traceable to `revision + projection spec + engine version + source hash`.
- Projection invalidation is allowed and expected; rebuildability is a core property of the architecture.

## Related atoms

### Depends on

- [depends_on:: [[projection-spec]]]
- [depends_on:: [[revision]]]
- [depends_on:: [[graph-store]]]

### Supports

- [supports:: [[projection-surface]]]
- [supports:: [[document-materializer]]]
- [supports:: [[semantic-exporter]]]
- [supports:: [[search-projection]]]
- [supports:: [[embeddings]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.
