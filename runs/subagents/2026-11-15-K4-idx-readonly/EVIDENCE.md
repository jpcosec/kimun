# K4 follow-up — a reader does not rewrite the `.idx` of a pack

Base: `main` at `d2dae59d` (K4). Contract: `docs/v2/10-escala.md §11.4`, point «Carga»; CLI
`docs/v2/06-superficies-y-cli.md §3`, `stores pack`.

## The defect

`load-pack` rebuilt a missing or stale `.idx` and wrote it back. It ran in read-only commands
too (`stores sense`, `stores heartbeat`, `stores unreachable`, `stores check`), and `sense` and
`heartbeat` promise to write nothing. Found while reviewing the K4b proposal, after K4 was
integrated.

## The fix

- `load-pack` rebuilds the index in memory, once per backend instance, and writes nothing.
- `pack!` repairs, under the lock, the `.idx` of every pack whose index is missing or does not
  match its footer, even with nothing to pack, and returns `:indexes-repaired`;
  `stores pack` reports it.
- One function, `matching-idx`, decides whether an index file is read, for readers and for
  the repair: the footer's count must fit the body of the pack and the file must be exactly
  count × 44 bytes before a byte is read. The repair had been written without that bound
  (caught in review by the coordinator and by me); sharing the function is what keeps the two
  from diverging.

Implementation and tests by a herdr pi worker from `~/.cache/coord-briefs/kimun-K4-idx-readonly.md`;
review and the shared function here.

## Validation (through `~/bin/memcap`)

- `lint.txt`: rings and docstrings ok, 104 files.
- `targeted-tests.txt`: 6 namespaces — 71 tests, 573 assertions, 0 failures, exit 0.
- `full-test.txt`: `bb test`, pod available — 1108 tests, 12278 passes, 0 failures, 0 errors,
  131 namespaces, exit 0.
- `oracle.txt`: 9 rows, 0 mismatches, exit 0. `include-check.json`: ok.

## Tests

In `test/kimun/host/pack_test.cljc` and `test/kimun/cli/pack_test.cljc`:

- `.idx` absent, and `.idx` with wrong ids: after `open`, packed reads, `verify` and
  `unreachable`, `packs/` has the same files, bytes and last-modified times; reads are right;
  one rebuild per backend instance.
- `pack!` with nothing loose repairs the index (canonical bytes, `:indexes-repaired 1`), a
  second call gives 0, and a corrupt pack is left alone.
- CLI: `stores sense` and `stores unreachable` on a packed store whose `.idx` was removed
  leave `.kimun/` identical file by file; `stores pack` then reports one index repaired.
- The existing tests that expected the reader to rewrite the index were changed to expect
  the file untouched.

## Limits

- Until `stores pack` repairs it, every command scans the pack again (2.66 s for a 14.8 MB
  pack in the K4 measurement). Nothing signals that an index is missing.
- No test forges a footer count with a sparse index of that size: with and without the bound
  the observable result is the same (nothing repaired, file untouched); only memory differs,
  and 1 GiB fits under the default `memcap`. The existing 1 GiB test covers the reader.
