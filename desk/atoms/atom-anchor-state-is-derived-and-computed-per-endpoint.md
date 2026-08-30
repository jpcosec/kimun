---
id: atom-anchor-state-is-derived-and-computed-per-endpoint
title: Anchor state is derived and computed per endpoint
five_wh_one_plus: how
tags:
- system:sldb
- epoch:v2
- domain:anchors
provenance: docs/v2/02-sustrato-computacional.md
---

# Anchor state is derived and computed per endpoint

## Answer

An anchor is an active reference, binding, projection, semantic or derived edge in a revision; ownership is structure and supersedes is the succession record, so neither is an anchor. The state is computed per endpoint and then combined with the order intact < superseded < orphan, because the re-anchoring rules follow a successor at either end. An endpoint is superseded when at least one active supersedes edge targets it (two actors may name different successors, which makes the chain ambiguous rather than invalid), intact when it still resolves, and orphan otherwise. Resolving means occupying a position in some tree of the revision for ordinary signs, recursing to the leaf and checking the range against its grapheme count for spans, and merely existing in the CAS for symbols and facts, which is a first-slice concession the milestone 7 tightens to membership of a context tree. Every anchor query is derived — recomputed from revision plus pool, never persisted, and reproducible from the log.
