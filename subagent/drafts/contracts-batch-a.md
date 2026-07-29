# Implementation Plan

## Goal
Produce one combined draft file at `subagent/drafts/contracts-batch-a.md` that contains concise, clearly separated markdown drafts for the 4 requested contract docs, grounded in the target overview, spec2viz diagrams, and the 4 task files.

## Tasks
1. **Confirm and bound the source set before drafting**
   - File: `subagent/drafts/contracts-batch-a.md`
   - References:
     - `docs/architecture/target-system-overview.md`
     - `docs/architecture/spec2viz/target-components.yml`
     - `docs/architecture/spec2viz/target-runtime.yml`
     - `docs/architecture/spec2viz/target-store-graph.yml`
     - `docs/architecture/spec2viz/target-anchoring.yml`
     - `desk/tasks/task-freeze-phase-1-parity-contract.md`
     - `desk/tasks/task-freeze-python-rust-ownership-and-ffi-contract.md`
     - `desk/tasks/task-specify-rust-canonical-core-contract.md`
     - `desk/tasks/task-specify-markdown-roundtrip-contract.md`
   - Changes: Note in the working draft process that `/home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/context.md` was requested but is not present, so the drafts must be grounded in the available architecture docs, diagrams, task texts, and cited atoms only.
   - Acceptance:
     - The final combined draft stays within the 4 requested contracts only.
     - Any missing-context issue is surfaced explicitly rather than guessed around silently.
     - The draft language reflects only sources actually present in the repository.

2. **Create the combined output scaffold with explicit document separators**
   - File: `subagent/drafts/contracts-batch-a.md`
   - Changes: Structure the file as 4 compact markdown blocks separated by clear headings such as:
     - `# Draft: phase-1-parity-contract.md`
     - `# Draft: python-rust-ownership-and-ffi-contract.md`
     - `# Draft: rust-canonical-core-contract.md`
     - `# Draft: markdown-roundtrip-contract.md`
     Each block should use concise sections plus concrete bullets, not prose-heavy narrative.
   - Acceptance:
     - A reader can copy each block into its future standalone contract file without reformatting.
     - Each contract block is visually distinct and named after the requested doc.
     - No extra contract drafts are added.

3. **Draft the Phase 1 parity contract block first, then the Python/Rust ownership block**
   - File: `subagent/drafts/contracts-batch-a.md`
   - References for parity:
     - `docs/architecture/target-system-overview.md`
     - `docs/architecture/spec2viz/target-components.yml`
     - `docs/architecture/spec2viz/target-runtime.yml`
     - `desk/tasks/task-freeze-phase-1-parity-contract.md`
     - `desk/atoms/decision-v1-parity-before-scope-expansion.md`
     - `desk/atoms/phase-1-v1-replication.md`
     - `desk/atoms/cli-workflow-surface.md`
     - `desk/atoms/direct-mode.md`
     - `desk/atoms/store-backed-mode.md`
   - Changes for parity: Use the section outline already implied by the planning materials:
     - `Purpose and Governing Sources`
     - `Phase 1 Must Preserve`
     - `User-Visible Workflows in Scope`
     - `Explicit Phase 1 Non-Goals`
     - `Parity Acceptance Boundaries`
     - `Downstream Planning Constraints`
     Make the bullets explicitly name direct-mode continuity for `extract`, `render`, and `validate`, plus store-backed continuity for tracked docs, indexes, and integrity checks.
   - References for ownership:
     - `docs/architecture/target-system-overview.md`
     - `docs/architecture/spec2viz/target-components.yml`
     - `docs/architecture/spec2viz/target-runtime.yml`
     - `desk/tasks/task-freeze-python-rust-ownership-and-ffi-contract.md`
     - `desk/atoms/python-cli-orchestration-layer.md`
     - `desk/atoms/rust-core.md`
     - `desk/atoms/decision-rust-core-with-minimal-python.md`
     - `desk/atoms/decision-pyo3-ffi.md`
   - Changes for ownership: Use these sections:
     - `Purpose and Governing Sources`
     - `Ownership Principles`
     - `Python-Owned Responsibilities`
     - `Rust-Owned Responsibilities`
     - `PyO3 / FFI Boundary`
     - `Forbidden Ownership Drift`
     - `Downstream Consumption Rules`
     Keep the bullets concrete: Python owns CLI orchestration and Git interaction; Rust owns canonical AST, importer/emitter, store, query/indexing, invariants, and structural transforms.
   - Acceptance:
     - The parity block freezes scope without adding new product promises.
     - The ownership block keeps Python thin and Rust canonical.
     - Both blocks explicitly cite the source files listed above.

4. **Draft the Rust canonical core block using the store and anchoring diagrams as supporting constraints**
   - File: `subagent/drafts/contracts-batch-a.md`
   - References:
     - `docs/architecture/target-system-overview.md`
     - `docs/architecture/spec2viz/target-components.yml`
     - `docs/architecture/spec2viz/target-store-graph.yml`
     - `docs/architecture/spec2viz/target-anchoring.yml`
     - `desk/tasks/task-specify-rust-canonical-core-contract.md`
     - `desk/atoms/canonical-ast.md`
     - `desk/atoms/decision-rowan-ast.md`
     - `desk/atoms/decision-blake3-hashing.md`
     - `desk/atoms/decision-links-and-anchors-are-canonical.md`
     - `desk/atoms/relation-ast-extensibility.md`
     - `desk/atoms/stable-selector.md`
   - Changes: Use these sections:
     - `Purpose and Inputs`
     - `Canonical AST Baseline`
     - `Hashing and Structural Identity`
     - `Canonical Links, Anchors, and Relation ASTs`
     - `Selector and Invariant Baseline`
     - `Phase 1 Extensibility Limits`
     - `Downstream Guarantees`
     The bullets should explicitly mention lossless `rowan` support for reversible families, `blake3` hashing, hashes as node fields, links/anchors as canonical data, and stable selectors as downstream guarantees.
   - Acceptance:
     - The block reads as a Rust-owned substrate contract, not an implementation design doc.
     - It names the minimum canonical primitives needed by round-trip, store, anchoring, and retrieval work.
     - It does not push canonical responsibilities back into Python.

