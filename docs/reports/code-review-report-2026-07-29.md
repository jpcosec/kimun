# Code review report — 2026-07-29

## Critical breakages

- Python tests do not even collect.
  - `cd sldb-cli && pytest -q` fails with `ModuleNotFoundError: No module named 'sldb_cli'` from `sldb-cli/tests/test_documents.py:2`.
- Rust core tests do not compile.
  - `cd sldb-core && cargo test -q` fails at `sldb-core/src/adapters/treesitter.rs:35` because `tree.unwrap().root_node()` borrows from a temporary.

## Concrete findings

1. **Broken Python packaging / entrypoint**
   - `sldb-cli/pyproject.toml:14` exports `sldb = "sldb_cli:app"`.
   - There is no `app` object exported anywhere; the CLI entrypoint is `main()` in `sldb-cli/sldb_cli/__main__.py:4`.
   - `sldb-cli/pyproject.toml:16-18` points maturin `python-source` to `sldb-ffi`, which is not the Python package directory.
   - Result: install/entrypoint/import path is broken before runtime behavior matters.

2. **Declared Python deps do not match imports**
   - `sldb-cli/pyproject.toml:9-10` declares only `typer`.
   - Code imports `pydantic` in `sldb-cli/sldb_cli/models.py:1` and `markdown_it` in `sldb-cli/sldb_cli/importers/markdown.py:2`.
   - Result: even a fixed package layout will still break in clean environments.

3. **CLI surface is scaffold-only and not wired to the intended runtime**
   - `sldb-cli/sldb_cli/__main__.py:5-103` builds a large command tree, but almost everything prints placeholders.
   - Example: `stores build`, `stores destroy`, most `find/*`, and all non-store command groups have no implementation.
   - This is far from `target-runtime.yml` and `phase-1-v1-replication` because the CLI surface exists mostly as stubs.

4. **Python package is duplicated and inconsistent**
   - There are two importer trees:
     - `sldb-cli/importers/markdown.py:1-13`
     - `sldb-cli/sldb_cli/importers/markdown.py:1-30`
   - The top-level one returns a dummy AST string; the package one returns token dicts.
   - Same duplication exists for emitters.
   - Result: unclear import surface and guaranteed drift.

5. **Rust core test suite is currently red**
   - `sldb-core/src/adapters/treesitter.rs:35` is a compile error, not just a failing assertion.
   - That means the core crate cannot currently prove even its own adapter scaffold works.

6. **Pulldown adapter is not producing a safe canonical representation**
   - `sldb-core/src/adapters/pulldown.rs:4-24` hand-builds JSON with string concatenation.
   - It appends commas after each item and then closes with `]`, which yields invalid JSON for non-empty output.
   - It also only escapes quotes, not other JSON control characters.
   - Result: this is not a usable canonical AST layer.

7. **Current store schema is much smaller than the target graph store**
   - `sldb-core/src/store/schema.rs:8-40` defines only `nodes`, `edges`, and one FTS table.
   - Missing from the target model: anchor nodes, relation AST payloads, derived indexes split, history artifacts, provenance, typed dependency/projection edges.
   - Result: current code is a thin prototype, not the target `target-store-graph.yml` design.

8. **FFI boundary is thin pass-through, not an orchestration boundary yet**
   - `sldb-ffi/src/lib.rs:63-100` only exposes raw store lookups and text search.
   - No AST/importer/emitter API, no model workflows, no anchor operations, no parity-oriented command support.
   - This is much smaller than the Python↔Rust split described in `target-components.yml` and `target-runtime.yml`.

9. **Graph/query helpers assume semantics the store never enforces**
   - `sldb-cli/sldb_cli/graphs.py:33-53` traces lineage through `derived_from` edges.
   - The Rust schema and repository code do not define or enforce document-family/history semantics beyond generic `relation_type` strings.
   - Result: Python is assuming higher-level workflow semantics that the core does not model yet.

## Bottom line

- The codebase is still mostly scaffold/prototype.
- The biggest immediate problems are not subtle architecture issues; they are basic build/package failures.
- Relative to the planned target, the code is **far behind**:
  - CLI parity: far
  - Rust canonical core: early prototype
  - FFI boundary: minimal pass-through
  - graph store: partial prototype
  - reversible importer/emitter path: not production-shape
