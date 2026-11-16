FILE:desk/tasks/task-owner-decides-what-new-core-of-sldb-means.md
---
sldb:
  by: agent
  at: '2026-11-16T19:08:12Z'
  authority: generated
  via: save_document_payload
id: task-owner-decides-what-new-core-of-sldb-means
status: draft
summary: ''
tags:
- workspace:desk
- artifact:task
current_node: ritual-execution
history: []
references:
- docs/v2/drafts/sesion-2026-11-15.md
- docs/v2/drafts/diseno-leer.md
- docs/v2/drafts/traspaso-2026-11-17.md
- runs/research/2026-11-15/reemplazo-sldb
depends_on: []
pills: []
files: []
task_type: task.decision
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# Owner decides what new core of sldb means

## Rationale

_Explain why this task exists or the business driver behind it._

The owner (JP) said on 2026-11-16: «esto no está listo hasta que no sea capaz de 1- Ser el nucleo nuevo de sldb y 2- Usar algun proyecto ya avanzado como deskops y ingestarlo.» What 'new core of sldb' means was asked twice that day and not answered. sldb is a Python library (51 000 lines) that its consumers import at 527 sites, with pydantic model classes living in each consumer; kimun is Clojure and offers CLI, HTTP and MCP, no Python library. Without the answer nothing about criterion 1 can be designed.

## Goal

_Describe the concrete result this task must produce._

The owner's answer, in his own words, is appended as a dated section to docs/v2/drafts/sesion-2026-11-15.md (the file that holds his verbatim words), and the paragraph of docs/v2/drafts/diseno-leer.md §6 that starts «Mi lectura, sin confirmar por el dueño» is replaced by what he said.

## Scope

_State what is in scope and what is out of scope._

One question, asked to the owner (JP) by whoever is coordinating the work on kimun (the agent session he is talking to), as a plain message in that conversation: «¿'Núcleo nuevo de sldb' significa (a) que sldb sigue existiendo para quienes lo importan —su API de Python y sus modelos pydantic— y kimun hace por debajo el trabajo de saber qué cambió, indexar y responder; o (b) que kimun reemplaza a sldb y los consumidores se reescriben?» Any other answer the owner gives (a mix, a third option) is valid and is recorded as given. Out of scope: designing or implementing anything.

## Implementation Path

_Outline the expected implementation route or affected surface._

Ask the question alone: one message, prose, not inside a list of other questions and not through a multiple-choice dialog (that went wrong twice). If 'deskops show task task-finish-wip-leer-readers-by-class' says it is closed, give with the question the numbers measured on the desk (files matched, propositions read, reading errors, seconds) from the REPORT.md of the newest folder runs/subagents/*-leer/; if it is not closed, ask anyway. Record the answer verbatim between «», with the date; do not paraphrase it into the design.

## Validation

_List the checks required before this task can close._

- docs/v2/drafts/sesion-2026-11-15.md has a new section titled with the date of the answer, holding the answer between «»
- docs/v2/drafts/diseno-leer.md §6: its last paragraph, which starts «Mi lectura, sin confirmar por el dueño», is replaced by the owner's answer and what follows from it
- docs/v2/drafts/traspaso-2026-11-17.md §6 item 1 starts with **Respondida (dueño, YYYY-MM-DD):**

## Done When

_Name the observable condition that makes the task complete._

The three validation checks hold in a commit on main. The task stays open, and is not closed by the coordinator's own reading, while the owner has not answered.

FILE:instruccion
Preflight solo-TaskDoc de la tarea task-owner-decides-what-new-core-of-sldb-means: reformula el encargo tal como lo leería un ejecutor sin ningún otro contexto, lista ambigüedades con cita textual del input y da el DRY-RUN sin ejecutar nada. No ejecutes nada.

