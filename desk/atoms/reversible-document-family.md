---
id: reversible-document-family
title: Reversible document family
five_wh_one_plus: when
tags:
- system:sldb
- layer:document-model
- topic:document-families
provenance: desk/drawer/features/feature-canonical-ast-design-current-state.md
---

# Reversible document family

## Answer

A reversible document family is a family of structured documents for which SLDB can guarantee the strong AST-render-AST-render cycle.

## Supporting points

- Its rendered outputs must remain equal across the cycle.
- These are the main target families for strong v1 behavior recreation.
- They depend on explicit typed structure.

## Related atoms

### Depends on

- [depends_on:: [[canonical-ast]]]
- [depends_on:: [[markdown-emitter]]]
- [depends_on:: [[markdown-importer]]]

### Supports

- [supports:: [[locator-strategy]]]
- [supports:: [[ast-anchor]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.
