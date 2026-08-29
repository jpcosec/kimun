# Result summary — executor lane (after two tester lanes)

- task: task-harden-the-first-slice-test-suite-against-every-spec-promise
- tester evidence: runs/subagents/20260829-174447-…-testing/ (brief, lane-1, lane-2): 119 promises enumerated, 80 OK / 30 WEAK / 21 UNTESTED before this task
- session: https://claude.ai/code/session_01Fy6NkZNuvnLh1SaFeNpvFU

## Delivered
- docs/v2/tests/promises.md — promise → test table for §§2–8.1 and invariants 1–17; 0 first-slice promises without a test; milestone 4+ promises marked deferred with their drawer task
- test/sldb/kernel/hardening_test.cljc — 22 tests / 61 assertions: unicode beyond latin, empty/whitespace text, extreme integers, extra content and evidence keys, literals, tree boundaries (single node, order = count, duplicate child, root replace, 200-level path), empty plan, alias misuse, ownership edge by id, cycle move, :move dirty, multi-context bindings, span materialization, timestamp in revision id, heads per touched tree, replace in one of several trees, chained replace, semantic/projection/derived rows, :superseded-target/:removed-target, remove-edge already removed, edge-set union on rebase, 6 concurrent commits, store failure modes (descriptor fields, missing object, corrupt log line, hash-alg mismatch)
- scripts/canon_oracle.py + `bb oracle` — independent Python canonical-bytes + SHA-256 over test/fixtures/nodes.edn: 9/9 ids match (conformance no longer circular)

## Kernel bugs found and fixed by the new tests
1. apply-plan executed ops before check 1, so a plan referencing a node that head replaced/removed was rejected as ids-exist/tree-integrity instead of yielding the promised ConflictSet — check-base now runs first on raw ops.
2. ConflictSet kind precedence: a replaced node was reported :removed-target; :superseded-target now wins.
3. heads/commit! retried a fixed plan (stale :base) only 3 times — now accepts a plan-building function and retries up to max-attempts (32); ConflictSet carries the built plan.
4. fs-store read-log: a corrupt line surfaced as a raw reader exception — now :store/corrupt-log with the line number.

## Validation
- bb lint ok · bb test: 80 tests, 240 assertions, 0 failures, stable ×3 · bb oracle 9/9
