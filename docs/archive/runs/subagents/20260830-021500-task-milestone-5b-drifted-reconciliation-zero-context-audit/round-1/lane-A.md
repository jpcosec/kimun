# Lane A — coherence of §6.5 and 04 §8 against the 5a code (round 1)
Model: claude-haiku-4-5, fresh context, read-only.

## Findings

Note [medium] docs/v2/02 §6.5 vs §6.2 and src/sldb/kernel/anchor.cljc — §6.5 says the input
is "the orphans of `anchor/report`", but `report` returns **edge** ids while the three
methods are defined over **nodes**; and when both endpoints of an anchor are orphan the
spec does not say which one is reconciled, or whether both are. Two implementers produce
two different outputs. → **Real. Fixed in §6.5**: the input is `reconcile/orphans`, an
index `{node-id [edge-id …]}` over **every** orphan endpoint of every anchor, and both
endpoints of an edge with two orphans are reconciled independently.

Note [low] docs/v2/02 §6.5 `:position` vs §6.2 — `:position` speaks of the path a node
occupied, but symbols and facts never occupy positions in the first slice; the spec does
not say whether the method skips them or errors. → **Fixed**: it yields no candidate, and
never an error.

Note [none, verified] — the Dice definition is total, deterministic and portable (integer
numerator and denominator, one IEEE-754 division, grapheme segmentation through the
`TextSegmenter` port, no reader conditional, which `scripts/check_rings.clj` forbids in
the kernel).

Note [none, verified] — `accept-plan`'s output is a plan `revision/apply-plan` accepts:
`:supersedes` requires exactly `:actor` as evidence (`edge.cljc`), and adding it fires the
second re-anchoring trigger implemented in `add-edge-op`, which produces the state §6.5
claims.

Note [none, verified] — "outside the pool" agrees with invariant 18 and §6.2.

Note [none, verified] — `markdown->update-plan` is expressible with the existing ops:
repeated `:detach [0]` empties the root because siblings shift, and `collect` already emits
nodes and positions in an order that adds a parent before descending into it.

Note [none, verified] — nothing contradicts §9's exit criterion for row 5b or 01 §8.

## Verdict

§6.5 and the 04 §8 bullet are coherent and implementable against the 5a code. One real
medium (the input shape of the reconciliation, and the two-orphan case) and one low (the
`:position` method on a non-sign endpoint); everything else the lane probed came back
verified against the code rather than merely asserted.
