---
id: hook-runtime
title: Hook runtime
five_wh_one_plus: how
tags:
- system:sldb
- layer:runtime
- topic:hooks
provenance: desk/drawer/features/feature-canonical-ast-design-current-state.md
---

# Hook runtime

## Answer

The hook runtime is the execution-side component that interprets declarative hook bindings and manages their invocation policy and result contracts.

## Supporting points

- Hook declarations live in canonical structure, but execution belongs here.
- It should enforce input, output, and verification contracts.
- It should remain separable from the core node model.

## Related atoms

### Depends on

- [depends_on:: [[hook-binding]]]
- [depends_on:: [[projection]]]

### Supports

- [supports:: [[testing]]]
- [supports:: [[visual-ux-surface]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.
