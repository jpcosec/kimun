---
id: atom-transactionplan-edn-schema-and-validation
title: TransactionPlan EDN schema and validation
five_wh_one_plus: how
tags:
- system:sldb
- epoch:v2
- domain:ast-core
provenance: docs/v2/02-sustrato-computacional.md
---

# TransactionPlan EDN schema and validation

## Answer

A plan is pure EDN data: {:plan/version 1 :base <rev|nil> :actor <str> :engines {...} :ops [...]} with ops :new-tree, :add-node, :add-edge, :remove-edge, :replace {:tree :old :new}, :move {:tree :node :parent :order}; ops may declare :as aliases resolved inside the plan; :span nodes are ordinary nodes the plan adds itself. The seven validation checks, all-or-nothing: (1) base-cas: :base equals the current head of every touched tree (touched = created, or with ownership changes; other edge types live in the revision edge-set and merge by union on rebase); (2) ids-exist: every referenced id exists in the base pool or is created in the plan; (3) tree-integrity: every touched tree remains a tree (one parent, no cycles, dense unique sibling order); (4) evidence-ref-hash: mandatory evidence present and :ref-hash equals the id of :to in the plan-resolved state; (5) capability: {:op x} authorizes x in any tree, {:op x :tree t} only in t; ops carrying :tree (:new-tree, :replace, :move, ownership :add-edge/:remove-edge) are authorized per tree, :add-node and non-ownership edges with {:op x} alone, :all authorizes everything, an actor absent from the store capabilities map is rejected; (6) opaque-replace-only: an :opaque node changes only by whole :replace (edges to or from it are unrestricted); (7) pure-data: no functions or I/O. Adding an edge whose id already exists is a no-op. Result: Transaction {:id H(resolved-plan) :plan :revision} plus the alias map and the ids of created edges.
