# C2b — the document surface keeps the route map (K2a) true

On top of C2a (`790007e2`, the durable route map in the kernel), the `docs` surface
registers a route for every registered document with a file, typed or generic, and
keeps it right on track, ingest, update, a move to another path and untrack. The
route is a locator: nothing here is an identity. Contract and limits:
`docs/v2/06-superficies-y-cli.md §3`, «Rutas de lo trackeado (K2a, corte C2b)».
No file of `kimun.kernel.*` or `kimun.host.*` was edited.

## How this was run

A clean worktree from `main` at `ac09b764` with only this commit's files (the
temporary snapshot of C2a used while it was unmerged was never applied). Every `bb`
call went through `~/bin/memcap`; the full suite ran with no other suite running.

## Tests (base `ac09b764`)

- `lint.txt`: `bb lint` — rings and docstrings ok, 103 files.
- `targeted-tests.txt`: routes, route-store, route, generic-docs, names, contract,
  diagnostics, api-misc, edges-parity, migrate, semantic and the document
  namespaces — 222 tests, 2807 assertions, 0 failures, 0 errors.
- `full-test.txt`: `bb test` — 999 tests, 11419 assertions, 0 failures, 0 errors,
  122 namespaces, exit 0.
- `oracle.txt`: `bb oracle`, 9 rows, 0 mismatches.
- `include-check.json`: `kimun include check` on this repository, ok.

A first full run of this cut failed (1 failure, 3 errors) and found two real
regressions, both fixed before this commit:

- a typed document whose file is outside the project (`--store` from another
  directory, a path with `..`) was rejected by the route map. It is now registered
  without a route and reported (`:routes-outside`, `routes/outside-project`).
- the frozen v1 fixture holds two documents on one file. A new track on a held
  file is refused; such legacy pairs enter only through the explicit mode a v1
  migration uses (`:shared-path :allow`), the second without a route, reported
  (`:routes-shared`, `routes/shared-path`).

Existing tests touched, none weakened:

- `test/kimun/cli/diagnostics_test.cljc`: the route map object is the third object
  open itself verifies (`store/corrupt-head`); it is excluded from the random-victim
  test and added to the table of objects open refuses.
- `test/kimun/surface/edges_parity_test.cljc`: the fixture is rebuilt in the
  explicit legacy mode; its expectations are unchanged.
- `test/kimun/parity/api_misc_test.cljc`: one assertion added — the outside file is
  reported as not covered.

## Manual run (two temporary stores, `store-path.txt`)

Files `01`–`12` were taken on base `790007e2` before the two regressions were
fixed; the scenarios they show did not change. `13` was taken on `ac09b764`.

| file | scenario | result |
|---|---|---|
| `01-check-build-a.json`, `03-check-build-b-new-ulids.json` | the same two files registered in two stores | the same `routes-root`, 2 routes, complete |
| `02-`, `04-route-map-object-*.edn` | the route map objects | equal except for `:tree-id` |
| `05-check-after-reformat.json`, `06-route-map-after-reformat.edn` | blank line added, registered again | root moved: the fingerprint changed, the projection hash did not |
| `07-check-after-edit.json` | a paragraph changed | root moved |
| `08-check-after-move.json`, `09-route-map-after-move.edn` | the generic document re-anchored on `archive/plain.md` | still 2 routes: the old one left in the same commit |
| `10-check-after-untrack.json` | `docs untrack login` | 1 route |
| `11-track-held-path-refused.json` | a typed track on a file another document holds | `docs/exists`, exit 3 |
| `12-verify.json` | `stores verify` | ok |
| `13-check-document-outside-project.json` | a document created from another directory | valid store, `routes-complete? false`, `routes-outside` names it |

## Not covered, and what stays open

- **Task C4, not done:** no command registers at once the routes a store written
  before routes lacks, short of `include build --rebuild`; and only `stores check`
  reports coverage — `stores sense` and `heartbeat` do not.
- A path with `..` is refused by `kimun.kernel.route/valid-path?`. Admitting it
  would change a guard of the kernel; left as a separate decision.
- A parse-contract change that leaves the structure equal does not move the root
  (it is sensed by K6). If the root should move on any contract change, that needs
  an explicit field and route schema 2.
- Moving a typed document is covered through `update-plan` with `:path`; the CLI
  has no option for it.
- `migrate --from-v1` passes the legacy option, but no test migrates a v1 store that
  really holds two documents on one file.
- Not measured on a large store. The route map object is rewritten whole on every
  write that touches a route (see `runs/research/2026-11-14/concilio/review-c2a-claude.md §6`).
