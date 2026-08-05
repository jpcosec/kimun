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

Three existing surfaces already need a stable notion of “what unit are we talking about?”

- structural query design;
- composition inputs and references;
- semantic export provenance.

Without an explicit contract, each surface will invent its own selectors.

## Required Reads

Read these first:

- `desk/tasks/task-define-sldb-addressability-model.md`
- `docs/architecture/sldb-text-layer-vs-kgdb-graph-layer.md`
- `src/sldb/core/ast.py`
- `src/sldb/core/node.py`
- `src/sldb/models/structured_doc.py`
- `src/sldb/store/query_engine/structural.py`
- `src/sldb/store/export.py`
- `docs/architecture/semantic-export-boundary.md`

## Current Repo Reality

Today the repo already has several kinds of address-like references:

- tracked document identity via store model/doc names;
- field-level access through structural query addresses such as `st.{Model}.doc.field`;
- section export ids shaped like `Model:doc#section.path`;
- AST block trees produced from Markdown tokens in `src/sldb/core/ast.py`.

These are related but not yet unified into one explicit doctrine for document subunits.

## In Scope

Define:

- which textual units are meaningfully addressable in SLDB;
- which addresses are canonical versus derived;
- how addresses behave under normal edits such as heading renames, list insertions, or field value changes;
- whether some units are only queryable but not canonical export anchors;
- how document-local addresses relate to tracked document identity.

Likely units to evaluate:

- whole document
- section / subsection
- field
- list item
- table row
- paragraph or block
- composition target fragments

## Out of Scope

Do not define:

- KGDB global entity identity;
- graph equivalence or entity unification;
- final end-user CLI syntax for every future query surface;
- a full implementation of all address resolvers unless needed as a proof point.

## Expected Outputs

At minimum produce a durable contract doc that answers:

- the addressable unit inventory;
- canonical address shapes;
- derived address shapes;
- edit-stability rules;
- provenance implications for export.

If implementation is still unsafe afterward, extract follow-up tasks instead of forcing code prematurely.

## How

Use existing repo behaviors as evidence, then define the missing contract. Prefer examples that show how the same unit would be referenced by a document author, a structural query, a composition feature, and a semantic export payload.

## How Not

Do not equate textual location with graph identity, and do not treat the existing `st.{Model}.doc.field` store query surface as automatically sufficient for AST-like document addressability.
