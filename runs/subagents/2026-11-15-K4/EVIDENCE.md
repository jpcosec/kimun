# K4, first cut — explicit packs for small CAS objects

Base: `main` at `04cf04b8`. Contract: `docs/v2/10-escala.md §11.4`. CLI:
`docs/v2/06-superficies-y-cli.md §3`. Backlog: `docs/v2/12-ecosistema-y-pendientes.md §1`, K4.
The paused format-3 work (`runs/research/2026-10-08/A4-report.md`, `A4-packfiles.patch`) was
read as history and not applied.

Who did what: design, format decisions and review by Claude; implementation, tests and the
measurement by a herdr pi worker from the brief `~/.cache/coord-briefs/kimun-K4-pack.md`
(six addenda); the last review fixes by Claude. Early review findings by the coordinator.

## What the cut is

- `src/kimun/host/fs_store.cljc`: pack format, `pack!`, `unpack!`, `pack-stats`,
  `stored-size`; `read-object`, `read-stored-object`, `object-ids` and `write-object!` know
  packs.
- `kimun stores pack [--max-bytes N]` and `kimun stores unpack`
  (`src/kimun/cli/commands/stores.cljc`); `stores unreachable` sizes a packed object.
- Not in this cut: GC, the name index, packing `cache/`, repacking.

## Format decisions

- `packs/<name>.pack`: magic, one record per object in strict id order (length + content),
  and a 48-byte footer with the record count and the SHA-256 of the canonical index. No id per
  record: the id is the hash of the content.
- The pack and its footer are store; `packs/<name>.idx` is derived. It is used without hashing
  any content when its size and digest match the footer; else rebuilt once from the pack; an
  index that still does not match is `store/corrupt-pack`, and `stores unpack` is the recovery.
- Packing is explicit, never part of a commit. Store format stays 4.
- Only loose files smaller than `--max-bytes` (default 4096). A loose file that does not hash
  to its name is not packed and is reported.
- No descriptor is held by a backend: the pack is opened, read and closed per read.

## Validation (all through `~/bin/memcap`, on base `04cf04b8`)

- `lint.txt`: rings and docstrings ok, 104 files.
- `targeted-tests.txt`: 9 namespaces — 134 tests, 1035 assertions, 0 failures, exit 0.
- `full-test.txt`: `bb test`, tree-sitter pod available — 1106 tests, 12250 passes,
  0 failures, 0 errors, 131 namespaces, exit 0. Exclusive turn.
- `oracle.txt`: 9 rows, 0 mismatches, exit 0. `include-check.json`: ok.

## Tests

- `test/kimun/host/pack_test.cljc`: round trip byte-identical; a packed store opens, reads,
  verifies and replays; a commit after packing and a second pack; `max-bytes`; a damaged
  record is a `hash-mismatch` for `verify`; the index is rebuilt once and only when it does
  not match the footer; a damaged footer and the recovery by `unpack!`; a truncated pack; a
  record length checked before allocating; a valid-shaped index with wrong ids; a damaged
  loose file left alone; a loose copy beside a packed one; another backend sees a later pack;
  a second pack within the same timestamp tick; no descriptor leaked over 200 opens; a huge
  index never read; no temporaries left; `unpack!` replaces a damaged loose duplicate; sizes
  and stats; the K5 inventory is the same packed and loose; nothing but `objects/` and
  `packs/` is touched.
- `test/kimun/cli/pack_test.cljc`: `stores pack` / `stores unpack` end to end.
- Changed on purpose: `test/kimun/cli/stores_test.cljc`, the known subcommands gain `pack` and
  `unpack`.
- Two tests were checked to bite: the timestamp-tick test fails without the settle rule.

## Measurement

`measurement.md` and its raw files: a synthetic store of 600 documents (never a real store),
18,215 objects packed, 603 kept loose.

- `.kimun` in disk blocks: 204.6 MB → 145.6 MB (−28.8 %); `objects/` 151.7 → 77.1 MB.
- `docs list`: 0.72–0.73 s loose, 0.84–0.86 s packed (×1.16). `stores verify`: 75.7 → 77.5 s.
- `stores pack` 4.54 s; `stores unpack` 7.5 s; `objects/` sha256-identical after two cycles.
- The first packed figure (×1.55) was the interpreted hex conversion of the id on each
  lookup, not opening the pack per read; `measurement.md` keeps that profile labelled as
  historical with the corrected attribution.

## Limits

- `cache/` is not packed: 36 % of the blocks of the packed store in the measurement.
- The read cost is measured on one store of 600 documents, and before the last three review
  fixes (a tolerant read, one `stat` less per read, the timestamp settle rule). Not remeasured.
- Of the ≈ 29 µs a packed read costs over a loose one, ≈ 4 µs are open/read/close; the rest is
  not broken down.
- For 2 s after a pack or an unpack, a long-lived reader lists `packs/` on every read.
- `unpack` needs the frame of the pack intact; a truncated pack is not recovered in part.
- The tolerance to a pack or unpack running at the same time as a read has no test of its
  own (the window is not deterministic).
- No repacking and no GC: a packed object that stops being reachable stays in its pack.
- A binary older than this cut does not read a packed store (`store/corrupt-head`, missing).
- Durability: the pack file and the `packs/` directory are forced before the loose files are
  removed; nothing else is.
