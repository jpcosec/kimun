---
id: task-freeze-phase-1-parity-contract
status: closed
summary: Re-anchored to high-fidelity contract in docs/architecture/contracts/phase-1-parity-contract.md.
tags:
- workspace:desk
- artifact:task
- system:sldb
history: ["2026-07-29: closed with high-fidelity contract artifact."]
references:
- desk/atoms/decision-v1-parity-before-scope-expansion.md
- desk/atoms/phase-1-v1-replication.md
- desk/atoms/cli-workflow-surface.md
- desk/atoms/direct-mode.md
- desk/atoms/store-backed-mode.md
- docs/architecture/spec2viz/target-components.yml
- docs/architecture/spec2viz/target-runtime.yml
depends_on: []
pills:
- desk/contexts/pill-planning-contracts.md
files:
- docs/architecture/contracts/phase-1-parity-contract.md
checklists: []
---

# Freeze Phase 1 Parity Contract

## Rationale

This task is governed by the `decision-v1-parity-before-scope-expansion.md` atom. Phase 1 is a structural migration of the canonical engine from Python to Rust. To prevent architectural drift, we must freeze the product surface at the "v1 floor." This ensures that fundamental round-trip and storage invariants are replicated perfectly before any Phase 2 features (e.g., VisualUX, Semantic Search) are introduced.

## Goal

Formalize and lock the exact set of user-visible workflows, command groups, and structural invariants that constitute "v1 parity." This includes the transition from Python-owned ASTs to Rust-owned `CanonicalAST` as the single source of truth.

## Scope

- **In-Scope**: 
  - All command groups specified in `target-components.yml`: `docs`, `ast`, `fields`, `sections`, `stores`, `models`, `find`.
  - Direct Mode operations: `extract`, `render`, `validate`.
  - Store-Backed workflows: `track`, `update`.
  - Append-only `.sldb` persistence with `Blake3` integrity.
- **Out-of-Scope**: 
  - `VisualUX` (ProseMirror-based editing).
  - Semantic/Vector search (embeddings).
  - Multi-store/global orchestration.

## Implementation Path

1. **Audit**: Map every v1 command and flag to its required internal behavior.
2. **Translate**: Specify how Python orchestration sequences calls to the Rust engine via the FFI boundary.
3. **Formalize**: Define the "Parity Floor" in `docs/architecture/contracts/phase-1-parity-contract.md`.
4. **Freeze**: Secure approval of the contract as the immutable boundary for implementation.

## Validation

- Attestation against `phase-1-v1-replication.md`.
- Command tree must match `target-components.yml` exactly.
- Validation of the "Reversible Document" requirement in the contract.

## Done When

The high-fidelity `phase-1-parity-contract.md` is committed and approved as the boundary for all downstream implementation planning.
