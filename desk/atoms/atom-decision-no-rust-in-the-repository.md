---
id: atom-decision-no-rust-in-the-repository
title: 'Decision: no Rust in the repository'
five_wh_one_plus: why
tags:
- system:sldb
- epoch:v2
- domain:decisions
provenance: docs/v2/02-sustrato-computacional.md
---

# Decision: no Rust in the repository

## Answer

Rust adds nothing to the project's own value (identity rules, canonicalization, tree-as-index, evidence edges, succession, provenance) and a prior Rust implementation was already closed and removed. Where Rust genuinely leads (tree-sitter, BLAKE3, WASM sandboxing) the kernel consumes WASM artifacts built by others instead of writing Rust. Revisit only on a measured bottleneck. This retires decision-rowan-ast, decision-rusqlite-store, decision-rayon-parallelism and decision-pyo3-ffi.
