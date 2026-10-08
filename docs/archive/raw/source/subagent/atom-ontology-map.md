# Durable deskops atoms ontology map

## Scope

This map synthesizes durable architectural truths from:

- `desk/drawer/features/feature-sldb-explicit-target-architecture.md`
- `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md`
- `desk/drawer/features/feature-canonical-ast-design-current-state.md`
- `subagent/spec2viz-ast-architecture.md`

It focuses on **durable deskops atoms**: the smallest architecture-level concepts that appear stable across the source documents and are suitable as long-lived ontology anchors.

---

## 1. Executive map

### Stable center of gravity

The durable center is **not Markdown, not store artifacts, and not any editor model**. The stable center is a **canonical, typed, field-aware AST with a tree spine plus graph-capable side relations**.

### Most durable atom families

1. **Identity atoms** — document/node/field identity and stable addressing
2. **Structure atoms** — tree ownership, order, slots, content-bearing nodes
3. **Schema atoms** — field bindings, type contracts, validation/render/extract semantics
4. **Relation atoms** — links, transclusions, internal/external references
5. **Anchor atoms** — local selectors and external anchoring evidence
6. **Lineage atoms** — provenance, authored-vs-derived state, temporal validity
7. **Computation atoms** — hashes, dependency edges, cache/recompute signals
8. **Projection atoms** — materialization/export/view relationships
9. **Surface atoms** — CLI and future visual UX as shared consumers of the same substrate
10. **Semantic extension atoms** — semantic tags now; embeddings and richer semantic indexing later

### Main ontology split

- **Durable truths**: repeated across docs as architectural invariants
- **Structured placeholders**: explicitly anticipated but not fully specified
- **Non-canonical derived artifacts**: important, but not ontology center

---

## 2. Durable truths by domain

## Domain A — Canonical substrate

### Durable truths

- **AST is the source of truth**.
  - Evidence:
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:21-27`
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:44-50`
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:27-35`
    - `subagent/spec2viz-ast-architecture.md:153-155`
- **Markdown is important but not sovereign**.
  - Evidence:
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:23-24`
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:54-64`
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:31-33`
    - `subagent/spec2viz-ast-architecture.md:15`
- **Store behavior is demoted from domain center to infrastructure over AST**.
  - Evidence:
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:78-82`
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:223-247`
    - `subagent/spec2viz-ast-architecture.md:168-170`
- **CLI and visual UX must share the same canonical substrate**.
  - Evidence:
    - `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md:98-127`
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:42-52`
    - `subagent/spec2viz-ast-architecture.md:78-83`

### Durable atoms

- `CanonicalSubstrate`
- `CanonicalAST`
- `DocumentExistence`
- `Projection`
- `InfrastructureService`
- `OperatingSurface`

### Placeholders / not yet fixed

- Exact Clojure schema
- Final serialization format
- Final storage engine
- Exact AST v1 minimum schema

Evidence:
- `desk/drawer/features/feature-canonical-ast-design-current-state.md:15-17`
- `desk/drawer/features/feature-canonical-ast-design-current-state.md:318-336`
- `desk/drawer/features/feature-canonical-ast-design-current-state.md:292-317`

---

## Domain B — Identity, addressability, and existence

### Durable truths

- **Meaningful units must be stably addressable**.
  - Evidence:
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:92-102`
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:180-186`
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:118-131`
    - `subagent/spec2viz-ast-architecture.md:177-179`
- **Document existence is canonical before materialization**.
  - Evidence:
    - `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md:44-57`
    - `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md:104-112`
    - `subagent/spec2viz-ast-architecture.md:159-160`

### Durable atoms

- `DocumentId`
- `NodeId`
- `FieldId`
- `StructuralPath`
- `AnchorId`
- `FragmentId`
- `Alias`
- `StableSelector`
- `CanonicalAddress`
- `DerivedAddress`
- `CanonicalExistence`
- `MaterializationState`

### Placeholders / not yet fixed

