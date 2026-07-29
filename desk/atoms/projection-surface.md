---
layer: shared
id: projection-surface
title: Projection surface
five_wh_one_plus: what
tags:
- system:sldb
- domain:runtime.projections
provenance: core_README.md
---

# Projection surface

## Answer

A projection surface is any user-facing or language-facing representation derived from Rust-owned canonical kernel state.

## Supporting points

- Markdown, Lisp forms, JSON, HTML, CLI payloads, semantic views, and structural AST views are all projection surfaces.
- Projection surfaces may be used for input, output, or both without becoming authority.
- Translation between surfaces must preserve Rust-owned meaning where the contract requires it.

## Related atoms

### Depends on

- [depends_on:: [[rust-core]]]
- [depends_on:: [[projection]]]

### Supports

- [supports:: [[markdown-text-surface]]]
- [supports:: [[lisp-schema-language]]]
- [supports:: [[lisp-macro-language]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.
