---
id: task-replicate-v1-cli-workflow-capabilities
status: draft
summary: ''
tags:
- workspace:desk
- artifact:task
routine: routine-task-replicate-v1-cli-workflow-capabilities
current_node: checklist-task-replicate-v1-cli-workflow-capabilities-execution-ready
history: []
references: []
depends_on:
- macrotask-02-python-ffi
- macrotask-03-importers-emitters
pills: []
files: []
checklists:
- checklist-task-replicate-v1-cli-workflow-capabilities-execution-ready
- checklist-task-replicate-v1-cli-workflow-capabilities-testing-ready
- checklist-task-replicate-v1-cli-workflow-capabilities-closeout-ready
---

# Replicate V1 CLI Workflow Capabilities

## Rationale

_Explain why this task exists or the business driver behind it._

Phase 1 requires that all prior functionality (managing atoms, graph reflection, missing edges, desk status) is available via the new engine before expanding to new features.

## Goal

_Describe the concrete result this task must produce._

Re-implement the deskops equivalent commands on top of the new Python-Rust architecture, ensuring 100% parity with V1.

## Scope

_State what is in scope and what is out of scope._

Python CLI subcommands and integration testing against actual workspace atoms.

## Implementation Path

_Outline the expected implementation route or affected surface._



## Validation

_List the checks required before this task can close._

- pytest sldb-cli/tests/test_v1_parity.py

## Done When

_Name the observable condition that makes the task complete._
