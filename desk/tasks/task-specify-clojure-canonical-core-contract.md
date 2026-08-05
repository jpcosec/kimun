---
id: task-specify-clojure-canonical-core-contract
status: closed
summary: Re-anchored to high-fidelity contract in docs/architecture/contracts/clojure-canonical-core-contract.md.
tags:
- workspace:desk
- artifact:task
- system:sldb
history: ["2026-07-29: closed with high-fidelity contract artifact."]
references:
- desk/atoms/clojure-core.md
- desk/atoms/canonical-ast.md
- desk/atoms/decision-rowan-ast.md
- desk/atoms/decision-blake3-hashing.md
- desk/atoms/relation-ast-extensibility.md
- desk/atoms/source-document-hash.md
- docs/architecture/spec2viz/target-components.yml
depends_on: []
pills:
- desk/contexts/pill-planning-contracts.md
files:
- docs/architecture/contracts/clojure-canonical-core-contract.md
checklists: []
---

# Specify Clojure Canonical Core Contract

## Rationale

Govened by `clojure-core.md`. The Clojure engine must serve as the "Canonical Truth," ensuring that every document segment has a deterministic identity and every relation is structurally validated. This task ensures we don't compromise on lossless parsing or identity hashing.

## Goal

Define the technical requirements for the `CanonicalAST` (Rowan) and the `Hashing` layer (Blake3). Mandate a reversible stack that can handle "Markdown -> AST -> Markdown" without data loss.

## Scope

- **In-Scope**: 
  - `pulldown-cmark` (Markdown parser) and `Rowan` (Lossless Green Tree).
  - `Blake3` node-level hashing protocol.
  - Canonical Relations (Links, Anchors, Store edges).
  - Importer/Emitter stack requirements.
- **Out-of-Scope**: 
  - CLI orchestration (Python).
  - Persistence-specific SQL details (delegated to Graph Store task).

## Implementation Path

1. **Adapter Strategy**: Specify the flow: `Markdown` -> `pulldown-cmark` (events) -> `Rowan` GreenTree -> `Blake3` Hashing.
2. **Relation Engine**: Define how `LinkRelations` and `AnchorNodes` are extracted from the AST.
3. **Identity Protocal**: Bind `decision-blake3-hashing.md` to node identity.
4. **Contract Materialization**: Write the final specification in `docs/architecture/contracts/clojure-canonical-core-contract.md`.

## Validation

- Does the contract mandate the use of `Rowan` for lossless parsing?
- Does it require `Blake3` for content identity?
- Does it align with the `ClojureCore` block in `target-components.yml`?

## Done When

The high-fidelity `clojure-canonical-core-contract.md` is committed and approved.
