# stores heartbeat — fixes after Pi's review

Review: `runs/research/2026-11-14/concilio/review-heartbeat-pi.md` (of `86a0ba42`).

## What changed

- **Fatal `stopped` (medium).** The loop (`kimun.surface.heartbeat/run`) now catches its
  own failures and writes the `stopped` record itself, so the fatal one has the same
  four fields as every other (`at reason beats errors`) plus `error {type message}`.
  `:fatal` is in `stop-reasons`. The command maps it to a streamed failure, exit 70.
- **SIGINT (medium).** The subprocess test now covers SIGTERM (143) and SIGINT (130).
- **`stdout-closed` (low).** It is no longer a `stopped` reason: `stop-reasons` is
  `beats`, `cancelled`, `fatal`; `outcomes` adds `:stdout-closed` as a result of the
  loop only. `docs/v2/06` §3 says so.
- **`System/out` (low).** The writer was not changed. The limit is documented in the
  docstring of `emit-line!` and in `docs/v2/06` §3: only the process's stdout is supported.
- **Fatal path tests (low).** Loop-level tests for a failing wait and a failing emit;
  a command-level test for the envelope (exit 70, `:streamed`, no extra output).

## Tests

- `lint.txt`: `bb lint` (via ~/bin/memcap) — rings and docstrings ok, 101 files.
- `targeted-tests.txt`: heartbeat, sense, signals, stores and main namespaces —
  58 tests, 381 assertions, 0 failures, 0 errors.
- `full-test.txt`: `bb test` (via ~/bin/memcap) — 915 tests, 9726 assertions,
  0 failures, 0 errors, 113 namespaces, exit 0.
- `01-beats.ndjson`, `01-exit.txt`: the CLI on a temporary store after the change,
  `--beats 2`, exit 0.

## Not covered

- The command-level fatal test replaces the loop with a stub; no test forces a real
  fatal failure through a child process.
- A really closed stdout is still checked only by hand (`07-*` of the first run).
