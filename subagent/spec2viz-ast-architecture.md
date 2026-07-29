# SLDB Refactor Target Architecture — spec2viz-oriented compact spec

## System purpose

SLDB should be rebuilt around a canonical, typed AST that becomes the shared substrate for:

- structured document workflows
- typed field/model validation
- extraction and rendering
- links, transclusions, and anchors
- provenance and temporal tracking
- graph and semantic projections
- current CLI workflows and future visual UX

Markdown remains important, but only as an importer/exporter and human editing surface. Materialized Markdown is a projection, not the definition of document existence.

## Canonical layers

### 1. Canonical AST core
The architectural center. Owns canonical nodes plus the minimum stable tree spine and capability attachment points.

Core responsibilities:
- document and node identity
- parent/child ownership and order
- node kinds/subtypes
- slots/named regions
- typed or textual payloads
- source/origin spans
- stable addressability essentials

### 2. Capability layers on/adjacent to the AST
Optional typed layers that enrich canonical nodes without making every node a giant flat record.

Capability families:
- field/schema bindings
- links/references/transclusions
- addressability selectors and aliases
- external anchoring
- hooks
- provenance/temporal lineage
- semantic tags/classification
- hashing/fingerprints
- embedding references and semantic-index placeholders

### 3. Importers / translators
Convert external inputs into the canonical AST.

Primary target slice:
- Markdown -> AST

Additional inputs anticipated:
- YAML/JSON
- code-oriented inputs
- external anchor references

### 4. Emitters / compilers
Project canonical AST outward.

Primary target slice:
- AST -> Markdown

Additional outputs anticipated:
- code/text/config projections
- graph payloads
- visual/editor projections

### 5. Infrastructure beside the AST
Operational support systems that should not become the conceptual center.

Includes:
- structural hashing / subtree hashes / Merkle behavior
- cache and recomputation
- dependency indexes
- snapshots and append-only event log
- projection registries
- large sidecars such as embedding stores

### 6. Operating surfaces
Different user-facing/runtime surfaces over the same canonical substrate.

Surfaces:
- CLI: recognizable SLDB command family and workflows
- future visual UX: inspect/edit nodes, fields, links, anchors, provenance, templates, graphs

## Node families

### Required core node families
- `Document`
- `Section` / structural container nodes
- `Block` / content-bearing structural nodes
- `FieldBinding`-addressable content nodes or bindings to them
- `Anchorable` nodes with stable selectors

### Optional capability-bearing node/record families
- `LinkRef`
- `TransclusionRef`
- `ExternalAnchor`
- `HookBinding`
- `ProvenanceRecord`
- `SemanticTag` / semantic claim placeholders
- `EmbeddingRef`
- `ProjectionArtifactRef`

## Edge families

### Tree-spine edges
Canonical ownership/order edges:
- `contains`
- `ordered_child`
- `slot_member`

### Field/schema edges
Typed model relationships:
- `binds_field`
- `conforms_to_schema`
- `composes_into_field_path`

### Reference edges
Non-tree semantic/document relations:
- `links_to`
- `transcludes`
- `references_internal`
- `references_external`

### Addressability/anchor edges
Stable targeting relations:
- `anchored_by`
- `resolves_selector_to`
- `aliases`

### Runtime/behavior edges
Execution and dependency relations:
- `hook_attached_to`
- `depends_on`
- `derived_from`
- `materializes_as`

### Provenance/time edges
Lineage and history:
- `imported_from`
- `transformed_from`
- `supersedes`
- `valid_during`

### Semantic/graph edges
Optional higher-order relations:
- `classified_as`
- `semantically_related_to`
- `embedded_as`

## Key invariants

1. **Canonical truth invariant**
   The AST is the source of truth; Markdown, store artifacts, search indexes, and visual models are projections or infrastructure.

2. **CLI continuity invariant**
   The refactor changes internals deeply but should preserve the recognizable SLDB CLI/product promise.

3. **Optional materialization invariant**
   A document may exist canonically without an emitted Markdown file.

4. **Typed-field preservation invariant**
   Pydantic-style field/model semantics survive as explicit field/schema bindings inside the new architecture.

5. **Tree-plus-graph invariant**
   The canonical structure keeps a tree spine for ownership/order, while links, anchors, provenance, dependencies, and semantic relations are modeled as typed side edges.

6. **Store demotion invariant**
   Store behavior becomes infrastructure over the AST, not the conceptual center of the system.

7. **Projection separation invariant**
   ProseMirror and tree-sitter are downstream adapters/projections, never the sovereign model.

8. **Extensibility invariant**
   The core schema should stay small and stable, with optional capability layers and sidecars for heavy derived data.

