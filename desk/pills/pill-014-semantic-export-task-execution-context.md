---
id: pill-014-semantic-export-task-execution-context
tags:
- system:sldb
- system:kgdb
- topic:semantic-export
- topic:execution
---

# Semantic export provenance task execution context

## What

This task tightens the SLDB-to-KGDB handoff contract so downstream graph systems receive enough source-side provenance to reason globally without losing the chain back to authored textual units.

## Why

The repo already exports models, documents, sections, semantic tags, semantic DAG data, and hashes. The open question is whether that contract is explicit enough about document-subunit provenance once addressability/query/composition become richer.

## Required Reads

Read these first:

- `desk/tasks/task-tighten-semantic-export-provenance-contract.md`
- `desk/pills/pill-011-addressability-task-execution-context.md`
- `docs/architecture/semantic-export-boundary.md`
- `docs/architecture/sldb-text-layer-vs-kgdb-graph-layer.md`
- `src/sldb/store/export.py`

## Current Repo Reality

The current export payload includes:

- model entries with model metadata and hashes;
- document entries with stable export ids and document hashes;
- section entries with ids, breadcrumbs, slugs, semantic tags, and line ranges;
- semantic DAG nodes and equivalences;
- store/runtime provenance metadata.

The current contract deliberately excludes graph persistence policy, workflow-specific edges, and direct runtime scraping as an ingestion contract.

## In Scope

Define what provenance must survive export when richer SLDB structure is referenced, including:

- source document identity;
- addressable textual unit identity when relevant;
- model identity;
- semantic tags;
- extraction/hash lineage;
- how downstream systems distinguish source anchors from graph-native identities.

## Out of Scope

Do not define:

- KGDB persistence internals;
- graph entity unification rules;
- workflow-specific edges from deskops or repo-local operations;
- a requirement that KGDB mirror SLDB private runtime layout.

## Expected Outputs

Produce a durable contract update that states:

- what provenance anchors are required;
- which anchors are canonical versus derived;
- what the export payload must preserve for downstream traceability;
- whether new payload fields or clarified semantics are needed;
- follow-up implementation tasks if payload changes should be staged.

## How

Use the addressability contract as the source-side truth. Preserve enough information that a downstream graph node or edge can be traced back to the authored document unit it came from without pretending the source anchor is itself a graph identity.

## How Not

Do not make KGDB depend on undocumented `.sldb` runtime internals, and do not collapse textual provenance anchors into graph-global ids.
