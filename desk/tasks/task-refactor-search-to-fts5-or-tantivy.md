---
id: task-refactor-search-to-fts5-or-tantivy
status: draft
summary: 'Technical Debt: Upgrade simplistic LIKE search to Tantivy or SQLite FTS5'
tags:
- workspace:desk
- artifact:task
routine: routine-task-implement-rust-core
current_node: execution
history: []
references:
- desk/atoms/decision-search-index-library.md
depends_on: []
pills: []
files: []
checklists: []
---

# Refactor Search to FTS5 or Tantivy

## Rationale

The initial search implementation relies on full table scans (`LIKE %...%`) in SQLite, ignoring `decision-search-index-library.md` which mandated robust full-text indexing.

## Goal

- Remove simplistic payload scanning.
- Integrate either `tantivy` or setup SQLite `FTS5` virtual tables.
- Synchronize node insertion with the FTS/Tantivy index.

## Scope

- Rust Core `sldb-core/src/store/search.rs`
- SQLite schema in `sldb-core/src/store/schema.rs`

## Done When

Semantic and full-text searches resolve efficiently using a proper search index without O(N) table scans.
