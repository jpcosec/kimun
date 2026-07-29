---
id: task-specify-anchoring-contract
status: closed
summary: Re-anchored to high-fidelity contract in docs/architecture/contracts/anchoring-contract.md.
tags:
- workspace:desk
- artifact:task
- system:sldb
history: ["2026-07-29: closed with high-fidelity contract artifact."]
references:
- desk/atoms/ast-anchor.md
- desk/atoms/text-anchor.md
- desk/atoms/external-anchor.md
- desk/atoms/stable-selector.md
- desk/atoms/document-path.md
- desk/atoms/decision-links-and-anchors-are-canonical.md
- docs/architecture/spec2viz/target-anchoring.yml
- docs/architecture/spec2viz/target-components.yml
depends_on: []
pills:
- desk/contexts/pill-planning-contracts.md
files:
- docs/architecture/contracts/anchoring-contract.md
checklists: []
---

# Specify Anchoring Contract

## Rationale

Grounded in `decision-links-and-anchors-are-canonical.md`. To maintain reliable cross-references, we must have stable addressability into document fragments. This task ensures that anchors are not just line-offsets, but structural "pins" into the graph.

## Goal

Define the taxonomy and resolution lifecycle for anchors. Mandate the use of `stable-selector.md` logic to ensure that anchors survive minor document refactors.

## Scope

- **In-Scope**: 
  - `AnchorNode` structure including `SourceHash` and `Locator`.
  - `TextAnchors` vs `ASTAnchors` (AST-aware where possible).
  - `FamilySpecificLocators` for Markdown, YAML, and Code.
- **Out-of-Scope**: 
  - `VisualUX` anchor highlighting.
  - Multi-document fuzzy re-anchoring.

## Implementation Path

1. **Locator Strategy**: Map `target-anchoring.yml` nodes to implementation primitives.
2. **Selection Logic**: Detail how `TextLocators` (char/paragraph) and `CodeLocators` (tree-sitter/syntax) are prioritized.
3. **Resilience Pattern**: Define how `SampleText` is used as a verification fallback.
4. **Contract Materialization**: Write the final specification in `docs/architecture/contracts/anchoring-contract.md`.

## Validation

- Does the contract define "Resilience" for selectors?
- Does it require `DocumentPath` and `SourceHash` for every `AnchorNode`?
- Does it match the `AnchorNodes` contains list in `target-components.yml`?

## Done When

The high-fidelity `anchoring-contract.md` is committed and approved.
