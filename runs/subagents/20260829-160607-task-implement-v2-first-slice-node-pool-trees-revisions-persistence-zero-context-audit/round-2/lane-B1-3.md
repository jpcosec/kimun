# Lane B1-3 — executability of milestone 1, 2, 3 tasks (round 2)

Model: claude-haiku-4-5 · Explore (read-only) · fresh context · 2026-08-29

## Task: task-implement-v2-milestone-1-trees-as-indexes-with-lazy-merkle
- Note [medium]: scope "dirty marking" — no atom/doc explains the dirty-path algorithm — can the executor implement dirty-path tracking?
- Note [medium]: test/fixtures/trees.edn — exact EDN structure unspecified
- Note [medium]: atom-tree-identity-tree-objects-and-heads describes heads/CAS while heads are out of scope — which parts apply to milestone 1?
- Note [low]: "tree descriptor" undefined; "structural sharing check" undefined
Checklist: A pass · B pass · C pass · D partial · E pass · F pass · G pass · H pass · I partial · J pass · K medium · L pass · M pass. Verdict: partial ready.

## Task: task-implement-v2-milestone-2-revisions-transactionplan-validation-cas-heads
- Note [high]: goal/scope include "diff between revisions" (02 §9 exit criterion) but Done When does not test it
- Note [medium]: which Revision fields does tx-001.edn `:expected` compare? (02 §5 lists :roots :parents :tx :actor :engines :timestamp :provenance)
- Note [medium]: "automatic rebase for disjoint trees" — no algorithm / EDN transformation described
- Note [medium]: capability check 5 with a stub allow-all actor — how are denial cases tested?
- Note [low]: atom-tree-identity redundant with atom-conflictset
Checklist: A pass · B pass · C pass · D pass · E pass · F pass · G pass · H partial · I partial · J pass · K medium · L pass · M pass. Verdict: not ready.

## Task: task-implement-v2-milestone-3-file-persistence-and-reload
- Note [medium]: "rebuild-indexes" in goal/scope but not in Done When — required or stub?
- Note [medium]: objects/<hash> file format — canonical-bytes EDN or raw bytes?
- Note [medium]: "replay" in scope but only implicitly tested via reopen
- Note [low]: log.edn line = TransactionPlan or Transaction {:id :plan :revision}?
Checklist: A pass · B pass · C pass · D pass · E pass · F pass · G pass · H partial · I partial · J pass · K medium · L pass · M pass. Verdict: partial ready.

## Coverage
Milestones 1 and 3 fully cover 02 §9 rows; milestone 2 misses "diff entre revisiones" in Done When. No overlaps.

## Totals
high=1 medium=10 low=3.
