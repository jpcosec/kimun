---
id: query-engine
title: Query engine
five_wh_one_plus: what
tags:
- system:sldb
- layer:runtime
- topic:queries
provenance: /home/jp/proyectos/hum-ecosystem/tools/sldb/README.md
---

# Query engine

## Answer

The query engine executes structured retrieval over physical, semantic, section, field, and graph-oriented views derived from canonical content.

## Supporting points

- It unifies retrieval behavior across multiple derived views.
- It should sit over indexes and projections rather than own document truth.
- It is a core runtime service for navigation and discovery.

## Related atoms

### Depends on

- [depends_on:: [[search-projection]]]
- [depends_on:: [[section-index]]]
- [depends_on:: [[field-index]]]

### Supports

- [supports:: [[semantic-vs-physical-search]]]
- [supports:: [[how-to-get-data-out-of-sldb]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.
