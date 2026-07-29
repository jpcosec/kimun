## Review
- Correct: All three documents consistently establish a canonical AST-centered direction and de-center Markdown as sovereign state (`desk/drawer/features/feature-sldb-explicit-target-architecture.md:21-27`, `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md:98-112`, `desk/drawer/features/feature-canonical-ast-design-current-state.md:27-38`).
- Note [medium]: `desk/drawer/features/feature-sldb-explicit-target-architecture.md:84-86,367-379` vs `desk/drawer/features/feature-canonical-ast-design-current-state.md:102-116` — Are links/references canonical AST data, or only derived projections? The first document calls links/anchoring/graph export “derived projections,” but later says the AST itself owns references/anchoring.
- Note [medium]: `desk/drawer/features/feature-sldb-explicit-target-architecture.md:223-247,325-336` vs `desk/drawer/features/feature-canonical-ast-design-current-state.md:165-216,292-316,407-424` — Which of provenance/history and hashing/cache live inside the AST core versus AST-adjacent infrastructure/sidecars in v1? The boundaries are described both ways.
- Note [medium]: `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md:44-57,166-169` — If materialization is optional, what is the canonical persisted unit exposed to CLI workflows before Markdown exists: document id, AST snapshot, project store entry, or something else?
- Note [high]: `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md:25-42,166-169` — Which current CLI commands are guaranteed surface-stable in the first migration slice, and which may change semantics once documents can exist without Markdown materialization?
- Note [medium]: `desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md:114-127` and `desk/drawer/features/feature-canonical-ast-design-current-state.md:78-100,292-316` — Are models and templates first-class AST nodes, separate schema/template registries, or AST-adjacent infrastructure? The UX requires them to be inspectable, but their canonical representation is unspecified.
- Note [medium]: `desk/drawer/features/feature-sldb-explicit-target-architecture.md:147-156,180-206` and `desk/drawer/features/feature-canonical-ast-design-current-state.md:63-74,118-148` — What is the stability contract among `node_id`, structural path, anchors/selectors, and source spans? Which identifiers must survive Markdown re-imports and edits, and which are allowed to be recomputed?
- Note [medium]: `desk/drawer/features/feature-sldb-explicit-target-architecture.md:66-68,453-455` and `desk/drawer/features/feature-canonical-ast-design-current-state.md:532-535` — For the likely first slice `Markdown -> AST -> Markdown`, what counts as success: byte-for-byte round-trip, semantic equivalence, stable ids, or only renderability?
- Note [medium]: `desk/drawer/features/feature-canonical-ast-design-current-state.md:387-424` vs `desk/drawer/features/feature-canonical-ast-design-current-state.md:53-290` — Which capability layers are mandatory in AST v1 versus explicitly deferred to sidecars? The document lists many “must carry” dimensions, but later recommends a small required core plus optional layers.
- Note [low]: `desk/drawer/features/feature-canonical-ast-design-current-state.md:358-385` — How much of the future text-as-graph direction must be preserved in the initial Rust core API now, versus treated as a later migration that can introduce new node/edge families?
- Note [medium]: `desk/drawer/features/feature-sldb-explicit-target-architecture.md:208-221` and `desk/drawer/features/feature-canonical-ast-design-current-state.md:150-163` — What is the execution boundary for hooks in the first design: purely declarative metadata, orchestrated runtime jobs, or executable plugins? The trust, security, and reproducibility model is not yet specified.
- Note [low]: `desk/drawer/features/feature-canonical-ast-design-current-state.md:218-290` — Are embeddings and semantic-index placeholders in scope for the refactor architecture baseline, or should they be treated as reserved extension points only until the core AST/CLI continuity slice is complete?

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Provided concrete ambiguity findings with file paths, line ranges, and severity labels across the three requested design documents."
    }
  ],
  "changedFiles": [
    "subagent/questions-architecture-ambiguities.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "git diff --cached --name-only",
      "result": "passed",
      "summary": "No staged files reported."
    },
    {
      "command": "nl -ba desk/drawer/features/feature-sldb-explicit-target-architecture.md && printf '\n---FILE2---\n' && nl -ba desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md && printf '\n---FILE3---\n' && nl -ba desk/drawer/features/feature-canonical-ast-design-current-state.md",
      "result": "passed",
      "summary": "Read the three requested files with line numbers for citation."
    }
  ],
  "validationOutput": [
    "Ambiguity pass completed against only the three requested feature documents.",
    "Questions focus on unclear boundaries, missing contracts, and cross-document tension points."
  ],
  "residualRisks": [
    "Several questions concern architectural boundary decisions that may affect CLI continuity, persistence shape, and AST v1 scope.",
    "No code or broader repo context was inspected beyond the three requested documents."
  ],
  "noStagedFiles": true,
  "diffSummary": "Created the requested review output file with concise architecture ambiguity questions.",
  "reviewFindings": [
    "medium: desk/drawer/features/feature-sldb-explicit-target-architecture.md:84-86,367-379 and desk/drawer/features/feature-canonical-ast-design-current-state.md:102-116 - unclear whether links are canonical AST state or derived projections.",
    "high: desk/drawer/features/feature-sldb-product-principles-and-cli-continuity.md:25-42,166-169 - CLI continuity promise is underspecified for the first migration slice.",
    "medium: desk/drawer/features/feature-canonical-ast-design-current-state.md:387-424 - AST v1 mandatory layers versus sidecars remain unclear."
  ],
  "manualNotes": "No repo source files were edited; only the required output file was written."
}
```