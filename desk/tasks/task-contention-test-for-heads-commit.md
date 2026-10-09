---
id: task-contention-test-for-heads-commit
status: active
summary: 'Exercise kimun.kernel.heads/commit! under concurrent writers; a base test already lands disjoint trees.'
tags:
- workspace:desk
- artifact:task
- source:audit
routine: routine-task-contention-test-for-heads-commit
current_node: checklist-task-contention-test-for-heads-commit-execution-ready
history: []
references:
- src/kimun/kernel/heads.cljc
- test/kimun/kernel/hardening_test.cljc
depends_on: []
pills: []
files: []
checklists:
- checklist-task-contention-test-for-heads-commit-execution-ready
- checklist-task-contention-test-for-heads-commit-testing-ready
- checklist-task-contention-test-for-heads-commit-closeout-ready
task_type: ''
inherits_from: []
inherit_acceptance_context: false
atoms: []
---

# Contention test for heads commit!

## Rationale

_Explain why this task exists or the business driver behind it._

Not provided.

## Goal

_Describe the concrete result this task must produce._

Prove the CAS retry and ConflictSet paths of the heads commit under real concurrent writers.

## Scope

_State what is in scope and what is out of scope._

Exercise `kimun.kernel.heads/commit!` (decision 20 renames `sldb.kernel.heads`) under real concurrent writers (futures on the JVM, separate bb processes on the file backend) and prove the CAS retry and ConflictSet paths, including two processes committing to the same directory. There is already `concurrent-commits-on-disjoint-trees-all-land` in `test/kimun/kernel/hardening_test.cljc`; check its real coverage before extending.

## Implementation Path

_Outline the expected implementation route or affected surface._

`src/kimun/kernel/heads.cljc`; `test/kimun/kernel/hardening_test.cljc`.

## Validation

_List the checks required before this task can close._

- bb lint
- bb test

## Done When

_Name the observable condition that makes the task complete._

Concurrent commits to the same tree are serialised by compare-and-swap with no lost update, and the conflict path is covered by a test.
