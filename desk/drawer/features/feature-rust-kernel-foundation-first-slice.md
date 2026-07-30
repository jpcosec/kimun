---
id: feature-rust-kernel-foundation-first-slice
status: proposed
summary: First implementation macrotask for the Rust-owned canonical kernel, append-only persistence, transaction flow, and kernel API boundary.
tags:
- workspace:desk
- artifact:feature
- system:sldb
history:
- "2026-07-29: drafted as first-slice macrotask planning feature."
references:
- also_core.md
- core_README.md
- diagramas_core.md
- interfaces.md
- libraries_core.md
- plan_core.md
- desk/atoms/rust-core.md
- desk/atoms/graph-store.md
- desk/atoms/transaction-plan.md
- docs/architecture/target-system-overview.md
- docs/architecture/spec2viz/target-components.yml
- docs/architecture/spec2viz/target-runtime.yml
- docs/architecture/spec2viz/target-store-graph.yml
depends_on: []
---

# Rust kernel foundation first slice

## Goal

Establish the Rust-owned canonical kernel and database-backed authority boundary as the first implementation lane.

## Includes

- kernel-owned canonical state model
- append-only transaction and revision persistence
- kernel API boundary
- transaction validation and capability gate
- rebuildable derived-state boundary
- no-authority status for caches, projections, and indexes

## Excludes

- embeddings and semantic provider integration
- broad effect automation
- multi-surface expansion beyond first-slice needs

## Must stay true

- Rust kernel + canonical database state is the authority
- Markdown and Lisp do not own truth
- derived artifacts are rebuildable and non-authoritative

## Needs design or grounding before promotion

- exact first-slice storage backend choice and fallback policy
- exact transaction/revision payload envelope
- exact kernel API call surface needed by Markdown and Lisp first slice
- exact degraded/recovery envelope required before calling the store usable

## Exit shape

A promotable implementation task set exists for the kernel/store foundation without semantic-scope expansion.
