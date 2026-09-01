---
id: task-test-coverage-and-lint-tooling-for-babashka
status: active
summary: ''
tags:
- workspace:desk
- artifact:task
- source:drawer
routine: routine-task-test-coverage-and-lint-tooling-for-babashka
current_node: checklist-task-test-coverage-and-lint-tooling-for-babashka-execution-ready
history: []
references:
- desk/drawer/tasks/task-test-coverage-and-lint-tooling-for-babashka.md
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

Triage and resolve the inbox message promoted from `desk/inbox/20260829-174024-suggestion-test-coverage-and-lint-tooling-for-babashka.md`.

## Scope

_State what is in scope and what is out of scope._

Evaluate clj-kondo (lint), cloverage or a bb-compatible coverage approach, and a JVM run of the suite (needs the clojure CLI, absent on this machine) so that the same tests run on JVM and bb.

## Implementation Path

_Outline the expected implementation route or affected surface._

Promoted from desk/drawer/tasks/task-test-coverage-and-lint-tooling-for-babashka.md.

## Validation

_List the checks required before this task can close._

- bb lint
- bb test

## Done When

_Name the observable condition that makes the task complete._

Promoted work is completed, validated, and closed with a commit.
