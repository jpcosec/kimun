# Milestone 6 stand-off below the paragraph

ID: task-milestone-6-stand-off-below-the-paragraph
Status: deferred
Priority: medium

## Goal

Triage and resolve the inbox message promoted from `desk/inbox/20260830-022207-suggestion-milestone-6-stand-off-below-the-paragraph.md`.

## Scope

Next roadmap milestone after 5b (docs/v2/02 section 9 row 6, spec sections 4 and 4.1). Leaves with grapheme offsets, the deterministic UAX #29 layers (graphemes, words, sentences) derived on demand and cached by leaf id rather than materialized as nodes, and the primitive anchor (leaf, range, hash) addressing down to the grapheme without a node existing. Everything it needs is already in place - the TextSegmenter port, the :span node kind, and the rule of section 6.2 by which a span inherits the ground of its leaf and its range is checked against the leaf grapheme count. Exit criterion of row 6: an address down to the grapheme with no materialized nodes.

## Source

- `desk/inbox/20260830-022207-suggestion-milestone-6-stand-off-below-the-paragraph.md`

## Done When

- The message is resolved, answered, or promoted into active work.
