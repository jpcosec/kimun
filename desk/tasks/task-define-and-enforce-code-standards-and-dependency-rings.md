---
id: task-define-and-enforce-code-standards-and-dependency-rings
status: active
summary: ''
tags:
- workspace:desk
- artifact:task
routine: routine-task-define-and-enforce-code-standards-and-dependency-rings
current_node: checklist-task-define-and-enforce-code-standards-and-dependency-rings-testing-ready
history:
- operator-task-define-and-enforce-code-standards-and-dependency-rings-activate
references: []
depends_on: []
pills: []
files: []
checklists:
- checklist-task-define-and-enforce-code-standards-and-dependency-rings-execution-ready
- checklist-task-define-and-enforce-code-standards-and-dependency-rings-testing-ready
- checklist-task-define-and-enforce-code-standards-and-dependency-rings-closeout-ready
task_type: implementation
inherits_from: []
inherit_acceptance_context: false
atoms:
- atom-decision-pure-cljc-kernel-with-hosts-as-adapters
- atom-first-slice-runtime-choices
closeout_evidence_verified: false
---

# Define and enforce code standards and dependency rings

## Rationale

_Explain why this task exists or the business driver behind it._

The kernel currently imports host adapters (sldb.kernel.canon and sldb.kernel.store require sldb.host.*), which inverts the intended ring order; without enforced rings the kernel becomes spaghetti (user requirement).

## Goal

_Describe the concrete result this task must produce._

docs/v2/03-estandares-de-codigo.md defining rings (0 kernel: only clojure.* and kernel; 1 host adapters: implement kernel ports, only place with reader conditionals; 2 surfaces; 3 tests), docstrings on every public var, pure kernel with explicit host argument, namespaced error types, test standards; kernel ports (protocols) in sldb.kernel.ports with host implementations; a bb task that fails on ring violations or missing docstrings and runs before tests.

## Scope

_State what is in scope and what is out of scope._

In: docs/v2/03, sldb.kernel.ports, refactor of canon/node/edge/tree/revision/store to take a host value instead of requiring sldb.host.*, scripts/check_rings.clj, bb.edn tasks lint and test. Out: clj-kondo or coverage tooling (drawer), CLI surfaces.

## Implementation Path

_Outline the expected implementation route or affected surface._

docs/v2/03-estandares-de-codigo.md, src/sldb/kernel/ports.cljc, src/sldb/host/default.cljc, scripts/check_rings.clj, bb.edn, all src/sldb/**/*.cljc

## Validation

_List the checks required before this task can close._

- bb test

## Done When

_Name the observable condition that makes the task complete._

bb lint passes (zero inward requires from sldb.kernel.* to sldb.host.*, zero public vars without docstring); bb test green; docs/v2/03 cites the ring rule and the lint task.
