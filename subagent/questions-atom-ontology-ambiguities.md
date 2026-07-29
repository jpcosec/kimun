## Review
- Note [medium]: `subagent/atom-ontology-map.md:831-840` says exact CLI command names should stay outside or explicitly secondary, but `desk/atoms/ast-command-group.md:16-22`, `desk/atoms/docs-command-group.md:16-22`, `desk/atoms/fields-command-group.md:16-22`, and `desk/atoms/stores-command-group.md:16-22` all promote concrete command groups to first-class atoms. Should these collapse under `desk/atoms/cli-workflow-surface.md:16-22` instead of living as ontology peers?
- Note [high]: Where is the boundary between canonical identity/existence and tracked/store identity? `desk/atoms/canonical-identity.md:16-22` and `desk/atoms/canonical-existence.md:16-22` define durable model-level notions, but `desk/atoms/tracked-document-identity.md:16-22` and `desk/atoms/document-tracker.md:16-22` introduce logical store names plus file paths. Is tracked-document identity a durable ontology atom, or an operational handle layered over canonical identity?
- Note [high]: Is `Stable Selector` meant to absorb `LocalAnchor`, `FragmentId`, `Alias`, `CanonicalAddress`, and `DerivedAddress`, or are those missing atoms? The map names them explicitly in `subagent/atom-ontology-map.md:111-124` and `subagent/atom-ontology-map.md:265-278`, while `desk/atoms/` currently has `stable-selector.md`, `addressability-layer.md`, and resolver atoms but no separate `local-anchor`, `fragment-id`, `canonical-address`, or `derived-address` atom files.
- Note [medium]: What is the intended relation between `Stable Selector` and `External Anchor`? `desk/atoms/stable-selector.md:20-22` says anchors, aliases, and fragments belong to the selector domain, but `desk/atoms/external-anchor.md:16-22` frames an anchor as an evidence-backed attachment and even lists `stable-selector` under `Supports` at `desk/atoms/external-anchor.md:30-33`. Is an external anchor a kind of selector, evidence attached to a selector, or a separate relation model?
- Note [high]: Where does the generalized schema layer stop and the legacy `StructuredNLDoc` contract begin? `desk/atoms/field-binding.md:16-22`, `desk/atoms/schema-binding.md:16-22`, and `desk/atoms/type-contract.md:16-22` already generalize field semantics, while `desk/atoms/structurednldoc-contract.md:16-22` still calls the Pydantic/Markdown-template contract the central user-facing model. Should `StructuredNLDoc` remain a durable atom or become a v1 specialization/migration alias of the more general schema atoms?
- Note [medium]: Are `Cardinality`, `Requiredness`, `DefaultValue`, `ValidationConstraint`, `ExtractionStrategy`, `RenderStrategy`, `ReversibilityMarker`, and `CompositionMetadata` standalone atoms or just properties of `Type Contract` / `Field Binding`? The map elevates them to durable atoms in `subagent/atom-ontology-map.md:191-207`, but `desk/atoms/` only provides the higher-level wrapper atoms.
- Note [medium]: Do `Projection`, `Document materializer`, `Emitter / compiler`, and `Markdown emitter` represent four durable concepts, or one abstraction plus specializations? `desk/atoms/projection.md:16-22` already covers all derived views, while `desk/atoms/document-materializer.md:16-22`, `desk/atoms/emitter-compiler.md:16-22`, and `desk/atoms/markdown-emitter.md:16-22` all describe closely overlapping emission/materialization roles.
- Note [medium]: Is `Search projection` the parent derived view for field/section indexes and the query engine, or just one sibling component? `desk/atoms/field-index.md:26-30` and `desk/atoms/section-index.md:26-30` both depend on `search-projection`, while `desk/atoms/query-engine.md:16-22` sits over those indexes and `search-projection` together. The ownership/layering of retrieval atoms is still blurry.
- Note [high]: Where are the durable-now semantic content atoms promised by the map—`SemanticTag`, `SemanticRole`, `ConceptBinding`, `SemanticReference`, and `SemanticQueryHint` in `subagent/atom-ontology-map.md:443-470`? The current atom set only exposes semantic infrastructure/adapters such as `desk/atoms/semantic-indexing.md:16-22`, `desk/atoms/embeddings.md:16-22`, `desk/atoms/matrix-adapter.md:16-22`, and `desk/atoms/semantic-exporter.md:16-22`.
- Note [medium]: Which history atom is foundational: `Provenance Record`, `Append-only event log`, `Temporal layer`, or `Snapshots`? `desk/atoms/append-only-event-log.md:26-30` depends on `snapshots`, `desk/atoms/snapshots.md:31-33` supports the event log, and `desk/atoms/temporal-layer.md:26-33` depends on the event log while supporting snapshots. That lineage stack currently reads as cyclic rather than clearly layered.
- Note [medium]: Are `ResolutionState`, `LinkPredicate`, `InternalTarget`, `ExternalTarget`, and `BacklinkProjection` intentionally omitted as separate atoms, or should `Link Reference` / `Transclusion Reference` be decomposed further? The map treats them as durable relation atoms in `subagent/atom-ontology-map.md:233-243`, but `desk/atoms/` only exposes `desk/atoms/link-reference.md:16-22` and `desk/atoms/transclusion-reference.md:16-20`.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "The review lists concrete ontology questions with file paths, line references, and severity markers for each ambiguity or gap."
    }
  ],
  "changedFiles": [
    "/home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/subagent/questions-atom-ontology-ambiguities.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "python - <<'PY' ... extract headings/sections from desk/atoms/*.md ... PY",
      "result": "passed",
      "summary": "Enumerated atom files and summarized their answer/related-atoms sections for ambiguity scanning."
    },
    {
      "command": "nl -ba subagent/atom-ontology-map.md | sed -n '...' and nl -ba desk/atoms/*.md | sed -n '12,40p'",
      "result": "passed",
      "summary": "Captured exact line references for the ontology map and candidate atom files."
    },
    {
      "command": "find desk/atoms for missing atom names (e.g. local-anchor, canonical-address, semantic-tag, relation sub-atoms)",
      "result": "passed",
      "summary": "Verified several map-promised atom names do not currently have corresponding atom files."
    },
    {
      "command": "git diff --cached --name-only",
      "result": "passed",
      "summary": "Confirmed there were no staged files."
    }
  ],
  "validationOutput": [
    "Reviewed only desk/atoms plus subagent/atom-ontology-map.md, per task scope.",
    "Verified ambiguity findings against file contents and map line ranges rather than inferring from unrelated repo areas."
  ],
  "residualRisks": [
    "This pass identifies ontology questions, not confirmed schema defects; some gaps may be intentional placeholders.",
    "Missing-file findings are based on the current desk/atoms directory only and may reflect work-in-progress atomization."
  ],
  "noStagedFiles": true,
  "diffSummary": "Created the requested review artifact containing concise ontology-boundary questions only.",
  "reviewFindings": [
    "medium: subagent/atom-ontology-map.md:831-840 vs desk/atoms/*-command-group.md - CLI command names may be over-modeled as ontology atoms.",
    "high: desk/atoms/canonical-identity.md:16-22 vs desk/atoms/tracked-document-identity.md:16-22 - canonical identity and tracked/store identity boundary is unclear.",
    "high: subagent/atom-ontology-map.md:111-124 and 265-278 - several addressability/anchor atoms are named in the map but missing as atom files.",
    "high: subagent/atom-ontology-map.md:443-470 - durable semantic content atoms are named in the map but absent from desk/atoms.",
    "medium: desk/atoms/append-only-event-log.md:26-30 and desk/atoms/snapshots.md:31-33 - lineage stack relationships read as cyclic."
  ],
  "manualNotes": "No repo source files were edited; only the requested output artifact was written. The worktree already had extensive unrelated unstaged changes."
}
```