> **Archivado 2026-10-08 — planning-era, pre-v2.** El drawer vigente se describe en `docs/v2/05-estado.md` §6; este backlog no entra al store `.kimun`.

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

_Explain why this task exists or the business driver behind it._

The repo now has enough architecture and atom material that contradiction review should use broad discovery first and human-readable adjudication second.

## Goal

_Describe the concrete result this task must produce._

Produce one planning-only contradiction map that identifies real contradictions, wording drift, historical-only conflicts, and already-aligned concepts.

## Scope

_State what is in scope and what is out of scope._

- **In-Scope**:
  - documentation chunking and semantic grouping for discovery
  - contradiction candidate review across prioritized docs, contracts, architecture docs, atoms, and historical reports
  - authority-aware resolution notes
- **Out-of-Scope**:
  - runtime implementation
  - parity claims from code
  - changing architecture truth beyond documenting contradiction status

## Validation

_List the checks required before this task can close._

- Does the artifact describe the discovery method?
- Does it classify real contradictions vs wording drift vs historical-only conflicts?
- Does it cite governing atoms and target docs?
- Does it stay planning-only?

## Done When

_Name the observable condition that makes the task complete._

The `documentation-contradiction-map.md` contract exists and records the contradiction map plus authority-aware resolutions.

