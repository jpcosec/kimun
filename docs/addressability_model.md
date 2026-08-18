# SLDB Addressability Model

## Principles of Addressability
The addressability model defines how meaningful textual units are located, queried, composed, and updated. Addresses must be stable across minor edits, explicitly derivable from context, and maintain provenance when exported.

## Address Spaces

### 1. Document Level (Canonical)
- **Format:** `doc.<model_name>.<document_name>`
- **Stability:** High. The document name acts as the root identifier.
- **Role:** The primary unit of tracking in the `sldb-store`.

### 2. Section Level (Canonical)
- **Format:** `doc.<document_name>.sec.<section_slug>`
- **Stability:** High. Section slugs are derived from heading text (e.g., `# Roadmap` -> `roadmap`). If the exact heading text changes, the address breaks, forcing a re-evaluation of link provenance.
- **Role:** Structural bounding boxes for content.

### 3. Field Level (Derived)
- **Format:** `doc.<document_name>.field.<field_name>`
- **Stability:** Highest. Field names are enforced by the `__template__` definitions of the runtime model.
- **Role:** Property extraction and reverse-rendering targets.

### 4. Fine-Grained Units (List Items & Table Rows)
- **Format:** `doc.<document_name>.field.<field_name>[<index>]`
- **Stability:** Low to Medium. Relies on zero-based indexing. Moving items in a list shifts the addresses of subsequent items.
- **Role:** Used primarily for transient querying or appending logic, not for long-term canonical links unless stabilized by a marker or ID (e.g. `[id: 123]`).

## Canonical vs Derived
- **Canonical addresses** refer to the physical layout and parsed structure directly (Document Names, Headings).
- **Derived addresses** refer to elements that only make sense within the context of a bound Pydantic Model (Fields, Values). 

## Provenance Contract
When a composition (transclusion) is generated, SLDB must retain the original address in the export format, allowing the consumer to trace back exactly which document and field generated that specific line of output.
