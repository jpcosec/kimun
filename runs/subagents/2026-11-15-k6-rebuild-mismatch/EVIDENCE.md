# K6 — `rebuild-mismatch` (`docs/v2/10-escala.md §11.1`)

- **Base:** `main` at `a9c27007` (includes `C5` `b4931b7c` + `docs C5` `a9c27007`, `test-opt` `2f473155`, `DOC1` `5def87de`).
- **Worktree:** `/tmp/kimun-k6-wt` (detached HEAD, single commit fast-forwarded onto `main`).
- **Scope guard:** No changes to `D0`, `M1`, or `test/kimun/cli/diagnostics_test.cljc`.

## Contract & Changes

1. **Three-way distinction (`docs/v2/10-escala.md §11.1`):**
   - Mutated source (`content-ok = false`): `:missing`, `:reformatted`, `:edited` (`:source/*`).
   - Changed / broken parse contract (`content-ok = true`, `contract-ok = false`): `:contract-moved` (`:contract/changed`), `:parser-unavailable` (`:contract/parser-unavailable`), `:contract-inconsistent` (`:contract/inconsistent`).
   - Divergent reconstruction under identical source fingerprint and identical recorded parse contract (`content-ok = true`, `contract-ok = true`, registered `:merkle-root` != rebuilt root or `:recorded-content-hash` != recomputed content hash): `:rebuild-mismatch` (`:contract/rebuild-mismatch`, CLI warning `"docs/rebuild-mismatch"`), `:needs-human true`, carrying `:registered-root` and `:rebuilt-root` (plus `:registered-content-hash` / `:rebuilt-content-hash` when applicable), and repair action `"kimun docs update <doc>"`.
2. **Pure in-memory recalculation (no `<store>/cache/` writes on observation):**
   - `src/kimun/surface/docs.cljc`:
     - `describe` now includes `:merkle-root` for typed documents (matching `describe-generic`).
     - Added pure in-memory `rebuild-tree` and `rebuilt-root` (shared with `generic-root`), writing nothing to the store or `<store>/cache/`.
     - `doc-tree` only invokes `cache-write!` when `(not stale?)` (`(= merkle (:merkle-root reg))`), preventing stale files or `rebuild-mismatch` trees from overwriting `<store>/cache/`.
     - `update-plan` and `generic-update-plan` include `:merkle-root` (and `:recorded-content-hash` for typed docs) in `same?` so `kimun docs update` re-registers and repairs a `:rebuild-mismatch` even when path, fingerprint, and parse-contract are unchanged.
3. **Diagnostics, Signals & CLI Warnings:**
   - `src/kimun/surface/diagnostics.cljc`: updated `classify`, `diagnose-generic`, `action-of`, `message-of`, and `summary` for `:rebuild-mismatch` (`:needs-human true`, `:content-ok true`, `:contract-ok true`). Note: when both contract and root differ, `:contract-moved` (`:contract-ok false`) wins; when source differs, source categories (`:content-ok false`) win.
   - `src/kimun/surface/signals.cljc`: added `:rebuild-mismatch :contract/rebuild-mismatch` to `category->type` and included `:registered-root`, `:rebuilt-root`, `:registered-content-hash`, `:rebuilt-content-hash` in `detail-of`.
   - `src/kimun/cli/commands/stores.cljc`: added `:rebuild-mismatch "docs/rebuild-mismatch"` to `warning-type`.
4. **Tests & Governing Docs:**
   - `test/kimun/surface/signals_test.cljc`: signal mapping + root detail fields (`rebuild-mismatch-maps-to-contract-signal-with-roots-detail`).
   - `test/kimun/surface/diagnostics_test.cljc`: typed and generic `rebuild-mismatch`, precedence (`contract-moved` and `edited`), simulated parser divergence, `<store>/cache/` non-write assertion, and `doc-tree` non-write check (`distinguishes-source-contract-and-rebuild-mismatch-without-writing-cache`).
   - `test/kimun/cli/contract_test.cljc`: end-to-end `stores sense` / `stores check` / `stores update` / `docs update` over typed + generic documents (`rebuild-mismatch-is-sensed-never-writes-cache-and-repairs-with-docs-update`), verifying `<store>/cache/` untouched until human runs `docs update`.
   - `docs/v2/10-escala.md §11.1`, `docs/v2/06-superficies-y-cli.md`, `docs/v2/12-ecosistema-y-pendientes.md §1`, `docs/v2/14-juego-del-lenguaje.md §12/§13/§19`, and `test/kimun/doc14_contract_test.cljc`: recorded `K6` implemented for Markdown (`555b2dbc` + `rebuild-mismatch`); code-document selectors remain pending `M1`.

## Validation (exclusive run under `~/bin/memcap`)

- `lint.txt`: `~/bin/memcap bb lint` — `rings and docstrings ok: 104 files`, exit 0.
- `oracle.txt`: `~/bin/memcap bb oracle` — `9 rows, 0 mismatches`, exit 0.
- `include-check.txt`: `~/bin/memcap bin/kimun include check --format text` (run from `/home/jp/proyectos/kimun` against K6 code in `/tmp/kimun-k6-wt`) — `ok: true`, all finding counters `0`, exit 0.
- `focused-tests.txt`: `kimun.surface.signals-test`, `kimun.surface.diagnostics-test`, `kimun.cli.contract-test`, `kimun.doc14-contract-test` — `Ran 32 tests containing 396 assertions. 0 failures, 0 errors.`, exit 0.
- `full-test.txt`: `~/bin/memcap bb test` with temporary `pods/treesitter/target` symlink (removed before staging/commit):
  - `tree-sitter pod: available`
  - `Ran 127 namespaces in 127 s`
  - `{:test 1061, :pass 11913, :fail 0, :error 0, :namespaces 127}`, exit 0.
  - (`kimun.adapters.python-unittest-test`: `Ran 32 tests in 1.859s | OK (skipped=1)`; `SKIP kimun.adapters.treesitter-test: tree_sitter javascript grammar not available (set KIMUN_ADAPTER_PYTHON)`; `SKIPPED kimun.cli.migrate-test/real-store-sample-extracts-as-in-v1: /home/jp/proyectos/hum-ecosystem/tools/sldb/.sldb is not on this machine`.)
