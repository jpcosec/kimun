---
id: task-rewrite-the-pre-v2-spec2viz-specs
status: closed
summary: 'Closed 2026-10-09: docs/architecture/spec2viz/ only has v2-*.yml; target-*.yml live in docs/archive/architecture/spec2viz/.'
tags:
- workspace:desk
- artifact:task
- source:audit
routine: routine-task-rewrite-the-pre-v2-spec2viz-specs
current_node: checklist-task-rewrite-the-pre-v2-spec2viz-specs-closeout-ready
history: ["2026-10-09: closed; the pre-v2 target-*.yml/manifest.yml are only in docs/archive/architecture/spec2viz/, and docs/architecture/spec2viz/ declares only v2-*.yml."]
references:
- docs/architecture/spec2viz/
- docs/archive/architecture/spec2viz/
depends_on: []
pills: []
files: []
checklists:
- checklist-task-rewrite-the-pre-v2-spec2viz-specs-execution-ready
- checklist-task-rewrite-the-pre-v2-spec2viz-specs-testing-ready
- checklist-task-rewrite-the-pre-v2-spec2viz-specs-closeout-ready
task_type: ''
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# Rewrite the pre-v2 spec2viz specs

## Rationale

_Explain why this task exists or the business driver behind it._

Not provided.

## Goal

_Describe the concrete result this task must produce._

Retire the pre-v2 spec2viz specs once the v2 ones exist.

## Scope

_State what is in scope and what is out of scope._

`docs/architecture/spec2viz/target-*.yml` described the previous stage (Rust/Lisp-era vocabulary) and were to be rewritten or retired once the `v2-*.yml` specs existed. Done: `target-*.yml` and `manifest.yml` are only in `docs/archive/architecture/spec2viz/`, and `docs/architecture/spec2viz/` declares only `v2-*.yml`.

## Implementation Path

_Outline the expected implementation route or affected surface._

`docs/archive/architecture/spec2viz/` (retired); `docs/architecture/spec2viz/v2-*.yml` (live).

## Validation

_List the checks required before this task can close._

- bb lint
- bb test
- spec2viz diagram validate on every v2 spec

## Done When

_Name the observable condition that makes the task complete._

No pre-v2 `target-*.yml` remains outside `docs/archive/`.
