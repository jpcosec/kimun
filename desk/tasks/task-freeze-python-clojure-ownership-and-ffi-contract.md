---
id: task-freeze-python-clojure-ownership-and-ffi-contract
status: closed
summary: Re-anchored to high-fidelity contract in docs/architecture/contracts/python-clojure-ownership-and-ffi-contract.md.
tags:
- workspace:desk
- artifact:task
- system:sldb
history: ["2026-07-29: closed with high-fidelity contract artifact."]
references:
- desk/atoms/python-cli-orchestration-layer.md
- desk/atoms/clojure-core.md
- desk/atoms/decision-clojure-core-with-minimal-python.md
- desk/atoms/decision-pyo3-ffi.md
- docs/architecture/spec2viz/target-components.yml
- docs/architecture/spec2viz/target-runtime.yml
depends_on: []
pills:
- desk/contexts/pill-planning-contracts.md
files:
- docs/architecture/contracts/python-clojure-ownership-and-ffi-contract.md
checklists: []
---

# Freeze Python/Clojure Ownership and FFI Contract

## Rationale

Grounded in `decision-clojure-core-with-minimal-python.md`. To achieve "Canonical Truth in Clojure," we must strictly define where Python's orchestration ends and Clojure's structural reasoning begins. This prevents the "leaky abstraction" of the previous architecture where Python managed AST state.

## Goal

Formalize the FFI boundary using `PyO3` as specified in `decision-pyo3-ffi.md`. Define the ownership of the `GraphStore` handle and the lifecycle of data crossing the boundary.

## Scope

- **In-Scope**: 
  - **Python Responsibility**: CLI entrypoints, Git working tree management, workflow composition, and human-readable UX.
  - **Clojure Responsibility**: `CanonicalAST` management, `.sldb` persistence, `Blake3` hashing, and structural query execution.
  - Ownership of a thread-safe `GraphStore` handle in the Python runtime.
- **Out-of-Scope**: 
  - Python-side parsing of Markdown.
  - Shared mutable state across the FFI (all crossings must be immutable value objects or handles).

## Implementation Path

1. **Boundary Definition**: Specify the FFI surface for `init_store`, `ingest_document`, and `query_graph`.
2. **Ownership Mapping**: Ensure the `python-cli-orchestration-layer.md` acts only as a shell.
3. **Data Flow**: Use `FFI / API boundary` patterns from `target-runtime.yml`.
4. **Contract Materialization**: Write the final specification in `docs/architecture/contracts/python-clojure-ownership-and-ffi-contract.md`.

## Validation

- Does the contract enforce "No Python Logic in Core"?
- Is the `PyO3` substrate explicitly mandated?
- Does it match the `ProductSurfaces` split in `target-components.yml`?

## Done When

The `python-clojure-ownership-and-ffi-contract.md` is frozen and bound to all implementation tasks.