5. **Draft the Markdown round-trip block last so it can inherit parity, ownership, and Rust-core constraints**
   - File: `subagent/drafts/contracts-batch-a.md`
   - References:
     - `docs/architecture/target-system-overview.md`
     - `docs/architecture/spec2viz/target-components.yml`
     - `docs/architecture/spec2viz/target-runtime.yml`
     - `desk/tasks/task-specify-markdown-roundtrip-contract.md`
     - `desk/atoms/importer-translator.md`
     - `desk/atoms/markdown-importer.md`
     - `desk/atoms/emitter-compiler.md`
     - `desk/atoms/markdown-emitter.md`
     - `desk/atoms/reversible-document-family.md`
     - `desk/atoms/structurednldoc-contract.md`
     - `desk/atoms/decision-rowan-ast.md`
   - Changes: Use these sections:
     - `Purpose and Inputs`
     - `Reversible Family Definition`
     - `Markdown Import Contract`
     - `Canonical AST to Markdown Emit Contract`
     - `Exact Round-Trip Equality Rule`
     - `Proof Obligations and Evidence`
     - `Phase 1 Exclusions`
     Keep the bullets explicit about `AST -> render -> AST -> render` equality, Rust-owned importer/emitter behavior, and proof obligations such as fixture-based round-trip evidence rather than vague promises.
   - Acceptance:
     - The block treats Markdown as importer/emitter surface, not sovereign truth.
     - The equality rule is exact, not approximate.
     - Non-reversible families are explicitly deferred.

6. **Run a final content check and append the required acceptance report**
   - File: `subagent/drafts/contracts-batch-a.md`
   - Changes:
     - Verify each of the 4 contract blocks is concise, sectioned, and grounded in the listed sources.
     - Verify the file contains only the combined drafts plus the required acceptance report.
     - End the file with a fenced JSON block tagged `acceptance-report` using the user-specified schema.
   - Acceptance:
     - `changedFiles` lists only `subagent/drafts/contracts-batch-a.md` unless more files were actually touched.
     - `testsAddedOrUpdated` is empty unless tests were truly added.
     - `commandsRun`, `validationOutput`, `residualRisks`, and `noStagedFiles` are filled honestly from the execution run.
     - The final file ends with the acceptance JSON block and does not widen scope beyond the 4 requested drafts.

## Files to Modify
- None expected; the task should create a single combined output file rather than modifying existing contract docs.

## New Files
- `subagent/drafts/contracts-batch-a.md` - combined draft file containing the 4 compact contract-doc drafts plus the final acceptance report.

## Dependencies
- Task 2 depends on Task 1.
- Task 3 depends on Tasks 1 and 2.
- Task 4 depends on Task 3 for the parity and ownership constraints it must inherit.
- Task 5 depends on Tasks 3 and 4 because the Markdown contract should reflect the already-frozen parity, ownership, and Rust-core boundaries.
- Task 6 depends on Tasks 2 through 5.

## Risks
- `/home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/context.md` was requested by the task prompt but is missing from the repository root, so the execution agent must not imply it was used.
- The repository contains `plan.md` with section suggestions for these contracts, but the requested grounding source is the architecture docs, diagrams, and task texts; treat `plan.md` as optional support, not a governing source.
- The spec2viz diagrams give architectural boundaries but not low-level FFI or proof-mechanism details, so the drafts must stay at contract level and avoid inventing APIs.
- Because the output is a single combined draft file rather than 4 standalone files, the separators and headings must be unambiguous so the parent can split them later if needed.
- If staged-file state is not explicitly checked during execution, the acceptance report should describe that limitation rather than overclaiming validation.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Created only the requested planning artifact at subagent/drafts/contracts-batch-a.md, with no code changes and no expansion beyond the requested 4 contract-draft batch." 
    }
  ],
  "changedFiles": [
    "subagent/drafts/contracts-batch-a.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "functions.ls / functions.find / functions.grep / functions.read repository inspection",
      "result": "passed",
      "summary": "Inspected the available architecture docs, spec2viz diagrams, task files, and supporting atoms needed to build a concrete execution plan."
    }
  ],
  "validationOutput": [
    "Confirmed docs/architecture/target-system-overview.md and the 4 spec2viz YAML files exist and define the target boundaries used by the requested drafts.",
    "Confirmed the 4 relevant task files exist under desk/tasks and specify the contract intent, scope, and validation expectations.",
    "Confirmed /home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/context.md does not exist, so the plan surfaces that gap explicitly."
  ],
  "residualRisks": [
    "The requested context.md source is missing, so execution must rely on the available architecture docs, diagrams, task texts, and cited atoms.",
    "noStagedFiles is based on the fact that no staging action was performed in this run, not on a git-status check."
  ],
  "noStagedFiles": true,
  "diffSummary": "Added a concrete implementation plan for producing the 4 combined contract drafts in subagent/drafts/contracts-batch-a.md.",
  "reviewFindings": [
    "no blockers; the main execution caution is to acknowledge the missing context.md and avoid inventing details not supported by the architecture docs or task files"
  ],
  "manualNotes": "This file is a planning artifact only, per subagent role. It does not contain the contract drafts themselves."
}
```