# DOC1 (F18) — Thematic reorganization of `docs/v2/14-juego-del-lenguaje.md`

Base: `main` at `971b6b2c` (after `P1` first cut `70b8abe9` and journal verify test fix `971b6b2c`).
Worktree: `/tmp/kimun-doc1-wt`.

## Scope

- `docs/v2/14-juego-del-lenguaje.md`: reorganized by topic (`Tema I`–`Tema VI`), integrating addenda `§16`–`§21` and explicitly separating **target contract**, **implemented state**, **open decisions (`D0`, `D-G`)**, and **historical record**:
  - Preserved all incoming citation anchors across the repo and tests (`## 1.`–`## 21.`, `§18.1`–`§18.9`, `P1`–`P15`, decisions `21`–`46`, `etapa S.2`, `:content :inline`, `markdown/source`, `Q1`–`Q15`).
  - Kept the three `2026-11-14` owner direction blocks in the header and kept `D0` explicitly open in `§21` without inventing any positive node identity.
  - Reconciled `F18` contradictions: `P15` aligned with decision `38` (multilingual without fixed language/template; `Q13` initial default marked superseded), historical `§10` distinguished from in-place `2→3` upgrade in `10-escala.md`, `:contract` located on the `reference` edge (`root → external`, decision `40`), and decisions `24`/`31` + `§5` marked `EN REVISIÓN (D-G)`.
  - Aligned status tables (`§12`, `§13`, `§19`, `§20`) with `main` at `971b6b2c`:
    - Separated October base Stage S (`etapa S.2` blocks pod) from `S1` phase 2 partial (`65ca4b5f`, `03a9f552`, `6930afa8`, `735/735` SLDB-MD identical, ~3% free Markdown open) and `S2` phase 3 blocked by `K1`/`D0`.
    - Marked `K2a` as **partial** (cuts `C2a`/`C2b`/`C4` implemented in `3b2d6d5d`..`2c56e680` with `route-root` as a flat `O(n)` sorted-entries digest; incremental Merkle tree structure with local descent/cost remains open as `C5`).
    - Marked `K6` as **partial** (`555b2dbc` digest/comparison implemented; `rebuild-mismatch` remains open).
    - Recorded `K3` (`076be35b`, `43d9db6d`), `K8` (`bcd85a53`), `C1` (`cdce1cb9`, `3902731d`), `T01` (`9e106029`), `M1` spec (`ac09b764`), `stores sense|heartbeat` (`c07c0e0c`, `86a0ba42`, `08e2bbb4`, `f0619f7a`), `P1` first cut (`70b8abe9` + `971b6b2c`), and `DOC1` closure.
- `docs/v2/12-ecosistema-y-pendientes.md`: updated backlog status for `K2a` (partial after `C2a`/`C2b`/`C4`; `C5` incremental Merkle pending), `K2b`/`K4`/`K5`/`G3`/`C2` dependencies, `P1` (first cut `70b8abe9` + `971b6b2c`), `K3`, `K6`, `K8`, `S1`, and `DOC1` closed (`2026-11-15`).
- `docs/v2/README.md`: updated `14-juego-del-lenguaje.md` description row.
- `test/kimun/doc14_contract_test.cljc`: automated contract test (`4 tests`, `146 assertions`) checking thematic structure, preserved anchors `## 1.`–`## 21.`, `§18.1–9`, `P1–P15`, `21–46`, `Q1–Q15`, `D0`/`D-G` guards, `F18` reconciliations, `S1`/`S2`, `K2a` (`O(n)` flat digest + `C5` pending), `K3`/`K8`/`K6`/`P1` status markers.

## Validation (exclusive run under `~/bin/memcap`)

- `lint.txt`: `~/bin/memcap bb lint` — `rings and docstrings ok: 103 files`, exit 0.
- `oracle.txt`: `~/bin/memcap bb oracle` — `9 rows, 0 mismatches`, exit 0.
- `include-check.txt`: `~/bin/memcap bin/kimun include check --format text` — `ok: true`, all finding counters `0`, exit 0.
- `focused-tests.txt`: `kimun.doc14-contract-test`, `kimun.spec16-models-contract-test`, `kimun.wittgenstein-manifest-test` — `Ran 12 tests containing 1166 assertions. 0 failures, 0 errors.`, exit 0.
- `full-test.txt`: `~/bin/memcap bb test` with temporary `pods/treesitter/target` symlink (removed before staging/commit):
  - `tree-sitter pod: available`
  - `Ran 124 namespaces in 149 s`
  - `{:test 1013, :pass 11678, :fail 0, :error 0, :namespaces 124}`, exit 0.
  - (`kimun.adapters.python-unittest-test`: `Ran 32 tests | OK (skipped=1)`; `kimun.adapters.treesitter-test`: `SKIP kimun.adapters.treesitter-test: tree_sitter javascript grammar not available (set KIMUN_ADAPTER_PYTHON)`.)
