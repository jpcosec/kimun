# Tag Domain Map

This file defines the target tagging shape for `desk/atoms/`.

## Rule

Each atom should carry:

- `system:sldb`
- exactly one `domain:*` leaf tag

The `domain:*` leaf must come from `desk/atoms/tag-namespaces.yaml`.

## Why this shape

- it separates the ontology by durable knowledge domains instead of ad hoc topic words
- it keeps tag meaning stable even when `five_wh_one_plus` changes
- it aligns the atom space with the spec2viz target diagrams

## Spec2viz alignment

The domain tree is intentionally aligned to the target diagrams:

- `domain:surfaces.*` maps to `ProductSurfaces`, `PythonCLI`, `CommandGroups`, `DirectMode`, `StoreBackedMode`, and `VisualUX`
- `domain:model.*` maps to `CanonicalAST`, `CanonicalRelations`, and `AnchorNodes`
- `domain:pipeline.*` maps to `Importers`, `MarkdownImporter`, `Emitters`, `MarkdownEmitter`, and `DocumentMaterializer`
- `domain:store.*` maps to `GraphStore`, `ASTPersistence`, `DerivedIndexes`, `HistoryArtifacts`, `Hashing`, and `HashFields`
- `domain:runtime.*` maps to `Adapters`, `SemanticExporter`, projection surfaces, provenance, and query/runtime behavior
- `domain:implementation.*` maps to the PythonCLI/RustCore implementation split
- `domain:architecture.*` maps to cross-cutting decisions, boundaries, and migration sequencing
- `domain:quality.*` maps to implementation practice around patterns, testing, and clean code

## Notes

- Question-shape belongs in `five_wh_one_plus`, not in tags.
- Multi-area atoms should still choose one dominant domain leaf and connect to neighboring domains through links.
- If a future atom genuinely falls outside the tree, add a new leaf deliberately instead of reviving generic topic tags.
