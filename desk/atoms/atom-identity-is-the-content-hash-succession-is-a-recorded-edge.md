---
id: atom-identity-is-the-content-hash-succession-is-a-recorded-edge
title: Identity is the content hash; succession is a recorded edge
five_wh_one_plus: how
tags:
- system:sldb
- epoch:v2
- domain:ast-core
provenance: docs/v2/02-sustrato-computacional.md
---

# Identity is the content hash; succession is a recorded edge

## Answer

Node identity is trivial and exact: same canonical content, same id, in any tree and any revision. Identity across edits is not identity but succession: the transaction that replaces X by X' records a supersedes edge because it knows it did so. Edges hanging from X are re-anchored to X' by explicit per-type rule or flagged for review. Heuristic reconciliation (by sample and position) is used only for changes made outside the kernel, and its result is marked drifted with a confidence value, never presented as identity.
