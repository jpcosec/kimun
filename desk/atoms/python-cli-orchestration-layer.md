---
id: python-cli-orchestration-layer
title: Python CLI orchestration layer
five_wh_one_plus: how
tags:
- system:sldb
- domain:implementation.python-cli
provenance: desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md
---

# Python CLI orchestration layer

## Answer

The Python CLI orchestration layer is the user-facing shell that preserves SLDB workflow continuity while delegating canonical structural work to the core.

## Supporting points

- It keeps the recognizable SLDB command family.
- It should orchestrate workflows rather than re-own canonical structure.
- It is the migration surface between v1 behavior and the new core.

## Related atoms

### Depends on

- [depends_on:: [[cli-workflow-surface]]]
- [depends_on:: [[python-patterns]]]

### Supports

- [supports:: [[cli-invocation-contract]]]
- [supports:: [[create-vs-track-vs-update]]]
- [supports:: [[recover-vs-compose]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.

### Depends on
- [depends_on:: [[store-infrastructure]]]
- [depends_on:: [[what-a-store-is]]]
