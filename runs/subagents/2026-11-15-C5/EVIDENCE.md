# K2a C5 — the route map as a Merkle tree by path (route schema 2)

Base: `main` at `5def87de`. Contract: `docs/v2/10-escala.md §11.2`; surface summary in
`docs/v2/06-superficies-y-cli.md §3` («Rutas de lo trackeado», «Esquema del mapa»). Principle:
`docs/v2/14-juego-del-lenguaje.md §20`.

## What changed

- `src/kimun/kernel/route_tree.cljc` (new, pure): one node per directory; `update`, `lookup`,
  `entries`, `diff`, `from-entries`, `root-node`. Two hashes on one tree: the route hash (no
  `:tree-id`) is the route root; the object id covers everything. Every node read is checked
  against its object id, and a node below another also against the route hash and the count
  its parent recorded.
- `src/kimun/kernel/revision.cljc`: a transaction keeps its net route writes and, in schema 2,
  updates the tree along those paths. Schema 1 is the code it was.
- `src/kimun/kernel/store.cljc`: `store.edn` `:route-schema`; `open` reads and checks the root
  node only and serves `:routes` as a lazy map; `reachable` walks the nodes;
  `upgrade-routes!`; optional backend port `HeadsSwap`.
- `src/kimun/host/fs_store.cljc`: `swap-heads!` under the writer lock.
- `src/kimun/cli/commands/stores.cljc`: `stores check` reports `:routes-schema`;
  `stores upgrade` on a format-4 store moves schema 1 to 2 and puts the previous heads and
  `store.edn` back if the store does not verify afterwards.
- `src/kimun/cli/errors.cljc`: `:route/*` is exit 5. `scripts/store_size.clj`: classes
  `route/map` and `route/node`.

## Compatibility contract

- Store format stays 4; `heads.edn` keeps its keys.
- A new store: `store.edn` has `:route-schema 2`.
- A `store.edn` without the key (every store created before this cut) is schema 1: it opens,
  answers and writes as before. A write does not convert it.
- Schema 1 → 2 only when asked: `stores upgrade` or `include build --rebuild`. Same entries
  (tree ids included), documents, head revision and log; no transaction added.
- The route root changes with the schema; the command returns both.

## Validation (all through `~/bin/memcap`)

- `lint.txt`: rings and docstrings ok, 104 files.
- `targeted-tests.txt`: 18 namespaces around routes, store, upgrade and cache — 207 tests,
  2347 assertions, 0 failures, 0 errors, exit 0.
- `full-test.txt`: `bb test`, tree-sitter pod available — 1058 tests, 11950 passes, 0 failures,
  0 errors, 127 namespaces. **The file has no `exit` line**: the session was restarted while
  the wrapper script was running, after the suite printed its result map and before the
  script appended the exit code. The result map is the suite's own last line.
- `oracle.txt`: 9 rows, 0 mismatches. `include-check.json`: ok. Both taken after the restart,
  on the same tree (no source file is newer than `lint.txt`).
- An earlier full run on this cut failed one assertion, `kimun.kernel.hardening-test`
  (`store.edn carries the descriptor fields`): it expected exactly three descriptor keys. The
  fourth, `:route-schema`, is intended. The test now expects it on a new store and checks that
  a store with the three keys alone still opens and replays.

## The 202 s of `kimun.cli.heartbeat-test` in the full run

In `full-test.txt` that namespace took 202184 ms and the suite about 370 s (the previous full
run on `main`, DOC1, took about 173 s). `heartbeat-timing.txt`: the namespace run alone, one
process at a time, twice on this cut and twice on `main` at `5def87de`: 1724 and 1706 ms here,
1695 and 1666 ms on `main`, 7 tests and 68 assertions green each time. (The worktree HEAD in
that file, `2cc5dad9`, was the provisional commit; the working tree was the final one.) No
regression in the code. The slow run coincided with the restart of the session manager while
the suite was running; those tests start a subprocess and send it signals. That cause is the
likely one, not a proven one: the full suite was not run again.

## Tests

