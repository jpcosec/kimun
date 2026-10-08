# Lane A — coherence of §6.5 and 04 §8, round 2
Model: claude-haiku-4-5, fresh context, read-only.

## Findings

Note [medium] docs/v2/02 §6.5 `:position` — when the orphan occupied **several** paths in
the base revision and different paths are now held by different candidates of the same
class and kind, the spec says "X ocupaba el path `p`" in the singular and "un candidato
por (old, método)" without saying which path decides, nor which occupant decides the
confidence. Two implementers rank differently. → **Real. Fixed**: the paths are examined in
the order `:positions` already fixes (trees by ascending id, paths in document order) and
the first that yields a candidate wins; its own presence in that tree at `:base` decides
`1.0` or `0.9`.

Note [medium→low] docs/v2/02 §6.5 `dice` — the formula divides by zero when both texts are
empty, so the `1.0` case is a special case in code, and "aporta un único gramo" needs the
reading "one element, the whole text". → Not an ambiguity: both are stated in the spec and
the lane reached the same reading. Kept as written.

Note [low] docs/v2/04 §8 — repeated `:detach [0]` and the root verification are clear and
implementable; the lane confirmed the mechanism (each detach shifts the next sibling down)
and that both roots must hash equal by invariant 2.

Everything else audited clean and **verified against the code**: §6.2 resolution and
succession against `anchor.cljc`; §6.1 and its idempotent no-op against `revision.cljc`
lines 112–134; invariant 18; that only `:actor` reaches the `supersedes` edge; that nothing
demands a capability the existing structures cannot express.

## Verdict

Coherent, with one legitimate ambiguity in `:position` over multiple base positions. No
high finding.
