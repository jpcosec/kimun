# K3 follow-up — untrack a generic document; typed and generic names resolve together

Two gaps left by K3 (`076be35b`):

1. A document registered under `generic/md` could not be untracked: `docs untrack`
   and `docs delete` only knew typed documents, so its path and name stayed taken.
2. A short name shared by a typed and a generic document was resolved to the typed
   one in silence, against the rule of K8.

Now `docs untrack|delete` remove the anchor of a generic document, and typed and
generic documents are resolved by one function: a shared alias is `docs/ambiguous`
with both candidates. Contract: `docs/v2/06-superficies-y-cli.md §3`, «Documentos sin
modelo: clase genérica (K3)». No kernel or host file was edited.

## How this was run

From a clean worktree: `076be35b` plus only the files of this commit, every `bb`
call through `~/bin/memcap`. The full suite was started when no other suite was
running; the coordinator's notice that another session's suite was about to run
arrived while it was already going, so the two may have overlapped.

## Tests

- `lint.txt`: `bb lint` — rings and docstrings ok, 103 files.
- `targeted-tests.txt`: the namespaces of K3 plus names and contract — 157 tests,
  1708 assertions, 0 failures, 0 errors.
- `full-test.txt`: `bb test` — 967 tests, 10146 assertions, 0 failures, 0 errors,
  118 namespaces, exit 0.

## Manual run

`01`–`08` on one temporary store (`store-path.txt`), taken before the alias fix;
`09`–`12` on a second one, after it.

| file | scenario | result |
|---|---|---|
| `01-ingest.json` | `docs ingest a.md` | registered, class `generic/md` |
| `02-untrack.json` | `docs untrack a` | ok, the file stays |
| `03-list-after-untrack.json` | `docs list` | empty: the tree is hidden |
| `04-sense-after-untrack.json` | `stores sense` | tracked 0 |
| `05-ingest-again.json` | `docs ingest a.md` again | ok, a new tree id |
| `06-delete.json`, `07-file-after-delete.txt` | `docs delete a --yes` | untracked and the file removed |
| `08-verify.json` | `stores verify` | ok |
| `09-untrack-shared-alias-ambiguous.json` | typed and generic both named `plain`, `docs untrack plain` | `docs/ambiguous`, exit 2, both candidates |
| `10-render-shared-alias-ambiguous.json` | `docs render plain` | `docs/ambiguous`, exit 2 |
| `11-untrack-by-qualified-name.json` | `docs untrack notes/plain.md` | only the generic document is untracked |
| `12-alias-unambiguous-again.json` | `docs show plain` | the typed document |

## Not covered

- No `:remove-route` is emitted: the route registry (K2a C2a) is not in `main`. When
  it lands, untrack and a change of path must remove the old route in the same plan.
- A document without a model whose tree was persisted before K3 is still resolved
  after the tracked ones; if it shares a name with a typed document, the typed one
  is taken, as before.
- `fields` and `sections` take typed documents only; a name shared with a generic
  document is ambiguous there too, through the same resolver, but that path has no
  test of its own.
