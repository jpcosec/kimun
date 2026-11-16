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

A tree (document, section hierarchy, taxonomy, syntax tree, W_i context) is a tree of POSITIONS over the shared node pool, not a container of nodes: each position holds a node id and an ordered vector of child positions, and the identity of a position is its path (vector of sibling indices from the root). A node may occur at many positions of the same tree and in many trees (all the items of a list are one node); plans address ownership by path. Each position has exactly one parent. The Merkle root of a tree hashes only its own tree objects and node ids, so no other edge type can create hash cycles. This mirrors Git: one blob referenced from many paths and many trees.
