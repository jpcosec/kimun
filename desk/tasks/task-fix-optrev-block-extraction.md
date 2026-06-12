---
id: task-fix-optrev-block-extraction
domain: sldb/extraction
status: open
priority: p1
depends_on: []
created: "2026-06-12"
---

# Fix optrev block extraction

## Objective

Fix the known `DataExtractor` block matching bug that currently requires skipping advanced marker family roundtrip coverage.

## Reference

- Skipped test: `tests/test_standalone.py::test_advanced_marker_families_render_and_extract`
- Skip reason: `Known issue with DataExtractor optrev block matching`
- Related surfaces: `src/sldb/core/data_extractor.py`, `src/sldb/core/template_extractor.py`, `src/sldb/core/handlers/text.py`

## What To Fix

`AdvancedMarkersDoc` should render and extract without skipping the test. Optional reversible markers such as `⸢optrev•subtitle⸥` must not break block matching or corrupt nearby field extraction.

The fix should preserve current behavior for normal `rev`, `rev,list`, `rev,dict`, `render`, and safe `py` marker handling.

## How To Do It

Start by unskipping or locally reproducing `test_advanced_marker_families_render_and_extract` so it fails for the current bug.

Trace how template recipes are generated for optional reversible markers and how `DataExtractor` chooses matching document blocks.

Make the smallest extraction change that lets optional blocks match absent or empty rendered content without stealing adjacent block content.

## Validation

- Unskip `test_advanced_marker_families_render_and_extract`.
- Run `pytest tests/test_standalone.py -k 'advanced_marker_families or python_markers'`.
- Run the full `pytest` suite.

## Done When

- The advanced marker family roundtrip test is active and passing.
- Optional reversible block markers extract correctly when values are absent.
- Existing marker and extraction tests still pass.
