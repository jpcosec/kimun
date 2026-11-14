# K2a C4 — route coverage detection + rebuild

Base: `3902731d` (C2b `ff9d44c3`, C1 `3902731d`; M1 `ac09b764`).
Worktree: `/tmp/kimun-c4-final`.

## Contract

`kimun.kernel.store/route-coverage` compares the cached tree descriptors
currently anchored to a file (active `:reference` root→external edge) with the
durable route map:

- every anchored cached tree should have exactly one route entry;
- every route entry should point to an anchored tree;
- `:complete?` is false when the route source is unavailable, even if both
  lists are empty;
- `:missing-trees` lists anchored cached tree ids without a route;
  `:orphan-routes` lists paths whose tree is no longer anchored.

Historical trees left by untrack do not count: untrack removes the anchor edge
and the route, so coverage stays complete while the tree remains in the store.

Surface: `stores verify` exposes the kernel report as `:routes` (diagnostic;
does not alter the corruption result). `stores check` keeps the richer C2b
surface report (`:routes-root`, `:route-count`, `:routes-complete?`,
`:routes-missing`, `:routes-shared`, `:routes-outside`) without a duplicate
`:routes` key.

## Cases covered

- route present: complete, one anchored cached / one routed tree;
- second anchored cached tree without a route: incomplete, missing id;
- route entry pointing at a non-anchored tree: orphan route visible;
- untrack (remove anchor edge + route): complete, historical tree remains;
- end-to-end `include build`: three declared documents, complete root, three
  routes, and `stores verify :routes` complete;
- end-to-end `include build --rebuild`: complete root again and the same route
  root as before, proving the projection is stable across new ULIDs.

## Evidence

- `lint.txt`: 103 files, rings/docstrings ok.
- `full-test.txt`: `:test 1005, :pass 11503, :fail 0, :error 0`, 123
  namespaces, 173 s; `tree-sitter pod: available`.
- `oracle.txt`: 9 rows, 0 mismatches.
- `include-build.txt`: include build ok (repository include, 0 models/0 docs).

## Conflict resolution

`stores.cljc`: kept C2b's route surface block and did not add C4's `:routes` key
to `stores check`; C4's kernel report is exposed only in `stores verify`.
