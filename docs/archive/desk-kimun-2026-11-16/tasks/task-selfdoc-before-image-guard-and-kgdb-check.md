---
id: task-selfdoc-before-image-guard-and-kgdb-check
status: active
summary: 'Correctitud selfdoc: missing before-image write guard (state corpus) and the kgdb graph check (state abierta).'
tags:
- workspace:desk
- artifact:task
- source:audit
history: []
references:
- docs/v2/12-ecosistema-y-pendientes.md
depends_on: []
---

# Selfdoc write guard and kgdb graph check

## Rationale

_Explain why this task exists or the business driver behind it._

`docs/v2/12 §1` (Correctitud, punto 1): selfdoc lacks the before-image guard on write and the kgdb
graph check.

## Goal

_Describe the concrete result this task must produce._

Writing a self-documenting artifact keeps a before image (state `corpus`), and the graph check
against kgdb exists (state `abierta`).

## Scope

_State what is in scope and what is out of scope._

`docs/v2/12 §1` Correctitud, punto 1. Two gaps: the before-image guard when writing (state
`corpus`) and the kgdb check of the graph (state `abierta`).

## References

- `docs/v2/12-ecosistema-y-pendientes.md` §1, Correctitud, punto 1

## Done When

_Name the observable condition that makes the task complete._

A write that would change corpus-backed text is refused by the before-image guard, and the kgdb
graph check runs green (state no longer `abierta`).
