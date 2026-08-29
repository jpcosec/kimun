# Durable Atom Draft Set

Selection rule: this set includes only atoms that are explicitly durable across the source documents and are stable enough to create now as long-lived ontology anchors. It excludes placeholders, open design questions, exact storage/serialization choices, exact selector grammar, embeddings, richer semantic-indexing placeholders, text-as-graph placeholders, and exact CLI command matrices.

Source basis:
- `desk/drawer/features/feature-sldb-explicit-target-architecture.md`
- `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md`
- `desk/drawer/features/feature-canonical-ast-design-current-state.md`
- `subagent/atom-ontology-map.md`

## 1. Canonical AST
- **Domain:** Canonical substrate
- **Concise answer:** The canonical AST is the source of truth and the shared substrate for the refactored SLDB system.
- **Supporting points:**
  - Markdown is important, but it is an importer/exporter and editing surface rather than the sovereign representation.
  - CLI workflows, future visual UX, importers, emitters, and infrastructure all depend on this shared substrate.
  - Store-like behavior is re-scoped as infrastructure over the AST rather than the conceptual center.

## 2. Canonical Identity
- **Domain:** Identity
- **Concise answer:** Durable document, node, and field units need stable canonical identity inside the model.
- **Supporting points:**
  - The AST explicitly carries stable document and node identity.
  - Field-aware semantics require identity that persists across extraction, rendering, linking, and recomputation.
  - Provenance, hashes, anchors, and projections all depend on stable identity to remain durable.

## 3. Canonical Existence
- **Domain:** Existence
- **Concise answer:** A document can exist canonically before any Markdown file or other materialized artifact exists.
- **Supporting points:**
  - Materialization is optional rather than mandatory.
  - Render/materialize is an explicit projection step rather than the definition of existence.
  - Internal workflows must be able to operate directly on canonical AST-level documents and nodes.

## 4. Document
- **Domain:** Structure
- **Concise answer:** A document is a first-class canonical unit that owns structured content and participates in projections and lineage.
- **Supporting points:**
  - The AST must represent documents directly rather than only files or rendered text.
  - Document-level identity, provenance, hashing, and projection all depend on documents being explicit units.
  - CLI and future visual tooling both need inspectable document-level entities.

## 5. Node
- **Domain:** Structure
- **Concise answer:** A node is the primary owned structural unit inside the canonical AST.
- **Supporting points:**
  - Nodes carry kind/subtype, payload, attributes, and source/origin span information.
  - Nodes are the units addressed by links, anchors, provenance, and hashes.
  - Nodes sit on the tree spine while also participating in graph-capable side relations.

## 6. Tree Spine
- **Domain:** Structure
- **Concise answer:** The canonical document model keeps a tree spine for ownership and order.
- **Supporting points:**
  - Parent/child relationships and sibling order are part of the minimum structural layer.
  - Slots or named regions belong to this canonical ownership model.
  - The system is graph-capable, but the tree spine remains the core structural backbone.

## 7. Field Binding
- **Domain:** Schema and field semantics
- **Concise answer:** Field binding connects canonical structure to typed document-model semantics.
- **Supporting points:**
  - The refactor preserves the original SLDB promise around typed models rather than abandoning it.
  - One or more AST nodes may bind to typed fields without making field contracts the only structure.
  - Extraction, rendering, validation, reversibility, and composition all depend on explicit field binding.

## 8. Field Path
- **Domain:** Schema and field semantics
- **Concise answer:** Field path gives a durable way to locate a field within nested model composition.
- **Supporting points:**
  - The documents explicitly require field-path awareness in the canonical model.
  - Nested composition and multi-level typed structures depend on stable field paths.
  - Precise provenance, validation, and visual inspection need field-level addressability.

## 9. Schema Binding
- **Domain:** Schema and field semantics
- **Concise answer:** Schema binding attaches canonical content to an explicit model or schema contract.
- **Supporting points:**
  - Typed models remain first-class entities in the product promise.
  - Models and templates must become inspectable entities rather than hidden implementation behavior.
  - The AST generalizes Pydantic-style contracts instead of replacing them with untyped structure.

