# K4b — pack of the derived cache (`cache/objects`)

Base: `main` at `5d69f5c2`. Contract: `docs/v2/10-escala.md §11.5`. CLI:
`docs/v2/06-superficies-y-cli.md §3`, `stores pack --cache`. Design record, committed with
this cut: `runs/research/2026-11-15/K4b-cache-pack-propuesta.md` (the worker's proposal, with
the corrections of the review at its top) and
`runs/research/2026-11-15/K4b-revision-contrato-claude.md` (the review that found the two
cache failures fixed first in `5d69f5c2`).

Design, closed decisions and review by Claude; implementation, tests and measurement by a
herdr pi worker from `~/.cache/coord-briefs/kimun-K4b-cache-pack.md`; early review findings by
the coordinator.

## What the cut is

- `cache/packs/<name>.pack` + `.idx`, the K4 format. It is cache: derived, deleted with
  `cache/`. `object-ids`, `read-stored-object`, `verify`, `verify-full`, `unreachable`,
  `stored-size`, `pack-stats` and `unpack!` do not know it.
- Read order: loose store → store packs → loose cache (verified) → cache pack (verified) →
  plan. Every read from a cache pack is checked by hash.
- A reader never writes and never fails because of the cache: a cache pack that is corrupt,
  or whose `.idx` is missing or does not match, is ignored in memory for that backend
  instance. `stores check` names it with the warning `cache/corrupt-pack`; never exit 5.
- `stores pack --cache`: repairs the index of sound packs, drops the packs that still do not
  load, deletes loose cache objects that do not hash to their name, deletes loose duplicates
  of packed objects, and packs the rest in streaming. No `unpack --cache`.
- `src/kimun/host/fs_store.cljc`: the pack functions take a home (`{:packs :loose}` of
  Paths) for the store or for the cache; one writer, one place that decides to read an index.
  `kimun.host.store-cache` and `kimun.surface.docs` are unchanged.

## Validation (through `~/bin/memcap`)

- `lint.txt`: rings and docstrings ok, 104 files.
- `targeted-tests.txt`: 10 namespaces — 107 tests, 880 assertions, 0 failures, exit 0.
- `full-test.txt`: `bb test`, pod available — 1120 tests, 12388 passes, 0 failures, 0 errors,
  133 namespaces, exit 0.
- `oracle.txt`: 9 rows, 0 mismatches, exit 0. `include-check.json`: ok.
- The K4 tests (`test/kimun/host/pack_test.cljc`, `test/kimun/cli/pack_test.cljc`) are
  unchanged against `main` and pass.

## Tests (`test/kimun/cli/cache_pack_test.cljc`, seven)

The packed cache is deletable; `verify`, `object-ids` and `unreachable` are the same before
and after, and with the cache pack damaged; corrupted bytes with a valid index and a truncated
pack fall back to rebuilding from the file, and `stores check` warns without failing; readers
leave `.kimun/` identical file by file, and `docs render` leaves `cache/packs/` identical; the
command packs, drops damaged loose objects, deduplicates and drops a corrupt pack; a sound
pack without index is repaired, not dropped; the two scopes are separate.

## Review findings fixed before this commit

- A verification step that collected `[id content]` for every candidate (coordinator): gone;
  the writer holds one object at a time.
- `pack-cache!` dropped a sound pack without index before repairing it (coordinator and me):
  the repair runs first; a test covers it.
- `read-packed` had lost the fallback to the loose file when a pack vanishes between lookup
  and read, and carried a dead lookup: restored and removed.
- The `cache/corrupt-pack` warning depended on whether the check had loaded the cache packs:
  the query now loads them.

## Measurement

`measurement.md`: a copy of the 600-document bench store, store already packed. Disk blocks
143.7 → 101.8 MB (−29.2 %); files 12,953 → 1,839; 11,116 cache objects packed in 1.09 s;
`docs render` 0.54–0.60 → 0.55–0.58 s; `docs show` unchanged; `docs list` 1.01 s both. A
separate check of `docs list` across the K4 commit, `main` and this cut shows no difference
attributable to this cut; the gap to the 0.85 s measured for K4 was not isolated.

## Limits

- A cache pack without index is cold cache until the next `stores pack --cache`.
- No repacking and no GC: a cached object that stops being used stays in its pack until
  `cache/` is deleted. A loose duplicate lives until the next run of the command.
- The warning says «corrupt» also for a sound pack whose index is missing.
- One store measured, with the machine under other load.
- Readers of cached objects that do not go through `doc-tree` (listed in
  `runs/subagents/2026-11-15-cache-verificable/EVIDENCE.md`) are as they were.
