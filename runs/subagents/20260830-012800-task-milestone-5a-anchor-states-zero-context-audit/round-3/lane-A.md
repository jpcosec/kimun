# Lane A — internal coherence, round 3 (corrected bundle)
Model: claude-haiku-4-5, fresh context, read-only. The prompt stated explicitly that the
absence of the implementation is not a finding.

## Findings

Note [medium] docs/v2/02 §6.1:451-468 vs §5 — §6.1 says both `:replace` and a new
`:supersedes` `:add-edge` re-anchor, but §5, where the primitive ops are listed, never
mentions it; someone reading §5 alone would implement re-anchoring only for `:replace`.
→ **Real. Fixed**: §5 now names the two ops that re-anchor and says no other one does.

Note [low] docs/v2/02 §6.3:551-573 — the return shape shows `:as-from [state …]` (a
vector) while the prose calls them "mapas". → **Real. Fixed**: they are vectors of
`anchor/state` maps, and the prose says so.

Note [low] docs/v2/02 §6.3:563-573 — "ordenados por id de arista" does not say ascending.
→ **Fixed**: ascending, said in both places.

## Verdict

§6 is internally coherent on its core concepts: states (§6.2), query shapes (§6.3),
external fingerprints (§6.4) and reconciliation (§6.5) hang together, and no invariant of
§10 and no epoch:v2 atom contradicts it. None of the three findings is a contradiction in
the strict sense; all three are wording that leaves room for two readings, and all three
are corrected. No high finding.
