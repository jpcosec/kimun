# K2a C1 — pure tracked-route Merkle

Commit: `feat(route): add pure tracked-route Merkle`.

## Scope

Only the pure route primitive and its tests:

- `src/kimun/kernel/route.cljc`
- `test/kimun/kernel/route_test.cljc`
- `test/fixtures/kernel/route-entries.edn`

No store, revision, include, K6 or S1 file is changed. No identity is invented:
`:tree-id` is carried as a locator and deliberately excluded from the route root.

## Validation

- `lint.txt`: `bb lint` via `~/bin/memcap 3` — 103 files, rings/docstrings ok.
- `targeted-tests.txt`: `kimun.kernel.route-test` — 6 tests, 29 assertions, 0 failures.
- `full-test.txt`: `bb test` via `~/bin/memcap 3` — 933 tests, 9.837 assertions,
  0 failures, 0 errors.

## Worktree note

The shared worktree also contained Claude's uncommitted K6 changes
(`src/kimun/surface/parse_contract.cljc`, `contract_test.cljc`, and edits to
`docs`, `diagnostics`, `signals`, `markdown/ast`, `stores`). The full suite above ran
with those changes present and passed, so it did not fail because of K6. This is not
the final post-K6-commit run: repeat `bb test` under `memcap` after K6 is committed
and, if K6 changes the result, record the new log before closing K2a.

`route.cljc` itself has no dependency on the K6 files; the clean targeted run is
independent of them.

## Visible check (pure, no store)

The golden fixture root is
`348a08fe24e1a340377e8cd399bbf3cad0cd46fb0d75777e868c3ba845b9d757`.
Changing `:tree-id` leaves it unchanged; changing fingerprint, class, projection hash,
content hash or path moves it. Paths are normalized to NFC before sorting and
duplicate detection, so canonically equivalent Unicode paths raise
`:route/ambiguous`. Duplicate paths raise `:route/ambiguous`; absolute drive
paths (`C:/…`, `C:…`) and UNC/absolute paths are rejected.
