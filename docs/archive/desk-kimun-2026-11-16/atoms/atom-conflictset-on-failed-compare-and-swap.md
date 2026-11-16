---
id: atom-conflictset-on-failed-compare-and-swap
title: ConflictSet on failed compare-and-swap
five_wh_one_plus: how
tags:
- system:sldb
- epoch:v2
- domain:history
provenance: docs/v2/02-sustrato-computacional.md
---

# ConflictSet on failed compare-and-swap

## Answer

When the CAS on a head fails, the kernel computes the delta base->head and compares it with the plan. It is a conflict when both edit the child set of the same (tree, parent), or when the plan references a node that head replaced or removed. If the touched trees are disjoint, the plan is rebased on head automatically and the CAS retried. Otherwise a ConflictSet {:base :head :plan :conflicts [{:tree :node :kind same-parent-edit|superseded-target|removed-target}]} is returned to the author; there is no semantic merge in the first slice.
