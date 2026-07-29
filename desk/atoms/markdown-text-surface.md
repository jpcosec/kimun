---
layer: shell
id: markdown-text-surface
title: Markdown text surface
five_wh_one_plus: what
tags:
- system:sldb
- domain:model.text-structure
provenance: core_README.md
---

# Markdown text surface

## Answer

The Markdown text surface is the human-authored textual projection used for writing, reading, importing, and rendering structured content around the Rust kernel.

## Supporting points

- Markdown is useful as text, not as the system authority.
- Markdown import and render must remain subordinate to Rust-owned canonical meaning.
- Markdown may be round-trippable for reversible families while still remaining a projection surface.

## Related atoms

### Depends on

- [depends_on:: [[rust-core]]]
- [depends_on:: [[projection]]]

### Supports

- [supports:: [[markdown-importer]]]
- [supports:: [[markdown-emitter]]]
- [supports:: [[reversible-document-family]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.
