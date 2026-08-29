---
id: task-implement-v2-milestone-3-file-persistence-and-reload
status: active
summary: 'Milestone 3 of docs/v2/02 section 9: Store protocol, Babashka file backend
  (CAS objects, append-only log, atomic heads), replay, verify.'
tags:
- workspace:desk
- artifact:task
routine: routine-task-implement-v2-milestone-3-file-persistence-and-reload
current_node: checklist-task-implement-v2-milestone-3-file-persistence-and-reload-testing-ready
history:
- operator-task-implement-v2-milestone-3-file-persistence-and-reload-activate
references: []
depends_on:
- task-implement-v2-milestone-2-revisions-transactionplan-validation-cas-heads
pills:
- desk/contexts/pill-guardrail-v2-implementation-gate.md
files:
- src/sldb/kernel/store.cljc
- src/sldb/host/fs_store.cljc
- src/sldb/kernel/revision.cljc
- src/sldb/kernel/plan.cljc
- test/sldb/kernel/store_test.cljc
- test/fixtures/tx-001.edn
- test/fixtures/tx-002-conflict.edn
- docs/v2/02-sustrato-computacional.md
checklists:
- checklist-task-implement-v2-milestone-3-file-persistence-and-reload-execution-ready
- checklist-task-implement-v2-milestone-3-file-persistence-and-reload-testing-ready
- checklist-task-implement-v2-milestone-3-file-persistence-and-reload-closeout-ready
task_type: implementation
inherits_from: []
inherit_acceptance_context: false
atoms:
- atom-first-slice-runtime-choices
- atom-git-object-model-as-the-v2-roadmap
- atom-tree-identity-tree-objects-and-heads
closeout_evidence_verified: false
---

# Implement v2 milestone 3: file persistence and reload

## Rationale

_Explain why this task exists or the business driver behind it._

Milestone 3 of docs/v2/02 section 9; closing and reopening the store must reproduce identical state and hashes.

## Goal

_Describe the concrete result this task must produce._

A Store protocol with a Babashka file backend per docs/v2/02 section 8.1: objects/<hash> written as UTF-8 canonical EDN so that H(file) == name, log.edn append-only with one Transaction map per line, heads.edn and store.edn written atomically by rename; open = read descriptor and heads then replay (re-apply every logged plan and check each revision id matches the logged one); verify = recompute the hash of every object reachable from the heads and compare with its name. rebuild-indexes is out of scope until derived indexes exist (milestone 4+).

## Scope

_State what is in scope and what is out of scope._

In: sldb.kernel.store (protocol: put-object, get-object, append-tx, read-log, heads, cas-head!, open, replay, verify), sldb.host.fs-store (Babashka/JVM file semantics only), replay and verify as callable functions. Out: SQLite, sync, GC, rebuild-indexes, ClojureScript/Node host.

## Implementation Path

_Outline the expected implementation route or affected surface._

src/sldb/kernel/store.cljc, src/sldb/host/fs_store.cljc, test/sldb/kernel/store_test.cljc

## Validation

_List the checks required before this task can close._

- bb test

## Done When

_Name the observable condition that makes the task complete._

bb test passes with: applying test/fixtures/tx-001.edn and the base plans of tx-002-conflict.edn to a fresh temporary directory, then discarding every in-memory structure and calling open on that directory in the same process (a second test does the same from a separate bb subprocess), and comparing heads, revision ids, tree-sets, edge-sets, tree merkle-roots and every node id gives equality; replay given only log.edn and objects/ (heads.edn deleted) rebuilds the same heads and revision ids; corrupting one object file makes verify fail naming that object id; a heads.edn CAS whose expected value is stale is rejected without writing; test.check property over gen-plan-sequence (from milestone 2): for any valid plan sequence, in-memory state equals reloaded state (invariants 1, 3, 10, 17).
