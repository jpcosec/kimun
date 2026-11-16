---
id: task-compare-declare-about-with-the-sldb-edge-index-on-the-desk
status: draft
summary: ''
tags:
- workspace:desk
- artifact:task
current_node: ritual-execution
history: []
references: []
depends_on:
- task-readers-for-every-class-of-the-deskops-desk
pills: []
files: []
task_type: task.code
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# Compare declare about with the sldb edge index on the desk

## Rationale

_Explain why this task exists or the business driver behind it._

Criterion 2 is measured against what sldb/deskops answer today, not against synthetic demos.

## Goal

_Describe the concrete result this task must produce._

A measured comparison of the relations kimun reads from the desk against sldb's edge index.

## Scope

_State what is in scope and what is out of scope._

Read-only on SLDB_1 or on copies; no real store is touched.

## Implementation Path

_Outline the expected implementation route or affected surface._

Export sldb's edges for the desk, export kimun's propositions, diff, explain every difference.

## Validation

_List the checks required before this task can close._

- 

## Done When

_Name the observable condition that makes the task complete._

The diff is in runs/ with every difference explained or fixed.
