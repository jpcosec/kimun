# Lane A — internal coherence (round 4)
Model: claude-haiku-4-5 · Explore · fresh context · 2026-08-29

## Review
Correct: node identity (§2.1); ownership tree structure (§3, §3.1); Revision eight fields (§5); timestamp out of edge id, in revision id; supersedes only via :replace; re-anchoring rules; evidence per edge type; dirty-set Merkle; stand-off; capabilities per op.
- Note [high]: 02 §5.1:301-302 — ownership `:add-edge` ops carry `:tree` while check 5 says :add-edge carries no tree — are ownership edge ops authorized globally or per tree?
- Note [high]: 02 §2:51 vs §3.2:196 — "un binding por W_i" but nothing forbids duplicate (sign, symbol, W_i) — should validation forbid duplicates?
- Note [medium]: 02 §6:403 — drifted "confianza" never defined as a field
- Note [medium]: 02 §5 vs §8.1 — `:engines {}`: none or inherited from base?
- Note [medium]: 02 §2:88, §6:403 — `:sample` and `:fingerprint` formats undefined
- Note [medium]: 02 §2.1:71-73 — float-as-string format unspecified
- Note [medium]: 02 §5.1 rule 6 — does "only :replace" forbid removing edges that point at an opaque node?
- Note [medium]: 02 §2:46 — W_i defined by forward reference to Matrix
- Note [medium]: 02 §3.1:162-163 — reachability wording recursive; termination/cycles
- Note [medium]: 02 §4.1 vs §5.1 check 4 — when are :span nodes materialized relative to validation?
- Note [medium]: 02 §8.1:460 — timestamp precision affects revision id reproducibility
- Note [low]: "transclusion" undefined; "escalares" wording; rebase success condition; "touched tree" in check 1
## Verdict
Counts: high=1 (2 listed) medium=8 low=4. Milestones 0, 1, 3 can start; milestone 2 needs the capability and binding-uniqueness answers.