- Exact selector grammar
- Exact canonical-vs-derived addressing contract
- Granularity of finest addressable unit in v1

---

## Domain C — Structural document model

### Durable truths

- **The canonical center has a tree spine** with parent/child ownership and order.
  - Evidence:
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:145-156`
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:57-76`
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:346-356`
    - `subagent/spec2viz-ast-architecture.md:165-166`
- **The tree is not enough**; graph side relations are also required.
  - Evidence:
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:340-356`
    - `subagent/spec2viz-ast-architecture.md:165-166`

### Durable atoms

- `Document`
- `Node`
- `NodeKind`
- `NodeSubtype`
- `ParentChildRelation`
- `SiblingOrder`
- `Slot`
- `Region`
- `TextPayload`
- `TypedPayload`
- `Attribute`
- `SourceSpan`
- `OriginSpan`

### Placeholders / not yet fixed

- Exact core node family inventory
- Whether `Section`, `Block`, and field-carrying content are nodes, bindings, or both in final schema

Evidence:
- `desk/drawer/features/feature-sldb-explicit-target-architecture.md:272-277`
- `subagent/spec2viz-ast-architecture.md:87-102`

---

## Domain D — Field/schema/model semantics

### Durable truths

- **Typed models survive**; they are generalized into field-aware AST bindings.
  - Evidence:
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:108-118`
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:120-141`
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:78-100`
    - `subagent/spec2viz-ast-architecture.md:162-163`
- **Field semantics are a first-class ontology domain, not hidden parser behavior**.
  - Evidence:
    - `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md:114-127`
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:80-100`

### Durable atoms

- `FieldBinding`
- `FieldPath`
- `FieldName`
- `SchemaBinding`
- `TypeContract`
- `Cardinality`
- `Requiredness`
- `DefaultValue`
- `ValidationConstraint`
- `ExtractionStrategy`
- `RenderStrategy`
- `ReversibilityMarker`
- `AuthoredState`
- `DerivedState`
- `CompositionMetadata`

### Placeholders / not yet fixed

- Exact mapping from current Pydantic concepts to AST capability layer
- Exact v1 preservation set for current field semantics

Evidence:
- `desk/drawer/features/feature-sldb-explicit-target-architecture.md:272-277`
- `desk/drawer/features/feature-canonical-ast-design-current-state.md:100`

---

## Domain E — Links, references, and composition

### Durable truths

- **Links stop being string hacks and become typed canonical relations**.
  - Evidence:
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:84-86`
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:102-117`
- **Transclusions and typed references are part of the canonical model**.
  - Evidence:
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:27-38`
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:106-116`

### Durable atoms

- `LinkRef`
- `TransclusionRef`
- `TypedReference`
- `InternalTarget`
- `ExternalTarget`
- `ResolutionState`
- `LinkProvenance`
- `LinkPredicate`
- `BacklinkProjection`

### Placeholders / not yet fixed

- Exact relation vocabulary/predicate taxonomy
- Exact transclusion execution/materialization semantics

---

## Domain F — Anchoring and selectors

### Durable truths

- **Local addressability and external anchoring are separate but related concerns**.
  - Evidence:
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:180-206`
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:118-148`
- **External anchors need evidence, not just pointers**.
  - Evidence:
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:194-202`
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:137-146`

### Durable atoms

- `LocalAnchor`
- `StableSelector`
- `ExternalAnchor`
- `ExternalResourceRef`
- `Locator`
- `TextSample`
- `ContextBefore`
- `ContextAfter`
- `ContentFingerprint`
- `ReattachmentPolicy`
- `AnchorEvidence`
- `AnchorConfidence`

### Placeholders / not yet fixed

- Reattachment policy taxonomy
- Confidence/evidence scoring contract
- Whether anchor evidence is inline or sidecar in v1

---

## Domain G — Hooks and executable behavior

### Durable truths

- **Hooks are described declaratively in the model, even if execution is external**.
  - Evidence:
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:208-221`
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:150-163`

### Durable atoms

