# REPORT sin-adaptador-python (2026-11-15)

Decision del dueño: «Quita ese adaptador python, no tiene sentido.» Hecho en `/tmp/kimun-noadapter-wt` (sin commit).

## Qué se quitó
- `python/kimun/adapters/treesitter_symbols.py` (el adaptador).
- `python/tests/test_treesitter_symbols.py`, `python/tests/test_treesitter_agreement.py`.
- `test/kimun/adapters/treesitter_test.cljc` (ns completo).
- Fixtures solo suyos: `test/fixtures/adapters/jstree/`, `test/fixtures/adapters/pytree_ts/` (`pytree` se queda, lo usan otras pruebas).
- Registro/descubrimiento: no había registro por nombre; `adapters/builtins` lista `python/kimun/adapters/*.py`, así que al borrar el archivo `treesitter-symbols` deja de ser built-in y pedirlo da `adapters/missing` ("unknown adapter", exit 1) por el camino general. Solo cambió el texto de ayuda en `src/kimun/cli/main.cljc` (sin el nombre).
- Documentos: `docs/v2/11-adaptadores.md` (§2 nombres, §4.2, §6.4 ejemplo, §3 tabla exit 3, §9.4 ahora "retirado" con lo que se pierde), `python/README.md` (sin la línea; dice que el pod es el único mecanismo), `docs/v2/12 §1` (solo la viñeta "Tree-sitter como pod"), `docs/v2/14` línea 667 (afirmación que quedaba falsa; `doc14_contract_test` verde). No se tocó `docs/archive/` ni `runs/` previos.
- Inventario: `inventory.md`.

## Qué NO es el adaptador y se conserva
Mecanismo general de adaptadores (`cli/commands/adapters.cljc`, `surface/adapters.cljc`, protocolo); `python_symbols.py` (ast), `argparse_cli.py`, `click_cli.py`; el pod (`pods/treesitter`, `host/treesitter.cljc`, `release.edn`, `scripts/release.clj`, `bb.edn`). `python/kimun/models.py` y `__init__.py` son el exportador de modelos sldb v1 (`python -m kimun.models export`): no son parte del adaptador, no se tocaron.

## Funcionalidad que se pierde (hasta rehacerla sobre el pod)
Extraer símbolos como hechos con tree-sitter: módulos, clases, funciones, métodos, docstrings/JSDoc e imports, para Python y para JavaScript (incluidas declaraciones de función/generador/async, clases, métodos y `const/let/var` con flecha/función; `--language`; detección de lenguaje). Para Python sigue `python-symbols` (ast, equivalente); para JavaScript ya no hay ningún lector. El pod hoy trae Markdown, Markdown en línea y Python; Clojure y JavaScript no están. Además se pierde la comprobación de acuerdo ast vs tree-sitter.

## Dependencias de otras partes (verificado)
Nada se rompe: `source-graph-check`/`python-check`/`selfdoc-*`, la fila `C1` y los marcadores `selfdoc/` de `parity-v1.md` usan `python-symbols`/ast, no tree-sitter. `grep treesitter` en `docs/v2/tests/parity-v1.md` y `scripts/parity/coverage.py`: vacío. No toqué ningún invariante de `12 §1`.

## Pruebas
- Quitadas (solo probaban el adaptador): los 2 archivos Python, `treesitter_test.cljc` (`python-agreement`, `javascript-fixture`) y el deftest `python-and-treesitter-adapters-agree` de `cli/adapters_test.cljc`.
- Ajustadas: `surface/adapters_test.cljc` `non-python-language-prefix` y `owned-rule`: usaban el nombre "treesitter-symbols" como dato de funciones puras (prefijo de procedencia, regla owned?); ahora "other-symbols", misma aserción. Quitado el require `babashka.process` sin uso de `cli/adapters_test.cljc`.
- Añadida (1): `kimun.cli.adapters-test/treesitter-symbols-adapter-is-retired`: pedir `treesitter-symbols` da `adapters/missing`, exit 1, reason `unknown-adapter`, nombre ausente de `:known`, `python-symbols` presente. Discrimina: con el script restaurado fallaba (error `adapters/failed`, exit 5; `mutation.txt`). No añadí prueba del pod: sus pruebas ya existen (`parse-contract-test` etc.) y pasan.

## Validación (en `runs/subagents/2026-11-15-sin-adaptador-python/`)
- `lint.txt`: `bb lint` ok, 112 archivos.
- `directed.txt`: 79 tests, 2932 aserciones, 0 fallos, 0 errores (cli.adapters-test, surface.adapters-test, parity.adapters-parity-test, cli.main-test, cli.release-test, doc14-contract-test, wittgenstein-manifest-test, lint-test, surface.parse-contract-test; no existe un test "host treesitter" propio: el pod lo ejercitan parse-contract y las pruebas CST).
- `oracle.txt`: `bb oracle` 9 filas, 0 diferencias.
- `python-tests.txt`: suite `unittest` restante: 28 tests OK.
- `parity-before.txt` / `parity-after.txt`: `scripts/parity/coverage.py /home/jp/proyectos/SLDB_1/sldb . --strict` idénticos (diff vacío). OJO: ese comando sale con FAIL (827 blocking, 3 errors) también ANTES del cambio; probablemente el v1 root no es el que usa `parity-v1.md` (590 funciones). No pude reproducir el marcador 520/…; no es efecto de este corte.

## Para el dueño
Nada bloqueante. Pendiente fuera de este corte: lector de símbolos sobre el pod (Python, y JavaScript si se quiere; requiere compilar gramática JS en el pod). Sin commit.
