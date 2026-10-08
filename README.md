# kimun

**A content-addressed knowledge store with a CLI.** kimun is the successor of `sldb` (v1,
Python) and `kgdb`, merged into one product. The name is Mapudungun (*kimün*, knowledge).

The kernel is pure Clojure (`.cljc`) running on Babashka: a pool of content-addressed
immutable nodes (S/M/G), trees of positions as indexes, typed edges with evidence,
succession by `supersedes`, derived anchor states, and reconciliation of external edits.
Markdown documents are projections of it.

## Install

A release bundle needs nothing on the target machine: `bin/kimun` runs the bundled `bb`
on `lib/kimun.jar`.

```bash
bb release --local-bb --platforms linux-amd64   # dist/kimun-<ver>-<plat>.tar.gz
```

From a checkout, `bin/kimun` is a development launcher (needs `bb` ≥ 1.13 on PATH).

## Five-minute tour

```bash
bin/kimun stores init --name my-kb          # create .kimun/ in the working directory
bin/kimun docs ingest notes.md --name notes # parse Markdown and commit it as a document
bin/kimun docs list --format text           # name, tree id, and for typed ones model/state/path
bin/kimun docs render notes                 # canonical Markdown of a document
bin/kimun find "some term"                  # physical, semantic and address search
bin/kimun journal show --limit 5            # the write history, newest first
bin/kimun stores health                     # verdict: ok | degraded | needs-human
```

Every command prints one envelope (`--format json|edn|text`) and exits with a documented
code. `bin/kimun --help` lists every group.

## Build and test

```bash
bb lint && bb test && bb oracle   # rings/docstrings, suite, canonical-bytes oracle
bb jar                            # target/kimun.jar (built-in `bb uberjar`)
bb desk                           # build this repo's desk store (.kimun) from kimun.include.edn
```

## Read next

- `docs/v2/README.md` — authority table and reading order for the direction docs; **start here**
- `docs/v2/05-estado.md` — current status and decisions
- `docs/v2/06-superficies-y-cli.md` — the CLI inventory and the envelope/exit-code contract
- `docs/faq.md` — scope and how to read "desk" here
- `python/README.md` — the Python side (model exporter and adapters)

## Layout

- `src/kimun/{kernel,host,surface/markdown}/` — rings 0/1/2 of the kernel (`bb lint` enforces the rings)
- `src/kimun/cli/` — the CLI (ring 3, `kimun.*` may require the kernel rings, never the reverse)
- `test/`, `test/fixtures/cli/` — suite and golden envelopes
- `scripts/` — `check_rings.clj` (lint), `canon_oracle.py` (oracle), `release.clj` (bundles)
- `release.edn` — pinned Babashka version, platform matrix and asset digests
- `kimun.include.edn` — what this repo versions in git; the store itself is built, not committed
- `docs/v2/` — direction (highest authority); `docs/archive/` — historical material
- `desk/` — tracked desk sources (atoms, tasks, models, rituals); `.kimun/` — the built store
- `raw/source/` — frozen source material; `runs/` — execution evidence

## Lineage

Cloned from `jpcosec/sldb` branch `refactor-target` (history preserved). Three things carry
the word "knowledge" around here; they are distinct (`docs/v2/08 §0`):

| name | what | repo |
|---|---|---|
| `kimun` | this product: SLDB v2 kernel + models + CLI, binary `kimun` | `jpcosec/kimun` |
| `pron` | the provenance knowledge base and the Python evaluator over `.sldb` v1 + kgdb, in one package (pip `pron`); alive, imported by `kinesis` | `jpcosec/pron` |
| `knowledge` | frozen: the July 2026 file-based CLI only | `jpcosec/knowledge` |

`sldb` v1 and `kgdb` are frozen (`v1-frozen`). License: MIT.