- `HookBinding`
- `HookId`
- `HookKind`
- `ExecutableTarget`
- `HookInputContract`
- `HookOutputContract`
- `ExecutionPolicy`
- `VerificationContract`
- `HookAttachment`

### Placeholders / not yet fixed

- Execution runtime ownership
- Hook security model
- Failure and retry semantics

---

## Domain H — Provenance, history, and temporal validity

### Durable truths

- **Git history alone is insufficient**; the model needs system-level lineage.
  - Evidence:
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:223-237`
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:165-182`
- **Authored-vs-derived state is a stable ontology distinction**.
  - Evidence:
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:134-137`
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:171-180`

### Durable atoms

- `ProvenanceRecord`
- `ImportSource`
- `TransformationLineage`
- `CreatedAt`
- `UpdatedAt`
- `ValidFrom`
- `ValidTo`
- `RevisionId`
- `EventLineage`
- `EvidenceMetadata`
- `Supersession`

### Placeholders / not yet fixed

- Final append-only event model
- Snapshot/version storage contract
- Temporal query model

Evidence:
- `desk/drawer/features/feature-canonical-ast-design-current-state.md:298-306`

---

## Domain I — Hashing, dependency, cache, and recomputation

### Durable truths

- **Structural hashing is core infrastructure from early on**.
  - Evidence:
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:78-82`
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:238-247`
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:197-216`
- **Hashes support Merkle behavior, invalidation, and incremental compilation**.
  - Evidence:
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:210-216`
    - `subagent/spec2viz-ast-architecture.md:180-181`

### Durable atoms

- `NodeHash`
- `SubtreeHash`
- `DependencyHash`
- `MaterializationHash`
- `StructuralFingerprint`
- `DirtyState`
- `CacheKey`
- `DependencyEdge`
- `ProjectionRegistry`

### Placeholders / not yet fixed

- Exact hash recipe
- Boundary between AST-adjacent metadata and external index persistence
- Cache invalidation semantics for capability sidecars

---

## Domain J — Projections, materialization, and operating surfaces

### Durable truths

- **Markdown is a projection, not existence**.
  - Evidence:
    - `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md:44-57`
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:308-316`
- **CLI continuity is a stable product constraint**.
  - Evidence:
    - `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md:23-42`
    - `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md:129-162`
    - `subagent/spec2viz-ast-architecture.md:156-157`
- **Future visual UX is a first-class downstream consumer**.
  - Evidence:
    - `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md:59-81`
    - `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md:108-127`
    - `subagent/spec2viz-ast-architecture.md:82-83`

### Durable atoms

- `Importer`
- `Emitter`
- `MarkdownMaterialization`
- `CodeProjection`
- `GraphProjection`
- `SearchProjection`
- `VisualEditorProjection`
- `CLIWorkflowSurface`
- `VisualUXSurface`
- `ProjectionArtifactRef`

### Placeholders / not yet fixed

- Exact command continuity matrix
- Exact visual editor projection contract
- Boundary of user-visible store workflows after demotion

---

## Domain K — Semantic layer, embeddings, and future semantic indexing

### Durable truths

- **A lightweight semantic layer belongs in/adjacent to the AST now**.
  - Evidence:
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:184-196`
- **Embeddings are optional semantic materializations, never canonical structure**.
  - Evidence:
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:218-261`
- **Richer semantic indexing is intentionally placeholder territory**.
  - Evidence:
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:263-290`

### Durable atoms

- `SemanticTag`
- `SemanticRole`
- `ConceptBinding`
- `SemanticReference`
- `SemanticQueryHint`
- `EmbeddingRef`
- `EmbeddingScope`
- `EmbeddingRecipeVersion`
- `EmbeddingFreshness`

### Placeholders / not yet fixed

- `SemanticClaimSlot`
- `PropositionExtractionHook`
- `WorldBinding`
- `RelationTypingPlaceholder`
- `SemanticNeighborhoodProjection`
- `MatrixProjectionMetadata`
- `SemanticIndexRecipe`

### Important distinction

