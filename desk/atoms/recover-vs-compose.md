---
id: recover-vs-compose
title: Recover vs compose
five_wh_one_plus: what
tags:
- system:sldb
- layer:cli
- topic:composition
provenance: /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/faq.md
---

# Recover vs compose

## Answer

`recover` answers what links or transclusions resolve to, while `compose` produces the document view after transclusions are expanded; these are link-oriented behaviors, not payload extraction.

## Supporting points

- The durable distinction is behavioral, not tied to one exact CLI nesting form.
- Recover is about resolution; compose is about rendered composition.
- Users should keep the same mental model after the refactor.

## Related atoms

### Supports

- [supports:: [[testing]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.
