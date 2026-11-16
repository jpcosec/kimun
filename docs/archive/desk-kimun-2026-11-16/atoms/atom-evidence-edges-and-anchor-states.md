---
id: atom-evidence-edges-and-anchor-states
title: Evidence edges and anchor states
five_wh_one_plus: how
tags:
- system:sldb
- epoch:v2
- domain:anchors
provenance: docs/v2/02-sustrato-computacional.md
---

# Evidence edges and anchor states

## Answer

Every edge that crosses layers (binding S to M, projection M to G) or targets an opaque or external region carries evidence — the referent hash at creation time plus actor or engine and version and the W_i context. The timestamp is never part of an edge; it belongs to the transaction that created it. Mutation detection compares each anchor against a revision and yields a deterministic state per endpoint, combined into the worst of the two — intact when the referent still resolves, superseded when an active supersedes edge names a successor, orphan when it no longer resolves and no succession was recorded. Drifted is a refinement of orphan produced by reconciliation, not a fourth parallel state. What is anchored in what is a reverse index over these edges, and orphan and drifted feed the autopoietic loop from G and M back to S as new tasks or questions.
