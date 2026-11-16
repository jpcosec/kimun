---
sldb:
  by: agent
  at: '2026-11-16T19:08:20Z'
  authority: generated
  via: save_document_payload
id: task-open-owner-decisions-that-hold-work
status: draft
summary: ''
tags:
- workspace:desk
- artifact:task
current_node: ritual-execution
history: []
references:
- docs/v2/drafts/preguntas-abiertas.md
- runs/subagents/2026-11-16-serve-seguro/REPORT.md
- runs/subagents/2026-11-16-sin-rehacer-git-1/REPORT.md
- runs/subagents/2026-11-16-sin-paridad/REPORT.md
- pods/treesitter/Cargo.toml
depends_on: []
pills: []
files: []
task_type: task.decision
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# Open owner decisions that hold work

## Rationale

_Explain why this task exists or the business driver behind it._

Four questions for the owner (JP) came out of the cuts of 2026-11-16 and have no answer. They are rows GRAMATICA-CLJ, SERVE-4, RAZON and PRUEBAS-ALMACEN of the table in docs/v2/drafts/preguntas-abiertas.md; each row gives the facts, the evidence file and what it blocks.

## Goal

_Describe the concrete result this task must produce._

Each of the four rows of docs/v2/drafts/preguntas-abiertas.md carries the owner's answer, verbatim and dated, at the start of its second cell, prefixed by **Respondida (dueño, YYYY-MM-DD):** as the answered rows of that table already do; a question he declines is prefixed by **Sin decidir (dueño, YYYY-MM-DD):**.

## Scope

_State what is in scope and what is out of scope._

The four questions, as they are asked: (GRAMATICA-CLJ) «El pod de tree-sitter trae las gramáticas de Markdown y Python. Para leer código Clojure —incluido el de kimun— hay que descargar una crate de Rust con esa gramática. ¿La descargo, o leo Clojure con el lector de Babashka, que no sirve para otros lenguajes?» (SERVE-4) «kimun serve acepta en POST /docs/ingest cuerpos de hasta 8 MB y el costo de parsearlos no tiene tope. ¿Pongo un tope de tamaño, y de cuánto?» (RAZON) «Al borrar el journal, el --reason de docs reanchor|untrack|delete|mark|unmark ya no queda registrado en ningún sitio. ¿Dónde debe quedar el porqué: en el mensaje de un commit de git, en una declaración, o en ningún lado?» (PRUEBAS-ALMACEN) «Con la carpeta de paridad se borraron pruebas de promesas del store (durabilidad, merkle, shards, caché, escritores concurrentes). ¿Quieres alguna de vuelta, sabiendo que ese store se va a retirar?» Out of scope: implementing any of the answers; each answer becomes its own task.

## Implementation Path

_Outline the expected implementation route or affected surface._

Whoever coordinates the work on kimun (the agent session the owner, JP, is talking to) asks one question per message in that conversation, with the wording of Scope (it restates the table rows in plain words; the table keeps the evidence), in the order given (the first blocks code nodes; the others block nothing), whenever the owner is present. Write each answer into its row as soon as it is given, one commit per row: 'docs(open-questions): owner answers <ROW-ID>', or 'docs(open-questions): owner leaves <ROW-ID> undecided'.

## Validation

_List the checks required before this task can close._

- Each of the rows SERVE-4, GRAMATICA-CLJ, RAZON, PRUEBAS-ALMACEN starts its second cell with **Respondida (dueño, or **Sin decidir (dueño,
- Each answered row quotes the owner between «»

## Done When

_Name the observable condition that makes the task complete._

The four rows are answered or explicitly declined by the owner, each in its own commit on main.
