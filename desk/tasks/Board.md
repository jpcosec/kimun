---
# board-xxx
id: board-001
# Affected workspace or domain
scope: desk
# List of task-xxx paths
tasks:
- desk/tasks/task-track-atom-work.md
- desk/tasks/task-implement-rust-core-graph-store-and-rowan-ast.md
- desk/tasks/task-implement-python-cli-and-pyo3-ffi-boundary.md
- desk/tasks/task-importers-emitters-and-semantic-indexing.md
- desk/tasks/task-replicate-v1-cli-workflow-capabilities.md
- desk/tasks/task-implement-anchors.md
- desk/tasks/task-implement-general.md
- desk/tasks/task-implement-cli.md
- desk/tasks/task-implement-hashing.md
- desk/tasks/task-implement-adapters.md
- desk/tasks/task-implement-identity.md
- desk/tasks/task-implement-models.md
- desk/tasks/task-implement-semantic-indexing.md
- desk/tasks/task-implement-ux.md
- desk/tasks/task-implement-ast.md
- desk/tasks/task-implement-fields.md
- desk/tasks/task-implement-indexes.md
- desk/tasks/task-implement-structure.md
- desk/tasks/task-implement-store.md
- desk/tasks/task-implement-relations.md
- desk/tasks/task-implement-workflows.md
- desk/tasks/task-implement-history.md
- desk/tasks/task-implement-schemas.md
- desk/tasks/task-implement-queries.md
- desk/tasks/task-implement-links.md
- desk/tasks/task-implement-rust-core.md
- desk/tasks/task-implement-graphs.md
- desk/tasks/task-implement-clean-code.md
- desk/tasks/task-implement-addressability.md
- desk/tasks/task-implement-materialization.md
- desk/tasks/task-implement-markdown.md
- desk/tasks/task-implement-nodes.md
- desk/tasks/task-implement-types.md
- desk/tasks/task-implement-testing.md
- desk/tasks/task-implement-composition.md
- desk/tasks/task-implement-patterns.md
- desk/tasks/task-implement-documents.md
- desk/tasks/task-implement-rust.md
- desk/tasks/task-implement-importers.md
- desk/tasks/task-implement-navigation.md
- desk/tasks/task-implement-boundary.md
- desk/tasks/task-implement-hooks.md
- desk/tasks/task-implement-cache.md
- desk/tasks/task-implement-onboarding.md
- desk/tasks/task-implement-text-graph.md
- desk/tasks/task-implement-search.md
- desk/tasks/task-implement-structured-text.md
- desk/tasks/task-implement-semantic-export.md
- desk/tasks/task-implement-emitters.md
- desk/tasks/task-implement-authoring.md
- desk/tasks/task-implement-provenance.md
- desk/tasks/task-implement-python.md
- desk/tasks/task-implement-retrieval.md
- desk/tasks/task-implement-projections.md
- desk/tasks/task-implement-payloads.md
- desk/tasks/task-implement-document-families.md
# List of pill-xxx paths
pills:
- desk/contexts/pills.md
# List of ritual-xxx paths
rituals:
- desk/rituals/execution.md
- desk/rituals/testing.md
- desk/rituals/closeout.md
# e.g., system:sldb, workspace:desk
tags:
- workspace:desk
---

# sldb-refactor-worktree Board

## Purpose

_Explain what this board routes and why it exists._



## Notes

_Add short operational notes about the current routed set._

