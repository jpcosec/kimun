# Task audit report — 2026-07-29

## Conclusion

- The board was mostly junk scaffolding.
- The only tasks worth keeping are the four Phase 1 spine tasks already aligned to the target architecture:
  - `task-implement-rust-core-graph-store-and-rowan-ast`
  - `task-implement-python-cli-and-pyo3-ffi-boundary`
  - `task-importers-emitters-and-semantic-indexing`
  - `task-replicate-v1-cli-workflow-capabilities`
- Everything else was removed from the board/task set because it was either placeholder noise, duplicate decomposition, or non-plan admin work.

## How far the plan is from spec2viz + atoms

- **Direction:** roughly aligned.
  - The surviving four tasks follow the intended Phase 1 sequence from `target-system-overview.md`.
  - They map to the main lanes in `target-components.yml` and `target-runtime.yml`.
- **Execution readiness:** far from aligned.
  - Every retained task is still `draft`.
  - All retained tasks still have empty implementation paths.
  - All retained tasks still have empty validation sections.
  - Dependency references still use nonexistent `macrotask-*` ids instead of actual routed tasks.
- **Coverage gaps:** still implicit, not tasked explicitly.
  - `target-anchoring.yml` has no explicit surviving task.
  - `HistoryArtifacts` from `target-store-graph.yml` are only implied inside the Rust/store work.
  - `Adapters / Projections` are only implied inside importer/exporter and parity work.

## Why the kept tasks survive

| Task | Verdict | Why it survives |
|---|---|---|
| `task-implement-rust-core-graph-store-and-rowan-ast` | keep | Direct match for `RustCore`, `CanonicalAST`, `GraphStore`, `ASTPersistence`, `Hashing`; matches atoms `rust-core`, `graph-store`, `decision-rowan-ast`, `decision-rusqlite-store`. |
| `task-implement-python-cli-and-pyo3-ffi-boundary` | keep | Direct match for `PythonCLI`, `python_cli_layer`, FFI boundary, and Git orchestration; matches atoms `python-cli-orchestration-layer`, `decision-pyo3-ffi`, `git-orchestration-in-python`. |
| `task-importers-emitters-and-semantic-indexing` | keep | Direct match for `Importers`, `Emitters`, `MarkdownImporter`, `MarkdownEmitter`, `DocumentMaterializer`, `SemanticExporter`, `SemanticIndex`; matches atoms `markdown-importer`, `markdown-emitter`, `document-materializer`, `semantic-indexing`. |
| `task-replicate-v1-cli-workflow-capabilities` | keep | Direct match for Phase 1 parity and CLI continuity; matches atoms `phase-1-v1-replication`, `decision-v1-parity-before-scope-expansion`, `cli-workflow-surface`. |

## Removed tasks

### A. Placeholder topic/concept microtasks removed

These were all placeholder-generated junk:
- empty implementation path
- empty validation
- empty done criteria
- zero atom references
- generic goal text like `Implement the required functionality for topic:*`
- many depended on nonexistent `macrotask-*` ids
- they decomposed the architecture into ad hoc topic words instead of the Phase 1 implementation spine

Removed:
- `task-implement-adapters`
- `task-implement-addressability`
- `task-implement-anchors`
- `task-implement-ast`
- `task-implement-authoring`
- `task-implement-boundary`
- `task-implement-cache`
- `task-implement-clean-code`
- `task-implement-cli`
- `task-implement-composition`
- `task-implement-document-families`
- `task-implement-documents`
- `task-implement-emitters`
- `task-implement-fields`
- `task-implement-general`
- `task-implement-graphs`
- `task-implement-hashing`
- `task-implement-history`
- `task-implement-hooks`
- `task-implement-identity`
- `task-implement-importers`
- `task-implement-indexes`
- `task-implement-links`
- `task-implement-markdown`
- `task-implement-materialization`
- `task-implement-models`
- `task-implement-navigation`
- `task-implement-nodes`
- `task-implement-onboarding`
- `task-implement-patterns`
- `task-implement-payloads`
- `task-implement-projections`
- `task-implement-provenance`
- `task-implement-python`
- `task-implement-queries`
- `task-implement-relations`
- `task-implement-retrieval`
- `task-implement-rust`
- `task-implement-rust-core`
- `task-implement-schemas`
- `task-implement-search`
- `task-implement-semantic-export`
- `task-implement-semantic-indexing`
- `task-implement-store`
- `task-implement-structure`
- `task-implement-structured-text`
- `task-implement-testing`
- `task-implement-text-graph`
- `task-implement-types`
- `task-implement-ux`
- `task-implement-workflows`

### B. Duplicate refactor tasks removed

These pointed at the same work already covered by the surviving Phase 1 tasks, but were lower-quality and mostly empty:
- `task-refactor-ast-to-pulldown-cmark-and-rowan`
- `task-refactor-cli-to-v1-parity`
- `task-refactor-graphs-to-petgraph`
- `task-refactor-search-to-fts5-or-tantivy`

### C. Non-plan admin task removed

- `task-track-atom-work`
  - Not part of the target architecture implementation spine.
  - Admin hygiene task, not a product/runtime milestone.

## Per-task verdicts

