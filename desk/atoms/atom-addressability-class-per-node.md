---
id: atom-addressability-class-per-node
title: Addressability class per node
five_wh_one_plus: what
tags:
- system:sldb
- epoch:v2
- domain:addressability
provenance: docs/v2/01-orden-filosofico.md
---

# Addressability class per node

## Answer

Each node declares one addressability class instead of the document family deciding reversibility. structural: canonical, idempotent, editable by transaction, anchored by node identity. opaque: preserved but not modeled (unknown HTML block, undeclared extension syntax, inline HTML); anchored by sample plus hash over the region exactly like a PDF; a transaction may only replace the whole blob. external: content outside the store, anchored with locator, sample and fingerprint. Reversible and non-reversible families become per-family defaults over this per-node classification, and a document can report its addressable coverage and its opaque regions.
