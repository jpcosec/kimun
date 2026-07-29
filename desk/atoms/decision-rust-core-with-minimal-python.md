---
id: decision-rust-core-with-minimal-python
title: Decision: Rust core with minimal Python
five_wh_one_plus: why
tags:
- system:sldb
- layer:architecture
- topic:decision
provenance: desk/drawer/features/feature-sldb-explicit-target-architecture.md
---

# Decision: Rust core with minimal Python

## Answer

The target pushes almost all engine responsibility into Rust and keeps Python minimal so the canonical runtime, hashing, persistence, import/export, and structural transforms live in one fast, strongly constrained core.

## Supporting points

- The refactor aims to minimize Python left after migration rather than preserve the old split.
- A Rust core is a better fit for hashing, durable persistence, graph operations, and exact structural runtime contracts.
- Python remains valuable as an orchestration and CLI shell during migration and continuity.

## Related atoms

### Supports

- [supports:: [[rust-core]]]
- [supports:: [[python-cli-orchestration-layer]]]

### Constrains

- [constrains:: [[phase-1-v1-replication]]]

### 5WH1+ neighborhood

- This atom is typed by its `five_wh_one_plus` field and should be queried together with nearby `what`/`how`/`when`/`where` atoms rather than as an isolated note.