- Track Atom Work [draft] - Track all untracked atom documents
- Implement Rust Core: Graph Store and Rowan AST [draft] - Build the foundational `.sldb/` database schema in SQLite, the graph abstractions using petgraph, and the rowan-based lossless AST.
- Implement Python CLI and PyO3 FFI Boundary [draft] - Develop the pyo3 bindings to expose the Rust store and AST. Scaffold the Typer/Click based Python CLI that drives Git integrations.
- Importers, Emitters, and Semantic Indexing [draft] - Implement pulldown-cmark based importers, rowan-based reversible emitters, and setup Tantivy or SQLite FTS5 for the SearchIndex.
- Replicate V1 CLI Workflow Capabilities [draft] - Re-implement the deskops equivalent commands on top of the new Python-Rust architecture, ensuring 100% parity with V1.
- Implement Anchors [draft] - Implement the required functionality for topic:anchors.
- Implement General [draft] - Implement the required functionality for topic:general.
- Implement Cli [draft] - Implement the required functionality for topic:cli.
- Implement Hashing [draft] - Implement the required functionality for topic:hashing.
- Implement Adapters [draft] - Implement the required functionality for topic:adapters.
- Implement Identity [draft] - Implement the required functionality for topic:identity.
- Implement Models [draft] - Implement the required functionality for topic:models.
- Implement Semantic Indexing [draft] - Implement the required functionality for topic:semantic-indexing.
- Implement Ux [draft] - Implement the required functionality for topic:ux.
- Implement Ast [draft] - Implement the required functionality for concept:ast.
- Implement Fields [draft] - Implement the required functionality for topic:fields.
- Implement Indexes [draft] - Implement the required functionality for topic:indexes.
- Implement Structure [draft] - Implement the required functionality for topic:structure.
- Implement Store [draft] - Implement the required functionality for concept:store.
- Implement Relations [draft] - Implement the required functionality for topic:relations.
- Implement Workflows [draft] - Implement the required functionality for topic:workflows.
- Implement History [draft] - Implement the required functionality for topic:history.
- Implement Schemas [draft] - Implement the required functionality for topic:schemas.
- Implement Queries [draft] - Implement the required functionality for topic:queries.
- Implement Links [draft] - Implement the required functionality for topic:links.
- Implement Rust Core [draft] - Implement the required functionality for concept:rust-core.
- Implement Graphs [draft] - Implement the required functionality for topic:graphs.
- Implement Clean Code [draft] - Implement the required functionality for topic:clean-code.
- Implement Addressability [draft] - Implement the required functionality for topic:addressability.
- Implement Materialization [draft] - Implement the required functionality for topic:materialization.
- Implement Markdown [draft] - Implement the required functionality for topic:markdown.
- Implement Nodes [draft] - Implement the required functionality for topic:nodes.
- Implement Types [draft] - Implement the required functionality for topic:types.
- Implement Testing [draft] - Implement the required functionality for topic:testing.
- Implement Composition [draft] - Implement the required functionality for topic:composition.
- Implement Patterns [draft] - Implement the required functionality for topic:patterns.
- Implement Documents [draft] - Implement the required functionality for topic:documents.
- Implement Rust [draft] - Implement the required functionality for topic:rust.
- Implement Importers [draft] - Implement the required functionality for topic:importers.
- Implement Navigation [draft] - Implement the required functionality for topic:navigation.
- Implement Boundary [draft] - Implement the required functionality for topic:boundary.
- Implement Hooks [draft] - Implement the required functionality for topic:hooks.
- Implement Cache [draft] - Implement the required functionality for topic:cache.
- Implement Onboarding [draft] - Implement the required functionality for topic:onboarding.
- Implement Text Graph [draft] - Implement the required functionality for topic:text-graph.
- Implement Search [draft] - Implement the required functionality for topic:search.
- Implement Structured Text [draft] - Implement the required functionality for topic:structured-text.
- Implement Semantic Export [draft] - Implement the required functionality for topic:semantic-export.
- Implement Emitters [draft] - Implement the required functionality for topic:emitters.
- Implement Authoring [draft] - Implement the required functionality for topic:authoring.
- Implement Provenance [draft] - Implement the required functionality for topic:provenance.
- Implement Python [draft] - Implement the required functionality for topic:python.
- Implement Retrieval [draft] - Implement the required functionality for topic:retrieval.
- Implement Projections [draft] - Implement the required functionality for topic:projections.
- Implement Payloads [draft] - Implement the required functionality for topic:payloads.

## Task Details

_Generated from the task references above._

