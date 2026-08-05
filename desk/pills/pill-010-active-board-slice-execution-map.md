---
id: pill-010-active-board-slice-execution-map
tags:
- system:sldb
- workspace:desk
- topic:execution
- topic:design
---

# Active board slice execution map

## What

This board is a four-task design slice for SLDB document structure. The slice defines the contracts that later implementation work will consume:

1. addressability of meaningful textual units;
2. structural query primitives over those units;
3. richer text-first composition modes;
4. provenance-preserving semantic export to downstream graph systems.

A zero-context agent should treat these tasks as one ordered design chain, not as independent tickets.

## Why

The current repo already has partial surfaces for AST parsing, store structural querying, transclusion, render-time composition, and semantic export. Those surfaces encode assumptions, but the cross-cutting contract connecting them is still under-specified. This board exists to make those assumptions explicit before broad runtime expansion.

## Required Reads

Read these before changing anything in this slice:

- `desk/tasks/Board.md`
- `desk/tasks/task-define-sldb-addressability-model.md`
- `desk/tasks/task-design-sldb-ast-query-primitives.md`
- `desk/tasks/task-expand-sldb-composition-modes.md`
- `desk/tasks/task-tighten-semantic-export-provenance-contract.md`
- `docs/architecture/sldb-text-layer-vs-kgdb-graph-layer.md`
- `docs/architecture/semantic-export-boundary.md`
- `src/sldb/core/ast.py`
- `src/sldb/models/structured_doc.py`
- `src/sldb/links.py`
- `src/sldb/cli/commands/query.py`
- `src/sldb/store/query_engine/structural.py`
- `src/sldb/store/query_engine/structural_queries.py`
- `src/sldb/store/export.py`
- `tests/test_composition.py`

## Current Repo Reality

The repo already provides:

- Markdown AST parsing into `SLDBNode` trees in `src/sldb/core/ast.py`.
- Store structural queries over tracked model/doc/field addresses using the `st.{Model}.doc.field` family.
- Transclusion composition through `[[target]]` / `![[target]]` in `src/sldb/links.py`.
- Render-time composition through `StructuredNLDoc.__compositions__` in `src/sldb/models/structured_doc.py`.
- Semantic export with document and section provenance in `src/sldb/store/export.py` and `docs/architecture/semantic-export-boundary.md`.

The gap is not “nothing exists.” The gap is that document-subunit addressability and its relationship to query/composition/export are not yet named as a coherent contract.

## Execution Order

Use this order unless the board changes explicitly:

1. `task-define-sldb-addressability-model`
2. `task-design-sldb-ast-query-primitives`
3. `task-expand-sldb-composition-modes`
4. `task-tighten-semantic-export-provenance-contract`

Later tasks may read ahead, but they should not finalize contracts that assume answers the upstream tasks have not yet written down.

## Expected Outputs

This slice should primarily produce durable design artifacts:

- architecture docs or ADR-style docs under `docs/architecture/`
- atoms if a concept becomes reusable beyond a single task
- follow-up implementation tasks if runtime changes are implied but not yet safe to harden
- code changes only when needed to validate or demonstrate a now-explicit contract

## How

Work text-first and contract-first. Use existing repo surfaces as evidence, describe what is already true, then define the missing contract in a way later tasks can consume without reopening the architecture debate.

## How Not

Do not start by scattering code changes across query, composition, and export surfaces without first documenting the contract those changes are supposed to implement.
