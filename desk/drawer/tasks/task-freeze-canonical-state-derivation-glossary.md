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

The repo needs one clean vocabulary for authored inputs, canonical state, renders/materializations, projections, and derived indexes.

## Goal

Produce one planning-only glossary contract and align the most directly affected atoms to it.

## Scope

- **In-Scope**:
  - glossary definitions
  - terminology normalization in directly affected atoms
  - contradiction-map normalization after glossary freeze
- **Out-of-Scope**:
  - runtime implementation
  - parity claims from code
  - broad repo-wide prose rewrites beyond the directly affected files

## Validation

- Does the artifact define the glossary terms explicitly?
- Does it keep canonical authority in Clojure-owned database-backed state?
- Does it distinguish input, render/materialization, projection, and derived index?
- Does it stay planning-only?

## Done When

The `canonical-state-derivation-glossary.md` contract exists and the directly affected atoms align to it.
