# SLDB Refactor Target Workspace

This worktree is a planning and architecture workspace for the kernel/core direction.

## What remains here

- `also_core.md`
- `core_README.md`
- `diagramas_core.md`
- `interfaces.md`
- `libraries_core.md`
- `plan_core.md`
- `desk/` workflow state and durable atoms
- `docs/architecture/`
- `subagent/` architecture exploration artifacts

## Source priority

1. `also_core.md`
2. `core_README.md`
3. `diagramas_core.md`
4. `interfaces.md`
5. `libraries_core.md`
6. `plan_core.md`
7. `desk/atoms/`
8. the rest of the repo

## Target direction

- the kernel is Rust-owned
- the canonical persistence model is an immutable revisioned graph
- canonical AST remains the structural substrate for authored document families inside that kernel model
- links, anchors, relations, transactions, and provenance are canonical kernel concerns
- external interfaces are adapters around the kernel, not alternate authorities
- Python is an optional orchestration adapter, not the architectural center
- render equality stays strict for reversible document families

Legacy runtime code, legacy tests, and old documentation snapshots were intentionally removed from this worktree. Recovery lives in git history and the other worktree.
