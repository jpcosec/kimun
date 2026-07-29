---
id: direct-mode-vs-store-backed-mode
title: Direct mode vs store-backed mode
five_wh_one_plus: when
tags:
- system:sldb
- layer:cli
- topic:workflows
provenance: /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/faq.md
---

# Direct mode vs store-backed mode

## Answer

Use direct mode for `extract`, `render`, and `validate` without a store; use store-backed mode when you need registrations, tracked docs, project queries, semantic or section indexes, or integrity checks.

## Supporting points

- The product has two durable workflow modes.
- Not every operation requires `.sldb/`.
- Store-backed workflows add project-level metadata and navigation behavior.

## Related atoms

### Constrains

- [constrains:: [[testing]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.
