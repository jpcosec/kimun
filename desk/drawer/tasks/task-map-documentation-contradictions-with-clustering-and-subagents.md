---
id: task-map-documentation-contradictions-with-clustering-and-subagents
status: open
summary: Build a planning-only contradiction map for repository documentation using clustering for discovery and subagent review for adjudication.
tags:
- workspace:desk
- artifact:task
- system:sldb
history:
- "2026-07-29: opened to map documentation contradictions with a clustering-plus-subagent review pass."
references:
- also_core.md
- core_README.md
- diagramas_core.md
- interfaces.md
- libraries_core.md
- plan_core.md
- README.md
- docs/faq.md
- docs/architecture/target-system-overview.md
- docs/architecture/contracts/repo-source-order-and-contradiction-review.md
- desk/atoms/clojure-core.md
- desk/atoms/projection.md
- desk/atoms/lisp-metalanguage.md
- desk/atoms/markdown-text-surface.md
pills:
- desk/contexts/pill-planning-contracts.md
files:
- docs/architecture/contracts/documentation-contradiction-map.md
checklists: []
---

# Map documentation contradictions with clustering and subagents

## Rationale

The repo now has enough architecture and atom material that contradiction review should use broad discovery first and human-readable adjudication second.

## Goal

Produce one planning-only contradiction map that identifies real contradictions, wording drift, historical-only conflicts, and already-aligned concepts.

## Scope

- **In-Scope**:
  - documentation chunking and semantic grouping for discovery
  - contradiction candidate review across prioritized docs, contracts, architecture docs, atoms, and historical reports
  - authority-aware resolution notes
- **Out-of-Scope**:
  - runtime implementation
  - parity claims from code
  - changing architecture truth beyond documenting contradiction status

## Validation

- Does the artifact describe the discovery method?
- Does it classify real contradictions vs wording drift vs historical-only conflicts?
- Does it cite governing atoms and target docs?
- Does it stay planning-only?

## Done When

The `documentation-contradiction-map.md` contract exists and records the contradiction map plus authority-aware resolutions.
