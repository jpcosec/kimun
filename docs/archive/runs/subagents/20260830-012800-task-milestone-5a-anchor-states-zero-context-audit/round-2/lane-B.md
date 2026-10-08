# Lane B — executability, round 2 (corrected bundle)
Model: claude-haiku-4-5, fresh context, read-only.

## Checklist A–M

A pass · B pass · **C fail** · D pass · E pass · F pass · G pass · H pass · I pass ·
J partial · **K fail** · L pass · M pass.

## Findings

Note [high] task files section — `docs/v2/tests/promises.md` said to be missing from the
task's `files:` while required by done-when.
→ **Answered by the bundle**: the live task lists it (`desk/tasks/task-milestone-5a-anchor-states.md`,
  `files:` last entry). The lane read the run directory's `task.txt`, which was snapshotted
  before the round-1 corrections. Process defect, not a bundle defect: the snapshot is
  refreshed for round 3.

Note [high] src/sldb/kernel/revision.cljc:112-132 — the task says "add-edge-op re-anchors a
new supersedes" without saying where, how "new" is detected, whether `:superseded` is
recorded, or in what order. → **Fixed**: docs/v2/02 §6.1 settles all four, and the task's
implementation-path now states them literally (old = `:to`, new = `:from`, pair recorded,
re-anchoring after the edge joins the active set, never in the idempotent branch).

Note [high] src/sldb/kernel/node.cljc:50-59 — how `validate` obtains the algorithm, the
exact check and the error are said to be unspecified. → **Answered by the bundle**: §6.4
names `node/validate`, `(algorithm (hasher host))` and `:node/invalid`. The
implementation-path now repeats it so the task alone suffices.

Note [medium] docs/v2/02 §6.2 (succession chain) — "a cycle cuts the chain" does not say
what `:latest` and `:ambiguous?` become. → **Real gap. Fixed in §6.2.**

Note [medium] docs/v2/02 §6.3 (`anchor/diff`) — `:from-state` / `:to-state` do not say
whether they are keywords or full state maps, and their names collide with the `:from` and
`:to` endpoints. → **Real gap. Fixed in §6.3** (renamed and typed).

Note [medium] docs/v2/02 §6.3 (positions order) and §6.2 (`derived` with a superseded
endpoint) — → **Answered by the bundle**: §6.3 fixes the traversal order explicitly and
§6.2 states that the worst-of rule has no exception.

## Verdict

Lane B verdict: not ready — on two real gaps (cycle semantics, the shape and naming of
`anchor/diff`'s `:changed`) plus one process defect (a stale task snapshot in the run
directory). Everything else it raised is settled by text it did not reach.
