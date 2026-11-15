# K5, first cut — read-only inventory of stored objects the heads do not reach

Backlog: `docs/v2/12-ecosistema-y-pendientes.md §1`, K5. Contract: `docs/v2/10-escala.md §11.3`
(and the sentences of `§5`/`§7` it answers). CLI: `docs/v2/06-superficies-y-cli.md §3`,
`stores unreachable`.

## What the cut is

- `kimun.kernel.store/unreachable` and `object-class` (`src/kimun/kernel/store.cljc`).
- `kimun stores unreachable [--ids]` (`src/kimun/cli/commands/stores.cljc`, usage line in
  `src/kimun/cli/main.cljc`).
- **Inventory only.** It writes nothing and removes nothing. No command removes objects.
  The name index of K5 is not in this cut.

## Exact scope

- Stored: every id under `objects/`. The derived cache is not looked at.
- Reached: `store/reachable`, the walk `stores verify` rehashes — the head revision and its
  whole history, the per-tree heads, the route map the heads name. No retention policy.
- Unreachable = stored and not reached, each read from the store itself and rehashed:
  - `:held`: a resolved plan a committed log line names. Not a candidate: without it the log
    cannot be replayed.
  - `:damaged`: does not hash to its name or does not read. Only reported.
  - `:candidates`: the rest, with class by shape and `:root?`.
- `:lost-revisions`: candidates that are revisions (a losing writer, or the uncommitted tail).
- Every answer carries `:note`: a candidate is not known to be safe to remove.

## Validation (all through `~/bin/memcap`, on base `b6d9541f`)

- `lint.txt`: rings and docstrings ok, 104 files.
- `targeted-tests.txt`: `kimun.kernel.unreachable-test`, `kimun.cli.unreachable-test` and nine
  namespaces around stores, the CLI surface, the store kernel, the journal and the documentary
  contract — 150 tests, 1192 assertions, 0 failures, 0 errors, exit 0.
- `full-test.txt`: `bb test`, tree-sitter pod available — 1079 tests, 12038 passes, 0 failures,
  0 errors, 129 namespaces, exit 0. Run alone, in an exclusive turn.
- `oracle.txt`: 9 rows, 0 mismatches, exit 0. `include-check.json`: ok.
- An earlier run of the directed tests (before `:note` was added) overlapped with the full
  suite of another session; the runs recorded here did not.

## Tests

- `test/kimun/kernel/unreachable_test.cljc` (13 tests): the inventory is exactly what `verify`
  does not walk (a partition of the stored ids); one commit leaves nothing; replaced roots by
  class and their tops; the flat route map of schema 1 and the one left by the route upgrade;
  what a losing writer left, under its revision; plans in the CAS of a format-3 store are held,
  the plan of a lost transaction is a candidate; the uncommitted log tail is named and its
  objects listed; a damaged unreachable object; heads that moved; the inventory writes nothing
  (every file of the store compared); soundness on a copy; a held plan is needed.
- `test/kimun/cli/unreachable_test.cljc` (5 tests): counts against `stores check`, bytes that
  add up, `--ids`, the store byte-identical after the command, an empty store, no store.
- Changed on purpose: `test/kimun/cli/stores_test.cljc`, the list of known subcommands gains
  `unreachable`.

Two expectations of mine were wrong and were corrected before this commit, not the code:
a lost revision is not a top (the heads map its writer wrote mentions it), and removing a held
plan does not fail `verify-full` (it answers `:replay-unavailable`).

## Visible evidence (temporary project `/tmp/k5-ev`, never a real store)

One typed and two generic documents, then four edits: 8 revisions.

- `01-store-files-before.sha256`, `04-store-files-after.sha256`: sha256 of every file under
  the store directory before and after two runs of the command. Byte-identical.
- `02-unreachable.json`: 81 stored, 63 reached, 18 unreachable; 18 candidates (7 `mmap-leaf`,
  11 `route-node`, 7424 bytes), 13 tops, nothing held, nothing damaged, no lost revision,
  `stable? true`. (Taken before `:note` was added to the answer; `06` has it.)
- `03-unreachable-ids.json`: the same with `--ids`.
- `05-check.json`: `stores check` of the same store: 81 objects, 8 revisions.
- `06-unreachable-ids-with-note.json`: the command as it is in this commit, with `:note`.
  The store files still have the sha256 of `01` after this third run.
- `07-copy-without-candidates.txt`: the 18 candidates of `06` removed from a **copy** of the
  project (`/tmp/k5-ev-copy`). The copy has 63 objects and the same 8 revisions; `stores check`,
  `stores verify` (63 checked, nothing bad) and `journal verify` pass; `stores unreachable`
  gives 0; `docs list` and `journal show` are byte-identical to before the removal; a
  `docs update` afterwards works and the store still verifies. The original keeps its 81
  objects.

## Limits

- **A candidate is not known to be garbage.** A writer stores its objects before it moves the
  heads; an inventory taken during a commit lists objects about to be reached. `:stable?` only
  tells that the heads already moved. Removing needs the writer lock and a second look; none
  of that exists.
- History is all reached: which old revisions may be dropped (pins, pruning) is the retention
  policy of milestone 8 of `docs/v2/02`, not decided here.
- «Mentions» is every string shaped like an id inside an object: a superset of its
  references. It can mark as not-a-top an object nothing really refers to, never the reverse.
- References from outside the store (a linked store, the derived index,
  `upgrade-format-1.edn`) are not looked at. None is known to point into `objects/`; that it
  cannot is not shown.
- The soundness test removes candidates from a copy of small test stores of format 4 (route
  schemas 1 and 2). Not tried on a real store, on format 1–3 stores with documents, or with a
  writer running.
- Bytes are file sizes, not disk blocks.
