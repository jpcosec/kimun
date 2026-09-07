# knowledge

**Content-addressed knowledge store with a CLI** — the successor of `sldb` (v1, Python), `kgdb`
and the anchored evaluator of `legos/knowledge`, merged into one product. The kernel is pure
Clojure (`.cljc`) running on Babashka: a pool of content-addressed immutable nodes (S/M/G),
trees of positions as indexes, typed edges with evidence, succession by `supersedes`, derived
anchor states, reconciliation of external edits. Markdown documents are projections of it.

Status: **2.0.0-alpha.1** — milestone S0 (repo, `stores` CLI group, packaging). See
`docs/v2/05-estado.md` for what exists and what comes next.

## Use

```bash
bb lint && bb test && bb oracle     # rings/docstrings, suite, canonical-bytes oracle
bin/knowledge --version             # dev launcher (needs bb ≥ 1.13 on PATH)
bin/knowledge stores init --name my-kb
bin/knowledge stores check --format text
bb jar                              # target/knowledge.jar (built-in bb uberjar)
bb release --local-bb --platforms linux-amd64   # dist/knowledge-<ver>-<plat>.tar.gz
```

A release bundle needs nothing on the target machine: `bin/knowledge` runs the bundled `bb`
on `lib/knowledge.jar`. Every command prints one envelope (`--format json|edn|text`) and exits
with a documented code (`docs/v2/06`).

## Read first

0. `docs/v2/05-estado.md` — state, decisions, "pista S" milestones
1. `docs/v2/01-orden-filosofico.md` · `02-sustrato-computacional.md` — what the persisted object is; data structures, invariants, runtime
2. `docs/v2/06-superficies-y-cli.md` · `07-modelos-como-nodos.md` · `08-distribucion.md` · `09-evaluador-anclado.md` — the product
3. `desk/atoms/` — durable concept truth (`epoch:v2` prevails); `desk/tasks/Board.md` — active work

## Layout

- `src/sldb/{kernel,host,surface}/` — rings 0/1/2 of the kernel (`bb lint` enforces the rings)
- `src/knowledge/cli/` — the CLI (ring 2, `knowledge.*` may require `sldb.*`, never the reverse)
- `test/`, `test/fixtures/cli/` — suite and golden envelopes
- `scripts/` — `check_rings.clj` (lint), `canon_oracle.py` (oracle), `release.clj` (bundles)
- `release.edn` — pinned Babashka version, platform matrix and asset digests
- `docs/v2/` — direction (highest authority); `docs/architecture/` — previous-stage contracts, superseded where marked
- `desk/`, `raw/source/`, `runs/` — deskops state, frozen source material, execution evidence

## Lineage

Cloned from `jpcosec/sldb` branch `refactor-target` (history preserved). `sldb` v1 and `kgdb`
are frozen (`v1-frozen`); `legos/knowledge` becomes `provenance` once the evaluator lands here
(milestone S7). License: MIT.
