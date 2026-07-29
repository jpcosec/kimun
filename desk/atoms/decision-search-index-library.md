---
id: decision-search-index-library
title: Decision search index library
five_wh_one_plus: why
tags:
- architecture:decision
- concept:store
provenance: desk/drawer/features/feature-rust-library-stack.md
---

# Decision search index library

For deriving the `SearchIndex`, either `tantivy` (industrial-grade full-text search in Rust) or SQLite's `FTS5` (via `rusqlite`) will be used. Both options provide the capabilities necessary to power rapid lexical searches over the text extracted from the Canonical AST.