## 10. Type Contract
- **Domain:** Schema and field semantics
- **Concise answer:** Type contract records the expected type and validation semantics for bound canonical content.
- **Supporting points:**
  - The AST must preserve expected type information rather than treating content as opaque text.
  - Validation and rendering continuity both depend on explicit type-aware contracts.
  - Requiredness, defaults, cardinality, and other typed constraints are downstream of this contract layer.

## 11. Stable Selector
- **Domain:** Addressability
- **Concise answer:** A stable selector is the durable addressability mechanism for meaningful canonical units.
- **Supporting points:**
  - Stable addressability is required for links, graph projection, provenance, and future visual inspection.
  - Structural paths, anchors, aliases, and fragments all belong to this addressability domain.
  - Canonical units must stay targetable even when materializations or surface views change.

## 12. Link Reference
- **Domain:** Relations
- **Concise answer:** A link reference is a typed canonical relation from one unit to another target.
- **Supporting points:**
  - Links move out of ad hoc string behavior and into explicit canonical structure.
  - Link relations need target identity, resolution state, and provenance.
  - Graph-friendly navigation and semantic projection depend on links being first-class relations.

## 13. Transclusion Reference
- **Domain:** Relations
- **Concise answer:** A transclusion reference is a canonical relation that composes content by reference.
- **Supporting points:**
  - Transclusions are explicitly named as part of what the AST must represent.
  - They are distinct from ordinary links because they affect composition and downstream materialization.
  - Treating transclusions as first-class relations keeps composition logic inspectable and graph-friendly.

## 14. External Anchor
- **Domain:** Anchoring
- **Concise answer:** An external anchor is an evidence-backed canonical attachment to content outside the local document.
- **Supporting points:**
  - External anchoring is separate from local tree structure and must be modeled explicitly.
  - Durable external anchors require locator data, sampled text or context, and content fingerprint evidence.
  - Reattachment policy and confidence belong to the anchoring concept because external targets can drift.

## 15. Hook Binding
- **Domain:** Behavior
- **Concise answer:** A hook binding is a declarative canonical record of executable behavior attached to nodes or fields.
- **Supporting points:**
  - Hooks must be describable in the AST even when execution happens outside the core runtime.
  - Input contract, output contract, execution policy, and verification expectations are part of the durable concept.
  - Making hooks explicit avoids hiding behavior inside ad hoc implementation code.

## 16. Provenance Record
- **Domain:** Lineage and history
- **Concise answer:** A provenance record captures where canonical data came from and how it changed over time.
- **Supporting points:**
  - The target architecture requires lineage beyond git-only history.
  - Provenance must connect nodes and fields to import sources and transformations.
  - Canonical history needs explicit timestamps, revision knowledge, and evidence-bearing change records.

## 17. Authorship State
- **Domain:** Lineage and history
- **Concise answer:** Authorship state distinguishes authored canonical content from derived canonical content.
- **Supporting points:**
  - The docs repeatedly call out authored-versus-derived status as a required semantic distinction.
  - Reversibility, extraction, rendering, and validation behavior all depend on this distinction.
  - Future CLI and visual inspection both need to expose whether a value is authored or derived.

## 18. Node Hash
- **Domain:** Computation and recomputation
- **Concise answer:** A node hash is the structural fingerprint used to support integrity, caching, and incremental recomputation.
- **Supporting points:**
  - Structural hashing is part of the target architecture from the start rather than a later optimization.
  - Node and subtree hashes support Merkle behavior and fine-grained invalidation.
  - Hashing makes projections and infrastructure recomputation depend on canonical structure rather than file timestamps alone.

## 19. Projection
- **Domain:** Projection and materialization
- **Concise answer:** A projection is any derived output or view produced from the canonical AST without becoming the source of truth.
- **Supporting points:**
  - Markdown materialization is one projection among others, not the definition of the document.
  - Graph exports, search views, editor views, and runtime artifacts all belong to this derived layer.
  - Projection keeps canonical existence separate from emission and presentation.

