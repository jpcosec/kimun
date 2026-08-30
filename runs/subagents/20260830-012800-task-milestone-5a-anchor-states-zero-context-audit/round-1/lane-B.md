# Lane B — executability of task-milestone-5a-anchor-states (round 1)
Model: claude-haiku-4-5, fresh context, read-only.

## Checklist A–M

A goal · pass. B scope · pass. C files · pass. D functions · pass. E traceability · pass.
F validation · pass. G done-when · pass. H standards · pass. I ordering · pass.
J test names · pass. **K changes to existing files · fail.** L no 5b dependency · pass.
M no internal contradiction · pass.

## Decisions the implementer would be forced to invent

Note [high] docs/v2/02 §6.1 vs src/sldb/kernel/revision.cljc:112-132 — for the `:add-edge`
trigger the bundle does not fix: whether re-anchoring runs before or after the edge joins
the active set; which endpoint is `old` and which is `new`; whether the idempotent branch
re-anchors; the shape of the `:superseded` entry.

Note [high] docs/v2/02 §6.3 — the two error types are named but it is not said whether
they are raised or returned, from which query, or with what data.

Note [high] docs/v2/02 §6.3 — `:positions` has no stated order and no statement of
completeness (all trees of the revision? only some?).

Note [high] docs/v2/02 §6.3 — `:as-from`/`:as-to` have no stated order and no statement
about an edge whose two endpoints are the same node.

Note [high] docs/v2/02 §6.3 — "paths por orden lexicográfico de vectores" does not name a
comparator; Clojure's `compare` on vectors is length-first, not lexicographic, so the
sentence as written is not implementable without choosing.

Note [medium] docs/v2/02 §6.2 — `:successors` is defined as a set; the return shape does
not say it is deduplicated, ordered, or `[]` when empty.

Note [medium] docs/v2/02 §6.2 — the remark about `derived` edges reads like a rule; it is
not said which wins if a `derived` anchor does have a superseded endpoint.

Note [medium] src/sldb/kernel/node.cljc:16-17 — §6.4 does not say where the fingerprint
check lives, nor confirm the error type, nor that `alg` must equal the store algorithm.

Note [low, rejected in triage] — the lane also demanded the spec fix indexing, caching and
laziness strategy for successors and positions. Those have no observable effect on the
specified return values and are the implementer's choice; the triage answers this by
saying so in §6.3 rather than by specifying an algorithm.

## Verdict

Lane B verdict: not ready — the bundle is normative about *what* and silent about several
*observable* details of *how* (re-anchoring order and pair shape, error contract, sort
orders, empty/duplicate cases, where the fingerprint check lives).
