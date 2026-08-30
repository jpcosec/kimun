---
id: atom-tree-identity-tree-objects-and-heads
title: Tree identity, tree objects and heads
five_wh_one_plus: how
tags:
- system:sldb
- epoch:v2
- domain:graph
provenance: docs/v2/02-sustrato-computacional.md
---

# Tree identity, tree objects and heads

## Answer

A tree has a nominal id (ULID minted by :new-tree, like a Git ref), never a content hash; its descriptor {:tree :kind :name :root} is a CAS object referenced by the revision tree-set. For every POSITION there is a tree object {:node id :children [[child-id child-tree-hash] ...]} in sibling order; tree-hash = H(canonical-bytes(object)); a leaf has :children []; merkle-root = tree-hash(root); sibling order therefore enters the hash, and identical subtrees share one object within a tree and across trees. Lazy Merkle: a position without hash is dirty; ownership ops (add-child, detach, move, replace) clear the hash of the touched position and its ancestors; commit recomputes only those in post-order. Heads are {tree-id -> revision-id} updated by compare-and-swap per tree; a document head is the head of its :document tree. Non-ownership edges are persisted as edge objects and referenced by the revision edge-set; reachability for verify/GC follows tree objects and edges from the heads.
