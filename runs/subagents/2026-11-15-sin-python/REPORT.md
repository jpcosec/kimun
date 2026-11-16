# REPORT sin-python (worktree /tmp/kimun-nopython-wt, sin commit; cambios en el indice)

Total: 183 archivos, 10541 lineas borradas y 50 anadidas (165 archivos borrados, 18 editados).
Desglose: python/ 1301 lineas (11 archivos); src 1258 (adapters cli 507, adapters surface 481, migrate 175+95) mas create-plan; pruebas 1403 (adapters_test 572, surface adapters_test 388, adapters_parity 254, python_unittest 34, migrate_test 155); fixtures 5983 lineas (adapters, parity/adapters, migrate, parity/migrate); docs/v2/11-adaptadores.md 455; resources/kimun/adapters 3 modelos.

## Que capacidad deja de existir
- Grupo `adapters` (list/scan/sync/check, python-*, selfdoc-*), protocolo 1 de procesos externos, guarda before-image y `source-graph-check`/`python-check`.
- `models define --from-python`, referencias `modulo:Clase` en `resolve-model` (extract/render/validate ya solo aceptan X.edn o nombre de modelo del store) y la opcion `--pythonpath` de models.
- `migrate --from-v1` (su unico exportador de descriptores era el Python borrado; no tenia sentido dejarlo inoperante). Decision mia derivada del borrado; reversible via git.
- No hay mas Python en el repo salvo `scripts/canon_oracle.py` (y scripts/parity/*.py, ver abajo).

## Pruebas que se fueron
adapters_test (cli), adapters_test (surface), adapters_parity_test, python_unittest_test, migrate_test; `define-from-python` en models_test; el caso `--from-python` de cli_store_test (unknown-model-reports-missing... conserva el resto). No se tocaron doc14_contract_test ni otras: no fijaban frases de adaptadores.

## Evidencia
lint.txt: bb lint ok (108 archivos). directed.txt: 308 pruebas, 3389 aserciones, 0 fallos (main, release, lint, doc14_contract, wittgenstein_manifest, spec16_models_contract, stores, docs, include (cli y surface), http_test + las 6 de surface/http/, models, cli_store, contract, golden, tour, typed_docs, linked_models, compose, spec16 cli, docs_parity, known/closed gaps). oracle.txt: 9 filas, 0 diferencias. canon_oracle.py no importa nada de python/.

## Docs corregidas
README.md (quita python/README), docs/v2/README (tabla y orden sin 11), 01, 02 (Python solo el oraculo), 05 (adaptadores y migracion reemplazados por una frase sobre tree-sitter), 06 (grupos adapters/migrate, exit codes, from-python, tabla v1), 07, 08, 12 (C1 marcada borrada, invariante de paridad ahora "suspendida"; C2 intacta), 14 (§11 y C1), tests/parity-v1.md (filas que citaban pruebas borradas). AGENTS.md y docs/faq.md no nombraban nada borrado.

## Que NO borre y por que
- scripts/parity/* (incluye .py y compare_store.clj, que requiere kimun.cli.commands.migrate ya inexistente): me lo pediste para otro corte; ese corte debe retirarlo o fallara. Idem test/fixtures/parity/{edges,semantic}/generate.py (importan kimun.models) y docs/v2/tests/parity-*.edn/promises.md (ledgers; solo toque parity-v1.md).
- surface/docs.cljc `routed?` y `with-marks`: ya estaban sin uso antes de mi corte; no son efecto de el.
- .gitignore (__pycache__ etc.) y setup-python de ci.yml: bb oracle usa python3.
- docs/archive, runs, docs/v2/drafts, docs/reviews: intactos.
- bb.edn, release.edn, scripts/release.clj: no tenian nada de python/ ni adaptadores.
