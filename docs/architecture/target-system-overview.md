# Target System Overview

This document captures the current target architecture direction for the SLDB refactor.

## Main Direction

- **Phase 1**: Replicate v1 functionality
- canonical AST is the sovereign internal model
- links and anchors are canonical AST/store concepts, not late projections
- model = empty AST
- store = local graph database connecting ASTs (append-only / immutable)
- render round-trips for reversible document families must stay exact
- Rust should own almost everything needed to recreate SLDB v1 behavior (extensibility is done in Rust)
- Python should stay as a minimal CLI orchestration shell, but retains responsibility for Git interactions
- no semantic baseline expansion now beyond existing tags and store/field indexing

## Graph Store Direction

The current YAML-index-first store should evolve into an **append-only, immutable graph-store-first** local `.sldb/` workspace.

Keep from v1:

- local project store
- tracked document workflow (orchestrated by Python)
- integrity checks
- indexes and rebuilds
- semantic export boundary

Change internally:

- persist canonical AST nodes and typed edges
- persist canonical links between ASTs
- persist anchor nodes
- treat hashes as node fields
- allow relations to carry their own extensible payload structure (`RelationAST`), which can be indexed for store, semantic graphs, tagging, etc.
- materialize section/field/search/semantic views as derived indexes

## Reversible And Non-Reversible Families

There are two broad document families.

### Reversible families

These support the strong cycle:

- AST -> render -> AST -> render

The rendered text must remain equal across the cycle.

### Non-reversible families

These may still have partial structure or family-specific addressing:

- PDF by XML/page or similar locator
- HTML by DOM-like locator
- code by tree-sitter locator
- plain text by char/paragraph/page locator

These still participate in anchoring, but not necessarily in the same reversible contract.

## Spec2viz diagrams

- `docs/architecture/spec2viz/target-components.yml`
- `docs/architecture/spec2viz/target-runtime.yml`
- `docs/architecture/spec2viz/target-store-graph.yml`
- `docs/architecture/spec2viz/target-anchoring.yml`
