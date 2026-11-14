# C1 — Chequeo de actualidad de nodos fuente (`test_kgdb_graph_check_rejects_missing_or_stale_source_nodes`)

Fecha: 2026-11-14
Base commit: `b776ae42`
Worktree: `/tmp/kimun-c1-kgdb-wt`

## Alcance y análisis de dependencias (`P1` / `D0`)

- La única prueba aún `abierta` en `docs/v2/tests/parity-v1.md` era `selfdoc/test_python_scanner.py::test_kgdb_graph_check_rejects_missing_or_stale_source_nodes` (renombrada posteriormente en `SLDB_1/sldb` a `test_source_graph_check_rejects_missing_or_stale_source_nodes`).
- No depende de `D0` ni del paquete `P1`: `docs/v2/12-ecosistema-y-pendientes.md §1` asigna a `C1` dependencias `—`, y el chequeo opera completamente sobre hechos de adaptadores, documentos `PythonSymbolDoc` del store de `kimun` y snapshots de nodos en `kimun.surface.adapters` + `kimun.cli.commands.adapters` (anillo `kimun.`), sin requerir `kimun.graph.*` ni archivos KGDB externos.
- No se tocaron `src/kimun/surface/docs.cljc`, `src/kimun/cli/commands/docs.cljc`, `src/kimun/cli/commands/stores.cljc` ni `docs/v2/06-superficies-y-cli.md`.

## Contrato implementado

1. **`src/kimun/surface/adapters.cljc`**:
   - `source-node`: normaliza a `{:id str :source map :ast map}` cualquier nodo fuente — documentos `PythonSymbolDoc` planeados (`:fields`) o rastreados (`:payload`), hechos del protocolo de adaptadores (`source_hash`, `path`, `span`, `kind`, `qualified_name`) y snapshots/grafos v1 (`identity.node_id`, `schema.source`, `schema.ast`).
   - `source-graph-check`: compara nodos esperados contra nodos actuales y retorna `{:current? bool :status kw :missing [ids] :stale [ids] :orphan [ids]}` con `:status` ∈ `{:missing :unreadable :stale :current}`:
     - `:missing` cuando el conjunto destino es `nil`.
     - `:unreadable` cuando la entrada está mal formada, no contiene una colección válida de nodos o tiene IDs duplicados.
     - `:stale` cuando falta algún nodo esperado, difiere `:source` (`path` / `sha256`) o `:ast` (`kind` / `module` / `name` / `span`), o sobra un nodo huérfano/obsoleto.
     - `:current` únicamente cuando el conjunto de nodos y todos sus campos `:source` y `:ast` coinciden exactamente.
   - `source-graph-current?`: predicado booleano sobre `source-graph-check`.
2. **`src/kimun/cli/commands/adapters.cljc`**:
   - `inspect` calcula `:source-graph` con `planner/source-graph-check` sobre nodos `PythonSymbolDoc` planeados vs rastreados, detecta archivos `:unchanged` modificados externamente en disco (`"modified-externally"` vía `before-images`), refleja documentos `missing-file` y `modified-externally` en `:source-graph`, y propaga `:source-graph` en el payload de `adapters check` / `python-check` (fallo `:adapters/missing`, exit 1, cuando hay deriva).

## Tests y documentación añadidos

- `test/kimun/surface/adapters_test.cljc`: `source-graph-check-rejects-missing-or-stale-source-nodes` (contrato exacto del snapshot v1 + `PythonSymbolDoc` planeados vs rastreados).
- `test/kimun/cli/adapters_test.cljc`: `kgdb-graph-check-rejects-missing-or-stale-source-nodes` (flujo real `python-check` / `python-sync` para estados missing, current, stale `source_sha256`/AST, `"modified-externally"` en disco y nodo huérfano tras eliminar símbolos).
- `test/kimun/parity/adapters_parity_test.cljc`: `kgdb-graph-check-rejects-missing-or-stale-source-nodes` (contrato v1 + `python-check` / `python-sync` sobre el fixture congelado `composer.py`).
- `docs/v2/11-adaptadores.md`, `docs/v2/tests/parity-v1.md` (`portada` `519` → `520`, `abierta` `1` → `0`), `docs/v2/12-ecosistema-y-pendientes.md` (`C1` cerrada).

## Validación y archivos de corrida

- Tests focalizados (`focused-tests.txt`): `kimun.surface.adapters-test`, `kimun.cli.adapters-test`, `kimun.parity.adapters-parity-test` — `Ran 47 tests containing 1929 assertions. 0 failures, 0 errors.`
- Cobertura v1 (`coverage.txt` contra `/home/jp/.cache/uv/git-v0/checkouts/cc76a327e48ac903/d877dad`): `selfdoc/test_python_scanner.py` = `6/6 ported, 0 open`; `TOTAL`: `502 total, 442 ported, 1 mapped, 11 oracle, 48 n/a, 0 gap, 0 ment., 0 corpus, 0 open`.
- Oráculo canónico (`oracle.txt`): `9 rows, 0 mismatches`.
- Verificación del desk/include (`include-check.txt`): `ok: true` (`findings` todos en `0`).
- Suite completa exclusiva (`full-test.txt`, bajo `~/bin/memcap` y con `tree-sitter pod: available`):
  - `rings and docstrings ok: 103 files`
  - `tree-sitter pod: available`
  - `python unittest: Ran 32 tests in 1.833s | OK (skipped=1)`
  - `Ran 122 namespaces in 137 s`
  - `{:test 1002, :pass 11483, :fail 0, :error 0, :namespaces 122}`
