# Lane A — coherence of docs/v2/04 (round 1)
Model: claude-haiku-4-5 · Explore · fresh context · 2026-08-29
Correct: CST lossless structure (inv. 8 implementable); AST/marks model; pool mapping; opaque preservation; canonical render principles; report shape.
- Note [high]: 04 §3 — marks vector [kind s e] vs sort key [s e kind]: which order?
- Note [high]: 04 §2 — list/quote continuation and closing by blank lines undefined
- Note [high]: 04 §2 — code fence closure: same char? same length? tilde vs backtick?
- Note [high]: 04 §5 vs §2-3 — unescape algorithm for parsing absent
- Note [high]: 04 §4 vs 02 §4 — marks stored in the block vs "deterministic inline layer": contradiction
- Note [high]: 04 §3 — inline parsing algorithm (marks extraction, crossing detection, admitted constructs) unspecified
- Note [high]: 04 §2 — HTML block start/close under-specified
- Note [high]: 04 §3/§4 — canonical :attrs per block type unspecified
- Note [medium]: ordered list :start derivation; code fence info string; coverage counting of escapes; grapheme algorithm
- Note [low]: 02 §2.1 example row lacks :marks; adjacent lists separator rule
Verdict: high=6 (8 listed) medium=3 low=2 — not startable.
