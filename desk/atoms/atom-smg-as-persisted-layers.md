---
id: atom-smg-as-persisted-layers
title: SMG as persisted layers
five_wh_one_plus: what
tags:
- system:sldb
- epoch:v2
- domain:boundaries
provenance: docs/v2/01-orden-filosofico.md
---

# SMG as persisted layers

## Answer

S, M and G stop being an in-memory IR (v1 smg-ir) or separate stores (SLDB=S, KGDB=G per the 2026-06-19 ADR) and become the persistence model itself: three node classes and three index families over one pool, joined by binding (S to M) and projection (M to G) edges that carry evidence. A G index such as a Matrix W_i is another tree over the same pool.

SUPERADO — ver `docs/v2/14 §19` y `docs/v2/12 §1`: la estructura es una caché verificable (decisión 21) y el grafo se separa en un paquete aparte (decisión 43); el pool no es el modelo de persistencia único.
