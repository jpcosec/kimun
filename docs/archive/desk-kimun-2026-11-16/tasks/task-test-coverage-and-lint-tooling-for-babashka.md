---
id: task-test-coverage-and-lint-tooling-for-babashka
status: active
summary: 'Lint done (`bb lint` = scripts/check_rings.clj, rings + docstrings); pending coverage and a JVM run of the suite.'
tags:
- workspace:desk
- artifact:task
- source:audit
routine: routine-task-test-coverage-and-lint-tooling-for-babashka
current_node: checklist-task-test-coverage-and-lint-tooling-for-babashka-execution-ready
history: ["2026-10-09: lint part done — `bb lint` exists (scripts/check_rings.clj) and is not clj-kondo; coverage/JVM remain."]
references:
- bb.edn
- scripts/check_rings.clj
depends_on: []
pills: []
files: []
checklists:
- checklist-task-test-coverage-and-lint-tooling-for-babashka-execution-ready
- checklist-task-test-coverage-and-lint-tooling-for-babashka-testing-ready
- checklist-task-test-coverage-and-lint-tooling-for-babashka-closeout-ready
task_type: ''
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# Test coverage and lint tooling for Babashka

## Rationale

_Explain why this task exists or the business driver behind it._

Not provided.

## Goal

_Describe the concrete result this task must produce._

A coverage measure for the suite and a JVM run of the same tests.

## Scope

_State what is in scope and what is out of scope._

Done: lint. `bb lint` exists (`scripts/check_rings.clj`: dependency rings + docstrings, docs/v2/03 §1/§3), but it is **not** clj-kondo and there is no `.clj-kondo/`. Pending: cloverage or a bb-compatible coverage approach, and a JVM run of the suite (needs the clojure CLI, absent on this machine) so the same tests run on JVM and bb.

## Implementation Path

_Outline the expected implementation route or affected surface._

`bb.edn` (`test` depends on `lint`); coverage tooling; a JVM entry point.

## Validation

_List the checks required before this task can close._

- bb lint
- bb test
- coverage report produced
- the suite runs green on the JVM with the clojure CLI

## Done When

_Name the observable condition that makes the task complete._

Coverage is reported and the suite passes on both bb and the JVM.
