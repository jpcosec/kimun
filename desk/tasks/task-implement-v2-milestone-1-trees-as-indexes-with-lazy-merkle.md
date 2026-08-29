---
id: task-implement-v2-milestone-1-trees-as-indexes-with-lazy-merkle
status: draft
summary: 'Milestone 1 of docs/v2/02 section 9: nominal tree ids, ownership edges with
  sibling order, tree objects, lazy merkle-root per tree.'
tags:
- workspace:desk
- artifact:task
routine: routine-task-implement-v2-milestone-1-trees-as-indexes-with-lazy-merkle
current_node: checklist-task-implement-v2-milestone-1-trees-as-indexes-with-lazy-merkle-execution-ready
history: []
references: []
depends_on:
- task-implement-v2-first-slice-node-pool-trees-revisions-persistence
pills:
- desk/contexts/pill-guardrail-v2-implementation-gate.md
files:
- src/sldb/kernel/tree.cljc
- src/sldb/kernel/edge.cljc
- test/sldb/kernel/generators.cljc
- test/sldb/kernel/tree_test.cljc
- test/sldb/kernel/edge_test.cljc
- test/fixtures/trees.edn
- docs/v2/02-sustrato-computacional.md
checklists:
- checklist-task-implement-v2-milestone-1-trees-as-indexes-with-lazy-merkle-execution-ready
- checklist-task-implement-v2-milestone-1-trees-as-indexes-with-lazy-merkle-testing-ready
- checklist-task-implement-v2-milestone-1-trees-as-indexes-with-lazy-merkle-closeout-ready
task_type: implementation
inherits_from: []
inherit_acceptance_context: false
atoms:
- atom-tree-as-index
- atom-tree-identity-tree-objects-and-heads
- atom-evidence-required-per-edge-type
- atom-canonical-content-and-node-hashing
- atom-decision-first-slice-kernel-semantics-confirmed
---

# Implement v2 milestone 1: trees as indexes with lazy Merkle

## Rationale

_Explain why this task exists or the business driver behind it._

Milestone 1 of docs/v2/02 section 9; trees are the Git-tree analog and must exist before revisions.

## Goal

_Describe the concrete result this task must produce._

Nominal tree ids (ULID), ownership edges with dense sibling order, tree objects {:node :children [[child tree-hash]...]} hashed per docs/v2/02 section 3.1, merkle-root per tree with dirty-path recomputation, and a node belonging to several trees with one parent per tree.

## Scope

_State what is in scope and what is out of scope._

In: sldb.kernel.edge (Edge and Evidence shapes, edge id without timestamp per docs/v2/02 section 3.2), sldb.kernel.tree (tree ids as ULID, tree descriptor {:tree :kind :name :root} as a CAS object, ownership as tree objects {:node :children [[child tree-hash]...]}, merkle-root, per-tree dirty set that starts empty per apply and contains every node of a newly created tree, post-order recomputation of dirty objects only, reuse of unchanged tree objects from the base state into the new state per section 3.1), gen-tree added to test/sldb/kernel/generators.cljc. Heads: data shape {tree-id revision-id} only; CAS and revisions are milestone 2. Out: plans, revisions, persistence, non-ownership edge semantics beyond their shape and id.

## Implementation Path

_Outline the expected implementation route or affected surface._

src/sldb/kernel/tree.cljc, src/sldb/kernel/edge.cljc (edge shapes and edge id per section 3.2), test/sldb/kernel/tree_test.cljc, test/fixtures/trees.edn

## Validation

_List the checks required before this task can close._

- bb test

## Done When

_Name the observable condition that makes the task complete._

bb test passes with: golden fixture test/fixtures/trees.edn with exactly the shape {:nodes [{:class :kind :content} ...] :tree {:kind :document :root <alias> :children {<alias> [<alias> ...]}} :expected {:objects {<alias> <tree-hash>} :root <merkle-root>}} (aliases index into :nodes), ids computed once and frozen; test.check properties over gen-tree: changing one leaf changes exactly the tree objects on its path to the root and no other (the dirty set equals that path), reordering siblings changes the parent object hash, the same node in two trees has one node id and two independent tree hashes, every tree stays a tree with dense sibling order, unchanged subtrees between base state and new state keep identical tree-object ids, edge ids ignore any timestamp key (invariants 4, 5, 13, 14, 16).
