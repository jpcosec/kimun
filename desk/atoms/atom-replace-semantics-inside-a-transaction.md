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

For :replace {:tree t :old X :new X'}: X' takes the exact :order X had under the same parent in t and inherits X's subtree unless the plan moves it; a supersedes edge X' -> X is created with the plan actor; the re-anchoring rules are applied inside the same transaction (reference and binding edges to X are duplicated toward X' with fresh evidence and the originals marked superseded; semantic and projection edges stay superseded without following; derived edges are invalidated); other trees containing X are untouched because succession is per node while structural replacement is per tree. Capabilities in the first slice: the store descriptor maps actor -> :all or a set of {:op :tree}; an actor absent from the map is rejected.
