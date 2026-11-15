# Test runtime optimization — diagnostics (mmap reverted)

Base: `5def87de` (P1 + DOC1). Worktree: `/tmp/kimun-test-opt-final`.
Benchmark isolated, no full suite running concurrently; `memcap 3`; same
command per namespace in the baseline (`/tmp/kimun-test-opt-base2`) and the
optimized tree. Two runs each, before and after.

## Diagnostics

Changes in `test/kimun/cli/diagnostics_test.cljc`:

- Removed the random-victim duplicate. The kind/mode table still covers all 11
  reachable object kinds × 3 modes (`altered`, `unparseable`, `deleted`).
- In each kind/mode cell, kept `store/verify` (CAS), `stores check` (envelope,
  finding, warning) and `stores verify` (full replay). Dropped the redundant
  per-cell `verify-full` direct call, `journal verify` and `stores health`; those
  commands are still exercised by the critical-object table (head, heads-root,
  route map × 3 modes × 4 commands).
- Added `damaged-object-reaches-verify-full-and-health`: an ordinary reachable
  object, explicitly excluding `:head`, `:heads-root` and `:routes-object`,
  checks `verify-full` and `health` (`:needs-human`, `:corrupt`,
  `kimun stores verify`).

Measured (`diagnostics-test`, 13 tests):

| run | before | after |
|---|---:|---:|
| 1 | 38.08 s | 18.55 s |
| 2 | 39.97 s | 19.00 s |
| mean | 39.03 s | 18.78 s |

Assertions: 531 before, 419 after. The removed assertions were duplicate
commands, not kind/mode coverage.

## mmap

The deterministic oversized maps were reduced in a trial (`B²` entries instead
of 500/2000), but the second isolated pass showed no useful gain:

| run | before | reduced trial |
|---|---:|---:|
| 1 | 21.43 s | 21.16 s |
| 2 | 24.98 s | 22.51 s |
| mean | 23.21 s | 21.84 s |

The six 60-trial property specs dominate. The mmap test file was reverted to
`5def87de` unchanged, preserving the 500/2000-entry fixture sizes and depths.
No trials were reduced.

## Diff review (5def87de → worktree)

- Only `test/kimun/cli/diagnostics_test.cljc` plus `runs/` evidence: `git diff
  5def87de -- src/ test/kimun/kernel/mmap_test.cljc` is empty (0 lines), so the
  mmap revert is byte-identical to the base and no production code changed.
- No dead requires: `kimun.kernel.mmap` is still used by the kind selection
  (`mmap/entries`, `child-ids`, `branch?`, `node-count`, `:144-177`).
- Coverage kept: 11 kinds × 3 modes still assert `store/verify`, `stores check`
  (envelope + finding + warning) and `stores verify`; `verify-full`, `journal
  verify` and `stores health` still run for head / heads-root / route map × 3
  modes × 4 commands; `damaged-object-reaches-verify-full-and-health` covers the
  ordinary-object case for `verify-full` + `health` and asserts the victim is
  not one of the three critical objects.
- Assertion arithmetic matches the measurements: 8 → 5 per kind/mode cell
  (3 × 33 = 99 removed), random-victim test 18 removed, new test 5 added:
  531 − 99 − 18 + 5 = 419.
- Residual risk: randomized discovery is gone; replaced by full kind/mode
  enumeration plus one deterministic ordinary-object replay/health check, the
  condition the audit set for fusing it (`test-cost-audit.md`, Conclusion).

## Verification

- Directed (base tree, before the C5 pause): diagnostics + mmap = 32 tests /
  532 assertions, 0 failures; `bb lint`: `rings and docstrings ok: 103 files`.
- Rebased onto `b4931b7c` (C5), commit `dfcc9c71`; C5 touches neither
  `diagnostics_test.cljc` nor `mmap_test.cljc`.
- Full suite (`full-test.txt`, `KIMUN_POD` = the built pod, `memcap 3`):
  `1058 tests, 11838 assertions, 0 failures, 127 namespaces, 127 s`, wall
  129.25 s, maxrss 232 MB, `tree-sitter pod: available`. C5's own full had
  11950 passes; the 112 difference is exactly the 531 -> 419 drop here.
- Slowest namespaces in that full: `mmap-test` 20.6 s (25.1 s at the audit
  baseline) and `diagnostics-test` 17.8 s (45.1 s at the audit baseline).
- `bb oracle` (`oracle.txt`): `9 rows, 0 mismatches`.
- `bb cli include check` (`include-check.json`): `ok`, zero findings in all six
  classes.

## Status

Closed on `main` after C5. The commit records the test change; this run
directory holds the timings, the full run, the oracle and the include check.

Audit/reason: `runs/research/2026-11-14/test-cost-audit.md`, included in this
commit.
