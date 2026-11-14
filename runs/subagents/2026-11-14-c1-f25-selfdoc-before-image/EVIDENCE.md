# Evidencia: C1/F25 guarda de before-image en `kimun.cli.commands.adapters`

## Alcance e independencia del corte

- **Implementado dentro de `kimun` sin tocar `SLDB_1`, `src/kimun/kernel/*`, `src/kimun/surface/docs.cljc`, `src/kimun/cli/commands/docs.cljc`, `src/kimun/cli/commands/fields.cljc` ni `src/kimun/surface/markdown/*`.**
- Nota de frontera (F25 general): la atomicidad transaccional entre commit del store y `spit` en `fields.cljc::mutation` y `docs.cljc::create/update-typed` (`reviews/02-findings.md` F25) quedó fuera del corte por la restricción explícita de no editar `kernel/store` ni `docs CLI` mientras K3 trabaja sobre `docs`; este corte cierra la parte selfdoc (`kimun adapters sync` / `selfdoc-sync` / `python-sync`) y la promesa `tests/selfdoc/test_preflight.py::test_before_image_guard_prevents_clobbering_concurrent_edits`.

## Qué cambió

1. `src/kimun/cli/commands/adapters.cljc`:
   - `tracked` retiene `:source` y `:fingerprint` desde `tdocs/document`.
   - `adoptable` retiene `:previous` (el texto exacto leído en disco al planificar).
   - `current-source`, `matches-tracked-source?`, `expected-before-image`, `before-images-for` e `inspect` (ahora pública) registran `:before-images` para cada ruta planificada: `:previous` en adopciones, `disk-text` cuando coincide con el `:fingerprint` (o `:source`) del store en documentos rastreados, `:modified-externally` cuando el archivo en disco diverge del store, y `nil` cuando no existe archivo.
   - `check-before-images!` verifica que la ruta en disco coincida con la before-image esperada; ante `:modified-externally` o edición concurrente entre planificación y escritura lanza `:adapters/conflict` (exit `4`, `{:reason "before-image" :paths [...]}`) antes de mutar store o disco.
   - `preflight!` invoca `check-before-images!` antes de `ensure-models!`, y `apply-plan!` (ahora pública) lo re-verifica al inicio y antes de escribir cada documento.
2. `test/kimun/cli/adapters_test.cljc`:
   - Agrega `before-image-guard-prevents-clobbering-concurrent-edits` (`;; v1: tests/selfdoc/test_preflight.py::test_before_image_guard_prevents_clobbering_concurrent_edits`), cubriendo:
     - edición concurrente durante planificación entre `cmd/inspect` y `cmd/apply-plan!` (`:adapters/conflict`, exit `4`, archivo intacto, journal sin cambios);
     - archivo rastreado modificado externamente en disco antes de `adapters sync` (`:adapters/conflict`, exit `4`, no sobrescribe `"External edit on disk\n"`, journal sin cambios);
     - edición concurrente entre `cmd/inspect` y `cmd/apply-plan!` sobre un documento rastreado que va a actualizarse;
     - restauración de la before-image esperada y escritura normal de `adapters sync`.
3. `docs/v2/11-adaptadores.md` y `docs/v2/tests/parity-v1.md`:
   - Documentan el rechazo `:adapters/conflict` (`reason "before-image"`, exit `4`) y actualizan el marcador de `parity-v1.md` (`corpus`: `1` → `0`, `portada`: `518` → `519`).

## Validación y archivos de evidencia

- `validation.log`:
  - `~/bin/memcap bb lint`: `rings and docstrings ok: 103 files`
  - `~/bin/memcap bb oracle`: `9 rows, 0 mismatches`
  - Tests dirigidos (`kimun.surface.adapters-test`, `kimun.cli.adapters-test`, `kimun.parity.adapters-parity-test`): `Ran 44 tests containing 1865 assertions. 0 failures, 0 errors.`
  - `python3 scripts/parity/coverage.py /home/jp/proyectos/SLDB_1/sldb-astryx . --list-open`:
    - `selfdoc/test_preflight.py`: `total=4, ported=4, mapped=0, oracle=0, n/a=0, gap=0, ment.=0, corpus=0, open=0`
    - `TOTAL`: `corpus=0` (`test_before_image_guard_prevents_clobbering_concurrent_edits` dejó de estar en `corpus`).
- `full-test-isolated.txt`:
  - `~/bin/memcap bb test` en worktree limpio sobre `HEAD` (`f837069d`) con únicamente los 4 archivos del corte:
  - `tree-sitter pod: available`
  - `Ran 117 namespaces in 174 s`
  - `{:test 956, :pass 10024, :fail 0, :error 0, :namespaces 117}`
  - Nota: en `kimun.cli.migrate-test` se omitió `real-store-sample-extracts-as-in-v1` porque `/home/jp/proyectos/hum-ecosystem/tools/sldb/.sldb` no está presente en esta máquina; en `kimun.adapters.treesitter-test` se omitió la parte JavaScript por no estar disponible `tree_sitter` JavaScript grammar.
- `cli-demo.log`:
  - Demostración CLI real con `bin/kimun adapters sync python-symbols src --system demo`:
    1. Primer `sync` crea `knowledge/symbols/python-demo-demo-tools-greet.md` (exit `0`).
    2. Tras modificar `src/demo/tools.py` y sobrescribir externamente `python-demo-demo-tools-greet.md` con `External edit on disk\n`, `adapters sync` sale con exit `4`, `"type": "adapters/conflict"`, `"reason": "before-image"`, y conserva intacto `"External edit on disk\n"`.
    3. Tras restaurar la before-image rastreada, el `sync` normal sale con exit `0`, actualiza `docstring`, `provenance` y `source_sha256`, y escribe `"Return a much warmer greeting."` en el archivo.
