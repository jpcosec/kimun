---
id: task-harden-the-first-slice-test-suite-against-every-spec-promise
status: active
summary: ''
tags:
- workspace:desk
- artifact:task
routine: routine-task-harden-the-first-slice-test-suite-against-every-spec-promise
current_node: checklist-task-harden-the-first-slice-test-suite-against-every-spec-promise-testing-ready
history:
- operator-task-harden-the-first-slice-test-suite-against-every-spec-promise-activate
references: []
depends_on: []
pills: []
files: []
checklists:
- checklist-task-harden-the-first-slice-test-suite-against-every-spec-promise-execution-ready
- checklist-task-harden-the-first-slice-test-suite-against-every-spec-promise-testing-ready
- checklist-task-harden-the-first-slice-test-suite-against-every-spec-promise-closeout-ready
task_type: implementation
inherits_from: []
inherit_acceptance_context: false
atoms:
- atom-first-slice-runtime-choices
- atom-canonical-content-and-node-hashing
- atom-transactionplan-edn-schema-and-validation
- atom-decision-first-slice-kernel-semantics-confirmed
closeout_evidence_verified: false
---

# Harden the first-slice test suite against every spec promise

## Rationale

_Explain why this task exists or the business driver behind it._

Milestones 0-3 shipped with executor-written tests only; no independent tester lane ran and no promise-to-test traceability exists (user: build on rock, prove everything the specs promise).

## Goal

_Describe the concrete result this task must produce._

A traceability table docs/v2/tests/promises.md mapping every normative statement of docs/v2/02 sections 2.1, 3.1, 3.2, 4.1, 5, 5.1, 5.2, 6.1, 8.1 and invariants 1-17 to the test that proves it, with the gaps found by fresh-context tester lanes closed by new tests (negative cases, contention, cross-implementation oracle for canonical-bytes).

## Scope

_State what is in scope and what is out of scope._

In: tester lanes (fresh, read-only) over src/test/docs; new tests in test/sldb/kernel; an external oracle script for canonical-bytes hashes; promises.md. Out: new kernel features, host parity (separate drawer task), coverage tooling (drawer).

## Implementation Path

_Outline the expected implementation route or affected surface._

docs/v2/tests/promises.md, test/sldb/kernel/*_test.cljc, scripts/canon_oracle.py, runs/subagents/*-testing/

## Validation

_List the checks required before this task can close._

- bb test

## Done When

_Name the observable condition that makes the task complete._

Every promise row in promises.md names a passing test or an explicit accepted gap with reason; tester lanes report zero untested high-impact promises; bb test green and stable over 3 runs.
