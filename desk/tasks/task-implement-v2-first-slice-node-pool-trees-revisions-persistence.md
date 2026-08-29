---
id: task-implement-v2-first-slice-node-pool-trees-revisions-persistence
status: active
summary: 'MILESTONE 0 ONLY (the task id is inherited from the former umbrella task;
  milestones 1-3 are separate tasks that depend on this one): canonical EDN bytes,
  host Hasher (SHA-256), NFC, node ids for all S/M/G shapes, in-memory pool.'
tags:
- workspace:desk
- artifact:task
routine: routine-task-implement-v2-first-slice-node-pool-trees-revisions-persistence
current_node: checklist-task-implement-v2-first-slice-node-pool-trees-revisions-persistence-testing-ready
history:
- operator-task-implement-v2-first-slice-node-pool-trees-revisions-persistence-activate
references: []
depends_on: []
pills:
- desk/contexts/pill-guardrail-v2-implementation-gate.md
files:
- bb.edn
- deps.edn
- src/sldb/kernel/canon.cljc
- src/sldb/host/hash.cljc
- src/sldb/host/text.cljc
- src/sldb/kernel/node.cljc
- src/sldb/kernel/pool.cljc
- test/sldb/kernel/generators.cljc
- test/sldb/kernel/canon_test.cljc
- test/sldb/kernel/node_test.cljc
- test/sldb/kernel/pool_test.cljc
- test/fixtures/nodes.edn
- docs/v2/02-sustrato-computacional.md
checklists:
- checklist-task-implement-v2-first-slice-node-pool-trees-revisions-persistence-execution-ready
- checklist-task-implement-v2-first-slice-node-pool-trees-revisions-persistence-testing-ready
- checklist-task-implement-v2-first-slice-node-pool-trees-revisions-persistence-closeout-ready
task_type: implementation
inherits_from: []
inherit_acceptance_context: false
atoms:
- atom-smg-node-pool
- atom-canonical-content-and-node-hashing
- atom-first-slice-runtime-choices
- atom-decision-pure-cljc-kernel-with-hosts-as-adapters
- atom-w-i-context-node-and-index
closeout_evidence_verified: false
---

# Implement v2 milestone 0: content-addressed node pool

## Rationale

_Explain why this task exists or the business driver behind it._

docs/v2 reorients SLDB into a structured-language database over the SMG node pool; milestones 0-3 of the Git-model roadmap validate identity, tree-as-index, transactions and reload before any surface work.

## Goal

_Describe the concrete result this task must produce._

A pure .cljc namespace set where canonical-bytes implements docs/v2/02 section 2.1 exactly, a Hasher protocol yields SHA-256 on Babashka, node ids are H(canonical-bytes({:class :kind :content})) for every shape in the section 2.1 table, and an in-memory pool stores and retrieves nodes by id with put/get being idempotent.

## Scope

_State what is in scope and what is out of scope._

In: sldb.kernel.canon (canonical EDN serialization), sldb.host.hash (Hasher protocol + Babashka SHA-256 impl), sldb.kernel.node (node shapes, id, validation of content per class/kind), sldb.kernel.pool (in-memory map id->node), generative tests and golden fixtures for hashing. Out: trees, edges, plans, revisions, persistence, ClojureScript host, any M/G evaluation.

## Implementation Path

_Outline the expected implementation route or affected surface._

bb.edn, deps.edn, src/sldb/kernel/canon.cljc, src/sldb/host/hash.cljc, src/sldb/host/text.cljc (NFC), src/sldb/kernel/node.cljc, src/sldb/kernel/pool.cljc, test/sldb/kernel/canon_test.cljc, test/sldb/kernel/node_test.cljc, test/sldb/kernel/pool_test.cljc, test/fixtures/nodes.edn (golden ids)

## Validation

_List the checks required before this task can close._

- bb test

## Done When

_Name the observable condition that makes the task complete._

bb test passes with: (a) golden fixture test/fixtures/nodes.edn = vector of {:class :kind :content :expected-id}, one entry per class/kind row of docs/v2/02 section 2.1 (9 rows; the :external entry uses :locator {:kind :file :path "docs/x.pdf" :page 3}), ids computed once with sldb.kernel.canon, reviewed and frozen; (b) test.check properties using test/sldb/kernel/generators.cljc (gen-node covering all 9 class/kind rows): canonical-bytes is order-insensitive for map keys and sets and order-sensitive for vectors, NFC-equivalent strings (sldb.host.text over java.text.Normalizer) hash equal, floats are rejected from canonical content, class/kind change the id, pool put/get idempotent (pool_test.cljc); (c) invariants 1, 2 and 10 of section 10 exercised. One test file per namespace: canon_test, node_test, pool_test.
