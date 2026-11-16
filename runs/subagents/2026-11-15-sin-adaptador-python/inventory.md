# Inventario: adaptador `treesitter-symbols` (antes de borrar)

Comando: `grep -rIn "treesitter_symbols\|treesitter-symbols" src test python docs scripts bb.edn .github release.edn *.md` (sin `docs/archive/` ni `runs/`).

## Es el adaptador (se retira)
- `python/kimun/adapters/treesitter_symbols.py` (el script; tabla LANGUAGES python+javascript; usa paquetes pip `tree_sitter`, `tree_sitter_python`, `tree_sitter_javascript`).
- `python/tests/test_treesitter_symbols.py` (prueba solo el adaptador).
- `python/tests/test_treesitter_agreement.py` (acuerdo ast vs tree-sitter; solo existe por el adaptador).
- `test/kimun/adapters/treesitter_test.cljc` (ns `kimun.adapters.treesitter-test`: subproceso del script; acuerdo con python_symbols y fixture javascript).
- `test/kimun/cli/adapters_test.cljc:405-427` deftest `python-and-treesitter-adapters-agree`.
- `test/kimun/surface/adapters_test.cljc:147-155` `non-python-language-prefix` y `:296` (owned-rule): usan el NOMBRE "treesitter-symbols" como dato de una funcion pura (prefijo de procedencia para adaptadores symbols que no son python-ast; regla owned?). Ejercitan el mecanismo general, no el script.
- Fixtures: `test/fixtures/adapters/jstree` y `pytree_ts` los usa solo treesitter_test (verificar abajo).
- Docs vigentes: `docs/v2/11-adaptadores.md` lineas 42-43 (nombres), 105 (relaciones), 187 (procedencia, ejemplo), 417-445 (§9.4); `python/README.md:20`; `docs/v2/12 §1` linea 126 (fila "Tree-sitter como pod", seccion desviaciones) ; `docs/v2/14` linea 667 (tabla: "adaptador Python `treesitter_symbols.py`"); `src/kimun/cli/main.cljc:124` (texto de ayuda).

## Quien lo invoca / descubre
- Descubrimiento: `kimun.cli.commands.adapters/builtins` lista `python/kimun/adapters/*.py` por nombre de archivo (guion por subrayado). No hay registro por nombre: al borrar el archivo, `treesitter-symbols` deja de ser built-in y `resolve-adapter` ya da `:adapters/missing` ("unknown adapter ...") por el camino general. `adapters list` lo enumera por el mismo glob.
- `src/kimun/surface/adapters.cljc`: no nombra treesitter (generico; el prefijo de procedencia es el nombre del adaptador).

## NO es el adaptador (se conserva)
- Mecanismo general de adaptadores (`src/kimun/cli/commands/adapters.cljc`, `src/kimun/surface/adapters.cljc`, protocolo `11 §1-8`).
- Otros adaptadores: `python_symbols.py` (ast, base de `python-scan|sync|check`, `source-graph-check`, `selfdoc-*`), `argparse_cli.py`, `click_cli.py`. Marcadores de paridad `selfdoc/` y fila C1 dependen de python-symbols/ast, no de tree-sitter.
- El pod: `pods/treesitter/`, `src/kimun/host/treesitter.cljc`, `release.edn`, `scripts/release.clj`, `bb.edn`, `scripts/bench/*`, `scripts/tour_mutation.sh` (todos nombran el pod, no el adaptador).
- `python/kimun/models.py` y `python/kimun/__init__.py`: exportador de modelos sldb v1 (`python -m kimun.models export`), no es parte del adaptador. No se tocan.
- `.github/`: sin mencion de tree_sitter ni del adaptador.