## 20. CLI Workflow Surface
- **Domain:** Operating surface
- **Concise answer:** The CLI workflow surface is the terminal-facing operating surface that must remain recognizable across the refactor.
- **Supporting points:**
  - CLI continuity is an explicit product constraint, even while internals change deeply.
  - The recognizable SLDB command family should survive with reduced clutter and cleaner implementation.
  - The CLI is a consumer of the canonical substrate rather than an independent truth source.

## 21. Visual UX Surface
- **Domain:** Operating surface
- **Concise answer:** The visual UX surface is the future structured editing and inspection surface built over the same canonical substrate.
- **Supporting points:**
  - The architecture must be ready from the start for visual editing, graph inspection, and model/template browsing.
  - A visual surface cannot safely reverse-engineer truth from Markdown artifacts alone.
  - CLI and visual UX are sibling consumers of the same AST-centered runtime.

## Excluded from this draft set
- Embeddings and embedding storage details
- Semantic-index placeholders such as proposition or matrix-style engines
- Text-as-graph placeholders
- Exact Clojure schema, serialization format, storage engine, and selector grammar
- Exact CLI command continuity matrix

## Review findings
- info: `desk/drawer/features/feature-sldb-explicit-target-architecture.md` — The strongest repeated invariant is that the canonical AST replaces Markdown and store-centric thinking as the architectural center.
- info: `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md` — Optional materialization and CLI continuity are product-level constraints, not secondary implementation details.
- info: `desk/drawer/features/feature-canonical-ast-design-current-state.md` — Stable addressability, field-aware structure, provenance, and hashing are all required dimensions of the target model.
- info: `subagent/atom-ontology-map.md` — The hard-durable core clusters around canonical substrate, identity/addressability, field semantics, relations, anchoring, lineage, computation, and operating surfaces.
- info: `desk/drawer/features/feature-canonical-ast-design-current-state.md` — Embeddings and richer semantic-indexing areas are intentionally more optional or placeholder-like than the atoms listed above, so they are excluded from the create-now set.

## Residual risks
- Some atom titles are normalized synthesis names rather than exact schema identifiers from the source docs.
- The boundary between structural nodes, field bindings, and sidecar records is still conceptually clear but not yet schema-finalized.
- Exact selector grammar and exact event-log/storage mechanics remain intentionally unresolved, so downstream implementations should not overfit this draft into a concrete schema.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "The artifact provides a concrete create-now set of 21 durable atoms, each with title, concise answer, domain, and supporting points, and includes file-path-based review findings with severity."
    }
  ],
  "changedFiles": [
    "/home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/subagent/atom-draft-set.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [],
  "validationOutput": [
    "Reviewed the 4 specified source documents and synthesized only durable, non-placeholder atoms.",
    "Verified that every listed atom includes title, concise answer, domain, and 2-4 supporting points.",
    "Verified that placeholder areas and open questions were excluded from the create-now set."
  ],
  "residualRisks": [
    "Some atom names are normalized synthesis labels rather than finalized schema identifiers.",
    "The exact split between node data, capability attachments, and sidecar records remains open at schema level."
  ],
  "noStagedFiles": true,
  "diffSummary": "Wrote the requested durable atom draft set artifact only.",
  "reviewFindings": [
    "info: desk/drawer/features/feature-sldb-explicit-target-architecture.md - Canonical AST is the architectural center; Markdown and store are not the sovereign model.",
    "info: desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md - Canonical existence is separate from Markdown materialization, and CLI continuity remains a product constraint.",
    "info: desk/drawer/features/feature-canonical-ast-design-current-state.md - Field-aware structure, addressability, provenance, and hashing are durable parts of the target model.",
    "info: subagent/atom-ontology-map.md - The stable create-now cluster is the hard-durable substrate and operating-surface core rather than placeholder semantic extensions."
  ],
  "manualNotes": "No repo source files were edited; only the required output artifact was written."
}
```