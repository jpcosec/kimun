# kimun Python package

The Python side of kimun lives in `python/kimun/`. It is optional: the product is the
Clojure/Babashka CLI `bin/kimun`. Python is used to import sldb v1 model classes and to
host the shipped out-of-process adapters.

## Model exporter

`python -m kimun.models export mod:Class` prints a kimun model descriptor (EDN) for a
sldb v1 `StructuredNLDoc` class. It is the only place where the product imports a Python
module (`docs/v2/06-superficies-y-cli.md §6`) and it feeds
`bin/kimun models define --from-python` (`docs/v2/07-modelos-como-nodos.md §7`).

## Adapters

`python/kimun/adapters/` holds stand-alone fact adapters (protocol 1). Each module is also
runnable as a script, and `bin/kimun adapters` drives them:

- `python_symbols.py` — static Python symbols (port of the sldb v1 `PythonScanner`).
- `argparse_cli.py` — `argparse` CLI surface (port of v1 `ParserScanner`).
- `click_cli.py` — `click` CLI surface (port of v1 `ClickScanner`; needs the `click` package).

The protocol is fixed in `docs/v2/11-adaptadores.md`.

There is no tree-sitter adapter here. The tree-sitter parser is the Rust pod
(`pods/treesitter/`, `src/kimun/host/treesitter.cljc`), the only tree-sitter mechanism: today it
carries the Markdown, inline Markdown and Python grammars. Extracting code symbols (classes,
functions, docstrings, imports) with it, and other grammars (Clojure, JavaScript), are not
available yet; the former Python adapter `treesitter_symbols.py` was retired on 2026-11-15.

## Client library

There is no Python client library yet. Task `C2` was withdrawn on 2026-11-15 and deferred the
same day by the owner (decision 47, `docs/v2/05-estado.md`): a Python wrapper will be added, but
only after kimun is proven to work against today's sldb (`RS1`) and the stable consumer contract
is written (`RS2`); see `docs/v2/12-ecosistema-y-pendientes.md §1`. No client API is specified
yet (`docs/v2/06-superficies-y-cli.md §6`). Until then, to drive kimun from Python, call the CLI
with `subprocess` and parse the JSON envelope.

## Tests

`python/tests/` holds the `unittest` suite for the exporter and the adapters:

```bash
python3 -m unittest discover -s python/tests -t python/tests
```
