# kimun

**kimun sees, synthesizes and generates.** The name is Mapudungun (*kimün*, knowledge).

- Knowledge is propositions `(R A B)` in context, written as Clojure that runs
  (`docs/v2/00-nucleo.md`). Things mean by their relations.
- Reality lives in the sources (documents, code), not in kimun. kimun asks nothing of them:
  it **sees** them (up to the file level it uses **git**; inside a file it watches only what is
  anchored), **synthesizes** a graph of propositions, and **generates** (materializes) documents
  and answers from that graph.
- Redoing git (file-level monitoring of its own) is explicitly deferred; kimun uses git for it.

Owner's words, from `docs/v2/drafts/sesion-2026-11-15.md`: «Kimun solo ve, sintetiza y genera, no
debería exigir nada al resto.»

## Status

**kimun is being re-architected; there is no published release.** What exists today is the
previous design's implementation, pure Clojure (`.cljc`) on Babashka, in the middle of being cut
back toward the direction above (the deletion cuts are in progress):

- A content-addressed store `.kimun/`, with Markdown documents parsed (tree-sitter pod),
  anchored and rendered; typed documents, fields and sections, marks on units, an index,
  `find`, edges and a graph view, semantic tags, and an HTTP `serve`.
- Not there yet: declarations as Clojure that runs, relations as nodes, several classes per
  node, code nodes, a git-based sensor as the default, materialization from the graph, MCP.
  Parity with sldb v1 is suspended. The contextual `G` layer waits for owner decision `D-G`:
  its capabilities (`docs/v2/tests/wittgenstein-manifest.md`) are not yet implemented.

The plan, in stages, is `docs/v2/drafts/plan-producto.md` (approved by the owner on 2026-11-16);
the session that changed the frame is `docs/v2/drafts/sesion-2026-11-15.md`; its design is
`docs/v2/drafts/diseno-declaraciones.md`. Those are drafts, and they are the current direction.

## Install

There is no published release: no tag exists and the release workflow has never run. From a
checkout, `bin/kimun` is a development launcher (needs `bb` ≥ 1.13 on PATH, and the tree-sitter
pod built with `bb pod` for the tree-sitter parser).

`bb release --local-bb --platforms linux-amd64` builds a local bundle
(`dist/kimun-<ver>-linux-amd64.tar.gz`: `bin/kimun`, `lib/bb`, `lib/kimun.jar`,
`lib/pod-kimun-treesitter`, `MANIFEST.edn` with the sha256 of the packed pod). It needs the pod
(`bb pod`) and is not a distribution channel. Only **linux-amd64** is built and tested. A bundle
never carries another platform's pod: without a pod compiled for its platform the build fails,
unless `--without-pod` asks for a bundle with no pod (declared in `MANIFEST.edn`; its launcher
refuses `KIMUN_PARSER=treesitter`). The other platforms and the Windows launcher
(`bin/kimun.cmd`) are not built by CI and have never been run. `release.edn` pins no pod hash:
no pod has been published yet.

## Five-minute tour

```bash
bin/kimun stores init --name my-kb          # create .kimun/ in the working directory
bin/kimun docs ingest notes.md --name notes # parse Markdown and commit it as a document
bin/kimun docs list --format text           # name, tree id, and for typed ones model/state/path
bin/kimun docs render notes                 # canonical Markdown of a document
bin/kimun find "some term"                  # physical, semantic and address search
bin/kimun stores health                     # verdict: ok | degraded | needs-human
```

Every command prints one envelope (`--format json|edn|text`) and exits with a documented
code. `bin/kimun --help` lists every group.

## Build and test

```bash
bb lint && bb test && bb oracle   # rings/docstrings, suite, canonical-bytes oracle (needs Python 3)
bb jar                            # target/kimun.jar (built-in `bb uberjar`)
bb desk                           # build this repo's desk store (.kimun) from kimun.include.edn
```

## Read next

- `docs/v2/drafts/plan-producto.md` — the approved plan, by stages; `docs/v2/drafts/sesion-2026-11-15.md` — the owner's words
- `docs/v2/README.md` — authority table and reading order for the direction docs
- `docs/v2/05-estado.md` — current status and decisions
- `docs/v2/06-superficies-y-cli.md` — the CLI inventory and the envelope/exit-code contract
- `docs/v2/tests/wittgenstein-manifest.md` — inclusion manifest (`C01–C28`, Wittgenstein `W1`/`W2`) and current status
- `docs/faq.md` — scope and how to read "desk" here

## Layout

- `src/kimun/{kernel,host,surface/markdown}/` — rings 0/1/2 of the kernel (`bb lint` enforces the rings)
- `src/kimun/cli/` — the CLI (ring 3, `kimun.*` may require the kernel rings, never the reverse)
- `test/`, `test/fixtures/cli/` — suite and golden envelopes
- `scripts/` — `check_rings.clj` (lint), `canon_oracle.py` (oracle, Python 3), `release.clj` (bundles)
- `release.edn` — pinned Babashka version, platform matrix and asset digests
- `kimun.include.edn` — what this repo versions in git; the store itself is built, not committed
- `docs/v2/` — direction (highest authority); `docs/archive/` — historical material
- `desk/` — tracked desk sources; `.kimun/` — the built store (the include declares no models or documents today)
- `docs/archive/raw/source/` — frozen source material; `runs/` — execution evidence

## Lineage

Cloned from `jpcosec/sldb` branch `refactor-target` (history preserved). Three things carry
the word "knowledge" around here; they are distinct (`docs/v2/08 §0`):

| name | what | repo |
|---|---|---|
| `kimun` | this product, binary `kimun`; successor of `sldb` v1 and `kgdb` | `jpcosec/kimun` |
| `pron` | the provenance knowledge base and the Python evaluator over `.sldb` v1 + kgdb, in one package (pip `pron`); alive, imported by `kinesis` | `jpcosec/pron` |
| `knowledge` | frozen: the July 2026 file-based CLI only | `jpcosec/knowledge` |

`sldb` v1 and `kgdb` are frozen (`v1-frozen`). License: MIT.
