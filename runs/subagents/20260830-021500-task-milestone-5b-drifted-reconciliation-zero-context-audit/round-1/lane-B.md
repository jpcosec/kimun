# Lane B — executability of task-milestone-5b-drifted-reconciliation (round 1)
Model: claude-haiku-4-5, fresh context, read-only.

## Checklist A–M

A pass · B pass · C pass · **D partial** · **E partial** · F pass · G pass · H pass ·
I pass · **J partial** · **K fail** · L pass · **M partial**.

## Findings

Note [high] docs/v2/04 §8 vs the task — `:markdown/root-changed` is required by the task
and absent from the spec, and it reads as a contradiction: §8 states as a fact that the
root never changes in this profile, while the task demands a runtime check.
→ **Real. Fixed in 04 §8**: the profile makes the root invariant *and* the function
verifies it, because re-rooting would need a `:replace`, and a `:replace` records a
succession that the surface has no right to invent. The check is what keeps
`update-plan` honest about being an external edit.

Note [high] docs/v2/02 §6.5 — `:reconcile/unknown-node` is required by the task and defined
nowhere. → **Real. Fixed in §6.5**: `accept-plan` verifies both ends of the proposal are in
the pool and raises it otherwise.

Note [medium] task vs promises.md — the task requires a named test per promise of §6.5
without enumerating the promises. → **Fixed**: the implementation path now says one row per
normative claim of §6.5 plus the §9 row 5b and 04 §8 rows.

Note [medium] src/sldb/surface/markdown/plan.cljc — "reusing the existing `collect`" does
not say whether `collect` is called or copied, whether it becomes public, or whether
`ast->plan` changes. → **Fixed**: `collect` stays private and is called; `ast->plan`,
`store->ast` and `store->markdown` are untouched; only two public functions are added.

Note [medium] task — `ast->update-plan`'s signature is inferred from prose rather than
stated. → **Fixed**: stated literally in the implementation path.

Note [low] docs/v2/02 §6.5 — the order of the proposals when `:all?` is true is not stated.
→ **Fixed**: the same global order applies, and `:all?` only changes what is kept.

## Verdict

Lane B verdict: not ready — two error types the task demanded had no normative home (one of
them reading as a contradiction with §8), the promise enumeration was implicit, and the
change to `plan.cljc` did not say what must stay untouched. All six are corrected.
