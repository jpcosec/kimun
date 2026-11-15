# K1a — a rebuild carries the identity of documents (first cut of K1)

Base: `main` at `6e3e2297`. Contract: `docs/v2/14-juego-del-lenguaje.md §21` and
`runs/research/2026-11-15/concilio/d0-contrato-sintesis.md` (the S–M identity contract the
council derived; its rules are council inferences). CLI: `docs/v2/06-superficies-y-cli.md §3`,
`include build`. Backlog: `docs/v2/12-ecosistema-y-pendientes.md §1`, K1.

## The defect

Under that contract an identity is minted when a unit is marked and cannot be recomputed from
content or path. `kimun include build --rebuild` moved the store aside and minted a new document
id and a new tree id for every document: every identity was lost, and `--rebuild` is the
official path to change format or route schema.

## The cut

`src/kimun/cli/commands/include.cljc` only; nothing in the kernel, no new id format, nothing
below the document, nothing of G.

- Before the old store is moved, the rebuild opens it and reads the registry
  `qualified path → {document id, tree id}` of every document with a file, typed or generic.
- A declared document whose path is in the registry is registered with those ids, also when
  its file changed since (a mutation of the same unit).
- What cannot be followed is declared in `:identities`
  `{:source :available :carried :minted :not-carried :ambiguous :without-file}` and in the
  warnings `include/identities-not-carried` and `include/identities-unavailable`.
- An old store that cannot be read does not stop the rebuild (it is the recovery path): fresh
  ids, and it says so.
- The transaction is labelled `include build (identities carried from <the .bak>)`.

Implementation and tests by a herdr pi worker from `~/.cache/coord-briefs/kimun-K1a-arrastre.md`;
design and review here.

## Review fixes before this commit

- The registry read typed documents only: a document of a generic class ingested by hand
  vanished in a rebuild without being listed. It is now in the registry and shows as not
  carried.
- Documents without a file were grouped under a nil path and reported as ambiguous. They are
  counted in `:without-file`.
- `:source` and the transaction label said `.kimun.bak` instead of the real name with its
  timestamp.
- With an unreadable old store the answer said zero documents were minted.
- The first test compared route entries by a key that does not exist (two maps of nils, always
  equal). It now compares the real tree ids and the route root.

## Validation (through `~/bin/memcap`)

- `lint.txt`: rings and docstrings ok, 104 files.
- `targeted-tests.txt`: 7 namespaces — 48 tests, 609 assertions, 0 failures, exit 0.
- `full-test.txt`: `bb test`, pod available — 1124 tests, 12437 passes, 0 failures, 0 errors,
  134 namespaces, exit 0.
- `oracle.txt`: 9 rows, 0 mismatches, exit 0. `include-check.json`: ok.

## Tests (`test/kimun/cli/identity_carry_test.cljc`, four)

1. Files unchanged: the document keeps its document id and its tree id; the route root and the
   tree ids of its entries are the same; `:carried 1 :minted 0`.
2. File edited between the two builds: same ids.
3. What cannot be followed is declared: a path that is gone and a generic document the
   declaration does not build are in `:not-carried`, with the warning; a newly declared
   document gets an id that is none of the old ones; `:source` names the real `.bak`.
4. Unreadable old store: the rebuild succeeds with fresh ids, `:available false`, the warning.

Without the change under `src/` the four fail (15 failed assertions, 3 errors), checked after
the review fixes.

## Limits

- Models get new ids in a rebuild.
- History (revisions, journal) is not carried.
- Documents without a file, `:inline` ones without a path, and documents the declaration does
  not build are not carried; they are counted or listed.
- A replacement under the same path is carried as the same unit (rule I6 of the contract: the
  sensor cannot tell an edit from a replacement).
- The registry is read from the old store: an unreadable store loses its identities.
- Continuity is taken from the same qualified path; a file moved between builds is not
  followed, it is listed as not carried.
- No test with a path held by two documents in the old store (`:ambiguous`).
- Pending in K1: units below the document, declared re-anchoring and closure, model ids.
