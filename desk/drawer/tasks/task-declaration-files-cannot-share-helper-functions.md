---
id: task-declaration-files-cannot-share-helper-functions
status: deferred
summary: ''
tags:
- workspace:desk
- artifact:task
- source:inbox
current_node: ''
history: []
references: []
depends_on: []
pills: []
files: []
task_type: ''
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# Declaration files cannot share helper functions

## Rationale

_Explain why this task exists or the business driver behind it._

Promoted from the inbox note `desk/inbox/20261116-204320-suggestion-declaration-files-cannot-share-helper-functions.md`.

## Goal

_Describe the concrete result this task must produce._

Triage and resolve the inbox message promoted from `desk/inbox/20261116-204320-suggestion-declaration-files-cannot-share-helper-functions.md`.

## Scope

_State what is in scope and what is out of scope._

_Describe the incoming message with enough evidence to triage._

Rationale: found by the readers run of 2026-11-16 (runs/subagents/2026-11-16-lectores-desk/REPORT.md, limit 1). A context can :require only relations from another context, never a def or a function, and a relation is a macro so it cannot be passed as a value; the thirteen readers of the deskops desk carry the same helper block copied by a script (sync-helpers.py). Goal: decide and implement how declaration files share plain functions. Scope: design first (docs/v2/drafts/diseno-evaluar.md, diseno-leer.md): it changes what a context exports. Done When: the readers share one helper definition. Task type when authored: task.code, after a design by the coordinator.

## Implementation Path

_Outline the expected implementation route or affected surface._

To be defined when the task is promoted.

## Validation

_List the checks required before this task can close._

- 

## Done When

_Name the observable condition that makes the task complete._

The message is resolved, answered, or promoted into active work.
