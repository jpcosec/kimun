# K6 — parse contract recorded with its digest (first slice)

`stores check|health|sense|heartbeat` now tell a changed source from a changed parse
contract. Contract, compatibility and limits: `docs/v2/10-escala.md §11.1`. Identity
and G are untouched; no format-version change.

## How this was run

Another session was editing `src/kimun/kernel/store.cljc` in the main checkout while
this was closed (K2a; at one point the file did not load). So lint, both test runs
and the manual run were taken from a clean worktree: `HEAD` (`4913b9d1`) plus only
the files of this commit. Every `bb` call went through `~/bin/memcap`.

## Tests

- `lint.txt`: `bb lint` — rings and docstrings ok, 103 files.
- `targeted-tests.txt`: parse-contract, contract (CLI), sense, signals, diagnostics
  and heartbeat namespaces — 56 tests, 805 assertions, 0 failures, 0 errors.
- `full-test.txt`: `bb test` — 939 tests, 9874 assertions, 0 failures, 0 errors,
  116 namespaces, exit 0.

Coverage of the suite: the alternate parser is simulated (`ast/*parser*` and a
stubbed pod). That is exact for sensing, which compares contracts and never parses.
No test of the suite runs the real tree-sitter pod, which is optional.

## Manual run with the real pod (temporary stores, `store-path.txt`)

Signals are reduced to type, category, action and detail.

| file | scenario | result |
|---|---|---|
| `02-sense-native-clean.json` | new document, in-process parser | no signals |
| `03-sense-same-file-treesitter.json` | same file, `KIMUN_PARSER=treesitter` | `contract/changed`, `content-ok` true, changed `parser`, `grammar` |
| `05-sense-source-edited-native.json` | file edited, same parser | `source/mutated`, `contract-ok` true |
| `06-sense-source-edited-and-treesitter.json` | file edited and other parser | `source/mutated`, `contract-ok` false, changed `parser`, `grammar` |
| `08-update-under-treesitter.json` | `stores update` under tree-sitter | `login` updated |
| `10-sense-treesitter-after-update.json` | sense again under tree-sitter | no signals |
| `11-sense-native-after-update.json` | back to the in-process parser | `contract/changed`: the store now says tree-sitter |

`01-`, `04-` and `09-*.sha256`: the source file has the same sha256 before, after
sensing under the other parser and after `stores update`. The repair did not
rewrite it.

## Backward compatibility (a store written by the previous commit)

- `12-legacy-store-sense.json`, `14-legacy-store-sense-treesitter.json`: a store
  created with `4913b9d1` and read with this code gives no signals, also under the
  other parser: nothing is claimed about a contract that was never recorded.
- `13-legacy-store-check.json`: its document is `ok` with contract status `unrecorded`.
- `15-legacy-store-check-after-update.json`: after an edit and `docs update`, the
  status is `same`: the contract is recorded at the next write.
- `07-new-store-read-by-previous-kimun.json`: a store written with this code and
  read by the previous commit gives no signals: the extra evidence key is ignored.

## Not covered

- `rebuild-mismatch`: not implemented (see `10 §11.1`).
- A pod that is present but does not answer, and a document recorded under
  tree-sitter read where the pod is absent: only in the suite, with stubs.
- Code documents (tree-sitter grammar, indexing profile, EDN selector): blocked on
  spec 16 (M1).
- Not measured on a large store. The pod is asked once per diagnosis; a write asks
  it once per document written.
