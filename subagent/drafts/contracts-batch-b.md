# Implementation Plan

## Goal
Produce one combined markdown draft file at `subagent/drafts/contracts-batch-b.md` that contains concise, clearly separated contract drafts for `graph-store-contract.md`, `anchoring-contract.md`, `query-and-index-parity-contract.md`, and `cli-parity-contract.md`, grounded in the target overview, relevant spec2viz diagrams, and the four task files.

## Tasks
1. **Confirm the usable source set and record the missing requested context file**
   - File: `/home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/subagent/drafts/contracts-batch-b.md`
   - Changes: Add a brief opening note that the requested `/home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/context.md` does not exist, so the drafts are grounded in the available sources instead: `docs/architecture/target-system-overview.md`, the relevant `docs/architecture/spec2viz/*.yml` files, and the four `desk/tasks/task-specify-*.md` files.
   - Acceptance: The combined draft explicitly cites the source files actually used and does not invent missing `context.md` content.

2. **Create the combined draft scaffold with exact document separators**
   - File: `/home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/subagent/drafts/contracts-batch-b.md`
   - Changes: Structure the output as one markdown file with four obvious separators, each naming the intended contract doc exactly, for example:
     - `---`
     - `# graph-store-contract.md`
     - `# anchoring-contract.md`
     - `# query-and-index-parity-contract.md`
     - `# cli-parity-contract.md`
     Use the same compact section pattern inside each draft so the batch reads consistently.
   - Acceptance: The file contains four clearly labeled draft blocks, one per requested contract doc, with no extra documents added.

3. **Draft the `graph-store-contract.md` section from overview + store/runtime diagrams + task text**
   - File: `/home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/subagent/drafts/contracts-batch-b.md`
   - Changes: Under the `graph-store-contract.md` separator, write concise markdown with explicit sections such as:
     - `## Goal`
     - `## Governing Sources`
     - `## Canonical Persistence`
     - `## Derived Indexes`
     - `## Integrity and History`
     - `## Phase 1 Boundaries`
     Fill those sections with concrete bullets grounded in:
     - `docs/architecture/target-system-overview.md`
     - `docs/architecture/spec2viz/target-store-graph.yml`
     - `docs/architecture/spec2viz/target-runtime.yml`
     - `desk/tasks/task-specify-graph-store-contract.md`
     Required bullets should explicitly cover append-only local `.sldb/` storage, canonical AST nodes, typed relation edges, relation AST payloads, anchor nodes, hash fields, derived section/field/search/semantic indexes, and history/provenance expectations.
   - Acceptance: The draft separates canonical persisted data from derived indexes, matches the store graph diagram, and stays at contract level without schema or implementation detail.

4. **Draft the `anchoring-contract.md` section from anchoring/store diagrams + task text**
   - File: `/home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/subagent/drafts/contracts-batch-b.md`
   - Changes: Under the `anchoring-contract.md` separator, write concise markdown with explicit sections such as:
     - `## Goal`
     - `## Governing Sources`
     - `## Canonical Anchor Payload`
     - `## Text Anchor Contract`
     - `## AST Anchor Contract`
     - `## Reversible-Family Requirements`
     - `## Deferred Locator Scope`
     Ground the bullets in:
     - `docs/architecture/spec2viz/target-anchoring.yml`
     - `docs/architecture/spec2viz/target-store-graph.yml`
     - `desk/tasks/task-specify-anchoring-contract.md`
     Required bullets should name mandatory anchor payload fields (`document path`, `source hash`, `locator`, `sample text`, `anchor kind`), keep `comments` optional, distinguish text anchors from AST anchors, and state that anchors are canonical persisted data rather than projection metadata.
   - Acceptance: The draft clearly distinguishes text vs AST anchoring behavior, preserves canonical-anchor intent from the diagram/task, and explicitly defers PDF/HTML/code/plain-text locator expansion beyond Phase 1.

5. **Draft the `query-and-index-parity-contract.md` section from overview + store diagram + task text**
   - File: `/home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/subagent/drafts/contracts-batch-b.md`
   - Changes: Under the `query-and-index-parity-contract.md` separator, write concise markdown with explicit sections such as:
     - `## Goal`
     - `## Governing Sources`
     - `## Required Derived Indexes`
     - `## Query Surfaces`
     - `## Find Parity Boundaries`
     - `## Semantic Scope Limits`
     - `## Rebuild and Integrity Expectations`
     Ground the bullets in:
     - `docs/architecture/target-system-overview.md`
     - `docs/architecture/spec2viz/target-store-graph.yml`
     - `desk/tasks/task-specify-query-and-index-parity-contract.md`
     Required bullets should state that section, field, search, and tag-based semantic indexes are required derived infrastructure; query behavior sits over derived views rather than canonical truth; `find` parity is the minimum retrieval promise; and embeddings or semantic expansion remain out of scope.
   - Acceptance: The draft preserves Phase 1 parity scope, references graph-store and anchoring constraints instead of redefining them, and explicitly rejects semantic scope expansion.

