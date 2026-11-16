# Inventario sin-python

Borrado (git rm; lineas = lineas del archivo):
- python/ (11 archivos, 1301 lineas): adaptadores python_symbols/argparse_cli/click_cli, kimun/models.py (exportador v1), tests, README.
- src/kimun/cli/commands/adapters.cljc (507), src/kimun/surface/adapters.cljc (481); registro en main.cljc, codigos de salida en errors.cljc.
- resources/kimun/adapters/models/ (3 modelos congelados, solo los usaba el subsistema).
- Pruebas: test/kimun/cli/adapters_test (572), surface/adapters_test (388), parity/adapters_parity_test (254), adapters/python_unittest_test (34).
- Fixtures: test/fixtures/adapters (9 archivos), test/fixtures/parity/adapters (20).
- docs/v2/11-adaptadores.md (455).
- Consecuencia de borrar python/kimun/models.py: models define --from-python y el modulo:Clase de resolve-model ya no tienen exportador. Se quita de models.cljc (from-python, python-dir, opciones) y de direct.cljc (texto).
- migrate --from-v1 (src cli/commands/migrate 175, surface/migrate 95, test cli/migrate_test 155, fixtures test/fixtures/migrate 31 archivos y test/fixtures/parity/migrate 81): su unico exportador era from-python; sin el, el comando no podia funcionar. Borrado entero.
- kimun.surface.docs/create-plan: solo la usaba adapters_test; borrada.
- Pruebas ajustadas: models_test (define-from-python fuera), parity/cli_store_test (caso --from-python fuera).
- Docs corregidas: README.md, docs/v2/{README,01,02,05,06,07,08,12,14}, docs/v2/tests/parity-v1.md.
No tocado: bb.edn, release.edn, scripts/release.clj, .github (no empaquetaban python/; setup-python sigue porque bb oracle usa python3).