New:
- `test/kimun/kernel/route_tree_test.cljc` (20 tests): the tree is a function of its entries
  (one write at a time, one batch, built from nothing); it answers what the flat map answers
  (entries, lookup, schema-1 root and digest of the entries read back, diff); the root ignores
  tree ids; nodes read and written by a write, a lookup and a diff; a node altered only in a
  `:tree-id` is refused by lookup, entries, update, diff and root-node; a wide directory is
  rewritten whole.
- `test/kimun/kernel/route_tree_store_test.cljc` (21 tests): schema 2 in a store (heads,
  replay, move, untrack, new tree id), cost of one write against schema 1, open reads one
  node, damage and forged tree id, schema 1 stays schema 1, the upgrade and its edge cases.
- `test/kimun/cli/route_schema_test.cljc` (4 tests): the same from the CLI.

Changed on purpose:
- `test/kimun/kernel/route_store_test.cljc` (C2a): its stores are created as schema 1
  explicitly; no assertion changed.
- `test/kimun/cli/routes_test.cljc` (C2b): five assertions compared the root with the flat
  root; a new store is schema 2, so they compare with the tree root.
- `test/kimun/kernel/hardening_test.cljc`: the descriptor expectation above.

Checked that the object-id tests bite: with the check disabled, 18 assertions fail.

## A store written by the previous binary (`compat-*`)

Temporary project `/tmp/c5-compat`: one typed and three generic documents, created and
written with the CLI of `main` at the commit in `compat-00-old-binary-commit.txt`, then
operated with the CLI of this cut.

- `01`, `02`: `store.edn` (no `:route-schema`) and `heads.edn` as the old binary wrote them.
- `03`, `04`: `stores check` with the old and the new binary: same revision, same route root,
  4 routes, complete. The new one adds `"routes-schema": 1`. `docs list` byte-identical.
- `05`–`07`: a `docs update` with the **new** binary keeps schema 1 and leaves `store.edn`
  alone; the **old** binary then reads the store, gives the same route root and verifies it.
  While a store is schema 1 the two binaries can alternate.
- `08`: `stores upgrade` — `from-route-schema 1`, `route-schema 2`, 4 routes, 5 tree nodes,
  the root before and after, 53 objects rehashed.
- `09`–`11`: `stores check`, `stores verify`, `journal verify` after the upgrade: ok.
- `12`: `docs list`, `journal show` and `log.edn` byte-identical before and after.
- `13`, `14`: `store.edn` and `heads.edn` after the upgrade. `15`: a second upgrade does
  nothing.
- `16`: after a `docs update` and a `docs untrack` on the tree: schema 2, 3 routes, complete,
  store verifies.
- `17`: the **old** binary on the upgraded store fails closed, `store/corrupt-head`
  (`not-a-route-map`), exit 5. See limits.

## Limits

- **No fan-out inside a directory; the cost is not independent of the number of routes.** The
  tree bounds the number of nodes read and written (those on the path). Each is read, hashed
  and rewritten whole, so work and bytes follow the direct children of the directories on the
  path. A store with every file in one directory pays for all its routes on every write, as
  schema 1 does. The 2000-route test counts nodes touched, not bytes. A sublinear cost in a
  flat directory would be another node shape, hence another route schema with its own upgrade.
- A binary older than this cut does not know schema 2: it reports a schema-2 store as
  `store/corrupt-head`. It fails closed, with a message about corruption instead of version.
  The format version was not raised.
- `diff` goes down only where the route hashes differ, so a damaged node in a branch that did
  not change is not read by it; `stores verify` is what reports it.
- Replaced nodes stay in `objects/` unreferenced (collection is K5).
- Enumerating every route (`stores check`, coverage) reads the whole tree.
- Tracked fields as leaves (`14 §20`) are not here: they depend on K1/D0.
- Not measured on a real large store; the compatibility run has 4 routes.
- `stores upgrade` replays nothing: it rehashes what is reachable (`stores verify`). Replay
  under schema 2 leading to the same heads is covered by the kernel tests only.
- A path is a locator, not an identity; nothing here decides what a moved file is.
