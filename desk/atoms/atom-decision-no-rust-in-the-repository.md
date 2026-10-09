---
id: atom-decision-no-rust-in-the-repository
title: 'Decision: no Rust for domain logic; native pods are allowed'
five_wh_one_plus: why
tags:
- system:sldb
- epoch:v2
- domain:decisions
provenance: docs/v2/02-sustrato-computacional.md
---

# Decision: no Rust in the repository

## Answer

Rust adds nothing to the project's own value (identity rules, canonicalization, tree-as-index, evidence edges, succession, provenance) and a prior Rust implementation was already closed and removed. What is forbidden is domain logic spread over another language; native parts as pods are allowed. The `cero Rust` part of decision 8 was replaced by decision 19 (2026-10-06): Rust/C/Zig enter as pods, and tree-sitter is a Rust pod in-process (decision 29, `pods/treesitter`). Revisit only on a measured bottleneck. This retires decision-rowan-ast, decision-rusqlite-store, decision-rayon-parallelism and decision-pyo3-ffi.
