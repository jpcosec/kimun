---
# board-xxx
id: board-001
scope: desk
tasks:
- desk/tasks/task-freeze-phase-1-parity-contract.md
- desk/tasks/task-freeze-python-rust-ownership-and-ffi-contract.md
- desk/tasks/task-specify-rust-canonical-core-contract.md
- desk/tasks/task-specify-markdown-roundtrip-contract.md
- desk/tasks/task-specify-graph-store-contract.md
- desk/tasks/task-specify-anchoring-contract.md
- desk/tasks/task-specify-query-and-index-parity-contract.md
- desk/tasks/task-specify-cli-parity-contract.md
pills:
- desk/contexts/pills.md
- desk/contexts/pill-planning-contracts.md
rituals:
- desk/rituals/execution.md
- desk/rituals/testing.md
- desk/rituals/closeout.md
tags:
- workspace:desk
- system:sldb
---

# sldb-refactor-worktree Board

## Purpose

Route bounded workflow tasks that freeze the exact Phase 1 product contracts from atoms before any implementation starts.

## Notes

- The accidental implementation was removed from the workspace.
- This board now routes contract/specification tasks only.
- Atoms are the product truth.
- Spec2viz is the target projection.
- No task here claims implementation progress.

## Task Details

- Freeze Phase 1 Parity Contract [closed] - Lock the exact user-visible Phase 1 product contract and non-goals.
- Freeze Python/Rust Ownership and FFI Contract [closed] - Lock the orchestration boundary so Python stays thin and Rust owns the canonical engine.
- Specify Rust Canonical Core Contract [closed] - Define the exact canonical AST, hashing, selector, and relation substrate required by Phase 1.
- Specify Markdown Round-Trip Contract [closed] - Define the exact reversible Markdown import/render contract and proof obligations.
- Specify Graph Store Contract [closed] - Define the exact append-only persistence, derived indexes, and store integrity contract.
- Specify Anchoring Contract [closed] - Define the exact canonical anchor payload and anchor-kind behavior required by the target.
- Specify Query and Index Parity Contract [closed] - Define the exact Phase 1 retrieval/index surface without semantic scope expansion.
- Specify CLI Parity Contract [closed] - Define the exact command-group continuity surface that consumes the approved contracts above.
