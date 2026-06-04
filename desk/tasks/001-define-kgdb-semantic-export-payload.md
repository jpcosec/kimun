---
id: '001'
domain: contract
status: ready_for_closeout
priority: p0
depends_on: []
created: '2026-06-04'
---

# Define KGDB semantic export payload

## Objective

Define the SLDB-owned export payload that KGDB can ingest without scraping `.sldb/runtime` internals directly.

## Reference

- Inbox: `desk/inbox/20260604-000004-question-semantic-export-for-kgdb.md`

## Scope

- Model entries and model semantics.
- Document entries, paths, semantic tags, and hashes.
- Section context records.
- Semantic DAG nodes and equivalences.
- Store provenance metadata.

## Non-Goals

- Source-code relation extraction.
- Deskops-specific workflow edges.
- KGDB persistence implementation.

## Validation

- Add a fixture or golden payload generated from a small tracked store.
- Run the relevant SLDB tests for semantic index/export behavior.

## Handoff

- Implementation and validation were completed by a subagent on 2026-06-04.
- JSON Schema and fixture validation passed with `jsonschema`.
- Focused tests passed: `python -m pytest tests/store/test_semantic_rebuild.py tests/store/test_models_io.py tests/test_validation_semantics.py` with `20 passed`.
- The task remains `ready_for_closeout` because no dedicated closing commit has been made.
