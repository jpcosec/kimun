# stores sense — first visible cut

Command: `kimun stores sense [--once]`. Read-only: one diagnostics observation,
structured signals, no daemon, no identity/G/embeddings changes.

## Repository tests

- `lint.txt`: `bb lint` (via ~/bin/memcap) — rings and docstrings ok, 100 files.
- `targeted-tests.txt`: `kimun.surface.signals-test` + `kimun.cli.sense-test` — 8 tests, 46 assertions, 0 failures.
- `full-test.txt`: `bb test` (via ~/bin/memcap) — 898 tests, 9618 assertions, 0 failures, 0 errors.

## Visible evidence outside the repo

Temporary project at `/tmp/kimun-sense-evidence-x7XEGg` (no store inside the repo;
the repo desk is empty). The store path is recorded in `store-path.txt`.

- `03-sense-before.json`: clean tracked document, `no signals`, same revision as after.
- `05-sense-after.json`: `login.md` edited to `Status: closed`; one
  `source/mutated` signal with category `edited`, severity `warning`,
  `needs-human true`, fields `["status"]`, and repair `kimun docs update login`.
- `07-sense-after.edn`, `08-sense-after.txt`: same observation in EDN/text.
- `04-login-before.md`, `06-login-after.md`: the source mutation.
- Head revision is identical in before/after (`09fbf9…`), so the command wrote nothing.
- `observed-at` is the response timestamp only; it is not a persisted event.

This first cut reports the categories the existing diagnostics already detect. It
does not claim coverage of every topological change and is not a heartbeat daemon.
