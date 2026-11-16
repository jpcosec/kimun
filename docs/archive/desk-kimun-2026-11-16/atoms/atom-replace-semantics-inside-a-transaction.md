---
id: atom-replace-semantics-inside-a-transaction
title: Replace semantics inside a transaction
five_wh_one_plus: how
tags:
- system:sldb
- epoch:v2
- domain:ast-core
provenance: docs/v2/02-sustrato-computacional.md
---

# Replace semantics inside a transaction

## Answer

For :replace {:tree t :at p :new X-prime} with X = the node at position p: X-prime takes exactly position p (same parent, same order) and inherits its subtree unless the plan moves it; other occurrences of X in t are untouched because replacement is per position; a supersedes edge X-prime -> X is created with the plan actor; the re-anchoring rules are applied inside the same transaction (reference and binding edges with X at either endpoint are duplicated toward X-prime with fresh evidence and the originals stay superseded; semantic and projection edges stay superseded without following; derived edges are invalidated); other trees containing X are untouched. Capabilities in the first slice: the store descriptor maps actor -> :all or a set of {:op :tree}; an actor absent from the map is rejected.
