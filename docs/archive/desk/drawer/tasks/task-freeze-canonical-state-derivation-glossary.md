> **Archivado 2026-10-08 — planning-era, pre-v2.** El drawer vigente se describe en `docs/v2/05-estado.md` §6; este backlog no entra al store `.kimun`.

---
id: task-freeze-canonical-state-derivation-glossary
status: open
summary: Freeze the glossary for inputs, canonical state, renders, projections, and derived indexes so downstream atoms use one terminology set.
tags:
- workspace:desk
- artifact:task
- system:sldb
history:
- "2026-07-29: opened to normalize derivation terminology around canonical state."
references:
- also_core.md
- core_README.md
- diagramas_core.md
- interfaces.md
- libraries_core.md
- plan_core.md
- desk/atoms/clojure-core.md
- desk/atoms/projection.md
- desk/atoms/document-materializer.md
- docs/architecture/target-system-overview.md
pills:
- desk/contexts/pill-planning-contracts.md
files:
- docs/architecture/contracts/canonical-state-derivation-glossary.md
checklists: []
---

# Freeze canonical-state derivation glossary

## Rationale

_Explain why this task exists or the business driver behind it._

The repo needs one clean vocabulary for authored inputs, canonical state, renders/materializations, projections, and derived indexes.

## Goal

_Describe the concrete result this task must produce._

Produce one planning-only glossary contract and align the most directly affected atoms to it.

## Scope

_State what is in scope and what is out of scope._

- **In-Scope**:
  - glossary definitions
  - terminology normalization in directly affected atoms
  - contradiction-map normalization after glossary freeze
- **Out-of-Scope**:
  - runtime implementation
  - parity claims from code
  - broad repo-wide prose rewrites beyond the directly affected files

## Validation

_List the checks required before this task can close._

- Does the artifact define the glossary terms explicitly?
- Does it keep canonical authority in Clojure-owned database-backed state?
- Does it distinguish input, render/materialization, projection, and derived index?
- Does it stay planning-only?

## Done When

_Name the observable condition that makes the task complete._

The `canonical-state-derivation-glossary.md` contract exists and the directly affected atoms align to it.

