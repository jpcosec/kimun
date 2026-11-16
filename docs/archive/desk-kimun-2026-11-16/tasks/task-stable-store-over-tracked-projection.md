---
id: task-stable-store-over-tracked-projection
status: active
summary: 'Merkle per path over the tracked projection; the build plan leaves the CAS and drops the mmap-by-id index and the three redundant edge files.'
tags:
- workspace:desk
- artifact:task
- source:audit
history: []
references:
- docs/v2/12-ecosistema-y-pendientes.md
- docs/v2/14-juego-del-lenguaje.md
- docs/v2/drafts/sesion-2026-10-08-09.md
depends_on: []
---

# Stable store over the tracked projection

## Rationale

_Explain why this task exists or the business driver behind it._

`docs/v2/12 §1` (Kernel y store, punto 1) records that format 4 spends 907 KB + 708 KB + 489 KB of
2.51 MB on an mmap index ordered by internal ids and three redundant edge files per document.

## Goal

_Describe the concrete result this task must produce._

A stable store over what is tracked: a Merkle root **per path** over each document's tracked
projection, so a local reconciliation (hash vs hash) decides what is dirty; the build plan leaves
the CAS; no mmap index ordered by internal ids and no three redundant edges per document.

## Scope

_State what is in scope and what is out of scope._

`docs/v2/14 §19–§20`; decisions 43–44. The document tree and plan live in `<store>/cache/`; the
store keeps anchor + class + hashes. Out of scope: packfiles (own task) and name index/GC
(reshaped by this).

## References

- `docs/v2/12-ecosistema-y-pendientes.md` §1, Kernel y store, punto 1
- `docs/v2/14-juego-del-lenguaje.md` §19–§20
- `docs/v2/drafts/sesion-2026-10-08-09.md` §9.1

## Blocked by

Q5 of `docs/v2/drafts/preguntas-abiertas.md` (recommended (a): store stable first).

## Done When

_Name the observable condition that makes the task complete._

`stores verify`/reconciliation works per path over the tracked projection, and `store-size` shows
the index and redundant edges gone.
