---
id: task-markdown-v2-anchoring-and-generation-models
status: active
summary: 'Spec 16: separate the anchoring model from the generation model; the v1 template conflated them.'
tags:
- workspace:desk
- artifact:task
- source:audit
history: []
references:
- docs/v2/12-ecosistema-y-pendientes.md
- docs/v2/14-juego-del-lenguaje.md
- docs/v2/07-modelos-como-nodos.md
depends_on: []
---

# Markdown v2 models: anchoring vs generation (spec 16)

## Rationale

_Explain why this task exists or the business driver behind it._

`docs/v2/12 §1` (Funciones, Modelos): the desk descriptors are templates that mix extraction and
render (`docs/v2/14 §18.7`; `docs/v2/07`).

## Goal

_Describe the concrete result this task must produce._

Two distinct models: the anchoring model (what is extracted and how it binds) and the generation
model (what is materialized), with an EDN selector over tree-sitter, class inheritance, and v1
features without v1 bugs.

## Scope

_State what is in scope and what is out of scope._

`docs/v2/12 §1` Funciones/Modelos; decisions 41–42; `docs/v2/14 §18`. The desk atoms become M/G
nodes of the graph (`AtomDoc` was a patch). The owner removed every `desk/models/*.edn` on
2026-10-09 ("los podemos generar después"), so spec 16 defines what replaces them.

## References

- `docs/v2/12-ecosistema-y-pendientes.md` §1, Funciones: Modelos (spec 16)
- `docs/v2/14-juego-del-lenguaje.md` §18
- `docs/v2/07-modelos-como-nodos.md`

## Blocked by

Q4 of `docs/v2/drafts/preguntas-abiertas.md` (generated documents: rebuild from the `.md` or move to
the graph).

## Done When

_Name the observable condition that makes the task complete._

Anchoring and generation are separate, versioned models and the desk is re-generated from them.
