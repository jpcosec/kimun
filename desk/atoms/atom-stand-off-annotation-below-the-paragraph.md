---
id: atom-stand-off-annotation-below-the-paragraph
title: Stand-off annotation below the paragraph
five_wh_one_plus: how
tags:
- system:sldb
- epoch:v2
- domain:text-structure
provenance: docs/v2/02-sustrato-computacional.md
---

# Stand-off annotation below the paragraph

## Answer

Below the paragraph the markup tree stops being the single hierarchy: sentences, syntax and mentions overlap inline markup (an emphasis can span two sentences). The canonical leaf is therefore the paragraph's plain text with grapheme offsets, and everything else is a typed span layer over it: inline markup and Unicode UAX #29 grapheme, word and sentence boundaries (deterministic, derived on demand and cached by leaf hash), syntax and morphology (engine projections keyed by leaf hash, engine and version), mentions (bindings to symbols). An address down to a symbol exists as (leaf hash, offset) without a materialized node; the primitive anchor (leaf, range, hash) unifies sentence, phrase, mention, fact argument and opaque region.
