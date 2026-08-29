---
id: task-implement-v2-milestone-2-revisions-transactionplan-validation-cas-heads
status: draft
summary: 'Milestone 2 of docs/v2/02 section 9: TransactionPlan validation, immutable
  revisions, supersedes on replace, in-memory CAS heads and ConflictSet.'
tags:
- workspace:desk
- artifact:task
routine: routine-task-implement-v2-milestone-2-revisions-transactionplan-validation-cas-heads
current_node: checklist-task-implement-v2-milestone-2-revisions-transactionplan-validation-cas-heads-execution-ready
history: []
references: []
depends_on:
- task-implement-v2-milestone-1-trees-as-indexes-with-lazy-merkle
pills:
- desk/contexts/pill-guardrail-v2-implementation-gate.md
files:
- src/sldb/kernel/plan.cljc
- src/sldb/kernel/revision.cljc
- src/sldb/kernel/heads.cljc
- test/sldb/kernel/generators.cljc
- test/sldb/kernel/plan_test.cljc
- test/sldb/kernel/revision_test.cljc
- test/sldb/kernel/heads_test.cljc
- test/fixtures/tx-001.edn
- test/fixtures/tx-002-conflict.edn
- docs/v2/02-sustrato-computacional.md
checklists:
- checklist-task-implement-v2-milestone-2-revisions-transactionplan-validation-cas-heads-execution-ready
- checklist-task-implement-v2-milestone-2-revisions-transactionplan-validation-cas-heads-testing-ready
- checklist-task-implement-v2-milestone-2-revisions-transactionplan-validation-cas-heads-closeout-ready
task_type: implementation
inherits_from: []
inherit_acceptance_context: false
atoms:
- atom-transactionplan-edn-schema-and-validation
- atom-conflictset-on-failed-compare-and-swap
- atom-identity-is-the-content-hash-succession-is-a-recorded-edge
- atom-tree-identity-tree-objects-and-heads
- atom-revision-id-edge-set-and-diff
- atom-replace-semantics-inside-a-transaction
- atom-evidence-required-per-edge-type
---

# Implement v2 milestone 2: revisions, TransactionPlan validation, CAS heads

## Rationale

_Explain why this task exists or the business driver behind it._

Milestone 2 of docs/v2/02 section 9; the commit analog: a validated plan becomes an immutable revision.

## Goal

_Describe the concrete result this task must produce._

TransactionPlan EDN per docs/v2/02 section 5.1 with alias resolution and the seven validation checks, apply producing Revision {:id :roots :parents :tx :actor}, supersedes edges on :replace, in-memory heads with compare-and-swap per tree, ConflictSet per section 5.2 with automatic rebase for disjoint trees, and a diff between two revisions.

## Scope

_State what is in scope and what is out of scope._

In: sldb.kernel.plan (schema, alias resolution, the seven validation checks of docs/v2/02 section 5.1 by name: base-cas, ids-exist, tree-integrity, evidence-ref-hash, capability, opaque-replace-only, pure-data), sldb.kernel.revision (apply, eight-field Revision and its id, tree-set and edge-set objects, diff per section 5.2), sldb.kernel.heads (in-memory {tree-id revision-id}, compare-and-swap per entry, ConflictSet, automatic rebase for disjoint trees), full :replace semantics of section 5.1 including every re-anchoring rule of section 6.1 applied inside the transaction, gen-valid-plan and gen-plan-sequence in generators.cljc. Out: persistence, effects, heuristic reconciliation of external (drifted) changes, capabilities beyond the map in the store descriptor.

## Implementation Path

_Outline the expected implementation route or affected surface._

src/sldb/kernel/plan.cljc, src/sldb/kernel/revision.cljc, src/sldb/kernel/heads.cljc, test/sldb/kernel/plan_test.cljc, test/sldb/kernel/revision_test.cljc, test/fixtures/tx-001.edn, test/fixtures/tx-002-conflict.edn

## Validation

_List the checks required before this task can close._

- bb test

## Done When

_Name the observable condition that makes the task complete._

bb test passes with: fixture test/fixtures/tx-001.edn with exactly the shape {:store {:capabilities {<actor> :all}} :plan {...} :expected {:revision {:roots :trees :edges :parents :tx :actor :engines :timestamp} :revision-id <id>}} (fixed :timestamp in the plan) applied on an empty in-memory store yields a Revision whose eight fields and id equal :expected; fixture tx-002-conflict.edn ({:base-plans [...] :concurrent-plan {...} :expected {:conflict-set {...}}}) produces the expected ConflictSet, and a variant touching only disjoint trees rebases automatically; diff r1 r2 on the fixture revisions returns the section 5.2 shape with the expected :added/:removed/:moved/:superseded; test.check properties over gen-valid-plan: determinism (same plan applied on the same base state twice yields the same revision id and the same set of object ids), each of the seven checks named in Scope when violated rejects the whole plan with no state change (capability via an actor absent from the map), :replace keeps the old :order, records supersedes with the plan actor, re-anchors reference/binding edges and marks semantic/projection edges superseded, heads advance only via successful CAS (invariants 3, 7, 15, 16).
