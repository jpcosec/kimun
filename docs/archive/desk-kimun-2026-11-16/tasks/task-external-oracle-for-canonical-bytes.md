---
id: task-external-oracle-for-canonical-bytes
status: closed
summary: 'Closed 2026-10-09: scripts/canon_oracle.py and `bb oracle` (9/9 canonical ids, docs/v2/05 §2).'
tags:
- workspace:desk
- artifact:task
- source:audit
routine: routine-task-external-oracle-for-canonical-bytes
current_node: checklist-task-external-oracle-for-canonical-bytes-closeout-ready
history: ["2026-10-09: closed; scripts/canon_oracle.py exists and `bb oracle` reports 9/9 canonical ids (docs/v2/05 §2)."]
references:
- scripts/canon_oracle.py
- docs/v2/05-estado.md
depends_on: []
pills: []
files: []
checklists:
- checklist-task-external-oracle-for-canonical-bytes-execution-ready
- checklist-task-external-oracle-for-canonical-bytes-testing-ready
- checklist-task-external-oracle-for-canonical-bytes-closeout-ready
task_type: ''
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# External oracle for canonical-bytes

## Rationale

_Explain why this task exists or the business driver behind it._

Not provided.

## Goal

_Describe the concrete result this task must produce._

An independent implementation of canonical-bytes so conformity is not circular.

## Scope

_State what is in scope and what is out of scope._

A second, independent implementation of canonical-bytes (Python script `scripts/canon_oracle.py`) computing SHA-256 over the same fixture inputs; compare against `test/fixtures/nodes.edn` and `trees.edn` ids. Done: `bb oracle` (docs/v2/05 §2).

## Implementation Path

_Outline the expected implementation route or affected surface._

`scripts/canon_oracle.py`; task `oracle` in `bb.edn`.

## Validation

_List the checks required before this task can close._

- bb lint
- bb test
- bb oracle

## Done When

_Name the observable condition that makes the task complete._

`bb oracle` recomputes the canonical ids independently and agrees with the fixtures (9/9).