| Task | Verdict | Reason |
|---|---|---|
| `task-track-atom-work` | delete | Admin task; not in spec2viz target architecture. |
| `task-implement-rust-core-graph-store-and-rowan-ast` | keep | Core Phase 1 architecture task. |
| `task-implement-python-cli-and-pyo3-ffi-boundary` | keep | Core Phase 1 architecture task. |
| `task-importers-emitters-and-semantic-indexing` | keep | Core Phase 1 architecture task. |
| `task-replicate-v1-cli-workflow-capabilities` | keep | Core Phase 1 architecture task. |
| `task-implement-anchors` | delete | Placeholder microtask; no evidence or explicit plan body. |
| `task-implement-general` | delete | Placeholder microtask; vague topic, no atom binding. |
| `task-implement-cli` | delete | Placeholder duplicate of Python CLI / v1 parity work. |
| `task-implement-hashing` | delete | Placeholder duplicate of Rust core / graph store work. |
| `task-implement-adapters` | delete | Placeholder duplicate of adapters/projections work. |
| `task-implement-identity` | delete | Placeholder duplicate of canonical AST/store work. |
| `task-implement-models` | delete | Placeholder duplicate of store-backed CLI parity work. |
| `task-implement-semantic-indexing` | delete | Placeholder duplicate of importer/emitter/indexing work. |
| `task-implement-ux` | delete | Placeholder future-surface task; Phase 1 parity first. |
| `task-implement-ast` | delete | Placeholder duplicate of Rust core / canonical AST work. |
| `task-implement-fields` | delete | Placeholder duplicate of v1 parity/store-backed mode work. |
| `task-implement-indexes` | delete | Placeholder duplicate of graph store / derived indexes work. |
| `task-implement-structure` | delete | Placeholder duplicate of canonical AST work. |
| `task-implement-store` | delete | Placeholder duplicate of graph store work. |
| `task-implement-relations` | delete | Placeholder duplicate of canonical relations / graph store work. |
| `task-implement-workflows` | delete | Placeholder duplicate of Python CLI / v1 parity work. |
| `task-implement-history` | delete | Placeholder duplicate of graph-store history artifacts work. |
| `task-implement-schemas` | delete | Placeholder duplicate of models/store-backed mode work. |
| `task-implement-queries` | delete | Placeholder duplicate of store-backed mode / parity work. |
| `task-implement-links` | delete | Placeholder duplicate of canonical relations work. |
| `task-implement-rust-core` | delete | Placeholder duplicate of Rust core master task. |
| `task-implement-graphs` | delete | Placeholder duplicate of graph store / petgraph work. |
| `task-implement-clean-code` | delete | Process/theme, not an architecture milestone. |
| `task-implement-addressability` | delete | Placeholder duplicate of anchors/locators/parity work. |
| `task-implement-materialization` | delete | Placeholder duplicate of emitter/materializer work. |
| `task-implement-markdown` | delete | Placeholder duplicate of markdown importer/emitter work. |
| `task-implement-nodes` | delete | Placeholder duplicate of AST persistence work. |
| `task-implement-types` | delete | Placeholder duplicate of Rust core type system work. |
| `task-implement-testing` | delete | Process/theme, not a bounded architecture task. |
| `task-implement-composition` | delete | Placeholder; no target-spec task body. |
| `task-implement-patterns` | delete | Process/theme, not a bounded architecture task. |
| `task-implement-documents` | delete | Placeholder duplicate of importer/materializer/parity work. |
| `task-implement-rust` | delete | Placeholder duplicate of Rust core master task. |
| `task-implement-importers` | delete | Placeholder duplicate of importer/emitter/indexing task. |
| `task-implement-navigation` | delete | Placeholder duplicate of parity/search/index work. |
| `task-implement-boundary` | delete | Placeholder duplicate of Python/Rust FFI task. |
| `task-implement-hooks` | delete | Placeholder; no target-spec architecture anchor. |
| `task-implement-cache` | delete | Placeholder; not an explicit Phase 1 spine task. |
| `task-implement-onboarding` | delete | Not part of target architecture spine. |
| `task-implement-text-graph` | delete | Placeholder duplicate of graph store / projection work. |
| `task-implement-search` | delete | Placeholder duplicate of importer/indexing task. |
| `task-implement-structured-text` | delete | Placeholder duplicate of reversible docs/importers work. |
| `task-implement-semantic-export` | delete | Placeholder duplicate of adapters/export work. |
| `task-implement-emitters` | delete | Placeholder duplicate of importer/emitter/indexing task. |
| `task-implement-authoring` | delete | Placeholder; future UX/editor concern, not Phase 1 spine. |
| `task-implement-provenance` | delete | Placeholder duplicate of history/provenance store work. |
| `task-implement-python` | delete | Placeholder duplicate of Python CLI / FFI task. |
| `task-implement-retrieval` | delete | Placeholder duplicate of search/index/parity work. |
| `task-implement-projections` | delete | Placeholder duplicate of adapters/projections work. |
| `task-implement-payloads` | delete | Placeholder duplicate of relation AST extensibility work. |
| `task-implement-document-families` | delete | Placeholder duplicate; later broadening beyond tight Phase 1 tasks. |
| `task-refactor-ast-to-pulldown-cmark-and-rowan` | delete | Duplicate of Rust core + importer work; empty body. |
| `task-refactor-cli-to-v1-parity` | delete | Duplicate of Python CLI + parity work; empty body. |
| `task-refactor-graphs-to-petgraph` | delete | Duplicate of Rust core graph-store work; empty body. |
| `task-refactor-search-to-fts5-or-tantivy` | delete | Duplicate of importer/indexing work; empty body. |

## Board cleanup applied

- `desk/tasks/Board.md` now routes only the four surviving tasks.
- Removed task files listed above.
- Removed matching generated routines, conditions, operators, and edges for removed tasks.
