# pod-kimun-treesitter

Babashka pod that exposes [tree-sitter](https://tree-sitter.github.io/) parsing
to kimun: the parser of the **M layer** (Markdown blocks and, later, code),
decision 29 in `docs/v2/05-estado.md`. The pod is a single native executable
implementing the [Babashka pod protocol](https://book.babashka.org/#pods)
(bencode over stdin/stdout, JSON payloads). The grammars are Rust crates that
compile the generated `parser.c`/scanner *inside* the binary, so there is no
`.so` per grammar to ship and no external runtime.

## Build

Requires a Rust toolchain (developed against `rustc 1.95.0`; see
`rust-toolchain.toml`). From this directory:

```sh
# dynamic build (libc/libgcc), ~3.1 MB
cargo build --release

# static-pie build, ~4.1 MB — this is the one bundled per platform
RUSTFLAGS="-C target-feature=+crt-static" CARGO_TARGET_DIR=target-static cargo build --release
```

Both land under `target*/release/pod-kimun-treesitter`. The repo task
`bb pod` wraps the dynamic build:

```sh
bb pod          # cargo build --release in pods/treesitter
```

`target/` and `target-static/` are never committed (root `.gitignore`).

## Runtime discovery

`kimun.host.treesitter` (ring 1) is the only namespace that loads the pod, and
it looks for the binary in this order:

1. `$KIMUN_POD` — explicit override (path to the executable).
2. `<dir of bb>/../lib/pod-kimun-treesitter` — the layout of a release bundle,
   next to the bundled `bb` and `kimun.jar` (`release.edn` `:pods`).
3. `<repo>/pods/treesitter/target/release/pod-kimun-treesitter` — development.

If none exists the host reports the pod as unavailable and the surfaces fall
back to the in-process parser (`kimun.surface.models.blocks` native path,
`KIMUN_PARSER=native`); tests that require the pod are reported as `skipped`.

## Vars

| var | signature | shape |
|---|---|---|
| `version` | `(version)` | `{:pod :version :tree_sitter :languages}` |
| `blocks` | `(blocks text)` | flat top-level block list `[{:type :start_byte :end_byte :start_point :end_point}]` |
| `parse-block` | `(parse-block text)` | block CST as `[root]` (nodes with `:type :start_byte :end_byte :start_point :end_point :named :children?`) |
| `parse` | `(parse text)` | combined block+inline CST |
| `parse-inline` | `(parse-inline text)` | inline CST |
| `parse-lang` | `(parse-lang text language)` | CST with `markdown`/`markdown-inline`/`python` |
| `query` | `(query text query-string language)` | capture list |
| `bench` | `(bench text iters)` | timings |

For segmentation use `blocks`, **not** `parse`: the full CST JSON is roughly
15× the input and must never be materialized in Babashka (R2 §2).

## Grammars

`tree-sitter` 0.26, `tree-sitter-md` 0.5.3 (block + inline), `tree-sitter-python`
0.25; versions are pinned by `Cargo.lock`.

Known grammar behaviour that the host normalizes (R2 §6):

- an ATX heading with no trailing newline makes the block grammar return an
  ERROR root; the host appends a final `\n` before parsing.
- the `list` node absorbs one trailing blank line; the host trims it when
  mapping spans back to line numbers.
