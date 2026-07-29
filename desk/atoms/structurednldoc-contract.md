---
layer: shared
id: structurednldoc-contract
title: StructuredNLDoc contract
five_wh_one_plus: what
tags:
- system:sldb
- domain:model.documents
provenance: /home/jp/proyectos/hum-ecosystem/tools/sldb/docs/faq.md
---

# StructuredNLDoc contract

## Answer

A `StructuredNLDoc` defines the Markdown template, typed Pydantic fields, field descriptions, and optional semantics that make Markdown-to-payload-to-Markdown workflows possible.

## Supporting points

- This is the central user-facing document-model contract in v1.
- Typed fields and template structure are both part of the public behavior.
- The refactor generalizes this contract rather than discarding it.

## Related atoms

### Supports

- [supports:: [[python-patterns]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.
