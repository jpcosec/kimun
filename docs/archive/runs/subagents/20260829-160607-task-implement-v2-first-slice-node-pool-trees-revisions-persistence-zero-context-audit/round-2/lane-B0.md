# Lane B0 — executability of milestone 0 task (round 2)

Model: claude-haiku-4-5 · Explore (read-only) · fresh context · 2026-08-29

## Review

**Correct:** clear goal/scope (task:50-66); atoms bind pool, canonical bytes, hashing, .cljc, W_i (task:34-39); bb.edn runner with clojure.test + test.check verified executable on Babashka 1.13.219; deps.edn has test.check; `files:` lists 10 concrete artifacts; `bb test` runnable; Done When has three concrete criteria; Hasher target named; ritual/pill coherent.

- Note [high]: task:2 id `task-implement-v2-first-slice-node-pool-trees-revisions-persistence` vs task:42 title "milestone 0" — filename suggests milestones 1–3 are in scope — Would a blind executor be misled by the id?
- Note [medium]: task:78 "put/get idempotent" but no `test/sldb/kernel/pool_test.cljc` in files — separate file or merged into node_test?
- Note [medium]: task:78 golden fixture `test/fixtures/nodes.edn` — EDN structure unspecified — what shape?
- Note [medium]: docs/v2/02:63 NFC — library not named (java.text.Normalizer?) — which?
- Note [medium]: task:50 generators unspecified — all class/kind combinations or a sample?
- Note [low]: task:3 `status: draft` while current_node is execution-ready.

## Checklist
A pass · B pass · C pass · D pass · E partial (pool test file) · F pass · G pass · H partial (test structure per invariant) · I partial (fixture shape) · J pass · K partial (fixture shape, pool test location, NFC lib, generator coverage) · L pass · M partial (task id vs title)

## Verdict
not ready. Required fixes: (1) rename or annotate the task id; (2) fixture EDN shape; (3) pool test location; (4) NFC library; (5) generator coverage; (6) status draft vs phase.
