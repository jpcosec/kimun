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

Every edge that crosses layers (binding S-M, projection M-G) or targets an opaque or external region carries evidence: the referent's hash at creation time plus actor or engine, version, W_i context and timestamp. Mutation detection compares that evidence against the current revision and yields one of four states per anchor: intact (referent present, hash matches), superseded (replaced, supersedes edge exists), drifted (replaced without recorded succession, i.e. an external change) and orphan (referent in no tree). 'What is anchored in what' is a reverse index over these edges; drifted and orphan feed the autopoietic loop (G/M to S) as new tasks or questions.
