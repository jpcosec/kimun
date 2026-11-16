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

**kimun is being re-architected; there is no published release.** The direction is the one
above; the plan, in stages, is `docs/v2/drafts/plan-producto.md` (approved by the owner on
2026-11-16; its last section says how far each stage is). What works today, in two blocks.

**New, no store needed** (reads files; writes nothing unless it says so):

- Declarations in Clojure under `kimun/`: `declare read` evaluates them (isolated, with `sci`)
  into propositions `(R A B)`. A context is the file's `ns`; contexts `:require` each other to
  share relations.
- `declare add` appends one form to a declaration file (the only write of the group).
- `declare anchors [--since COMMIT]` resolves the `(at …)` anchors of declarations against the
  files; git says which files changed, and only those are opened.
- Asking: `declare terms`, `declare about TERM | --path PATH`, `declare members 'PATTERN'`.
- `kimun mcp`: an MCP server over stdio with nine tools (the `declare` commands and read-only
  `stores` ones). Not tried against a real MCP client.

**Earlier, with a store** (`.kimun/`, content-addressed): Markdown documents (tree-sitter pod),
typed documents with models, fields, sections and marks on units, an index and `find`, edges
and a graph view, semantic tags, and `serve` (HTTP; loopback by default, paths confined to the
project, a token required off loopback or with `--cors`). The sensor asks git: `stores
changed|seen`, and `stores sense|health|heartbeat` open only the documents git says changed
(`:scope`; `--full` reads them all). The journal, packs and `docs status` were deleted.

**Not there yet** (checked against the code):

- Generating from the propositions: `declare generate` is being implemented
  (`docs/v2/drafts/diseno-generar.md`); it is not in `main`.
- Reading code: there are no code nodes, and the tree-sitter pod has the Markdown and Python
  grammars (`pods/treesitter/Cargo.toml`), not Clojure.
- Declarations do not replace the old store: the two paths live side by side.
- The contextual `G` layer waits for owner decision `D-G`; its capabilities
  (`docs/v2/tests/wittgenstein-manifest.md`) are not yet implemented.
- A published release (see Install). Parity with sldb v1 is suspended.

Who decided what: the direction is the owner's words (`docs/v2/drafts/sesion-2026-11-15.md`).
`diseno-declaraciones.md`, `diseno-evaluar.md` and `diseno-generar.md` (same folder) are drafts
by the coordinator: his choices, not owner decisions.

## Install

There is no published release: no release tag exists (`git tag` lists two old, non-release tags) and the release workflow has never run. From a
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

Declarations, no store. A minimal project with one commit (needs `bb` ≥ 1.13; `kimun` stands
for this checkout's `bin/kimun`, run from the project directory):

```bash
mkdir -p kimun docs && git init -q .
printf '# Spec\n\n## Cache\n\nThe cache is derived and may be deleted.\n' > docs/spec.md
cat > kimun/base.clj <<'EOF'
(ns base "Relations every context of this project can use.")
(relation isa "A is an instance of the class B.")
(relation anchors "A is the text that B stands on.")
(relation decides "A is a decision about B.")
EOF
cat > kimun/decisiones.clj <<'EOF'
(ns decisiones "Decisions of the project." (:require [base :refer [isa anchors decides]]))
(isa (files "docs/**/*.md") Doc.spec)
(anchors cache-decision (at "docs/spec.md" ["Spec" "Cache"]))
(decides cache-decision Cache)
EOF
git add -A && git commit -qm init

kimun declare read --format text                   # three propositions, each with file and line
kimun declare anchors --since HEAD --format text   # the anchor is :anchored; no file was opened
sed -i 's/may be deleted/can be deleted/' docs/spec.md
kimun declare anchors --since HEAD --format text   # now :mutated: git said it changed, kimun opened it
kimun declare about Cache --format text            # what the declarations say about a term
kimun declare add --to kimun/decisiones.clj '(decides cache-decision Spec.format)' --format text
```

Also `declare terms`, `declare about --path docs/spec.md` and `declare members 'docs/**/*.md'`.
A real session of this tour is in `runs/subagents/2026-11-16-docs-al-dia-2/tour.txt`. Every
command prints one envelope (`--format json|edn|text`) and exits with a documented code;
`bin/kimun --help` lists every group.

The store, in a git repository (`.kimun/` is created in the working directory):

```bash
kimun stores init --name my-kb          # create .kimun/
kimun docs ingest notes.md --name notes # parse Markdown and commit it as a document
kimun docs list --format text           # name, tree id, class
kimun docs render notes                 # canonical Markdown of a document
kimun find cache --format text          # a word of the text: the section that holds it, and its line
kimun stores seen --set HEAD            # the pointer "looked up to this commit"
kimun stores health --format text       # verdict: ok | degraded | needs-human (:scope :git)
```

## Build and test

```bash
bb lint && bb test && bb oracle   # rings/docstrings, suite, canonical-bytes oracle (needs Python 3)
bb jar                            # target/kimun.jar (built-in `bb uberjar`)
bb desk                           # build this repo's desk store (.kimun) from kimun.include.edn
```

## Read next

- `docs/v2/drafts/plan-producto.md` — the approved plan, by stages, with its progress; `docs/v2/drafts/sesion-2026-11-15.md` — the owner's words
- `docs/v2/drafts/diseno-declaraciones.md`, `diseno-evaluar.md`, `diseno-generar.md` — the coordinator's design drafts (declare, evaluate, generate)
- `docs/v2/README.md` — authority table and reading order for the direction docs
- `docs/v2/05-estado.md` — current status and decisions
- `docs/v2/06-superficies-y-cli.md` — the CLI inventory and the envelope/exit-code contract
- `docs/v2/tests/wittgenstein-manifest.md` — inclusion manifest (`C01–C28`, Wittgenstein `W1`/`W2`) and current status
- `docs/faq.md` — scope and how to read "desk" here

## Layout

- `src/kimun/{kernel,host,surface/markdown}/` — rings 0/1/2 of the kernel (`bb lint` enforces the rings)
- `src/kimun/surface/` — the surfaces over them: declarations (`declare.cljc`, `declared_anchors.cljc`, `propositions.cljc`), `mcp.cljc`, `changes.cljc` (what git says changed), diagnostics, HTTP, models, index
- `src/kimun/graph/` — the graph package (over the base, never the CLI)
- `src/kimun/cli/` — the CLI (`kimun.*` may require the kernel rings, never the reverse)
- `test/`, `test/fixtures/cli/` — suite and golden envelopes
- `scripts/` — `check_rings.clj` (lint), `canon_oracle.py` (oracle, Python 3), `release.clj` (bundles), `store_size.clj`, `tour_mutation.sh`, `bench/`
- `pods/treesitter/` — the tree-sitter pod (Rust; `bb pod`)
- `release.edn` — pinned Babashka version, platform matrix and asset digests
- `kimun.include.edn` — what this repo versions in git; the store itself is built, not committed
- `kimun/` (in a project kimun observes, not in this repo) — the folder of its declarations: `*.clj`, one context per file
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
