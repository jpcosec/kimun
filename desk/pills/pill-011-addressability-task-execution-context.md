---
id: pill-011-addressability-task-execution-context
tags:
- system:sldb
- topic:addressability
- topic:execution
---

# Addressability task execution context

## What

This task defines how SLDB names meaningful textual units inside authored documents. The output is an addressability contract for document-local structure, not a graph identity system.

## Why

Three existing surfaces already need a stable notion of what textual unit is being referenced:

- structural query design
- composition inputs and references
- semantic export provenance

Without an explicit contract, each surface will invent its own selectors.

## When

Apply this pill when defining canonical versus derived addresses, deciding which document subunits are meaningfully addressable, or reasoning about edit stability and provenance.

## Where

Read these first:

- `desk/tasks/task-define-sldb-addressability-model.md`
- `docs/architecture/sldb-text-layer-vs-kgdb-graph-layer.md`
- `src/sldb/core/ast.py`
- `src/sldb/core/node.py`
- `src/sldb/models/structured_doc.py`
- `src/sldb/store/query_engine/structural.py`
- `src/sldb/store/export.py`
- `docs/architecture/semantic-export-boundary.md`

Relevant unit types to evaluate:

- whole document
- section / subsection
- field
- list item
- table row
- paragraph or block
- composition target fragments

## How

Use existing repo behaviors as evidence, then define the missing contract. Prefer examples that show how the same unit would be referenced by a document author, a structural query, a composition feature, and a semantic export payload.

At minimum, produce a durable contract that answers:

- the addressable unit inventory
- canonical address shapes
- derived address shapes
- edit-stability rules
- provenance implications for export

If implementation is still unsafe afterward, extract follow-up tasks instead of forcing code prematurely.

## How Not

Do not equate textual location with graph identity, and do not treat the existing `st.{Model}.doc.field` store query surface as automatically sufficient for AST-like document addressability.
