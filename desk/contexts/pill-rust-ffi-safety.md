---
id: pill-rust-ffi-safety
title: "PILL: Rust FFI Safety"
tags:
- artifact:pill
- layer:core
- layer:shell
- domain:ffi
---

# Rust FFI Safety

## Context
SLDB uses a dual-language architecture: Rust for the canonical core and Python for the CLI orchestration. PyO3 is the bridge.

## Rules
1. **No Panics across FFI**: All Rust functions exposed to Python must return `Result<T, E>`. Use `catch_unwind` if necessary to prevent panics from crossing the FFI boundary, which would cause a segfault in the Python interpreter.
2. **Error Translation**: Map Rust internal errors to meaningful Python exceptions (e.g., `PyValueError`, `PyRuntimeError`).
3. **Handle Management**: When passing complex Rust objects to Python, prefer opaque handles or `Py<T>` references managed by PyO3.
4. **Thread Safety**: Ensure that the Global Interpreter Lock (GIL) is respected or released (`Python::allow_threads`) when performing long-running Rust operations.
5. **Validation at Boundary**: Validate all inputs at the FFI boundary (both sides). Python is dynamic; Rust must enforce types.
