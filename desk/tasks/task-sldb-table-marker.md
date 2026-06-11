---
id: task-sldb-table-marker
domain: sldb/templates
status: open
priority: p1
depends_on: []
created: "2026-06-11"
---

# Add reversible table template marker

## Objective

Implement a generic SLDB template marker for Markdown tables so structured table-like fields can render and extract without flattening them into opaque text.

## Reference

- Source issue: `desk/issues/issue-sldb-table-marker.md`
- Context pill: `desk/pills/pill-003-template-marker-roundtrip-contract.md`
- Read first: `src/sldb/core/template_extractor.py`
- Read first: `src/sldb/core/data_extractor.py`
- Read first: `src/sldb/core/renderer_engine/yaml.py`
- Read first: `src/sldb/runtime/validation.py`
- Use existing `⸢rev,list•...⸥` tests as the baseline testing pattern.

## What To Fix

Add support for a marker shaped like `⸢rev,table[col1,col2]•fieldname⸥`, with an optional explicit column list.

The marker should render a `list[dict]` as a standard pipe Markdown table and extract a matching Markdown table back into structured data keyed by the table headers.

The first implementation should keep cell values plain strings and avoid nested or multiline cell semantics unless a later task explicitly scopes that behavior.

## How To Do It

Start by tracing how `rev` and `rev,list` markers are parsed into recipes, rendered, and extracted.

Then add the smallest table-specific branch at the same boundary, keeping storage/model semantics unchanged.

Prefer explicit columns from `table[...]`; if omitted, infer columns from the first row and document the behavior in tests.

## Validation

- Add render tests for explicit columns.
- Add extraction tests for Markdown tables.
- Add roundtrip tests for render -> extract -> same structured value.
- Cover empty cells and empty/no-row tables if the chosen behavior is defined by the implementation.
- Run the relevant pytest suite.

## Done When

- `⸢rev,table[...]•field⸥` renders `list[dict]` values as Markdown tables.
- Extracting those tables returns the expected structured value.
- Roundtrip behavior is covered by tests.
- Existing marker behavior still passes.