This domain contains the clearest explicit split between durable truths and placeholders:

- **Durable now**: semantic tags/classification, embedding-as-optional-side-material, semantic extensibility
- **Placeholder**: proposition/fact engine, logic-oriented semantic runtime, matrix-style indexing

---

## Domain L — Editor/runtime adapters and non-sovereign representations

### Durable truths

- **ProseMirror is downstream, not canonical**.
  - Evidence:
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:249-260`
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:372-379`
- **tree-sitter is downstream, not canonical**.
  - Evidence:
    - `desk/drawer/features/feature-sldb-explicit-target-architecture.md:266-274`
    - `desk/drawer/features/feature-canonical-ast-design-current-state.md:380-384`

### Durable atoms

- `EditingProjection`
- `SyntaxProjection`
- `CodeImporterHelper`
- `VisualTransformModel`

### Placeholders / not yet fixed

- Exact adapter boundaries and data loss guarantees
- Whether certain editor/runtime structures round-trip fully or partially

---

## 3. Dependencies between atom domains

## Primary dependency graph

```text
CanonicalAST
├── requires Identity/Addressability atoms
├── owns Structural atoms
├── is enriched by Schema atoms
├── is enriched by Relation atoms
├── is enriched by Anchor atoms
├── is enriched by Lineage atoms
├── is indexed by Computation atoms
├── is consumed by Projection atoms
└── is shared by Surface atoms
```

## Dependency detail

### Foundational layer

1. **Identity/Addressability**
   - Needed by almost every other domain.
   - Without stable IDs/selectors, links, provenance, projections, hooks, and hashes cannot refer durably.

2. **Structural model**
   - Needed by field binding, addressability, hashing, projection, and editor views.
   - Provides ownership/order spine.

### Semantic-operational layer

3. **Field/schema semantics** depend on:
   - `Node`
   - `NodeId`
   - `FieldPath`
   - `SchemaBinding`

4. **Links/references** depend on:
   - `NodeId`
   - `StableSelector` or `TargetAddress`
   - provenance when link state is derived/resolved

5. **Anchoring** depends on:
   - local addressability for in-document targets
   - relation atoms for attachment semantics
   - provenance/evidence atoms for reattachment confidence

6. **Hooks** depend on:
   - field/structure atoms for input binding
   - provenance for result lineage
   - dependency/cache atoms for recomputation control

7. **Provenance/temporal** depends on:
   - durable identities of nodes/fields/relations
   - transformation and import boundaries

8. **Hashing/cache** depends on:
   - structural atoms
   - capability layers included in the fingerprint boundary
   - projection definitions for cache keys

### Consumption layer

9. **Projections/materializations** depend on:
   - canonical AST
   - relevant capability layers
   - hash/dependency atoms for incremental emission

10. **CLI and visual surfaces** depend on:
   - same canonical AST
   - projection layer for user-facing representations
   - explicit inspectable atoms for fields/models/templates/links/anchors/provenance

11. **Semantic extension atoms** depend on:
   - canonical structure and addressability
   - provenance for freshness/versioning
   - optional sidecars for heavy vector/index data

---

## 4. Durable truths vs placeholders matrix

| Area | Durable truth | Placeholder / future fill | Evidence |
|---|---|---|---|
| Canonical center | AST is canonical truth | exact AST v1 schema | `feature-sldb-explicit-target-architecture.md:21-27`, `feature-canonical-ast-design-current-state.md:15-17` |
| Materialization | Markdown is a projection | exact materialization lifecycle | `feature-sldb-product-principles-and-cli-continuity.md:44-57` |
| Field semantics | field-aware typed contracts remain first-class | exact Pydantic-to-AST mapping | `feature-sldb-explicit-target-architecture.md:108-137` |
| Graph model | tree spine + side edges | final edge taxonomy | `feature-canonical-ast-design-current-state.md:346-356` |
| Hashing | structural hashes and Merkle behavior matter early | exact hashing recipe/persistence | `feature-sldb-explicit-target-architecture.md:78-82`, `feature-canonical-ast-design-current-state.md:197-216` |
| Visual UX | first-class downstream surface | exact visual editor implementation | `feature-sldb-product-principles-and-cli-continuity.md:59-81`, `feature-canonical-ast-design-current-state.md:372-379` |
| Semantic layer | semantic tags/classification belong | proposition/matrix semantic engine | `feature-canonical-ast-design-current-state.md:184-196`, `263-290` |
| Text model | text payload is canonical enough for now | text-as-graph internalization | `feature-canonical-ast-design-current-state.md:358-360` |
| Adapters | ProseMirror and tree-sitter are projections | exact round-trip contract | `feature-sldb-explicit-target-architecture.md:249-274` |

