# Rust Canonical Core Contract

## Purpose and governing sources

This contract defines the technical requirements for the Rust engine, which serves as the "Canonical Truth" for the SLDB system.

Governing sources:
- `desk/atoms/rust-core.md`
- `desk/atoms/canonical-ast.md`
- `desk/atoms/decision-rowan-ast.md`
- `desk/atoms/decision-blake3-hashing.md`
- `desk/atoms/relation-ast-extensibility.md`
- `desk/atoms/source-document-hash.md`

## Technical Substrate

### 1. Canonical AST (Rowan)
- **Green Tree Architecture**: Support for full-fidelity, lossless parsing (preserving whitespace and comments).
- **Identity Nodes**: Every significant AST node must have a deterministic Blake3 hash based on its content and child hashes.
- **Incrementalism**: The AST must support efficient updates without re-parsing the entire document.

### 2. Identity and Hashing
- **Algorithm**: Blake3.
- **Scope**: File-level, block-level (sections/fields), and node-level identity.
- **Invariants**: Two AST nodes with the same hash are structurally identical.

### 3. Canonical Relations
- **Link Resolution**: Logic for resolving internal and external links into graph edges.
- **Anchor Management**: Extraction and validation of stable selectors (line/offset or semantic anchors).
- **Extensibility**: The relation engine must support custom relation types defined in YAML metadata.

### 4. Importer/Emitter Stack
- **Markdown Importer**: Translation of CommonMark + Frontmatter into the canonical structural substrate via `pulldown-cmark` event mapping to `Rowan` nodes.
- **Document Emitter**: Compilation of the canonical structural substrate back into target formats.
- **Phase 1 Requirement**: Markdown round-trip parity is mandatory for reversible document families.
- **Non-Phase-1 Outputs**: HTML/JSON may exist as later or optional projections, but they are not required Phase 1 parity outputs.

## Contract Invariants

- **Persistence-Ready**: Every node produced by the core must be capable of being stored in the `graph-store` without loss of identity.
- **FFI-Ready**: Data structures must be compatible with PyO3 serialization or handle-passing.
- **Thread Safety**: The core must be thread-safe for parallel document ingestion and query execution.

## Parity Obligations

The Rust core is complete for Phase 1 when it can:
- Parse all v1-compliant Markdown files into a stable AST.
- Generate stable Blake3 hashes for all document segments.
- Detect and extract all v1-style links and fields.
