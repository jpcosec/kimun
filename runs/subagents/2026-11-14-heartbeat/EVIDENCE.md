# stores heartbeat — minimal heartbeat daemon

Command: `kimun stores heartbeat [--interval SECONDS] [--beats N]`. It repeats the
observation of `stores sense` and writes it to stdout as NDJSON. It senses only: no
actions, no LLM, no embeddings, no write to the store. Identity and the topologies
observed are unchanged. Contract: `docs/v2/06-superficies-y-cli.md` §1 (the explicit
exception to the envelope) and §3 «El latido».

## Repository tests

- `lint.txt`: `bb lint` (via ~/bin/memcap) — rings and docstrings ok, 101 files.
- `targeted-tests.txt`: `kimun.surface.heartbeat-test`, `kimun.cli.heartbeat-test`,
  `kimun.cli.sense-test`, `kimun.surface.signals-test`, `kimun.cli.stores-test`,
  `kimun.cli.main-test` — 55 tests, 356 assertions, 0 failures, 0 errors.
- `full-test.txt`: `bb test` (via ~/bin/memcap) — 912 tests, 9701 assertions, 0 failures, 0 errors, 113 namespaces, exit 0.

## Visible evidence outside the repo

Temporary project recorded in `store-path.txt` (no store inside the repo). Every
command ran through `~/bin/memcap`.

- `04-beats-clean.ndjson`, `04-exit.txt`: `--interval 0.2 --beats 2` on a clean
  document: `started`, two `beat` (`changed` true then false, `no signals`),
  `stopped` with reason `beats`; exit 0.
- `05-stream-sigterm.ndjson`, `05-exit.txt`: unbounded stream. `login.md` is edited
  while it runs (a `source/mutated` signal appears, `changed: true` once); the store
  directory is moved away and back (three `error` records `cli/no-store` with
  `consecutive-errors` 1–3, then `beat` again); SIGTERM gives `stopped` with reason
  `cancelled` and exit 143. `05-stderr.txt` is empty.
- `06-stream-sigint.ndjson`, `06-exit.txt`: SIGINT gives `stopped`/`cancelled`, exit 130.
- `07-consumer-closes.ndjson`, `07-exit.txt`: `… | head -2`; the loop ends when the
  consumer closes stdout, exit 0.
- `08-bad-interval.json`: `--interval 0` fails before any record with the usual
  failure envelope, `cli/usage`, exit 6.
- `03-store-files-before.sha256`, `09-store-files-after.sha256`: sha256 of every file
  under the store directory before and after all the runs above. The two files are
  byte-identical: nothing was added or changed (objects, log, heads, lock, cache).

## Limits

- Each beat includes the store hash check that `stores sense` already runs, so its
  cost grows with the store. Not measured on a large store.
- A stop is honoured between observations; the process waits at most 2 s for the
  `stopped` record. A slow observation in progress ends the stream without it.
- No event-driven activation: the daemon only gives the pulse.
