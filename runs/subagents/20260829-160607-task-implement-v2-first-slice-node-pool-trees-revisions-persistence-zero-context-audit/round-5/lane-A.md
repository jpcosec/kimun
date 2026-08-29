# Lane A — internal coherence (round 5, final confirmation)
Model: claude-haiku-4-5 · Explore · fresh context · 2026-08-29

## Review
Correct: node hashing and canonical-bytes (§2.1); dirty set and lazy Merkle (§3.1); timestamp out of edge id, in revision id; evidence per edge type (§3.2); W_i as node and tree (§2); re-anchoring rules (§6.1); span materialization by the plan (§4.1); tree objects and merkle-root (§3.1); seven validation checks (§5.1); rebase and ConflictSet (§5.2).
- Note [medium]: 02:78,89 vs §5.1 — is a non-NFC string in a plan rejected, normalized, or accepted?
- Note [medium]: 02:297 — can `:remove-edge` reference an edge created earlier in the same plan (alias) or must the id be pre-computed?
- Note [low]: `:move` and the dirty set not explicitly confirmed
- Note [low]: are tree-integrity checks per op or after all ops?
- Note [low]: which bytes `:fingerprint` hashes per external `:kind` (out of milestones 0–3)
## Verdict
Counts: high=0 medium=2 low=2 (3 low lines listed). Milestones 0, 1, 3 can start without improvising; milestone 2 with one improvisation point (edge-id in-plan), now answered.
