# Lane B — executability of task-milestone-4-markdown-cst-to-neutral-ast (round 1)
Model: claude-haiku-4-5 · Explore · fresh context · 2026-08-29

## Review
Correct: scope, atoms, ring 2 already in check_rings.clj, kernel green, Done When explicit with invariants and fixtures.
- Note [high]: 04:143 — TextSegmenter `(graphemes [this s])` return type undefined (positions? count? strings?)
- Note [high]: promises.md:105 — invariants 8/9 "deferred"; which rows/test names to add?
- Note [high]: 04:82-83 — store->ast has no signature or algorithm (how are marks/opaque reconstructed?)
- Note [medium]: 04:134-135 / Done When — canonical form for gen-ast not formalized (empty marks, fence length, mark ordering)
- Note [medium]: 04:112-115 — "marcas cruzadas" and inline-HTML detection: exact algorithm?
- Note [low]: first file to create not stated; profile.md coverage not enumerated
## Checklist
A ✓ B ✓ C ✓ D ✓ E ⚠ F ✓ G ✓ H ✓ I ⚠ J ✓ K ✗ L ✓ M ⚠
## Verdict
not ready. Fixes: (1) TextSegmenter signature/return; (2) promises.md rows for inv. 8/9; (3) store->ast subsection; (4) canonical-form rules for gen-ast.
