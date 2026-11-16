---
id: task-toolchain-on-robocop2-and-suite-reproduced
status: draft
summary: ''
tags:
- workspace:desk
- artifact:task
current_node: ritual-execution
history: []
references: []
depends_on: []
pills: []
files: []
task_type: task.external
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# Toolchain on robocop2 and suite reproduced

## Rationale

_Explain why this task exists or the business driver behind it._

The work moved to robocop2 on 2026-11-16 and nothing can run there: no bb, no cargo, no memcap (docs/v2/drafts/traspaso-2026-11-17.md §10).

## Goal

_Describe the concrete result this task must produce._

bb test with the tree-sitter pod gives the same numbers as the last chore(evidence) commit of main.

## Scope

_State what is in scope and what is out of scope._

Install bb (version in release.edn), cargo and memcap on robocop2; build the pod with bb pod.

## Implementation Path

_Outline the expected implementation route or affected surface._

Install, bb pod, bb test; check the output says 'tree-sitter pod: available'.

## Validation

_List the checks required before this task can close._

- 

## Done When

_Name the observable condition that makes the task complete._

1081 tests, 0 failures, pod available, on robocop2.
