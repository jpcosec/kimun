---
id: atom-evidence-required-per-edge-type
title: Evidence required per edge type
five_wh_one_plus: what
tags:
- system:sldb
- epoch:v2
- domain:anchors
provenance: docs/v2/02-sustrato-computacional.md
---

# Evidence required per edge type

## Answer

Edge = {:type :from :to :tree :order :evidence}; Evidence = {:ref-hash, exactly one origin (:actor or :engine+:version), :context, :status}. Edge id = H(canonical-bytes(Edge)); the timestamp is NOT part of the edge (it is recorded in the transaction), so the same assertion by the same origin is the same edge (idempotent) and :remove-edge <edge-id> is deterministic. Required Evidence fields: ownership none (represented by tree objects); supersedes :actor (taken from the plan actor); reference :ref-hash + origin; binding :ref-hash + origin + :context; projection adds :status sinnvoll|sinnlos|unsinnig; semantic :ref-hash + origin + :context; derived :ref-hash + :engine + :version. :ref-hash is the id of :to at creation time and is what mutation detection compares; :context is the id of the :fact/:context node (W_i); one binding per W_i.

PENDIENTE — ver docs/v2/14 §21 y docs/v2/drafts/preguntas-abiertas.md: el id de arista vuelve a la identidad, no al hash; el timestamp fuera del id y la idempotencia se conservan como reglas de deduplicación (la decisión 46 revisa la decisión 3).
