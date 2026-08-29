---
id: atom-git-object-model-as-the-v2-roadmap
title: Git object model as the v2 roadmap
five_wh_one_plus: how
tags:
- system:sldb
- epoch:v2
- domain:migration-strategy
provenance: docs/v2/02-sustrato-computacional.md
---

# Git object model as the v2 roadmap

## Answer

The v2 roadmap follows Git's object model because it solves the same problem: blobs (content-addressed nodes), trees (ordered ownership edges, many trees per node), commits (revisions with parents, transaction, actor, engines), refs (document and index heads with compare-and-swap), staging (validated TransactionPlan), packfiles and gc (content-addressed store and retention), remotes (pool sync, out of scope). Milestones 0-3 (blobs, trees, commits, persistence) are the first slice and are pure cljc except persistence; then first surface (Markdown CST to neutral AST with inverse-correct emitter and property tests), evidence and anchor states, stand-off layers, M and G with a Matrix adapter, retention and GC, and finally sync.
