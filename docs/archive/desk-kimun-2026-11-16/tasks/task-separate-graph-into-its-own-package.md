---
id: task-separate-graph-into-its-own-package
status: active
summary: 'Move the graph (models, semantic edges, graph, G layer) into kimun.graph.* with a bb lint ring rule.'
tags:
- workspace:desk
- artifact:task
- source:audit
history: []
references:
- docs/v2/12-ecosistema-y-pendientes.md
- docs/v2/14-juego-del-lenguaje.md
depends_on: []
---

# Separate the graph into its own package

## Rationale

_Explain why this task exists or the business driver behind it._

`docs/v2/12 §1` (Kernel y store, punto 4) and `docs/v2/14 §19`: the graph is not the same thing as
the store, and today they share a namespace root.

## Goal

_Describe the concrete result this task must produce._

The graph (models, semantic edges, graph, G layer) lives in `kimun.graph.*` as a separate package,
with a dependency-ring rule in `bb lint` proving the base does not import it.

## Scope

_State what is in scope and what is out of scope._

`docs/v2/12 §1` punto 4; decision 43 / `docs/v2/14 §19`. Out of scope: the G semantics themselves
(own task, blocked by the owner).

## References

- `docs/v2/12-ecosistema-y-pendientes.md` §1, Kernel y store, punto 4
- `docs/v2/14-juego-del-lenguaje.md` §19

## Blocked by

Q5(b) of `docs/v2/drafts/preguntas-abiertas.md` sequences this after (or instead of) the stable
store.

## Done When

_Name the observable condition that makes the task complete._

`bb lint` enforces the ring and `kimun.*` (base) compiles without `kimun.graph.*`.
