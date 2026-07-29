---
id: create-vs-track-vs-update
title: Create vs track vs update
five_wh_one_plus: what
tags:
- system:sldb
- layer:cli
- topic:documents
provenance: /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/faq.md
---

# Create vs track vs update

## Answer

`docs create` renders a new document from payload and tracks it, `docs track` validates and registers an existing Markdown file, and `docs update` re-renders a tracked doc from new payload data.

## Supporting points

- These commands have distinct lifecycle roles.
- The user-facing distinction is durable even if internal implementation changes.
- The refactor should preserve the same conceptual document lifecycle.

## Related atoms

### Supports

- [supports:: [[testing]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.

### Depends on
- [depends_on:: [[ast-persistence]]]
