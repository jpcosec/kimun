---
id: atom-smg-node-pool
title: SMG node pool
five_wh_one_plus: what
tags:
- system:sldb
- epoch:v2
- domain:ast-core
provenance: docs/v2/02-sustrato-computacional.md
---

# SMG node pool

## Answer

SLDB v2 persists one pool of content-addressed, immutable nodes of three classes: signs (S: text leaves, CST blocks, opaque regions, external anchors), symbols (M: propositions in canonical logical form) and facts (G: validated subject-predicate-object triples in a W_i context). A node's id is the hash of its canonical content, so the node is the symbol, not its occurrence: 'the sky is blue' is one node that documents A, B, C and Z reference. S, M and G share the pool; they are node classes and index families, not separate stores.