---

## 5. Proposed durable deskops atom taxonomy

This taxonomy is proposed as a stable naming layer for durable ontology work. It is intentionally compact and favors long-lived conceptual atoms over implementation detail.

## A. Core entity atoms

- `Document`
- `Node`
- `Field`
- `Schema`
- `Relation`
- `Anchor`
- `Hook`
- `ProvenanceRecord`
- `ProjectionArtifact`
- `OperatingSurface`

## B. Identity atoms

- `DocumentId`
- `NodeId`
- `FieldId`
- `SchemaId`
- `RelationId`
- `AnchorId`
- `HookId`
- `RevisionId`

## C. Structural atoms

- `NodeKind`
- `NodeSubtype`
- `ParentChildEdge`
- `SiblingOrder`
- `Slot`
- `Region`
- `TextPayload`
- `TypedPayload`
- `AttributeBag`
- `SourceSpan`
- `OriginSpan`

## D. Addressability atoms

- `StructuralPath`
- `FragmentId`
- `Alias`
- `StableSelector`
- `CanonicalAddress`
- `DerivedAddress`

## E. Schema/field atoms

- `FieldBinding`
- `FieldPath`
- `TypeContract`
- `Cardinality`
- `Requiredness`
- `DefaultValue`
- `ValidationConstraint`
- `ExtractionStrategy`
- `RenderStrategy`
- `Reversibility`
- `CompositionRule`
- `AuthorshipState`

## F. Reference atoms

- `LinkRef`
- `TransclusionRef`
- `TypedReference`
- `InternalTarget`
- `ExternalTarget`
- `ResolutionState`
- `LinkPredicate`

## G. Anchor atoms

- `LocalAnchor`
- `ExternalAnchor`
- `ExternalResourceRef`
- `Locator`
- `TextSample`
- `ContextWindow`
- `ContentFingerprint`
- `ReattachmentPolicy`
- `AnchorEvidence`
- `AnchorConfidence`

## H. Behavior atoms

- `HookBinding`
- `ExecutableTarget`
- `InputContract`
- `OutputContract`
- `ExecutionPolicy`
- `VerificationContract`
- `DependencyEdge`

## I. Lineage atoms

- `ImportSource`
- `TransformationLineage`
- `CreatedAt`
- `UpdatedAt`
- `ValidInterval`
- `EventLineage`
- `Supersession`
- `EvidenceMetadata`

## J. Computation atoms

- `NodeHash`
- `SubtreeHash`
- `DependencyHash`
- `MaterializationHash`
- `StructuralFingerprint`
- `DirtyState`
- `CacheKey`
- `ProjectionRegistry`

## K. Projection/surface atoms

- `Importer`
- `Emitter`
- `MarkdownMaterialization`
- `CodeProjection`
- `GraphProjection`
- `SearchProjection`
- `VisualEditorProjection`
- `CLIWorkflowSurface`
- `VisualUXSurface`

## L. Semantic extension atoms

### Durable now

- `SemanticTag`
- `SemanticRole`
- `ConceptBinding`
- `SemanticReference`
- `SemanticQueryHint`
- `EmbeddingRef`
- `EmbeddingScope`
- `EmbeddingMetadata`

### Placeholder later

