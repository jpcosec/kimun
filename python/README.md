# kimun Python package

The Python side of kimun lives in `python/kimun/`. It is optional: the product is the
Clojure/Babashka CLI `bin/kimun`. Python is used to import sldb v1 model classes and to
host the shipped out-of-process adapters.

## Model exporter

`python -m kimun.models export mod:Class` prints a kimun model descriptor (EDN) for a
sldb v1 `StructuredNLDoc` class. It is the only place where the product imports a Python
module (`docs/v2/06-superficies-y-cli.md §9`) and it feeds
`bin/kimun models define --from-python` (`docs/v2/07-modelos-como-nodos.md §7`).

## Adapters

`python/kimun/adapters/` holds stand-alone fact adapters (protocol 1). Each module is also
runnable as a script, and `bin/kimun adapters` drives them:

- `python_symbols.py` — static Python symbols (port of the sldb v1 `PythonScanner`).
- `treesitter_symbols.py` — symbols for Python and JavaScript via tree-sitter.
- `argparse_cli.py` — `argparse` CLI surface (port of v1 `ParserScanner`).
- `click_cli.py` — `click` CLI surface (port of v1 `ClickScanner`; needs the `click` package).

The protocol is fixed in `docs/v2/11-adaptadores.md`.

## Client library

There is no Python client library yet. The `from kimun import Client` snippet in
`docs/v2/06-superficies-y-cli.md §9` is the planned surface. Until it exists, call the CLI
with `subprocess` and parse the JSON envelope.

## Tests

`python/tests/` holds the `unittest` suite for the exporter and the adapters:

```bash
python3 -m unittest discover -s python/tests -t python/tests
```
