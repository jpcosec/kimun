---
id: decision-pyo3-ffi
title: Decision pyo3 FFI
five_wh_one_plus: why
tags:
- architecture:decision
- concept:rust-core
provenance: desk/drawer/features/feature-rust-library-stack.md
---

# Decision pyo3 FFI

The architecture uses `pyo3` as the FFI boundary between the Python CLI orchestration layer and the Rust core. This avoids heavy IPC overhead, allowing Python to directly invoke Rust parsers, store queries, and materialization logic in-memory.
