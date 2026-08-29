---
id: atom-tree-as-index
title: Tree as index
five_wh_one_plus: what
tags:
- system:sldb
- epoch:v2
- domain:graph
provenance: docs/v2/02-sustrato-computacional.md
---

# Tree as index

## Answer

A tree (document, section hierarchy, taxonomy, syntax tree, W_i context) is a set of ordered ownership edges over the shared node pool, not a container of nodes. A node may belong to many trees and has exactly one parent per tree. Each tree's Merkle root hashes only its own ownership edges and node ids, so no other edge type can create hash cycles. This mirrors Git: one blob referenced by many trees.
