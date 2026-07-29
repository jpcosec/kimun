---
id: task-produce-atom-ontology-map
status: open
summary: Produce a planning-only ontology map of desk atoms, including axes, domain grouping, and relation semantics.
tags:
- workspace:desk
- artifact:task
- system:sldb
history: ["2026-07-29: opened for planning-only atom ontology mapping."]
references:
- also_core.md
- core_README.md
- desk/atoms/tag-namespaces.yaml
- docs/architecture/tag-domain-map.md
- docs/architecture/spec2viz/target-components.yml
- docs/architecture/spec2viz/target-runtime.yml
- docs/architecture/spec2viz/target-store-graph.yml
pills:
- desk/contexts/pill-planning-contracts.md
files:
- docs/architecture/contracts/atom-ontology-map.md
checklists: []
---

# Produce atom ontology map

## Rationale

The atom set is now large enough that planning and architecture work need one explicit ontology map showing how atoms are grouped, typed, and linked.

## Goal

Produce one planning-only ontology map for the current atom space.

## Scope

- **In-Scope**:
  - ontology axes for atoms
  - domain grouping
  - relation semantics
  - foundational and cross-cutting atom hubs
- **Out-of-Scope**:
  - runtime implementation
  - parity claims from code
  - atom rewrites beyond mapping them

## Validation

- Does the artifact define the ontology axes?
- Does it map all domain groups explicitly?
- Does it explain relation types and hub atoms?
- Does it stay planning-only?

## Done When

The `atom-ontology-map.md` contract exists and provides an explicit ontology map for the current atom space.
