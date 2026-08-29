---
# board-xxx
id: board-001
# Affected workspace or domain
scope: desk
# List of task-xxx paths
tasks:
- desk/tasks/task-implement-v2-milestone-2-revisions-transactionplan-validation-cas-heads.md
- desk/tasks/task-implement-v2-milestone-3-file-persistence-and-reload.md
# List of pill-xxx paths
pills:
- desk/contexts/pills.md
- desk/contexts/pill-planning-contracts.md
# List of ritual-xxx paths
rituals:
- desk/rituals/execution.md
- desk/rituals/testing.md
- desk/rituals/closeout.md
# e.g., system:sldb, workspace:desk
tags:
- workspace:desk
- system:sldb
---

# sldb-refactor-worktree Board

## Purpose

_Explain what this board routes and why it exists._



## Notes

_Add short operational notes about the current routed set._

- Implement v2 milestone 1: trees as indexes with lazy Merkle [draft] - Nominal tree ids (ULID), ownership edges with dense sibling order, tree objects {:node :children [[child tree-hash]...]} hashed per docs/v2/02 section 3.1, merkle-root per tree with dirty-path recomputation, and a node belonging to several trees with one parent per tree.
- Implement v2 milestone 2: revisions, TransactionPlan validation, CAS heads [draft] - TransactionPlan EDN per docs/v2/02 section 5.1 with alias resolution and the seven validation checks, apply producing Revision {:id :roots :parents :tx :actor}, supersedes edges on :replace, in-memory heads with compare-and-swap per tree, ConflictSet per section 5.2 with automatic rebase for disjoint trees, and a diff between two revisions.
- Implement v2 milestone 3: file persistence and reload [draft] - A Store protocol with a Babashka file backend per docs/v2/02 section 8.1: objects/<hash> written as UTF-8 canonical EDN so that H(file) == name, log.edn append-only with one Transaction map per line, heads.edn and store.edn written atomically by rename; open = read descriptor and heads then replay (re-apply every logged plan and check each revision id matches the logged one); verify = recompute the hash of every object reachable from the heads and compare with its name. rebuild-indexes is out of scope until derived indexes exist (milestone 4+).

## Task Details

_Generated from the task references above._

- Implement v2 milestone 2: revisions, TransactionPlan validation, CAS heads [draft] - TransactionPlan EDN per docs/v2/02 section 5.1 with alias resolution and the seven validation checks, apply producing Revision {:id :roots :parents :tx :actor}, supersedes edges on :replace, in-memory heads with compare-and-swap per tree, ConflictSet per section 5.2 with automatic rebase for disjoint trees, and a diff between two revisions.
- Implement v2 milestone 3: file persistence and reload [draft] - A Store protocol with a Babashka file backend per docs/v2/02 section 8.1: objects/<hash> written as UTF-8 canonical EDN so that H(file) == name, log.edn append-only with one Transaction map per line, heads.edn and store.edn written atomically by rename; open = read descriptor and heads then replay (re-apply every logged plan and check each revision id matches the logged one); verify = recompute the hash of every object reachable from the heads and compare with its name. rebuild-indexes is out of scope until derived indexes exist (milestone 4+).
