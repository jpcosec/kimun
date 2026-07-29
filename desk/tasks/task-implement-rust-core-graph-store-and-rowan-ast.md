---
id: task-implement-rust-core-graph-store-and-rowan-ast
status: draft
summary: ''
tags:
- workspace:desk
- artifact:task
routine: routine-task-implement-rust-core-graph-store-and-rowan-ast
current_node: checklist-task-implement-rust-core-graph-store-and-rowan-ast-execution-ready
history: []
references: []
depends_on: []
pills: []
files: []
checklists:
- checklist-task-implement-rust-core-graph-store-and-rowan-ast-execution-ready
- checklist-task-implement-rust-core-graph-store-and-rowan-ast-testing-ready
- checklist-task-implement-rust-core-graph-store-and-rowan-ast-closeout-ready
---

# Implement Rust Core: Graph Store and Rowan AST

## Rationale

_Explain why this task exists or the business driver behind it._

The new architecture requires a sovereign Rust core to guarantee lossless reversible parsing (via rowan), cryptographically sound structural identity (blake3), and immutable append-only persistence (rusqlite).

## Goal

_Describe the concrete result this task must produce._

Build the foundational `.sldb/` database schema in SQLite, the graph abstractions using petgraph, and the rowan-based lossless AST.

## Scope

_State what is in scope and what is out of scope._

Rust core only. No FFI or Python bindings in this phase. Focus on schema, SQLite file-locks, hashing, and tree manipulations.

## Implementation Path

_Outline the expected implementation route or affected surface._



## Validation

_List the checks required before this task can close._

- cargo test --manifest-path sldb-core/Cargo.toml

## Done When

_Name the observable condition that makes the task complete._
