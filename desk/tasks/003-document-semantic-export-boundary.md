---
id: '003'
domain: docs
status: open
priority: p1
depends_on: ['002']
created: '2026-06-04'
---

# Document semantic export boundary

## Objective

Document that SLDB owns semantic document truth and exports graph-ready semantic payloads for KGDB, while source-file and workflow-specific graph edges belong downstream.

## Reference

- Inbox: `desk/inbox/20260604-000004-question-semantic-export-for-kgdb.md`

## What to Document

- What the export includes.
- What the export intentionally excludes.
- Example SLDB-to-KGDB flow.
- How this differs from SLDB semantic search.

## Validation

- Docs examples match implemented command behavior.
- Relevant SLDB tests pass.
