---
id: atom-re-anchoring-rules-under-succession
title: Re-anchoring rules under succession
five_wh_one_plus: how
tags:
- system:sldb
- epoch:v2
- domain:anchors
provenance: docs/v2/02-sustrato-computacional.md
---

# Re-anchoring rules under succession

## Answer

When a successor node replaces X: ownership edges are rewritten by the replacing transaction itself; reference and binding edges that have X at either endpoint follow the successor automatically (a new edge with the successor in place of X, and :ref-hash equal to the successor when X was the :to; the old edge stays as superseded, never deleted); semantic edges are not followed and stay superseded for human or agent review because meaning may have changed; projection edges stay superseded pending revalidation of the fact sense status; derived edges are invalidated and recomputed on demand; supersedes edges chain, and re-anchoring always targets the direct successor at replace time. For an external change (drifted) heuristic reconciliation proposes a successor with a confidence outside the pool; once accepted, supersedes is recorded and the same rules apply.
