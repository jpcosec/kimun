# K2a C2a — durable route map, route root and locator check

Base: `076be35b` (K3 integrated). Worktree: `/tmp/kimun-c2a-rb`.

## Scope

- `src/kimun/kernel/route.cljc`: complete route map object + digest; root remains
  the hash of the five hashed/sensorized fields and excludes `:tree-id`.
- `src/kimun/kernel/{revision,store,plan}.cljc`: `:register-tree :route` with
  full entry, `:remove-route` with `:tree` owner check, durable CAS route-map
  object, heads `:routes-root` + `:routes-object`, open/verify validation.
- `src/kimun/host/{store_cache,fs_store}.cljc`: optional mirror cache
  `cache/routes/<root>.<head>.edn`; the CAS map is the durable source.
- `test/kimun/kernel/route_store_test.cljc`.

No surface/docs or CLI docs changes.

## Contract

- Route root: five fields (path, fingerprint, class, projection-hash,
  content-hash), tree-id excluded.
- Durable map object: `{:route/version … :entries path→full-entry}`; its digest
  is `:routes-object` in heads. `open` checks H(bytes)==id and shape, so a
  missing/tampered CAS map is a head-corruption error.
- Cache is a mirror keyed by root+head; deletion is not corruption and cannot
  lose the durable map. Cache tampering is reported as `:routes-invalid?` and
  the CAS map is used.
- `:remove-route` requires `:tree`, rejects another tree's path, and does not
  detach the tree. Registering another tree on an owned path rejects
  `:route/ambiguous`.
- Format 3 remains legacy CAS; route ops are optional, old ops still apply.

## Evidence

- `lint.txt`: 103 files, rings/docstrings ok.
- `targeted-tests.txt`: 155 tests / 1.599 assertions, 0 failures.
- `full-test.txt` (base `9e106029`, K3u+T01 combined): 984 tests / 10.943
  assertions, 0 failures, 0 errors, 120 namespaces.
- `oracle.txt`: 9/9 canonical ids, 0 mismatches.
- `include-check.txt`: clean.

## Limits / next

- A cache-first open is not an acceleration: open validates/loads the CAS map
  first; cost is O(number of route entries). Incremental route validation is
  pending.
- Stores with cached trees registered before C2a have no `:route`; a partial
  root must be detected/reported in C2b/C4, not claimed complete.
- `:projection-hash` is opaque in C2a. C2b must use a stable projection digest
  (AST before `:attrs {:doc ULID}`, or equivalent); real build/rebuild with new
  ULIDs is required before claiming root stability.
- C2b also owns `untrack` integration and route moves on the surface.
