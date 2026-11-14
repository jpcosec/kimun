# K8 — qualified names by path, short name as alias (independent cut)

`a/README.md` and `b/README.md` are now two tracked documents. The short name
`README` is an alias; the path of the file is the qualified name; an alias that means
more than one document fails with its candidates and is never resolved by guessing.
The path is a locator, not the identity (the tree id is). Contract and limits:
`docs/v2/06-superficies-y-cli.md §3`, «Nombres de documento (K8)».

## How this was run

Other sessions had uncommitted work in the main checkout (kernel store), so lint and
tests were taken from a clean worktree: `6930afa8` plus only the files of this
commit. Every `bb` call went through `~/bin/memcap`.

## Tests (base `6930afa8`)

- `lint.txt`: `bb lint` — rings and docstrings ok, 103 files.
- `targeted-tests.txt`: names, typed-docs, contract, sense, diagnostics, links, fields
  and sections namespaces — 75 tests, 1082 assertions, 0 failures, 0 errors.
- `full-test.txt`: `bb test` — 950 tests, 9974 assertions, 0 failures, 0 errors,
  117 namespaces, exit 0.

One existing assertion changed on purpose (`test/kimun/cli/typed_docs_test.cljc`): the
same name on another file used to be `docs/exists` (v1 parity); it is now a second
document. The same name on the same file is still `docs/exists`.

## Manual run (temporary store, `store-path.txt`)

Taken earlier with the same K8 files on base `555b2dbc`; not repeated on `6930afa8`
(the commits in between touch only the Markdown parser). Outputs are reduced to the
fields that matter.

| file | scenario | result |
|---|---|---|
| `01-track-a.json`, `02-track-b.json` | track `a/README.md`, then `b/README.md` | both ok, name `README`, different tree ids |
| `03-list.json` | `docs list` | two entries with `qualified` `a/README.md` and `b/README.md` |
| `04-show-alias-ambiguous.json` | `docs show README` | `docs/ambiguous`, exit 2, both candidates |
| `05-show-qualified.json` | `docs show b/README.md` | the document of `b/README.md` |
| `06-track-same-file-again.json` | track `a/README.md` again | `docs/exists`, exit 3 |
| `07-sense-after-edit.json` | edit `a/README.md`, `stores sense` | `source/mutated`; the repair names `a/README.md` |
| `08-update-alias-ambiguous.json` | `docs update README` | `docs/ambiguous`, exit 2, nothing written |
| `09-update-qualified.json` | `docs update a/README.md` | ok, same tree id |
| `10-verify.json` | `stores verify` | ok |
| `11-links-shared-alias.json` | `docs recover index.md` with `[[README]]` and `[[a/README.md]]` | the alias is unresolved (`links/missing`); the path resolves |
| `12-recover-alias-ambiguous.json` | `docs recover README` | `docs/ambiguous`, exit 2 |

Files `01`–`10` were taken before the link fix; `11` and `12` after it.

## Not covered, and what waits for K2a C2

- `include` still names documents by file stem and refuses two declared files with
  the same stem (`include/conflict`). It matches declared files to the store by name;
  moving that to routes needs the route registry of K2a cut C2, which does not exist.
- Lookup by qualified name reads every document (no route index until C2). Not
  measured on a large store.
- Untyped documents (`docs ingest`) keep unique names.
- A moved file is `source/missing`; nothing is re-pointed. Continuity across moves
  is the open rule of D0.
- The HTTP routes were not exercised by hand; the suite's HTTP tests pass.
