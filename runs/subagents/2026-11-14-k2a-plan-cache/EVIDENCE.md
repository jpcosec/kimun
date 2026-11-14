# K2a C3 — resolved plans in cache, not CAS

Commit: `refactor(store): keep resolved plans in the derived plan cache`.

## Scope

- `src/kimun/kernel/store.cljc`: `PlanCache` port, plan-cache commit, `verify-full`
  distinguishes plan hash mismatch from missing cache, log-id mismatch check.
- `src/kimun/host/store_cache.cljc`: `cache/plans/<tx-id>.edn` read/write.
- `src/kimun/host/fs_store.cljc`: implements `PlanCache`; the in-memory object view
  still exposes cached plans so `journal`/`models history` keep their contract.
- Tests: `test/kimun/kernel/{store,revision_format2}_test.cljc`,
  `test/kimun/cli/diagnostics_test.cljc`.

No surface/docs or CLI docs change. Format 3 remains legacy CAS: only format 4
uses the plan cache.

## Validation

- `lint.txt`: `bb lint` via `~/bin/memcap 3` — 103 files, rings/docstrings ok.
- `targeted-tests.txt`: 124 tests / 1.559 assertions, 0 failures.
- `full-test.txt`: `bb test` via `~/bin/memcap 3` — 955 tests / 9.998 assertions,
  0 failures, 0 errors, 117 namespaces. K6 and S1 were committed before this run.
- `oracle.txt`: `bb oracle` via `~/bin/memcap 3` — 9/9 canonical ids, 0 mismatches.
- `include-check.txt`: `bin/kimun include check` via `~/bin/memcap 3` — clean.

## Visible evidence (temporary store outside the repo)

`00-init.json` … `06-doc-show.json`, `store-path.txt`, `tx-id.txt`:

- `04-layout.txt`: the resolved plan is **not** in `objects/<tx>` and is in
  `cache/plans/<tx>.edn`.
- `05-verify.json`: `stores verify` passes.
- `06-doc-show.json`: `docs show` still resolves after the plan moved.

`verify-full` behavior covered by tests:

- plan cache missing, log intact ⇒ `:replay-unavailable`, not `:bad`;
- cached plan digest wrong ⇒ `:bad [:hash-mismatch]`, replay skipped;
- durable log `:id` ≠ revision `:tx` ⇒ corruption before a missing plan can be
  misread as cache absence;
- format 1/2/3 keep CAS/log replay; format 4 uses the cache.
