# K2a C5 — documentary correction (docs only)

After C5 (`b4931b7c`) three documents still said the route Merkle tree was pending and that the
route root is a flat digest. That is false for a store of route schema 2. No code changes.

## What was corrected

- `docs/v2/12-ecosistema-y-pendientes.md §1`: the «Sensor y store» row, the `K2a` row and the
  closing paragraph.
- `docs/v2/14-juego-del-lenguaje.md`: the reading table (Tema II), the stratum note of §1, the
  «store estable» row of §19, the strata list of §20, decisions 39 and 44 (§12) and the `K2a`
  entry of §13.
- `test/kimun/doc14_contract_test.cljc`: it pinned «C5 pending». It now pins what is true:
  C2a/C2b/C4/C5 implemented, schema 1 kept as legacy with its flat `O(n)` digest, schema 2 the
  tree, the fan-out cost open, `K2a` not closed, C5 not listed as pending, and §20 naming the
  two schemas, the limits and `K1`/`D0`.

What every corrected passage now distinguishes:

- **Schema 1 (legacy):** one flat object, `route-root` a flat `O(n)` digest of the sorted
  entries. Every store created before C5 stays so until `stores upgrade` or
  `include build --rebuild`.
- **Schema 2 (new stores):** one node per directory; a write rewrites the nodes on its path,
  `open` checks the root node, a diff goes down where the hashes differ.
- **Limits:** no fan-out inside a directory (each node on the path is hashed and rewritten
  whole), enumeration and `route-coverage` read the whole tree, not measured on a real store.
- **D0 stays open:** tracked fields as leaves depend on `K1`/`D0`; `K2a` is still «parcial».

Source of the facts: `runs/subagents/2026-11-15-C5/EVIDENCE.md` and `docs/v2/10-escala.md §11.2`.

## Not changed

- `docs/v2/05-estado.md`: decision 44 there states the principle, not a status; nothing in it
  says the tree is pending or that the root is flat.
- `K2a` is not marked closed: that is the coordinator's and the owner's call.

## Validation (through `~/bin/memcap`)

- `lint.txt`: rings and docstrings ok, 104 files.
- `targeted-tests.txt`: `kimun.doc14-contract-test`, `kimun.spec16-models-contract-test`,
  `kimun.wittgenstein-manifest-test` — 12 tests, 1187 assertions, 0 failures, 0 errors, exit 0.
  Run again after rebasing onto `2f473155`.
- The full suite was not run: no file under `src/` changed, and the tests that read the two
  edited documents are the first two above.