- `SemanticClaimSlot`
- `PropositionExtractionHook`
- `WorldBinding`
- `SemanticNeighborhoodProjection`
- `MatrixProjectionMetadata`
- `SemanticIndexRecipe`

---

## 6. Proposed atom classes by durability level

## Level 1 — Hard durable atoms

These are repeated across docs and should be safe ontology anchors now.

- `CanonicalAST`
- `Document`
- `Node`
- `DocumentId`
- `NodeId`
- `FieldBinding`
- `FieldPath`
- `SchemaBinding`
- `TypeContract`
- `StableSelector`
- `LinkRef`
- `TransclusionRef`
- `ExternalAnchor`
- `HookBinding`
- `ProvenanceRecord`
- `NodeHash`
- `Projection`
- `CLIWorkflowSurface`
- `VisualUXSurface`

## Level 2 — Stable but implementation-flexible atoms

These are clearly required, but their exact encoding can vary.

- `NodeKind`
- `NodeSubtype`
- `Slot`
- `TypedPayload`
- `ValidationConstraint`
- `ExtractionStrategy`
- `RenderStrategy`
- `Reversibility`
- `ResolutionState`
- `AnchorEvidence`
- `ExecutionPolicy`
- `TransformationLineage`
- `DependencyEdge`
- `MaterializationHash`
- `SemanticTag`
- `EmbeddingRef`

## Level 3 — Explicit placeholder atoms

These should exist in the ontology only as forward-compatible placeholders.

- `SemanticClaimSlot`
- `PropositionExtractionHook`
- `WorldBinding`
- `RelationTypingPlaceholder`
- `SemanticNeighborhoodProjection`
- `MatrixProjectionMetadata`
- `SemanticIndexRecipe`
- `TextAsGraphUnit`

---

## 7. Recommended ontology boundaries

## Put inside the durable atom ontology now

- canonical entity types
- IDs and addressability concepts
- tree-spine structural concepts
- field/schema contract concepts
- typed relation concepts
- anchoring evidence concepts
- provenance/temporal concepts
- hash/dependency concepts
- projection/surface concepts
- semantic-tag and embedding-reference concepts

## Keep outside or explicitly secondary

- exact CLI command names
- exact storage backend mechanics
- full event-log persistence layout
- renderer-specific projection schemas
- editor-specific document internals
- dense vector payload storage

Rationale: the docs repeatedly define these as projections, infrastructure, or unresolved implementation choices rather than durable ontology center.

---

## 8. Key risks if the ontology is modeled incorrectly

### high — Confusing projection atoms with canonical atoms

If Markdown, ProseMirror, tree-sitter, or store artifacts are modeled as primary truth-bearing atoms, the ontology will contradict the architecture.

Evidence:
- `desk/drawer/features/feature-sldb-explicit-target-architecture.md:21-24`
- `desk/drawer/features/feature-sldb-explicit-target-architecture.md:249-274`
- `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md:46-57`

### high — Under-modeling field semantics

If the ontology only models a document tree and omits field binding/type/render/extract semantics, it loses a core SLDB invariant.

Evidence:
- `desk/drawer/features/feature-sldb-explicit-target-architecture.md:120-141`
- `desk/drawer/features/feature-canonical-ast-design-current-state.md:78-100`

### high — Ignoring stable addressability

Without durable IDs/selectors/anchors, links, provenance, graph projection, and visual inspection all become fragile.

Evidence:
- `desk/drawer/features/feature-sldb-explicit-target-architecture.md:92-102`
- `desk/drawer/features/feature-canonical-ast-design-current-state.md:118-131`

### medium — Treating placeholders as finalized semantics

The semantic-indexing and text-as-graph areas are intentionally open; overcommitting the ontology there will calcify unresolved design space.

Evidence:
- `desk/drawer/features/feature-canonical-ast-design-current-state.md:263-290`
- `desk/drawer/features/feature-canonical-ast-design-current-state.md:358-360`

### medium — Omitting operating-surface atoms

CLI continuity and future visual UX are stable product constraints and should appear as ontology consumers, even though they are not canonical truth.

