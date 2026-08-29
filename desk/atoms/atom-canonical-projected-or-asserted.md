---
id: atom-canonical-projected-or-asserted
title: Canonical, projected or asserted
five_wh_one_plus: when
tags:
- system:sldb
- epoch:v2
- domain:provenance
provenance: docs/v2/01-orden-filosofico.md
---

# Canonical, projected or asserted

## Answer

Determinism decides authority. Markup structure and UAX #29 boundaries are deterministic from the text and therefore canonical. Anything produced by an engine (syntactic parse, morphology, fact extraction, embeddings) is a projection: content-addressed by (input hash, engine, version), computed once, never recomputed on engine upgrade unless requested, and several engines may coexist over the same leaf. Anything a human or agent affirms (a semantic relation, a fact) is a transaction with an actor. Derived data has no authority and is rebuildable from the log and the pool.
