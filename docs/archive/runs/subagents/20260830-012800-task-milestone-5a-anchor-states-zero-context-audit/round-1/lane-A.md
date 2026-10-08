# Lane A — internal coherence of docs/v2 §6 (round 1)
Model: claude-haiku-4-5, fresh context, read-only.

## Findings

Note [high] docs/v2/02:436-462 (§6.1) vs src/sldb/kernel/revision.cljc:112-132 — §6.1
demands two re-anchoring triggers; `add-edge-op` implements neither the `re-anchor` call
nor the `:superseded` entry for a new `:supersedes` edge (only `replace-op` does, l.175).
The idempotent no-op branch at l.126 correctly skips re-anchoring.
→ Not a spec defect: this is exactly the change the task scopes. Recorded as the
  acceptance condition for `revision.cljc`.

Note [high] src/sldb/kernel/anchor.cljc does not exist — the six §6.3 queries have no
implementation. Signatures are unambiguous and must be matched exactly.
→ Not a spec defect: that namespace is the deliverable.

Note [medium] src/sldb/kernel/node.cljc:17 — the `:external` predicate checks only
`(string? (:fingerprint c))`; §6.4 demands the `<alg>:<hex>` form with `alg` equal to the
store algorithm and rejection as `:node/invalid` otherwise. → in scope, must be added.

Note [medium] test/fixtures/nodes.edn — the frozen external fixture carries a bare 64-hex
fingerprint, which the §6.4 validation will reject; its frozen id therefore changes.
→ real consequence: fixture, generator and `node_test` must be updated and the new frozen
  id re-verified by the independent Python oracle.

Note [medium] docs/v2/02 §6.3:527 — `anchored-in` shows `:as-from [state …]` and
`:as-to [state …]` without saying what a `state` is there or in what order.
→ spec gap. Fixed in triage.

Note [low] docs/v2/02 §6.3:540-541 — the two error types are named but not attached to
particular queries, nor is it said whether they are raised or returned.
→ spec gap. Fixed in triage.

## Verdict

§6.1–§6.5 are internally coherent, coherent with §2.1, §3.1, §3.2, §5, §9 and §10, and
coherent with the epoch:v2 atoms; invariant 18 agrees with §6 and with 01 §8. The two
high findings are the absence of the implementation itself, which is what the task is
for. Two real spec gaps remain, both in §6.3 and both about the shape of a return value.
