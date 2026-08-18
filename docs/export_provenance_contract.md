# Semantic Export Provenance Contract

## Objective
When exporting SLDB document representations to downstream graph databases like KGDB, SLDB must preserve the exact chain of truth back to the authored Markdown source.

## Export Requirements

### 1. Document Identity
Every node in the exported graph must track its root document source.
- **`doc_address`**: The canonical address of the document (e.g., `doc.MyModel.MyDocument`).
- **`doc_version`**: The current hash or version identifier of the document, ensuring that KGDB can detect stale or overwritten information.

### 2. Field and Section Provenance
Facts and relationships derived from the text must point back to their specific origin.
- **`local_address`**: The address within the document (e.g., `field.tasks[2]` or `sec.roadmap`).
- **`model_identity`**: The Pydantic model and schema version that guided the extraction.

### 3. Semantic Tags and Extraction Lineage
- Explicit structural bounds: If a tag `[tag: foo]` appears inside `sec.roadmap`, the export must emit an edge linking the tag `foo` to the document, specifically annotated with `origin=sec.roadmap`.
- **Extraction Timestamp/Hash**: Export packages should contain the global store hash to correlate cross-document state at a single point in time.

## Handoff to KGDB
KGDB will ingest this export payload. Because SLDB provides stable block/field addressing, KGDB can confidently offer reverse-navigation: "This cluster of concepts came from `doc.Project.Plan.sec.objectives`." When a user clicks to edit, the IDE uses SLDB primitives to find and open the exact physical file and line number corresponding to that section.
