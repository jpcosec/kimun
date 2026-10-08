# Phase 1 Parity Contract

## Purpose and governing sources

This contract freezes the exact Phase 1 product surface for the refactor workspace, ensuring that structural internal changes do not degrade recognizable user workflows.

Governing sources:
- `desk/atoms/decision-v1-parity-before-scope-expansion.md`
- `desk/atoms/phase-1-v1-replication.md`
- `desk/atoms/cli-workflow-surface.md`
- `desk/atoms/direct-mode.md`
- `desk/atoms/store-backed-mode.md`
- `docs/architecture/spec2viz/target-components.yml`
- `docs/architecture/spec2viz/target-runtime.yml`

## Phase 1 Parity Floor

Phase 1 is a structural refactor, not a feature expansion. The "Parity Floor" is the set of capabilities that must survive the transition from the legacy Python implementation to the Clojure-core implementation.

### 1. Recognizable CLI Surface
- Maintenance of the `sldb` plural-first command structure.
- Preservation of flag parity for core workflows (e.g., path filtering, recursive discovery).

### 2. Direct-Mode Integrity
- `extract`: Must produce a valid structural representation of a document without an initialized store.
- `render`: Must materialize a document from a structural representation.
- `validate`: Must verify document integrity against schema/rules.

### 3. Store-Backed Lifecycle
- `stores init`: Creation of the `.sldb` append-only infrastructure.
- `docs track/update`: Document registration and change propagation.
- `find`: Structural search across the local graph.

## Strict Non-Goals (Deferred to Phase 2+)

- **Visual UX**: No implementation of the ProseMirror-based editor or graph explorer.
- **Semantic Retrieval**: No vector embeddings, LLM integrations, or RAG-style query expansion.
- **Multi-Store Orchestration**: Phase 1 focus is strictly on the local repository `.sldb` store.
- **Speculative Families**: No document families beyond the current Markdown + YAML metadata parity.

## Acceptance Criteria

- **Functional Parity**: All v1 test cases (re-anchored to the new shell) must pass.
- **Structural Identity**: The `Source Document -> AST -> Target Document` round-trip must be byte-equivalent for non-semantic changes.
- **Performance Parity**: Local graph operations must be O(N) or better relative to current Python performance.

## Verification Protocol

1. **CLI Attestation**: Verification that the command tree matches the approved `cli-parity-contract`.
2. **Round-Trip Attestation**: Verification that `markdown-roundtrip-contract` obligations are met.
3. **Store Integrity Attestation**: Verification that the `graph-store-contract` invariants hold after a mass document import.
