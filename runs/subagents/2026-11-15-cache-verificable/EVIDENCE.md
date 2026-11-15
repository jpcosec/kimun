# Verifiable cache (C1/C2) — a cut before K4b

Base: `main` at `db497ca7`. Contract: `docs/v2/10-escala.md §11`, «Caché por hash». Why:
`runs/research/2026-11-15/K4b-revision-contrato-claude.md` (the two failures, probed on the
loose cache of `main`).

## The two failures, before this cut

1. C1 — a cached object whose bytes were altered under the same name was served: `docs render`
   and `docs show --ast` returned the altered text while the source file was untouched, and
   `stores sense`, `stores verify` and `stores health` answered ok.
2. C2 — a cached object that was missing (not the root) made `docs render` fail with
   `store->ast: missing node` instead of rebuilding from the file.

## The fix

- C1, `src/kimun/host/fs_store.cljc`: `read-object` returns an object of the derived cache
  only when the SHA-256 of its bytes is its id; else it is a cache miss. `objects/`, packs and
  the plan cache are unchanged.
- C2, `src/kimun/surface/docs.cljc`: a cache hit in `doc-tree` needs every object of the
  tree (tree objects and the nodes their positions name); if any is missing or does not
  verify, the tree is rebuilt from the file.
- A read does not delete, rename or overwrite the damaged object.

Not D0: no id, no document and no store format changes. The cache is not packed here.
Implementation, tests and timing by a herdr pi worker from
`~/.cache/coord-briefs/kimun-cache-verificable.md`; review here.

## Validation (through `~/bin/memcap`)

- `lint.txt`: rings and docstrings ok, 104 files.
- `targeted-tests.txt`: 9 namespaces — 92 tests, 827 assertions, 0 failures, exit 0.
- `full-test.txt`: `bb test`, pod available — 1113 tests, 12315 passes, 0 failures, 0 errors,
  132 namespaces, exit 0.
- `oracle.txt`: 9 rows, 0 mismatches, exit 0. `include-check.json`: ok.

## Tests (`test/kimun/cli/cache_verified_test.cljc`, five)

| | altered cached object, same name | missing cached object, not the root |
|---|---|---|
| generic | `docs render` gives the text of the file; the altered file keeps its bytes | `docs render` gives the text of the file |
| typed | `docs show` gives the fields of the file; bytes kept | `docs show` answers |

Plus one on the backend: `read-object` of an id whose cached copy does not hash gives nil.
The worker checked that the five fail without the change under `src/` (`git stash` of `src/`:
5 failing tests).

## Timing (`times.txt`)

Bench store of 600 documents (`~/.cache/kimun-bench/k4-600`), one document, 3 runs:
`docs render` 0.50–0.54 s before, 0.50–0.52 s after; `docs show` 0.51–0.58 s before,
0.51–0.52 s after; output byte-identical. The SHA-256 per cache hit is inside the noise, not
isolated.

## Limits

- A damaged cached object makes that document slow until someone deletes `cache/`: every
  read rejects it and rebuilds in memory. No signal and no repair command.
- In that rebuild `doc-tree` still rewrites the per-document cache entry and writes back
  objects that are absent, as it did for a cold cache. Only the damaged object is left alone.
- Checking that the tree is complete reads every object of it on each resolution, also when
  the reader wanted a part. Measured on `render` and `show` only.
- Other readers of cached objects that do not go through `doc-tree`, listed by the worker and
  not changed: `kimun.surface.edges` (`relations-tree`, `entries`), the `docs show --ast`
  fallback (`kimun.surface.markdown.plan/store->ast`), the edge helpers of
  `kimun.surface.docs` (`edges-of`, `target`, `hidden-trees`) and `kimun.surface.models`. After
  C1 none of them can be handed altered bytes from the cache; on a missing object they fail
  instead of rebuilding. The worker reports they read persisted taxonomies today; not verified
  here.
- Tested with one generic and one typed document.
