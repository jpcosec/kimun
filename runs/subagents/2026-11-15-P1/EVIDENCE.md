# P1, first cut — the graph package `kimun.graph.*` and its ring

Base: `main` at `2c56e680` (C4). Backlog entry: `docs/v2/12-ecosistema-y-pendientes.md §1`, P1
(`docs/v2/14-juego-del-lenguaje.md §19` rule 3, decision 43). Contract of the ring:
`docs/v2/03-estandares-de-codigo.md §1`.

## What the cut is

- Six namespaces moved, content unchanged apart from the namespace name:
  `kimun.surface.graph.{model,io,query,traversal,analysis,store}` → `kimun.graph.*`
  (`src/kimun/graph/`). Their tests and JSON fixtures moved to `test/kimun/graph/`.
- `scripts/check_rings.clj` (`bb lint`):
  - ring `kimun.graph.`: may require `clojure.`, `kimun.kernel.`, `kimun.surface.`,
    `kimun.graph.`, `cheshire.`; never `kimun.cli.`;
  - only `kimun.graph.*` and `kimun.cli.*` may require `kimun.graph.*`;
  - `pending-graph-crossings`: the crossings still open, one by one. Today one:
    `kimun.surface.semantic` → `kimun.graph.io` (the semantic export). A crossing not
    listed fails; a listed one that is no longer required fails too.
- Callers updated: `src/kimun/cli/commands/graph.cljc`, `src/kimun/surface/semantic.cljc`
  (the require line), a docstring in `src/kimun/surface/edges.cljc`,
  `test/kimun/cli/graph_test.cljc`.
- Not touched: `src/kimun/kernel/store.cljc`, `src/kimun/cli/commands/stores.cljc`,
  `src/kimun/surface/docs.cljc`, the backlog and `docs/v2/12`.

## Validation

- `lint.txt`: `bb lint` — rings and docstrings ok, 103 files.
- `targeted-tests.txt`: `kimun.lint-test`, the five `kimun.graph.*-test`,
  `kimun.cli.graph-test`, `kimun.cli.semantic-test`, `kimun.surface.semantic-test`,
  `kimun.surface.edges-test`, `kimun.surface.edges-parity-test` — 101 tests,
  1446 assertions, 0 failures, 0 errors.
- `full-test.txt`: `bb test` with the tree-sitter pod available — 1009 tests, 11532
  assertions, 0 failures, 0 errors, 123 namespaces, exit 0. It ran on this cut plus the
  fix of `test/kimun/cli/journal_test.cljc`, which is the next commit
  (`runs/subagents/2026-11-15-journal-verify-test/`).
- The first full run on this base gave 1008 tests, 4 failures, all in
  `kimun.cli.journal-test/verify-detects-corrupt-object`: an intermittent test that
  does not depend on this cut (cause and fix in that directory). Its output was
  replaced by the run above.
- `oracle.txt`: `bb oracle`, 9 rows, 0 mismatches.
- `include-check.json`: `kimun include check` on this repository, ok.
- `cli-parity.txt`: eleven `graph` commands run on the same temporary store
  (`/tmp/p1-graph-ev`, two linked documents) with the CLI of `main` at `3902731d` and
  the CLI of this cut before it was rebased onto `2c56e680` (C4 does not touch the
  `graph` commands): stdout and exit code byte-identical in all eleven. The
  semantic export is not in that table (it carries a timestamp); its tests are in the
  targeted run.

Every `bb` call ran through `~/bin/memcap`.

## New tests (`test/kimun/lint_test.cljc`)

- a namespace of the base (`kimun.surface.*`, `kimun.surface.index.*`,
  `kimun.surface.markdown.*`, `kimun.host.*`, `kimun.kernel.*`) requiring
  `kimun.graph.model` fails lint;
- `kimun.graph.*` may require the surface and itself, `kimun.cli.*` may require the
  graph, `kimun.graph.*` requiring `kimun.cli.*` fails;
- the pending crossing passes exactly as written, a second graph namespace from the
  same place fails, and the entry fails once the crossing is gone.

## Limits

- This is the first cut of P1, not P1. Models, semantic edges and the derived edge
  view (`kimun.surface.models.*`, `kimun.surface.semantic`, `kimun.surface.edges`)
  are still surface namespaces: `kimun.surface.docs`, `fields`, `sections`,
  `diagnostics` and `index.*` require them, so moving them means first separating
  anchoring from generation (spec 16), which depends on D0. No identity decided here.
- One crossing stays open and declared: `kimun.surface.semantic` → `kimun.graph.io`.
- No alias left under the old names: nothing in `src/`, `test/`, `scripts/` or
  `python/` referred to them besides the files updated. Code outside this repository
  requiring `kimun.surface.graph.*` would break; none is known.
- The lint tests call `bb` as a subprocess without `memcap`, as the existing ones in
  that file do (the lint reads source files only).
