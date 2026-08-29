---
id: task-implement-v2-first-slice-node-pool-trees-revisions-persistence
status: draft
summary: ''
tags:
- workspace:desk
- artifact:task
routine: routine-task-implement-v2-first-slice-node-pool-trees-revisions-persistence
current_node: checklist-task-implement-v2-first-slice-node-pool-trees-revisions-persistence-execution-ready
history: []
references: []
depends_on: []
pills: []
files: []
checklists:
- checklist-task-implement-v2-first-slice-node-pool-trees-revisions-persistence-execution-ready
- checklist-task-implement-v2-first-slice-node-pool-trees-revisions-persistence-testing-ready
- checklist-task-implement-v2-first-slice-node-pool-trees-revisions-persistence-closeout-ready
task_type: implementation
inherits_from: []
inherit_acceptance_context: false
atoms:
- atom-smg-node-pool
- atom-tree-as-index
- atom-identity-is-the-content-hash-succession-is-a-recorded-edge
- atom-decision-pure-cljc-kernel-with-hosts-as-adapters
- atom-decision-no-separate-lisp-metalanguage
- atom-git-object-model-as-the-v2-roadmap
---

# Implement v2 first slice: node pool, trees, revisions, persistence

## Rationale

_Explain why this task exists or the business driver behind it._

docs/v2 reorients SLDB into a structured-language database over the SMG node pool; milestones 0-3 of the Git-model roadmap validate identity, tree-as-index, transactions and reload before any surface work.

## Goal

_Describe the concrete result this task must produce._

A pure .cljc kernel where content-addressed nodes (S/M/G classes) live in one pool, trees are ordered ownership-edge indexes with lazy per-tree Merkle roots, TransactionPlans (EDN) are validated and applied into immutable revisions with supersedes edges and CAS heads, and an append-only log plus content-addressed store on disk reproduces identical state and hashes after reload.

## Scope

_State what is in scope and what is out of scope._

Milestones 0 (blobs), 1 (trees), 2 (commits), 3 (persistence) from docs/v2/02-sustrato-computacional.md section 9. Kernel runs on Babashka and ClojureScript from the same source. Excludes: Markdown/CST surface, evidence-edge states, stand-off layers, M/G engines, embeddings, UI.

## Implementation Path

_Outline the expected implementation route or affected surface._

src/sldb/kernel/*.cljc (node, tree, plan, revision, store), test/sldb/kernel/*.cljc, bb.edn

## Validation

_List the checks required before this task can close._

- bb test

## Done When

_Name the observable condition that makes the task complete._

Invariants 1-7 and 10 of docs/v2/02 section 10 hold under generative tests; a hand-described transaction yields exactly its expected revision; closing and reopening the store reproduces the same state and hashes.
