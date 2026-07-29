---
id: task-downstream-core-source-coverage-into-atoms-and-spec2viz
status: open
summary: Add missing operational architecture concepts from core source docs into concise atoms and aligned spec2viz surfaces.
tags:
- workspace:desk
- artifact:task
- system:sldb
history: ["2026-07-29: opened for planning-only downstream coverage into atoms and spec2viz."]
references:
- also_core.md
- core_README.md
- diagramas_core.md
- interfaces.md
- libraries_core.md
- plan_core.md
- desk/atoms/canonical-ast.md
- desk/atoms/store-infrastructure.md
- docs/architecture/spec2viz/target-components.yml
- docs/architecture/spec2viz/target-runtime.yml
- docs/architecture/spec2viz/target-store-graph.yml
pills:
- desk/contexts/pill-planning-contracts.md
files:
- docs/architecture/contracts/core-source-downstream-coverage-contract.md
checklists: []
---

# Downstream core source coverage into atoms and spec2viz

## Rationale

The root core docs define several operational architecture concepts that were only partially represented in atoms and spec2viz. Planning quality depends on making those concepts explicit, concise, and queryable.

## Goal

Normalize the missing concepts from the six core source docs into short atoms and aligned spec2viz surfaces.

## Scope

- **In-Scope**:
  - concise atoms for missing operational concepts
  - spec2viz updates for missing architecture surfaces
  - explicit alignment contract documenting what was added
- **Out-of-Scope**:
  - runtime implementation
  - parity claims from code
  - broad rewrite of existing architecture truth

## Validation

- Does the contract name the missing concepts and where they land?
- Are new atoms brief, descriptive, and split instead of overloaded?
- Do spec2viz views show the newly required surfaces?
- Does everything remain planning-only?

## Done When

The coverage contract exists and the missing concepts are represented in concise atoms plus aligned spec2viz views.
