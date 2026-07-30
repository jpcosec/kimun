# Canonical-State Derivation Glossary

## Purpose and governing sources

This contract freezes the vocabulary for how authored inputs, canonical state, render/materialization, projections, and derived indexes relate.

Governing sources:

- `also_core.md`
- `core_README.md`
- `diagramas_core.md`
- `interfaces.md`
- `libraries_core.md`
- `plan_core.md`
- `desk/atoms/rust-core.md`
- `desk/atoms/document-materializer.md`
- `docs/architecture/target-system-overview.md`

## In-scope

- glossary definitions for the derivation pipeline
- authority placement
- boundaries between input, render, projection, and derived index

## Explicit non-goals

- no runtime implementation claims
- no code-derived validation
- no broad taxonomy rewrite outside directly affected docs

## Canonical pipeline

```text
Input authored
    ↓ parse/import
Transacción
    ↓
Estado canónico
    ├── renderizado / materialización
    ├── proyección
    └── índice / artefacto derivado
```

## Definitions

### Input authored

Content written or supplied externally before canonicalization.

Examples:

- human Markdown
- human Lisp
- imported JSON
- source code or external files

Rule:

- authored input is not authoritative by itself
- authored input is not yet a projection
- it becomes meaningful to the system only after kernel-controlled parse/import and transaction handling

### Canonical state

The maximum authority of knowledge in this system: Rust-owned kernel state persisted in the canonical database.

It includes:

- nodes
- edges
- revisions
- types
- metadata
- provenance

Rule:

- canonical state is the source of truth
- Markdown is not the source of truth
- Lisp is not the source of truth

### Render / materialization

An external representation of canonical state.

Examples:

- Markdown output
- HTML output
- JSON output
- S-expression output
- API payload output

Rule:

- render/materialization changes representation
- it does not become authority

### Projection

A reproducible view that selects, reorganizes, or interprets canonical state for a specific purpose without becoming authority.

Examples:

- semantic graph
- backlinks view
- dependency tree
- task view
- anchor map
- agent subgraph

Rule:

- projection changes visible structure, focus, or interpretation
- projection is narrower than generic render/materialization wording

### Derived index / derived artifact

A rebuildable structure calculated from canonical state to accelerate or enrich system behavior.

Examples:

- search index
- embeddings
- vector index
- cached hashes
- materialized logical facts

Rule:

- derived indexes are auxiliary and reconstructible
- they are never authority

## Summary rules

- `canonical state` = authority
- `authored input` = proposed external content entering through kernel-controlled import/transaction flow
- `render/materialization` = external representation of canonical state
- `projection` = structured specialized view over canonical state
- `derived index/artifact` = rebuildable auxiliary computation

## Downstream constraints

- do not call authored Markdown or authored Lisp a projection
- do not call generic rendered Markdown a graph-style projection when `render` or `materialization` is clearer
- keep the authority statement fixed: Rust-owned canonical state persisted in the database is the source of truth
- use `projection` for specialized views and `render/materialization` for representation output unless a doc explicitly needs both terms

## Validation attestation

This artifact defines the glossary, keeps authority in Rust-owned canonical database-backed state, distinguishes the derivation categories clearly, and makes no implementation progress claims.
