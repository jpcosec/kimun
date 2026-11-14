# K3 — documents without a model are registered under the generic class

`docs ingest FILE` in a by-reference store now registers the file like a typed
document with a file: external anchor (relative path, fingerprint, parse contract),
class `generic/md` in the tree descriptor and the root hash. The tree and its blocks
are cache. The document is then watched by `stores sense`. Identity is untouched:
the document keeps its own tree id and `:doc`, as before. Contract and limits:
`docs/v2/06-superficies-y-cli.md §3`, «Documentos sin modelo: clase genérica (K3)».
No file of `kimun.kernel.*` or `kimun.host.*` was edited.

## How this was run

From a clean worktree: `cdce1cb9` plus only the files of this commit. Every `bb`
call went through `~/bin/memcap`, and the full suite was started only after the
other session's suite had finished.

## Tests (base `cdce1cb9`)

- `lint.txt`: `bb lint` — rings and docstrings ok, 103 files.
- `targeted-tests.txt`: generic-docs, docs, journal (CLI and surface), stores,
  typed-docs, hashing, upgrade, diagnostics, sense, names, contract, find, index,
  links, golden, main and include namespaces — 155 tests, 1661 assertions, 0 failures.
- `full-test.txt`: `bb test` — 965 tests, 10099 assertions, 0 failures, 0 errors,
  118 namespaces, exit 0.
- `oracle.txt`: `bb oracle`, exit 0.
- `include-check.json`: `kimun include check` on this repository, ok.

Four existing assertions changed on purpose:

- `test/kimun/cli/docs_test.cljc`: `docs list` shows `:class "generic/md"`.
- `test/kimun/surface/journal_test.cljc`: an ingest is `{add-node 2, register-tree 1,
  add-edge 1}` instead of the block-by-block ops; the test that ingested one file
  under two names now uses two files and changes the file before the third write
  (re-registering an unchanged file writes nothing).
- `test/kimun/cli/stores_test.cljc`: with the heads one transaction behind, the file
  is set to the text that revision recorded before `stores check`, because the
  ingested file is now watched.

## Manual run (temporary store, `store-path.txt`)

Taken with the same K3 files on base `6930afa8`, before the one-path rule was the
last change; `13` was taken with it.

| file | scenario | result |
|---|---|---|
| `01-ingest.json` | `docs ingest notes/plain.md` | class `generic/md`, anchor `notes/plain.md` |
| `02-list.json` | `docs list` | the document with `class` |
| `03-journal-ops.json` | ops of the ingest | 2 nodes, 1 tree registration, 1 edge |
| `04-store-vs-cache.txt` | files in `objects/` and in `cache/objects/` | the blocks are in the cache |
| `05-sense-clean.json` | `stores sense` | tracked 1, no signals |
| `06-sense-reformatted.json` | blank line added | `source/reformatted`, store still valid |
| `07-sense-edited.json` | a paragraph changed | `source/mutated`, needs a person |
| `08-stores-update-skips-edit.json` | `stores update` | the edit is skipped |
| `09-docs-update.json`, `10-sense-after-update.json` | `docs update FILE --name` | registered again, no signals |
| `11-render-after-cache-deleted.md` | cache deleted, `docs render` | rebuilt from the file |
| `12-sense-same-file-treesitter.json` | `KIMUN_PARSER=treesitter` | `contract/changed`, source intact |
| `13-ingest-same-path-refused.json` | same file under another name | `docs/exists`, exit 3 |
| `14-ingest-dotdot-name.json` | a file named `..foo.md` | registered, anchor `..foo.md` |
| `15-verify.json` | `stores verify` | ok |

## Not covered

- No indexing profile per file type or per-store granularity: `generic/md` is the
  whole SLDB-MD tree. Other file types wait for spec 16 (M1).
- Documents ingested before K3 keep their persisted tree; there is no automatic
  migration.
- `find`, `fields`, `sections` and `docs show` (without `--ast`) do not see documents
  without a model, as before.
- A typed `docs track` of a file a generic document already holds is not refused
  here (only the generic side checks the path); the route registry of K2a C2 will.
- A symlink inside the project is anchored by its own path, whatever it points to.
- Not measured on a large store: `generic-documents` and the path check read every
  tree descriptor and every active edge.
