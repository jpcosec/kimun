---
id: task-rewrite-the-pre-v2-spec2viz-specs
status: active
summary: ''
tags:
- workspace:desk
- artifact:task
- source:drawer
routine: routine-task-rewrite-the-pre-v2-spec2viz-specs
current_node: checklist-task-rewrite-the-pre-v2-spec2viz-specs-execution-ready
history: []
references:
- desk/drawer/tasks/task-rewrite-the-pre-v2-spec2viz-specs.md
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

Triage and resolve the inbox message promoted from `desk/inbox/20260829-174027-suggestion-rewrite-the-pre-v2-spec2viz-specs.md`.

## Scope

_State what is in scope and what is out of scope._

docs/architecture/spec2viz/target-*.yml describe the previous stage (Rust/Lisp era vocabulary in places); rewrite or retire them once the v2-*.yml specs exist, and update manifest.yml and vistas.yml accordingly.

## Implementation Path

_Outline the expected implementation route or affected surface._

Promoted from desk/drawer/tasks/task-rewrite-the-pre-v2-spec2viz-specs.md.

## Validation

_List the checks required before this task can close._

- bb lint
- bb test
- spec2viz diagram validate on every rewritten spec

## Done When

_Name the observable condition that makes the task complete._

Promoted work is completed, validated, and closed with a commit.
