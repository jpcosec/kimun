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

A tree has a nominal id (ULID minted by :new-tree, like a Git ref), never a content hash; its descriptor {:tree :kind :name :root} is stored in the creating revision. For every node in a tree there is a tree object {:node id :children [[child-id child-tree-hash] ...]} in sibling order; tree-hash = H(canonical-bytes(object)); a leaf has :children []; merkle-root = tree-hash(root); sibling order therefore enters the hash. Tree objects live in the CAS and are shared between revisions. Lazy Merkle = a per-tree dirty set: any op changing the children of n adds n and its ancestors in that tree to dirty; on commit only dirty objects are recomputed in post-order, the rest are reused. Heads are {tree-id -> revision-id} updated by compare-and-swap per tree (milestone 2); a document head is the head of its :document tree; one head per tree in the first slice. Non-ownership edges are persisted as edge objects and referenced by the revision edge-set; reachability for verify/GC follows tree objects and edges from the heads.
