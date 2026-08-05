---
id: pill-013-composition-task-execution-context
tags:
- system:sldb
- topic:composition
- topic:execution
---

# Composition task execution context

## What

This task expands SLDB composition beyond the two currently evidenced modes:

- document transclusion using `![[...]]` in `src/sldb/links.py`;
- render-time child summarization using `StructuredNLDoc.__compositions__` in `src/sldb/models/structured_doc.py` and `tests/test_composition.py`.

The task is to define what additional text-first composition modes belong in SLDB.

## Why

The repo already shows that SLDB can compose text both by document inclusion and by rendering summaries from referenced docs. The open question is how far that model should extend while remaining document-first and readable.

## Required Reads

Read these first:

- `desk/tasks/task-expand-sldb-composition-modes.md`
- `desk/pills/pill-011-addressability-task-execution-context.md`
- `desk/pills/pill-012-query-task-execution-context.md`
- `src/sldb/links.py`
- `src/sldb/models/structured_doc.py`
- `tests/test_composition.py`
- `docs/architecture/sldb-text-layer-vs-kgdb-graph-layer.md`

## Current Repo Reality

Currently evidenced composition behavior:

- transclusion resolves tracked docs or paths and inlines nested Markdown;
- render composition loads referenced docs, extracts model payload, and formats lines/templates;
- composition presently operates at document-level references more than explicit sub-document target contracts.

## In Scope

Define which additional composition modes are valid for SLDB, such as:

- section-level composition
- field-summary composition
- query-driven fragment composition
- multi-document sectional synthesis that still materializes readable text

Also define how composition inputs should reference source units once addressability/query contracts exist.

## Out of Scope

Do not turn SLDB composition into:

- graph reasoning;
- non-textual analytics pipelines;
- KGDB-side relation synthesis;
- opaque aggregation that loses authored provenance.

## Expected Outputs

Produce a design that states:

- the supported composition mode taxonomy;
- what each mode consumes as input;
- how each mode materializes text;
- how provenance of composed fragments should be represented;
- what code surfaces would likely host future implementation.

## How

Keep composition text-first. A useful test is: can the output still be read as a coherent authored document artifact rather than merely as an intermediate graph projection?

## How Not

Do not define composition in a way that requires KGDB-style traversal or hidden graph semantics just to decide what text should appear.
