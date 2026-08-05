---
id: pill-012-query-task-execution-context
tags:
- system:sldb
- topic:query
- topic:execution
---

# AST query task execution context

## What

This task defines the first public query primitives that operate on SLDB document structure itself. It should describe what a user or downstream tool can ask about a document's structural units once addressability is defined.

## Why

SLDB already exposes store structural queries, but those queries are centered on tracked model/doc/field payload access. This task is about document-structure queries over AST-like units, not just store payload lookup.

## Required Reads

Read these first:

- `desk/tasks/task-design-sldb-ast-query-primitives.md`
- `desk/pills/pill-011-addressability-task-execution-context.md`
- `src/sldb/cli/commands/query.py`
- `src/sldb/store/query_engine/structural.py`
- `src/sldb/store/query_engine/structural_queries.py`
- `src/sldb/core/ast.py`
- `docs/architecture/sldb-text-layer-vs-kgdb-graph-layer.md`

## Current Repo Reality

Existing query surfaces already support:

- `ls`, `get`, `glob`, and `find` over `st`, `se`, and `gse` roots;
- structural document lookup by tracked model/doc/field path;
- semantic and global-semantic queries that are downstream from authored structure.

What is missing is an explicit public contract for questions like:

- find section by path or title
- retrieve section body
- locate owning field/block
- enumerate structural children
- perform document-local structural search

## In Scope

Define the first query primitives, their inputs, and their return semantics. The result can be a design/API document before it becomes code.

Questions to answer:

- what unit types can be queried;
- which queries require canonical addresses versus derived selectors;
- what should queries return: text, metadata, structural nodes, or addresses;
- which primitives belong in document-local APIs versus store-level CLI surfaces.

## Out of Scope

Do not define:

- graph traversal, equivalence expansion, or inference;
- KGDB query semantics;
- a sprawling search language before the core primitives are clear.

## Expected Outputs

Produce a durable design that names:

- the minimum query primitive set;
- example inputs and outputs;
- relation to existing `st` structural query surfaces;
- follow-up implementation tasks if runtime work should be staged.

## How

Anchor the design in the addressability model. Show how each primitive consumes an address or selector and what stable result shape it returns.

## How Not

Do not silently overload current `st.{Model}.doc.field` semantics and call that “AST query design” if the result still cannot name or return meaningful document subunits.
