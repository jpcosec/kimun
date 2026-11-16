---
id: atom-decision-trees-are-trees-of-positions-git-like
title: 'Decision: trees are trees of positions (Git-like)'
five_wh_one_plus: why
tags:
- system:sldb
- epoch:v2
- domain:decisions
provenance: runs/subagents/20260829-180407-task-milestone-4-markdown-cst-to-neutral-ast-zero-context-audit/triage.md
---

# Decision: trees are trees of positions (Git-like)

## Answer

On 2026-08-29, while building the Markdown surface, the one-parent-per-node-per-tree rule proved unrepresentable: every list item is the same node {:type :item} and identical paragraphs repeat, so ast->plan failed with node-already-in-tree. The user chose the Git-like model over occurrence discriminators: a tree is a tree of positions identified by path; a node may occur at many positions of one tree; tree objects are content-addressed subtrees that identical subtrees share; ownership ops in plans address positions by path (:parent, :at, :from, :to) and a :detach op was added; conflicts are reported per parent path. Invariant 5 now reads: a node may occur at N positions of N trees, each position has one parent. Milestones 1-2 were revised accordingly (tree.cljc rewritten, fixtures tx-001/tx-002 regenerated; trees.edn root hash unchanged because tree objects kept their format).
