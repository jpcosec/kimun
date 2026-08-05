# Python/Clojure Ownership and FFI Contract

## Purpose and governing sources

This contract defines the Python adapter boundary for deployments that keep a Python CLI shell around the Clojure kernel. It does not make Python a required architectural center.

Governing sources:
- `desk/atoms/python-cli-orchestration-layer.md`
- `desk/atoms/clojure-core.md`
- `desk/atoms/decision-clojure-core-with-minimal-python.md`
- `desk/atoms/decision-pyo3-ffi.md`
- `docs/architecture/spec2viz/target-components.yml`

## Ownership Architecture

### Python Shell (Optional Orchestration Adapter)
- **Git Integration**: Management of the working tree, staging, and commits.
- **Workflow Composition**: Sequencing calls to the Clojure kernel when a Python CLI is the chosen host.
- **UX/IO**: Formatting terminal output, handling standard input, and managing user-facing error reporting.
- **Environment**: Handling adapter-local configuration files (`pyproject.toml`, `.env`).

### Clojure Kernel (Canonical Truth)
- **Canonical Model**: Ownership of documents, revisions, nodes, edges, transactions, and provenance.
- **Structural Substrate**: Parsing, structural validation, and tree manipulation for authored document families.
- **Graph Store**: Persistence of canonical state and history in the append-only database.
- **Identity**: Blake3-based hashing of canonical units and document fragments.
- **Relations**: Resolution of links, anchors, and cross-document dependencies.
- **Query Execution**: Structural search and index maintenance behind replaceable storage/query adapters.

## FFI Boundary (PyO3)

### Permitted Data Flow
- **Python to Clojure**: Command parameters, filesystem paths, raw document content for extraction.
- **Clojure to Python**: Serialized AST segments, query result sets, structural error manifests, success/fail status.

### Prohibited Crossings
- **No Python Logic in Core**: Clojure must not call back into Python for any canonical decision.
- **No Duplicate Persistence**: Python must not attempt to parse or cache the internal `.sldb` state.
- **No Shared Mutability**: All data crossing the FFI boundary should be treated as immutable value objects or handled via explicit handle-based APIs (e.g., an opaque pointer to the `GraphStore`).

## Boundary Floor

The Python FFI boundary is sufficient once it supports:
1. `init_store(path)`
2. `ingest_document(path, content)`
3. `query_graph(query_string)`
4. `render_document(node_id)`
5. `verify_integrity()`

Other hosts may use Clojure API, IPC, HTTP, or other adapter boundaries instead of PyO3.

## Non-Negotiable
If a task requires structural reasoning about a document, it **must** reside in Clojure. If a task requires environmental side-effects (Git, subprocesses), it belongs in a host adapter outside canonical kernel authority; Python is one valid adapter, not the only one.
