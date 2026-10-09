---
id: task-packfiles-4kb-blocks
status: active
summary: 'One 4 KB block per small object on disk (also in the cache); branch close/A4 is paused and its format 3 is superseded.'
tags:
- workspace:desk
- artifact:task
- source:audit
history: []
references:
- docs/v2/12-ecosistema-y-pendientes.md
- docs/v2/14-juego-del-lenguaje.md
depends_on:
- task-stable-store-over-tracked-projection
---

# Packfiles: a 4 KB block per small object

## Rationale

_Explain why this task exists or the business driver behind it._

`docs/v2/12 §1` (Kernel y store, punto 5): many small objects waste space and inodes on disk.

## Goal

_Describe the concrete result this task must produce._

Small objects are packed one block of 4 KB per object, on disk and in the cache.

## Scope

_State what is in scope and what is out of scope._

`docs/v2/12 §1` punto 5; partial work in the paused branch `close/A4`, whose "format 3" with
`:names`/GC is **superseded** and must not be merged as-is. Reshape after the stable store.

## References

- `docs/v2/12-ecosistema-y-pendientes.md` §1, Kernel y store, punto 5
- `docs/v2/12-ecosistema-y-pendientes.md` §1, Kernel y store, punto 1 (store stable) and §1.6

## Blocked by

The stable-store task; Q6 of `docs/v2/drafts/preguntas-abiertas.md` keeps `close/A4` as a reference.

## Done When

_Name the observable condition that makes the task complete._

`store-size` shows small objects packed in 4 KB blocks and `stores verify` still passes.
