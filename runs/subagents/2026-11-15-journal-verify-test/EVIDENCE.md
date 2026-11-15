# journal verify test — a victim the head reaches

Found while validating P1 (`runs/subagents/2026-11-15-P1/`); independent of it.
Only `test/kimun/cli/journal_test.cljc` changes. No change under `src/`.

## The failure

`kimun.cli.journal-test/verify-detects-corrupt-object` damaged the first object of
`objects/` in name order and expected `journal verify` to answer `store/corrupt`. It
failed in one full run on `2c56e680` (4 failed assertions of that one test) and in 1 of
7 isolated repetitions; 0 of 20 on another checkout of the same commit. Object names
are hashes over fresh ULIDs, so the first one changes from run to run.

## The cause, measured

`undetected-objects.txt`: on the fixture of the test (init, ingest, two updates: 26
objects) each object was damaged in turn and restored. `journal verify` did not report
the damage in 4 of the 26: two heads maps (`:mmap :leaf`) and two route maps that a
later write replaced. Nothing reaches them from the head. The two heads maps were
already unreachable before routes existed, so the test was intermittent before K2a;
the route maps of C2a/C2b doubled the chance.

## The fix

- `verify-detects-corrupt-object` damages the head revision object (`:head` of
  `heads.edn`), which the head always reaches. Same four assertions.
- New `verify-does-not-answer-for-a-superseded-route-map`: damages a route map that
  an update replaced and pins what happens today: `journal verify` answers ok.

## Validation

- `targeted-tests.txt`: `kimun.cli.journal-test` run 15 times, 0 runs with a failure;
  12 tests, 62 assertions.
- Full suite, run with this fix and the P1 cut together:
  `runs/subagents/2026-11-15-P1/full-test.txt` — 1009 tests, 11532 assertions,
  0 failures, 0 errors, exit 0, pod available.

## Limits

- The new test pins a limit, it does not endorse it: whether damage to an object
  nothing reaches should be reported (or such objects collected) is not decided here.
- Only the route map case is pinned by a test; the superseded heads maps behave the
  same in the measurement but have no test of their own.