- Track Atom Work [draft] - Track all untracked atom documents
- Implement Rust Core: Graph Store and Rowan AST [draft] - Build the foundational `.sldb/` database schema in SQLite, the graph abstractions using petgraph, and the rowan-based lossless AST.
- Implement Python CLI and PyO3 FFI Boundary [draft] - Develop the pyo3 bindings to expose the Rust store and AST. Scaffold the Typer/Click based Python CLI that drives Git integrations.
- Importers, Emitters, and Semantic Indexing [draft] - Implement pulldown-cmark based importers, rowan-based reversible emitters, and setup Tantivy or SQLite FTS5 for the SearchIndex.
- Replicate V1 CLI Workflow Capabilities [draft] - Re-implement the deskops equivalent commands on top of the new Python-Rust architecture, ensuring 100% parity with V1.
- Implement Anchors [draft] - Implement the required functionality for topic:anchors.
- Implement General [draft] - Implement the required functionality for topic:general.
- Implement Cli [draft] - Implement the required functionality for topic:cli.
- Implement Hashing [draft] - Implement the required functionality for topic:hashing.
- Implement Adapters [draft] - Implement the required functionality for topic:adapters.
- Implement Identity [draft] - Implement the required functionality for topic:identity.
- Implement Models [draft] - Implement the required functionality for topic:models.
- Implement Semantic Indexing [draft] - Implement the required functionality for topic:semantic-indexing.
- Implement Ux [draft] - Implement the required functionality for topic:ux.
- Implement Ast [draft] - Implement the required functionality for concept:ast.
- Implement Fields [draft] - Implement the required functionality for topic:fields.
- Implement Indexes [draft] - Implement the required functionality for topic:indexes.
- Implement Structure [draft] - Implement the required functionality for topic:structure.
- Implement Store [draft] - Implement the required functionality for concept:store.
- Implement Relations [draft] - Implement the required functionality for topic:relations.
- Implement Workflows [draft] - Implement the required functionality for topic:workflows.
- Implement History [draft] - Implement the required functionality for topic:history.
- Implement Schemas [draft] - Implement the required functionality for topic:schemas.
- Implement Queries [draft] - Implement the required functionality for topic:queries.
- Implement Links [draft] - Implement the required functionality for topic:links.
- Implement Rust Core [draft] - Implement the required functionality for concept:rust-core.
- Implement Graphs [draft] - Implement the required functionality for topic:graphs.
- Implement Clean Code [draft] - Implement the required functionality for topic:clean-code.
- Implement Addressability [draft] - Implement the required functionality for topic:addressability.
- Implement Materialization [draft] - Implement the required functionality for topic:materialization.
- Implement Markdown [draft] - Implement the required functionality for topic:markdown.
- Implement Nodes [draft] - Implement the required functionality for topic:nodes.
- Implement Types [draft] - Implement the required functionality for topic:types.
- Implement Testing [draft] - Implement the required functionality for topic:testing.
- Implement Composition [draft] - Implement the required functionality for topic:composition.
- Implement Patterns [draft] - Implement the required functionality for topic:patterns.
- Implement Documents [draft] - Implement the required functionality for topic:documents.
- Implement Rust [draft] - Implement the required functionality for topic:rust.
- Implement Importers [draft] - Implement the required functionality for topic:importers.
- Implement Navigation [draft] - Implement the required functionality for topic:navigation.
- Implement Boundary [draft] - Implement the required functionality for topic:boundary.
- Implement Hooks [draft] - Implement the required functionality for topic:hooks.
- Implement Cache [draft] - Implement the required functionality for topic:cache.
- Implement Onboarding [draft] - Implement the required functionality for topic:onboarding.
- Implement Text Graph [draft] - Implement the required functionality for topic:text-graph.
- Implement Search [draft] - Implement the required functionality for topic:search.
- Implement Structured Text [draft] - Implement the required functionality for topic:structured-text.
- Implement Semantic Export [draft] - Implement the required functionality for topic:semantic-export.
- Implement Emitters [draft] - Implement the required functionality for topic:emitters.
- Implement Authoring [draft] - Implement the required functionality for topic:authoring.
- Implement Provenance [draft] - Implement the required functionality for topic:provenance.
- Implement Python [draft] - Implement the required functionality for topic:python.
- Implement Retrieval [draft] - Implement the required functionality for topic:retrieval.
- Implement Projections [draft] - Implement the required functionality for topic:projections.
- Implement Payloads [draft] - Implement the required functionality for topic:payloads.
- Implement Document Families [draft] - Implement the required functionality for topic:document-families.
