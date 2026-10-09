---
id: task-model-less-documents-generic-class
status: active
summary: 'Documents ingested without a model persist a tree; register them under the profile''s generic class.'
tags:
- workspace:desk
- artifact:task
- source:audit
history: []
references:
- docs/v2/12-ecosistema-y-pendientes.md
- docs/v2/10-escala.md
depends_on: []
---

# Documents without a model

## Rationale

_Explain why this task exists or the business driver behind it._

`docs/v2/12 §1` (Kernel y store, punto 3): `docs ingest FILE` accepts a file with no model, yet the
document's tree still has to persist.

## Goal

_Describe the concrete result this task must produce._

Model-less documents are registered under the generic class of the Markdown profile instead of
being invisible or rejected.

## Scope

_State what is in scope and what is out of scope._

`docs/v2/12 §1` punto 3; the include format (`docs/v2/13`) also requires `:model` per document, so a
model-less declaration is not possible today (the desk include is empty for this reason). Decide the
generic class and how `docs ingest` and `include` name it.

## References

- `docs/v2/12-ecosistema-y-pendientes.md` §1, Kernel y store, punto 3
- `docs/v2/13-include.md` (`:documents` requires `:model`)

## Blocked by

Related to Q4 of `docs/v2/drafts/preguntas-abiertas.md` (generated documents): whether their data is
rebuilt from the `.md` with its anchoring model or already lives in the graph.

## Done When

_Name the observable condition that makes the task complete._

A document ingested without a model is listed and verified like any other, under a documented
generic class.
