---
id: atom-inline-marks-live-on-the-block-text-leaves-stay-plain
title: Inline marks live on the block, text leaves stay plain
five_wh_one_plus: how
tags:
- system:sldb
- epoch:v2
- domain:text-structure
provenance: docs/v2/04-superficie-markdown.md
---

# Inline marks live on the block, text leaves stay plain

## Answer

A heading or paragraph maps to a :sign/:block node whose :attrs carry :marks, a vector of [kind start end] (or [:link start end url]) spans in grapheme offsets over the block's single :sign/:text child, which holds the plain NFC text without markup. Spans must be nested or disjoint and sorted by [start end kind]; otherwise the paragraph degrades to an opaque node. This keeps text leaves shareable across documents (same text, same node) while the markup differs on the block, matching the one-symbol-many-signs principle.
