---
id: task-document-the-v2-kernel-with-spec2viz-diagrams
status: draft
summary: ''
tags:
- workspace:desk
- artifact:task
routine: routine-task-document-the-v2-kernel-with-spec2viz-diagrams
current_node: checklist-task-document-the-v2-kernel-with-spec2viz-diagrams-execution-ready
history: []
references: []
depends_on: []
pills: []
files: []
checklists:
- checklist-task-document-the-v2-kernel-with-spec2viz-diagrams-execution-ready
- checklist-task-document-the-v2-kernel-with-spec2viz-diagrams-testing-ready
- checklist-task-document-the-v2-kernel-with-spec2viz-diagrams-closeout-ready
task_type: documentation
inherits_from: []
inherit_acceptance_context: false
atoms:
- atom-smg-node-pool
- atom-tree-identity-tree-objects-and-heads
- atom-revision-id-edge-set-and-diff
- atom-decision-pure-cljc-kernel-with-hosts-as-adapters
---

# Document the v2 kernel with spec2viz diagrams

## Rationale

_Explain why this task exists or the business driver behind it._

Diagrams help a lot (user); the v2 kernel has no spec2viz specs yet and the existing docs/architecture/spec2viz specs describe the previous stage.

## Goal

_Describe the concrete result this task must produce._

spec2viz YAML specs for the v2 kernel (rings and namespaces, CAS object model, transaction flow, store layout, succession/re-anchoring) rendered and cataloged, referenced from docs/v2/README.md.

## Scope

_State what is in scope and what is out of scope._

In: docs/architecture/spec2viz/v2-*.yml, rendered outputs, catalog. Out: rewriting the old target-*.yml specs (drawer).

## Implementation Path

_Outline the expected implementation route or affected surface._

docs/architecture/spec2viz/v2-*.yml, docs/architecture/spec2viz/manifest.yml, docs/v2/README.md

## Validation

_List the checks required before this task can close._

- bb test

## Done When

_Name the observable condition that makes the task complete._

spec2viz diagram validate passes for every v2 spec; rendered files exist and the catalog builds; docs/v2/README.md links them.
