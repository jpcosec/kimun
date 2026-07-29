---
id: pill-rust-guidelines
tags:
- workspace:desk
---

# Pill: Rust Guidelines

This pill ensures the Rust Core (`sldb-core`) implementation adheres to the project's strict architecture:
- **Zero-loss Reversibility**: Use `rowan` strictly. No data loss (including trivia/whitespace) is allowed when mutating the AST.
- **Append-Only Store**: `rusqlite` interactions must follow the append-only paradigm. Do not UPDATE records; append new edges/nodes.
- **Identity First**: Hashes (`blake3`) dictate the graph, not internal autoincrements.
- **Safety**: Fail fast with `thiserror`. Do not panic.
