---
id: atom-revision-id-edge-set-and-diff
title: Revision id, edge-set and diff
five_wh_one_plus: what
tags:
- system:sldb
- epoch:v2
- domain:history
provenance: docs/v2/02-sustrato-computacional.md
---

# Revision id, edge-set and diff

## Answer

Revision = {:roots {tree-id merkle-root} for every live tree, :trees (id of the tree-set object = H of the sorted vector of [tree-id descriptor-id]; descriptors are CAS objects {:tree :kind :name :root}), :edges (id of the edge-set object = H of the sorted vector of active edge ids; each edge is stored individually at objects/<edge-id>), :parents, :tx, :actor, :engines, :timestamp}; revision-id = H(canonical-bytes) of exactly those eight fields; provenance is derived from tx and actor. Transaction = {:id H(resolved-plan) :plan :revision}. diff r1 r2 returns {:trees {tree-id {:added :removed :moved}} :edges {:added :removed} :superseded [[old new]...]} computed by descending only into tree objects whose hashes differ and comparing edge-sets; it is the exit criterion of milestone 2.
