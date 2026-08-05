# CLI Parity Contract

## Purpose and governing sources

This contract defines the terminal-facing surface of SLDB, ensuring continuity for existing users while enabling the new Clojure-backed engine.

Governing sources:
- `desk/atoms/cli-workflow-surface.md`
- `desk/atoms/docs-command-group.md`
- `desk/atoms/ast-command-group.md`
- `desk/atoms/fields-command-group.md`
- `desk/atoms/stores-command-group.md`
- `desk/atoms/models-command-group.md`
- `desk/atoms/sections-command-group.md`
- `desk/atoms/find-command-group.md`
- `desk/atoms/faq-command-group.md`
- `desk/atoms/help-command-group.md`

## Primary Command Groups (Plural-First)

The CLI must implement the following top-level groups:

1. **`docs`**: Lifecycle and tracking of documents.
   - `docs track`, `docs update`, `docs show`, `docs list`.
2. **`ast`**: Direct inspection of the canonical representation.
   - `ast view`, `ast find-node`.
3. **`fields`**: Inspection and extraction of metadata fields.
   - `fields get`, `fields list`.
4. **`sections`**: Navigation of document structure.
   - `sections list`, `sections show`.
5. **`stores`**: Maintenance of the local `.sldb` infrastructure.
   - `stores init`, `stores check`, `stores rebuild`.
6. **`models`**: Interaction with schema and templates.
   - `models list`, `models validate`.
7. **`find`**: Cross-cutting structural search.
   - `find text`, `find links`, `find refs`.

## Workflow Modes

### 1. Direct Mode
- Commands that operate on individual files without requiring an initialized store (e.g., `extract`, `render`).
- Mandatory for integration into pipelines that do not use the full graph store.

### 2. Store-Backed Mode
- Commands that utilize the `.sldb` graph for context (e.g., `find`, `track`).

## UX Constraints

- **Consistent Flags**: Standardized `-r/--recursive`, `-f/--format`, and `-o/--output`.
- **Error Reporting**: Human-readable errors from the Python orchestration layer, wrapping technical Clojure-core failures.
- **Output Formats**: Mandatory support for `text` (default) and `json`.
