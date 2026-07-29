# SLDB Refactor Target Workspace

This worktree is now a planning and architecture workspace for the SLDB refactor target.

## What remains here

- `desk/` workflow state and durable atoms
- `docs/architecture/target-system-overview.md`
- `docs/architecture/spec2viz/target-*.yml`
- `subagent/` architecture exploration artifacts

## Target direction

- canonical AST is the sovereign model
- store = local graph database connecting ASTs
- links and anchors are canonical
- relations can carry extensible relation-AST payloads
- render equality remains strict for reversible document families
- Rust should absorb almost all engine/runtime responsibility
- Python should remain a minimal CLI orchestration shell

Legacy runtime code, legacy tests, and old documentation snapshots were intentionally removed from this worktree. Recovery lives in git history and the other worktree.
