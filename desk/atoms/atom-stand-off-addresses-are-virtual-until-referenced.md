---
id: atom-stand-off-addresses-are-virtual-until-referenced
title: Stand-off addresses are virtual until referenced
five_wh_one_plus: how
tags:
- system:sldb
- epoch:v2
- domain:text-structure
provenance: docs/v2/02-sustrato-computacional.md
---

# Stand-off addresses are virtual until referenced

## Answer

An address is [leaf-id start end] in UAX #29 grapheme offsets over the leaf's NFC text and resolves without any node. A :span node {:leaf :range} is materialized only when an edge needs an endpoint there; being content-addressed, the same span requested twice is the same node. Deterministic layers (graphemes, words, sentences) are offset arrays cached by leaf id, not nodes. When the leaf changes id, spans over the old leaf follow the succession rules (superseded or drifted).
