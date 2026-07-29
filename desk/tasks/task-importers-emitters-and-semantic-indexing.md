---
id: task-importers-emitters-and-semantic-indexing
status: draft
summary: ''
tags:
- workspace:desk
- artifact:task
routine: routine-task-importers-emitters-and-semantic-indexing
current_node: checklist-task-importers-emitters-and-semantic-indexing-execution-ready
history: []
references: []
depends_on:
- macrotask-01-rust-core-ast-store
pills: []
files: []
checklists:
- checklist-task-importers-emitters-and-semantic-indexing-execution-ready
- checklist-task-importers-emitters-and-semantic-indexing-testing-ready
- checklist-task-importers-emitters-and-semantic-indexing-closeout-ready
---

# Importers, Emitters, and Semantic Indexing

## Rationale

_Explain why this task exists or the business driver behind it._

The system needs to convert physical Markdown files into the Canonical AST and vice versa without losing data, and support fast text searching.

## Goal

_Describe the concrete result this task must produce._

Implement pulldown-cmark based importers, rowan-based reversible emitters, and setup Tantivy or SQLite FTS5 for the SearchIndex.

## Scope

_State what is in scope and what is out of scope._

Both Rust and Python adapters. Serialization payloads with serde.

## Implementation Path

_Outline the expected implementation route or affected surface._



## Validation

_List the checks required before this task can close._

- cargo test --manifest-path sldb-core/Cargo.toml --test importers

## Done When

_Name the observable condition that makes the task complete._
