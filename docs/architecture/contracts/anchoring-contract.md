> **Superseded (2026-09-06).** Governed by `docs/v2/02-sustrato-computacional.md §6 and docs/v2/09-evaluador-anclado.md`: anchor states are derived per endpoint (intact/superseded/orphan) and the evaluator anchors are documents of the AnchorDoc model.

# Anchoring Contract

## Purpose and governing sources

This contract defines the mechanism for stable addressability into document fragments and external artifacts.

Governing sources:
- `desk/atoms/ast-anchor.md`
- `desk/atoms/text-anchor.md`
- `desk/atoms/external-anchor.md`
- `desk/atoms/stable-selector.md`
- `desk/atoms/document-path.md`
- `docs/architecture/spec2viz/target-anchoring.yml`

## Anchor Taxonomy

### 1. Internal AST Anchors
- Addressability to specific AST nodes (e.g., a specific heading or field).
- **Selector**: Uses a path-like selector (e.g., `doc_id#section_id`).

### 2. External Anchors
- Addressability to files or resources outside the SLDB graph (e.g., source code lines, URL segments).
- **Selector**: Uses a `stable-selector` string that can survive minor formatting changes in the target.

### 3. Text/Line Anchors
- Fallback addressability based on line/character offsets.
- **Selector**: `L{line}C{col}`.

## Stable Selectors

- **Resilience**: Selectors must be designed to survive non-structural changes (e.g., adding whitespace around a target).
- **Resolution**: The Clojure engine must be able to resolve a selector to a specific byte range or AST node with high confidence.

## Anchor Lifecycle

- **Extraction**: Anchors are detected during document import/indexing.
- **Validation**: The system must verify that an anchor still "hits" its target during `validate` or `track` operations.
- **Broken Anchors**: Detection of stale or missing targets must be reported as a validation error.

## Parity Floor

- Support for all v1-style anchor formats.
- Implementation of the `stable-selector` logic for file-system references.
- Integration of anchors into the `find` and `docs show` command output.