Evidence:
- `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md:23-42`
- `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md:59-81`

---

## 9. Review findings

- info: `desk/drawer/features/feature-sldb-explicit-target-architecture.md:21-27` — strongest statement of the ontology center: canonical typed AST replaces Markdown as sovereign representation.
- info: `desk/drawer/features/feature-sldb-explicit-target-architecture.md:120-141` — field-aware semantics are durable truths, not incidental metadata.
- info: `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md:44-57` — document existence must be separable from Markdown materialization.
- info: `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md:98-127` — CLI and future visual UX are sibling surfaces over the same substrate.
- info: `desk/drawer/features/feature-canonical-ast-design-current-state.md:197-216` — hashing/Merkle/invalidation is early-core infrastructure, not an afterthought.
- info: `desk/drawer/features/feature-canonical-ast-design-current-state.md:263-290` — semantic indexing beyond tags/embeddings is explicitly placeholder territory and should be labeled as such.
- info: `subagent/spec2viz-ast-architecture.md:85-149` — useful compact normalization of node and edge families, but still a synthesis artifact rather than a ratified schema.

---

## 10. Residual risks

- The source set is architectural and conceptual, not a finalized schema contract; some proposed atom names are normalization choices rather than source-native identifiers.
- The exact split between “node,” “binding,” and “sidecar record” remains unresolved for several capability areas.
- Semantic-index placeholder space is intentionally open and should not be mistaken for implementation commitment.
- The docs do not yet define a canonical selector grammar, event model, or complete edge vocabulary.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "The artifact contains concrete findings with file paths and severity in 'Review findings' and 'Key risks', and distinguishes durable truths from placeholders with source-backed evidence."
    }
  ],
  "changedFiles": [
    "/home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/subagent/atom-ontology-map.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "nl -ba desk/drawer/features/feature-sldb-explicit-target-architecture.md | sed -n '1,260p'",
      "result": "passed",
      "summary": "Read source with line numbers for evidence extraction."
    },
    {
      "command": "nl -ba desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md | sed -n '1,220p'",
      "result": "passed",
      "summary": "Read source with line numbers for evidence extraction."
    },
    {
      "command": "nl -ba desk/drawer/features/feature-canonical-ast-design-current-state.md | sed -n '1,360p'",
      "result": "passed",
      "summary": "Read source with line numbers for evidence extraction."
    },
    {
      "command": "nl -ba subagent/spec2viz-ast-architecture.md | sed -n '1,260p'",
      "result": "passed",
      "summary": "Read compact spec synthesis for normalization of node and edge families."
    }
  ],
  "validationOutput": [
    "Verified that all required source documents were reviewed and cited with file paths and line ranges.",
    "Verified that the ontology map groups stable truths by domain, shows dependencies, distinguishes durable truths from placeholders, and proposes an atom taxonomy."
  ],
  "residualRisks": [
    "The docs are conceptual and open-ended in places, so some taxonomy labels are synthesis choices rather than ratified schema names.",
    "Placeholder semantic-index and text-as-graph areas remain intentionally unspecified by the source docs."
  ],
  "noStagedFiles": true,
  "diffSummary": "Wrote the requested ontology map artifact only.",
  "reviewFindings": [
    "info: desk/drawer/features/feature-sldb-explicit-target-architecture.md:21-27 - Canonical typed AST is the architectural center; Markdown is not sovereign.",
    "info: desk/drawer/features/feature-sldb-explicit-target-architecture.md:120-141 - Field-aware typed semantics are a durable requirement of the AST.",
    "info: desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md:44-57 - Canonical document existence must be separable from Markdown materialization.",
    "info: desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md:98-127 - CLI and future visual UX must share the same canonical substrate.",
    "info: desk/drawer/features/feature-canonical-ast-design-current-state.md:263-290 - Rich semantic indexing is placeholder territory and should be modeled as such, not as finalized ontology."
  ],
  "manualNotes": "No repo source files were edited; only the requested output artifact was written."
}
```