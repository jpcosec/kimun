---
id: task-refactor-graphs-to-petgraph
status: draft
summary: 'Technical Debt: Offload heavy graph traversal from Python loop to Rust petgraph'
tags:
- workspace:desk
- artifact:task
routine: routine-task-implement-rust-core
current_node: execution
history: []
references:
- desk/tasks/task-implement-rust-core-graph-store-and-rowan-ast.md
depends_on: []
pills: []
files: []
checklists: []
---

# Refactor Graph Traversals to petgraph

## Rationale

Currently, graph traversal logic (e.g. `trace_lineage`) is executing inside Python using a `while` loop over FFI-provided SQLite edge lookups. This bypasses the performance mandate that required in-memory Rust graphing via `petgraph`.

## Goal

- Integrate `petgraph` into `sldb-core`.
- Maintain an in-memory `DiGraph` constructed from SQLite edges.
- Expose complex traversals (like deep lineage, cycle detection) via FFI so Python executes O(1) FFI calls instead of N DB calls.

## Scope

- Rust Core `sldb-core/src/store/`
- PyO3 FFI `sldb-ffi/src/lib.rs`

## Done When

Python orchestrates lineage traversals with a single FFI call invoking `petgraph` algorithms in Rust.