9. **Addressability invariant**
   Meaningful units must support stable IDs/selectors/anchors sufficient for linking, graph projection, and future visual inspection.

10. **Migration-slice invariant**
    The first safe vertical slice is likely `Markdown -> AST -> Markdown`, with hashing early and embeddings later/optional.

## Diagram spec

```yaml
spec2viz:
  title: SLDB Canonical AST Target Architecture
  diagram: layered-directed-graph
  nodes:
    - id: inputs
      label: External Inputs
      family: ingress
      notes: Markdown, YAML/JSON, code-like sources, external anchor refs
    - id: importers
      label: Importers / Translators
      family: transform
    - id: ast
      label: Canonical AST Core
      family: core
    - id: structural
      label: Structural Layer
      family: capability
    - id: field_schema
      label: Field / Schema Layer
      family: capability
    - id: references
      label: Link / Reference Layer
      family: capability
    - id: addressability
      label: Addressability Layer
      family: capability
    - id: anchoring
      label: External Anchoring Layer
      family: capability
    - id: hooks
      label: Hook Layer
      family: capability
    - id: provenance
      label: Provenance / Temporal Layer
      family: capability
    - id: semantics
      label: Semantic Layer
      family: capability
    - id: hashing
      label: Hashing / Merkle / Cache
      family: infrastructure
    - id: sidecars
      label: Sidecars / Indexes / Snapshots
      family: infrastructure
    - id: emitters
      label: Emitters / Compilers
      family: transform
    - id: projections
      label: Derived Projections
      family: projection
    - id: cli
      label: CLI Surface
      family: surface
    - id: visual
      label: Future Visual UX
      family: surface
  edges:
    - from: inputs
      to: importers
      kind: ingest
    - from: importers
      to: ast
      kind: translate_to_canonical
    - from: ast
      to: structural
      kind: owns_layer
    - from: ast
      to: field_schema
      kind: owns_layer
    - from: ast
      to: references
      kind: owns_layer
    - from: ast
      to: addressability
      kind: owns_layer
    - from: ast
      to: anchoring
      kind: owns_layer
    - from: ast
      to: hooks
      kind: owns_layer
    - from: ast
      to: provenance
      kind: owns_layer
    - from: ast
      to: semantics
      kind: owns_layer
    - from: ast
      to: hashing
      kind: supports_infrastructure
    - from: hashing
      to: sidecars
      kind: persists_to
    - from: ast
      to: emitters
      kind: compiles_to
    - from: ast
      to: projections
      kind: derives
    - from: ast
      to: cli
      kind: shared_substrate_for
    - from: ast
      to: visual
      kind: shared_substrate_for
    - from: emitters
      to: projections
      kind: materializes_subset
  style_hints:
    layers_top_to_bottom:
      - ingress
      - transform
      - core
      - capability
      - infrastructure
      - projection
      - surface
    emphasize:
      - ast
      - cli
      - visual
    dashed_edges:
      - kind: materializes_subset
      - kind: supports_infrastructure
```

## Concrete findings

- info: `desk/drawer/features/feature-sldb-explicit-target-architecture.md` — establishes AST-centered architecture, demotes Markdown from sovereign representation, and defines layer split among AST core, importers, emitters, infrastructure, and derived projections.
- info: `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md` — requires CLI continuity, optional materialization, and a future visual UX over the same canonical substrate.
- info: `desk/drawer/features/feature-canonical-ast-design-current-state.md` — defines the current target AST dimensions, tree-plus-graph model, optional capability layers, sidecars, and the likely first slice `Markdown -> AST -> Markdown`.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Concrete findings include source-backed statements with file paths in the 'Concrete findings' section, derived from the three requested feature docs."
    }
  ],
  "changedFiles": [
    "/home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/subagent/spec2viz-ast-architecture.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [],
  "validationOutput": [
    "Reviewed the three requested architecture/product docs and synthesized a compact spec2viz-oriented architecture spec with system purpose, canonical layers, node/edge families, invariants, and a renderable diagram section."
  ],
  "residualRisks": [
    "The source documents describe target architecture conceptually, not a finalized schema; node families and edge families here are normalized design abstractions rather than a ratified implementation contract.",
    "The diagram spec is intentionally renderer-agnostic YAML for later spec2viz conversion; exact field names may need adjustment to the eventual renderer contract."
  ],
  "noStagedFiles": true,
  "diffSummary": "Added the requested output artifact only, containing a compact SLDB target architecture spec and acceptance report.",
  "reviewFindings": [
    "no blockers: requested docs consistently support an AST-centered architecture with CLI continuity and optional Markdown materialization."
  ],
  "manualNotes": "No repo source files were edited; only the requested output artifact was written."
}
```