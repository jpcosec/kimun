---
id: task-structural-and-semantic-ids
status: active
summary: 'Structural ids for S, semantic ids for M/G; the hash only verifies (docs/v2/14 §21, decision 46).'
tags:
- workspace:desk
- artifact:task
- source:audit
history: []
references:
- docs/v2/12-ecosistema-y-pendientes.md
- docs/v2/14-juego-del-lenguaje.md
- docs/v2/drafts/ids-desde-matrix.md
depends_on: []
---

# Structural and semantic ids

## Rationale

_Explain why this task exists or the business driver behind it._

`docs/v2/12 §1` (Kernel y store, punto 2): today a node's id is the hash of its canonical content,
but `docs/v2/14 §21` separates identity from hash.

## Goal

_Describe the concrete result this task must produce._

Ids are structural for S (place in the environment plus projection) and semantic for M/G (context
path plus sign); the hash detects mutation and no longer identifies.

## Scope

_State what is in scope and what is out of scope._

`docs/v2/14 §21`; decision 46 (pending). Before touching code, answer the two questions of
`docs/v2/drafts/ids-desde-matrix.md`. Paragraphs that are identical become two S nodes with the
same hash; links go by id, not hash; reconciliation Merkle over the structural tree (S) and the
context tree (M/G).

## References

- `docs/v2/12-ecosistema-y-pendientes.md` §1, Kernel y store, punto 2
- `docs/v2/14-juego-del-lenguaje.md` §21
- `docs/v2/drafts/ids-desde-matrix.md`

## Blocked by

Q1 (confirm `14 §21` now or after the stable store), Q2 (structural id when the structure changes)
and Q3 (semantic id when a symbol is curated into another context) of
`docs/v2/drafts/preguntas-abiertas.md`. No default is applied to Q1–Q3.

## Done When

_Name the observable condition that makes the task complete._

Nodes and edges carry structural/semantic ids and hash verification is a separate, derived check.