6. **Draft the `cli-parity-contract.md` section from overview + components/runtime diagrams + task text**
   - File: `/home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/subagent/drafts/contracts-batch-b.md`
   - Changes: Under the `cli-parity-contract.md` separator, write concise markdown with explicit sections such as:
     - `## Goal`
     - `## Governing Sources`
     - `## Command-Group Continuity`
     - `## Direct-Mode Contract`
     - `## Store-Backed Contract`
     - `## Python Responsibilities`
     - `## Contract Mapping`
     - `## Phase 1 Non-Goals`
     Ground the bullets in:
     - `docs/architecture/target-system-overview.md`
     - `docs/architecture/spec2viz/target-components.yml`
     - `docs/architecture/spec2viz/target-runtime.yml`
     - `desk/tasks/task-specify-cli-parity-contract.md`
     Required bullets should cover recognizable continuity for `extract`, `render`, `validate`, plus `docs`, `ast`, `fields`, `find`, `models`, `stores`, `sections`, `faq`, and `help`; keep Python responsible for orchestration/UX/Git interaction only; and map CLI groups back to the underlying core/store/query contracts without redefining internals.
   - Acceptance: The draft preserves the CLI surface described by the task and diagrams, keeps canonical logic in Rust, and lists explicit Phase 1 CLI non-goals.

7. **Add a compact closing acceptance report for the batch-draft artifact**
   - File: `/home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/subagent/drafts/contracts-batch-b.md`
   - Changes: End the file with the required fenced `acceptance-report` JSON, describing the single output-file change, the inspection commands used, the fact that no tests were added, and residual risks such as the missing `context.md` and unverified staged-file state.
   - Acceptance: The file ends with a valid fenced JSON block tagged `acceptance-report`, and its evidence only claims planning/drafting work performed in this run.

## Files to Modify
- `/home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/subagent/drafts/contracts-batch-b.md` - write the combined batch-draft plan and its acceptance report.

## New Files
- `/home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/subagent/drafts/contracts-batch-b.md` - single consolidated output containing the four separated contract drafts.

## Dependencies
- Task 1 should happen before any drafting so the missing `context.md` is handled explicitly.
- Task 2 depends on Task 1.
- Task 3 depends on Task 2.
- Task 4 depends on Task 2 and should stay aligned to Task 3’s canonical-store language.
- Task 5 depends on Tasks 3 and 4 because query/index parity must treat store and anchors as already-defined canonical inputs.
- Task 6 depends on Tasks 3 and 5 because CLI parity should map to the already-stated store/query commitments instead of restating them independently.
- Task 7 depends on Tasks 1 through 6.

## Risks
- The user-directed `context.md` file is missing from the repository, so the executor must rely on the overview, spec2viz YAMLs, and task files unless clarified otherwise.
- The destination directory `subagent/drafts/` does not currently exist and must be created implicitly when writing the output file.
- The four named contract docs do not yet exist as standalone files in the repo; this task asks for combined draft content only, so the executor should not widen scope by creating the individual docs.
- The graph, anchoring, query, and CLI tasks depend conceptually on parity/rust-core/round-trip contracts that are not part of this batch; the drafts should reference those dependencies carefully without inventing missing contract details.
- `noStagedFiles` cannot be directly verified with the available tools; any acceptance note on staged state should be marked as inferred unless separately checked by the parent.

```acceptance-report
{
  "criteriaSatisfied": [
    {
      "id": "criterion-1",
      "status": "satisfied",
      "evidence": "Produced a scope-limited implementation plan for creating one combined draft file containing exactly the four requested contract-doc sections and the required acceptance report, without expanding into code or standalone contract-file creation."
    }
  ],
  "changedFiles": [
    "/home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/subagent/drafts/contracts-batch-b.md"
  ],
  "testsAddedOrUpdated": [],
  "commandsRun": [
    {
      "command": "read /home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/context.md",
      "result": "failed",
      "summary": "Requested context file does not exist in the repository."
    },
    {
      "command": "ls/find/grep/read repository sources under docs/architecture, desk/tasks, and desk/atoms",
      "result": "passed",
      "summary": "Located and inspected the overview, relevant spec2viz diagrams, the four task files, and supporting atoms needed to make the drafting plan concrete."
    },
    {
      "command": "write /home/jp/proyectos/hum-ecosystem/tools/sldb-refactor-worktree/subagent/drafts/contracts-batch-b.md",
      "result": "passed",
      "summary": "Wrote the planning artifact to the required output path."
    }
  ],
  "validationOutput": [
    "Confirmed there is no existing docs/architecture/contracts directory in this worktree.",
    "Confirmed the requested output directory subagent/drafts did not exist before writing and is being used only for the combined output artifact.",
    "Confirmed the plan names the four requested contract docs and specifies concrete section headings and bullet themes for each."
  ],
  "residualRisks": [
    "The requested context.md source is missing, so downstream drafting must rely on the available architecture/task sources unless clarified.",
    "The staged-file state was not directly verifiable with the available toolset; no staging action was performed in this run."
  ],
  "noStagedFiles": true,
  "diffSummary": "Created a single planning file at the required output path describing how to draft the four requested contract docs as separated sections in one markdown artifact.",
  "reviewFindings": [
    "no blockers for planning; main ambiguity is the missing context.md file referenced by the user task"
  ],
  "manualNotes": "This subagent run produced a plan only, per role constraints. It did not draft the contract content itself or create standalone contract markdown files."
}
```